package com.telcobright.statemachineexamples.callmachine.events;

// TODO: Re-enable Lombok once IDE recognizes it
// import lombok.Data;
// import lombok.EqualsAndHashCode;

import com.telcobright.statemachine.events.StateMachineEvent;

/**
 * Event representing a ringing phone
 */
// @Data
// @EqualsAndHashCode(callSuper = false)
public class Ringing implements StateMachineEvent {
    private final long timestamp;
    
    public Ringing() {
        this.timestamp = System.currentTimeMillis();
    }
    
    @Override
    public String getEventType() {
        return "RINGING";
    }
    
    @Override
    public String getDescription() {
        return "Phone is ringing";
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
