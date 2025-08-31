package com.telcobright.statemachine.performance;

import com.telcobright.core.*;
import com.telcobright.core.TelecomRegistryOptimizer;
import com.telcobright.core.timeout.TimeoutManager;
import com.telcobright.debugger.CallMachineRunnerEnhanced;
import com.telcobright.examples.callmachine.events.*;
import com.telcobright.core.events.StateMachineEvent;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Random;

/**
 * Optimized Registry Demo
 * Demonstrates the TelecomRegistryOptimizer with high TPS and concurrent machine management
 */
public class OptimizedRegistryDemo {
    
    public static void main(String[] args) throws Exception {
        System.out.println("🚀 Optimized Telecom Registry Demo");
        System.out.println("===================================\n");
        
        // Demo scenarios
        demoScenario("Small Call Center", 100, 400);
        Thread.sleep(3000);
        
        demoScenario("Medium Call Center", 500, 2000);
        Thread.sleep(3000);
        
        demoScenario("Large Telecom Core", 1000, 4000);
        
        System.out.println("\n🎉 All optimization demos completed successfully!");
    }
    
    private static void demoScenario(String scenarioName, int maxMachines, int tpsLimit) throws Exception {
        System.out.println("\n🎯 Demo Scenario: " + scenarioName);
        System.out.println("-".repeat(50));
        System.out.println("📊 Configuration:");
        System.out.println("   Max Concurrent Machines: " + maxMachines);
        System.out.println("   TPS Limit: " + tpsLimit);
        System.out.println("   Max Events Per Machine/Sec: 4");
        
        // Create core registry
        TimeoutManager timeoutManager = new TimeoutManager();
        StateMachineRegistry coreRegistry = new StateMachineRegistry(
            scenarioName.toLowerCase().replace(" ", "_"), timeoutManager, 19997);
        
        // Create optimizer wrapper
        TelecomRegistryOptimizer.TelecomRegistryConfig config = 
            TelecomRegistryOptimizer.TelecomRegistryConfig.forCallCenter(
                scenarioName.toLowerCase().replace(" ", "_"), maxMachines);
        
        TelecomRegistryOptimizer optimizer = new TelecomRegistryOptimizer(coreRegistry, config);
        
        // Demo Phase 1: Machine Creation with Optimization
        System.out.println("\n📞 Phase 1: Creating Optimized Call Machines");
        createOptimizedMachines(optimizer, Math.min(maxMachines / 4, 50));
        
        // Demo Phase 2: High-TPS Event Processing
        System.out.println("\n⚡ Phase 2: High-TPS Event Processing (30 seconds)");
        simulateHighTpsLoad(optimizer, 30);
        
        // Demo Phase 3: Resource Management
        System.out.println("\n🔄 Phase 3: Resource Management Demo");
        demonstrateResourceManagement(optimizer);
        
        // Demo Phase 4: Performance Statistics
        System.out.println("\n📈 Phase 4: Performance Statistics");
        optimizer.printOptimizedStats();
        
        // Cleanup
        optimizer.shutdown();
        coreRegistry.shutdownAsyncLogging();
        timeoutManager.shutdown();
        
        System.out.println("✅ " + scenarioName + " demo completed");
    }
    
