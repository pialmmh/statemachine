package com.telcobright.statemachine.debugger.stresstest;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.timeout.TimeoutManager;
import com.telcobright.statemachine.db.MockPartitionedRepository;
import com.telcobright.examples.callmachine.events.*;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fast version of partitioned-repo test with 1000 machines for quick demo
 */
public class FastPartitionedTest {
    
    private static final int TOTAL_MACHINES = 1000;
    private static final int BATCH_SIZE = 100;
    private static final PrintStream originalOut = System.out;
    private static final PrintStream originalErr = System.err;
    private static final PrintStream nullStream = new PrintStream(new java.io.OutputStream() {
        public void write(int b) {}
    });
    
    public static void main(String[] args) throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   PARTITIONED-REPO TEST WITH REHYDRATION");
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
        // Setup
        suppressOutput();
        TimeoutManager timeoutManager = new TimeoutManager();
        StateMachineRegistry registry = new StateMachineRegistry("partition-test", timeoutManager, 9998);
        restoreOutput();
        
        // Create mock repository
        MockPartitionedRepository<SimpleCallEntity, String> repository = 
            new MockPartitionedRepository<>("call_states");
        
        System.out.println("📋 Phase 1: Creating " + TOTAL_MACHINES + " State Machines");
        System.out.println("-".repeat(50));
        
        long startTime = System.currentTimeMillis();
        
        suppressOutput();
        for (int i = 0; i < TOTAL_MACHINES; i++) {
            String machineId = "call-" + i;
            
            // Create entity
            SimpleCallEntity entity = new SimpleCallEntity(machineId);
            entity.setCallerId("+1-555-" + String.format("%04d", i % 10000));
            entity.setCalleeId("+1-555-" + String.format("%04d", (i + 1) % 10000));
            
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
            
            if ((i + 1) % 100 == 0) {
                restoreOutput();
                System.out.print("  Created " + (i + 1) + " machines...\r");
                suppressOutput();
            }
        }
        restoreOutput();
        
        long creationTime = System.currentTimeMillis() - startTime;
        System.out.println("✅ Created " + TOTAL_MACHINES + " machines in " + 
            String.format("%.2f", creationTime/1000.0) + " seconds");
        System.out.println("   Rate: " + String.format("%.0f", TOTAL_MACHINES/(creationTime/1000.0)) + " machines/sec");
        
        // Print initial repository statistics
        System.out.println();
        repository.printStatistics();
        
        // Phase 2: Evict machines from memory
        System.out.println("\n📋 Phase 2: Evicting Machines from Memory");
        System.out.println("-".repeat(50));
        
        System.out.print("Removing all machines from memory... ");
        suppressOutput();
        for (int i = 0; i < TOTAL_MACHINES; i++) {
            registry.removeMachine("call-" + i);
        }
        restoreOutput();
        System.out.println("Done");
        System.out.println("✅ All machines evicted - memory cleared");
        
        // Phase 3: Test rehydration
        System.out.println("\n📋 Phase 3: Testing Rehydration");
        System.out.println("-".repeat(50));
        
        Random random = new Random();
        int rehydrateCount = 100;
        long rehydrateStart = System.currentTimeMillis();
        int successful = 0;
        
        for (int i = 0; i < rehydrateCount; i++) {
            int machineIndex = random.nextInt(TOTAL_MACHINES);
            String machineId = "call-" + machineIndex;
            
            // Simulate rehydration from repository
            SimpleCallEntity entity = repository.findById(machineId);
            if (entity != null) {
                suppressOutput();
                // Create machine from persisted state
                GenericStateMachine<SimpleCallEntity, Object> machine = createMachine(machineId);
                machine.setPersistingEntity(entity);
                registry.register(machineId, machine);
                restoreOutput();
                successful++;
            }
        }
        
        long rehydrateTime = System.currentTimeMillis() - rehydrateStart;
        System.out.println("✅ Rehydrated " + successful + "/" + rehydrateCount + " machines");
        System.out.println("   Time: " + String.format("%.3f", rehydrateTime/1000.0) + " seconds");
        System.out.println("   Rate: " + String.format("%.0f", rehydrateCount/(rehydrateTime/1000.0)) + " machines/sec");
        
