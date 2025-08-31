package com.telcobright.statemachine;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.function.Function;
import com.telcobright.statemachine.timeout.TimeoutManager;
import com.telcobright.statemachine.history.History;
import com.telcobright.debugger.StateMachineWebSocketServer;
import java.nio.file.Paths;
import com.telcobright.statemachine.events.EventTypeRegistry;
import com.telcobright.statemachine.events.StateMachineEvent;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.HashSet;

/**
 * Abstract base class for state machine registries
 * Provides core functionality for managing state machine instances
 */
public abstract class AbstractStateMachineRegistry {
    
    protected final Map<String, GenericStateMachine<?, ?>> activeMachines = new ConcurrentHashMap<>();
    protected final Map<String, GenericStateMachine<?, ?>> offlineMachinesForDebug = new ConcurrentHashMap<>();
    protected final TimeoutManager timeoutManager;
    protected boolean debugMode = false;       // Debug mode flag
    protected History history;
    protected final List<StateMachineListener<?, ?>> listeners = new CopyOnWriteArrayList<>();
    protected StateMachineWebSocketServer webSocketServer;
    protected int webSocketPort = 9999; // Default WebSocket port
    
    // Track last added and removed machines
    protected volatile String lastAddedMachine = null;
    protected volatile String lastRemovedMachine = null;
    
    /**
     * Default constructor
     */
    public AbstractStateMachineRegistry() {
        this.timeoutManager = null;
    }
    
    /**
     * Constructor with timeout manager
     */
    public AbstractStateMachineRegistry(TimeoutManager timeoutManager) {
        this.timeoutManager = timeoutManager;
    }
    
    /**
     * Constructor with timeout manager and WebSocket port
     */
    public AbstractStateMachineRegistry(TimeoutManager timeoutManager, int webSocketPort) {
        this.timeoutManager = timeoutManager;
        this.webSocketPort = webSocketPort;
    }
    
    /**
     * Enable debug mode - starts WebSocket server for real-time monitoring
     * and enables history tracking for treeview
     */
    public void enableDebugMode() {
        enableDebugMode(webSocketPort);
    }
    
    /**
     * Enable debug mode with custom port
     * @param port WebSocket server port
     */
    public void enableDebugMode(int port) {
        this.debugMode = true;
        this.webSocketPort = port;
        
        // Start WebSocket server first
        startWebSocketServer();
        
        // Initialize history with broadcaster after server is started
        this.history = new History(message -> {
            if (webSocketServer != null) {
                // The message is already a complete JSON object with type field
                webSocketServer.broadcastMessage(message.toString());
            }
        });
        
        System.out.println("🔍 Debug mode ENABLED");
        System.out.println("   WebSocket server: ws://localhost:" + port);
        System.out.println("   History tracking: ACTIVE");
    }
    
