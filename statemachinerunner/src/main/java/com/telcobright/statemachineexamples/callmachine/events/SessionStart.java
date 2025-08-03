package com.telcobright.statemachineexamples.callmachine.events;

// TODO: Re-enable Lombok once IDE recognizes it
// import lombok.Data;
// import lombok.EqualsAndHashCode;

import com.telcobright.statemachine.events.StateMachineEvent;

/**
 * Event representing a session start
 */
// @Data
// @EqualsAndHashCode(callSuper = false)
public class SessionStart implements StateMachineEvent {
    private final long timestamp;
    
    public SessionStart() {
        this.timestamp = System.currentTimeMillis();
    }
    
    @Override
    public String getEventType() {
        return "SESSION_START";
    }
    
    @Override
    public String getDescription() {
        return "Call session started";
    }
    
    @Override
    public Object getPayload() {
        return null; // No payload for this simple event
    }
    
    // Explicitly provide getTimestamp since Lombok might not be recognized yet
    @Override
    public long getTimestamp() {
        return timestamp;
    }
}
