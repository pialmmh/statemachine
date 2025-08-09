package com.telcobright.statemachine.monitoring;

import com.telcobright.statemachine.StateMachineContextEntity;
import com.telcobright.db.entity.Id;
import com.telcobright.db.entity.ShardingKey;
import com.telcobright.db.entity.Column;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Abstract base class for machine snapshot entities.
 * Provides common fields and implements StateMachineContextEntity for persistence.
 * 
 * Concrete implementations should extend this class and add any domain-specific fields.
 */
public abstract class AbstractMachineSnapshot implements MachineSnapshot, StateMachineContextEntity<String> {
    
    @Id
    @Column("snapshot_id")
    protected String snapshotId;
    
    @Column("machine_id")
    protected String machineId;
    
    @Column("machine_type")
    protected String machineType;
    
    @Column("version")
    protected Long version;
    
    @Column("state_before")
    protected String stateBefore;
    
    @Column("state_after")
    protected String stateAfter;
    
    @Column("event_name")
    protected String eventName;
    
    @Column("event_type")
    protected String eventType;
    
    @Column("event_payload_json")
    protected String eventPayloadJson;
    
    @Column("event_parameters_json")
    protected String eventParametersJson;
    
    @Column("machine_online_status")
    protected Boolean machineOnlineStatus;
    
    @Column("state_offline_status")
    protected Boolean stateOfflineStatus;
    
    @Column("registry_status")
    protected String registryStatus;
    
    @Column("context_hash_before")
    protected String contextHashBefore;
    
    @Column("context_hash_after")
    protected String contextHashAfter;
    
    @Column("context_json_before")
    protected String contextJsonBefore;
    
    @Column("context_json_after")
    protected String contextJsonAfter;
    
    @Column("transition_duration_millis")
    protected Long transitionDurationMillis;
    
    @Column("run_id")
    protected String runId;
    
    @Column("correlation_id")
    protected String correlationId;
    
    @Column("debug_session_id")
    protected String debugSessionId;
    
    @ShardingKey
    @Column("created_at")
    protected LocalDateTime createdAt;
    
    @Column("is_complete")
    protected boolean isComplete = false; // Snapshots are immediately complete
    
    // Constructors
    public AbstractMachineSnapshot() {
        this.snapshotId = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.isComplete = true; // Snapshots are always complete once created
    }
    
    public AbstractMachineSnapshot(String machineId, String machineType, Long version) {
        this();
        this.machineId = machineId;
        this.machineType = machineType;
        this.version = version;
    }
    
    // MachineSnapshot implementation
    @Override
    public String getSnapshotId() { return snapshotId; }
    
    @Override
    public String getMachineId() { return machineId; }
    
    @Override
    public String getMachineType() { return machineType; }
    
    @Override
    public Long getVersion() { return version; }
    
    @Override
    public String getStateBefore() { return stateBefore; }
    
    @Override
    public String getStateAfter() { return stateAfter; }
    
    @Override
    public String getEventName() { return eventName; }
    
    @Override
    public String getEventType() { return eventType; }
    
    @Override
    public String getEventPayloadJson() { return eventPayloadJson; }
    
    @Override
    public String getEventParametersJson() { return eventParametersJson; }
    
    @Override
    public Boolean getMachineOnlineStatus() { return machineOnlineStatus; }
    
    @Override
    public Boolean getStateOfflineStatus() { return stateOfflineStatus; }
    
    @Override
    public String getRegistryStatus() { return registryStatus; }
    
    @Override
    public String getContextHashBefore() { return contextHashBefore; }
    
    @Override
    public String getContextHashAfter() { return contextHashAfter; }
    
    @Override
    public String getContextJsonBefore() { return contextJsonBefore; }
    
    @Override
    public String getContextJsonAfter() { return contextJsonAfter; }
    
    @Override
    public Long getTransitionDurationMillis() { return transitionDurationMillis; }
    
    @Override
    public String getRunId() { return runId; }
    
    @Override
    public String getCorrelationId() { return correlationId; }
    
    @Override
    public String getDebugSessionId() { return debugSessionId; }
    
    @Override
    public LocalDateTime getCreatedAt() { return createdAt; }
    
    // StateMachineContextEntity implementation
    @Override
    public boolean isComplete() { return isComplete; }
    
    @Override
    public void setComplete(boolean complete) { this.isComplete = complete; }
    
    // Setters for building snapshots
    public void setSnapshotId(String snapshotId) { this.snapshotId = snapshotId; }
    public void setMachineId(String machineId) { this.machineId = machineId; }
    public void setMachineType(String machineType) { this.machineType = machineType; }
    public void setVersion(Long version) { this.version = version; }
    public void setStateBefore(String stateBefore) { this.stateBefore = stateBefore; }
    public void setStateAfter(String stateAfter) { this.stateAfter = stateAfter; }
    public void setEventName(String eventName) { this.eventName = eventName; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public void setEventPayloadJson(String eventPayloadJson) { this.eventPayloadJson = eventPayloadJson; }
    public void setEventParametersJson(String eventParametersJson) { this.eventParametersJson = eventParametersJson; }
    public void setMachineOnlineStatus(Boolean machineOnlineStatus) { this.machineOnlineStatus = machineOnlineStatus; }
    public void setStateOfflineStatus(Boolean stateOfflineStatus) { this.stateOfflineStatus = stateOfflineStatus; }
    public void setRegistryStatus(String registryStatus) { this.registryStatus = registryStatus; }
    public void setContextHashBefore(String contextHashBefore) { this.contextHashBefore = contextHashBefore; }
    public void setContextHashAfter(String contextHashAfter) { this.contextHashAfter = contextHashAfter; }
    public void setContextJsonBefore(String contextJsonBefore) { this.contextJsonBefore = contextJsonBefore; }
    public void setContextJsonAfter(String contextJsonAfter) { this.contextJsonAfter = contextJsonAfter; }
    public void setTransitionDurationMillis(Long transitionDurationMillis) { this.transitionDurationMillis = transitionDurationMillis; }
    public void setRunId(String runId) { this.runId = runId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public void setDebugSessionId(String debugSessionId) { this.debugSessionId = debugSessionId; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    /**
     * Default implementation of getShardingKey for partitioned storage.
     * Uses machineId as the sharding key to keep all snapshots of a machine together.
     * Subclasses can override this for different sharding strategies.
     */
    public Object getShardingKey() {
        return machineId;
    }
    
    @Override
    public String toString() {
        return String.format("%s{snapshotId='%s', machineId='%s', version=%d, %s->%s, event='%s', duration=%dms}", 
                getClass().getSimpleName(), snapshotId, machineId, version, stateBefore, stateAfter, eventName, transitionDurationMillis);
    }
}