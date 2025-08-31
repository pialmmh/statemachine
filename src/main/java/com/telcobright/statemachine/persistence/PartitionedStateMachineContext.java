package com.telcobright.statemachine.persistence;

import com.telcobright.statemachine.StateMachineContextEntity;
import com.telcobright.statemachine.db.entity.ShardingEntity;

import java.time.LocalDateTime;

/**
 * Example implementation of a StateMachineContextEntity that also implements ShardingEntity
 * This allows it to be used with PartitionedRepositoryPersistenceProvider
 * 
 * This class demonstrates how to combine state machine persistence with partitioned storage
 */
public class PartitionedStateMachineContext implements StateMachineContextEntity<String>, ShardingEntity<String> {
    
    // State machine fields
    private String machineId;
    private String currentState;
    private boolean complete;
    private LocalDateTime lastStateChange;
    
    // Business context fields
    private String customData;
    private String sessionId;
    private LocalDateTime createdAt;
    
    // Sharding/partitioning fields
    private LocalDateTime partitionKey; // Used for time-based partitioning
    
    /**
     * Default constructor
     */
    public PartitionedStateMachineContext() {
        this.complete = false;
        this.lastStateChange = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.partitionKey = LocalDateTime.now(); // Partition by current time
    }
    
    /**
     * Constructor with machine ID
     */
    public PartitionedStateMachineContext(String machineId) {
        this();
        this.machineId = machineId;
    }
    
    // StateMachineContextEntity implementation
    @Override
    public boolean isComplete() {
        return complete;
    }
    
    @Override
    public void setComplete(boolean complete) {
        this.complete = complete;
    }
    
    @Override
    public String getCurrentState() {
        return currentState;
    }
    
    @Override
    public void setCurrentState(String state) {
        this.currentState = state;
        this.lastStateChange = LocalDateTime.now();
    }
    
    @Override
    public LocalDateTime getLastStateChange() {
        return lastStateChange;
    }
    
    @Override
    public void setLastStateChange(LocalDateTime lastStateChange) {
        this.lastStateChange = lastStateChange;
    }
    
    // Business context getters/setters
    public String getMachineId() {
        return machineId;
    }
    
    public void setMachineId(String machineId) {
        this.machineId = machineId;
    }
    
    public String getCustomData() {
        return customData;
    }
    
    public void setCustomData(String customData) {
        this.customData = customData;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    // Partitioning support
    public LocalDateTime getPartitionKey() {
        return partitionKey;
    }
    
    public void setPartitionKey(LocalDateTime partitionKey) {
        this.partitionKey = partitionKey;
    }
    
    @Override
    public String toString() {
        return String.format("PartitionedStateMachineContext[machineId=%s, state=%s, complete=%s, customData=%s, sessionId=%s, partitionKey=%s]",
            machineId, currentState, complete, customData, sessionId, partitionKey);
    }
    
    /**
     * Update partition key to current time (useful for moving entities to current partition)
     */
    public void updatePartitionKey() {
        this.partitionKey = LocalDateTime.now();
    }
    
    /**
     * Check if this context is within a specific date range (useful for partition queries)
     */
    public boolean isWithinDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return partitionKey != null && 
               (partitionKey.isEqual(startDate) || partitionKey.isAfter(startDate)) &&
               (partitionKey.isEqual(endDate) || partitionKey.isBefore(endDate));
    }
}