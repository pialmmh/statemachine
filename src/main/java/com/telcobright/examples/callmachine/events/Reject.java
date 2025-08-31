package com.telcobright.examples.callmachine.events;

import com.telcobright.statemachine.events.StateMachineEvent;

/**
 * Reject Event - Call has been rejected
 */
public class Reject implements StateMachineEvent {
    
    private final String reason;
    private final long timestamp;
    
    public Reject() {
        this(null);
    }
    
    public Reject(String reason) {
        this.reason = reason;
        this.timestamp = System.currentTimeMillis();
    }
    
    @Override
    public String getEventType() {
        return "REJECT";
    }
    
    public String getReason() {
        return reason;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String getDescription() {
        return reason != null ? "Call rejected: " + reason : "Call rejected";
    }
    
    @Override
    public Object getPayload() {
        return reason;
    }
    
    @Override
    public String toString() {
        return String.format("Reject{reason='%s'}", reason);
    }
}