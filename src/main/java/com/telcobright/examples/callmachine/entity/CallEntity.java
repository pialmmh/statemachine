package com.telcobright.examples.callmachine.entity;

import com.telcobright.statemachine.StateMachineContextEntity;
import java.time.LocalDateTime;

/**
 * Call Entity - Persistent data for call state machine
 * 
 * This entity is persisted to database and represents the durable state
 * of a call that needs to survive system restarts
 */
public class CallEntity implements StateMachineContextEntity<String> {
    
    // Core state machine fields
    private String callId;
    private String currentState;
    private LocalDateTime lastStateChange;
    private boolean isComplete = false;
    
    // Call information
    private String fromNumber;
    private String toNumber;
    private String callType;  // INBOUND, OUTBOUND, INTERNAL
    
    // Call timing
    private LocalDateTime createdAt;
    private LocalDateTime connectedAt;
    private LocalDateTime endedAt;
    
    // Call metrics
    private long durationSeconds = 0;
    private int ringCount = 0;
    
    // Call status
    private String callStatus;  // INITIALIZING, RINGING, CONNECTED, ENDED
    private String disconnectReason;
    private boolean recordingEnabled = false;
    
    /**
     * Default constructor
     */
    public CallEntity() {
        this.createdAt = LocalDateTime.now();
        this.lastStateChange = LocalDateTime.now();
    }
    
    /**
     * Constructor with required fields
     */
    public CallEntity(String callId, String currentState, String fromNumber, String toNumber) {
        this();
        this.callId = callId;
        this.currentState = currentState;
        this.fromNumber = fromNumber;
        this.toNumber = toNumber;
        this.callStatus = "INITIALIZING";
        this.callType = "INBOUND";  // Default
    }
    
    // StateMachineContextEntity implementation
    
    @Override
    public String getCurrentState() {
        return currentState;
    }
    
    @Override
    public void setCurrentState(String currentState) {
        this.currentState = currentState;
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
    
    @Override
    public boolean isComplete() {
        return isComplete;
    }
    
    @Override
    public void setComplete(boolean complete) {
        this.isComplete = complete;
        if (complete && endedAt == null) {
            this.endedAt = LocalDateTime.now();
        }
    }
    
    // Business methods
    
    /**
     * Mark call as connected
     */
    public void markConnected() {
        this.connectedAt = LocalDateTime.now();
        this.callStatus = "CONNECTED";
    }
    
    /**
     * Mark call as ended
     */
    public void markEnded(String reason) {
        this.endedAt = LocalDateTime.now();
        this.callStatus = "ENDED";
        this.disconnectReason = reason;
        this.isComplete = true;
        
        // Calculate duration if connected
        if (connectedAt != null && endedAt != null) {
            this.durationSeconds = java.time.Duration.between(connectedAt, endedAt).getSeconds();
        }
    }
    
    /**
     * Increment ring count
     */
    public void incrementRingCount() {
        this.ringCount++;
    }
    
    // Getters and Setters
    
    public String getCallId() {
        return callId;
    }
    
    public void setCallId(String callId) {
        this.callId = callId;
    }
    
    public String getFromNumber() {
        return fromNumber;
    }
    
    public void setFromNumber(String fromNumber) {
        this.fromNumber = fromNumber;
    }
    
    public String getToNumber() {
        return toNumber;
    }
    
    public void setToNumber(String toNumber) {
        this.toNumber = toNumber;
    }
    
    public String getCallType() {
        return callType;
    }
    
    public void setCallType(String callType) {
        this.callType = callType;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getConnectedAt() {
        return connectedAt;
    }
    
    public void setConnectedAt(LocalDateTime connectedAt) {
        this.connectedAt = connectedAt;
    }
    
    public LocalDateTime getEndedAt() {
        return endedAt;
    }
    
    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }
    
    public long getDurationSeconds() {
        return durationSeconds;
    }
    
    public void setDurationSeconds(long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }
    
    public int getRingCount() {
        return ringCount;
    }
    
    public void setRingCount(int ringCount) {
        this.ringCount = ringCount;
    }
    
    public String getCallStatus() {
        return callStatus;
    }
    
    public void setCallStatus(String callStatus) {
        this.callStatus = callStatus;
    }
    
    public String getDisconnectReason() {
        return disconnectReason;
    }
    
    public void setDisconnectReason(String disconnectReason) {
        this.disconnectReason = disconnectReason;
    }
    
    public boolean isRecordingEnabled() {
        return recordingEnabled;
    }
    
    public void setRecordingEnabled(boolean recordingEnabled) {
        this.recordingEnabled = recordingEnabled;
    }
    
    @Override
    public String toString() {
        return String.format("CallEntity{id='%s', state='%s', from='%s', to='%s', status='%s', duration=%ds}",
            callId, currentState, fromNumber, toNumber, callStatus, durationSeconds);
    }
}