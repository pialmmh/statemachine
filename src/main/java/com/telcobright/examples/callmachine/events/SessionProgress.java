package com.telcobright.examples.callmachine.events;

import com.telcobright.statemachine.events.StateMachineEvent;

/**
 * Session Progress Event - Call is progressing
 */
public class SessionProgress implements StateMachineEvent {
    
    private final String details;
    private final long timestamp;
    
    public SessionProgress() {
        this(null);
    }
    
    public SessionProgress(String details) {
        this.details = details;
        this.timestamp = System.currentTimeMillis();
    }
    
    @Override
    public String getEventType() {
        return "SESSION_PROGRESS";
    }
    
    public String getDetails() {
        return details;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String getDescription() {
        return details != null ? "Session progress: " + details : "Session progress";
    }
    
    @Override
    public Object getPayload() {
        return details;
    }
    
    @Override
    public String toString() {
        return String.format("SessionProgress{details='%s'}", details);
    }
}