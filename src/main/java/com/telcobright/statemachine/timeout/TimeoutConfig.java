package com.telcobright.statemachine.timeout;

/**
 * Configuration for timeout behavior
 */
public class TimeoutConfig {
    private final long duration;
    private final TimeUnit unit;
    private final String targetState;
    
    public TimeoutConfig(long duration, TimeUnit unit, String targetState) {
        this.duration = duration;
        this.unit = unit;
        this.targetState = targetState;
    }
    
    public long getDuration() {
        return duration;
    }
    
    public TimeUnit getUnit() {
        return unit;
    }
    
    public String getTargetState() {
        return targetState;
    }
    
    public long getDurationInMillis() {
        return unit.toMillis(duration);
    }
    
    @Override
    public String toString() {
        return String.format("TimeoutConfig{duration=%d, unit=%s, targetState='%s'}", 
                            duration, unit, targetState);
    }
}
