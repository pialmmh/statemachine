package com.telcobright.statemachineexamples.callmachine.events;

import com.telcobright.statemachine.events.StateMachineEvent;

// TODO: Re-enable Lombok once IDE recognizes it
// import lombok.Data;
// import lombok.EqualsAndHashCode;

/**
 * Event representing hanging up a call
 */
// @Data
// @EqualsAndHashCode(callSuper = false)
public class Hangup implements StateMachineEvent {
    public static final String EVENT_TYPE = "HANGUP";
    
    private final long timestamp;
    
    public Hangup() {
        this.timestamp = System.currentTimeMillis();
    }
    
    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }
    
    @Override
    public String getDescription() {
        return "User hung up the call";
    }
    
    @Override
    public Object getPayload() {
        return null;
    }
    
    // Explicitly provide getTimestamp since Lombok might not be recognized yet
    @Override
    public long getTimestamp() {
        return timestamp;
    }
}
