package com.telcobright.statemachineexamples.callmachine;

import java.time.LocalDateTime;

/**
 * Simple call context with basic parameters
 */
public class CallContext {
    private String callId;
    private String fromNumber;
    private String toNumber;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    
    public CallContext() {}
    
    public CallContext(String callId, String fromNumber, String toNumber) {
        this.callId = callId;
        this.fromNumber = fromNumber;
        this.toNumber = toNumber;
        this.startTime = LocalDateTime.now();
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
    }
}