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
            JsonObject request = gson.fromJson(message, JsonObject.class);
            
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
    }
    
    private void broadcastMessage(String message) {
        for (WebSocket client : connectedClients) {
            if (client.isOpen()) {
                client.send(message);
            }
        }
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
        
        conn.send(gson.toJson(state));
    }
    
    private void sendMachineState(WebSocket conn, String machineId) {
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
            
            conn.send(gson.toJson(state));
        }
    }
    
    private void routeEventToMachine(String machineId, String eventType, JsonObject payload) {
        GenericStateMachine<?, ?> machine = registry.getMachine(machineId);
        if (machine != null) {
            // This would need to be extended based on specific machine types
            // For now, just log the attempt
            System.out.println("[Event] Would route " + eventType + " to machine: " + machineId);
            
            // Send confirmation back to client
            JsonObject response = new JsonObject();
            response.addProperty("type", "EVENT_RECEIVED");
            response.addProperty("machineId", machineId);
            response.addProperty("eventType", eventType);
            response.addProperty("timestamp", LocalDateTime.now().format(TIME_FORMAT));
            broadcastMessage(gson.toJson(response));
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
            scheduler.shutdown();
            scheduler.awaitTermination(1, TimeUnit.SECONDS);
            this.stop();
        } catch (Exception e) {
            System.err.println("Error during WebSocket server shutdown: " + e.getMessage());
        }
    }
}