package com.telcobright.statemachine.history;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.StateMachineContextEntity;
import com.telcobright.statemachine.events.StateMachineEvent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simplified History class for per-machine debugging
 * Shows transitions only for the selected machine
 */
public class History {
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    // Store transitions per machine - simplified approach
    private final Map<String, List<TransitionInfo>> machineTransitions = new ConcurrentHashMap<>();
    private final AtomicInteger version = new AtomicInteger(0);
    
    // Current UI state
    private String selectedMachineId = null;
    
    // WebSocket broadcaster callback
    private final StoreBroadcaster broadcaster;
    
    public interface StoreBroadcaster {
        void broadcast(JsonObject store);
    }
    
    public History(StoreBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }
    
    /**
     * Record a state transition for a machine - simplified version
     */
    public synchronized void recordTransition(String machineId, 
                                             String fromState, 
                                             String toState, 
                                             StateMachineEvent event,
                                             StateMachineContextEntity<?> contextBefore,
                                             StateMachineContextEntity<?> contextAfter,
                                             long duration) {
        
        System.out.println("[History] Recording transition: " + machineId + " " + 
                          (fromState != null ? fromState : "Initial") + " -> " + toState);
        
        // Get or create transition list for this machine
        List<TransitionInfo> transitions = machineTransitions.computeIfAbsent(machineId, 
            k -> new ArrayList<>());
        
        // Create transition info
        TransitionInfo transition = new TransitionInfo();
        transition.machineId = machineId;
        transition.fromState = fromState != null ? fromState : "Initial";
        transition.toState = toState;
        transition.event = event != null ? event.getEventType() : "Start";
        transition.timestamp = LocalDateTime.now().format(TIME_FORMAT);
        transition.duration = duration;
        transition.stepNumber = transitions.size() + 1;
        
        // Add to machine's transition list
        transitions.add(transition);
        
        System.out.println("[History] Machine " + machineId + " now has " + 
                         transitions.size() + " transitions");
        
        // Broadcast updated store
        broadcastStore();
    }
    
    /**
     * Record machine initialization
     */
    public synchronized void recordMachineStart(String machineId, String initialState) {
        recordTransition(machineId, null, initialState, null, null, null, 0);
    }
    
    /**
     * Record machine removal
     */
    public synchronized void recordMachineRemoval(String machineId) {
        machineTransitions.remove(machineId);
        
        // Clear selection if this was the selected machine
        if (machineId.equals(selectedMachineId)) {
            selectedMachineId = null;
        }
        
        broadcastStore();
    }
    
    /**
     * Set selected machine
     */
    public synchronized void setSelectedMachine(String machineId) {
        this.selectedMachineId = machineId;
        System.out.println("[History] Selected machine changed to: " + machineId);
        broadcastStore();
    }
    
    /**
     * Reset selected machine (called when new client connects)
     */
    public synchronized void resetSelectedMachine() {
        this.selectedMachineId = null;
        System.out.println("[History] Selected machine reset to null (new client connected)");
        broadcastStore();
    }
    
    /**
     * Clear history
     */
    public synchronized void clear() {
        machineTransitions.clear();
        selectedMachineId = null;
        version.set(0);
        broadcastStore();
    }
    
    /**
     * Get current store as JSON - simplified per-machine view
     */
    public synchronized JsonObject getStore() {
        JsonObject store = new JsonObject();
        
        // Add available machines
        JsonArray machinesArray = new JsonArray();
        for (String machineId : machineTransitions.keySet()) {
            machinesArray.add(machineId);
        }
        store.add("availableMachines", machinesArray);
        
        // Add selected machine
        store.addProperty("selectedMachineId", selectedMachineId);
        
        // Add transitions for selected machine only
        JsonArray transitionsArray = new JsonArray();
        if (selectedMachineId != null && machineTransitions.containsKey(selectedMachineId)) {
            List<TransitionInfo> transitions = machineTransitions.get(selectedMachineId);
            
            for (TransitionInfo transition : transitions) {
                JsonObject transitionObj = new JsonObject();
                transitionObj.addProperty("stepNumber", transition.stepNumber);
                transitionObj.addProperty("fromState", transition.fromState);
                transitionObj.addProperty("toState", transition.toState);
                transitionObj.addProperty("event", transition.event);
                transitionObj.addProperty("timestamp", transition.timestamp);
                transitionObj.addProperty("duration", transition.duration);
                transitionObj.addProperty("machineId", transition.machineId);
                
                transitionsArray.add(transitionObj);
            }
        }
        
        store.add("transitions", transitionsArray);
        
        // Add metadata
        store.addProperty("lastUpdate", LocalDateTime.now().format(TIME_FORMAT));
        store.addProperty("version", version.incrementAndGet());
        
        return store;
    }
    
    /**
     * Broadcast current store through WebSocket
     */
    private void broadcastStore() {
        if (broadcaster != null) {
            JsonObject message = new JsonObject();
            message.addProperty("type", "TREEVIEW_STORE_UPDATE");
            message.addProperty("timestamp", LocalDateTime.now().format(TIME_FORMAT));
            message.add("store", getStore());
            
            int transitionCount = selectedMachineId != null && machineTransitions.containsKey(selectedMachineId) ?
                machineTransitions.get(selectedMachineId).size() : 0;
            
            System.out.println("[History] Broadcasting update - Machine: " + selectedMachineId + 
                             ", Transitions: " + transitionCount + ", Version: " + version.get());
            
            broadcaster.broadcast(message);
        } else {
            System.out.println("[History] No broadcaster configured - cannot send update");
        }
    }
    
    // Simplified TransitionInfo class
    private static class TransitionInfo {
        String machineId;
        String fromState;
        String toState;
        String event;
        String timestamp;
        long duration;
        int stepNumber;
    }
}