    private static void createOptimizedMachines(TelecomRegistryOptimizer optimizer, int machineCount) {
        System.out.println("Creating " + machineCount + " optimized call machines...");
        
        ExecutorService creationExecutor = Executors.newFixedThreadPool(10);
        AtomicInteger createdCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        
        CompletableFuture[] creationTasks = new CompletableFuture[machineCount];
        
        for (int i = 0; i < machineCount; i++) {
            final int machineIndex = i;
            
            creationTasks[i] = CompletableFuture.runAsync(() -> {
                try {
                    String machineId = "opt-demo-" + String.format("%04d", machineIndex);
                    boolean success = createOptimizedCallMachine(optimizer, machineId);
                    
                    if (success) {
                        createdCount.incrementAndGet();
                        if (createdCount.get() % 10 == 0) {
                            System.out.println("   Created " + createdCount.get() + " machines...");
                        }
                    } else {
                        failedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failedCount.incrementAndGet();
                }
            }, creationExecutor);
        }
        
        // Wait for completion
        try {
            CompletableFuture.allOf(creationTasks).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("Machine creation timeout: " + e.getMessage());
        }
        
        creationExecutor.shutdown();
        
        System.out.println("✅ Machine creation complete:");
        System.out.println("   Successful: " + createdCount.get());
        System.out.println("   Failed: " + failedCount.get());
    }
    
    private static boolean createOptimizedCallMachine(TelecomRegistryOptimizer optimizer, String machineId) {
        try {
            // Create contexts
            CallMachineRunnerEnhanced.CallPersistentContext persistentContext = 
                new CallMachineRunnerEnhanced.CallPersistentContext(machineId, 
                    "+1-555-" + String.format("%04d", new Random().nextInt(10000)),
                    "+1-555-DEST");
            CallMachineRunnerEnhanced.CallVolatileContext volatileContext = 
                new CallMachineRunnerEnhanced.CallVolatileContext();
            
            // Build state machine with optimizations
            GenericStateMachine<CallMachineRunnerEnhanced.CallPersistentContext, CallMachineRunnerEnhanced.CallVolatileContext> machine = 
                EnhancedFluentBuilder.<CallMachineRunnerEnhanced.CallPersistentContext, CallMachineRunnerEnhanced.CallVolatileContext>create(machineId)
                    .withPersistentContext(persistentContext)
                    .withVolatileContext(volatileContext)
                    .withSampleLogging(SampleLoggingConfig.oneIn2())
                    .initialState("IDLE")
                    .state("IDLE")
                        .onEntry(() -> {
                            // Simulate billing initialization with async processing
                            CompletableFuture.runAsync(() -> {
                                try { 
                                    Thread.sleep(1); // Simulate billing lookup
                                } catch (InterruptedException e) {}
                            });
                        })
                        .on(IncomingCall.class).to("RINGING")
                        .done()
                    .state("RINGING") 
                        .onEntry(() -> {
                            // Simulate call routing setup
                            CompletableFuture.runAsync(() -> {
                                try { 
                                    Thread.sleep(2); // Simulate routing setup
                                } catch (InterruptedException e) {}
                            });
                        })
                        .on(Answer.class).to("CONNECTED")
                        .on(Hangup.class).to("IDLE")
                        .done()
                    .state("CONNECTED")
                        .onEntry(() -> {
                            // Simulate billing start
                            CompletableFuture.runAsync(() -> {
                                try { 
                                    Thread.sleep(1); // Simulate billing start
                                } catch (InterruptedException e) {}
                            });
                        })
                        .onExit(() -> {
                            // Simulate billing finalization
                            CompletableFuture.runAsync(() -> {
                                try { 
                                    Thread.sleep(3); // Simulate billing calculation
                                } catch (InterruptedException e) {}
                            });
                        })
                        .on(Hangup.class).to("IDLE")
                        .done()
                    .build();
            
            // Register with optimizer
            boolean registered = optimizer.registerMachine(machineId, machine);
            
            if (registered) {
                machine.start();
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            System.err.println("Failed to create machine " + machineId + ": " + e.getMessage());
            return false;
        }
    }
    
    private static void simulateHighTpsLoad(TelecomRegistryOptimizer optimizer, int durationSeconds) {
        System.out.println("Starting high-TPS event simulation for " + durationSeconds + " seconds...");
        
        TelecomRegistryOptimizer.TelecomOptimizerStats initialStats = optimizer.getOptimizedStats();
        int activeMachines = initialStats.activeMachines;
        
        if (activeMachines == 0) {
            System.out.println("⚠️ No active machines - skipping TPS simulation");
            return;
        }
        
        ExecutorService loadExecutor = Executors.newFixedThreadPool(
            Math.min(Runtime.getRuntime().availableProcessors() * 2, 20));
        
        AtomicLong eventsSent = new AtomicLong(0);
        AtomicLong eventsSucceeded = new AtomicLong(0);
        
        // Start load generation
        CompletableFuture<Void> loadGeneration = CompletableFuture.runAsync(() -> {
            Random random = new Random();
            long startTime = System.currentTimeMillis();
            long endTime = startTime + (durationSeconds * 1000L);
            
            while (System.currentTimeMillis() < endTime && !Thread.currentThread().isInterrupted()) {
                try {
                    // Pick random machine
                    int machineIndex = random.nextInt(activeMachines);
                    String machineId = "opt-demo-" + String.format("%04d", machineIndex);
                    
                    // Generate random event
                    StateMachineEvent event = generateRandomEvent(random);
                    
                    eventsSent.incrementAndGet();
                    
                    boolean success = optimizer.sendEvent(machineId, event);
                    if (success) {
                        eventsSucceeded.incrementAndGet();
                    }
                    
                    // Throttle to maintain realistic load
                    Thread.sleep(random.nextInt(5) + 1); // 1-5ms between events
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // Continue on errors
                }
            }
        }, loadExecutor);
        
        // Monitor progress
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(() -> {
            long sent = eventsSent.get();
            long succeeded = eventsSucceeded.get();
            double successRate = sent > 0 ? (succeeded * 100.0) / sent : 0.0;
            
            TelecomRegistryOptimizer.TelecomOptimizerStats stats = optimizer.getOptimizedStats();
            
            System.out.println("   📊 TPS: " + stats.currentTps + "/" + stats.maxTps + 
                             ", Events: " + succeeded + "/" + sent + 
                             " (" + String.format("%.1f%%", successRate) + ")");
        }, 5, 5, TimeUnit.SECONDS);
        
        // Wait for completion
        try {
            loadGeneration.get(durationSeconds + 10, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("Load generation error: " + e.getMessage());
        }
        
        monitor.shutdown();
        loadExecutor.shutdown();
        
        // Final results
        long finalSent = eventsSent.get();
        long finalSucceeded = eventsSucceeded.get();
        double finalSuccessRate = finalSent > 0 ? (finalSucceeded * 100.0) / finalSent : 0.0;
        double avgTps = finalSent / (double) durationSeconds;
        
        System.out.println("✅ High-TPS simulation complete:");
        System.out.println("   Total Events Sent: " + finalSent);
        System.out.println("   Events Succeeded: " + finalSucceeded + " (" + String.format("%.1f%%", finalSuccessRate) + ")");
        System.out.println("   Average TPS: " + String.format("%.1f", avgTps));
    }
    
    private static StateMachineEvent generateRandomEvent(Random random) {
        int eventType = random.nextInt(3);
        switch (eventType) {
            case 0: return new IncomingCall();
            case 1: return new Answer();
            case 2: return new Hangup();
            default: return new IncomingCall();
        }
    }
    
    private static void demonstrateResourceManagement(TelecomRegistryOptimizer optimizer) {
        System.out.println("Demonstrating resource management...");
        
        TelecomRegistryOptimizer.TelecomOptimizerStats stats = optimizer.getOptimizedStats();
        int activeMachines = stats.activeMachines;
        
        System.out.println("   Current active machines: " + activeMachines);
        System.out.println("   Available machine permits: " + stats.availableMachinePermits);
        System.out.println("   Available event permits: " + stats.availableEventPermits);
        
        // Demonstrate machine release
        if (activeMachines > 0) {
            System.out.println("   Releasing some machines to free resources...");
            
            for (int i = 0; i < Math.min(activeMachines / 4, 10); i++) {
                String machineId = "opt-demo-" + String.format("%04d", i);
                boolean released = optimizer.releaseMachine(machineId);
                if (released) {
                    System.out.println("   Released: " + machineId);
                }
            }
            
            TelecomRegistryOptimizer.TelecomOptimizerStats newStats = optimizer.getOptimizedStats();
            System.out.println("   Active machines after release: " + newStats.activeMachines);
            System.out.println("   Available machine permits: " + newStats.availableMachinePermits);
        }
    }
}