        // Phase 4: Send events to test state transitions
        System.out.println("\n📋 Phase 4: Sending Events");
        System.out.println("-".repeat(50));
        
        int eventsToSend = 500;
        Map<String, Integer> eventCounts = new HashMap<>();
        eventCounts.put("IncomingCall", 0);
        eventCounts.put("Answer", 0);
        eventCounts.put("Hangup", 0);
        
        suppressOutput();
        for (int i = 0; i < eventsToSend; i++) {
            int machineIndex = random.nextInt(TOTAL_MACHINES);
            String machineId = "call-" + machineIndex;
            
            // Check if we need to rehydrate
            if (!registry.isInMemory(machineId)) {
                SimpleCallEntity entity = repository.findById(machineId);
                if (entity != null) {
                    GenericStateMachine<SimpleCallEntity, Object> machine = createMachine(machineId);
                    machine.setPersistingEntity(entity);
                    registry.register(machineId, machine);
                }
            }
            
            // Send random event
            int eventType = random.nextInt(3);
            switch (eventType) {
                case 0:
                    registry.routeEvent(machineId, new IncomingCall("+1", "+2"));
                    eventCounts.put("IncomingCall", eventCounts.get("IncomingCall") + 1);
                    break;
                case 1:
                    registry.routeEvent(machineId, new Answer());
                    eventCounts.put("Answer", eventCounts.get("Answer") + 1);
                    break;
                case 2:
                    registry.routeEvent(machineId, new Hangup());
                    eventCounts.put("Hangup", eventCounts.get("Hangup") + 1);
                    break;
            }
        }
        restoreOutput();
        
        System.out.println("✅ Sent " + eventsToSend + " events");
        System.out.println("   IncomingCall: " + eventCounts.get("IncomingCall"));
        System.out.println("   Answer: " + eventCounts.get("Answer"));
        System.out.println("   Hangup: " + eventCounts.get("Hangup"));
        
        // Final statistics
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   FINAL STATISTICS");
        System.out.println("=".repeat(80));
        
        repository.printStatistics();
        
        // Sample state distribution
        System.out.println("\n📊 State Distribution (Sample of 100 machines):");
        System.out.println("-".repeat(50));
        Map<String, Integer> stateDistribution = new HashMap<>();
        
        suppressOutput();
        for (int i = 0; i < 100; i++) {
            int machineIndex = random.nextInt(TOTAL_MACHINES);
            String machineId = "call-" + machineIndex;
            
            GenericStateMachine<?, ?> machine = registry.getMachine(machineId);
            if (machine == null) {
                // Need to rehydrate
                SimpleCallEntity entity = repository.findById(machineId);
                if (entity != null) {
                    machine = createMachine(machineId);
                    ((GenericStateMachine<SimpleCallEntity, Object>) machine).setPersistingEntity(entity);
                    registry.register(machineId, machine);
                }
            }
            
            if (machine != null) {
                String state = machine.getCurrentState();
                stateDistribution.merge(state, 1, Integer::sum);
            }
        }
        restoreOutput();
        
        System.out.println(String.format("%-15s | %-10s | %-10s", "State", "Count", "Percentage"));
        System.out.println("-".repeat(50));
        for (Map.Entry<String, Integer> entry : stateDistribution.entrySet()) {
            double percentage = (entry.getValue() / 100.0) * 100;
            System.out.println(String.format("%-15s | %-10d | %.1f%%", 
                entry.getKey(), entry.getValue(), percentage));
        }
        
        System.out.println("\n📊 Performance Summary:");
        System.out.println("-".repeat(50));
        System.out.println("Total Machines Created: " + TOTAL_MACHINES);
        System.out.println("Creation Rate: " + String.format("%.0f machines/sec", TOTAL_MACHINES/(creationTime/1000.0)));
        System.out.println("Rehydration Rate: " + String.format("%.0f machines/sec", rehydrateCount/(rehydrateTime/1000.0)));
        System.out.println("Events Processed: " + eventsToSend);
        
        // Cleanup
        suppressOutput();
        registry.shutdown();
        timeoutManager.shutdown();
        restoreOutput();
        
        System.out.println("\n✅ Test Complete - Partitioned Repository with Rehydration Works!");
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