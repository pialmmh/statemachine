package com.telcobright.statemachine.websocket;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.events.EventTypeRegistry;
import com.telcobright.statemachineexamples.callmachine.CallContext;
import com.telcobright.statemachineexamples.callmachine.CallState;
import com.telcobright.statemachineexamples.callmachine.events.*;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializer;

import com.telcobright.statemachine.monitoring.web.SimpleMonitoringServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;












/**
 * CallMachine runner with WebSocket server for real-time monitoring
 */
public class CallMachineRunnerWithWebServer extends WebSocketServer 
        implements StateMachineListener<CallContext, Void> {
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int DEFAULT_PORT = 9999;
    private static final int DEFAULT_UI_PORT = 8091;
    private static final String MACHINE_ID = "websocket-" + System.currentTimeMillis();
    
    private SimpleMonitoringServer monitoringServer;
    private final int uiPort;
    
    private final StateMachineRegistry registry;
    private GenericStateMachine<CallContext, Void> machine;
    private CallContext context;
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Gson gson;
    
    // Track connected clients
    private final Set<WebSocket> connectedClients = ConcurrentHashMap.newKeySet();
    
    // Debug mode and countdown timer
    private boolean debugMode = true; // Hardcoded to true for countdown timer feature
    private ScheduledFuture<?> countdownTimer;
    private int currentCountdown = 0;
    private String countdownState = null;
    
    // Track last event for display
    private String lastEventName = null;
    
    public CallMachineRunnerWithWebServer() {
        this(DEFAULT_PORT, DEFAULT_UI_PORT);
    }
    
    public CallMachineRunnerWithWebServer(int port) {
        this(port, DEFAULT_UI_PORT);
    }
    
    public CallMachineRunnerWithWebServer(int wsPort, int uiPort) {
        super(new InetSocketAddress(wsPort));
        this.uiPort = uiPort;
        
        this.registry = new StateMachineRegistry();
        this.registry.addListener(this);
        
        // Configure Gson with custom serializer to avoid circular references
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalDateTime.class, (JsonSerializer<LocalDateTime>) 
                (src, typeOfSrc, context) -> {
                    return context.serialize(src.format(TIME_FORMAT));
                })
            .create();
            
        // WebSocket server configured
        
        // Initialize state machine immediately so it's ready when clients connect
        initializeStateMachine();
        
        // Start UI server in debug mode
        startUIServer();
    }
    
    /**
     * Enable debug mode with countdown timer
     */
    public CallMachineRunnerWithWebServer withDebugMode(boolean enabled) {
        this.debugMode = enabled;
        System.out.println("[Config] Debug mode " + (enabled ? "enabled" : "disabled"));
        return this;
    }
    
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        connectedClients.add(conn);
        System.out.println("[WS] Client connected: " + conn.getRemoteSocketAddress());
        System.out.println("[WS] Connection is open: " + conn.isOpen());
        System.out.println("[WS] Total connected clients: " + connectedClients.size());
        
        // Send initial state
        sendInitialState(conn);
        
        // Send a test message to verify connection
        JsonObject test = new JsonObject();
        test.addProperty("type", "CONNECTION_TEST");
        test.addProperty("message", "WebSocket connection established");
        conn.send(gson.toJson(test));
        System.out.println("[WS] Sent CONNECTION_TEST message");
        
        // Don't send metadata immediately - wait for client to request it
        // The client will send GET_EVENT_METADATA after connection
    }
    
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        connectedClients.remove(conn);
        System.out.println("[WS] Client disconnected: " + conn.getRemoteSocketAddress());
    }
    
    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("[WS-onMessage] Thread: " + Thread.currentThread().getName());
        System.out.println("[WS-onMessage] Raw message received: " + message);
        System.out.println("[WS-onMessage] Message length: " + message.length());
        System.out.println("[WS-onMessage] From client: " + conn.getRemoteSocketAddress());
        
        // Try to handle the message immediately
        if (message.contains("GET_EVENT_METADATA")) {
            System.out.println("[WS-onMessage] Detected GET_EVENT_METADATA in message!");
            sendEventMetadata(conn);
            return;
        }
        
        try {
            JsonObject request = gson.fromJson(message, JsonObject.class);
            System.out.println("[WS] Parsed JSON successfully");
            
            // Handle messages from Live Mode UI
            if (request.has("type") && "EVENT".equals(request.get("type").getAsString())) {
                String eventType = request.get("eventType").getAsString();
                JsonObject payload = request.has("payload") ? request.getAsJsonObject("payload") : null;
                
                System.out.println("[WS] Received event: " + eventType);
                
                switch (eventType) {
                    case "DIAL":
                    case "INCOMING_CALL":
                        String callerNumber = payload != null && payload.has("phoneNumber") 
                            ? payload.get("phoneNumber").getAsString() 
                            : "+1-555-9999";
                        sendIncomingCall(callerNumber);
                        break;
                    case "ANSWER":
                        sendAnswer();
                        break;
                    case "HANGUP":
                        sendHangup();
                        break;
                    case "BUSY":
                    case "REJECT":
                    case "TIMEOUT":
                        sendHangup(); // Treat as hangup for now
                        break;
                    case "SESSION_PROGRESS":
                        int ringNumber = payload != null && payload.has("ringNumber") 
                            ? payload.get("ringNumber").getAsInt() 
                            : context.getRingCount() + 1;
                        sendSessionProgress("v=0", ringNumber);
                        break;
                    default:
                        System.out.println("[WS] Unhandled event type: " + eventType);
                }
                return;
            }
            
            // Handle legacy format (backward compatibility)
            System.out.println("[WS] Checking for 'action' field in request...");
            String action = request.has("action") ? request.get("action").getAsString() : null;
            System.out.println("[WS] Action field value: " + action);
            if (action != null) {
                JsonObject payload = request.has("payload") ? request.getAsJsonObject("payload") : null;
                
                System.out.println("[WS] Processing action: " + action);
                switch (action) {
                    case "INCOMING_CALL":
                        String callerNumber = payload != null && payload.has("callerNumber") 
                            ? payload.get("callerNumber").getAsString() 
                            : "+1-555-9999";
                        sendIncomingCall(callerNumber);
                        break;
                    case "ANSWER":
                        sendAnswer();
                        break;
                    case "HANGUP":
                        sendHangup();
                        break;
                    case "SESSION_PROGRESS":
                        int ringNumber = payload != null && payload.has("ringNumber") 
                            ? payload.get("ringNumber").getAsInt() 
                            : context.getRingCount() + 1;
                        sendSessionProgress("v=0", ringNumber);
                        break;
                    case "GET_STATE":
                        sendCurrentState(conn);
                        break;
                    case "GET_EVENT_METADATA":
                        System.out.println("[WS] Received GET_EVENT_METADATA request from client");
                        try {
                            sendEventMetadata(conn);
                        } catch (Exception ex) {
                            System.err.println("[WS] Error sending metadata: " + ex.getMessage());
                            ex.printStackTrace();
                        }
                        break;
                    default:
                        System.out.println("[WS] Unknown action: " + action);
                }
            }
        } catch (Exception e) {
            System.err.println("[WS] Error processing message: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("[WS] Connection error: " + ex.getMessage());
    }
    
    @Override
    public void onStart() {
        // Server started - machine already initialized in constructor
        // Periodic updates disabled - updates only sent on connect and state changes
    }
    
    // StateMachineListener implementation
    @Override
    public void onRegistryCreate(String machineId) {
        broadcastEvent("REGISTRY_CREATE", machineId, null, null, null, null);
    }
    
    @Override
    public void onRegistryRehydrate(String machineId) {
        broadcastEvent("REGISTRY_REHYDRATE", machineId, null, null, null, null);
    }
    
    @Override
    public void onRegistryRemove(String machineId) {
        broadcastEvent("REGISTRY_REMOVE", machineId, null, null, null, null);
    }
    
    @Override
    public void onStateMachineEvent(String machineId, String oldState, String newState, 
                                   CallContext contextEntity, Void volatileContext) {
        broadcastEvent("STATE_CHANGE", machineId, oldState, newState, contextEntity, volatileContext);
    }
    
    private void broadcastEvent(String eventType, String machineId, String oldState, 
                               String newState, CallContext contextEntity, Void volatileContext) {
        JsonObject event = new JsonObject();
        event.addProperty("type", eventType);
        event.addProperty("timestamp", LocalDateTime.now().format(TIME_FORMAT));
        event.addProperty("machineId", machineId);
        
        // Include the event name if this is a state change
        if ("STATE_CHANGE".equals(eventType) && lastEventName != null) {
            event.addProperty("eventName", lastEventName);
            // Don't reset here - it will be reset after the state transition is complete
        }
        
        // Send both formats for compatibility
        if (oldState != null) {
            event.addProperty("oldState", oldState);
            event.addProperty("stateBefore", oldState); // For UI compatibility
        }
        if (newState != null) {
            event.addProperty("newState", newState);
            event.addProperty("stateAfter", newState); // For UI compatibility
        }
        
        if (contextEntity != null) {
            JsonObject contextJson = new JsonObject();
            contextJson.addProperty("callId", contextEntity.getCallId());
            contextJson.addProperty("fromNumber", contextEntity.getFromNumber());
            contextJson.addProperty("toNumber", contextEntity.getToNumber());
            contextJson.addProperty("callDirection", contextEntity.getCallDirection());
            contextJson.addProperty("callStatus", contextEntity.getCallStatus());
            contextJson.addProperty("ringCount", contextEntity.getRingCount());
            contextJson.addProperty("recordingEnabled", contextEntity.isRecordingEnabled());
            contextJson.addProperty("currentState", contextEntity.getCurrentState());
            event.add("context", contextJson);
        }
        
        String message = gson.toJson(event);
        broadcastMessage(message);
    }
    
    private void broadcastMessage(String message) {
        for (WebSocket client : connectedClients) {
            if (client.isOpen()) {
                client.send(message);
            }
        }
    }
    
    private void sendInitialState(WebSocket conn) {
        sendCurrentState(conn);
    }
    
    private void sendCurrentState(WebSocket conn) {
        if (machine != null && context != null) {
            JsonObject state = new JsonObject();
            state.addProperty("type", "CURRENT_STATE");
            state.addProperty("timestamp", LocalDateTime.now().format(TIME_FORMAT));
            state.addProperty("machineId", MACHINE_ID);
            state.addProperty("currentState", machine.getCurrentState());
            
            JsonObject contextJson = new JsonObject();
            contextJson.addProperty("callId", context.getCallId());
            contextJson.addProperty("fromNumber", context.getFromNumber());
            contextJson.addProperty("toNumber", context.getToNumber());
            contextJson.addProperty("callDirection", context.getCallDirection());
            contextJson.addProperty("callStatus", context.getCallStatus());
            contextJson.addProperty("ringCount", context.getRingCount());
            contextJson.addProperty("recordingEnabled", context.isRecordingEnabled());
            state.add("context", contextJson);
            
            conn.send(gson.toJson(state));
        }
    }
    
    // Periodic updates disabled - not needed
    // Updates are sent only on:
    // 1. Client connection (initial state)
    // 2. State machine events (via listener)
    private void startPeriodicUpdates() {
        // Disabled - no periodic updates
    }
    
    private void initializeStateMachine() {
        System.out.println("[Init] Initializing state machine...");
        registerEventTypes();
        
        context = new CallContext(MACHINE_ID, "+1-555-0001", "+1-555-0002");
        
        // Temporarily suppress verbose StateMachine output
        java.io.PrintStream originalOut = System.out;
        java.io.PrintStream dummyOut = new java.io.PrintStream(new java.io.OutputStream() {
            public void write(int b) {} // Discard output
        });
        
        try {
            // Redirect System.out during machine creation
            System.setOut(dummyOut);
            
            machine = FluentStateMachineBuilder.<CallContext, Void>create(MACHINE_ID)
                .initialState(CallState.IDLE)
                
                .state(CallState.IDLE)
                    .on(IncomingCall.class).to(CallState.RINGING)
                    .done()
                    
                .state(CallState.RINGING)
                    .timeout(30, com.telcobright.statemachine.timeout.TimeUnit.SECONDS, CallState.IDLE.name())
                    .on(Answer.class).to(CallState.CONNECTED)
                    .on(Hangup.class).to(CallState.IDLE)
                    .stay(SessionProgress.class, (m, e) -> {
                        context.setRingCount(context.getRingCount() + 1);
                    })
                    .done()
                    
                .state(CallState.CONNECTED)
                    .on(Hangup.class).to(CallState.IDLE)
                    .done()
                    
                .build();
            
            machine.setPersistingEntity(context);
            
            // Set up callback to notify registry on ALL state transitions (including same-state transitions)
            // Track state changes properly - currentState is already updated when callback fires
            final String[] previousState = {machine.getCurrentState()};
            machine.setOnStateTransition(newState -> {
                String oldState = previousState[0];
                
                // Check if this is a timeout transition
                if ("IDLE".equals(newState) && "RINGING".equals(oldState) && lastEventName == null) {
                    lastEventName = "Timeout";
                }
                
                // Always notify registry about transitions, even same-state transitions (e.g., SESSION_PROGRESS)
                // This ensures events like SESSION_PROGRESS in RINGING state are recorded
                registry.notifyStateMachineEvent(MACHINE_ID, oldState, newState, context, null);
                
                // Only update previous state and restart timer if state actually changed
                if (!oldState.equals(newState)) {
                    previousState[0] = newState;
                    
                    // Start countdown timer if debug mode is enabled and state has timeout
                    if (debugMode) {
                        startCountdownTimer(newState);
                    }
                }
                
                // Reset event name after the notification is complete
                lastEventName = null;
            });
            
            registry.register(MACHINE_ID, machine);
            machine.start();
            
        } finally {
            // Restore original System.out
            System.setOut(originalOut);
        }
        
        System.out.println("[CallMachine] Initialized with ID: " + MACHINE_ID);
        System.out.println("[CallMachine] Machine ready with events: INCOMING_CALL, ANSWER, HANGUP, SESSION_PROGRESS, REJECT, BUSY, TIMEOUT");
        System.out.println("[CallMachine] Debug mode ENABLED - countdown timer will show for states with timeout");
    }
    
    private void registerEventTypes() {
        EventTypeRegistry.register(IncomingCall.class, "INCOMING_CALL");
        EventTypeRegistry.register(Answer.class, "ANSWER");
        EventTypeRegistry.register(Hangup.class, "HANGUP");
        EventTypeRegistry.register(SessionProgress.class, "SESSION_PROGRESS");
        EventTypeRegistry.register(Reject.class, "REJECT");
        // Note: Busy and Timeout events are in the state machine but not all may have classes
        System.out.println("[Init] Registered event types in EventTypeRegistry");
    }
    
    private void sendIncomingCall() {
        sendIncomingCall("+1-555-9999");
    }
    
    private void sendIncomingCall(String callerNumber) {
        if (machine != null) {
            lastEventName = "IncomingCall";
            machine.fire(new IncomingCall(callerNumber));
            // State change notification now handled by onStateTransition callback
            System.out.println("[Event] INCOMING_CALL -> caller: " + callerNumber);
            // Event name will be reset after the state transition completes
        }
    }
    
    private void sendAnswer() {
        if (machine != null) {
            lastEventName = "Answer";
            machine.fire(new Answer());
            context.setConnectTime(LocalDateTime.now());
            // State change notification now handled by onStateTransition callback
            System.out.println("[Event] ANSWER");
            // Event name will be reset after the state transition completes
        }
    }
    
    private void sendHangup() {
        if (machine != null) {
            lastEventName = "Hangup";
            machine.fire(new Hangup());
            // State change notification now handled by onStateTransition callback
            System.out.println("[Event] HANGUP");
            // Event name will be reset after the state transition completes
        }
    }
    
    private void sendSessionProgress() {
        sendSessionProgress("v=0", context.getRingCount() + 1);
    }
    
    private void sendSessionProgress(String sdp, int ringNumber) {
        if (machine != null) {
            lastEventName = "SessionProgress";
            String currentState = machine.getCurrentState();
            machine.fire(new SessionProgress(sdp, ringNumber));
            
            // For "stay" transitions (same-state), manually notify registry since onStateTransition won't fire
            if (currentState.equals(machine.getCurrentState())) {
                // This is a same-state transition (stay), manually broadcast it
                registry.notifyStateMachineEvent(MACHINE_ID, currentState, currentState, context, null);
            }
            
            System.out.println("[Event] SESSION_PROGRESS -> ring: " + ringNumber);
            // Event name will be reset after the state transition completes
            lastEventName = null;
        }
    }
    
    private void startCountdownTimer(String state) {
        // Cancel any existing countdown timer
        if (countdownTimer != null && !countdownTimer.isDone()) {
            countdownTimer.cancel(false);
            System.out.println("[Countdown] Cancelled existing timer");
        }
        
        // Check if the state has a timeout configured
        int timeoutSeconds = 0;
        if ("RINGING".equals(state)) {
            timeoutSeconds = 30; // RINGING has 30 second timeout
        }
        // Add other states with timeouts here as needed
        
        if (timeoutSeconds > 0) {
            currentCountdown = timeoutSeconds;
            countdownState = state;
            System.out.println("[Countdown] Starting countdown for state: " + state + " with " + timeoutSeconds + " seconds");
            
            // Send initial countdown
            broadcastCountdown();
            
            // Start countdown timer that updates every second
            countdownTimer = scheduler.scheduleAtFixedRate(() -> {
                currentCountdown--;
                if (currentCountdown > 0) {
                    broadcastCountdown();
                } else {
                    // Countdown finished, cancel timer
                    countdownTimer.cancel(false);
                    countdownState = null;
                    currentCountdown = 0;
                    System.out.println("[Countdown] Timer finished for state: " + state);
                }
            }, 1, 1, TimeUnit.SECONDS);
        } else {
            // State has no timeout, clear countdown
            currentCountdown = 0;
            countdownState = null;
            broadcastCountdown();
            System.out.println("[Countdown] No timeout for state: " + state);
        }
    }
    
    private void broadcastCountdown() {
        JsonObject countdown = new JsonObject();
        countdown.addProperty("type", "TIMEOUT_COUNTDOWN");
        countdown.addProperty("timestamp", LocalDateTime.now().format(TIME_FORMAT));
        countdown.addProperty("state", countdownState);
        countdown.addProperty("remainingSeconds", currentCountdown);
        countdown.addProperty("debugMode", debugMode);
        
        String message = gson.toJson(countdown);
        
        // Debug logging
        if (currentCountdown % 5 == 0 || currentCountdown <= 5) {
            System.out.println("[Countdown] Broadcasting: " + currentCountdown + "s remaining for state: " + countdownState);
        }
        
        for (WebSocket client : connectedClients) {
            if (client.isOpen()) {
                client.send(message);
            }
        }
    }
    
    private void startUIServer() {
        try {
            // Check if port is available before trying to bind
            if (!isPortAvailable(uiPort)) {
                System.err.println("[UI] Port " + uiPort + " is already in use, skipping UI server");
                return;
            }
            
            System.out.println("\n======================================");
            System.out.println("  Starting Monitoring Server on port " + uiPort + "...");
            System.out.println("======================================");
            
            // Instantiate and start SimpleMonitoringServer
            monitoringServer = new SimpleMonitoringServer();
            monitoringServer.setWebSocketPort(getPort()); // Set the actual WebSocket port
            monitoringServer.start(uiPort);
            
            // Wait a moment for server to start
            Thread.sleep(1000);
            
            System.out.println("\n======================================");
            System.out.println("  Monitoring UI Started Successfully!");
            System.out.println("======================================");
            System.out.println("");
            System.out.println("🚀 Open your browser and go to:");
            System.out.println("  http://localhost:" + uiPort);
            System.out.println("");
            System.out.println("Features:");
            System.out.println("  📸 Snapshot Mode - View historical transitions from database");
            System.out.println("  🔴 Live Mode - Connect to WebSocket server (running on port " + getPort() + ")");
            System.out.println("");
            System.out.println("Note: Live Mode is automatically available as WebSocket server is running");
            System.out.println("");
        } catch (Exception e) {
            System.err.println("[UI] Failed to start monitoring UI server: " + e.getMessage());
            // UI is optional, don't fail the whole server
        }
    }
    
    public void shutdown() {
        System.out.println("\n[WS] Shutting down servers...");
        
        // Shutdown monitoring server
        if (monitoringServer != null) {
            System.out.println("[UI] Stopping monitoring server...");
            // SimpleMonitoringServer doesn't have a stop method, but we can try to stop its HttpServer
            // The server will be stopped when the JVM exits
        }
        
        try {
            // Stop accepting new connections
            this.stop(1000);
            
            // Close all existing connections
            for (WebSocket client : connectedClients) {
                if (client.isOpen()) {
                    client.close();
                }
            }
            connectedClients.clear();
            
            // Shutdown scheduler
            scheduler.shutdown();
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            
            // Remove from registry
            if (registry != null) {
                registry.removeMachine(MACHINE_ID);
            }
            
            System.out.println("[WS] Servers stopped successfully");
        } catch (Exception e) {
            System.err.println("[WS] Error during shutdown: " + e.getMessage());
            try {
                this.stop(0); // Force stop
            } catch (Exception ex) {
                // Ignore
            }
        }
    }
    
    /**
     * Check if a port is available
     */
    private static boolean isPortAvailable(int port) {
        try (java.net.ServerSocket serverSocket = new java.net.ServerSocket()) {
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress("0.0.0.0", port));
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * Try to clean up existing processes on the specified ports
     */
    private static void cleanupExistingProcesses(int wsPort, int uiPort) {
        cleanupExistingProcesses(wsPort, uiPort, false);
    }
    
    /**
     * Try to clean up existing processes on the specified ports
     */
    private static void cleanupExistingProcesses(int wsPort, int uiPort, boolean forceAggressive) {
        System.out.println("[Cleanup] Checking for existing processes...");
        
        // Check if we're in debug mode (likely from IDE)
        boolean isDebugMode = java.lang.management.ManagementFactory.getRuntimeMXBean()
            .getInputArguments().toString().contains("-agentlib:jdwp") || forceAggressive;
        
        if (isDebugMode) {
            System.out.println("[Cleanup] Performing aggressive cleanup" + 
                (forceAggressive ? " (forced)" : " (debug mode)"));
        }
        
        // Try to kill processes on WebSocket port
        if (!isPortAvailable(wsPort)) {
            System.out.println("[Cleanup] Port " + wsPort + " is in use, attempting cleanup...");
            
            boolean cleaned = false;
            int attempts = isDebugMode ? 3 : 2; // More attempts in debug mode
            
            for (int attempt = 1; attempt <= attempts && !cleaned; attempt++) {
                System.out.println("[Cleanup] Attempt " + attempt + " of " + attempts);
                
                // Approach 1: Kill Java processes on the port (common in debug scenarios)
                try {
                    // First get the PID using lsof
                    ProcessBuilder getPid = new ProcessBuilder("sh", "-c", 
                        "lsof -ti:" + wsPort + " | head -1");
                    Process pidProcess = getPid.start();
                    
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(pidProcess.getInputStream()));
                    String pid = reader.readLine();
                    
                    if (pid != null && !pid.isEmpty() && !pid.equals("2")) {
                        System.out.println("[Cleanup] Found process PID: " + pid);
                        
                        // Check if it's a Java process
                        ProcessBuilder checkJava = new ProcessBuilder("sh", "-c",
                            "ps -p " + pid + " -o comm= | grep -q java && echo 'java'");
                        Process checkProcess = checkJava.start();
                        
                        java.io.BufferedReader checkReader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(checkProcess.getInputStream()));
                        String isJava = checkReader.readLine();
                        
                        if ("java".equals(isJava)) {
                            System.out.println("[Cleanup] Process is Java - killing it");
                            ProcessBuilder killProcess = new ProcessBuilder("sh", "-c",
                                "kill -9 " + pid);
                            killProcess.start().waitFor(1, TimeUnit.SECONDS);
                        }
                    }
                } catch (Exception e) {
                    // Ignore and try next approach
                }
                
                // Approach 2: Try with lsof and xargs
                try {
                    ProcessBuilder pb = new ProcessBuilder("sh", "-c", 
                        "lsof -ti:" + wsPort + " | grep -v '^2$' | xargs -r kill -9 2>/dev/null");
                    Process p = pb.start();
                    p.waitFor(1, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // Ignore
                }
                
                // Approach 3: Try with fuser
                try {
                    ProcessBuilder pb = new ProcessBuilder("sh", "-c", 
                        "fuser -k " + wsPort + "/tcp 2>/dev/null");
                    Process p = pb.start();
                    p.waitFor(1, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // Ignore
                }
                
                // Approach 4: In debug mode, try to force kill all Java processes on the port
                if (isDebugMode && attempt == attempts) {
                    try {
                        System.out.println("[Cleanup] Final attempt - force killing all processes on port");
                        ProcessBuilder pb = new ProcessBuilder("sh", "-c",
                            "for pid in $(lsof -ti:" + wsPort + "); do " +
                            "  if [ \"$pid\" != \"2\" ]; then " +
                            "    kill -9 $pid 2>/dev/null || true; " +
                            "  fi; " +
                            "done");
                        pb.start().waitFor(2, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        // Ignore
                    }
                }
                
                // Wait a bit for port to be released
                try {
                    Thread.sleep(attempt * 500); // Progressively longer waits
                } catch (InterruptedException e) {
                    // Ignore
                }
                
                cleaned = isPortAvailable(wsPort);
                if (cleaned) {
                    System.out.println("[Cleanup] Successfully cleaned up port " + wsPort);
                    break;
                }
            }
            
            if (!cleaned) {
                System.out.println("[Cleanup] Could not clean up port " + wsPort);
                if (isDebugMode) {
                    System.out.println("[Cleanup] Debug mode detected - you may have orphaned debug processes");
                    System.out.println("[Cleanup] Try stopping all debug sessions in your IDE");
                }
            }
        } else {
            System.out.println("[Cleanup] Port " + wsPort + " is available");
        }
        
        // Try to kill processes on UI port
        if (!isPortAvailable(uiPort)) {
            System.out.println("[Cleanup] Port " + uiPort + " is in use, attempting cleanup...");
            
            boolean cleaned = false;
            
            // Similar aggressive cleanup for UI port
            for (int attempt = 1; attempt <= 2 && !cleaned; attempt++) {
                try {
                    ProcessBuilder pb = new ProcessBuilder("sh", "-c", 
                        "lsof -ti:" + uiPort + " | grep -v '^2$' | xargs -r kill -9 2>/dev/null");
                    Process p = pb.start();
                    p.waitFor(1, TimeUnit.SECONDS);
                    Thread.sleep(500);
                } catch (Exception e) {
                    // Ignore
                }
                
                cleaned = isPortAvailable(uiPort);
            }
            
            if (cleaned) {
                System.out.println("[Cleanup] Successfully cleaned up port " + uiPort);
            } else {
                System.out.println("[Cleanup] Could not clean up port " + uiPort);
            }
        } else {
            System.out.println("[Cleanup] Port " + uiPort + " is available");
        }
    }
    
    /**
     * Send event metadata to a client
     */
    private void sendEventMetadata(WebSocket conn) {
        if (machine == null) {
            System.err.println("[WS] Cannot send metadata - machine not initialized");
            return;
        }
        
        JsonObject metadata = null;
        try {
            metadata = registry.extractMachineEventMetadata(machine);
            System.out.println("[WS] Extracted metadata for machine: " + metadata.get("machineId"));
            System.out.println("[WS] Metadata has " + metadata.getAsJsonArray("supportedEvents").size() + " events");
        } catch (Exception e) {
            System.err.println("[WS] Error extracting metadata: " + e.getMessage());
            e.printStackTrace();
            
            // Send error response
            JsonObject error = new JsonObject();
            error.addProperty("type", "ERROR");
            error.addProperty("message", "Failed to extract metadata: " + e.getMessage());
            conn.send(gson.toJson(error));
            return;
        }
        
        // Wrap in EVENT_METADATA_UPDATE message
        JsonObject update = new JsonObject();
        update.addProperty("type", "EVENT_METADATA_UPDATE");
        update.addProperty("timestamp", System.currentTimeMillis());
        
        // Add machine metadata as an array
        com.google.gson.JsonArray machines = new com.google.gson.JsonArray();
        machines.add(metadata);
        update.add("machines", machines);
        
        conn.send(gson.toJson(update));
        System.out.println("[WS] Sent event metadata with " + metadata.getAsJsonArray("supportedEvents").size() + " events");
    }
    
    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        boolean skipCleanup = false;
        boolean forceCleanup = false;
        
        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            if ("--skip-cleanup".equals(args[i])) {
                skipCleanup = true;
            } else if ("--force-cleanup".equals(args[i])) {
                forceCleanup = true;
            } else if ("--help".equals(args[i]) || "-h".equals(args[i])) {
                System.out.println("Usage: CallMachineRunnerWithWebServer [options] [port]");
                System.out.println("Options:");
                System.out.println("  --skip-cleanup   Skip automatic port cleanup");
                System.out.println("  --force-cleanup  Force aggressive cleanup");
                System.out.println("  --help, -h       Show this help message");
                System.out.println("  [port]           WebSocket port (default: 9999)");
                System.exit(0);
            } else {
                try {
                    port = Integer.parseInt(args[i]);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid port number: " + args[i] + ", using default: " + DEFAULT_PORT);
                }
            }
        }
        
        // Check if we're in debug mode
        boolean isDebugMode = java.lang.management.ManagementFactory.getRuntimeMXBean()
            .getInputArguments().toString().contains("-agentlib:jdwp");
        
        if (isDebugMode) {
            System.out.println("\n[Debug] Running in debug mode - automatic cleanup enabled");
        }
        
        // Try to clean up any existing processes on our ports (unless skipped)
        if (!skipCleanup) {
            if (forceCleanup) {
                System.out.println("[Main] Force cleanup requested");
            }
            cleanupExistingProcesses(port, DEFAULT_UI_PORT, forceCleanup);
        } else {
            System.out.println("[Main] Skipping port cleanup as requested");
        }
        
        // Check if ports are available
        if (!isPortAvailable(port)) {
            System.err.println("\n[ERROR] WebSocket port " + port + " is still in use!");
            
            if (isDebugMode) {
                System.err.println("\n[Debug Mode Tips]:");
                System.err.println("  • Stop all debug sessions in your IDE");
                System.err.println("  • Check for orphaned Java processes");
                System.err.println("  • Try: ps aux | grep java | grep " + port);
            }
            
            System.err.println("\nOptions:");
            System.err.println("  1. Manual kill: lsof -ti:" + port + " | xargs kill -9");
            System.err.println("  2. Force cleanup: add --force-cleanup flag");
            System.err.println("  3. Use different port: add port number as argument");
            System.err.println("  4. Skip cleanup: add --skip-cleanup flag");
            
            // In debug mode, provide a helper script
            if (isDebugMode) {
                System.err.println("\nQuick fix command:");
                System.err.println("  kill -9 $(lsof -ti:" + port + ") 2>/dev/null");
            }
            
            System.exit(1);
        }
        
        if (!isPortAvailable(DEFAULT_UI_PORT)) {
            System.err.println("\n[WARNING] UI port " + DEFAULT_UI_PORT + " is in use!");
            System.err.println("The UI server will be skipped, but WebSocket will still work.");
            // Don't exit - UI is optional
        }
        
        CallMachineRunnerWithWebServer server = new CallMachineRunnerWithWebServer(port);
        
        // Register shutdown hook BEFORE starting the server
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Shutdown] Cleaning up...");
            server.shutdown();
            // Give it time to clean up
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                // Ignore
            }
        }));
        
        // Now start the server
        server.start();
        
        System.out.println("\n==========================================");
        System.out.println("   CallMachine Server with Live Monitor");
        System.out.println("==========================================");
        System.out.println("🌐 Monitoring UI: http://localhost:" + DEFAULT_UI_PORT);
        System.out.println("🔌 WebSocket API: ws://localhost:" + port);
        System.out.println("==========================================");
        System.out.println("Press Ctrl+C to stop\n");
        
        // Keep the main thread alive
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("\n[Main] Interrupted, shutting down...");
            server.shutdown();
        }
    }
}