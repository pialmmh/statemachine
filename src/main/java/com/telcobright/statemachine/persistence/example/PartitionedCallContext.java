package com.telcobright.statemachine.persistence.example;

import com.telcobright.statemachine.StateMachineContextEntity;
import com.telcobright.statemachine.persistence.BaseStateMachineEntity;
import com.telcobright.statemachine.db.entity.ShardingEntity;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Example entity class that can be used with PartitionedRepository
 * Implements both StateMachineContextEntity and ShardingEntity interfaces
 * 
 * This allows the entity to be:
 * 1. Used as state machine context (StateMachineContextEntity)
 * 2. Stored in partitioned tables (ShardingEntity)
 * 
 * Example usage:
 * - Partition by date for time-based partitioning (daily/monthly)
 * - Partition by customer ID for customer-based sharding
 * - Partition by hash of machine ID for even distribution
 */
public class PartitionedCallContext extends BaseStateMachineEntity implements ShardingEntity<String> {
    
    // Call-specific fields
    private String callerId;
    private String calleeId;
    private LocalDateTime callStartTime;
    private LocalDateTime callEndTime;
    private String callType; // INBOUND, OUTBOUND, INTERNAL
    private Integer duration; // in seconds
    private String customerId; // for customer-based partitioning
    
    // Constructor
    public PartitionedCallContext() {
        super();
        this.callStartTime = LocalDateTime.now();
    }
    
    public PartitionedCallContext(String id) {
        super();
        setId(id);
        this.callStartTime = LocalDateTime.now();
    }
    
    /**
     * Implementation of ShardingEntity interface
     * Returns the key used for partitioning
     * 
     * Different strategies can be used:
     * 1. Date-based: return formatted date string
     * 2. Customer-based: return customerId
     * 3. Hash-based: return hash of ID
     * 4. Range-based: return range identifier
     */
    @Override
    public String getShardingKey() {
        // Example 1: Date-based partitioning (monthly)
        if (callStartTime != null) {
            return callStartTime.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }
        
        // Example 2: Customer-based partitioning
        // if (customerId != null) {
        //     return customerId;
        // }
        
        // Example 3: Hash-based partitioning (4 partitions)
        // if (getId() != null) {
        //     int hash = Math.abs(getId().hashCode());
        //     return "p" + (hash % 4);
        // }
        
        // Default: use current month
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }
    
    /**
     * Optional: Define the partition type
     * This can be used by the PartitionedRepository to determine
     * how to create and manage partitions
     */
    public PartitionType getPartitionType() {
        return PartitionType.MONTHLY;
    }
    
    /**
     * Enum for partition types
     */
    public enum PartitionType {
        DAILY,      // One partition per day
        WEEKLY,     // One partition per week
        MONTHLY,    // One partition per month
        YEARLY,     // One partition per year
        HASH,       // Hash-based partitioning
        RANGE,      // Range-based partitioning
        LIST        // List-based partitioning
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
    
    public LocalDateTime getCallStartTime() {
        return callStartTime;
    }
    
    public void setCallStartTime(LocalDateTime callStartTime) {
        this.callStartTime = callStartTime;
    }
    
    public LocalDateTime getCallEndTime() {
        return callEndTime;
    }
    
    public void setCallEndTime(LocalDateTime callEndTime) {
        this.callEndTime = callEndTime;
        if (callStartTime != null && callEndTime != null) {
            this.duration = (int) java.time.Duration.between(callStartTime, callEndTime).getSeconds();
        }
    }
    
    public String getCallType() {
        return callType;
    }
    
    public void setCallType(String callType) {
        this.callType = callType;
    }
    
    public Integer getDuration() {
        return duration;
    }
    
    public void setDuration(Integer duration) {
        this.duration = duration;
    }
    
    public String getCustomerId() {
        return customerId;
    }
    
    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }
    
    @Override
    public String toString() {
        return "PartitionedCallContext{" +
            "id='" + getId() + '\'' +
            ", state='" + getCurrentState() + '\'' +
            ", callerId='" + callerId + '\'' +
            ", calleeId='" + calleeId + '\'' +
            ", callType='" + callType + '\'' +
            ", duration=" + duration +
            ", shardingKey='" + getShardingKey() + '\'' +
            '}';
    }
}