package com.telcobright.statemachine.performance;

import com.telcobright.core.*;
import com.telcobright.core.timeout.TimeoutManager;
import com.telcobright.debugger.CallMachineRunnerEnhanced;
import com.telcobright.examples.callmachine.events.*;
import com.telcobright.core.events.StateMachineEvent;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Random;

/**
 * High-TPS Optimized Registry Test
 * Tests the OptimizedTelecomRegistry under extreme load conditions
 * Validates TPS limits, resource borrowing/pooling, and performance optimizations
 */
public class HighTpsOptimizedRegistryTest {
    
    private static final int TEST_DURATION_SECONDS = 60;
    private static final int RAMP_UP_SECONDS = 10;
    
    // Test configuration
    private final int maxConcurrentMachines;
    private final int optimizedTpsLimit;
    private final int maxEventsPerMachinePerSec;
    
    // Test infrastructure
    private TelecomRegistryOptimizer optimizer;
    private StateMachineRegistry coreRegistry;
    private TimeoutManager timeoutManager;
    private ExecutorService loadGeneratorExecutor;
    private ScheduledExecutorService statsReporter;
    
    // Test metrics
    private final AtomicLong totalEventsSent = new AtomicLong(0);
    private final AtomicLong totalEventsSucceeded = new AtomicLong(0);
    private final AtomicLong totalEventsThrottled = new AtomicLong(0);
    private final AtomicInteger activeMachines = new AtomicInteger(0);
    private final AtomicLong startTime = new AtomicLong(0);
    
    public HighTpsOptimizedRegistryTest(int maxMachines, int tpsLimit) {
        this.maxConcurrentMachines = maxMachines;
        this.optimizedTpsLimit = tpsLimit;
        this.maxEventsPerMachinePerSec = 4; // Typical call processing: IncomingCall, Answer, Connected, Hangup
    }
    
    public void runHighTpsTest() throws Exception {
        System.out.println("🚀 Starting High-TPS Optimized Registry Test");
        System.out.println("============================================");
        System.out.println("📊 Configuration:");
        System.out.println("   Max Concurrent Machines: " + maxConcurrentMachines);
        System.out.println("   Optimized TPS Limit: " + optimizedTpsLimit);
        System.out.println("   Max Events Per Machine/Sec: " + maxEventsPerMachinePerSec);
        System.out.println("   Test Duration: " + TEST_DURATION_SECONDS + " seconds");
        System.out.println();
        
        // Initialize test infrastructure
        initializeTest();
        
        // Phase 1: Ramp-up load generation
        System.out.println("📈 Phase 1: Ramp-up (" + RAMP_UP_SECONDS + " seconds)");
        startTime.set(System.currentTimeMillis());
        rampUpLoad();
        
        // Phase 2: Sustained high load
        System.out.println("⚡ Phase 2: Sustained High Load (" + (TEST_DURATION_SECONDS - RAMP_UP_SECONDS) + " seconds)");
        sustainedLoad();
        
        // Phase 3: Results and validation
        System.out.println("📊 Phase 3: Results Analysis");
        analyzeResults();
        
        // Cleanup
        cleanup();
    }
    
