package com.telcobright.statemachineexamples.callmachine;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.telcobright.statemachine.StateMachineContextEntity;
import com.telcobright.db.entity.Id;
import com.telcobright.db.entity.ShardingKey;
import com.telcobright.db.entity.Column;

/**
 * Rich call context with comprehensive call data and business logic
 * Now implements StateMachineContextEntity for persistence support
 */
public class CallContext implements StateMachineContextEntity<String> {
    
    @Id
    @Column("call_id")
    private String callId;
    
    @Column("current_state")
    private String currentState;
    
    @Column("last_state_change")
    private LocalDateTime lastStateChange;
    
    @Column("from_number")
    private String fromNumber;
    
    @Column("to_number")
    private String toNumber;
    
    @Column("start_time")
    private LocalDateTime startTime;
    
    @Column("end_time")
    private LocalDateTime endTime;
    
    @Column("connect_time")
    private LocalDateTime connectTime;
    
    @Column("call_direction")
    private String callDirection; // INBOUND, OUTBOUND
    
    @Column("call_status")
    private String callStatus; // RINGING, CONNECTED, ENDED
    
    // Note: sessionEvents will be stored separately or as JSON
    private List<String> sessionEvents;
    
    @Column("ring_count")
    private int ringCount;
    
    @Column("disconnect_reason")
    private String disconnectReason;
    
    @Column("recording_enabled")
    private boolean recordingEnabled;
    
    @Column("is_complete")
    private boolean isComplete = false;
    
    @ShardingKey
    @Column("partition_key")
    private String partitionKey; // For sharding - based on callId
    
    public CallContext() {
        this.sessionEvents = new ArrayList<>();
    }
    
    public CallContext(String callId, String fromNumber, String toNumber) {
        this();
        this.callId = callId;
        this.fromNumber = fromNumber;
        this.toNumber = toNumber;
        this.startTime = LocalDateTime.now();
        this.currentState = "IDLE"; // Default initial state
        this.lastStateChange = LocalDateTime.now();
        this.callDirection = "INBOUND"; // Default
        this.callStatus = "INITIALIZING";
        this.ringCount = 0;
        this.recordingEnabled = false;
        this.isComplete = false;
        this.partitionKey = callId; // Use callId for sharding
        
        addSessionEvent("Call initialized from " + fromNumber + " to " + toNumber);
    }
    
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
    
    public LocalDateTime getStartTime() {
        return startTime;
    }
    
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }
    
    public LocalDateTime getEndTime() {
        return endTime;
    }
    
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
    
    public void endCall() {
        this.endTime = LocalDateTime.now();
        this.callStatus = "ENDED";
        addSessionEvent("Call ended");
    }
    
    public void startRinging() {
        this.callStatus = "RINGING";
        this.ringCount++;
        addSessionEvent("Phone started ringing (ring #" + ringCount + ")");
    }
    
    public void answerCall() {
        this.connectTime = LocalDateTime.now();
        this.callStatus = "CONNECTED";
        addSessionEvent("Call answered and connected");
    }
    
    public void addSessionEvent(String event) {
        String timestamp = LocalDateTime.now().toString();
        sessionEvents.add("[" + timestamp + "] " + event);
    }
    
    public Duration getCallDuration() {
        if (connectTime != null && endTime != null) {
            return Duration.between(connectTime, endTime);
        }
        return Duration.ZERO;
    }
    
    public Duration getRingDuration() {
        if (startTime != null && connectTime != null) {
            return Duration.between(startTime, connectTime);
        }
        return Duration.ZERO;
    }
    
    public boolean isLongCall() {
        return getCallDuration().toMinutes() > 5;
    }
    
    public void setDisconnectReason(String reason) {
        this.disconnectReason = reason;
        addSessionEvent("Disconnect reason: " + reason);
    }
    
    // Getters and setters for new fields
    public String getCallDirection() { return callDirection; }
    public void setCallDirection(String callDirection) { this.callDirection = callDirection; }
    
    public String getCallStatus() { return callStatus; }
    public void setCallStatus(String callStatus) { this.callStatus = callStatus; }
    
    public List<String> getSessionEvents() { return sessionEvents; }
    
    public int getRingCount() { return ringCount; }
    public void setRingCount(int ringCount) { this.ringCount = ringCount; }
    
    public String getDisconnectReason() { return disconnectReason; }
    
    public boolean isRecordingEnabled() { return recordingEnabled; }
    public void setRecordingEnabled(boolean recordingEnabled) { 
        this.recordingEnabled = recordingEnabled;
        addSessionEvent("Recording " + (recordingEnabled ? "enabled" : "disabled"));
    }
    
    public LocalDateTime getConnectTime() { return connectTime; }
    public void setConnectTime(LocalDateTime connectTime) { this.connectTime = connectTime; }
    
    public boolean isCallAnswered() {
        return connectTime != null;
    }
    
    public boolean isCallEnded() {
        return endTime != null;
    }
    
    public boolean isTollFreeCall() {
        return fromNumber != null && fromNumber.startsWith("+1800");
    }
    
    public String getCallerNumber() {
        return fromNumber;
    }
    
    // StateMachineContextEntity interface implementation
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
    }
    
    public Object getShardingKey() { 
        return partitionKey; 
    }
    
    // Additional getters/setters for new fields
    public String getPartitionKey() { 
        return partitionKey; 
    }
    
    public void setPartitionKey(String partitionKey) { 
        this.partitionKey = partitionKey; 
    }
    
    @Override
    public String toString() {
        return String.format("CallContext{callId='%s', state='%s', from='%s', to='%s', status='%s', duration=%s, complete=%s}",
                callId, currentState, fromNumber, toNumber, callStatus, 
                getCallDuration().toSeconds() + "s", isComplete);
    }
}