package com.telcobright.statemachine;

/**
 * Performance configuration for StateMachineRegistry
 * Easy-to-use configuration class for all performance parameters
 */
public class RegistryPerformanceConfig {
    
    // TPS Configuration (Performance Capacity)
    private final int targetTps; // Target events/second capacity
    private final int maxEventsPerMachinePerSecond;
    
    // Concurrent Machine Limits
    private final int maxConcurrentMachines;
    private final int maxMachinesPerPartner;
    
    // Virtual Thread Pool Configuration
    private final int virtualThreadPoolSize;
    private final boolean useAdaptiveThreading;
    
    // Resource Management
    private final int eventQueueCapacity;
    private final int machineEvictionThreshold;
    private final long machineIdleTimeoutMs;
    
    // Performance Monitoring
    private final boolean enablePerformanceMetrics;
    private final int metricsReportingIntervalMs;
    
    private RegistryPerformanceConfig(Builder builder) {
        this.targetTps = builder.targetTps;
        this.maxEventsPerMachinePerSecond = builder.maxEventsPerMachinePerSecond;
        this.maxConcurrentMachines = builder.maxConcurrentMachines;
        this.maxMachinesPerPartner = builder.maxMachinesPerPartner;
        this.virtualThreadPoolSize = builder.virtualThreadPoolSize;
        this.useAdaptiveThreading = builder.useAdaptiveThreading;
        this.eventQueueCapacity = builder.eventQueueCapacity;
        this.machineEvictionThreshold = builder.machineEvictionThreshold;
        this.machineIdleTimeoutMs = builder.machineIdleTimeoutMs;
        this.enablePerformanceMetrics = builder.enablePerformanceMetrics;
        this.metricsReportingIntervalMs = builder.metricsReportingIntervalMs;
    }
    
    // Getters
    public int getTargetTps() { return targetTps; }
    public int getMaxEventsPerMachinePerSecond() { return maxEventsPerMachinePerSecond; }
    public int getMaxConcurrentMachines() { return maxConcurrentMachines; }
    public int getMaxMachinesPerPartner() { return maxMachinesPerPartner; }
    public int getVirtualThreadPoolSize() { return virtualThreadPoolSize; }
    public boolean isUseAdaptiveThreading() { return useAdaptiveThreading; }
    public int getEventQueueCapacity() { return eventQueueCapacity; }
    public int getMachineEvictionThreshold() { return machineEvictionThreshold; }
    public long getMachineIdleTimeoutMs() { return machineIdleTimeoutMs; }
    public boolean isEnablePerformanceMetrics() { return enablePerformanceMetrics; }
    public int getMetricsReportingIntervalMs() { return metricsReportingIntervalMs; }
    
    // Predefined configurations for different use cases
    public static RegistryPerformanceConfig forDevelopment() {
        return new Builder()
            .targetTps(1000)
            .maxConcurrentMachines(1000)
            .enablePerformanceMetrics(true)
            .build();
    }
    
    public static RegistryPerformanceConfig forTesting() {
        return new Builder()
            .targetTps(5000)
            .maxConcurrentMachines(5000)
            .enablePerformanceMetrics(true)
            .metricsReportingIntervalMs(1000)
            .build();
    }
    
    public static RegistryPerformanceConfig forProduction() {
        return new Builder()
            .targetTps(50000)
            .maxConcurrentMachines(50000)
            .useAdaptiveThreading(true)
            .enablePerformanceMetrics(true)
            .build();
    }
    
