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
        
        HistoryEntry(String state, String event, boolean eventIgnored, Object eventPayloadObj,
                    boolean transitionOrStay, String transitionToState,
                    Object persistentContextObj, Object volatileContextObj) {
            this.datetime = new Timestamp(System.currentTimeMillis());
            this.state = state;
            this.event = event;
            this.eventIgnored = eventIgnored;
            this.eventPayload = encodeToBase64(eventPayloadObj);
            this.transitionOrStay = transitionOrStay;
            this.transitionToState = transitionToState;
            this.persistentContext = encodeToBase64(persistentContextObj);
            this.volatileContext = encodeToBase64(volatileContextObj);
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
            return;
        }
        
        this.machineId = machineId;
        // Create table name by sanitizing machine ID (replace non-alphanumeric with underscore)
        this.tableName = "history_" + machineId.replaceAll("[^a-zA-Z0-9]", "_");
        this.connectionProvider = connectionProvider;
        this.writeQueue = new LinkedBlockingQueue<>();
        this.isActive = new AtomicBoolean(true);
        
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
                "persistent_context MEDIUMTEXT, " +  // Base64 encoded JSON
                "volatile_context MEDIUMTEXT, " +  // Base64 encoded JSON
                "INDEX idx_datetime (datetime), " +
                "INDEX idx_state (state), " +
                "INDEX idx_event (event)" +
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
            "transition_to_state, persistent_context, volatile_context) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            
            pstmt.setTimestamp(1, entry.datetime);
            pstmt.setString(2, entry.state);
            pstmt.setString(3, entry.event);
            pstmt.setBoolean(4, entry.eventIgnored);
            pstmt.setString(5, entry.eventPayload);
            pstmt.setBoolean(6, entry.transitionOrStay);
            pstmt.setString(7, entry.transitionToState);
            pstmt.setString(8, entry.persistentContext);
            pstmt.setString(9, entry.volatileContext);
            
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            System.err.println("[History] Error writing to database: " + e.getMessage());
        }
    }
    
    /**
     * Record the initial state of the machine
     */
    public void recordInitialState(String state, StateMachineContextEntity contextEntity, Object volatileContext) {
        if (!isActive.get()) return;
        
        HistoryEntry entry = new HistoryEntry(
            state,
            "INITIAL",
            false,  // not ignored
            null,   // no event payload
            false,  // stay (not a transition)
            null,   // no transition
            contextEntity,
            volatileContext
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
     * Record a state transition
     */
    public void recordStateTransition(String fromState, String toState, String eventType, Object eventPayload,
                                     StateMachineContextEntity contextAfter, Object volatileContextAfter,
                                     long transitionDurationMs, String entryActionStatus) {
        if (!isActive.get()) return;
        
        // Record the event in the source state
        HistoryEntry fromEntry = new HistoryEntry(
            fromState,
            eventType,
            false,  // not ignored
            eventPayload,
            true,   // transition
            toState,
            contextAfter,  // Context after transition
            volatileContextAfter
        );
        writeQueue.offer(fromEntry);
        
        // Record arrival in the target state
        HistoryEntry toEntry = new HistoryEntry(
            toState,
            eventType + "_ARRIVAL",  // Mark as arrival from event
            false,  // not ignored
            eventPayload,
            false,  // stay (this is the arrival record)
            null,   // no further transition
            contextAfter,  // Same context as after transition
            volatileContextAfter
        );
        writeQueue.offer(toEntry);
    }
    
    /**
     * Record an event that didn't cause a state change
     */
    public void recordEventNoTransition(String currentState, String eventType, Object eventPayload,
                                       StateMachineContextEntity contextAfter, Object volatileContextAfter,
                                       long processingDurationMs) {
        if (!isActive.get()) return;
        
        HistoryEntry entry = new HistoryEntry(
            currentState,
            eventType,
            false,  // not ignored (it was processed)
            eventPayload,
            false,  // stay (no transition)
            null,   // no transition
            contextAfter,
            volatileContextAfter
        );
        
        writeQueue.offer(entry);
    }
    
    /**
     * Record an ignored event
     */
    public void recordEventIgnored(String currentState, String eventType, Object eventPayload,
                                  StateMachineContextEntity context, Object volatileContext) {
        if (!isActive.get()) return;
        
        HistoryEntry entry = new HistoryEntry(
            currentState,
            eventType,
            true,   // ignored
            eventPayload,
            false,  // stay
            null,   // no transition
            context,
            volatileContext
        );
        
        writeQueue.offer(entry);
    }
    
    /**
     * Record a timeout event
     */
    public void recordTimeout(String fromState, String toState, long timeoutDurationMs,
                             StateMachineContextEntity contextAfter, Object volatileContextAfter) {
        if (!isActive.get()) return;
        
        // Record timeout as a transition
        HistoryEntry fromEntry = new HistoryEntry(
            fromState,
            "TIMEOUT",
            false,  // not ignored
            null,   // no payload for timeout
            true,   // transition
            toState,
            contextAfter,
            volatileContextAfter
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
            volatileContextAfter
        );
        writeQueue.offer(toEntry);
    }
    
    /**
     * Record machine completion
     */
    public void recordCompletion(String finalState, StateMachineContextEntity finalContext, Object finalVolatileContext) {
        if (!isActive.get()) return;
        
        HistoryEntry entry = new HistoryEntry(
            finalState,
            "COMPLETION",
            false,  // not ignored
            null,   // no payload
            false,  // stay
            null,   // no transition
            finalContext,
            finalVolatileContext
        );
        
        writeQueue.offer(entry);
    }
    
    /**
     * Record an error that occurred
     */
    public void recordError(String currentState, String errorType, String errorMessage, 
                           StateMachineContextEntity context, Object volatileContext) {
        if (!isActive.get()) return;
        
        HistoryEntry entry = new HistoryEntry(
            currentState,
            "ERROR_" + errorType,
            false,  // not ignored
            errorMessage,  // Use error message as payload
            false,  // stay
            null,
            context,
            volatileContext
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
}