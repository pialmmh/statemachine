package com.telcobright.statemachine.events;

/**
 * Generic implementation of StateMachineEvent
 */
public class GenericStateMachineEvent implements StateMachineEvent {
    
    private final String eventType;
    private final Object payload;
    private final long timestamp;
    
    public GenericStateMachineEvent(String eventType) {
        this(eventType, null);
    }
    
    public GenericStateMachineEvent(String eventType, Object payload) {
        this.eventType = eventType;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
    }
    
    @Override
    public String getEventType() {
        return eventType;
    }
    
    @Override
    public Object getPayload() {
        return payload;
    }
    
    @Override
    public long getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String getDescription() {
        return "Generic event: " + eventType + (payload != null ? " with payload: " + payload : "");
    }
    
    @Override
    public String toString() {
        return String.format("GenericStateMachineEvent{eventType='%s', payload=%s, timestamp=%d}", 
                           eventType, payload, timestamp);
    }
}
