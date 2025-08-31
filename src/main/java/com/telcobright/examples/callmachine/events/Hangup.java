package com.telcobright.examples.callmachine.events;

import com.telcobright.statemachine.events.StateMachineEvent;

/**
 * Hangup Event - Call has been terminated
 */
public class Hangup implements StateMachineEvent {
    
    private final String reason;
    private final long timestamp;
    
    public Hangup() {
        this(null);
    }
    
    public Hangup(String reason) {
        this.reason = reason;
        this.timestamp = System.currentTimeMillis();
    }
    
    @Override
    public String getEventType() {
        return "HANGUP";
    }
    
    public String getReason() {
        return reason;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String getDescription() {
        return reason != null ? "Call terminated: " + reason : "Call terminated";
    }
    
    @Override
    public Object getPayload() {
        return reason;
    }
    
    @Override
    public String toString() {
        return String.format("Hangup{reason='%s'}", reason);
    }
}