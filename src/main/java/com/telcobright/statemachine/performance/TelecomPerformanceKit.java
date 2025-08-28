package com.telcobright.statemachine.performance;

import com.telcobright.statemachine.pool.TelecomStringPool;
import com.telcobright.statemachine.pool.StringBuilderPool;
import com.telcobright.statemachine.batch.BatchHistoryLogger;
import com.telcobright.statemachine.batch.BatchRegistryLogger;
import com.telcobright.statemachine.mmap.MappedStatePersistence;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Simplified telecom performance kit for immediate integration
 * Provides ready-to-use performance optimizations without API changes
 */
public class TelecomPerformanceKit {
    
    // Core optimization components
    private final TelecomStringPool stringPool;
    private final StringBuilderPool stringBuilderPool;
    private final BatchHistoryLogger historyLogger;
    private final BatchRegistryLogger registryLogger;
    private final MappedStatePersistence statePersistence;
    
    // Statistics reporting
    private final ScheduledExecutorService statsExecutor;
    
    private TelecomPerformanceKit(String registryId, String mmapPath, int maxMachines) throws IOException {
        // Initialize core optimizations
        this.stringPool = TelecomStringPool.getInstance();
        this.stringBuilderPool = StringBuilderPool.getInstance();
        this.historyLogger = new BatchHistoryLogger();
        this.registryLogger = new BatchRegistryLogger(registryId);
        this.statePersistence = mmapPath != null ? 
            new MappedStatePersistence(mmapPath, maxMachines) : null;
        
        // Statistics reporting
        this.statsExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TelecomPerformanceKit-Stats");
            t.setDaemon(true);
            return t;
        });
        
        startStatisticsReporting();
    }
    
    /**
     * Factory method for common telecom configurations
     */
    public static TelecomPerformanceKit forCallCenter(String registryId, int maxConcurrentCalls) throws IOException {
        String mmapPath = maxConcurrentCalls > 1000 ? "/tmp/telecom-" + registryId + ".mmap" : null;
        return new TelecomPerformanceKit(registryId, mmapPath, maxConcurrentCalls);
    }
    
    /**
     * Factory method for SMS processing
     */
    public static TelecomPerformanceKit forSmsGateway(String registryId) throws IOException {
        return new TelecomPerformanceKit(registryId, null, 0);
    }
    
    /**
     * Factory method for high-volume telecom processing
     */
    public static TelecomPerformanceKit forHighVolume(String registryId, int maxMachines) throws IOException {
        return new TelecomPerformanceKit(registryId, "/var/lib/telecom/state-" + registryId + ".mmap", maxMachines);
    }
    
    public static TelecomPerformanceKit forDevelopment(String registryId, int maxMachines) throws IOException {
        return new TelecomPerformanceKit(registryId, System.getProperty("java.io.tmpdir") + "/telecom-state-" + registryId + ".mmap", maxMachines);
    }
    
    private void startStatisticsReporting() {
        statsExecutor.scheduleWithFixedDelay(() -> {
            System.out.println("\n=== Telecom Performance Statistics ===");
            System.out.println(stringPool.getStatistics());
            System.out.println(stringBuilderPool.getStatistics());
            System.out.println(historyLogger.getStatistics());
            System.out.println(registryLogger.getStatistics());
            if (statePersistence != null) {
                System.out.println(statePersistence.getStatistics());
            }
            System.out.println("=======================================");
        }, 60, 60, TimeUnit.SECONDS);
    }
    
    // Accessors for optimized components
    public TelecomStringPool getStringPool() { return stringPool; }
    public StringBuilderPool getStringBuilderPool() { return stringBuilderPool; }
    public BatchHistoryLogger getHistoryLogger() { return historyLogger; }
    public BatchRegistryLogger getRegistryLogger() { return registryLogger; }
    public MappedStatePersistence getStatePersistence() { return statePersistence; }
    
    /**
     * Optimize phone number using string pool
     */
    public String optimizePhoneNumber(String phoneNumber) {
        return stringPool.optimizePhoneNumber(phoneNumber);
    }
    
    /**
     * Get StringBuilder for SMS processing
     */
    public StringBuilder getSmsBuilder() {
        return stringBuilderPool.borrowSmsBuilder();
    }
    
    /**
     * Log history event with batching
     */
    public void logHistoryEvent(String machineId, String eventType, String fromState, 
                               String toState, long timestamp, String additionalData) {
        historyLogger.logEvent(machineId, eventType, fromState, toState, timestamp, additionalData);
    }
    
    /**
     * Log registry event with batching  
     */
    public void logRegistryEvent(String machineId, String eventType, String reason, long timestamp) {
        registryLogger.logEvent(machineId, eventType, reason, timestamp);
    }
    
    /**
     * Update machine state using memory-mapped persistence (if enabled)
     */
    public boolean updateMachineState(String machineId, String state, long timestamp, 
                                    String callerId, String calleeId, float billingAmount) {
        if (statePersistence == null) {
            return false;
        }
        
        MappedStatePersistence.CallState callState;
        try {
            callState = MappedStatePersistence.CallState.valueOf(state.toUpperCase());
        } catch (IllegalArgumentException e) {
            callState = MappedStatePersistence.CallState.IDLE;
        }
        
        MappedStatePersistence.CallData callData = new MappedStatePersistence.CallData(
            callerId, calleeId, 0, 0L, billingAmount, (short) 100
        );
        
        return statePersistence.updateMachineState(machineId, callState, timestamp, callData);
    }
    
    /**
     * Read machine state using memory-mapped persistence (if enabled)
     */
    public MappedStatePersistence.MachineStateSnapshot readMachineState(String machineId) {
        return statePersistence != null ? statePersistence.readMachineState(machineId) : null;
    }
    
    /**
     * Print current performance statistics
     */
    public void printStatistics() {
        System.out.println("\n=== Current Performance Statistics ===");
        System.out.println("📞 " + stringPool.getStatistics());
        System.out.println("📝 " + stringBuilderPool.getStatistics());
        System.out.println("📊 " + historyLogger.getStatistics());
        System.out.println("🗃️ " + registryLogger.getStatistics());
        if (statePersistence != null) {
            System.out.println("💾 " + statePersistence.getStatistics());
        }
        System.out.println("======================================\n");
    }
    
    /**
     * Graceful shutdown
     */
    public void shutdown() {
        System.out.println("🛑 Shutting down Telecom Performance Kit...");
        
        statsExecutor.shutdown();
        historyLogger.shutdown();
        registryLogger.shutdown();
        
        if (statePersistence != null) {
            try {
                statePersistence.close();
            } catch (IOException e) {
                System.err.println("Error closing state persistence: " + e.getMessage());
            }
        }
        
        System.out.println("✅ Telecom Performance Kit shutdown complete");
    }
}