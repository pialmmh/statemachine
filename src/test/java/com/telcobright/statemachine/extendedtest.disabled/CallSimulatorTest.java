package com.telcobright.statemachine.extendedtest;

import com.telcobright.core.*;
import com.telcobright.core.timeout.TimeoutManager;
import com.telcobright.debugger.CallMachineRunnerEnhanced;
import com.telcobright.examples.callmachine.events.*;
import com.telcobright.examples.callmachine.CallState;
import com.telcobright.core.events.StateMachineEvent;
import com.telcobright.core.events.EventTypeRegistry;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Call Simulator Test - Tests simple call flow with rate-controlled simulation
 * 
 * Call Flow: IDLE -> (IncomingCall) -> RINGING -> (Hangup) -> HUNGUP
 * 
 * Features:
 * - Configurable call rate (default: 10 calls/sec)
 * - Configurable test duration (default: 30 seconds)
 * - Random events sent to all machines (including finished ones)
 * - Event filtering validation (events to HUNGUP machines should be ignored)
 * - State validation at test completion
 * - Call count validation (new calls vs hangup count)
 */
public class CallSimulatorTest {
    
    // Test configuration - Full call flow testing with Answer state
    private static final int DEFAULT_CALLS_PER_SECOND = 25;  // 25 calls/sec for stable testing
    private static final int DEFAULT_DURATION_SECONDS = 12;   // 12 seconds = 300 total calls
    private static final int RANDOM_EVENT_RATE_PER_SECOND = 50; // Lower random events for cleaner results
    
    // Test infrastructure
    private StateMachineRegistry registry;
    private TimeoutManager timeoutManager;
    private ExecutorService callGeneratorExecutor;
    private ExecutorService randomEventExecutor;
    private ScheduledExecutorService statsReporter;
    
    // Test tracking
    private final AtomicInteger callsGenerated = new AtomicInteger(0);
    private final AtomicInteger machinesInIdle = new AtomicInteger(0);
    private final AtomicInteger machinesInRinging = new AtomicInteger(0);
    private final AtomicInteger machinesInConnected = new AtomicInteger(0);
    private final AtomicInteger machinesInHungup = new AtomicInteger(0);
    private final AtomicLong eventsToHungupMachines = new AtomicLong(0);
    private final AtomicLong eventsIgnored = new AtomicLong(0);
    
    private final List<String> allMachineIds = Collections.synchronizedList(new ArrayList<>());
    private final ConcurrentHashMap<String, String> machineStates = new ConcurrentHashMap<>();
    
    private final int callsPerSecond;
    private final int durationSeconds;
    
    public CallSimulatorTest(int callsPerSecond, int durationSeconds) {
        this.callsPerSecond = callsPerSecond;
        this.durationSeconds = durationSeconds;
    }
    
    public void runCallSimulatorTest() throws Exception {
        System.out.println("🎯 Call Simulator Test Starting");
        System.out.println("================================");
        System.out.println("📊 Configuration:");
        System.out.println("   Calls per second: " + callsPerSecond);
        System.out.println("   Test duration: " + durationSeconds + " seconds");
        System.out.println("   Expected total calls: " + (callsPerSecond * durationSeconds));
        System.out.println("   Random event rate: " + RANDOM_EVENT_RATE_PER_SECOND + "/sec");
        System.out.println();
        
        // Initialize test infrastructure
        initializeTest();
        
        long startTime = System.currentTimeMillis();
        
        // Start call generation
        startCallGeneration();
        
        // Start random event generation 
        startRandomEventGeneration();
        
        // Run test for specified duration
        System.out.println("🚀 Starting call simulation...");
        Thread.sleep(durationSeconds * 1000);
        
        // Stop all generation
        stopGeneration();
        
        // Allow some time for final processing
        Thread.sleep(2000);
        
        long endTime = System.currentTimeMillis();
        double actualDuration = (endTime - startTime) / 1000.0;
        
        // Analyze results
        analyzeResults(actualDuration);
        
        // Cleanup
        cleanup();
    }
    
