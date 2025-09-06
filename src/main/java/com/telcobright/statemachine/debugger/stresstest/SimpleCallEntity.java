package com.telcobright.statemachine.debugger.stresstest;

import com.telcobright.statemachine.StateMachineContextEntity;
import com.telcobright.statemachine.persistence.BaseStateMachineEntity;
import com.telcobright.statemachine.db.entity.ShardingEntity;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simplified call entity for testing partitioned persistence
 * Extends BaseStateMachineEntity which implements StateMachineContextEntity
 * Also implements ShardingEntity for partitioning support
 */
public class SimpleCallEntity extends BaseStateMachineEntity<String> implements ShardingEntity<String> {
    
    private String machineId;
    private String callerId;
    private String calleeId;
    private LocalDateTime startTime;
    private int partitionId; // For hash-based partitioning
    
    public SimpleCallEntity() {
        super();
        this.startTime = LocalDateTime.now();
        // Randomly assign to one of 10 partitions for even distribution
        this.partitionId = ThreadLocalRandom.current().nextInt(10);
    }
    
    public SimpleCallEntity(String id) {
        this();
        this.machineId = id;
    }
    
    // Method for sharding key (not from interface, but used via reflection)
    public String getShardingKey() {
        // Use hash-based partitioning for even distribution
        return "p" + partitionId;
    }
    
    @Override
    public String getMachineId() {
        return machineId;
    }
    
    @Override
    public void setMachineId(String machineId) {
        this.machineId = machineId;
    }
    
    // Method to get ID (for repository compatibility)
    public String getId() {
        return machineId;
    }
    
    @Override
    public StateMachineContextEntity<String> deepCopy() {
        SimpleCallEntity copy = new SimpleCallEntity();
        copy.machineId = this.machineId;
        copy.setCurrentState(this.getCurrentState());
        copy.setLastStateChange(this.getLastStateChange());
        copy.setComplete(this.isComplete());
        copy.callerId = this.callerId;
        copy.calleeId = this.calleeId;
        copy.startTime = this.startTime;
        copy.partitionId = this.partitionId;
        return copy;
    }
    
    // Getters and setters
    public String getCallerId() {
        return callerId;
    }
    
    public void setCallerId(String callerId) {
        this.callerId = callerId;
    }
    
    public String getCalleeId() {
        return calleeId;
    }
    
    public void setCalleeId(String calleeId) {
        this.calleeId = calleeId;
    }
    
    public LocalDateTime getStartTime() {
        return startTime;
    }
    
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }
    
    public int getPartitionId() {
        return partitionId;
    }
    
    public void setPartitionId(int partitionId) {
        this.partitionId = partitionId;
    }
}