    private void initializeTest() throws Exception {
        System.out.println("🔧 Initializing optimized registry...");
        
        // Create timeout manager
        timeoutManager = new TimeoutManager();
        
        // Create core registry
        coreRegistry = new StateMachineRegistry("high_tps_test", timeoutManager, 19998);
        
        // Create optimizer wrapper
        TelecomRegistryOptimizer.TelecomRegistryConfig config = 
            TelecomRegistryOptimizer.TelecomRegistryConfig.forCallCenter(
                "high_tps_test", maxConcurrentMachines);
        
        optimizer = new TelecomRegistryOptimizer(coreRegistry, config);
        
        // Create load generation infrastructure
        int loadThreads = Math.min(Runtime.getRuntime().availableProcessors() * 4, 50);
        loadGeneratorExecutor = Executors.newFixedThreadPool(loadThreads,
            r -> new Thread(r, "HighTpsLoadGenerator"));
        
        // Create stats reporter
        statsReporter = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "HighTpsStatsReporter"));
        
        // Start periodic stats reporting
        statsReporter.scheduleAtFixedRate(this::reportRealTimeStats, 5, 5, TimeUnit.SECONDS);
        
        System.out.println("✅ Optimized registry initialized with " + loadThreads + " load generation threads");
    }
    
    private void rampUpLoad() throws InterruptedException {
        System.out.println("⬆️ Starting load ramp-up...");
        
        // Gradually increase load over ramp-up period
        int rampUpIntervals = RAMP_UP_SECONDS;
        int machinesPerInterval = maxConcurrentMachines / rampUpIntervals;
        
        for (int interval = 1; interval <= rampUpIntervals; interval++) {
            int targetMachines = Math.min(machinesPerInterval * interval, maxConcurrentMachines);
            
            // Create machines for this interval
            createMachinesForInterval(targetMachines - activeMachines.get());
            
            // Start event generation for active machines
            startEventGeneration();
            
            System.out.println("   Interval " + interval + ": Target machines = " + targetMachines + 
                             ", Active = " + activeMachines.get());
            
            Thread.sleep(1000);
        }
        
        System.out.println("🎯 Ramp-up complete - Active machines: " + activeMachines.get());
    }
    
    private void createMachinesForInterval(int machinesToCreate) {
        if (machinesToCreate <= 0) return;
        
        CompletableFuture[] creationTasks = new CompletableFuture[machinesToCreate];
        
        for (int i = 0; i < machinesToCreate; i++) {
            final int machineIndex = activeMachines.get() + i;
            
            creationTasks[i] = CompletableFuture.runAsync(() -> {
                try {
                    String machineId = "htp-test-" + String.format("%06d", machineIndex);
                    createOptimizedCallMachine(machineId);
                    activeMachines.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Failed to create machine " + machineIndex + ": " + e.getMessage());
                }
            }, loadGeneratorExecutor);
        }
        
        // Wait for all machines to be created
        try {
            CompletableFuture.allOf(creationTasks).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("Machine creation timeout: " + e.getMessage());
        }
    }
    
    private void createOptimizedCallMachine(String machineId) {
        // Create contexts
        CallMachineRunnerEnhanced.CallPersistentContext persistentContext = 
            new CallMachineRunnerEnhanced.CallPersistentContext(machineId, 
                "+1-555-" + String.format("%04d", new Random().nextInt(10000)),
                "+1-555-DEST");
        CallMachineRunnerEnhanced.CallVolatileContext volatileContext = 
            new CallMachineRunnerEnhanced.CallVolatileContext();
        
        // Build machine using standard EnhancedFluentBuilder
        EnhancedFluentBuilder<CallMachineRunnerEnhanced.CallPersistentContext, CallMachineRunnerEnhanced.CallVolatileContext> builder = 
            EnhancedFluentBuilder.create(machineId);
        
        GenericStateMachine<CallMachineRunnerEnhanced.CallPersistentContext, CallMachineRunnerEnhanced.CallVolatileContext> machine = 
            builder.withPersistentContext(persistentContext)
                .withVolatileContext(volatileContext)
                .withSampleLogging(SampleLoggingConfig.oneIn2())
                .initialState("IDLE")
                .state("IDLE")
                    .onEntry(() -> {
                        // Simulate billing initialization
                        CompletableFuture.runAsync(() -> {
                            try { Thread.sleep(1); } catch (InterruptedException e) {}
                        });
                    })
                    .on(IncomingCall.class).to("RINGING")
                    .done()
                .state("RINGING")
                    .onEntry(() -> {
                        // Simulate billing start  
                        CompletableFuture.runAsync(() -> {
                            try { Thread.sleep(1); } catch (InterruptedException e) {}
                        });
                    })
                    .on(Answer.class).to("CONNECTED")
                    .on(Hangup.class).to("IDLE")
                    .done()
                .state("CONNECTED")
                    .onEntry(() -> {
                        // Simulate call routing
                        CompletableFuture.runAsync(() -> {
                            try { Thread.sleep(1); } catch (InterruptedException e) {}
                        });
                    })
                    .onExit(() -> {
                        // Simulate billing finalization
                        CompletableFuture.runAsync(() -> {
                            try { Thread.sleep(2); } catch (InterruptedException e) {}
                        });
                    })
                    .on(Hangup.class).to("IDLE")
                    .done()
                .build();
        
        // Register with optimizer
        optimizer.registerMachine(machineId, machine);
        
        machine.start();
    }
    
    private void startEventGeneration() {
        // Generate events at configured rate for active machines
        loadGeneratorExecutor.submit(() -> {
            Random random = new Random();
            
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    int machineIndex = random.nextInt(activeMachines.get());
                    String machineId = "htp-test-" + String.format("%06d", machineIndex);
                    
                    // Generate random call events
                    StateMachineEvent event = generateRandomCallEvent(random);
                    
                    totalEventsSent.incrementAndGet();
                    
                    boolean success = optimizer.sendEvent(machineId, event);
                    if (success) {
                        totalEventsSucceeded.incrementAndGet();
                    } else {
                        totalEventsThrottled.incrementAndGet();
                    }
                    
                    // Throttle to respect per-machine event limits
                    Thread.sleep(1000 / maxEventsPerMachinePerSec);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // Continue on errors
                }
            }
        });
    }
    
    private StateMachineEvent generateRandomCallEvent(Random random) {
        int eventType = random.nextInt(3);
        switch (eventType) {
            case 0: return new IncomingCall();
            case 1: return new Answer();
            case 2: return new Hangup();
            default: return new IncomingCall();
        }
    }
    
    private void sustainedLoad() throws InterruptedException {
        System.out.println("🔥 Sustaining high load with " + activeMachines.get() + " machines...");
        
        // Continue load generation for remaining test duration
        Thread.sleep((TEST_DURATION_SECONDS - RAMP_UP_SECONDS) * 1000);
    }
    
    private void reportRealTimeStats() {
        long elapsedMs = System.currentTimeMillis() - startTime.get();
        double elapsedSec = elapsedMs / 1000.0;
        
        TelecomRegistryOptimizer.TelecomOptimizerStats stats = optimizer.getOptimizedStats();
        
        double currentTps = totalEventsSent.get() / elapsedSec;
        double successRate = totalEventsSent.get() > 0 ? 
            (totalEventsSucceeded.get() * 100.0) / totalEventsSent.get() : 0.0;
        
        System.out.println("📊 Real-time Stats (T+" + String.format("%.1f", elapsedSec) + "s):");
        System.out.println("   🏭 Active Machines: " + stats.activeMachines + "/" + stats.maxMachines);
        System.out.println("   ⚡ Current TPS: " + stats.currentTps + "/" + stats.maxTps + 
                         " (avg: " + String.format("%.1f", currentTps) + ")");
        System.out.println("   ✅ Events: " + totalEventsSucceeded.get() + "/" + totalEventsSent.get() + 
                         " (" + String.format("%.1f%%", successRate) + " success)");
        System.out.println("   🚦 Throttled: " + totalEventsThrottled.get());
        System.out.println("   💾 Available Permits: " + stats.availableEventPermits + " events, " + 
                         stats.availableMachinePermits + " machines");
        System.out.println();
    }
    
    private void analyzeResults() {
        long totalDurationMs = System.currentTimeMillis() - startTime.get();
        double totalDurationSec = totalDurationMs / 1000.0;
        
        TelecomRegistryOptimizer.TelecomOptimizerStats finalStats = optimizer.getOptimizedStats();
        
        System.out.println("\n🎯 FINAL TEST RESULTS");
        System.out.println("=" .repeat(50));
        
        // Performance metrics
        double avgTps = totalEventsSent.get() / totalDurationSec;
        double successRate = totalEventsSent.get() > 0 ? 
            (totalEventsSucceeded.get() * 100.0) / totalEventsSent.get() : 0.0;
        double throttleRate = totalEventsSent.get() > 0 ? 
            (totalEventsThrottled.get() * 100.0) / totalEventsSent.get() : 0.0;
        
        System.out.println("📊 Performance Results:");
        System.out.println("   Total Duration: " + String.format("%.1f", totalDurationSec) + " seconds");
        System.out.println("   Average TPS: " + String.format("%.1f", avgTps) + "/" + optimizedTpsLimit);
        System.out.println("   Peak TPS: " + finalStats.currentTps);
        System.out.println("   Total Events Sent: " + totalEventsSent.get());
        System.out.println("   Events Succeeded: " + totalEventsSucceeded.get() + " (" + String.format("%.2f%%", successRate) + ")");
        System.out.println("   Events Throttled: " + totalEventsThrottled.get() + " (" + String.format("%.2f%%", throttleRate) + ")");
        
        // Resource utilization
        double machineUtilization = (finalStats.activeMachines * 100.0) / finalStats.maxMachines;
        double tpsUtilization = (avgTps * 100.0) / optimizedTpsLimit;
        
        System.out.println("\n🎯 Resource Utilization:");
        System.out.println("   Machine Capacity: " + String.format("%.1f%%", machineUtilization) + 
                         " (" + finalStats.activeMachines + "/" + finalStats.maxMachines + ")");
        System.out.println("   TPS Capacity: " + String.format("%.1f%%", tpsUtilization) + 
                         " (" + String.format("%.0f", avgTps) + "/" + optimizedTpsLimit + ")");
        
        // Test validation
        boolean tpsWithinLimits = avgTps <= optimizedTpsLimit * 1.1; // Allow 10% tolerance
        boolean successRateGood = successRate >= 95.0;
        boolean throttlingReasonable = throttleRate <= 10.0;
        
        System.out.println("\n✅ Test Validation:");
        System.out.println("   TPS Within Limits: " + (tpsWithinLimits ? "✅ PASS" : "❌ FAIL"));
        System.out.println("   Success Rate Good: " + (successRateGood ? "✅ PASS" : "❌ FAIL"));
        System.out.println("   Throttling Reasonable: " + (throttlingReasonable ? "✅ PASS" : "❌ FAIL"));
        
        boolean testPassed = tpsWithinLimits && successRateGood && throttlingReasonable;
        System.out.println("\n🏁 OVERALL TEST RESULT: " + (testPassed ? "✅ PASSED" : "❌ FAILED"));
        
        // Print detailed registry stats
        optimizer.printOptimizedStats();
    }
    
    private void cleanup() {
        System.out.println("🧹 Cleaning up test resources...");
        
        // Shutdown load generation
        if (loadGeneratorExecutor != null) {
            loadGeneratorExecutor.shutdownNow();
        }
        
        if (statsReporter != null) {
            statsReporter.shutdown();
        }
        
        // Shutdown optimizer
        optimizer.shutdown();
        coreRegistry.shutdownAsyncLogging();
        
        // Shutdown timeout manager
        timeoutManager.shutdown();
        
        System.out.println("✅ Cleanup complete");
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println("🎯 High-TPS Optimized Registry Test Suite");
        System.out.println("==========================================\n");
        
        // Test scenarios
        runTestScenario("Small Call Center", 1000, 4000);
        runTestScenario("Medium Call Center", 5000, 20000);  
        runTestScenario("Large Telecom Core", 10000, 40000);
        
        System.out.println("\n🎉 All High-TPS Tests Completed!");
    }
    
    private static void runTestScenario(String scenarioName, int maxMachines, int tpsLimit) throws Exception {
        System.out.println("\n🚀 Running Scenario: " + scenarioName);
        System.out.println("-".repeat(40));
        
        HighTpsOptimizedRegistryTest test = new HighTpsOptimizedRegistryTest(maxMachines, tpsLimit);
        test.runHighTpsTest();
        
        // Wait between scenarios
        Thread.sleep(5000);
    }
}