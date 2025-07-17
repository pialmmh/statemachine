package com.telcobright.statemachineexamples.callmachine.events;

import com.telcobright.statemachine.events.StateMachineEvent;

// TODO: Re-enable Lombok once IDE recognizes it
// import lombok.Data;
// import lombok.EqualsAndHashCode;

/**
 * Event representing an incoming call
 */
// @Data
// @EqualsAndHashCode(callSuper = false)
public class IncomingCall implements StateMachineEvent {
    public static final String EVENT_TYPE = "INCOMING_CALL";
    
    private final long timestamp;
    private final String callerNumber;
    
    public IncomingCall() {
        this(null);
    }
    
    public IncomingCall(String callerNumber) {
        this.timestamp = System.currentTimeMillis();
        this.callerNumber = callerNumber;
    }
    
    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }
    
    @Override
    public String getDescription() {
        return callerNumber != null ? 
            "Incoming call from " + callerNumber : 
            "Incoming call from unknown number";
    }
    
    @Override
    public Object getPayload() {
        return callerNumber;
    }
    
    // Explicitly provide getTimestamp and getCallerNumber since Lombok might not be recognized yet
    @Override
    public long getTimestamp() {
        return timestamp;
    }
    
    public String getCallerNumber() {
        return callerNumber;
    }
}
