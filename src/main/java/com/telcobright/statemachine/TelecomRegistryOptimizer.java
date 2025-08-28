package com.telcobright.statemachine;

import com.telcobright.statemachine.performance.TelecomPerformanceKit;
import com.telcobright.statemachine.events.StateMachineEvent;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.io.IOException;

/**
 * Telecom Registry Optimizer - Wrapper around existing StateMachineRegistry
 * Adds TPS management, resource pooling, and performance optimizations
 * 
 * This approach preserves API compatibility while adding optimizations
 */
public class TelecomRegistryOptimizer {
    
    // Configuration
    private final int maxConcurrentMachines;
    private final int optimizedTpsLimit;
    private final int maxEventsPerMachinePerSec;
    
    // Core registry (composition, not inheritance)
    private final StateMachineRegistry coreRegistry;
    private final TelecomPerformanceKit performanceKit;
    
    // TPS Monitoring and Control
    private final AtomicInteger currentMachineCount = new AtomicInteger(0);
    private final LongAdder currentSecondEvents = new LongAdder();
    private final AtomicLong lastSecondTimestamp = new AtomicLong(System.currentTimeMillis() / 1000);
    private final AtomicInteger currentTps = new AtomicInteger(0);
    
    // Resource Control
    private final Semaphore eventProcessingSemaphore;
    private final Semaphore machineCreationSemaphore;
    private final ExecutorService eventProcessingExecutor;
    private final ScheduledExecutorService tpsMonitorExecutor;
    
    // Per-machine event tracking
    private final ConcurrentHashMap<String, MachineEventTracker> machineTrackers = new ConcurrentHashMap<>();
    
    // Statistics
    private final AtomicLong totalEventsProcessed = new AtomicLong(0);
    private final AtomicLong totalEventsThrottled = new AtomicLong(0);
    private final AtomicLong totalMachinesCreated = new AtomicLong(0);
    
