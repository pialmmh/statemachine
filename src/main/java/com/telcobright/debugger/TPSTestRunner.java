package com.telcobright.debugger;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.timeout.TimeoutManager;
import com.telcobright.examples.callmachine.CallState;
import com.telcobright.examples.callmachine.events.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * TPS Test Runner - Tests state machine throughput and collects statistics
 */
public class TPSTestRunner {
    private static final int TARGET_TPS = 1000;
    private static final int TEST_DURATION_SECONDS = 10;
    private static final int WARMUP_SECONDS = 2;
    private static final int NUM_MACHINES = 100;
    
    // Statistics
    private static final AtomicLong totalTransitions = new AtomicLong(0);
    private static final AtomicLong successfulTransitions = new AtomicLong(0);
    private static final AtomicLong failedTransitions = new AtomicLong(0);
    private static final Map<String, AtomicLong> stateCount = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> eventCount = new ConcurrentHashMap<>();
    
    static {
        // Initialize state counters
        for (CallState state : CallState.values()) {
            stateCount.put(state.name(), new AtomicLong(0));
        }
        // Initialize event counters
        eventCount.put("INCOMING_CALL", new AtomicLong(0));
        eventCount.put("ANSWER", new AtomicLong(0));
        eventCount.put("HANGUP", new AtomicLong(0));
        eventCount.put("SESSION_PROGRESS", new AtomicLong(0));
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("                    TPS PERFORMANCE TEST");
        System.out.println("=".repeat(80));
        System.out.println("Configuration:");
        System.out.println("  Target TPS: " + TARGET_TPS);
        System.out.println("  Test Duration: " + TEST_DURATION_SECONDS + " seconds");
        System.out.println("  Warmup Period: " + WARMUP_SECONDS + " seconds");
        System.out.println("  Number of Machines: " + NUM_MACHINES);
        System.out.println("=".repeat(80) + "\n");
        
        // Create registry and timeout manager
        TimeoutManager timeoutManager = new TimeoutManager();
        StateMachineRegistry registry = new StateMachineRegistry("call", timeoutManager, 9999);
        
        // Create state machines
        System.out.println("📦 Creating " + NUM_MACHINES + " state machines...");
        Map<String, GenericStateMachine<CallMachineRunnerEnhanced.CallPersistentContext, CallMachineRunnerEnhanced.CallVolatileContext>> machines = new HashMap<>();
        
        for (int i = 1; i <= NUM_MACHINES; i++) {
            String machineId = String.format("tps-test-%03d", i);
            GenericStateMachine<CallMachineRunnerEnhanced.CallPersistentContext, CallMachineRunnerEnhanced.CallVolatileContext> machine = createTestMachine(machineId);
            
            // Set up context
            CallMachineRunnerEnhanced.CallPersistentContext context = 
                new CallMachineRunnerEnhanced.CallPersistentContext(machineId, "+1-555-" + String.format("%04d", i), "+1-555-9999");
            CallMachineRunnerEnhanced.CallVolatileContext volatileContext = 
                new CallMachineRunnerEnhanced.CallVolatileContext();
            
            machine.setPersistingEntity(context);
            machine.setContext(volatileContext);
            
            registry.register(machineId, machine);
            machine.start();
            machines.put(machineId, machine);
            
            updateStateCount(machine.getCurrentState());
        }
        System.out.println("✅ Created " + NUM_MACHINES + " machines\n");
        
        // Warmup phase
        System.out.println("🔥 Warming up for " + WARMUP_SECONDS + " seconds...");
        Thread.sleep(WARMUP_SECONDS * 1000);
        
        // Test execution
        System.out.println("\n📊 Starting TPS test...\n");
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(10);
        AtomicBoolean testRunning = new AtomicBoolean(true);
        
        // Event generator task
        executor.scheduleAtFixedRate(() -> {
            if (!testRunning.get()) return;
            
            try {
                // Select random machine
                String machineId = String.format("tps-test-%03d", ThreadLocalRandom.current().nextInt(1, NUM_MACHINES + 1));
                GenericStateMachine machine = machines.get(machineId);
                if (machine == null) return;
                
                String currentState = machine.getCurrentState();
                String previousState = currentState;
                
                // Fire appropriate event based on current state
                switch (currentState) {
                    case "ADMISSION":
                        machine.fire(new IncomingCall("+1-555-0000", "+1-555-9999"));
                        eventCount.get("INCOMING_CALL").incrementAndGet();
                        break;
                    case "RINGING":
                        if (ThreadLocalRandom.current().nextBoolean()) {
                            machine.fire(new Answer());
                            eventCount.get("ANSWER").incrementAndGet();
                        } else {
                            machine.fire(new SessionProgress("Ring " + System.currentTimeMillis()));
                            eventCount.get("SESSION_PROGRESS").incrementAndGet();
                        }
                        break;
                    case "CONNECTED":
                        machine.fire(new Hangup());
                        eventCount.get("HANGUP").incrementAndGet();
                        break;
                    case "HUNGUP":
                        // Reset to ADMISSION for next cycle
                        machine.fire(new IncomingCall("+1-555-0000", "+1-555-9999"));
                        eventCount.get("INCOMING_CALL").incrementAndGet();
                        break;
                }
                
                totalTransitions.incrementAndGet();
                
                // Update state counts
                String newState = machine.getCurrentState();
                if (!previousState.equals(newState)) {
                    successfulTransitions.incrementAndGet();
                    updateStateCount(newState);
                }
                
            } catch (Exception e) {
                failedTransitions.incrementAndGet();
            }
        }, 0, 1000000 / TARGET_TPS, TimeUnit.MICROSECONDS);
        
        // Statistics reporter task
        executor.scheduleAtFixedRate(() -> {
            printStatistics();
        }, 0, 5, TimeUnit.SECONDS);
        
        // Run test for specified duration
        Thread.sleep(TEST_DURATION_SECONDS * 1000);
        testRunning.set(false);
        
        // Shutdown
        System.out.println("\n🛑 Stopping test...");
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        // Final statistics
        System.out.println("\n" + "=".repeat(80));
        System.out.println("                    FINAL TEST RESULTS");
        System.out.println("=".repeat(80));
        printFinalStatistics();
        
        // Cleanup
        registry.shutdown();
        System.exit(0);
    }
    