    public static RegistryPerformanceConfig forHighVolumeTelecom() {
        return new Builder()
            .targetTps(100000)
            .maxConcurrentMachines(100000)
            .maxEventsPerMachinePerSecond(50)
            .useAdaptiveThreading(true)
            .enablePerformanceMetrics(true)
            .metricsReportingIntervalMs(5000)
            .build();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private int targetTps = 10000; // Target capacity, not throttling limit
        private int maxEventsPerMachinePerSecond = 100;
        private int maxConcurrentMachines = 100000;
        private int maxMachinesPerPartner = 1000;
        private int virtualThreadPoolSize = -1; // Will be auto-calculated in build()
        private boolean useAdaptiveThreading = true;
        private int eventQueueCapacity = 50000;
        private int machineEvictionThreshold = 8000;
        private long machineIdleTimeoutMs = 300000; // 5 minutes
        private boolean enablePerformanceMetrics = true;
        private int metricsReportingIntervalMs = 5000;
        
        public Builder targetTps(int targetTps) {
            this.targetTps = targetTps;
            return this;
        }
        
        public Builder maxEventsPerMachinePerSecond(int maxEventsPerMachinePerSecond) {
            this.maxEventsPerMachinePerSecond = maxEventsPerMachinePerSecond;
            return this;
        }
        
        public Builder maxConcurrentMachines(int maxConcurrentMachines) {
            this.maxConcurrentMachines = maxConcurrentMachines;
            this.machineEvictionThreshold = (int) (maxConcurrentMachines * 0.8);
            return this;
        }
        
        public Builder maxMachinesPerPartner(int maxMachinesPerPartner) {
            this.maxMachinesPerPartner = maxMachinesPerPartner;
            return this;
        }
        
        public Builder virtualThreadPoolSize(int virtualThreadPoolSize) {
            this.virtualThreadPoolSize = virtualThreadPoolSize;
            this.useAdaptiveThreading = false; // Explicit size disables adaptive
            return this;
        }
        
        public Builder useAdaptiveThreading(boolean useAdaptiveThreading) {
            this.useAdaptiveThreading = useAdaptiveThreading;
            if (useAdaptiveThreading) {
                this.virtualThreadPoolSize = calculateOptimalThreadPoolSize(targetTps);
            }
            return this;
        }
        
        public Builder eventQueueCapacity(int eventQueueCapacity) {
            this.eventQueueCapacity = eventQueueCapacity;
            return this;
        }
        
        public Builder machineEvictionThreshold(int machineEvictionThreshold) {
            this.machineEvictionThreshold = machineEvictionThreshold;
            return this;
        }
        
        public Builder machineIdleTimeoutMs(long machineIdleTimeoutMs) {
            this.machineIdleTimeoutMs = machineIdleTimeoutMs;
            return this;
        }
        
        public Builder enablePerformanceMetrics(boolean enablePerformanceMetrics) {
            this.enablePerformanceMetrics = enablePerformanceMetrics;
            return this;
        }
        
        public Builder metricsReportingIntervalMs(int metricsReportingIntervalMs) {
            this.metricsReportingIntervalMs = metricsReportingIntervalMs;
            return this;
        }
        
        public RegistryPerformanceConfig build() {
            validate();
            autoCalculateSizes(); // Auto-calculate optimal sizes based on TPS
            return new RegistryPerformanceConfig(this);
        }
        
        private void validate() {
            if (targetTps <= 0) {
                throw new IllegalArgumentException("targetTps must be positive");
            }
            if (maxConcurrentMachines <= 0) {
                throw new IllegalArgumentException("maxConcurrentMachines must be positive");
            }
            // Note: virtualThreadPoolSize validation moved to autoCalculateSizes() since it can be auto-calculated
            if (machineEvictionThreshold >= maxConcurrentMachines) {
                throw new IllegalArgumentException("machineEvictionThreshold must be less than maxConcurrentMachines");
            }
        }
        
        private static int calculateOptimalThreadPoolSize(int targetTps) {
            int cpuCores = Runtime.getRuntime().availableProcessors();
            // Base calculation: assume each virtual thread can handle ~50 TPS
            int threadsForTps = Math.max(1, targetTps / 50);
            // Consider I/O bound operations (database, API calls)
            int ioThreads = cpuCores * 8;
            // Take the higher of TPS-based or I/O-based calculation
            return Math.min(Math.max(threadsForTps, ioThreads), 5000); // Max 5000 for safety
        }
        
        /**
         * Auto-calculate buffer and pool sizes based on target TPS
         */
        private void autoCalculateSizes() {
            // Auto-calculate virtual thread pool size if not explicitly set
            if (virtualThreadPoolSize <= 0) {
                virtualThreadPoolSize = calculateOptimalThreadPoolSize(targetTps);
            }
            
            // Auto-calculate event queue capacity based on TPS (buffer for 1 second + safety margin)
            if (eventQueueCapacity <= 0) {
                eventQueueCapacity = Math.max(10000, targetTps * 2); // 2 seconds buffer
            }
            
            // Auto-calculate machine eviction threshold (80% of max concurrent)
            machineEvictionThreshold = (int) (maxConcurrentMachines * 0.8);
            
            // Final validation
            if (virtualThreadPoolSize <= 0) {
                throw new IllegalArgumentException("virtualThreadPoolSize must be positive after auto-calculation");
            }
        }
    }
    
    @Override
    public String toString() {
        return String.format(
            "RegistryPerformanceConfig{" +
            "targetTps=%d, " +
            "maxConcurrentMachines=%d, " +
            "virtualThreadPoolSize=%d, " +
            "useAdaptiveThreading=%s, " +
            "eventQueueCapacity=%d" +
            "}",
            targetTps, maxConcurrentMachines, virtualThreadPoolSize, 
            useAdaptiveThreading, eventQueueCapacity
        );
    }
}