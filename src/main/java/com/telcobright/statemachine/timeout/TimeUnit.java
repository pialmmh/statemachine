package com.telcobright.statemachine.timeout;

/**
 * Time units for timeout configuration
 */
public enum TimeUnit {
    MILLISECONDS(1L),
    SECONDS(1000L),
    MINUTES(60000L);
    
    private final long multiplier;
    
    TimeUnit(long multiplier) {
        this.multiplier = multiplier;
    }
    
    public long toMillis(long duration) {
        return duration * multiplier;
    }
    
    public long getMultiplier() {
        return multiplier;
    }
}
