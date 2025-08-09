package com.telcobright.statemachine.monitoring;

import com.telcobright.statemachine.StateMachineContextEntity;
import com.telcobright.statemachine.events.StateMachineEvent;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.IOException;

/**
 * Default implementation of SnapshotRecorder that creates snapshot entities in memory.
 * This implementation is suitable for testing and development scenarios.
 * 
 * @param <TPersistingEntity> The persisting entity type
 * @param <TContext> The volatile context type
 */
public class DefaultSnapshotRecorder<TPersistingEntity extends StateMachineContextEntity<?>, TContext> 
        implements SnapshotRecorder<TPersistingEntity, TContext> {
    
    private final ConcurrentHashMap<String, AtomicLong> versionCounters = new ConcurrentHashMap<>();
    private final SnapshotSerializationUtils serializationUtils;
    private final ExecutorService asyncExecutor;
    private final SnapshotHistoryViewer historyViewer;
    private SnapshotConfig config;
    
    public DefaultSnapshotRecorder() {
        this(SnapshotConfig.defaultConfig());
    }
    
    public DefaultSnapshotRecorder(SnapshotConfig config) {
        this.config = config;
        this.serializationUtils = new SnapshotSerializationUtils();
        this.historyViewer = new SnapshotHistoryViewer();
        this.asyncExecutor = config.isAsync() ? 
            Executors.newFixedThreadPool(Math.max(1, config.getAsyncQueueSize() / 100)) : null;
    }
    
    @Override
    public void recordTransition(String machineId, String machineType, Long version, 
                                String stateBefore, String stateAfter, StateMachineEvent event, 
                                TContext contextBefore, TContext contextAfter, 
                                long transitionDurationMillis, String runId, 
                                String correlationId, String debugSessionId,
                                boolean machineOnlineStatus, boolean stateOfflineStatus,
                                String registryStatus) {
        
        if (!config.isEnabled()) {
            return;
        }
        
        try {
            if (config.isAsync() && asyncExecutor != null) {
                CompletableFuture.runAsync(() -> doRecordTransition(
                    machineId, machineType, version, stateBefore, stateAfter, 
                    event, contextBefore, contextAfter, transitionDurationMillis,
                    runId, correlationId, debugSessionId, machineOnlineStatus, 
                    stateOfflineStatus, registryStatus
                ), asyncExecutor).exceptionally(throwable -> {
                    System.err.println("Async snapshot recording failed for machine " + machineId + ": " + throwable.getMessage());
                    return null;
                });
            } else {
                doRecordTransition(machineId, machineType, version, stateBefore, stateAfter,
                    event, contextBefore, contextAfter, transitionDurationMillis,
                    runId, correlationId, debugSessionId, machineOnlineStatus,
                    stateOfflineStatus, registryStatus);
            }
        } catch (Exception e) {
            System.err.println("Failed to record snapshot for machine " + machineId + ": " + e.getMessage());
            // For now, just log the error - we can add a failOnError config later if needed
            // throw new RuntimeException("Snapshot recording failed", e);
        }
    }
    
    private void doRecordTransition(String machineId, String machineType, Long version,
                                   String stateBefore, String stateAfter, StateMachineEvent event,
                                   TContext contextBefore, TContext contextAfter,
                                   long transitionDurationMillis, String runId,
                                   String correlationId, String debugSessionId,
                                   boolean machineOnlineStatus, boolean stateOfflineStatus,
                                   String registryStatus) {
        
        // Create snapshot entity using the abstract base class
        DefaultMachineSnapshot snapshot = new DefaultMachineSnapshot();
        
        // Generate unique snapshot ID
        snapshot.setSnapshotId(UUID.randomUUID().toString());
        
        // Set basic machine info
        snapshot.setMachineId(machineId);
        snapshot.setMachineType(machineType);
        snapshot.setVersion(version);
        
        // Set state transition info
        snapshot.setStateBefore(stateBefore);
        snapshot.setStateAfter(stateAfter);
        snapshot.setTransitionDurationMillis(transitionDurationMillis);
        
        // Set correlation IDs
        snapshot.setRunId(runId);
        snapshot.setCorrelationId(correlationId);
        snapshot.setDebugSessionId(debugSessionId);
        
        // Set timestamp
        snapshot.setCreatedAt(LocalDateTime.now());
        
        // Set status information
        snapshot.setMachineOnlineStatus(machineOnlineStatus);
        snapshot.setStateOfflineStatus(stateOfflineStatus);
        snapshot.setRegistryStatus(registryStatus);
        
        // Serialize event with full payload and parameters
        if (event != null) {
            snapshot.setEventType(event.getEventType());
            try {
                // Serialize complete event payload
                snapshot.setEventPayloadJson(serializationUtils.serializeEventToJson(event));
                
                // Serialize event parameters separately
                snapshot.setEventParametersJson(serializationUtils.serializeEventParametersToJson(event));
            } catch (Exception e) {
                System.err.println("Failed to serialize event: " + e.getMessage());
            }
        }
        
        // Serialize context before (if available)
        if (contextBefore != null && config.isStoreBeforeJson()) {
            try {
                snapshot.setContextJsonBefore(
                    serializationUtils.serializeToBase64Json(contextBefore, config.isRedactSensitiveFields())
                );
                snapshot.setContextHashBefore(
                    serializationUtils.computeContextHash(contextBefore, config.isRedactSensitiveFields())
                );
            } catch (Exception e) {
                System.err.println("Failed to serialize context before: " + e.getMessage());
            }
        }
        
        // Serialize context after
        if (contextAfter != null && config.isStoreAfterJson()) {
            try {
                snapshot.setContextJsonAfter(
                    serializationUtils.serializeToBase64Json(contextAfter, config.isRedactSensitiveFields())
                );
                snapshot.setContextHashAfter(
                    serializationUtils.computeContextHash(contextAfter, config.isRedactSensitiveFields())
                );
            } catch (Exception e) {
                System.err.println("Failed to serialize context after: " + e.getMessage());
            }
        }
        
        // For now, just log the snapshot creation (in real implementation, this would be persisted)
        System.out.println("📸 Recorded snapshot: " + machineId + " v" + version + " " + 
                           stateBefore + " → " + stateAfter + " (" + transitionDurationMillis + "ms)");
        
        // Add to history viewer for HTML generation
        historyViewer.addSnapshot(
            machineId, machineType, version, stateBefore, stateAfter,
            event != null ? event.getEventType() : "N/A",
            snapshot.getContextJsonBefore(), snapshot.getContextJsonAfter(),
            snapshot.getContextHashBefore(), snapshot.getContextHashAfter(),
            transitionDurationMillis, snapshot.getCreatedAt(),
            runId, correlationId,
            snapshot.getEventPayloadJson(), snapshot.getEventParametersJson(),
            machineOnlineStatus, stateOfflineStatus, registryStatus
        );
        
        // In a real implementation, you would persist the snapshot to database here
        // persistSnapshot(snapshot);
    }
    
    @Override
    public Long getNextVersion(String machineId) {
        return versionCounters.computeIfAbsent(machineId, k -> new AtomicLong(0))
                             .incrementAndGet();
    }
    
    @Override
    public SnapshotConfig getConfig() {
        return config;
    }
    
    @Override
    public void updateConfig(SnapshotConfig config) {
        this.config = config;
    }
    
    @Override
    public void shutdown() {
        if (asyncExecutor != null) {
            asyncExecutor.shutdown();
        }
    }
    
    /**
     * Generate HTML viewer for a specific machine's history
     */
    public void generateHtmlViewer(String machineId, String outputPath) {
        try {
            historyViewer.generateHtmlViewer(machineId, outputPath);
        } catch (IOException e) {
            System.err.println("Failed to generate HTML viewer for machine " + machineId + ": " + e.getMessage());
        }
    }
    
    /**
     * Generate HTML viewer for all machines
     */
    public void generateCombinedHtmlViewer(String outputPath) {
        try {
            historyViewer.generateCombinedHtmlViewer(outputPath);
        } catch (IOException e) {
            System.err.println("Failed to generate combined HTML viewer: " + e.getMessage());
        }
    }
    
    /**
     * Get the history viewer for direct access
     */
    public SnapshotHistoryViewer getHistoryViewer() {
        return historyViewer;
    }
    
    /**
     * Default implementation of MachineSnapshot for testing/development
     */
    private static class DefaultMachineSnapshot extends AbstractMachineSnapshot {
        
        @Override
        public Object getShardingKey() {
            return getMachineId();
        }
        
        @Override
        public String toString() {
            return String.format("DefaultMachineSnapshot{id=%s, machine=%s, %s->%s, v%d}", 
                                getSnapshotId(), getMachineId(), getStateBefore(), getStateAfter(), getVersion());
        }
    }
}