    private static GenericStateMachine<CallMachineRunnerEnhanced.CallPersistentContext, CallMachineRunnerEnhanced.CallVolatileContext> 
            createTestMachine(String machineId) {
        return EnhancedFluentBuilder.<CallMachineRunnerEnhanced.CallPersistentContext, CallMachineRunnerEnhanced.CallVolatileContext>create(machineId)
            .initialState(CallState.ADMISSION.name())
            
            .state(CallState.ADMISSION.name())
                .on(IncomingCall.class).to(CallState.RINGING.name())
                .on(Hangup.class).to(CallState.HUNGUP.name())
                .done()
                
            .state(CallState.RINGING.name())
                .on(Answer.class).to(CallState.CONNECTED.name())
                .on(Hangup.class).to(CallState.HUNGUP.name())
                .stay(SessionProgress.class, (m, e) -> {
                    // Handle session progress
                })
                .done()
                
            .state(CallState.CONNECTED.name())
                .on(Hangup.class).to(CallState.HUNGUP.name())
                .done()
                
            .state(CallState.HUNGUP.name())
                .on(IncomingCall.class).to(CallState.ADMISSION.name())
                .done()
                
            .build();
    }
    
    private static void updateStateCount(String state) {
        stateCount.computeIfAbsent(state, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    private static void printStatistics() {
        long total = totalTransitions.get();
        long successful = successfulTransitions.get();
        long failed = failedTransitions.get();
        
        System.out.println("\n📈 Current Statistics @ " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        System.out.println("─".repeat(50));
        System.out.println("Transitions:");
        System.out.println("  Total: " + total);
        System.out.println("  Successful: " + successful);
        System.out.println("  Failed: " + failed);
        System.out.println("  Success Rate: " + (total > 0 ? String.format("%.2f%%", (successful * 100.0) / total) : "N/A"));
        
        System.out.println("\nState Distribution:");
        long totalStates = stateCount.values().stream().mapToLong(AtomicLong::get).sum();
        stateCount.forEach((state, count) -> {
            long c = count.get();
            if (c > 0) {
                double percentage = (c * 100.0) / totalStates;
                System.out.printf("  %-12s: %6d (%.1f%%)\n", state, c, percentage);
            }
        });
        
        System.out.println("\nEvent Counts:");
        eventCount.forEach((event, count) -> {
            long c = count.get();
            if (c > 0) {
                System.out.printf("  %-16s: %6d\n", event, c);
            }
        });
    }
    
    private static void printFinalStatistics() {
        long total = totalTransitions.get();
        long successful = successfulTransitions.get();
        long failed = failedTransitions.get();
        double actualTPS = total / (double) TEST_DURATION_SECONDS;
        
        System.out.println("\n🎯 Performance Metrics:");
        System.out.println("  Target TPS: " + TARGET_TPS);
        System.out.println("  Actual TPS: " + String.format("%.2f", actualTPS));
        System.out.println("  Efficiency: " + String.format("%.2f%%", (actualTPS / TARGET_TPS) * 100));
        
        System.out.println("\n📊 Transition Summary:");
        System.out.println("  Total Transitions: " + total);
        System.out.println("  Successful: " + successful);
        System.out.println("  Failed: " + failed);
        System.out.println("  Success Rate: " + String.format("%.2f%%", (successful * 100.0) / total));
        
        System.out.println("\n🗂️ Final State Distribution:");
        long totalStates = stateCount.values().stream().mapToLong(AtomicLong::get).sum();
        stateCount.forEach((state, count) -> {
            long c = count.get();
            if (c > 0) {
                double percentage = (c * 100.0) / totalStates;
                System.out.printf("  %-12s: %8d (%.2f%%)\n", state, c, percentage);
            }
        });
        
        System.out.println("\n📨 Event Statistics:");
        long totalEvents = eventCount.values().stream().mapToLong(AtomicLong::get).sum();
        eventCount.forEach((event, count) -> {
            long c = count.get();
            if (c > 0) {
                double percentage = (c * 100.0) / totalEvents;
                System.out.printf("  %-16s: %8d (%.2f%%)\n", event, c, percentage);
            }
        });
        
        System.out.println("\n✅ Test completed successfully!");
        System.out.println("=".repeat(80));
    }
}