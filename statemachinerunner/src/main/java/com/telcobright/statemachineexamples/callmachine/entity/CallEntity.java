package com.telcobright.statemachineexamples.callmachine.entity;

import com.telcobright.db.entity.Id;
import com.telcobright.db.entity.ShardingKey;
import com.telcobright.db.entity.Column;
import com.telcobright.statemachine.StateMachineContextEntity;
import java.time.LocalDateTime;

/**
 * Call Entity for persistence - the ID is the state machine ID
 * Uses ById lookup mode for simple call processing
 */
public class CallEntity implements StateMachineContextEntity<String> {
    
    @Id
    @Column("call_id")
    private String callId;  // This is the state machine ID
    
    @Column("current_state")
    private String currentState;
    
    @Column("from_number")
    private String fromNumber;
    
    @Column("to_number")
    private String toNumber;
    
    @Column("call_status")
    private String callStatus;
    
    @Column("duration_seconds")
    private long durationSeconds;
    
    @Column("recording_enabled")
    private boolean recordingEnabled;
    
    @Column("ring_count")
    private int ringCount;
    
    @ShardingKey
    @Column("created_at")
    private LocalDateTime createdAt;
    
    @Column("updated_at")
    private LocalDateTime updatedAt;
    
    @Column("ended_at")
    private LocalDateTime endedAt;
    
    @Column("is_complete")
    private boolean isComplete = false;
    
    @Column("last_state_change")
    private LocalDateTime lastStateChange;
    
    // Default constructor
    public CallEntity() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    // Constructor with required fields
    public CallEntity(String callId, String currentState, String fromNumber, String toNumber) {
        this();
        this.callId = callId;
        this.currentState = currentState;
        this.lastStateChange = LocalDateTime.now();
        this.fromNumber = fromNumber;
        this.toNumber = toNumber;
        this.callStatus = "INITIALIZING";
        this.durationSeconds = 0;
        this.recordingEnabled = false;
        this.ringCount = 0;
    }
    
    // Getters and setters
    public String getCallId() { return callId; }
    public void setCallId(String callId) { this.callId = callId; }
    
    public String getCurrentState() { return currentState; }
    public void setCurrentState(String currentState) { 
        this.currentState = currentState;
        this.updatedAt = LocalDateTime.now();
        this.lastStateChange = LocalDateTime.now();
    }
    
    public LocalDateTime getLastStateChange() { return lastStateChange; }
    public void setLastStateChange(LocalDateTime lastStateChange) { 
        this.lastStateChange = lastStateChange;
        this.updatedAt = LocalDateTime.now();
    }
    
    public String getFromNumber() { return fromNumber; }
    public void setFromNumber(String fromNumber) { this.fromNumber = fromNumber; }
    
    public String getToNumber() { return toNumber; }
    public void setToNumber(String toNumber) { this.toNumber = toNumber; }
    
    // Convenience methods for test compatibility
    public String getCallerNumber() { return fromNumber; }
    public void setCallerNumber(String callerNumber) { this.fromNumber = callerNumber; }
    
    public String getCalleeNumber() { return toNumber; }
    public void setCalleeNumber(String calleeNumber) { this.toNumber = calleeNumber; }
    
    public String getCallStatus() { return callStatus; }
    public void setCallStatus(String callStatus) { this.callStatus = callStatus; }
    
    public long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(long durationSeconds) { this.durationSeconds = durationSeconds; }
    
    public boolean isRecordingEnabled() { return recordingEnabled; }
    public void setRecordingEnabled(boolean recordingEnabled) { this.recordingEnabled = recordingEnabled; }
    
    public int getRingCount() { return ringCount; }
    public void setRingCount(int ringCount) { this.ringCount = ringCount; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
    
    // StateMachineContextEntity interface implementation
    @Override
    public boolean isComplete() { return isComplete; }
    
    @Override
    public void setComplete(boolean complete) { 
        this.isComplete = complete; 
        this.updatedAt = LocalDateTime.now();
    }
    
    @Override
    public String toString() {
        return String.format("CallEntity{callId='%s', state='%s', from='%s', to='%s', status='%s', duration=%ds, recording=%s}", 
                callId, currentState, fromNumber, toNumber, callStatus, durationSeconds, recordingEnabled);
    }
}