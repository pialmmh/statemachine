package com.telcobright.statemachine.websocket;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.events.EventTypeRegistry;
import com.telcobright.statemachine.monitoring.web.SimpleMonitoringServer;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * Generic WebSocket-based state machine runner that can work with any state machine
 * @param <TPersistingEntity> The type of the persisting entity
 * @param <TContext> The type of the volatile context
 */
public abstract class GenericWebSocketStateMachineRunner<TPersistingEntity extends StateMachineContextEntity<?>, TContext> 
        extends WebSocketServer 
        implements StateMachineListener<TPersistingEntity, TContext> {
    
    protected static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    
    protected final String machineId;
    protected final StateMachineRegistry registry;
    protected GenericStateMachine<TPersistingEntity, TContext> machine;
    protected TPersistingEntity persistingEntity;
    protected TContext volatileContext;
    
    protected final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    protected final Gson gson;
    
    // Track connected clients
    protected final Set<WebSocket> connectedClients = ConcurrentHashMap.newKeySet();
    
    // Debug mode and countdown timer
    protected boolean debugMode = true;
    protected ScheduledFuture<?> countdownTimer;
    protected int currentCountdown = 0;
    protected String countdownState = null;
    
    // Track last event for display
    protected String lastEventName = null;
    
    // Optional UI server
    protected SimpleMonitoringServer monitoringServer;
    protected final int uiPort;
    
    /**
     * Constructor with WebSocket and UI ports
     */
    public GenericWebSocketStateMachineRunner(int wsPort, int uiPort, String machineId) {
        super(new InetSocketAddress(wsPort));
        this.uiPort = uiPort;
        this.machineId = machineId;
        
        this.registry = createRegistry();
        this.registry.addListener(this);
        
        // Configure Gson with custom serializer
        this.gson = createGson();
        
        // Initialize state machine
        initializeStateMachine();
        
        // Start UI server if needed
        if (shouldStartUIServer()) {
            startUIServer();
        }
    }
    
    /**
     * Create the registry instance - can be overridden for custom configuration
     */
    protected StateMachineRegistry createRegistry() {
        StateMachineRegistry reg = new StateMachineRegistry();
        // Enable live debugging by default
        reg.enableLiveDebug(getPort());
        return reg;
    }
    
    /**
     * Create Gson instance with custom configuration
     */
    protected Gson createGson() {
        return new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalDateTime.class, (JsonSerializer<LocalDateTime>) 
                (src, typeOfSrc, context) -> {
                    return context.serialize(src.format(TIME_FORMAT));
                })
            .create();
    }
    
    /**
     * Initialize the state machine - must be implemented by subclasses
     */
    protected abstract void initializeStateMachine();
    
    /**
     * Create the state machine instance
     */
    protected abstract GenericStateMachine<TPersistingEntity, TContext> createStateMachine();
    
    /**
     * Create the persisting entity
     */
    protected abstract TPersistingEntity createPersistingEntity();
    
    /**
     * Create the volatile context (if needed)
     */
    protected TContext createVolatileContext() {
        return null; // Default implementation - override if needed
    }
    
    /**
     * Register event types - override to register specific events
     */
    protected abstract void registerEventTypes();
    
    /**
     * Handle incoming WebSocket messages - must be implemented by subclasses
     */
    protected abstract void handleMessage(WebSocket conn, JsonObject request);
    
    /**
     * Get supported event metadata for the UI
     */
    protected abstract JsonObject getEventMetadata();
    
    /**
     * Check if UI server should be started
     */
    protected boolean shouldStartUIServer() {
        return debugMode && uiPort > 0;
    }
    
    /**
     * Enable/disable debug mode
     */
    public GenericWebSocketStateMachineRunner<TPersistingEntity, TContext> withDebugMode(boolean enabled) {
        this.debugMode = enabled;
        System.out.println("[Config] Debug mode " + (enabled ? "enabled" : "disabled"));
        return this;
    }
    
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        connectedClients.add(conn);
        System.out.println("[WS] Client connected: " + conn.getRemoteSocketAddress());
        System.out.println("[WS] Total connected clients: " + connectedClients.size());
        
        // Send initial state
        sendInitialState(conn);
        
        // Send connection test
        JsonObject test = new JsonObject();
        test.addProperty("type", "CONNECTION_TEST");
        test.addProperty("message", "WebSocket connection established");
        conn.send(gson.toJson(test));
    }
    
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        connectedClients.remove(conn);
        System.out.println("[WS] Client disconnected: " + conn.getRemoteSocketAddress());
    }
    
    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("[WS] Message received: " + message);
        
        // Handle metadata request
        if (message.contains("GET_EVENT_METADATA")) {
            sendEventMetadata(conn);
            return;
        }
        
        try {
            JsonObject request = gson.fromJson(message, JsonObject.class);
            
            // Handle GET_STATE request
            if (request.has("action") && "GET_STATE".equals(request.get("action").getAsString())) {
                sendCurrentState(conn);
                return;
            }
            
            // Delegate to subclass for specific message handling
            handleMessage(conn, request);
            
        } catch (Exception e) {
            System.err.println("[WS] Error processing message: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("[WS] Error: " + ex.getMessage());
        ex.printStackTrace();
    }
    
    @Override
    public void onStart() {
        System.out.println("[WS] WebSocket server started on port " + getPort());
        startPeriodicUpdates();
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
                                   TPersistingEntity contextEntity, TContext volatileContext) {
        broadcastEvent("STATE_CHANGE", machineId, oldState, newState, contextEntity, volatileContext);
    }
    
    /**
     * Broadcast an event to all connected clients
     */
    protected void broadcastEvent(String eventType, String machineId, String oldState, 
                                 String newState, TPersistingEntity contextEntity, TContext volatileContext) {
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
        if (lastEventName != null) {
            event.addProperty("eventName", lastEventName);
        }
        
        // Add context if available
        if (contextEntity != null) {
            try {
                JsonObject contextJson = gson.toJsonTree(contextEntity).getAsJsonObject();
                event.add("context", contextJson);
            } catch (Exception e) {
                System.err.println("[WS] Error serializing context: " + e.getMessage());
            }
        }
        
        // Add countdown if active
        if (debugMode && currentCountdown > 0 && countdownState != null) {
            JsonObject countdown = new JsonObject();
            countdown.addProperty("state", countdownState);
            countdown.addProperty("remaining", currentCountdown);
            event.add("countdown", countdown);
        }
        
        broadcastMessage(gson.toJson(event));
    }
    
    /**
     * Broadcast a message to all connected clients
     */
    protected void broadcastMessage(String message) {
        for (WebSocket client : connectedClients) {
            if (client.isOpen()) {
                client.send(message);
            }
        }
    }
    
    /**
     * Send initial state to a newly connected client
     */
    protected void sendInitialState(WebSocket conn) {
        sendCurrentState(conn);
    }
    
    /**
     * Send current state to a client
     */
    protected void sendCurrentState(WebSocket conn) {
        if (machine == null) {
            JsonObject response = new JsonObject();
            response.addProperty("type", "STATE");
            response.addProperty("state", "NOT_INITIALIZED");
            response.addProperty("timestamp", LocalDateTime.now().format(TIME_FORMAT));
            conn.send(gson.toJson(response));
            return;
        }
        
        JsonObject response = new JsonObject();
        response.addProperty("type", "STATE");
        response.addProperty("state", machine.getCurrentState());
        response.addProperty("timestamp", LocalDateTime.now().format(TIME_FORMAT));
        response.addProperty("machineId", machineId);
        
        // Add context
        if (persistingEntity != null) {
            try {
                JsonObject contextJson = gson.toJsonTree(persistingEntity).getAsJsonObject();
                response.add("context", contextJson);
            } catch (Exception e) {
                System.err.println("[WS] Error serializing context: " + e.getMessage());
            }
        }
        
        conn.send(gson.toJson(response));
    }
    
    /**
     * Send event metadata to client
     */
    protected void sendEventMetadata(WebSocket conn) {
        JsonObject metadata = getEventMetadata();
        metadata.addProperty("type", "EVENT_METADATA");
        metadata.addProperty("timestamp", LocalDateTime.now().format(TIME_FORMAT));
        conn.send(gson.toJson(metadata));
    }
    
    /**
     * Start periodic state updates
     */
    protected void startPeriodicUpdates() {
        scheduler.scheduleAtFixedRate(this::sendPeriodicUpdate, 5, 5, TimeUnit.SECONDS);
    }
    
    /**
     * Send periodic update to all clients
     */
    protected void sendPeriodicUpdate() {
        if (machine != null) {
            JsonObject update = new JsonObject();
            update.addProperty("type", "PERIODIC_UPDATE");
            update.addProperty("timestamp", LocalDateTime.now().format(TIME_FORMAT));
            update.addProperty("state", machine.getCurrentState());
            update.addProperty("machineId", machineId);
            broadcastMessage(gson.toJson(update));
        }
    }
    
    /**
     * Start countdown timer for states with timeouts
     */
    protected void startCountdownTimer(String state) {
        // Cancel existing timer if any
        if (countdownTimer != null && !countdownTimer.isDone()) {
            countdownTimer.cancel(false);
        }
        
        // Get timeout duration for the state (override this method in subclass)
        int timeoutSeconds = getTimeoutForState(state);
        if (timeoutSeconds <= 0) {
            currentCountdown = 0;
            countdownState = null;
            return;
        }
        
        currentCountdown = timeoutSeconds;
        countdownState = state;
        
        // Start countdown
        countdownTimer = scheduler.scheduleAtFixedRate(() -> {
            currentCountdown--;
            broadcastCountdown();
            
            if (currentCountdown <= 0) {
                countdownTimer.cancel(false);
                countdownState = null;
            }
        }, 1, 1, TimeUnit.SECONDS);
    }
    
    /**
     * Get timeout duration for a state (in seconds)
     * Override this method to provide state-specific timeouts
     */
    protected int getTimeoutForState(String state) {
        return 0; // Default: no timeout
    }
    
    /**
     * Broadcast countdown update
     */
    protected void broadcastCountdown() {
        if (countdownState != null && currentCountdown > 0) {
            JsonObject countdown = new JsonObject();
            countdown.addProperty("type", "COUNTDOWN");
            countdown.addProperty("state", countdownState);
            countdown.addProperty("remaining", currentCountdown);
            countdown.addProperty("timestamp", LocalDateTime.now().format(TIME_FORMAT));
            broadcastMessage(gson.toJson(countdown));
        }
    }
    
    /**
     * Start the UI server
     */
    protected void startUIServer() {
        if (monitoringServer == null) {
            try {
                monitoringServer = new SimpleMonitoringServer();
                monitoringServer.start(uiPort);
                System.out.println("[UI] Live Mode UI server started at http://localhost:" + uiPort);
            } catch (Exception e) {
                System.err.println("[UI] Failed to start UI server: " + e.getMessage());
            }
        }
    }
    
    /**
     * Stop the runner and cleanup resources
     */
    public void shutdown() {
        try {
            // Stop countdown timer
            if (countdownTimer != null) {
                countdownTimer.cancel(false);
            }
            
            // Stop scheduler
            scheduler.shutdown();
            
            // Stop UI server
            if (monitoringServer != null) {
                monitoringServer.stop();
            }
            
            // Stop WebSocket server
            this.stop();
            
            // Shutdown registry
            if (registry != null) {
                registry.shutdown();
            }
            
            System.out.println("[Shutdown] All services stopped");
        } catch (Exception e) {
            System.err.println("[Shutdown] Error during shutdown: " + e.getMessage());
        }
    }
}