package com.telcobright.statemachine.history;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.telcobright.statemachine.StateMachineContextEntity;
import com.telcobright.statemachine.persistence.MysqlConnectionProvider;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * MySQL-based history tracker for state machines with asynchronous writing
 * Creates a table per machine instance for complete history tracking
 */
public class MachineHistoryMySQLTracker {
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
    private final String tableName;
    private final MysqlConnectionProvider connectionProvider;
    private final ExecutorService executorService;
    private final BlockingQueue<HistoryEntry> writeQueue;
    private final AtomicBoolean isActive;
    private final Future<?> writerTask;
    private final Map<String, Integer> stateTransitionCounters;  // Track transition counter per state
    
    // History entry for async writing
    private static class HistoryEntry {
        final Timestamp datetime;
        final String state;
        final String event;
        final boolean eventIgnored;
        final String eventPayload;
        final boolean transitionOrStay;
        final String transitionToState;
        final String persistentContext;
        final String volatileContext;
        final int transitionCounter;  // Track which instance of this state
        
        HistoryEntry(String state, String event, boolean eventIgnored, Object eventPayloadObj,
                    boolean transitionOrStay, String transitionToState,
                    Object persistentContextObj, Object volatileContextObj, int transitionCounter) {
            this.datetime = new Timestamp(System.currentTimeMillis());
            this.state = state;
            this.event = event;
            this.eventIgnored = eventIgnored;
            this.eventPayload = encodeToBase64(eventPayloadObj);
            this.transitionOrStay = transitionOrStay;
            this.transitionToState = transitionToState;
            this.persistentContext = encodeToBase64(persistentContextObj);
            this.volatileContext = encodeToBase64(volatileContextObj);
            this.transitionCounter = transitionCounter;
        }
        
        private static String encodeToBase64(Object obj) {
            if (obj == null) return null;
            try {
                String json = gson.toJson(obj);
                return Base64.getEncoder().encodeToString(json.getBytes());
            } catch (Exception e) {
                System.err.println("[History] Error encoding to Base64: " + e.getMessage());
                return null;
            }
        }
    }
    
