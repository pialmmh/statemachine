package com.telcobright.statemachineexamples.callmachine.events;

import com.telcobright.statemachine.events.StateMachineEvent;

// TODO: Re-enable Lombok once IDE recognizes it
// import lombok.Data;
// import lombok.EqualsAndHashCode;

/**
 * Event representing a timeout
 */
// @Data
// @EqualsAndHashCode(callSuper = false)
public class Timeout implements StateMachineEvent {
    public static final String EVENT_TYPE = "TIMEOUT";
    
    private final long timestamp;
    private final String timeoutType;
    
    public Timeout() {
        this("GENERAL_TIMEOUT");
    }
    
    public Timeout(String timeoutType) {
        this.timestamp = System.currentTimeMillis();
        this.timeoutType = timeoutType;
    }
    
    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }
    
    @Override
    public String getDescription() {
        return "Timeout occurred: " + timeoutType;
    }
    
    @Override
    public Object getPayload() {
        return timeoutType;
    }
    
    // Explicitly provide getTimestamp and getTimeoutType since Lombok might not be recognized yet
    @Override
    public long getTimestamp() {
        return timestamp;
    }
    
    public String getTimeoutType() {
        return timeoutType;
    }
}
