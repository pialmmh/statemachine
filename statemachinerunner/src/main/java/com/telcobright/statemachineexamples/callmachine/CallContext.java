package com.telcobright.statemachineexamples.callmachine;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Rich call context with comprehensive call data and business logic
 */
public class CallContext {
    private String callId;
    private String fromNumber;
    private String toNumber;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime connectTime;
    private String callDirection; // INBOUND, OUTBOUND
    private String callStatus; // RINGING, CONNECTED, ENDED
    private List<String> sessionEvents;
    private int ringCount;
    private String disconnectReason;
    private boolean recordingEnabled;
    
    public CallContext() {}
    
    public CallContext(String callId, String fromNumber, String toNumber) {
        this.callId = callId;
        this.fromNumber = fromNumber;
        this.toNumber = toNumber;
        this.startTime = LocalDateTime.now();
        this.callDirection = "INBOUND"; // Default
        this.callStatus = "INITIALIZING";
        this.sessionEvents = new ArrayList<>();
        this.ringCount = 0;
        this.recordingEnabled = false;
        
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
    
    public String toString() {
        return String.format("CallContext{callId='%s', from='%s', to='%s', status='%s', duration=%s}",
                callId, fromNumber, toNumber, callStatus, 
                getCallDuration().toSeconds() + "s");
    }
}