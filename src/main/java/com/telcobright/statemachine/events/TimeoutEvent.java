package com.telcobright.statemachine.events;

/**
 * Special event for timeout occurrences
 */
public class TimeoutEvent implements StateMachineEvent {
    
    private final String sourceState;
    private final String targetState;
    private final long timestamp;
    
    public TimeoutEvent(String sourceState, String targetState) {
        this.sourceState = sourceState;
        this.targetState = targetState;
        this.timestamp = System.currentTimeMillis();
    }
    
    @Override
    public String getEventType() {
        return "TIMEOUT";
    }
    
    @Override
    public Object getPayload() {
        return targetState;
    }
    
    @Override
    public long getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String getDescription() {
        return "Timeout transition from " + sourceState + " to " + targetState;
    }
    
    public String getSourceState() {
        return sourceState;
    }
    
    public String getTargetState() {
        return targetState;
    }
    
    @Override
    public String toString() {
        return String.format("TimeoutEvent{sourceState='%s', targetState='%s', timestamp=%d}", 
                           sourceState, targetState, timestamp);
    }
}
