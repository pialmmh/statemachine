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

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * State Machine runner with WebSocket server for real-time monitoring
 */
public class StateMachineRunnerWithWebServer extends WebSocketServer 
        implements StateMachineListener<CallContext, Void> {
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int DEFAULT_PORT = 9999;
    private static final String MACHINE_ID = "websocket-" + System.currentTimeMillis();
    
    private final StateMachineRegistry registry;
    private GenericStateMachine<CallContext, Void> machine;
    private CallContext context;
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Gson gson;
    
    // Track connected clients
    private final Set<WebSocket> connectedClients = ConcurrentHashMap.newKeySet();
    
    public StateMachineRunnerWithWebServer() {
        this(DEFAULT_PORT);
    }
    
    public StateMachineRunnerWithWebServer(int port) {
        super(new InetSocketAddress(port));
        
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
            
        System.out.println("WebSocket server configured on port: " + port);
    }
    
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        connectedClients.add(conn);
        System.out.println("New WebSocket connection from: " + conn.getRemoteSocketAddress());
        
        // Send initial state
        sendInitialState(conn);
    }
    
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        connectedClients.remove(conn);
        System.out.println("WebSocket connection closed: " + conn.getRemoteSocketAddress());
    }
    
    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            JsonObject request = gson.fromJson(message, JsonObject.class);
            String action = request.get("action").getAsString();
            JsonObject payload = request.has("payload") ? request.getAsJsonObject("payload") : null;
            
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
                    String sdp = payload != null && payload.has("sdp") 
                        ? payload.get("sdp").getAsString() 
                        : "v=0";
                    int ringNumber = payload != null && payload.has("ringNumber") 
                        ? payload.get("ringNumber").getAsInt() 
                        : context.getRingCount() + 1;
                    sendSessionProgress(sdp, ringNumber);
                    break;
                case "GET_STATE":
                    sendCurrentState(conn);
                    break;
                default:
                    System.out.println("Unknown action: " + action);
            }
        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("WebSocket error: " + ex.getMessage());
        ex.printStackTrace();
    }
    
    @Override
    public void onStart() {
        System.out.println("WebSocket server started successfully");
        initializeStateMachine();
        // Start periodic updates only if scheduler is not shut down
        if (!scheduler.isShutdown()) {
            startPeriodicUpdates();
        }
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
        
        if (oldState != null) {
            event.addProperty("oldState", oldState);
        }
        if (newState != null) {
            event.addProperty("newState", newState);
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
    
    private void startPeriodicUpdates() {
        // Send periodic updates every 2 seconds
        scheduler.scheduleAtFixedRate(() -> {
            if (machine != null && context != null && !connectedClients.isEmpty()) {
                JsonObject update = new JsonObject();
                update.addProperty("type", "PERIODIC_UPDATE");
                update.addProperty("timestamp", LocalDateTime.now().format(TIME_FORMAT));
                update.addProperty("machineId", MACHINE_ID);
                update.addProperty("currentState", machine.getCurrentState());
                update.addProperty("isRegistered", registry.isRegistered(MACHINE_ID));
                
                JsonObject contextJson = new JsonObject();
                contextJson.addProperty("callStatus", context.getCallStatus());
                contextJson.addProperty("ringCount", context.getRingCount());
                update.add("context", contextJson);
                
                broadcastMessage(gson.toJson(update));
            }
        }, 2, 2, TimeUnit.SECONDS);
    }
    
    private void initializeStateMachine() {
        registerEventTypes();
        
        context = new CallContext(MACHINE_ID, "+1-555-0001", "+1-555-0002");
        
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
        registry.register(MACHINE_ID, machine);
        machine.start();
        
        System.out.println("State machine initialized: " + MACHINE_ID);
    }
    
    private void registerEventTypes() {
        EventTypeRegistry.register(IncomingCall.class, "INCOMING_CALL");
        EventTypeRegistry.register(Answer.class, "ANSWER");
        EventTypeRegistry.register(Hangup.class, "HANGUP");
        EventTypeRegistry.register(SessionProgress.class, "SESSION_PROGRESS");
    }
    
    private void sendIncomingCall() {
        sendIncomingCall("+1-555-9999");
    }
    
    private void sendIncomingCall(String callerNumber) {
        if (machine != null) {
            String previousState = machine.getCurrentState();
            machine.fire(new IncomingCall(callerNumber));
            String newState = machine.getCurrentState();
            if (!previousState.equals(newState)) {
                registry.notifyStateMachineEvent(MACHINE_ID, previousState, newState, context, null);
            }
            System.out.println("Fired INCOMING_CALL event with caller: " + callerNumber);
        }
    }
    
    private void sendAnswer() {
        if (machine != null) {
            String previousState = machine.getCurrentState();
            machine.fire(new Answer());
            context.setConnectTime(LocalDateTime.now());
            String newState = machine.getCurrentState();
            if (!previousState.equals(newState)) {
                registry.notifyStateMachineEvent(MACHINE_ID, previousState, newState, context, null);
            }
            System.out.println("Fired ANSWER event");
        }
    }
    
    private void sendHangup() {
        if (machine != null) {
            String previousState = machine.getCurrentState();
            machine.fire(new Hangup());
            String newState = machine.getCurrentState();
            if (!previousState.equals(newState)) {
                registry.notifyStateMachineEvent(MACHINE_ID, previousState, newState, context, null);
            }
            System.out.println("Fired HANGUP event");
        }
    }
    
    private void sendSessionProgress() {
        sendSessionProgress("v=0", context.getRingCount() + 1);
    }
    
    private void sendSessionProgress(String sdp, int ringNumber) {
        if (machine != null) {
            String previousState = machine.getCurrentState();
            machine.fire(new SessionProgress(sdp, ringNumber));
            String newState = machine.getCurrentState();
            if (!previousState.equals(newState)) {
                registry.notifyStateMachineEvent(MACHINE_ID, previousState, newState, context, null);
            }
            System.out.println("Fired SESSION_PROGRESS event with SDP: " + sdp + ", ring: " + ringNumber);
        }
    }
    
    public void shutdown() {
        try {
            scheduler.shutdown();
            scheduler.awaitTermination(1, TimeUnit.SECONDS);
            registry.removeMachine(MACHINE_ID);
            this.stop();
        } catch (Exception e) {
            System.err.println("Error during shutdown: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number, using default: " + DEFAULT_PORT);
            }
        }
        
        StateMachineRunnerWithWebServer server = new StateMachineRunnerWithWebServer(port);
        server.start();
        
        System.out.println("WebSocket server running on ws://localhost:" + port);
        System.out.println("Press Enter to stop the server...");
        
        try {
            System.in.read();
            server.shutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}