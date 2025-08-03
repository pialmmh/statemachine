package com.telcobright.statemachineexamples.callmachine.events;

import com.telcobright.statemachine.events.StateMachineEvent;

// TODO: Re-enable Lombok once IDE recognizes it
// import lombok.Data;
// import lombok.EqualsAndHashCode;

/**
 * Event representing answering a call
 */
// @Data
// @EqualsAndHashCode(callSuper = false)
public class Answer implements StateMachineEvent {
    public static final String EVENT_TYPE = "ANSWER";
    
    private final long timestamp;
    
    public Answer() {
        this.timestamp = System.currentTimeMillis();
    }
    
    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }
    
    @Override
    public String getDescription() {
        return "User answered the call";
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
