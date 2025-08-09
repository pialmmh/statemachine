package com.telcobright.statemachineexamples.callmachine.events;

import com.telcobright.statemachine.events.StateMachineEvent;

/**
 * Event fired when a call is rejected
 */
public class Reject implements StateMachineEvent {
    private String reason;
    private long timestamp;
    
    public Reject(String reason) {
        this.reason = reason;
        this.timestamp = System.currentTimeMillis();
    }
    
    @Override
    public String getEventType() {
        return "Reject";
    }
    
    @Override
    public String getDescription() {
        return "Call rejected: " + reason;
    }
    
    @Override
    public Object getPayload() {
        return reason;
    }
    
    @Override
    public long getTimestamp() {
        return timestamp;
    }
    
    public String getReason() {
        return reason;
    }
}