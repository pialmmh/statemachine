package com.telcobright.statemachine.websocket;

import com.telcobright.statemachine.*;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonSerializer;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.time.LocalDate;

/**
 * WebSocket server for real-time state machine monitoring
 * Automatically started by the registry when debug mode is enabled
 */
public class StateMachineWebSocketServer extends WebSocketServer 
        implements StateMachineListener<StateMachineContextEntity, Object> {
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private final Set<WebSocket> connectedClients = ConcurrentHashMap.newKeySet();
    private final Gson gson;
    private final AbstractStateMachineRegistry registry;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final WebSocketLogger wsLogger = new WebSocketLogger();
    
    public StateMachineWebSocketServer(int port, AbstractStateMachineRegistry registry) {
        super(new InetSocketAddress(port));
        this.registry = registry;
        
        // Configure Gson with custom serializer
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalDateTime.class, (JsonSerializer<LocalDateTime>) 
                (src, typeOfSrc, context) -> {
                    return context.serialize(src.format(TIME_FORMAT));
                })
            .create();
    }
    
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        connectedClients.add(conn);
        System.out.println("[WS] Client connected: " + conn.getRemoteSocketAddress());
        
        // Send complete registry and machine status on connection
        sendCompleteStatus(conn);
        
        // Also send event metadata for all machines
        registry.sendEventMetadataUpdate();
    }
    
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        connectedClients.remove(conn);
        System.out.println("[WS] Client disconnected: " + conn.getRemoteSocketAddress());
    }
    
    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            // Skip empty or malformed messages
            if (message == null || message.trim().isEmpty()) {
                return;
            }
            
            // Log incoming message
            wsLogger.logIncoming(extractMachineId(message), message);
            
            JsonObject request;
            try {
                request = gson.fromJson(message, JsonObject.class);
            } catch (Exception e) {
                // Log and skip malformed JSON
                System.err.println("[WS] Failed to parse message as JSON: " + message.substring(0, Math.min(message.length(), 100)));
                wsLogger.logSystem("Failed to parse message: " + e.getMessage());
                return;
            }
            
            // Handle messages from Live Mode UI
            if (request.has("type") && "EVENT".equals(request.get("type").getAsString())) {
                String machineId = request.has("machineId") ? request.get("machineId").getAsString() : null;
                String eventType = request.get("eventType").getAsString();
                JsonObject payload = request.has("payload") ? request.getAsJsonObject("payload") : null;
                
                System.out.println("[WS] Received event: " + eventType + " for machine: " + machineId);
                
                // Route event to appropriate machine
                if (machineId != null) {
                    routeEventToMachine(machineId, eventType, payload);
                }
                return;
            }
            
            // Handle history requests
            if (request.has("action")) {
                String action = request.get("action").getAsString();
                
                if ("GET_HISTORY".equals(action) && request.has("machineId")) {
                    String machineId = request.get("machineId").getAsString();
                    sendMachineHistory(conn, machineId);
                    return;
                }
                
                if ("GET_HISTORY_SINCE".equals(action) && request.has("machineId")) {
                    String machineId = request.get("machineId").getAsString();
                    int lastId = request.has("lastId") ? request.get("lastId").getAsInt() : 0;
                    sendMachineHistorySince(conn, machineId, lastId);
                    return;
                }
            }
            
            // Handle registry queries
            if (request.has("action")) {
                String action = request.get("action").getAsString();
                switch (action) {
                    case "GET_REGISTRY_STATE":
                        sendRegistryState(conn);
                        break;
                    case "GET_MACHINE_STATE":
                        String machineId = request.get("machineId").getAsString();
                        sendMachineState(conn, machineId);
                        break;
                    case "GET_EVENT_METADATA":
                        // Request event metadata update
                        registry.sendEventMetadataUpdate();
                        break;
                    case "GET_MACHINES":
                        // Send list of machines to the client
                        sendMachinesList(conn);
                        break;
                    case "GET_STATE":
                        // Send current state of all machines
                        sendCompleteStatus(conn);
                        break;
                    case "GET_EVENTS":
                        // Get events from EventStore
                        handleGetEvents(conn, request);
                        break;
                    case "LOG":
                        // Handle log messages from React app
                        handleLogMessage(conn, request);
                        break;
                    default:
                        System.out.println("[WS] Unknown action: " + action);
                }
            }
        } catch (Exception e) {
            System.err.println("[WS] Error processing message: " + e.getMessage());
        }
    }
    
    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("[WS] Connection error: " + ex.getMessage());
    }
    
    @Override
    public void onStart() {
        // Server started successfully
        // Periodic updates disabled - updates only on connect and events
    }
    
    private void handleLogMessage(WebSocket conn, JsonObject logData) {
        try {
            // Extract log information
            String category = logData.has("category") ? logData.get("category").getAsString() : "INFO";
            String message = logData.has("message") ? logData.get("message").getAsString() : "";
            String timestamp = logData.has("timestamp") ? logData.get("timestamp").getAsString() : "";
            
            // Get client identifier
            String clientId = conn.getRemoteSocketAddress().toString();
            
            // Format and log the message
            String logMessage = String.format("[CLIENT %s] [%s] %s", clientId, category, message);
            
            // If there's additional data, include it
            if (logData.has("data") && !logData.get("data").isJsonNull()) {
                logMessage += " | Data: " + gson.toJson(logData.get("data"));
            }
            
            // Log to our WebSocket logger
            wsLogger.logIncoming("CLIENT_LOG", logMessage);
            
        } catch (Exception e) {
            System.err.println("[WS] Error handling log message: " + e.getMessage());
        }
    }
    
    // StateMachineListener implementation
    @Override
    public void onRegistryCreate(String machineId) {
        broadcastEvent("REGISTRY_CREATE", machineId, null, null, null, null);
        // Send complete status after machine creation
        broadcastCompleteStatus();
    }
    
    @Override
    public void onRegistryRehydrate(String machineId) {
        broadcastEvent("REGISTRY_REHYDRATE", machineId, null, null, null, null);
        // Send complete status after machine rehydration
        broadcastCompleteStatus();
    }
    
    @Override
    public void onRegistryRemove(String machineId) {
        broadcastEvent("REGISTRY_REMOVE", machineId, null, null, null, null);
        // Send complete status after machine removal
        broadcastCompleteStatus();
    }
    
    @Override
    public void onStateMachineEvent(String machineId, String oldState, String newState, 
                                   StateMachineContextEntity contextEntity, Object volatileContext) {
        // Broadcast the state change event
        broadcastEvent("STATE_CHANGE", machineId, oldState, newState, contextEntity, volatileContext);
        
        // Also broadcast complete status after state change
        broadcastCompleteStatus();
    }
    
    private void broadcastEvent(String eventType, String machineId, String oldState, 
                               String newState, StateMachineContextEntity contextEntity, Object volatileContext) {
        System.out.println("[WS] Broadcasting " + eventType + " for machine " + machineId + ": " + oldState + " -> " + newState);
        
        JsonObject event = new JsonObject();
        event.addProperty("type", eventType);
        event.addProperty("timestamp", LocalDateTime.now().format(TIME_FORMAT));
        event.addProperty("machineId", machineId);
        
        if (oldState != null) {
            event.addProperty("stateBefore", oldState);
        }
        if (newState != null) {
            event.addProperty("stateAfter", newState);
        }
        
        // Try to get entry action status from the machine
        GenericStateMachine<?, ?> machine = registry.getMachine(machineId);
        if (machine != null) {
            String entryActionStatus = machine.getEntryActionStatus();
            event.addProperty("entryActionStatus", entryActionStatus);
        }
        
        if (contextEntity != null) {
            try {
                // Convert context to JSON
                JsonObject contextJson = gson.toJsonTree(contextEntity).getAsJsonObject();
                event.add("context", contextJson);
            } catch (Exception e) {
                event.addProperty("contextError", "Failed to serialize context: " + e.getMessage());
            }
        }
        
        String message = gson.toJson(event);
        broadcastMessage(message);
        
        // WebSocket events logged via MySQL history
    }
    
    private void broadcastMessage(String message) {
        wsLogger.logOutgoing("BROADCAST", message);
        for (WebSocket client : connectedClients) {
            if (client.isOpen()) {
                client.send(message);
            }
        }
    }
    
    private String extractMachineId(String message) {
        try {
            JsonObject obj = gson.fromJson(message, JsonObject.class);
            if (obj.has("machineId")) {
                return obj.get("machineId").getAsString();
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
    
    private void sendRegistryState(WebSocket conn) {
        JsonObject state = new JsonObject();
        state.addProperty("type", "REGISTRY_STATE");
        state.addProperty("timestamp", LocalDateTime.now().format(TIME_FORMAT));
        state.addProperty("debugMode", registry.isDebugEnabled());
        state.addProperty("machineCount", registry.getActiveMachineCount());
        
        // Add machine IDs
        Set<String> machineIds = registry.getActiveMachineIds();
        state.add("machineIds", gson.toJsonTree(machineIds));
        
        // Add last added and removed machines
        String lastAdded = registry.getLastAddedMachine();
        String lastRemoved = registry.getLastRemovedMachine();
        System.out.println("[WS] Sending registry state - lastAdded: " + lastAdded + ", lastRemoved: " + lastRemoved);
        state.addProperty("lastAddedMachine", lastAdded);
        state.addProperty("lastRemovedMachine", lastRemoved);
        
        conn.send(gson.toJson(state));
    }
    
    private void sendMachineState(WebSocket conn, String machineId) {
        System.out.println("[WS] GET_MACHINE_STATE request for: " + machineId);
        GenericStateMachine<?, ?> machine = registry.getMachine(machineId);
        if (machine != null) {
            JsonObject state = new JsonObject();
            state.addProperty("type", "MACHINE_STATE");
            state.addProperty("timestamp", LocalDateTime.now().format(TIME_FORMAT));
            state.addProperty("machineId", machineId);
            state.addProperty("currentState", machine.getCurrentState());
            
            // Add context if available
            if (machine.getPersistingEntity() != null) {
                try {
                    JsonObject contextJson = gson.toJsonTree(machine.getPersistingEntity()).getAsJsonObject();
                    state.add("context", contextJson);
                } catch (Exception e) {
                    state.addProperty("contextError", "Failed to serialize context: " + e.getMessage());
                }
            }
            
            System.out.println("[WS] Sending MACHINE_STATE for " + machineId + " with state: " + machine.getCurrentState());
            conn.send(gson.toJson(state));
        } else {
            System.err.println("[WS] Machine not found: " + machineId);
        }
    }
    
    private void routeEventToMachine(String machineId, String eventType, JsonObject payload) {
        // Log incoming WebSocket event
        String clientAddress = "WebSocketClient"; // You could track actual client addresses if needed
        Map<String, Object> eventPayload = payload != null ? gson.fromJson(payload, Map.class) : new HashMap<>();
        
        GenericStateMachine<?, ?> machine = registry.getMachine(machineId);
        if (machine != null) {
            System.out.println("[Event] Routing " + eventType + " to machine: " + machineId);
            
            try {
                // Create event instance based on event type
                com.telcobright.statemachine.events.StateMachineEvent event = null;
                
                // Handle CallMachine events
                if (eventType.equals("INCOMING_CALL")) {
                    String phoneNumber = payload != null && payload.has("phoneNumber") 
                        ? payload.get("phoneNumber").getAsString() 
                        : "+1-555-0000";
                    event = new com.telcobright.statemachineexamples.callmachine.events.IncomingCall(phoneNumber);
                } else if (eventType.equals("ANSWER")) {
                    event = new com.telcobright.statemachineexamples.callmachine.events.Answer();
                } else if (eventType.equals("HANGUP")) {
                    event = new com.telcobright.statemachineexamples.callmachine.events.Hangup();
                } else if (eventType.equals("SESSION_PROGRESS")) {
                    // SessionProgress requires sessionData and ringNumber parameters
                    String sessionData = payload != null && payload.has("sessionData") 
                        ? payload.get("sessionData").getAsString() 
                        : "v=0";
                    int ringNumber = payload != null && payload.has("ringNumber") 
                        ? payload.get("ringNumber").getAsInt() 
                        : 1;
                    event = new com.telcobright.statemachineexamples.callmachine.events.SessionProgress(sessionData, ringNumber);
                } else if (eventType.equals("REJECT")) {
                    // Reject requires a reason parameter
                    String reason = payload != null && payload.has("reason") 
                        ? payload.get("reason").getAsString() 
                        : "User Busy";
                    event = new com.telcobright.statemachineexamples.callmachine.events.Reject(reason);
                }
                
                if (event != null) {
                    // Get the old state before firing
                    String oldState = machine.getCurrentState();
                    
                    // Fire the event to the state machine
                    machine.fire(event);
                    
                    // Get the new state after firing
                    String newState = machine.getCurrentState();
                    
                    System.out.println("[Event] Successfully fired " + eventType + " to machine: " + machineId);
                    System.out.println("[Event] State transition: " + oldState + " -> " + newState);
                    
                    // NOTE: We don't broadcast STATE_CHANGE here anymore because the registry's
                    // onStateTransition callback will handle it. This prevents duplicate broadcasts.
                    // The registry callback ensures ALL state changes (WebSocket events, timeouts, 
                    // internal transitions) are broadcast consistently.
                    
                    // Send confirmation back to client (this is just acknowledgment, not state change)
                    JsonObject response = new JsonObject();
                    response.addProperty("type", "EVENT_FIRED");
                    response.addProperty("machineId", machineId);
                    response.addProperty("eventType", eventType);
                    response.addProperty("timestamp", LocalDateTime.now().format(TIME_FORMAT));
                    response.addProperty("success", true);
                    response.addProperty("oldState", oldState);
                    response.addProperty("newState", newState);
                    broadcastMessage(gson.toJson(response));
                    
                    // Event success logged via MySQL history
                } else {
                    System.err.println("[Event] Could not create event instance for type: " + eventType);
                    
                    // Event error logged via MySQL history
                }
                
            } catch (Exception e) {
                System.err.println("[Event] Error firing event: " + e.getMessage());
                e.printStackTrace();
                
                // Event exception logged via MySQL history
                
                // Send error response
                JsonObject response = new JsonObject();
                response.addProperty("type", "EVENT_ERROR");
                response.addProperty("machineId", machineId);
                response.addProperty("eventType", eventType);
                response.addProperty("error", e.getMessage());
                response.addProperty("timestamp", LocalDateTime.now().format(TIME_FORMAT));
                broadcastMessage(gson.toJson(response));
            }
        } else {
            System.err.println("[Event] Machine not found: " + machineId);
        }
    }
    
    private void startPeriodicUpdates() {
        // Disabled - no periodic updates needed
        // Updates are sent only on:
        // 1. Client connection
        // 2. State machine events
    }
    
    /**
     * Send complete status to a specific client
     */
    private void sendCompleteStatus(WebSocket conn) {
        JsonObject status = buildCompleteStatus();
        conn.send(gson.toJson(status));
    }
    
    /**
     * Broadcast complete status to all connected clients
     */
    private void broadcastCompleteStatus() {
        JsonObject status = buildCompleteStatus();
        broadcastMessage(gson.toJson(status));
    }
    
    /**
     * Send machine history to a client
     */
    private void sendMachineHistory(WebSocket conn, String machineId) {
        System.out.println("[WS] GET_HISTORY request for: " + machineId);
        
        GenericStateMachine<?, ?> machine = registry.getMachine(machineId);
        if (machine != null && machine.getHistoryTracker() != null) {
            try {
                // Use grouped history instead of raw history
                List<Map<String, Object>> history = machine.getHistoryTracker().readGroupedHistory();
                
                // Debug: Print grouped history structure
                if (!history.isEmpty()) {
                    System.out.println("[WS] Sending grouped history with " + history.size() + " state instances");
                    Map<String, Object> firstInstance = history.get(0);
                    System.out.println("[WS] First state instance: state=" + firstInstance.get("state") + 
                        ", instanceNumber=" + firstInstance.get("instanceNumber") + 
                        ", transitions=" + ((List)firstInstance.get("transitions")).size());
                }
                
                JsonObject response = new JsonObject();
                response.addProperty("type", "HISTORY_DATA");
                response.addProperty("machineId", machineId);
                response.addProperty("timestamp", LocalDateTime.now().format(TIME_FORMAT));
                response.add("history", gson.toJsonTree(history));
                
                conn.send(gson.toJson(response));
                System.out.println("[WS] Sent grouped history with " + history.size() + " state instances for " + machineId);
            } catch (Exception e) {
                System.err.println("[WS] Error reading history for " + machineId + ": " + e.getMessage());
                sendErrorResponse(conn, "Failed to read history: " + e.getMessage());
            }
        } else {
            System.err.println("[WS] No history tracker for machine: " + machineId);
            sendErrorResponse(conn, "No history available for machine: " + machineId);
        }
    }
    
    /**
     * Send incremental history updates to a client
     */
    private void sendMachineHistorySince(WebSocket conn, String machineId, int lastId) {
        System.out.println("[WS] GET_HISTORY_SINCE request for: " + machineId + " since ID: " + lastId);
        
        GenericStateMachine<?, ?> machine = registry.getMachine(machineId);
        if (machine != null && machine.getHistoryTracker() != null) {
            try {
                List<Map<String, Object>> history = machine.getHistoryTracker().readHistorySince(lastId);
                
                JsonObject response = new JsonObject();
                response.addProperty("type", "HISTORY_UPDATE");
                response.addProperty("machineId", machineId);
                response.addProperty("timestamp", LocalDateTime.now().format(TIME_FORMAT));
                response.addProperty("lastId", lastId);
                response.add("newEntries", gson.toJsonTree(history));
                
                conn.send(gson.toJson(response));
                System.out.println("[WS] Sent " + history.size() + " new history entries for " + machineId);
            } catch (Exception e) {
                System.err.println("[WS] Error reading history updates for " + machineId + ": " + e.getMessage());
                sendErrorResponse(conn, "Failed to read history updates: " + e.getMessage());
            }
        } else {
            System.err.println("[WS] No history tracker for machine: " + machineId);
            sendErrorResponse(conn, "No history available for machine: " + machineId);
        }
    }
    
    /**
     * Send error response to client
     */
    private void sendErrorResponse(WebSocket conn, String errorMessage) {
        JsonObject error = new JsonObject();
        error.addProperty("type", "ERROR");
        error.addProperty("message", errorMessage);
        error.addProperty("timestamp", LocalDateTime.now().format(TIME_FORMAT));
        conn.send(gson.toJson(error));
    }
    
    /**
     * Send list of machines to a specific client
     */
    private void sendMachinesList(WebSocket conn) {
        JsonObject response = new JsonObject();
        response.addProperty("type", "MACHINES_LIST");
        response.addProperty("timestamp", LocalDateTime.now().format(TIME_FORMAT));
        
        JsonArray machineArray = new JsonArray();
        Set<String> machineIds = registry.getActiveMachineIds();
        
        for (String machineId : machineIds) {
            GenericStateMachine<?, ?> machine = registry.getMachine(machineId);
            if (machine != null) {
                JsonObject machineInfo = new JsonObject();
                machineInfo.addProperty("id", machineId);
                // Try to determine machine type from class name
                String className = machine.getClass().getSimpleName();
                String machineType = className.contains("Call") ? "CallMachine" : 
                                    className.contains("Sms") ? "SmsMachine" : "StateMachine";
                machineInfo.addProperty("type", machineType);
                machineArray.add(machineInfo);
            }
        }
        
        response.add("machines", machineArray);
        conn.send(gson.toJson(response));
        System.out.println("[WS] Sent machines list with " + machineArray.size() + " machines");
    }
    
    /**
     * Broadcast event metadata to all connected clients
     */
    public void broadcastMetadata(JsonObject metadata) {
        broadcastMessage(gson.toJson(metadata));
    }
    
    /**
     * Build complete status including registry and all machines
     */
    private JsonObject buildCompleteStatus() {
        JsonObject status = new JsonObject();
        status.addProperty("type", "COMPLETE_STATUS");
        status.addProperty("timestamp", LocalDateTime.now().format(TIME_FORMAT));
        
        // Registry information
        JsonObject registryInfo = new JsonObject();
        registryInfo.addProperty("debugMode", registry.isDebugEnabled());
        registryInfo.addProperty("webSocketPort", registry.getWebSocketPort());
        registryInfo.addProperty("machineCount", registry.getActiveMachineCount());
        registryInfo.addProperty("connectedClients", connectedClients.size());
        status.add("registry", registryInfo);
        
        // All machines information
        JsonArray machines = new JsonArray();
        Set<String> machineIds = registry.getActiveMachineIds();
        
        for (String machineId : machineIds) {
            GenericStateMachine<?, ?> machine = registry.getMachine(machineId);
            if (machine != null) {
                JsonObject machineInfo = new JsonObject();
                machineInfo.addProperty("machineId", machineId);
                machineInfo.addProperty("currentState", machine.getCurrentState());
                machineInfo.addProperty("isActive", true);
                
                // Add context if available
                if (machine.getPersistingEntity() != null) {
                    try {
                        StateMachineContextEntity<?> context = machine.getPersistingEntity();
                        
                        // Serialize the entire context
                        JsonObject contextJson = gson.toJsonTree(context).getAsJsonObject();
                        
                        // Add the current state if available
                        if (context.getCurrentState() != null) {
                            contextJson.addProperty("currentState", context.getCurrentState());
                        }
                        contextJson.addProperty("isComplete", context.isComplete());
                        
                        machineInfo.add("context", contextJson);
                    } catch (Exception e) {
                        machineInfo.addProperty("contextError", "Failed to serialize: " + e.getMessage());
                    }
                }
                
                // Add any volatile context info if needed
                machineInfo.addProperty("hasVolatileContext", false);
                
                machines.add(machineInfo);
            }
        }
        
        status.add("machines", machines);
        
        // Add summary statistics
        JsonObject stats = new JsonObject();
        stats.addProperty("totalMachines", machineIds.size());
        stats.addProperty("activeMachines", machineIds.size());
        stats.addProperty("totalClients", connectedClients.size());
        status.add("statistics", stats);
        
        return status;
    }
    
    public void shutdown() {
        try {
            wsLogger.logSystem("Shutting down WebSocket server");
            wsLogger.shutdown();
            scheduler.shutdown();
            scheduler.awaitTermination(1, TimeUnit.SECONDS);
            this.stop();
        } catch (Exception e) {
            System.err.println("Error during WebSocket server shutdown: " + e.getMessage());
        }
    }
    
    /**
     * Handle GET_EVENTS request from client - deprecated, use GET_HISTORY instead
     */
    private void handleGetEvents(WebSocket conn, JsonObject request) {
        // Events are now tracked in MySQL history only
        JsonObject response = new JsonObject();
        response.addProperty("type", "EVENTS_ERROR");
        response.addProperty("error", "Events are now tracked in MySQL history. Use GET_HISTORY instead.");
        conn.send(gson.toJson(response));
    }
    
    /**
     * Old handleGetEvents method - deprecated - removed completely
     */
}
