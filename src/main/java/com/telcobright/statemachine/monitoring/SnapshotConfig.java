package com.telcobright.statemachine.monitoring;

/**
 * Configuration for snapshot recording behavior.
 * Controls what data is captured and how it's stored.
 */
public class SnapshotConfig {
    
    private boolean enabled = false;
    private boolean storeBeforeJson = false;
    private boolean storeAfterJson = true;
    private boolean storeJsonDiff = false;
    private boolean async = false;
    private int asyncQueueSize = 1000;
    private boolean redactSensitiveFields = true;
    
    public SnapshotConfig() {}
    
    /**
     * Create a default configuration suitable for development/testing
     */
    public static SnapshotConfig defaultConfig() {
        return new SnapshotConfig()
                .enabled(true)
                .storeAfterJson(true)
                .redactSensitiveFields(true);
    }
    
    /**
     * Create a comprehensive configuration that stores everything
     */
    public static SnapshotConfig comprehensiveConfig() {
        return new SnapshotConfig()
                .enabled(true)
                .storeBeforeJson(true)
                .storeAfterJson(true)
                .redactSensitiveFields(true);
    }
    
    /**
     * Create a high-performance configuration for production
     */
    public static SnapshotConfig productionConfig() {
        return new SnapshotConfig()
                .enabled(true)
                .storeAfterJson(true)
                .async(true)
                .asyncQueueSize(5000)
                .redactSensitiveFields(true);
    }
    
    // Fluent setters
    public SnapshotConfig enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }
    
    public SnapshotConfig storeBeforeJson(boolean storeBeforeJson) {
        this.storeBeforeJson = storeBeforeJson;
        return this;
    }
    
    public SnapshotConfig storeAfterJson(boolean storeAfterJson) {
        this.storeAfterJson = storeAfterJson;
        return this;
    }
    
    public SnapshotConfig storeJsonDiff(boolean storeJsonDiff) {
        this.storeJsonDiff = storeJsonDiff;
        return this;
    }
    
    public SnapshotConfig async(boolean async) {
        this.async = async;
        return this;
    }
    
    public SnapshotConfig asyncQueueSize(int asyncQueueSize) {
        this.asyncQueueSize = asyncQueueSize;
        return this;
    }
    
    public SnapshotConfig redactSensitiveFields(boolean redactSensitiveFields) {
        this.redactSensitiveFields = redactSensitiveFields;
        return this;
    }
    
    // Getters
    public boolean isEnabled() { return enabled; }
    public boolean isStoreBeforeJson() { return storeBeforeJson; }
    public boolean isStoreAfterJson() { return storeAfterJson; }
    public boolean isStoreJsonDiff() { return storeJsonDiff; }
    public boolean isAsync() { return async; }
    public int getAsyncQueueSize() { return asyncQueueSize; }
    public boolean isRedactSensitiveFields() { return redactSensitiveFields; }
    
    // Additional methods for compatibility
    public java.util.Set<String> getRedactionFields() {
        return new java.util.HashSet<>();
    }
    
    public boolean isCaptureContextBefore() { return storeBeforeJson; }
    public boolean isSerializeContext() { return storeAfterJson; }
    public boolean isHashContext() { return true; }
    public boolean isAsyncRecording() { return async; }
    public boolean isVerboseLogging() { return false; }
    public boolean isFailOnRecordingError() { return false; }
    
    @Override
    public String toString() {
        return String.format("SnapshotConfig{enabled=%s, beforeJson=%s, afterJson=%s, diff=%s, async=%s, queueSize=%d, redact=%s}",
                enabled, storeBeforeJson, storeAfterJson, storeJsonDiff, async, asyncQueueSize, redactSensitiveFields);
    }
}