package com.telcobright.statemachine;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.function.Function;
import com.telcobright.statemachine.timeout.TimeoutManager;
import com.telcobright.statemachine.monitoring.SimpleDatabaseSnapshotRecorder;
import com.telcobright.statemachine.websocket.StateMachineWebSocketServer;
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
    protected final TimeoutManager timeoutManager;
    protected boolean snapshotDebug = false;  // Snapshot debugging flag
    protected boolean liveDebug = false;       // Live debugging flag
    protected SimpleDatabaseSnapshotRecorder snapshotRecorder;
    protected final List<StateMachineListener<?, ?>> listeners = new CopyOnWriteArrayList<>();
    protected StateMachineWebSocketServer webSocketServer;
    protected int webSocketPort = 9999; // Default WebSocket port
    
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
     * Enable snapshot debugging - records state transitions to database
     */
    public void enableSnapshotDebug() {
        enableSnapshotDebug(new SimpleDatabaseSnapshotRecorder());
    }
    
    /**
     * Enable snapshot debugging with custom recorder
     * @param customRecorder Custom snapshot recorder implementation
     */
    public void enableSnapshotDebug(SimpleDatabaseSnapshotRecorder customRecorder) {
        this.snapshotDebug = true;
        this.snapshotRecorder = customRecorder;
        
        System.out.println("📸 Snapshot debugging ENABLED");
        System.out.println("   All state transitions will be recorded to database");
    }
    
    /**
     * Enable live debugging - starts WebSocket server for real-time monitoring
     */
    public void enableLiveDebug() {
        enableLiveDebug(webSocketPort);
    }
    
    /**
     * Enable live debugging with custom port
     * @param port WebSocket server port
     */
    public void enableLiveDebug(int port) {
        this.liveDebug = true;
        this.webSocketPort = port;
        startWebSocketServer();
        
        System.out.println("🔴 Live debugging ENABLED");
        System.out.println("   WebSocket server: ws://localhost:" + port);
    }
    
    /**
     * Start WebSocket server for real-time monitoring
     */
    protected void startWebSocketServer() {
        if (webSocketServer == null) {
            try {
                webSocketServer = new StateMachineWebSocketServer(webSocketPort, this);
                webSocketServer.start();
                
                System.out.println("\n[WebSocket Server]");
                System.out.println("Running on: ws://localhost:" + webSocketPort);
                System.out.println("Ready for real-time monitoring...\n");
                
                // Add WebSocket server as a listener
                addListener(webSocketServer);
                
                // Send initial event metadata to connected clients
                sendEventMetadataUpdate();
            } catch (Exception e) {
                System.err.println("Failed to start WebSocket server: " + e.getMessage());
                e.printStackTrace();
            }
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
     * Disable snapshot debugging
     */
    public void disableSnapshotDebug() {
        this.snapshotDebug = false;
        this.snapshotRecorder = null;
        System.out.println("📸 Snapshot debugging DISABLED");
    }
    
    /**
     * Disable live debugging
     */
    public void disableLiveDebug() {
        this.liveDebug = false;
        stopWebSocketServer();
        System.out.println("🔴 Live debugging DISABLED");
    }
    
    /**
     * Disable all debugging
     */
    public void disableAllDebug() {
        disableSnapshotDebug();
        disableLiveDebug();
    }
    
    /**
     * Check if snapshot debugging is enabled
     */
    public boolean isSnapshotDebugEnabled() {
        return snapshotDebug;
    }
    
    /**
     * Check if live debugging is enabled
     */
    public boolean isLiveDebugEnabled() {
        return liveDebug;
    }
    
    /**
     * Check if any debugging is enabled
     */
    public boolean isDebugEnabled() {
        return snapshotDebug || liveDebug;
    }
    
    /**
     * Get the snapshot recorder (if debug mode is enabled)
     */
    public SimpleDatabaseSnapshotRecorder getSnapshotRecorder() {
        return snapshotRecorder;
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
        if (timeoutManager != null) {
            // Shutdown timeout manager if needed
        }
    }
}