    /**
     * Start WebSocket server for real-time monitoring
     */
    protected void startWebSocketServer() {
        if (webSocketServer != null) {
            System.out.println("[WS] WebSocket server already running on port " + webSocketPort);
            return;
        }
        
        try {
            System.out.println("[WS] Creating WebSocket server on port " + webSocketPort + "...");
            webSocketServer = new StateMachineWebSocketServer(webSocketPort, this);
            
            // Start the server in a separate thread to avoid blocking
            Thread serverThread = new Thread(() -> {
                try {
                    webSocketServer.start();
                    System.out.println("[WS] WebSocket server started successfully");
                } catch (Exception e) {
                    System.err.println("[WS] Failed to start WebSocket server: " + e.getMessage());
                    webSocketServer = null;
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();
            
            // Wait a bit for the server to start
            Thread.sleep(500);
            
            if (webSocketServer != null) {
                System.out.println("\n[WebSocket Server]");
                System.out.println("Running on: ws://localhost:" + webSocketPort);
                System.out.println("Ready for real-time monitoring...\n");
                
                // Add WebSocket server as a listener
                addListener(webSocketServer);
                
                // Send initial event metadata to connected clients
                sendEventMetadataUpdate();
            }
        } catch (Exception e) {
            System.err.println("[WS] Connection error: " + e.getMessage());
            webSocketServer = null;
        }
    }
    
    /**
     * Stop WebSocket server if running
     */
    public void stopWebSocketServer() {
        if (webSocketServer != null) {
            try {
                removeListener(webSocketServer);
                webSocketServer.stop();
                webSocketServer = null;
                System.out.println("WebSocket server stopped");
            } catch (Exception e) {
                System.err.println("Error stopping WebSocket server: " + e.getMessage());
            }
        }
    }
    
    /**
     * Disable debug mode
     */
    public void disableDebugMode() {
        this.debugMode = false;
        stopWebSocketServer();
        if (history != null) {
            history.clear();
            history = null;
        }
        System.out.println("🔴 Debug mode DISABLED");
    }
    
    /**
     * Check if debug mode is enabled
     */
    public boolean isDebugEnabled() {
        return debugMode;
    }
    
    /**
     * Get the history tracker (if debug mode is enabled)
     */
    public History getHistory() {
        return history;
    }
    
    /**
     * Get WebSocket server port
     */
    public int getWebSocketPort() {
        return webSocketPort;
    }
    
    /**
     * Set WebSocket server port (must be called before enabling debug mode)
     */
    public void setWebSocketPort(int port) {
        if (webSocketServer != null) {
            throw new IllegalStateException("Cannot change port while WebSocket server is running");
        }
        this.webSocketPort = port;
    }
    
    /**
     * Check if WebSocket server is running
     */
    public boolean isWebSocketServerRunning() {
        return webSocketServer != null;
    }
    
    /**
     * Add a listener for state machine events
     */
    public void addListener(StateMachineListener<?, ?> listener) {
        listeners.add(listener);
    }
    
    /**
     * Remove a listener
     */
    public void removeListener(StateMachineListener<?, ?> listener) {
        listeners.remove(listener);
    }
    
    /**
     * Get all active machine IDs
     */
    public java.util.Set<String> getActiveMachineIds() {
        return new java.util.HashSet<>(activeMachines.keySet());
    }
    
    /**
     * Check if a machine is registered
     */
    public boolean isRegistered(String machineId) {
        return activeMachines.containsKey(machineId);
    }
    
    /**
     * Get the number of active machines
     */
    public int getActiveMachineCount() {
        return activeMachines.size();
    }
    
    /**
     * Clear all machines from the registry
     */
    public void clearAll() {
        for (String machineId : activeMachines.keySet()) {
            removeMachine(machineId);
        }
    }
    
    /**
     * Notify listeners that an event was ignored
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void notifyEventIgnored(String machineId, String currentState, String eventType,
                                   StateMachineContextEntity contextEntity, Object volatileContext) {
        // Record in history if debug mode is enabled
        if (debugMode && history != null) {
            history.recordIgnoredEvent(machineId, currentState, 
                new com.telcobright.statemachine.events.GenericStateMachineEvent(eventType), contextEntity);
        }
        
        // Notify all listeners
        for (StateMachineListener listener : listeners) {
            try {
                listener.onEventIgnored(machineId, currentState, eventType, contextEntity, volatileContext);
            } catch (Exception e) {
                System.err.println("Error notifying listener of ignored event: " + e.getMessage());
            }
        }
    }
    
    // Abstract methods to be implemented by concrete classes
    public abstract void register(String machineId, GenericStateMachine<?, ?> machine);
    public abstract GenericStateMachine<?, ?> getMachine(String machineId);
    public abstract void removeMachine(String machineId);
    public abstract <T extends StateMachineContextEntity<?>> T rehydrateMachine(
        String machineId, 
        Class<T> contextClass, 
        Supplier<T> contextSupplier, 
        Function<T, GenericStateMachine<T, ?>> machineBuilder
    );
    
    /**
     * Clear offline machines for debug when no WebSocket clients are connected
     */
    public void clearOfflineMachinesForDebug() {
        int count = offlineMachinesForDebug.size();
        if (count > 0) {
            offlineMachinesForDebug.clear();
            System.out.println("[Registry] Cleared " + count + " offline machines from debug cache");
        }
    }
    
    /**
     * Check if WebSocket has connected clients
     */
    public boolean hasWebSocketClients() {
        return webSocketServer != null && webSocketServer.hasConnectedClients();
    }
    
    /**
     * Get offline machines for debug (read-only)
     */
    public Map<String, GenericStateMachine<?, ?>> getOfflineMachinesForDebug() {
        return new ConcurrentHashMap<>(offlineMachinesForDebug);
    }
    
    /**
     * Extract supported events from a state machine using reflection
     * @param machine The state machine to analyze
     * @return JsonObject containing event metadata
     */
    public JsonObject extractMachineEventMetadata(GenericStateMachine<?, ?> machine) {
        System.out.println("[Registry] Extracting metadata for machine...");
        JsonObject metadata = new JsonObject();
        metadata.addProperty("machineId", getMachineId(machine));
        metadata.addProperty("currentState", machine.getCurrentState());
        
        JsonArray supportedEvents = new JsonArray();
        Set<String> uniqueEventTypes = new HashSet<>();
        
        try {
            // Access the transitions map via reflection
            Field transitionsField = GenericStateMachine.class.getDeclaredField("transitions");
            transitionsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Map<String, String>> transitions = 
                (Map<String, Map<String, String>>) transitionsField.get(machine);
            
            // Extract all event types from transitions
            System.out.println("[Registry] Found " + transitions.size() + " states with transitions");
            for (Map.Entry<String, Map<String, String>> stateEntry : transitions.entrySet()) {
                String fromState = stateEntry.getKey();
                Map<String, String> stateTransitions = stateEntry.getValue();
                
                for (Map.Entry<String, String> transition : stateTransitions.entrySet()) {
                    String eventType = transition.getKey();
                    String toState = transition.getValue();
                    
                    if (!uniqueEventTypes.contains(eventType)) {
                        uniqueEventTypes.add(eventType);
                        
                        JsonObject eventInfo = new JsonObject();
                        eventInfo.addProperty("eventType", eventType);
                        eventInfo.addProperty("displayName", formatEventName(eventType));
                        
                        // Generate mock data for this event type
                        JsonObject mockData = generateEventMockData(eventType);
                        eventInfo.add("mockData", mockData);
                        
                        // Add possible transitions for this event
                        JsonArray possibleTransitions = new JsonArray();
                        for (Map.Entry<String, Map<String, String>> state : transitions.entrySet()) {
                            if (state.getValue().containsKey(eventType)) {
                                JsonObject transitionInfo = new JsonObject();
                                transitionInfo.addProperty("fromState", state.getKey());
                                transitionInfo.addProperty("toState", state.getValue().get(eventType));
                                possibleTransitions.add(transitionInfo);
                            }
                        }
                        eventInfo.add("transitions", possibleTransitions);
                        
                        supportedEvents.add(eventInfo);
                    }
                }
            }
            
            // Also check for stay actions (events that don't cause transitions)
            Field stayActionsField = GenericStateMachine.class.getDeclaredField("stayActions");
            stayActionsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Map<String, ?>> stayActions = 
                (Map<String, Map<String, ?>>) stayActionsField.get(machine);
            
            for (Map.Entry<String, Map<String, ?>> stateEntry : stayActions.entrySet()) {
                String state = stateEntry.getKey();
                for (String eventType : stateEntry.getValue().keySet()) {
                    if (!uniqueEventTypes.contains(eventType)) {
                        uniqueEventTypes.add(eventType);
                        
                        JsonObject eventInfo = new JsonObject();
                        eventInfo.addProperty("eventType", eventType);
                        eventInfo.addProperty("displayName", formatEventName(eventType));
                        eventInfo.addProperty("isStayEvent", true);
                        eventInfo.addProperty("inState", state);
                        
                        // Generate mock data for stay events too
                        JsonObject mockData = generateEventMockData(eventType);
                        eventInfo.add("mockData", mockData);
                        
                        supportedEvents.add(eventInfo);
                    }
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error extracting event metadata: " + e.getMessage());
            e.printStackTrace();
        }
        
        metadata.add("supportedEvents", supportedEvents);
        
        // Add context information if available
        if (machine.getPersistingEntity() != null) {
            JsonObject contextInfo = new JsonObject();
            contextInfo.addProperty("contextClass", machine.getPersistingEntity().getClass().getSimpleName());
            metadata.add("contextInfo", contextInfo);
        }
        
        return metadata;
    }
    
    /**
     * Generate mock JSON data for an event type
     */
    private JsonObject generateEventMockData(String eventType) {
        JsonObject mockData = new JsonObject();
        
        // Generate mock data based on event type
        switch (eventType) {
            case "INCOMING_CALL":
                mockData.addProperty("callerNumber", "+1-555-1234");
                break;
                
            case "ANSWER":
                // Answer event typically has no payload
                break;
                
            case "HANGUP":
                // Hangup event typically has no payload
                break;
                
            case "SESSION_PROGRESS":
                mockData.addProperty("sessionData", "v=0");
                mockData.addProperty("ringNumber", 1);
                break;
                
            case "REJECT":
                mockData.addProperty("reason", "User Busy");
                break;
                
            case "TIMEOUT":
                mockData.addProperty("duration", 30);
                mockData.addProperty("unit", "SECONDS");
                break;
                
            case "DIAL":
                mockData.addProperty("phoneNumber", "+1-555-5678");
                break;
                
            default:
                // For unknown events, provide empty object
                break;
        }
        
        return mockData;
    }
    
    /**
     * Format event name for display
     */
    private String formatEventName(String eventType) {
        // Convert SNAKE_CASE to Title Case
        String[] parts = eventType.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.length() > 0) {
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    result.append(part.substring(1));
                }
                result.append(" ");
            }
        }
        return result.toString().trim();
    }
    
    /**
     * Get machine ID from a GenericStateMachine instance
     */
    private String getMachineId(GenericStateMachine<?, ?> machine) {
        try {
            Field idField = GenericStateMachine.class.getDeclaredField("id");
            idField.setAccessible(true);
            return (String) idField.get(machine);
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    /**
     * Collect and send event metadata for all registered machines
     */
    public void sendEventMetadataUpdate() {
        if (webSocketServer == null) {
            return;
        }
        
        JsonObject update = new JsonObject();
        update.addProperty("type", "EVENT_METADATA_UPDATE");
        update.addProperty("timestamp", System.currentTimeMillis());
        
        JsonArray machinesMetadata = new JsonArray();
        
        // Extract metadata for each registered machine
        for (Map.Entry<String, GenericStateMachine<?, ?>> entry : activeMachines.entrySet()) {
            JsonObject machineMetadata = extractMachineEventMetadata(entry.getValue());
            machinesMetadata.add(machineMetadata);
        }
        
        update.add("machines", machinesMetadata);
        
        // Send to all connected WebSocket clients
        webSocketServer.broadcastMetadata(update);
    }
    
    
    /**
     * Shutdown the registry and clean up resources
     */
    public void shutdown() {
        clearAll();
        stopWebSocketServer();
        
        // Event logging cleanup handled by MySQL connection pool
        
        if (timeoutManager != null) {
            // Shutdown timeout manager if needed
        }
    }
    
    /**
     * Get the ID of the last added/rehydrated machine
     * @return Machine ID or null if none
     */
    public String getLastAddedMachine() {
        return lastAddedMachine;
    }
    
    /**
     * Get the ID of the last removed/offline machine
     * @return Machine ID or null if none
     */
    public String getLastRemovedMachine() {
        return lastRemovedMachine;
    }
}