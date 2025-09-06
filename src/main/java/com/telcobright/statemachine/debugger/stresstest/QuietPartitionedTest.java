package com.telcobright.statemachine.debugger.stresstest;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.timeout.TimeoutManager;
import com.telcobright.statemachine.db.MockPartitionedRepository;
import com.telcobright.examples.callmachine.events.*;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Quiet version of partitioned-repo test with suppressed debug output
 */
public class QuietPartitionedTest {
    
    private static final int TOTAL_MACHINES = 10_000;  // Reduced for faster demo
    private static final int BATCH_SIZE = 1_000;
    private static final PrintStream originalOut = System.out;
    private static final PrintStream originalErr = System.err;
    private static final PrintStream nullStream = new PrintStream(new java.io.OutputStream() {
        public void write(int b) {}
    });
    
    public static void main(String[] args) throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   PARTITIONED-REPO TEST (1 MILLION MACHINES) - QUIET MODE");
        System.out.println("=".repeat(80));
        System.out.println();
        
        runTest();
    }
    
    private static void suppressOutput() {
        System.setOut(nullStream);
        System.setErr(nullStream);
    }
    
    private static void restoreOutput() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }
    
    private static void runTest() throws Exception {
        // Setup - suppress debug output during initialization
        suppressOutput();
        TimeoutManager timeoutManager = new TimeoutManager();
        StateMachineRegistry registry = new StateMachineRegistry("partition-test", timeoutManager, 9998);
        restoreOutput();
        
        // Create mock repository
        MockPartitionedRepository<SimpleCallEntity, String> repository = 
            new MockPartitionedRepository<>("call_states");
        
        System.out.println("📋 Phase 1: Creating " + String.format("%,d", TOTAL_MACHINES) + " machines");
        System.out.println("-".repeat(50));
        
        long startTime = System.currentTimeMillis();
        Map<String, AtomicInteger> stateCount = new HashMap<>();
        stateCount.put("IDLE", new AtomicInteger(0));
        stateCount.put("RINGING", new AtomicInteger(0));
        stateCount.put("CONNECTED", new AtomicInteger(0));
        
        // Create machines in batches
        for (int batch = 0; batch < TOTAL_MACHINES / BATCH_SIZE; batch++) {
            int batchStart = batch * BATCH_SIZE;
            int batchEnd = Math.min(batchStart + BATCH_SIZE, TOTAL_MACHINES);
            
            System.out.print("Batch " + (batch + 1) + " [" + 
                String.format("%,d", batchStart) + "-" + String.format("%,d", batchEnd) + "]... ");
            
            suppressOutput();
            for (int i = batchStart; i < batchEnd; i++) {
                String machineId = "call-" + i;
                
                // Create entity
                SimpleCallEntity entity = new SimpleCallEntity(machineId);
                entity.setCallerId("+1-555-" + (i % 10000));
                entity.setCalleeId("+1-555-" + ((i + 1) % 10000));
                
                // Create simple state machine
                GenericStateMachine<SimpleCallEntity, Object> machine = 
                    EnhancedFluentBuilder.<SimpleCallEntity, Object>create(machineId)
                        .initialState("IDLE")
                        .state("IDLE")
                            .on(IncomingCall.class).to("RINGING")
                        .done()
                        .state("RINGING")
                            .on(Answer.class).to("CONNECTED")
                            .on(Hangup.class).to("IDLE")
                        .done()
                        .state("CONNECTED")
                            .on(Hangup.class).to("IDLE")
                        .done()
                        .build();
                
                machine.setPersistingEntity(entity);
                registry.register(machineId, machine);
                
                // Persist to mock repository
                repository.insert(entity);
                stateCount.get("IDLE").incrementAndGet();
            }
            restoreOutput();
            
            System.out.println("✓");
            
            // Remove some machines from memory to simulate eviction
            if (batch > 0 && batch % 2 == 0) {
                System.out.print("  Evicting old machines from memory... ");
                suppressOutput();
                int evictStart = Math.max(0, batchStart - 100000);
                int evictEnd = Math.max(0, batchStart - 50000);
                for (int i = evictStart; i < evictEnd; i++) {
                    registry.removeMachine("call-" + i);
                }
                restoreOutput();
                System.out.println("✓");
            }
        }
        
        long creationTime = System.currentTimeMillis() - startTime;
        System.out.println("\n✅ Created " + String.format("%,d", TOTAL_MACHINES) + 
            " machines in " + String.format("%.2f", creationTime/1000.0) + " seconds");
        System.out.println("   Rate: " + String.format("%,.0f", TOTAL_MACHINES/(creationTime/1000.0)) + " machines/sec");
        
        // Print repository statistics
        repository.printStatistics();
        
        // Phase 2: Send events and test rehydration
        System.out.println("\n📋 Phase 2: Sending Events (Testing Rehydration)");
        System.out.println("-".repeat(50));
        
        Random random = new Random();
        int eventsToSend = 100_000;
        int rehydrationCount = 0;
        
        suppressOutput();
        for (int i = 0; i < eventsToSend; i++) {
            int machineIndex = random.nextInt(TOTAL_MACHINES);
            String machineId = "call-" + machineIndex;
            
            // Check if machine is in memory
            boolean wasInMemory = registry.isInMemory(machineId);
            
            // Send event
            boolean sent = false;
            int eventType = random.nextInt(3);
            switch (eventType) {
                case 0:
                    sent = registry.routeEvent(machineId, 
                        new IncomingCall("+1-555-0001", "+1-555-0002"));
                    break;
                case 1:
                    sent = registry.routeEvent(machineId, new Answer());
                    break;
                case 2:
                    sent = registry.routeEvent(machineId, new Hangup());
                    break;
            }
            
            if (sent && !wasInMemory) {
                rehydrationCount++;
            }
            
            if (i % 10000 == 0 && i > 0) {
                restoreOutput();
                System.out.println("  Sent " + String.format("%,d", i) + " events, " +
                    "Rehydrations: " + rehydrationCount);
                suppressOutput();
            }
        }
        restoreOutput();
        
        System.out.println("\n✅ Event Processing Complete:");
        System.out.println("   Total events sent: " + String.format("%,d", eventsToSend));
        System.out.println("   Machines rehydrated: " + rehydrationCount);
        
        // Phase 3: Test targeted rehydration
        System.out.println("\n📋 Phase 3: Targeted Rehydration Test");
        System.out.println("-".repeat(50));
        
        // Clear all from memory
        System.out.print("Clearing all machines from memory... ");
        suppressOutput();
        for (int i = 0; i < TOTAL_MACHINES; i++) {
            registry.removeMachine("call-" + i);
        }
        restoreOutput();
        System.out.println("✓");
        
        // Rehydrate specific machines
        int rehydrateCount = 10_000;
        System.out.println("Rehydrating " + String.format("%,d", rehydrateCount) + " random machines...");
        
        long rehydrateStart = System.currentTimeMillis();
        int successful = 0;
        
        suppressOutput();
        for (int i = 0; i < rehydrateCount; i++) {
            int machineIndex = random.nextInt(TOTAL_MACHINES);
            String machineId = "call-" + machineIndex;
            
            // Simulate rehydration from repository
            SimpleCallEntity entity = repository.findById(machineId);
            if (entity != null) {
                // Create machine from persisted state
                GenericStateMachine<SimpleCallEntity, Object> machine = 
                    createMachine(machineId);
                machine.setPersistingEntity(entity);
                registry.register(machineId, machine);
                successful++;
            }
            
            if (i % 1000 == 0 && i > 0) {
                restoreOutput();
                System.out.print(".");
                suppressOutput();
            }
        }
        restoreOutput();
        
        long rehydrateTime = System.currentTimeMillis() - rehydrateStart;
        System.out.println("\n✅ Rehydration complete:");
        System.out.println("   Successful: " + successful + "/" + rehydrateCount);
        System.out.println("   Time: " + String.format("%.2f", rehydrateTime/1000.0) + " seconds");
        System.out.println("   Rate: " + String.format("%,.0f", rehydrateCount/(rehydrateTime/1000.0)) + " machines/sec");
        
        // Final statistics
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   FINAL STATISTICS");
        System.out.println("=".repeat(80));
        
        repository.printStatistics();
        
        System.out.println("\n📊 State Distribution (Approximate):");
        System.out.println("-".repeat(50));
        System.out.println(String.format("%-15s | %-10s", "State", "Count"));
        System.out.println("-".repeat(50));
        for (Map.Entry<String, AtomicInteger> entry : stateCount.entrySet()) {
            System.out.println(String.format("%-15s | %,10d", 
                entry.getKey(), entry.getValue().get()));
        }
        
        System.out.println("\n📊 Performance Summary:");
        System.out.println("-".repeat(50));
        System.out.println("Total Machines: " + String.format("%,d", TOTAL_MACHINES));
        System.out.println("Creation Time: " + String.format("%.2f seconds", creationTime/1000.0));
        System.out.println("Creation Rate: " + String.format("%,.0f machines/sec", TOTAL_MACHINES/(creationTime/1000.0)));
        System.out.println("Events Sent: " + String.format("%,d", eventsToSend));
        System.out.println("Rehydration Rate: " + String.format("%,.0f machines/sec", rehydrateCount/(rehydrateTime/1000.0)));
        
        // Cleanup
        suppressOutput();
        registry.shutdown();
        timeoutManager.shutdown();
        restoreOutput();
        
        System.out.println("\n✅ Test Complete!");
    }
    
    private static GenericStateMachine<SimpleCallEntity, Object> createMachine(String machineId) {
        return EnhancedFluentBuilder.<SimpleCallEntity, Object>create(machineId)
            .initialState("IDLE")
            .state("IDLE")
                .on(IncomingCall.class).to("RINGING")
            .done()
            .state("RINGING")
                .on(Answer.class).to("CONNECTED")
                .on(Hangup.class).to("IDLE")
            .done()
            .state("CONNECTED")
                .on(Hangup.class).to("IDLE")
            .done()
            .build();
    }
}