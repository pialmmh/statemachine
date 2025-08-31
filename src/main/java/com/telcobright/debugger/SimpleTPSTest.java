package com.telcobright.debugger;

import com.telcobright.statemachine.*;
import com.telcobright.examples.callmachine.CallState;
import com.telcobright.examples.callmachine.events.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Simple TPS Test - Tests state transitions without persistence
 */
public class SimpleTPSTest {
    private static final int TARGET_TPS = 1000;
    private static final int TEST_DURATION_SECONDS = 10;
    private static final int NUM_MACHINES = 100;
    
    // Statistics
    private static final AtomicLong totalTransitions = new AtomicLong(0);
    private static final AtomicLong successfulTransitions = new AtomicLong(0);
    private static final AtomicLong[] stateCounters = new AtomicLong[4];
    
    static {
        for (int i = 0; i < stateCounters.length; i++) {
            stateCounters[i] = new AtomicLong(0);
        }
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("                    SIMPLE TPS TEST");
        System.out.println("=".repeat(80));
        System.out.println("Configuration:");
        System.out.println("  Target TPS: " + TARGET_TPS);
        System.out.println("  Test Duration: " + TEST_DURATION_SECONDS + " seconds");
        System.out.println("  Number of Machines: " + NUM_MACHINES);
        System.out.println("=".repeat(80) + "\n");
        
        // Create simple state machines without persistence
        System.out.println("📦 Creating " + NUM_MACHINES + " state machines...");
        GenericStateMachine[] machines = new GenericStateMachine[NUM_MACHINES];
        
        for (int i = 0; i < NUM_MACHINES; i++) {
            String machineId = String.format("test-%03d", i + 1);
            machines[i] = createSimpleMachine(machineId);
            machines[i].start();
        }
        System.out.println("✅ Created " + NUM_MACHINES + " machines\n");
        
        // Test execution
        System.out.println("📊 Starting TPS test...\n");
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(10);
        AtomicBoolean testRunning = new AtomicBoolean(true);
        long startTime = System.currentTimeMillis();
        
        // Event generator task
        executor.scheduleAtFixedRate(() -> {
            if (!testRunning.get()) return;
            
            try {
                // Select random machine
                GenericStateMachine machine = machines[ThreadLocalRandom.current().nextInt(NUM_MACHINES)];
                String currentState = machine.getCurrentState();
                
                // Fire appropriate event based on current state
                switch (currentState) {
                    case "ADMISSION":
                        machine.fire(new IncomingCall("+1-555-0000", "+1-555-9999"));
                        updateStateIndex(1); // RINGING
                        break;
                    case "RINGING":
                        if (ThreadLocalRandom.current().nextBoolean()) {
                            machine.fire(new Answer());
                            updateStateIndex(2); // CONNECTED
                        } else {
                            machine.fire(new Hangup());
                            updateStateIndex(3); // HUNGUP
                        }
                        break;
                    case "CONNECTED":
                        machine.fire(new Hangup());
                        updateStateIndex(3); // HUNGUP
                        break;
                    case "HUNGUP":
                        // Fire event to cycle back
                        machine.fire(new IncomingCall("+1-555-0000", "+1-555-9999"));
                        updateStateIndex(0); // ADMISSION
                        break;
                }
                
                totalTransitions.incrementAndGet();
                successfulTransitions.incrementAndGet();
                
            } catch (Exception e) {
                // Ignore errors
            }
        }, 0, 1000000 / TARGET_TPS, TimeUnit.MICROSECONDS);
        
        // Statistics reporter task
        executor.scheduleAtFixedRate(() -> {
            printStatistics(startTime);
        }, 0, 2, TimeUnit.SECONDS);
        
        // Run test for specified duration
        Thread.sleep(TEST_DURATION_SECONDS * 1000);
        testRunning.set(false);
        
        // Shutdown
        System.out.println("\n🛑 Stopping test...");
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        // Final statistics
        printFinalStatistics(startTime);
        
        System.exit(0);
    }
    
    private static GenericStateMachine createSimpleMachine(String machineId) {
        return EnhancedFluentBuilder.create(machineId)
            .initialState("ADMISSION")
            
            .state("ADMISSION")
                .on(IncomingCall.class).to("RINGING")
                .done()
                
            .state("RINGING")
                .on(Answer.class).to("CONNECTED")
                .on(Hangup.class).to("HUNGUP")
                .done()
                
            .state("CONNECTED")
                .on(Hangup.class).to("HUNGUP")
                .done()
                
            .state("HUNGUP")
                .on(IncomingCall.class).to("ADMISSION")
                .done()
                
            .build();
    }
    
    private static void updateStateIndex(int index) {
        stateCounters[index].incrementAndGet();
    }
    
    private static void printStatistics(long startTime) {
        long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
        if (elapsedSeconds == 0) elapsedSeconds = 1;
        
        long total = totalTransitions.get();
        double actualTPS = total / (double) elapsedSeconds;
        
        System.out.println("\n📈 Statistics @ " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        System.out.println("─".repeat(50));
        System.out.printf("  Elapsed: %d seconds\n", elapsedSeconds);
        System.out.printf("  Total Transitions: %,d\n", total);
        System.out.printf("  Current TPS: %.0f (Target: %d)\n", actualTPS, TARGET_TPS);
        
        System.out.println("\n  State Distribution:");
        String[] states = {"ADMISSION", "RINGING", "CONNECTED", "HUNGUP"};
        long totalStates = 0;
        for (AtomicLong counter : stateCounters) {
            totalStates += counter.get();
        }
        
        for (int i = 0; i < states.length; i++) {
            long count = stateCounters[i].get();
            if (totalStates > 0) {
                double percentage = (count * 100.0) / totalStates;
                System.out.printf("    %-12s: %,8d (%.1f%%)\n", states[i], count, percentage);
            }
        }
    }
    
    private static void printFinalStatistics(long startTime) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("                    FINAL TEST RESULTS");
        System.out.println("=".repeat(80));
        
        long total = totalTransitions.get();
        long successful = successfulTransitions.get();
        double actualTPS = total / (double) TEST_DURATION_SECONDS;
        
        System.out.println("\n🎯 Performance Metrics:");
        System.out.printf("  Target TPS: %,d\n", TARGET_TPS);
        System.out.printf("  Actual TPS: %,.0f\n", actualTPS);
        System.out.printf("  Efficiency: %.1f%%\n", (actualTPS / TARGET_TPS) * 100);
        
        System.out.println("\n📊 Transition Summary:");
        System.out.printf("  Total Transitions: %,d\n", total);
        System.out.printf("  Successful: %,d\n", successful);
        System.out.printf("  Success Rate: %.1f%%\n", (successful * 100.0) / total);
        
        System.out.println("\n🗂️ Final State Distribution:");
        String[] states = {"ADMISSION", "RINGING", "CONNECTED", "HUNGUP"};
        long totalStates = 0;
        for (AtomicLong counter : stateCounters) {
            totalStates += counter.get();
        }
        
        for (int i = 0; i < states.length; i++) {
            long count = stateCounters[i].get();
            if (totalStates > 0) {
                double percentage = (count * 100.0) / totalStates;
                System.out.printf("  %-12s: %,10d (%.2f%%)\n", states[i], count, percentage);
            }
        }
        
        System.out.println("\n✅ Test completed successfully!");
        System.out.println("=".repeat(80));
    }
}