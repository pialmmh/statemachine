package com.telcobright.examples.callmachine.events;

import com.telcobright.statemachine.events.StateMachineEvent;

/**
 * Incoming Call Event
 * 
 * This event triggers auto-creation of call machines
 */
public class IncomingCall implements StateMachineEvent {
    
    private final String fromNumber;
    private final String toNumber;
    private final long timestamp;
    
    public IncomingCall(String fromNumber, String toNumber) {
        this.fromNumber = fromNumber;
        this.toNumber = toNumber;
        this.timestamp = System.currentTimeMillis();
    }
    
    @Override
    public String getEventType() {
        return "INCOMING_CALL";
    }
    
    public String getFromNumber() {
        return fromNumber;
    }
    
    public String getToNumber() {
        return toNumber;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String getDescription() {
        return String.format("Incoming call from %s to %s", fromNumber, toNumber);
    }
    
    @Override
    public Object getPayload() {
        return new Object[]{fromNumber, toNumber};
    }
    
    @Override
    public String toString() {
        return String.format("IncomingCall{from='%s', to='%s'}", fromNumber, toNumber);
    }
}