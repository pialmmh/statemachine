package com.telcobright.statemachine.history;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonParseException;
import com.telcobright.statemachine.StateMachineContextEntity;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks complete history of state machine execution including all state transitions,
 * events, and context snapshots. Writes to individual files per machine ID.
 */
public class MachineHistoryTracker {
    private static final String HISTORY_DIR = "machine-history";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Gson gson = new GsonBuilder()
        .serializeNulls()
        .registerTypeAdapter(LocalDateTime.class, (JsonSerializer<LocalDateTime>) 
            (src, typeOfSrc, context) -> new JsonPrimitive(src.format(TIMESTAMP_FORMAT)))
        .registerTypeAdapter(LocalDateTime.class, (JsonDeserializer<LocalDateTime>) 
            (json, typeOfT, context) -> LocalDateTime.parse(json.getAsString(), TIMESTAMP_FORMAT))
        .registerTypeAdapter(LocalDate.class, (JsonSerializer<LocalDate>) 
            (src, typeOfSrc, context) -> new JsonPrimitive(src.format(DATE_FORMAT)))
        .registerTypeAdapter(LocalDate.class, (JsonDeserializer<LocalDate>) 
            (json, typeOfT, context) -> LocalDate.parse(json.getAsString(), DATE_FORMAT))
        .create();
    
    private final String machineId;
    private final Path historyFile;
    private final BufferedWriter writer;
    private final AtomicInteger stepCounter = new AtomicInteger(0);
    private boolean isActive = false;
    
    // Track active trackers to ensure cleanup
    private static final Map<String, MachineHistoryTracker> activeTrackers = new ConcurrentHashMap<>();
    
