package com.telcobright.statemachine.monitoring;

import com.telcobright.statemachine.StateMachineContextEntity;
import com.telcobright.statemachine.persistence.StateMachineSnapshotRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * Grafana Integration for State Machine Monitoring
 * 
 * Provides direct PostgreSQL integration for optimal Grafana performance
 * Handles Base64 encoding/decoding and entity-specific snapshot storage
 */
public class GrafanaIntegration {
    
    private final Connection dbConnection;
    private final SnapshotSerializationUtils serializationUtils;
    
    // Prepared statements for high-performance inserts
    private final PreparedStatement insertSnapshot;
    private final PreparedStatement insertCallSnapshot;
    
    public GrafanaIntegration(Connection dbConnection) throws SQLException {
        this.dbConnection = dbConnection;
        this.serializationUtils = new SnapshotSerializationUtils();
        
        // Prepare optimized insert statements
        this.insertSnapshot = dbConnection.prepareStatement(
            "INSERT INTO state_machine_snapshots (" +
            "machine_id, machine_type, version, run_id, correlation_id, debug_session_id, " +
            "state_before, state_after, event_type, transition_duration, timestamp, " +
            "machine_online_status, state_offline_status, registry_status, " +
            "event_payload_json, event_parameters_json, context_before_json, " +
            "context_before_hash, context_after_json, context_after_hash" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "RETURNING id"
        );
        
        this.insertCallSnapshot = dbConnection.prepareStatement(
            "INSERT INTO call_entity_snapshots (" +
            "snapshot_id, from_number, to_number, call_type, ring_count, " +
            "call_duration_ms, disconnect_reason, recording_enabled" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        );
    }
    
    /**
     * Save snapshot directly to PostgreSQL for Grafana visualization
     */
    public CompletableFuture<Void> saveSnapshotForGrafana(MachineSnapshot snapshot, Object context) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Insert main snapshot record
                long snapshotId = insertMainSnapshot(snapshot);
                
                // Insert entity-specific data if applicable
                if ("CallEntity".equals(snapshot.getMachineType()) && context != null) {
                    insertCallSpecificData(snapshotId, context, snapshot);
                }
                
