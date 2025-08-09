package com.telcobright.statemachine.monitoring;

import java.time.LocalDateTime;

/**
 * Common interface for all machine snapshot entities.
 * Captures runtime history of state machine transitions including before/after states,
 * triggering events, and context changes.
 */
public interface MachineSnapshot {
    
    /**
     * Get the unique snapshot ID
     */
    String getSnapshotId();
    
    /**
     * Get the machine ID this snapshot belongs to
     */
    String getMachineId();
    
    /**
     * Get the machine type (class name)
     */
    String getMachineType();
    
    /**
     * Get the sequential version number for this machine (1, 2, 3, ...)
     */
    Long getVersion();
    
    /**
     * Get the state before the event was processed
     */
    String getStateBefore();
    
    /**
     * Get the state after the event was processed
     */
    String getStateAfter();
    
    /**
     * Get the name of the triggering event
     */
    String getEventName();
    
    /**
     * Get the type of the triggering event
     */
    String getEventType();
    
    /**
     * Get the event payload as JSON (if present)
     */
    String getEventPayloadJson();
    
    /**
     * Get the context hash before transition (SHA-256)
     */
    String getContextHashBefore();
    
    /**
     * Get the context hash after transition (SHA-256)
     */
    String getContextHashAfter();
    
    /**
     * Get the full context JSON before transition (Base64 encoded)
     * May be null if not configured to store before context
     */
    String getContextJsonBefore();
    
    /**
     * Get the full context JSON after transition (Base64 encoded)
     */
    String getContextJsonAfter();
    
    /**
     * Get the transition duration in milliseconds
     */
    Long getTransitionDurationMillis();
    
    /**
     * Get the test run ID or correlation ID
     */
    String getRunId();
    
    /**
     * Get the correlation ID (optional)
     */
    String getCorrelationId();
    
    /**
     * Get the debug session ID (optional)
     */
    String getDebugSessionId();
    
    /**
     * Get the snapshot creation timestamp (UTC)
     */
    LocalDateTime getCreatedAt();
    
    /**
     * Get the event parameters as JSON (separate from payload)
     */
    String getEventParametersJson();
    
    /**
     * Get the machine's online status at time of snapshot
     */
    Boolean getMachineOnlineStatus();
    
    /**
     * Get the state's offline configuration at time of snapshot
     */
    Boolean getStateOfflineStatus();
    
    /**
     * Get the registry status of the machine at time of snapshot
     */
    String getRegistryStatus();
    
    /**
     * Check if this snapshot represents a successful transition
     */
    default boolean isSuccessfulTransition() {
        return getStateBefore() != null && getStateAfter() != null 
            && !getStateBefore().equals(getStateAfter());
    }
    
    /**
     * Check if this snapshot has context changes
     */
    default boolean hasContextChanges() {
        String hashBefore = getContextHashBefore();
        String hashAfter = getContextHashAfter();
        return hashBefore != null && hashAfter != null && !hashBefore.equals(hashAfter);
    }
    
    // Deprecated or alternative method names for compatibility
    
    /**
     * @deprecated Use getTransitionDurationMillis() instead
     */
    @Deprecated
    default Long getTransitionDuration() {
        return getTransitionDurationMillis();
    }
    
    /**
     * @deprecated Use getCreatedAt() instead
     */
    @Deprecated
    default java.time.LocalDateTime getTimestamp() {
        return getCreatedAt();
    }
    
    /**
     * @deprecated Use getContextJsonBefore() instead
     */
    @Deprecated
    default String getContextBeforeJson() {
        return getContextJsonBefore();
    }
    
    /**
     * @deprecated Use getContextJsonAfter() instead
     */
    @Deprecated
    default String getContextAfterJson() {
        return getContextJsonAfter();
    }
    
    /**
     * @deprecated Use getContextHashBefore() instead
     */
    @Deprecated
    default String getContextBeforeHash() {
        return getContextHashBefore();
    }
    
    /**
     * @deprecated Use getContextHashAfter() instead
     */
    @Deprecated
    default String getContextAfterHash() {
        return getContextHashAfter();
    }
}