    public MachineHistoryMySQLTracker(String machineId, MysqlConnectionProvider connectionProvider, 
                                     boolean debugEnabled) throws SQLException {
        if (!debugEnabled) {
            this.machineId = null;
            this.tableName = null;
            this.connectionProvider = null;
            this.executorService = null;
            this.writeQueue = null;
            this.isActive = new AtomicBoolean(false);
            this.writerTask = null;
            this.stateTransitionCounters = null;
            return;
        }
        
        this.machineId = machineId;
        // Create table name by sanitizing machine ID (replace non-alphanumeric with underscore)
        this.tableName = "history_" + machineId.replaceAll("[^a-zA-Z0-9]", "_");
        this.connectionProvider = connectionProvider;
        this.writeQueue = new LinkedBlockingQueue<>();
        this.isActive = new AtomicBoolean(true);
        this.stateTransitionCounters = new ConcurrentHashMap<>();
        
        // Create single-threaded executor for async writes
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "HistoryWriter-" + machineId);
            t.setDaemon(true);
            return t;
        });
        
        // Create/recreate table
        createOrRecreateTable();
        
        // Start the async writer task
        this.writerTask = executorService.submit(this::writerLoop);
        
        System.out.println("[History] Created MySQL history tracker for machine: " + machineId + " (table: " + tableName + ")");
    }
    
    private void createOrRecreateTable() throws SQLException {
        try (Connection conn = connectionProvider.getConnection()) {
            // Drop existing table if it exists (to start fresh for each run)
            String dropTableSQL = "DROP TABLE IF EXISTS " + tableName;
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(dropTableSQL);
            }
            
            // Create new table
            String createTableSQL = "CREATE TABLE " + tableName + " (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "datetime TIMESTAMP(3) NOT NULL, " +  // Millisecond precision
                "state VARCHAR(100) NOT NULL, " +
                "event VARCHAR(100) NOT NULL, " +
                "event_ignored BOOLEAN DEFAULT FALSE, " +
                "event_payload TEXT, " +  // Base64 encoded JSON
                "transition_or_stay BOOLEAN DEFAULT FALSE, " +  // TRUE = transition, FALSE = stay
                "transition_to_state VARCHAR(100), " +
                "transition_counter INT NOT NULL DEFAULT 1, " +  // Track state instance
                "persistent_context MEDIUMTEXT, " +  // Base64 encoded JSON
                "volatile_context MEDIUMTEXT, " +  // Base64 encoded JSON
                "INDEX idx_datetime (datetime), " +
                "INDEX idx_state (state), " +
                "INDEX idx_event (event), " +
                "INDEX idx_state_counter (state, transition_counter)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
                
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createTableSQL);
                System.out.println("[History] Created history table: " + tableName);
            }
        }
    }
    
    private void writerLoop() {
        while (isActive.get() || !writeQueue.isEmpty()) {
            try {
                // Wait for entries with timeout to allow checking isActive
                HistoryEntry entry = writeQueue.poll(100, TimeUnit.MILLISECONDS);
                if (entry != null) {
                    writeEntryToDatabase(entry);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[History] Error in writer loop: " + e.getMessage());
            }
        }
    }
    
    private void writeEntryToDatabase(HistoryEntry entry) {
        String insertSQL = "INSERT INTO " + tableName + 
            " (datetime, state, event, event_ignored, event_payload, transition_or_stay, " +
            "transition_to_state, transition_counter, persistent_context, volatile_context) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            
            pstmt.setTimestamp(1, entry.datetime);
            pstmt.setString(2, entry.state);
            pstmt.setString(3, entry.event);
            pstmt.setBoolean(4, entry.eventIgnored);
            pstmt.setString(5, entry.eventPayload);
            pstmt.setBoolean(6, entry.transitionOrStay);
            pstmt.setString(7, entry.transitionToState);
            pstmt.setInt(8, entry.transitionCounter);
            pstmt.setString(9, entry.persistentContext);
            pstmt.setString(10, entry.volatileContext);
            
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            System.err.println("[History] Error writing to database: " + e.getMessage());
        }
    }
    
    /**
     * Get or increment the transition counter for a state
     */
    private int getStateCounter(String state) {
        return stateTransitionCounters.computeIfAbsent(state, k -> 1);
    }
    
    /**
     * Increment and get the transition counter for entering a state
     */
    private int incrementStateCounter(String state) {
        return stateTransitionCounters.compute(state, (k, v) -> (v == null) ? 1 : v + 1);
    }
    
    /**
     * Record the initial state of the machine
     */
    public void recordInitialState(String state, StateMachineContextEntity contextEntity, Object volatileContext) {
        if (!isActive.get()) return;
        
        int counter = getStateCounter(state);
        
        HistoryEntry entry = new HistoryEntry(
            state,
            "ENTRY",  // Changed from INITIAL to ENTRY
            false,  // not ignored
            null,   // no event payload
            false,  // stay (not a transition)
            null,   // no transition
            contextEntity,
            volatileContext,
            counter
        );
        
        writeQueue.offer(entry);
    }
    
    /**
     * Record an event being sent to the machine
     */
    public void recordEventReceived(String currentState, String eventType, Object eventPayload, 
                                   StateMachineContextEntity contextBefore, Object volatileContextBefore) {
        // This is recorded as part of the transition or no-transition record
        // We don't need a separate entry for event received
    }
    
    /**
     * Record a state transition with entry action support
     */
    public void recordStateTransition(String fromState, String toState, String eventType, Object eventPayload,
                                     StateMachineContextEntity contextAfter, Object volatileContextAfter,
                                     long transitionDurationMs, String entryActionStatus) {
        if (!isActive.get()) return;
        
        // Get current counter for the from state
        int fromCounter = getStateCounter(fromState);
        
        // Increment counter for the target state (re-entering)
        int toCounter = incrementStateCounter(toState);
        
        // Record the event in the source state (showing transition)
        HistoryEntry fromEntry = new HistoryEntry(
            fromState,
            eventType,
            false,  // not ignored
            eventPayload,
            true,   // transition
            toState,
            contextAfter,  // Context after transition
            volatileContextAfter,
            fromCounter
        );
        writeQueue.offer(fromEntry);
        
        // Now record the entry into the target state (after the transition)
        HistoryEntry entryRecord = new HistoryEntry(
            toState,
            "ENTRY",
            false,  // not ignored
            null,   // no event payload
            false,  // stay
            null,   // no transition
            contextAfter,
            volatileContextAfter,
            toCounter
        );
        writeQueue.offer(entryRecord);
    }
    
    /**
     * Record entry into a state (with or without entry actions)
     */
    public void recordStateEntry(String state, StateMachineContextEntity contextBefore, Object volatileContextBefore,
                                StateMachineContextEntity contextAfter, Object volatileContextAfter, 
                                String entryActionStatus) {
        if (!isActive.get()) return;
        
        int counter = getStateCounter(state);
        
        if (entryActionStatus != null && entryActionStatus.equals("executed")) {
            // Record "Before Entry Actions" with context before entry actions
            HistoryEntry beforeEntry = new HistoryEntry(
                state,
                "BEFORE_ENTRY_ACTIONS",
                false,  // not ignored
                null,   // no event payload
                false,  // stay
                null,   // no transition
                contextBefore,
                volatileContextBefore,
                counter
            );
            writeQueue.offer(beforeEntry);
            
            // Record "After Entry Actions" with context after entry actions
            HistoryEntry afterEntry = new HistoryEntry(
                state,
                "AFTER_ENTRY_ACTIONS",
                false,  // not ignored
                null,   // no event payload
                false,  // stay
                null,   // no transition
                contextAfter,
                volatileContextAfter,
                counter
            );
            writeQueue.offer(afterEntry);
        } else {
            // No entry actions or not executed - just record "Entry"
            HistoryEntry entry = new HistoryEntry(
                state,
                "ENTRY",
                false,  // not ignored
                null,   // no event payload
                false,  // stay
                null,   // no transition
                contextAfter,
                volatileContextAfter,
                counter
            );
            writeQueue.offer(entry);
        }
    }
    
    /**
     * Record an event that didn't cause a state change
     */
    public void recordEventNoTransition(String currentState, String eventType, Object eventPayload,
                                       StateMachineContextEntity contextAfter, Object volatileContextAfter,
                                       long processingDurationMs) {
        if (!isActive.get()) return;
        
        int counter = getStateCounter(currentState);
        
        HistoryEntry entry = new HistoryEntry(
            currentState,
            eventType,
            false,  // not ignored (it was processed)
            eventPayload,
            false,  // stay (no transition)
            null,   // no transition
            contextAfter,
            volatileContextAfter,
            counter
        );
        
        writeQueue.offer(entry);
    }
    
    /**
     * Record an ignored event
     */
    public void recordEventIgnored(String currentState, String eventType, Object eventPayload,
                                  StateMachineContextEntity context, Object volatileContext) {
        if (!isActive.get()) return;
        
        int counter = getStateCounter(currentState);
        
        HistoryEntry entry = new HistoryEntry(
            currentState,
            eventType,
            true,   // ignored
            eventPayload,
            false,  // stay
            null,   // no transition
            context,
            volatileContext,
            counter
        );
        
        writeQueue.offer(entry);
    }
    
    /**
     * Record a timeout event
     */
    public void recordTimeout(String fromState, String toState, long timeoutDurationMs,
                             StateMachineContextEntity contextAfter, Object volatileContextAfter) {
        if (!isActive.get()) return;
        
        int fromCounter = getStateCounter(fromState);
        int toCounter = incrementStateCounter(toState);
        
        // Record timeout as a transition
        HistoryEntry fromEntry = new HistoryEntry(
            fromState,
            "TIMEOUT",
            false,  // not ignored
            null,   // no payload for timeout
            true,   // transition
            toState,
            contextAfter,
            volatileContextAfter,
            fromCounter
        );
        writeQueue.offer(fromEntry);
        
        // Record arrival in target state
        HistoryEntry toEntry = new HistoryEntry(
            toState,
            "TIMEOUT_ARRIVAL",
            false,  // not ignored
            null,
            false,  // stay
            null,
            contextAfter,
            volatileContextAfter,
            toCounter
        );
        writeQueue.offer(toEntry);
    }
    
    /**
     * Record machine completion
     */
    public void recordCompletion(String finalState, StateMachineContextEntity finalContext, Object finalVolatileContext) {
        if (!isActive.get()) return;
        
        int counter = getStateCounter(finalState);
        
        HistoryEntry entry = new HistoryEntry(
            finalState,
            "COMPLETION",
            false,  // not ignored
            null,   // no payload
            false,  // stay
            null,   // no transition
            finalContext,
            finalVolatileContext,
            counter
        );
        
        writeQueue.offer(entry);
    }
    
    /**
     * Record an error that occurred
     */
    public void recordError(String currentState, String errorType, String errorMessage, 
                           StateMachineContextEntity context, Object volatileContext) {
        if (!isActive.get()) return;
        
        int counter = getStateCounter(currentState);
        
        HistoryEntry entry = new HistoryEntry(
            currentState,
            "ERROR_" + errorType,
            false,  // not ignored
            errorMessage,  // Use error message as payload
            false,  // stay
            null,
            context,
            volatileContext,
            counter
        );
        
        writeQueue.offer(entry);
    }
    
    /**
     * Close the history tracker and cleanup resources
     */
    public void close() {
        if (!isActive.compareAndSet(true, false)) {
            return; // Already closed
        }
        
        try {
            // Wait for remaining entries to be written (max 5 seconds)
            long startTime = System.currentTimeMillis();
            while (!writeQueue.isEmpty() && (System.currentTimeMillis() - startTime) < 5000) {
                Thread.sleep(100);
            }
            
            // Shutdown executor
            executorService.shutdown();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            
            System.out.println("[History] Closed MySQL history tracker for machine: " + machineId);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
    }
    
    /**
     * Check if tracker is active
     */
    public boolean isActive() {
        return isActive.get();
    }
    
    /**
     * Get the table name
     */
    public String getTableName() {
        return tableName;
    }
    
    /**
     * Read all history entries for this machine
     */
    public List<Map<String, Object>> readHistory() {
        List<Map<String, Object>> history = new ArrayList<>();
        
        if (connectionProvider == null || tableName == null) {
            return history;
        }
        
        String query = "SELECT * FROM " + tableName + " ORDER BY datetime ASC, id ASC";
        
        try (Connection conn = connectionProvider.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            while (rs.next()) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("id", rs.getInt("id"));
                entry.put("datetime", rs.getTimestamp("datetime"));
                entry.put("state", rs.getString("state"));
                entry.put("event", rs.getString("event"));
                entry.put("eventIgnored", rs.getBoolean("event_ignored"));
                entry.put("eventPayload", decodeFromBase64(rs.getString("event_payload")));
                entry.put("transitionOrStay", rs.getBoolean("transition_or_stay"));
                entry.put("transitionToState", rs.getString("transition_to_state"));
                entry.put("transitionCounter", rs.getInt("transition_counter"));
                entry.put("persistentContext", decodeFromBase64(rs.getString("persistent_context")));
                entry.put("volatileContext", decodeFromBase64(rs.getString("volatile_context")));
                history.add(entry);
            }
        } catch (SQLException e) {
            System.err.println("[History] Error reading history: " + e.getMessage());
        }
        
        return history;
    }
    
    /**
     * Read history entries since a specific ID (for incremental updates)
     */
    public List<Map<String, Object>> readHistorySince(int lastId) {
        List<Map<String, Object>> history = new ArrayList<>();
        
        if (connectionProvider == null || tableName == null) {
            return history;
        }
        
        String query = "SELECT * FROM " + tableName + " WHERE id > ? ORDER BY datetime ASC, id ASC";
        
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            
            pstmt.setInt(1, lastId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("id", rs.getInt("id"));
                    entry.put("datetime", rs.getTimestamp("datetime"));
                    entry.put("state", rs.getString("state"));
                    entry.put("event", rs.getString("event"));
                    entry.put("eventIgnored", rs.getBoolean("event_ignored"));
                    entry.put("eventPayload", decodeFromBase64(rs.getString("event_payload")));
                    entry.put("transitionOrStay", rs.getBoolean("transition_or_stay"));
                    entry.put("transitionToState", rs.getString("transition_to_state"));
                    entry.put("transitionCounter", rs.getInt("transition_counter"));
                    entry.put("persistentContext", decodeFromBase64(rs.getString("persistent_context")));
                    entry.put("volatileContext", decodeFromBase64(rs.getString("volatile_context")));
                    history.add(entry);
                }
            }
        } catch (SQLException e) {
            System.err.println("[History] Error reading history since ID " + lastId + ": " + e.getMessage());
        }
        
        return history;
    }
    
    /**
     * Get the latest history entry ID
     */
    public int getLatestHistoryId() {
        if (connectionProvider == null || tableName == null) {
            return 0;
        }
        
        String query = "SELECT MAX(id) as max_id FROM " + tableName;
        
        try (Connection conn = connectionProvider.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            if (rs.next()) {
                return rs.getInt("max_id");
            }
        } catch (SQLException e) {
            System.err.println("[History] Error getting latest ID: " + e.getMessage());
        }
        
        return 0;
    }
    
    /**
     * Build grouped history structure for frontend
     * Groups history entries by state and transition_counter
     */
    public List<Map<String, Object>> readGroupedHistory() {
        System.out.println("[History] readGroupedHistory called for machine: " + machineId);
        List<Map<String, Object>> rawHistory = readHistory();
        System.out.println("[History] Raw history size: " + rawHistory.size());
        List<Map<String, Object>> groupedHistory = new ArrayList<>();
        
        // Map to track state instances: key = "state-counter", value = list of events
        Map<String, List<Map<String, Object>>> stateInstances = new LinkedHashMap<>();
        
        int nextId = 1000000; // Start synthetic IDs at a high number to avoid conflicts
        
        for (Map<String, Object> entry : rawHistory) {
            String state = (String) entry.get("state");
            Integer counter = (Integer) entry.get("transitionCounter");
            String instanceKey = state + "-" + counter;
            
            // Get or create the list for this state instance
            List<Map<String, Object>> instanceEvents = stateInstances.computeIfAbsent(
                instanceKey, k -> new ArrayList<>()
            );
            
            // Add this entry to the instance
            instanceEvents.add(entry);
            
            // If this event causes a transition, add a synthetic TRANSITION event
            Boolean transitionOrStay = (Boolean) entry.get("transitionOrStay");
            String transitionToState = (String) entry.get("transitionToState");
            if (transitionOrStay != null && transitionOrStay && transitionToState != null) {
                // Create a synthetic transition event
                Map<String, Object> transitionEvent = new HashMap<>();
                transitionEvent.put("id", nextId++);
                transitionEvent.put("state", state);
                transitionEvent.put("event", "TRANSITION");
                transitionEvent.put("eventIgnored", false);
                transitionEvent.put("transitionOrStay", true);
                transitionEvent.put("transitionToState", transitionToState);
                transitionEvent.put("transitionCounter", counter);
                transitionEvent.put("datetime", entry.get("datetime"));
                transitionEvent.put("persistentContext", entry.get("persistentContext"));
                transitionEvent.put("volatileContext", entry.get("volatileContext"));
                
                // Add the transition event to the same state instance
                instanceEvents.add(transitionEvent);
            }
        }
        
        // Convert to the format expected by frontend
        for (Map.Entry<String, List<Map<String, Object>>> instance : stateInstances.entrySet()) {
            Map<String, Object> stateInstance = new HashMap<>();
            String[] parts = instance.getKey().split("-");
            stateInstance.put("state", parts[0]);
            stateInstance.put("instanceNumber", Integer.parseInt(parts[1]));
            stateInstance.put("transitions", instance.getValue());
            groupedHistory.add(stateInstance);
        }
        
        System.out.println("[History] Grouped history size: " + groupedHistory.size() + " state instances");
        for (Map<String, Object> group : groupedHistory) {
            System.out.println("[History] State instance: " + group.get("state") + "-" + group.get("instanceNumber") +
                " has " + ((List)group.get("transitions")).size() + " transitions");
        }
        
        return groupedHistory;
    }
    
    /**
     * Decode from Base64
     */
    private static Object decodeFromBase64(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(base64);
            String json = new String(decoded);
            return gson.fromJson(json, Object.class);
        } catch (Exception e) {
            System.err.println("[History] Error decoding from Base64: " + e.getMessage());
            return null;
        }
    }
}