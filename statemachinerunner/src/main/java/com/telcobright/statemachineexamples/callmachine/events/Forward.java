package com.telcobright.statemachineexamples.callmachine.events;

import com.telcobright.statemachine.events.StateMachineEvent;

/**
 * Event fired when a call is forwarded to another number
 */
public class Forward implements StateMachineEvent {
    private String forwardToNumber;
    private long timestamp;
    
    public Forward(String forwardToNumber) {
        this.forwardToNumber = forwardToNumber;
        this.timestamp = System.currentTimeMillis();
    }
    
    @Override
    public String getEventType() {
        return "Forward";
    }
    
    @Override
    public String getDescription() {
        return "Call forwarded to: " + forwardToNumber;
    }
    
    @Override
    public Object getPayload() {
        return forwardToNumber;
    }
    
    @Override
    public long getTimestamp() {
        return timestamp;
    }
    
    public String getForwardToNumber() {
        return forwardToNumber;
    }
}