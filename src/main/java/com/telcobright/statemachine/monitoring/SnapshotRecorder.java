package com.telcobright.statemachine.monitoring;

import com.telcobright.statemachine.events.StateMachineEvent;

/**
 * Interface for recording state machine snapshots.
 * Provides different strategies for capturing and storing transition history.
 */
public interface SnapshotRecorder<TPersistingEntity, TContext> {
    
    /**
     * Record a state machine transition snapshot with comprehensive status tracking.
     * This method should handle all the complexity of capturing before/after state,
     * serializing context, event payloads, and persisting the snapshot.
     * 
     * @param machineId The state machine ID
     * @param machineType The machine type (class name)
     * @param version The sequential version number for this machine
     * @param stateBefore The state before the event was processed
     * @param stateAfter The state after the event was processed
     * @param event The triggering event with full payload
     * @param contextBefore The context before the event (may be null)
     * @param contextAfter The context after the event
     * @param transitionDurationMillis How long the transition took
     * @param runId The test run ID or correlation ID
     * @param correlationId Optional correlation ID
     * @param debugSessionId Optional debug session ID
     * @param machineOnlineStatus Whether the machine is online/active
     * @param stateOfflineStatus Whether the current state is configured as offline
     * @param registryStatus The registry status of this machine
     */
    void recordTransition(
            String machineId,
            String machineType,
            Long version,
            String stateBefore,
            String stateAfter,
            StateMachineEvent event,
            TContext contextBefore,
            TContext contextAfter,
            long transitionDurationMillis,
            String runId,
            String correlationId,
            String debugSessionId,
            boolean machineOnlineStatus,
            boolean stateOfflineStatus,
            String registryStatus
    );
    
    /**
     * Get the current snapshot configuration
     */
    SnapshotConfig getConfig();
    
    /**
     * Update the snapshot configuration
     */
    void updateConfig(SnapshotConfig config);
    
    /**
     * Generate HTML viewer for a specific machine (optional method)
     */
    default void generateHtmlViewer(String machineId, String outputPath) {
        System.out.println("HTML viewer generation not implemented for this recorder");
    }
    
    /**
     * Get all snapshots (optional method)
     */
    default java.util.List<MachineSnapshot> getAllSnapshots() {
        return java.util.Collections.emptyList();
    }
    
    /**
     * Check if snapshot recording is currently enabled
     */
    default boolean isEnabled() {
        return getConfig().isEnabled();
    }
    
    /**
     * Get the next version number for a given machine ID
     */
    Long getNextVersion(String machineId);
    
    /**
     * Shutdown the recorder and release any resources
     */
    default void shutdown() {
        // Default implementation does nothing
    }
}