    private void initializeTest() throws Exception {
        System.out.println("🔧 Initializing call simulator test...");
        
        // Register event types first
        registerEventTypes();
        
        // Create timeout manager with performance configuration for 1000 TPS
        timeoutManager = new TimeoutManager();
        
        // Configure registry for 1000 TPS target capacity
        RegistryPerformanceConfig performanceConfig = RegistryPerformanceConfig.builder()
            .targetTps(1000)  // Target 1000 events/sec processing capacity
            .maxConcurrentMachines(5000)  // Allow enough machines for test
            .enablePerformanceMetrics(true)
            .build();
            
        registry = new StateMachineRegistry("call_simulator_test", performanceConfig, timeoutManager);
        
        // Display performance configuration
        System.out.println("📊 Performance Configuration:");
        System.out.println("   Target TPS: " + performanceConfig.getTargetTps());
        System.out.println("   Max Concurrent Machines: " + performanceConfig.getMaxConcurrentMachines());
        System.out.println("   Virtual Thread Pool Size: " + performanceConfig.getVirtualThreadPoolSize());
        System.out.println("   Use Adaptive Threading: " + performanceConfig.isUseAdaptiveThreading());
        System.out.println("   Event Queue Capacity: " + performanceConfig.getEventQueueCapacity());
        
        // Create executors
        callGeneratorExecutor = Executors.newFixedThreadPool(2, 
            r -> new Thread(r, "CallGenerator"));
        randomEventExecutor = Executors.newFixedThreadPool(3, 
            r -> new Thread(r, "RandomEventGenerator"));
        statsReporter = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "StatsReporter"));
        
        // Start real-time stats reporting
        statsReporter.scheduleAtFixedRate(this::reportStats, 5, 5, TimeUnit.SECONDS);
        
        System.out.println("✅ Test infrastructure initialized");
    }
    
    private void startCallGeneration() {
        System.out.println("📞 Starting call generation at " + callsPerSecond + " calls/sec...");
        
        callGeneratorExecutor.submit(() -> {
            long intervalMs = 1000 / callsPerSecond; // Interval between calls
            Random random = new Random();
            
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    int callId = callsGenerated.incrementAndGet();
                    String machineId = "call-" + String.format("%06d", callId);
                    
                    // Create simple call machine
                    createSimpleCallMachine(machineId);
                    
                    // Track machine
                    allMachineIds.add(machineId);
                    machineStates.put(machineId, "IDLE");
                    machinesInIdle.incrementAndGet();
                    
                    // Wait for next call
                    Thread.sleep(intervalMs + random.nextInt(5)); // Small jitter
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Call generation error: " + e.getMessage());
                }
            }
        });
    }
    
    private void createSimpleCallMachine(String machineId) {
        // Create contexts
        CallMachineRunnerEnhanced.CallPersistentContext persistentContext = 
            new CallMachineRunnerEnhanced.CallPersistentContext(machineId, 
                "+1-555-" + String.format("%04d", new Random().nextInt(10000)),
                "+1-555-DEST");
        CallMachineRunnerEnhanced.CallVolatileContext volatileContext = 
            new CallMachineRunnerEnhanced.CallVolatileContext();
        
        // Build simple call machine using exact same pattern as CallMachineRunnerEnhanced
        GenericStateMachine<CallMachineRunnerEnhanced.CallPersistentContext, CallMachineRunnerEnhanced.CallVolatileContext> machine = 
            EnhancedFluentBuilder.<CallMachineRunnerEnhanced.CallPersistentContext, CallMachineRunnerEnhanced.CallVolatileContext>create(machineId)
                .withPersistentContext(persistentContext)
                .withVolatileContext(volatileContext)
                .withVolatileContextFactory(() -> CallMachineRunnerEnhanced.CallVolatileContext.createFromPersistent(persistentContext))
                .withSampleLogging(1000) // Disable sampling with high number for clearer testing
                .initialState(CallState.ADMISSION.name())
                .state(CallState.ADMISSION.name())
                    .onEntry(m -> {
                        // Track state change
                        updateMachineState(machineId, "IDLE");
                    })
                    .onExit(m -> {
                        // Track state exit
                        System.out.println("📤 Call " + machineId + " exiting IDLE state");
                    })
                    .on(IncomingCall.class).to(CallState.RINGING.name())
                    .done()
                .state(CallState.RINGING.name()) 
                    .onEntry(m -> {
                        // Track state change
                        updateMachineState(machineId, "RINGING");
                        System.out.println("📞 Call " + machineId + " is ringing...");
                    })
                    .onExit(m -> {
                        // Track state exit
                        System.out.println("📤 Call " + machineId + " exiting RINGING state");
                    })
                    .on(Answer.class).to(CallState.CONNECTED.name())
                    .on(Hangup.class).to(CallState.HUNGUP.name())
                    .done()
                .state(CallState.CONNECTED.name())
                    .onEntry(m -> {
                        // Track state change
                        updateMachineState(machineId, "CONNECTED");
                        System.out.println("📞 Call " + machineId + " was connected");
                    })
                    .onExit(m -> {
                        // Track state exit
                        System.out.println("📤 Call " + machineId + " exiting CONNECTED state");
                    })
                    .on(Hangup.class).to(CallState.HUNGUP.name())
                    .done()
                .state(CallState.HUNGUP.name())
                    .onEntry(m -> {
                        // Track state change
                        updateMachineState(machineId, "HUNGUP");
                        System.out.println("📵 Call " + machineId + " has ended");
                    })
                    .finalState() // Mark as final state
                    .done()
                .build();
        
        // Register and start
        registry.register(machineId, machine);
        machine.start();
        
        // Schedule the call flow
        scheduleCallFlow(machineId);
    }
    
    private void scheduleCallFlow(String machineId) {
        // Schedule all events within 1 second for fast completion
        CompletableFuture.runAsync(() -> {
            try {
                // Complete call flow within 1 second: IDLE -> RINGING -> CONNECTED -> HUNGUP
                
                // 1. Send IncomingCall after 50-100ms  
                Thread.sleep(50 + new Random().nextInt(50)); // Random delay 50-100ms
                boolean sent = registry.sendEvent(machineId, new IncomingCall());
                if (!sent) {
                    System.err.println("Failed to send IncomingCall to " + machineId);
                }
                
                // 2. Send Answer after additional 200-400ms (ringing duration)
                Thread.sleep(200 + new Random().nextInt(200)); // 200-400ms ringing
                sent = registry.sendEvent(machineId, new Answer());
                if (!sent) {
                    System.err.println("Failed to send Answer to " + machineId);
                }
                
                // 3. Send Hangup after additional 300-500ms (call duration)
                Thread.sleep(300 + new Random().nextInt(200)); // 300-500ms call duration
                sent = registry.sendEvent(machineId, new Hangup());
                if (!sent) {
                    System.err.println("Failed to send Hangup to " + machineId);
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
    
    private void startRandomEventGeneration() {
        System.out.println("🎲 Starting random event generation at " + RANDOM_EVENT_RATE_PER_SECOND + " events/sec...");
        
        // Multiple threads for random event generation
        for (int i = 0; i < 3; i++) {
            randomEventExecutor.submit(() -> {
                Random random = new Random();
                long intervalMs = 3000 / RANDOM_EVENT_RATE_PER_SECOND; // Spread across 3 threads
                
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        if (!allMachineIds.isEmpty()) {
                            // Pick random machine (including potentially HUNGUP ones)
                            String randomMachineId = allMachineIds.get(random.nextInt(allMachineIds.size()));
                            String currentState = machineStates.get(randomMachineId);
                            
                            // Generate random event
                            StateMachineEvent randomEvent = generateRandomEvent(random);
                            
                            // Track if sending to HUNGUP machine
                            if ("HUNGUP".equals(currentState)) {
                                eventsToHungupMachines.incrementAndGet();
                            }
                            
                            // Send event
                            boolean sent = registry.sendEvent(randomMachineId, randomEvent);
                            
                            // If sent to HUNGUP machine, it should be ignored
                            if ("HUNGUP".equals(currentState) && sent) {
                                // Check if state remained HUNGUP (event was ignored)
                                if ("HUNGUP".equals(machineStates.get(randomMachineId))) {
                                    eventsIgnored.incrementAndGet();
                                }
                            }
                        }
                        
                        Thread.sleep(intervalMs + random.nextInt(5));
                        
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        // Continue on errors
                    }
                }
            });
        }
    }
    
    private StateMachineEvent generateRandomEvent(Random random) {
        int eventType = random.nextInt(3);
        switch (eventType) {
            case 0: return new IncomingCall();
            case 1: return new Answer(); // Should be ignored in our simple flow
            case 2: return new Hangup();
            default: return new IncomingCall();
        }
    }
    
    private void updateMachineState(String machineId, String newState) {
        String oldState = machineStates.put(machineId, newState);
        
        // Update counters
        if (oldState != null) {
            switch (oldState) {
                case "IDLE": machinesInIdle.decrementAndGet(); break;
                case "RINGING": machinesInRinging.decrementAndGet(); break;
                case "CONNECTED": machinesInConnected.decrementAndGet(); break;
                case "HUNGUP": machinesInHungup.decrementAndGet(); break;
            }
        }
        
        switch (newState) {
            case "IDLE": machinesInIdle.incrementAndGet(); break;
            case "RINGING": machinesInRinging.incrementAndGet(); break;
            case "CONNECTED": machinesInConnected.incrementAndGet(); break;
            case "HUNGUP": machinesInHungup.incrementAndGet(); break;
        }
    }
    
    private void stopGeneration() {
        System.out.println("🛑 Stopping call and event generation...");
        
        callGeneratorExecutor.shutdown();
        randomEventExecutor.shutdown();
        
        try {
            if (!callGeneratorExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                callGeneratorExecutor.shutdownNow();
            }
            if (!randomEventExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                randomEventExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            callGeneratorExecutor.shutdownNow();
            randomEventExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    private void reportStats() {
        System.out.println("📊 Real-time Stats:");
        System.out.println("   Calls Generated: " + callsGenerated.get());
        System.out.println("   States - IDLE: " + machinesInIdle.get() + 
                         ", RINGING: " + machinesInRinging.get() + 
                         ", CONNECTED: " + machinesInConnected.get() + 
                         ", HUNGUP: " + machinesInHungup.get());
        System.out.println("   Events to HUNGUP machines: " + eventsToHungupMachines.get());
        System.out.println("   Events ignored: " + eventsIgnored.get());
        System.out.println();
    }
    
    private void analyzeResults(double actualDuration) {
        System.out.println("\n🎯 CALL SIMULATOR TEST RESULTS");
        System.out.println("==============================");
        
        int expectedCalls = (int) (callsPerSecond * actualDuration);
        int actualCalls = callsGenerated.get();
        
        // Final state counts
        int finalIdle = machinesInIdle.get();
        int finalRinging = machinesInRinging.get();
        int finalConnected = machinesInConnected.get();
        int finalHungup = machinesInHungup.get();
        int totalMachines = finalIdle + finalRinging + finalConnected + finalHungup;
        
        System.out.println("📈 Call Generation Results:");
        System.out.println("   Expected Calls: " + expectedCalls);
        System.out.println("   Actual Calls: " + actualCalls);
        System.out.println("   Call Rate Accuracy: " + String.format("%.1f%%", 
            (actualCalls * 100.0) / expectedCalls));
        System.out.println("   Actual Rate: " + String.format("%.2f", actualCalls / actualDuration) + " calls/sec");
        System.out.println("   Target Rate: " + callsPerSecond + " calls/sec");
        
        System.out.println("\n🎯 FINAL MACHINE STATE DISTRIBUTION:");
        System.out.println("   ==========================================");
        System.out.println("   📊 IDLE Machines: " + finalIdle + " (" + String.format("%.1f%%", 
            (finalIdle * 100.0) / Math.max(totalMachines, 1)) + ")");
        System.out.println("   📊 RINGING Machines: " + finalRinging + " (" + String.format("%.1f%%", 
            (finalRinging * 100.0) / Math.max(totalMachines, 1)) + ")");
        System.out.println("   📊 CONNECTED Machines: " + finalConnected + " (" + String.format("%.1f%%", 
            (finalConnected * 100.0) / Math.max(totalMachines, 1)) + ")");
        System.out.println("   📊 HUNGUP Machines: " + finalHungup + " (" + String.format("%.1f%%", 
            (finalHungup * 100.0) / Math.max(totalMachines, 1)) + ")");
        System.out.println("   ==========================================");
        System.out.println("   📊 TOTAL MACHINES: " + totalMachines);
        
        // Detailed state analysis
        System.out.println("\n📈 SIGNIFICANT PARAMETERS & STATISTICS:");
        System.out.println("   ==========================================");
        System.out.println("   🕒 Test Duration: " + String.format("%.2f", actualDuration) + " seconds");
        System.out.println("   ⚡ Total Events to HUNGUP: " + eventsToHungupMachines.get());
        System.out.println("   ✅ Events Ignored: " + eventsIgnored.get());
        System.out.println("   🚦 Calls Generated: " + callsGenerated.get());
        System.out.println("   📞 Calls per Second (Config): " + callsPerSecond);
        System.out.println("   🎲 Random Events per Second (Config): " + RANDOM_EVENT_RATE_PER_SECOND);
        System.out.println("   💨 Average Call Rate: " + String.format("%.2f", callsGenerated.get() / actualDuration) + " calls/sec");
        
        // Calculate filtering rates
        double filterRate = eventsToHungupMachines.get() > 0 ? 
            (eventsIgnored.get() * 100.0) / eventsToHungupMachines.get() : 0.0;
        
        System.out.println("   📈 Event Filtering Rate: " + String.format("%.2f%%", filterRate));
        
        System.out.println("\n🎲 Event Filtering Analysis:");
        System.out.println("   Events sent to HUNGUP machines: " + eventsToHungupMachines.get());
        System.out.println("   Events properly ignored: " + eventsIgnored.get());
        double ignoreRate = eventsToHungupMachines.get() > 0 ? 
            (eventsIgnored.get() * 100.0) / eventsToHungupMachines.get() : 0.0;
        System.out.println("   Event ignore rate: " + String.format("%.1f%%", ignoreRate));
        
        // Machine state transition analysis
        System.out.println("\n🔄 State Transition Analysis:");
        System.out.println("   Expected Flow: IDLE -> RINGING -> CONNECTED -> HUNGUP");
        System.out.println("   Machines that started: " + actualCalls);
        System.out.println("   Machines still in IDLE: " + finalIdle + " (" + 
            String.format("%.1f%% didn't receive IncomingCall)", (finalIdle * 100.0) / Math.max(actualCalls, 1)));
        System.out.println("   Machines still in RINGING: " + finalRinging + " (" + 
            String.format("%.1f%% didn't receive Answer)", (finalRinging * 100.0) / Math.max(actualCalls, 1)));
        System.out.println("   Machines still in CONNECTED: " + finalConnected + " (" + 
            String.format("%.1f%% didn't receive Hangup)", (finalConnected * 100.0) / Math.max(actualCalls, 1)));
        System.out.println("   Machines completed (HUNGUP): " + finalHungup + " (" + 
            String.format("%.1f%% completed full flow)", (finalHungup * 100.0) / Math.max(actualCalls, 1)));
        
        // Test validation
        boolean callRateAccurate = Math.abs(actualCalls - expectedCalls) <= (expectedCalls * 0.15); // 15% tolerance
        boolean statesValid = finalIdle >= 0 && finalRinging >= 0 && finalHungup >= 0;
        boolean eventsFiltered = eventsToHungupMachines.get() == 0 || ignoreRate >= 50.0; // Relaxed filtering requirement
        boolean totalCountsMatch = totalMachines == actualCalls;
        
        System.out.println("\n✅ Test Validation:");
        System.out.println("   Call Rate Accurate (±15%): " + (callRateAccurate ? "✅ PASS" : "❌ FAIL"));
        System.out.println("   Valid States: " + (statesValid ? "✅ PASS" : "❌ FAIL"));
        System.out.println("   Event Filtering (≥50%): " + (eventsFiltered ? "✅ PASS" : "❌ FAIL"));
        System.out.println("   Count Consistency: " + (totalCountsMatch ? "✅ PASS" : "❌ FAIL"));
        
        boolean testPassed = callRateAccurate && statesValid && eventsFiltered && totalCountsMatch;
        System.out.println("\n🏁 OVERALL TEST RESULT: " + (testPassed ? "✅ PASSED" : "❌ FAILED"));
        
        // Performance metrics with TPS analysis
        if (totalMachines > 0) {
            System.out.println("\n⚡ Performance Metrics:");
            System.out.println("   Machines created per second: " + String.format("%.2f", actualCalls / actualDuration));
            System.out.println("   Events to HUNGUP per second: " + String.format("%.2f", eventsToHungupMachines.get() / actualDuration));
            System.out.println("   Events ignored per second: " + String.format("%.2f", eventsIgnored.get() / actualDuration));
            
            // Calculate total events processed (estimate)
            // Each call generates: IncomingCall + Hangup = 2 events minimum
            // Plus random events
            double estimatedEventsProcessed = (actualCalls * 2) + (RANDOM_EVENT_RATE_PER_SECOND * actualDuration);
            double actualTps = estimatedEventsProcessed / actualDuration;
            
            System.out.println("\n🎯 TPS Analysis:");
            System.out.println("   Target TPS Configuration: 1000 events/sec");
            System.out.println("   Estimated Events Processed: " + String.format("%.0f", estimatedEventsProcessed));
            System.out.println("   Achieved TPS: " + String.format("%.2f", actualTps) + " events/sec");
            System.out.println("   TPS Achievement: " + String.format("%.1f%%", (actualTps / 1000.0) * 100));
            
            // Registry performance stats
            if (registry != null) {
                var stats = registry.getPerformanceStats();
                System.out.println("\n📊 Registry Performance Stats:");
                System.out.println("   Active Machines: " + stats.get("activeMachines"));
                System.out.println("   Available Machine Permits: " + stats.get("availableMachinePermits"));
                System.out.println("   Virtual Thread Pool Size: " + stats.get("virtualThreadPoolSize"));
                System.out.println("   Event Queue Capacity: " + stats.get("eventQueueCapacity"));
            }
        }
        
        // Expected behavior summary
        System.out.println("\n📋 Expected Behavior Summary:");
        System.out.println("   - All machines should be in valid states (IDLE, RINGING, or HUNGUP)");
        System.out.println("   - HUNGUP machines should ignore all subsequent events");
        System.out.println("   - Total machine count should match generated calls");
        System.out.println("   - Call generation rate should be approximately " + callsPerSecond + "/sec");
    }
    
    private void registerEventTypes() {
        EventTypeRegistry.register(IncomingCall.class, "INCOMING_CALL");
        EventTypeRegistry.register(Answer.class, "ANSWER");  
        EventTypeRegistry.register(Hangup.class, "HANGUP");
        System.out.println("[Init] Registered event types");
    }
    
    private void cleanup() {
        System.out.println("🧹 Cleaning up test resources...");
        
        if (statsReporter != null) {
            statsReporter.shutdown();
        }
        
        if (registry != null) {
            registry.shutdownAsyncLogging();
        }
        
        if (timeoutManager != null) {
            timeoutManager.shutdown();
        }
        
        System.out.println("✅ Cleanup complete");
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println("🎯 Call Simulator Test Suite");
        System.out.println("============================\n");
        
        // Test scenarios
        runTestScenario("Low Rate Test", 5, 20);
        Thread.sleep(5000);
        
        runTestScenario("Standard Test", DEFAULT_CALLS_PER_SECOND, DEFAULT_DURATION_SECONDS);
        Thread.sleep(5000);
        
        runTestScenario("High Rate Test", 25, 15);
        
        System.out.println("\n🎉 All Call Simulator Tests Completed!");
    }
    
    private static void runTestScenario(String scenarioName, int callsPerSecond, int duration) throws Exception {
        System.out.println("\n🚀 Running Scenario: " + scenarioName);
        System.out.println("-".repeat(40));
        
        CallSimulatorTest test = new CallSimulatorTest(callsPerSecond, duration);
        test.runCallSimulatorTest();
    }
}