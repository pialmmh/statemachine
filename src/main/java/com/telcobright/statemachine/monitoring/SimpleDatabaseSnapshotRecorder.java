package com.telcobright.statemachine.monitoring;

import com.telcobright.statemachine.StateMachineContextEntity;
import com.telcobright.statemachine.events.StateMachineEvent;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple database snapshot recorder for registry debug mode
 * This is a minimal implementation that compiles and works for basic monitoring
 */
public class SimpleDatabaseSnapshotRecorder<TPersistingEntity extends StateMachineContextEntity<?>, TContext> 
        implements SnapshotRecorder<TPersistingEntity, TContext> {
    
    private final AtomicLong versionCounter = new AtomicLong(0);
    
    public SimpleDatabaseSnapshotRecorder() {
        System.out.println("📊 Simple DatabaseSnapshotRecorder initialized");
    }
    
    @Override
    public Long getNextVersion(String machineId) {
        return versionCounter.incrementAndGet();
    }
    
    @Override
    public void recordTransition(String machineId, String machineType, Long version,
                                String stateBefore, String stateAfter, StateMachineEvent event,
                                TContext contextBefore, TContext contextAfter,
                                long transitionDurationMillis, String runId, String correlationId, 
                                String debugSessionId, boolean machineOnlineStatus, 
                                boolean stateOfflineStatus, String registryStatus) {
        
        // Simple console logging for now
        System.out.println("📸 Snapshot recorded: " + machineId + 
                          " | " + stateBefore + " → " + stateAfter + 
                          " | " + event.getEventType() + 
                          " | " + transitionDurationMillis + "ms");
    }
    
    @Override
    public void updateConfig(SnapshotConfig config) {
        // Simple implementation - just acknowledge config update
        System.out.println("📋 Snapshot config updated for SimpleDatabaseSnapshotRecorder");
    }
    
    @Override
    public SnapshotConfig getConfig() {
        // Return a basic config
        return new SnapshotConfig();
    }
    
}