                // Commit transaction
                dbConnection.commit();
                
            } catch (SQLException e) {
                try {
                    dbConnection.rollback();
                } catch (SQLException rollbackEx) {
                    System.err.println("Failed to rollback transaction: " + rollbackEx.getMessage());
                }
                throw new RuntimeException("Failed to save snapshot for Grafana", e);
            }
        });
    }
    
    /**
     * Insert main snapshot data with Base64 encoding
     */
    private long insertMainSnapshot(MachineSnapshot snapshot) throws SQLException {
        insertSnapshot.setString(1, snapshot.getMachineId());
        insertSnapshot.setString(2, snapshot.getMachineType());
        insertSnapshot.setLong(3, snapshot.getVersion());
        insertSnapshot.setString(4, snapshot.getRunId());
        insertSnapshot.setString(5, snapshot.getCorrelationId());
        insertSnapshot.setString(6, snapshot.getDebugSessionId());
        insertSnapshot.setString(7, snapshot.getStateBefore());
        insertSnapshot.setString(8, snapshot.getStateAfter());
        insertSnapshot.setString(9, snapshot.getEventType());
        insertSnapshot.setLong(10, snapshot.getTransitionDuration());
        insertSnapshot.setObject(11, snapshot.getTimestamp());
        insertSnapshot.setBoolean(12, snapshot.getMachineOnlineStatus());
        insertSnapshot.setBoolean(13, snapshot.getStateOfflineStatus());
        insertSnapshot.setString(14, snapshot.getRegistryStatus());
        
        // Base64 encoded JSON data (already encoded by DatabaseSnapshotRecorder)
        insertSnapshot.setString(15, snapshot.getEventPayloadJson());
        insertSnapshot.setString(16, snapshot.getEventParametersJson());
        insertSnapshot.setString(17, snapshot.getContextBeforeJson());
        insertSnapshot.setString(18, snapshot.getContextBeforeHash());
        insertSnapshot.setString(19, snapshot.getContextAfterJson());
        insertSnapshot.setString(20, snapshot.getContextAfterHash());
        
        var rs = insertSnapshot.executeQuery();
        if (rs.next()) {
            return rs.getLong("id");
        }
        throw new SQLException("Failed to get generated snapshot ID");
    }
    
    /**
     * Insert call-specific data for enhanced Grafana analytics
     */
    private void insertCallSpecificData(long snapshotId, Object context, MachineSnapshot snapshot) throws SQLException {
        try {
            // Extract call-specific fields using reflection
            Class<?> contextClass = context.getClass();
            
            String fromNumber = getFieldValue(context, contextClass, "fromNumber", String.class);
            String toNumber = getFieldValue(context, contextClass, "toNumber", String.class);
            String callType = getFieldValue(context, contextClass, "callType", String.class);
            Integer ringCount = getFieldValue(context, contextClass, "ringCount", Integer.class);
            String disconnectReason = getFieldValue(context, contextClass, "disconnectReason", String.class);
            Boolean recordingEnabled = getFieldValue(context, contextClass, "recordingEnabled", Boolean.class);
            
            // Calculate call duration from context
            Long callDurationMs = null;
            try {
                Object duration = contextClass.getMethod("getCallDuration").invoke(context);
                if (duration instanceof java.time.Duration) {
                    callDurationMs = ((java.time.Duration) duration).toMillis();
                }
            } catch (Exception e) {
                // Duration not available - that's ok
            }
            
            // Insert call-specific data
            insertCallSnapshot.setLong(1, snapshotId);
            insertCallSnapshot.setString(2, fromNumber);
            insertCallSnapshot.setString(3, toNumber);
            insertCallSnapshot.setString(4, callType);
            insertCallSnapshot.setObject(5, ringCount);
            insertCallSnapshot.setObject(6, callDurationMs);
            insertCallSnapshot.setString(7, disconnectReason);
            insertCallSnapshot.setObject(8, recordingEnabled);
            
            insertCallSnapshot.executeUpdate();
            
        } catch (Exception e) {
            System.err.println("Warning: Could not extract call-specific data for Grafana: " + e.getMessage());
            // Don't fail the main snapshot save for this
        }
    }
    
    /**
     * Generic field extraction helper
     */
    @SuppressWarnings("unchecked")
    private <T> T getFieldValue(Object context, Class<?> contextClass, String fieldName, Class<T> expectedType) {
        try {
            // Try getter method first
            String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            Object value = contextClass.getMethod(getterName).invoke(context);
            if (value != null && expectedType.isAssignableFrom(value.getClass())) {
                return (T) value;
            }
        } catch (Exception e1) {
            try {
                // Try direct field access
                java.lang.reflect.Field field = contextClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(context);
                if (value != null && expectedType.isAssignableFrom(value.getClass())) {
                    return (T) value;
                }
            } catch (Exception e2) {
                // Field doesn't exist or not accessible - return null
            }
        }
        return null;
    }
    
    /**
     * Create optimized Grafana-ready snapshot recorder
     */
    public static <TPersistingEntity extends StateMachineContextEntity<?>, TContext> 
           DatabaseSnapshotRecorder<TPersistingEntity, TContext> createGrafanaRecorder(
               Connection dbConnection, 
               Class<TPersistingEntity> entityClass,
               SnapshotConfig config) throws SQLException {
        
        // Create Grafana integration
        GrafanaIntegration grafanaIntegration = new GrafanaIntegration(dbConnection);
        
        // Create custom repository that saves directly to PostgreSQL
        StateMachineSnapshotRepository grafanaRepository = new StateMachineSnapshotRepository() {
            @Override
            public void saveAsync(com.telcobright.statemachine.persistence.StateMachineSnapshotEntity entity) {
                // This won't be used - we'll override the recorder to use GrafanaIntegration directly
                throw new UnsupportedOperationException("Use GrafanaIntegration.saveSnapshotForGrafana instead");
            }
            
            @Override
            public java.util.Optional<com.telcobright.statemachine.persistence.StateMachineSnapshotEntity> 
                   findLatestByMachineId(String machineId) {
                return java.util.Optional.empty();
            }
            
            @Override
            public java.util.List<com.telcobright.statemachine.persistence.StateMachineSnapshotEntity> 
                   findAllByMachineId(String machineId) {
                return java.util.List.of();
            }
            
            @Override
            public void deleteByMachineId(String machineId) {}
            
            @Override
            public java.util.List<com.telcobright.statemachine.persistence.StateMachineSnapshotEntity> 
                   findAllOfflineSnapshots() {
                return java.util.List.of();
            }
        };
        
        return new DatabaseSnapshotRecorder<TPersistingEntity, TContext>(config, grafanaRepository, entityClass) {
            @Override
            public void recordTransition(String machineId, String machineType, Long version,
                                       String stateBefore, String stateAfter, 
                                       com.telcobright.statemachine.events.StateMachineEvent event,
                                       TContext contextBefore, TContext contextAfter, long transitionDuration,
                                       String runId, String correlationId, String debugSessionId,
                                       boolean machineOnlineStatus, boolean stateOfflineStatus, 
                                       String registryStatus) {
                
                // Call parent to handle in-memory storage and HTML generation
                super.recordTransition(machineId, machineType, version, stateBefore, stateAfter, event,
                                     contextBefore, contextAfter, transitionDuration, runId, correlationId,
                                     debugSessionId, machineOnlineStatus, stateOfflineStatus, registryStatus);
                
                // Additionally save directly to PostgreSQL for Grafana
                try {
                    MachineSnapshot snapshot = createSnapshotForGrafana(machineId, machineType, version,
                                                                      stateBefore, stateAfter, event,
                                                                      contextBefore, contextAfter, transitionDuration,
                                                                      runId, correlationId, debugSessionId,
                                                                      machineOnlineStatus, stateOfflineStatus, registryStatus);
                    
                    grafanaIntegration.saveSnapshotForGrafana(snapshot, contextAfter);
                    
                } catch (Exception e) {
                    System.err.println("Warning: Failed to save snapshot for Grafana: " + e.getMessage());
                    // Don't let Grafana issues break the state machine
                }
            }
            
            private MachineSnapshot createSnapshotForGrafana(String machineId, String machineType, Long version,
                                                           String stateBefore, String stateAfter, 
                                                           com.telcobright.statemachine.events.StateMachineEvent event,
                                                           TContext contextBefore, TContext contextAfter, long transitionDuration,
                                                           String runId, String correlationId, String debugSessionId,
                                                           boolean machineOnlineStatus, boolean stateOfflineStatus, String registryStatus) {
                
                DefaultMachineSnapshot snapshot = new DefaultMachineSnapshot();
                snapshot.setMachineId(machineId);
                snapshot.setMachineType(machineType);
                snapshot.setVersion(version);
                snapshot.setStateBefore(stateBefore);
                snapshot.setStateAfter(stateAfter);
                snapshot.setEventType(event.getEventType());
                snapshot.setTransitionDurationMillis(transitionDuration);
                snapshot.setCreatedAt(LocalDateTime.now());
                snapshot.setRunId(runId);
                snapshot.setCorrelationId(correlationId);
                snapshot.setDebugSessionId(debugSessionId);
                snapshot.setMachineOnlineStatus(machineOnlineStatus);
                snapshot.setStateOfflineStatus(stateOfflineStatus);
                snapshot.setRegistryStatus(registryStatus);
                
                // Encode data for storage
                SnapshotSerializationUtils utils = new SnapshotSerializationUtils();
                try {
                    if (contextAfter != null) {
                        String contextJson = utils.serializeContextToJson(contextAfter, config.getRedactionFields());
                        snapshot.setContextJsonAfter(utils.encodeToBase64(contextJson));
                        snapshot.setContextHashAfter(utils.generateHash(contextJson));
                    }
                    
                    String eventJson = utils.serializeEventToJson(event);
                    snapshot.setEventPayloadJson(utils.encodeToBase64(eventJson));
                    
                    String eventParamsJson = utils.serializeEventParametersToJson(event);
                    snapshot.setEventParametersJson(utils.encodeToBase64(eventParamsJson));
                    
                } catch (Exception e) {
                    System.err.println("Warning: Failed to serialize data for Grafana: " + e.getMessage());
                }
                
                return snapshot;
            }
        };
    }
    
    /**
     * Test Grafana connectivity and schema
     */
    public boolean testGrafanaIntegration() {
        try {
            var rs = dbConnection.createStatement().executeQuery(
                "SELECT COUNT(*) FROM state_machine_snapshots WHERE timestamp >= NOW() - INTERVAL '1 hour'"
            );
            
            if (rs.next()) {
                int recentSnapshots = rs.getInt(1);
                System.out.println("✅ Grafana integration test passed. Recent snapshots: " + recentSnapshots);
                return true;
            }
        } catch (SQLException e) {
            System.err.println("❌ Grafana integration test failed: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Close prepared statements and connection
     */
    public void close() throws SQLException {
        if (insertSnapshot != null) insertSnapshot.close();
        if (insertCallSnapshot != null) insertCallSnapshot.close();
        if (dbConnection != null) dbConnection.close();
    }
}