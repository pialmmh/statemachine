package com.telcobright.statemachine;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Configuration for sample logging - logs only 1 in N events to reduce database load
 * while maintaining statistical visibility into system behavior
 */
public class SampleLoggingConfig {
    
    private final int samplingRate;           // 1 in N events will be logged
    private final AtomicLong eventCounter;    // Thread-safe counter
    private final boolean enabled;
    
    /**
     * Disabled sample logging (logs nothing)
     */
    public static final SampleLoggingConfig DISABLED = new SampleLoggingConfig(0);
    
    /**
     * Log all events (no sampling)
     */
    public static final SampleLoggingConfig ALL = new SampleLoggingConfig(1);
    
    /**
     * Create sample logging config
     * @param samplingRate 1 in N events will be logged (e.g., 100 = 1 in 100 events)
     */
    public SampleLoggingConfig(int samplingRate) {
        this.samplingRate = Math.max(1, samplingRate);
        this.eventCounter = new AtomicLong(0);
        this.enabled = samplingRate > 0;
    }
    
    /**
     * Convenience factory methods
     */
    public static SampleLoggingConfig oneInN(int n) {
        return new SampleLoggingConfig(n);
    }
    
    public static SampleLoggingConfig oneIn2() {
        return new SampleLoggingConfig(2);
    }
    
    public static SampleLoggingConfig oneIn10() {
        return new SampleLoggingConfig(10);
    }
    
    public static SampleLoggingConfig oneIn100() {
        return new SampleLoggingConfig(100);
    }
    
    public static SampleLoggingConfig oneIn1000() {
        return new SampleLoggingConfig(1000);
    }
    
    public static SampleLoggingConfig oneInMillion() {
        return new SampleLoggingConfig(1_000_000);
    }
    
    /**
     * Check if this event should be logged based on sampling rate
     * Thread-safe and distributed across all callers
     * 
     * @return true if event should be logged, false otherwise
     */
    public boolean shouldLog() {
        if (!enabled) {
            return false;
        }
        
        if (samplingRate == 1) {
            return true; // Log all events
        }
        
        // Use modulo to determine if this event should be logged
        // This ensures exactly 1 in N events are logged on average
        long currentCount = eventCounter.incrementAndGet();
        return (currentCount % samplingRate) == 0;
    }
    
    /**
     * Get current event count
     */
    public long getEventCount() {
        return eventCounter.get();
    }
    
    /**
     * Get sampling rate
     */
    public int getSamplingRate() {
        return samplingRate;
    }
    
    /**
     * Check if sampling is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Get expected log rate as percentage
     */
    public double getExpectedLogRate() {
        if (!enabled) return 0.0;
        if (samplingRate == 1) return 100.0;
        return (100.0 / samplingRate);
    }
    
    /**
     * Reset counter (useful for testing)
     */
    public void resetCounter() {
        eventCounter.set(0);
    }
    
    @Override
    public String toString() {
        if (!enabled) {
            return "SampleLogging[DISABLED]";
        }
        return String.format("SampleLogging[1-in-%d, %.1f%%, events=%d]", 
            samplingRate, getExpectedLogRate(), getEventCount());
    }
}