    public MachineHistoryTracker(String machineId, boolean debugEnabled) throws IOException {
        this.machineId = machineId;
        
        if (!debugEnabled) {
            this.historyFile = null;
            this.writer = null;
            return;
        }
        
        // Create history directory if it doesn't exist
        Path historyDir = Paths.get(HISTORY_DIR);
        if (!Files.exists(historyDir)) {
            Files.createDirectories(historyDir);
        }
        
        // Close any existing tracker for this machine
        MachineHistoryTracker existing = activeTrackers.get(machineId);
        if (existing != null) {
            existing.close();
        }
        
        // Create history file path
        this.historyFile = historyDir.resolve("history-" + machineId + ".jsonl");
        
        // Delete existing file if it exists (overwrite for new run)
        Files.deleteIfExists(historyFile);
        
        // Create new file and writer
        this.writer = Files.newBufferedWriter(historyFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        this.isActive = true;
        
        // Register this tracker
        activeTrackers.put(machineId, this);
        
        System.out.println("[History] Created history tracker for machine: " + machineId + " at " + historyFile);
    }
    
    /**
     * Record the initial state of the machine
     */
    public void recordInitialState(String state, StateMachineContextEntity contextEntity, Object volatileContext) {
        if (!isActive || writer == null) return;
        
        try {
            JsonObject entry = new JsonObject();
            entry.addProperty("stepNumber", stepCounter.incrementAndGet());
            entry.addProperty("type", "INITIAL_STATE");
            entry.addProperty("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            entry.addProperty("machineId", machineId);
            entry.addProperty("state", state);
            
            // Add context snapshots
            if (contextEntity != null) {
                entry.add("persistentContext", gson.toJsonTree(contextEntity));
            }
            if (volatileContext != null) {
                entry.add("volatileContext", gson.toJsonTree(volatileContext));
            }
            
            writeEntry(entry);
            
        } catch (Exception e) {
            System.err.println("[History] Error recording initial state: " + e.getMessage());
        }
    }
    
    /**
     * Record an event being sent to the machine
     */
    public void recordEventReceived(String currentState, String eventType, Object eventPayload, 
                                   StateMachineContextEntity contextBefore, Object volatileContextBefore) {
        if (!isActive || writer == null) return;
        
        try {
            JsonObject entry = new JsonObject();
            entry.addProperty("stepNumber", stepCounter.incrementAndGet());
            entry.addProperty("type", "EVENT_RECEIVED");
            entry.addProperty("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            entry.addProperty("machineId", machineId);
            entry.addProperty("currentState", currentState);
            entry.addProperty("eventType", eventType);
            
            // Add event payload
            if (eventPayload != null) {
                entry.add("eventPayload", gson.toJsonTree(eventPayload));
            }
            
            // Add context before event
            if (contextBefore != null) {
                entry.add("contextBefore", gson.toJsonTree(contextBefore));
            }
            if (volatileContextBefore != null) {
                entry.add("volatileContextBefore", gson.toJsonTree(volatileContextBefore));
            }
            
            writeEntry(entry);
            
        } catch (Exception e) {
            System.err.println("[History] Error recording event: " + e.getMessage());
        }
    }
    
    /**
     * Record a state transition
     */
    public void recordStateTransition(String fromState, String toState, String eventType, Object eventPayload,
                                     StateMachineContextEntity contextAfter, Object volatileContextAfter,
                                     long transitionDurationMs, String entryActionStatus) {
        if (!isActive || writer == null) return;
        
        try {
            JsonObject entry = new JsonObject();
            entry.addProperty("stepNumber", stepCounter.incrementAndGet());
            entry.addProperty("type", "STATE_TRANSITION");
            entry.addProperty("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            entry.addProperty("machineId", machineId);
            entry.addProperty("fromState", fromState);
            entry.addProperty("toState", toState);
            entry.addProperty("eventType", eventType);
            entry.addProperty("transitionDurationMs", transitionDurationMs);
            entry.addProperty("entryActionStatus", entryActionStatus);
            
            // Add event payload if present
            if (eventPayload != null) {
                entry.add("eventPayload", gson.toJsonTree(eventPayload));
            }
            
            // Add context after transition
            if (contextAfter != null) {
                entry.add("contextAfter", gson.toJsonTree(contextAfter));
            }
            if (volatileContextAfter != null) {
                entry.add("volatileContextAfter", gson.toJsonTree(volatileContextAfter));
            }
            
            writeEntry(entry);
            
        } catch (Exception e) {
            System.err.println("[History] Error recording state transition: " + e.getMessage());
        }
    }
    
    /**
     * Record an event that didn't cause a state change
     */
    public void recordEventNoTransition(String currentState, String eventType, Object eventPayload,
                                       StateMachineContextEntity contextAfter, Object volatileContextAfter,
                                       long processingDurationMs) {
        if (!isActive || writer == null) return;
        
        try {
            JsonObject entry = new JsonObject();
            entry.addProperty("stepNumber", stepCounter.incrementAndGet());
            entry.addProperty("type", "EVENT_NO_TRANSITION");
            entry.addProperty("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            entry.addProperty("machineId", machineId);
            entry.addProperty("currentState", currentState);
            entry.addProperty("eventType", eventType);
            entry.addProperty("processingDurationMs", processingDurationMs);
            
            // Add event payload
            if (eventPayload != null) {
                entry.add("eventPayload", gson.toJsonTree(eventPayload));
            }
            
            // Add context after event processing
            if (contextAfter != null) {
                entry.add("contextAfter", gson.toJsonTree(contextAfter));
            }
            if (volatileContextAfter != null) {
                entry.add("volatileContextAfter", gson.toJsonTree(volatileContextAfter));
            }
            
            writeEntry(entry);
            
        } catch (Exception e) {
            System.err.println("[History] Error recording event with no transition: " + e.getMessage());
        }
    }
    
    /**
     * Record a timeout event
     */
    public void recordTimeout(String fromState, String toState, long timeoutDurationMs,
                             StateMachineContextEntity contextAfter, Object volatileContextAfter) {
        if (!isActive || writer == null) return;
        
        try {
            JsonObject entry = new JsonObject();
            entry.addProperty("stepNumber", stepCounter.incrementAndGet());
            entry.addProperty("type", "TIMEOUT");
            entry.addProperty("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            entry.addProperty("machineId", machineId);
            entry.addProperty("fromState", fromState);
            entry.addProperty("toState", toState);
            entry.addProperty("timeoutDurationMs", timeoutDurationMs);
            
            // Add context after timeout
            if (contextAfter != null) {
                entry.add("contextAfter", gson.toJsonTree(contextAfter));
            }
            if (volatileContextAfter != null) {
                entry.add("volatileContextAfter", gson.toJsonTree(volatileContextAfter));
            }
            
            writeEntry(entry);
            
        } catch (Exception e) {
            System.err.println("[History] Error recording timeout: " + e.getMessage());
        }
    }
    
    /**
     * Record machine completion
     */
    public void recordCompletion(String finalState, StateMachineContextEntity finalContext, Object finalVolatileContext) {
        if (!isActive || writer == null) return;
        
        try {
            JsonObject entry = new JsonObject();
            entry.addProperty("stepNumber", stepCounter.incrementAndGet());
            entry.addProperty("type", "COMPLETION");
            entry.addProperty("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            entry.addProperty("machineId", machineId);
            entry.addProperty("finalState", finalState);
            
            // Add final context
            if (finalContext != null) {
                entry.add("finalContext", gson.toJsonTree(finalContext));
            }
            if (finalVolatileContext != null) {
                entry.add("finalVolatileContext", gson.toJsonTree(finalVolatileContext));
            }
            
            writeEntry(entry);
            
        } catch (Exception e) {
            System.err.println("[History] Error recording completion: " + e.getMessage());
        }
    }
    
    /**
     * Record an error that occurred
     */
    public void recordError(String currentState, String errorType, String errorMessage, 
                           StateMachineContextEntity context, Object volatileContext) {
        if (!isActive || writer == null) return;
        
        try {
            JsonObject entry = new JsonObject();
            entry.addProperty("stepNumber", stepCounter.incrementAndGet());
            entry.addProperty("type", "ERROR");
            entry.addProperty("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            entry.addProperty("machineId", machineId);
            entry.addProperty("currentState", currentState);
            entry.addProperty("errorType", errorType);
            entry.addProperty("errorMessage", errorMessage);
            
            // Add context at time of error
            if (context != null) {
                entry.add("context", gson.toJsonTree(context));
            }
            if (volatileContext != null) {
                entry.add("volatileContext", gson.toJsonTree(volatileContext));
            }
            
            writeEntry(entry);
            
        } catch (Exception e) {
            System.err.println("[History] Error recording error: " + e.getMessage());
        }
    }
    
    private void writeEntry(JsonObject entry) throws IOException {
        if (writer != null) {
            writer.write(gson.toJson(entry));
            writer.newLine();
            writer.flush();
        }
    }
    
    /**
     * Close the history tracker and cleanup resources
     */
    public void close() {
        if (writer != null) {
            try {
                writer.flush();
                writer.close();
                isActive = false;
                activeTrackers.remove(machineId);
                System.out.println("[History] Closed history tracker for machine: " + machineId);
            } catch (IOException e) {
                System.err.println("[History] Error closing history tracker: " + e.getMessage());
            }
        }
    }
    
    /**
     * Get the path to the history file
     */
    public Path getHistoryFile() {
        return historyFile;
    }
    
    /**
     * Check if tracker is active
     */
    public boolean isActive() {
        return isActive;
    }
    
    /**
     * Get current step count
     */
    public int getStepCount() {
        return stepCounter.get();
    }
    
    /**
     * Clean up all active trackers (for shutdown)
     */
    public static void closeAllTrackers() {
        for (MachineHistoryTracker tracker : activeTrackers.values()) {
            tracker.close();
        }
        activeTrackers.clear();
    }
}