    public TelecomRegistryOptimizer(StateMachineRegistry coreRegistry, TelecomRegistryConfig config) throws IOException {
        this.coreRegistry = coreRegistry;
        this.maxConcurrentMachines = config.maxConcurrentMachines;
        this.optimizedTpsLimit = config.optimizedTpsLimit;
        this.maxEventsPerMachinePerSec = config.maxEventsPerMachinePerSec;
        
        // Initialize performance kit  
        this.performanceKit = TelecomPerformanceKit.forDevelopment(config.registryId, maxConcurrentMachines);
        
        // Create resource control
        this.eventProcessingSemaphore = new Semaphore(optimizedTpsLimit);
        this.machineCreationSemaphore = new Semaphore(maxConcurrentMachines);
        
        // Create optimized thread pools
        this.eventProcessingExecutor = Executors.newFixedThreadPool(
            Math.min(Runtime.getRuntime().availableProcessors() * 4, optimizedTpsLimit / 100),
            r -> new Thread(r, "TelecomOptimizer-EventProcessor")
        );
        
        this.tpsMonitorExecutor = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "TelecomOptimizer-TPSMonitor")
        );
        
        // Start monitoring
        startTpsMonitoring();
        
        System.out.println("🚀 TelecomRegistryOptimizer initialized:");
        System.out.println("   📞 Max Concurrent Machines: " + maxConcurrentMachines);
        System.out.println("   ⚡ Optimized TPS Limit: " + optimizedTpsLimit + " events/sec");
        System.out.println("   🎯 Max Events Per Machine: " + maxEventsPerMachinePerSec + " events/sec");
    }
    
    private void startTpsMonitoring() {
        tpsMonitorExecutor.scheduleAtFixedRate(() -> {
            long currentSecond = System.currentTimeMillis() / 1000;
            long lastSecond = lastSecondTimestamp.get();
            
            if (currentSecond > lastSecond) {
                int eventsInLastSecond = (int) currentSecondEvents.sum();
                currentTps.set(eventsInLastSecond);
                currentSecondEvents.reset();
                lastSecondTimestamp.set(currentSecond);
                
                if (eventsInLastSecond > optimizedTpsLimit * 0.8) {
                    System.out.println("⚠️ High TPS: " + eventsInLastSecond + "/" + optimizedTpsLimit);
                }
            }
        }, 100, 100, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Register machine with optimization
     */
    public boolean registerMachine(String machineId, GenericStateMachine<?, ?> machine) {
        // Check machine limit
        if (!machineCreationSemaphore.tryAcquire()) {
            System.err.println("❌ Machine registration rejected - capacity reached");
            return false;
        }
        
        try {
            // Optimize machine ID
            String optimizedMachineId = performanceKit.optimizePhoneNumber(machineId);
            
            // Register with core registry
            coreRegistry.register(optimizedMachineId, machine);
            
            // Registration successful
            currentMachineCount.incrementAndGet();
            totalMachinesCreated.incrementAndGet();
            
            // Create event tracker
            machineTrackers.put(optimizedMachineId, new MachineEventTracker(maxEventsPerMachinePerSec));
            
            // Log to performance kit
            performanceKit.logRegistryEvent(optimizedMachineId, "MACHINE_REGISTERED", 
                "Optimized registration", System.currentTimeMillis());
            
            System.out.println("✅ Optimized machine registered: " + optimizedMachineId + 
                             " (total: " + currentMachineCount.get() + ")");
            return true;
            
        } catch (Exception e) {
            machineCreationSemaphore.release();
            return false;
        }
    }
    
    /**
     * Send event with TPS optimization
     */
    public boolean sendEvent(String machineId, StateMachineEvent event) {
        // Check system TPS limit
        if (!eventProcessingSemaphore.tryAcquire()) {
            totalEventsThrottled.incrementAndGet();
            return false;
        }
        
        try {
            // Check per-machine event rate
            MachineEventTracker tracker = machineTrackers.get(machineId);
            if (tracker != null && !tracker.allowEvent()) {
                totalEventsThrottled.incrementAndGet();
                return false;
            }
            
            // Process event asynchronously
            CompletableFuture.runAsync(() -> {
                try {
                    String optimizedMachineId = performanceKit.optimizePhoneNumber(machineId);
                    
                    boolean success = coreRegistry.sendEvent(optimizedMachineId, event);
                    
                    if (success) {
                        currentSecondEvents.increment();
                        totalEventsProcessed.incrementAndGet();
                        
                        if (tracker != null) {
                            tracker.recordEvent();
                        }
                        
                        // Log to performance kit
                        performanceKit.logHistoryEvent(optimizedMachineId, 
                            event.getEventType(), "PROCESSING", "PROCESSED", 
                            System.currentTimeMillis(), event.getDescription());
                    }
                } catch (Exception e) {
                    System.err.println("Event processing error: " + e.getMessage());
                }
            }, eventProcessingExecutor);
            
            return true;
            
        } finally {
            eventProcessingSemaphore.release();
        }
    }
    
    /**
     * Release machine and free resources
     */
    public boolean releaseMachine(String machineId) {
        String optimizedMachineId = performanceKit.optimizePhoneNumber(machineId);
        
        MachineEventTracker tracker = machineTrackers.remove(optimizedMachineId);
        if (tracker != null) {
            currentMachineCount.decrementAndGet();
            machineCreationSemaphore.release();
            
            performanceKit.logRegistryEvent(optimizedMachineId, "MACHINE_RELEASED", 
                "Machine completed", System.currentTimeMillis());
            
            System.out.println("🔄 Machine released: " + optimizedMachineId);
            return true;
        }
        return false;
    }
    
    /**
     * Get comprehensive statistics
     */
    public TelecomOptimizerStats getOptimizedStats() {
        return new TelecomOptimizerStats(
            currentMachineCount.get(),
            maxConcurrentMachines,
            currentTps.get(),
            optimizedTpsLimit,
            totalEventsProcessed.get(),
            totalEventsThrottled.get(),
            totalMachinesCreated.get(),
            eventProcessingSemaphore.availablePermits(),
            machineCreationSemaphore.availablePermits()
        );
    }
    
    /**
     * Print comprehensive performance statistics
     */
    public void printOptimizedStats() {
        TelecomOptimizerStats stats = getOptimizedStats();
        
        System.out.println("\n📊 Optimized Telecom Registry Statistics");
        System.out.println("=========================================");
        System.out.println("🏢 Active Machines: " + stats.activeMachines + "/" + stats.maxMachines);
        System.out.println("⚡ Current TPS: " + stats.currentTps + "/" + stats.maxTps);
        System.out.println("📈 Total Events: " + stats.totalEventsProcessed);
        System.out.println("🚦 Events Throttled: " + stats.totalEventsThrottled);
        System.out.println("🎯 Available Permits: " + stats.availableEventPermits + " events, " + 
                         stats.availableMachinePermits + " machines");
        
        double machineUtilization = (stats.activeMachines * 100.0) / stats.maxMachines;
        double tpsUtilization = (stats.currentTps * 100.0) / stats.maxTps;
        
        System.out.println("📊 Machine Utilization: " + String.format("%.1f%%", machineUtilization));
        System.out.println("📊 TPS Utilization: " + String.format("%.1f%%", tpsUtilization));
        
        // Performance kit statistics
        performanceKit.printStatistics();
        System.out.println("=========================================\n");
    }
    
    /**
     * Get access to core registry for existing functionality
     */
    public StateMachineRegistry getCoreRegistry() {
        return coreRegistry;
    }
    
    /**
     * Get access to performance kit
     */
    public TelecomPerformanceKit getPerformanceKit() {
        return performanceKit;
    }
    
    /**
     * Graceful shutdown
     */
    public void shutdown() {
        System.out.println("🛑 Shutting down TelecomRegistryOptimizer...");
        
        tpsMonitorExecutor.shutdown();
        eventProcessingExecutor.shutdown();
        
        try {
            if (!eventProcessingExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                eventProcessingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            eventProcessingExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        performanceKit.shutdown();
        machineTrackers.clear();
        
        System.out.println("✅ TelecomRegistryOptimizer shutdown complete");
    }
    
    /**
     * Configuration for TelecomRegistryOptimizer
     */
    public static class TelecomRegistryConfig {
        public final String registryId;
        public final int maxConcurrentMachines;
        public final int optimizedTpsLimit;
        public final int maxEventsPerMachinePerSec;
        
        public TelecomRegistryConfig(String registryId, int maxMachines, int tpsLimit, int eventsPerMachine) {
            this.registryId = registryId;
            this.maxConcurrentMachines = maxMachines;
            this.optimizedTpsLimit = tpsLimit;
            this.maxEventsPerMachinePerSec = eventsPerMachine;
        }
        
        public static TelecomRegistryConfig forCallCenter(String id, int maxCalls) {
            return new TelecomRegistryConfig(id, maxCalls, maxCalls * 4, 4);
        }
        
        public static TelecomRegistryConfig forSmsGateway(String id, int maxSms) {
            return new TelecomRegistryConfig(id, maxSms, maxSms * 2, 2);
        }
        
        public static TelecomRegistryConfig forTelecomCore(String id, int maxMachines) {
            return new TelecomRegistryConfig(id, maxMachines, Math.min(maxMachines * 4, 100000), 4);
        }
    }
    
    /**
     * Per-machine event rate tracker
     */
    private static class MachineEventTracker {
        private final int maxEventsPerSecond;
        private final AtomicInteger currentSecondEvents = new AtomicInteger(0);
        private final AtomicLong lastSecondTimestamp = new AtomicLong(System.currentTimeMillis() / 1000);
        
        public MachineEventTracker(int maxEventsPerSecond) {
            this.maxEventsPerSecond = maxEventsPerSecond;
        }
        
        public boolean allowEvent() {
            long currentSecond = System.currentTimeMillis() / 1000;
            long lastSecond = lastSecondTimestamp.get();
            
            if (currentSecond > lastSecond) {
                currentSecondEvents.set(0);
                lastSecondTimestamp.set(currentSecond);
            }
            
            return currentSecondEvents.get() < maxEventsPerSecond;
        }
        
        public void recordEvent() {
            currentSecondEvents.incrementAndGet();
        }
    }
    
    /**
     * Statistics snapshot
     */
    public static class TelecomOptimizerStats {
        public final int activeMachines;
        public final int maxMachines;
        public final int currentTps;
        public final int maxTps;
        public final long totalEventsProcessed;
        public final long totalEventsThrottled;
        public final long totalMachinesCreated;
        public final int availableEventPermits;
        public final int availableMachinePermits;
        
        public TelecomOptimizerStats(int activeMachines, int maxMachines, int currentTps, int maxTps,
                                   long totalEventsProcessed, long totalEventsThrottled,
                                   long totalMachinesCreated, int availableEventPermits, int availableMachinePermits) {
            this.activeMachines = activeMachines;
            this.maxMachines = maxMachines;
            this.currentTps = currentTps;
            this.maxTps = maxTps;
            this.totalEventsProcessed = totalEventsProcessed;
            this.totalEventsThrottled = totalEventsThrottled;
            this.totalMachinesCreated = totalMachinesCreated;
            this.availableEventPermits = availableEventPermits;
            this.availableMachinePermits = availableMachinePermits;
        }
    }
}