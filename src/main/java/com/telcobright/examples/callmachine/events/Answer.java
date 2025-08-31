package com.telcobright.examples.callmachine.events;

import com.telcobright.statemachine.events.StateMachineEvent;

/**
 * Answer Event - Call has been answered
 */
public class Answer implements StateMachineEvent {
    
    private final long timestamp;
    
    public Answer() {
        this.timestamp = System.currentTimeMillis();
    }
    
    @Override
    public String getEventType() {
        return "ANSWER";
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String getDescription() {
        return "Call answered";
    }
    
    @Override
    public Object getPayload() {
        return null;
    }
    
    @Override
    public String toString() {
        return "Answer{}";
    }
}