package com.telcobright.statemachine.extendedtest;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for concurrent machine testing
 */
public class ConcurrentTestConfig {
    
    // Test scale configuration
    public static final int DEFAULT_MACHINE_COUNT = 1000;
    public static final int DEFAULT_THREAD_POOL_SIZE = 50;
    public static final int DEFAULT_EVENT_SCHEDULER_THREADS = 20;
    
    // Event generation configuration
    public static final int MIN_EVENTS_PER_MACHINE = 10;
    public static final int MAX_EVENTS_PER_MACHINE = 100;
    public static final long MIN_EVENT_DELAY_MS = 100;
    public static final long MAX_EVENT_DELAY_MS = 5000;
    
    // Test duration configuration
    public static final long TEST_DURATION_MINUTES = 5;
    public static final long WARMUP_DURATION_SECONDS = 30;
    public static final long COOLDOWN_DURATION_SECONDS = 60;
    
    // Database validation configuration
    public static final int DB_VALIDATION_TIMEOUT_SECONDS = 120;
    public static final int DB_BATCH_SIZE = 100;
    
    // Performance thresholds
    public static final long MAX_EVENT_PROCESSING_TIME_MS = 1000;
    public static final double MIN_SUCCESS_RATE = 0.95; // 95%
    public static final long MAX_MEMORY_USAGE_MB = 2048;
    
    // Test configuration
    private final int machineCount;
    private final int threadPoolSize;
    private final int eventSchedulerThreads;
    private final boolean debugMode;
    private final boolean validateDatabase;
    private final boolean enableMetrics;
    private final String registryId;
    private final int webSocketPort;
    
    public ConcurrentTestConfig(Builder builder) {
        this.machineCount = builder.machineCount;
        this.threadPoolSize = builder.threadPoolSize;
        this.eventSchedulerThreads = builder.eventSchedulerThreads;
        this.debugMode = builder.debugMode;
        this.validateDatabase = builder.validateDatabase;
        this.enableMetrics = builder.enableMetrics;
        this.registryId = builder.registryId;
        this.webSocketPort = builder.webSocketPort;
    }
    
    // Getters
    public int getMachineCount() { return machineCount; }
    public int getThreadPoolSize() { return threadPoolSize; }
    public int getEventSchedulerThreads() { return eventSchedulerThreads; }
    public boolean isDebugMode() { return debugMode; }
    public boolean isValidateDatabase() { return validateDatabase; }
    public boolean isEnableMetrics() { return enableMetrics; }
    public String getRegistryId() { return registryId; }
    public int getWebSocketPort() { return webSocketPort; }
    
    // Builder pattern
    public static class Builder {
        private int machineCount = DEFAULT_MACHINE_COUNT;
        private int threadPoolSize = DEFAULT_THREAD_POOL_SIZE;
        private int eventSchedulerThreads = DEFAULT_EVENT_SCHEDULER_THREADS;
        private boolean debugMode = true;
        private boolean validateDatabase = true;
        private boolean enableMetrics = true;
        private String registryId = "concurrent_test";
        private int webSocketPort = 19999;
        
        public Builder machineCount(int count) {
            this.machineCount = count;
            return this;
        }
        
        public Builder threadPoolSize(int size) {
            this.threadPoolSize = size;
            return this;
        }
        
        public Builder eventSchedulerThreads(int threads) {
            this.eventSchedulerThreads = threads;
            return this;
        }
        
        public Builder debugMode(boolean debug) {
            this.debugMode = debug;
            return this;
        }
        
        public Builder validateDatabase(boolean validate) {
            this.validateDatabase = validate;
            return this;
        }
        
        public Builder enableMetrics(boolean metrics) {
            this.enableMetrics = metrics;
            return this;
        }
        
        public Builder registryId(String id) {
            this.registryId = id;
            return this;
        }
        
        public Builder webSocketPort(int port) {
            this.webSocketPort = port;
            return this;
        }
        
        public ConcurrentTestConfig build() {
            return new ConcurrentTestConfig(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("ConcurrentTestConfig{machines=%d, threads=%d, debug=%s, validate=%s}", 
            machineCount, threadPoolSize, debugMode, validateDatabase);
    }
}