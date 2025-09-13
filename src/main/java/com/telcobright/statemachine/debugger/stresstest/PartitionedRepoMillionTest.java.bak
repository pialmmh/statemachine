package com.telcobright.statemachine.debugger.stresstest;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.timeout.TimeoutManager;
import com.telcobright.statemachine.persistence.*;
import com.telcobright.statemachine.db.MockPartitionedRepository;
import com.telcobright.statemachine.debugger.stresstest.SimpleEvents.*;
import java.util.function.Supplier;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Test class for partitioned-repo persistence with million iterations
 * Tests rehydration capability with a simplified state machine
 */
public class PartitionedRepoMillionTest {
    
    private static final int TOTAL_MACHINES = 1_000_000;
    private static final int BATCH_SIZE = 10_000;
    private static final int MEMORY_LIMIT = 50_000; // Keep only 50k machines in memory
    private static final int REHYDRATION_TEST_COUNT = 10_000;
    
    // State tracking
    private static final Map<String, AtomicInteger> stateCounters = new ConcurrentHashMap<>();
    private static final AtomicInteger totalEvents = new AtomicInteger(0);
    private static final AtomicInteger rehydrationCount = new AtomicInteger(0);
    private static final AtomicInteger offlineMachines = new AtomicInteger(0);
    
    static {
        // Initialize state counters
        stateCounters.put("IDLE", new AtomicInteger(0));
        stateCounters.put("RINGING", new AtomicInteger(0));
        stateCounters.put("CONNECTED", new AtomicInteger(0));
        stateCounters.put("ON_HOLD", new AtomicInteger(0));
        stateCounters.put("OFFLINE", new AtomicInteger(0));
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   PARTITIONED-REPO MILLION ITERATION TEST");
        System.out.println("=".repeat(80));
        System.out.println();
        System.out.println("Configuration:");
        System.out.println("  Total Machines: " + String.format("%,d", TOTAL_MACHINES));
        System.out.println("  Memory Limit: " + String.format("%,d", MEMORY_LIMIT));
        System.out.println("  Batch Size: " + String.format("%,d", BATCH_SIZE));
        System.out.println("  Rehydration Tests: " + String.format("%,d", REHYDRATION_TEST_COUNT));
        System.out.println();
        
        runPartitionedTest();
    }
    
    private static void runPartitionedTest() throws Exception {
        System.out.println("📋 Phase 1: Setup and Machine Creation");
        System.out.println("-".repeat(50));
        
        // Create components
        TimeoutManager timeoutManager = new TimeoutManager();
        StateMachineRegistry registry = new StateMachineRegistry("partitioned-test", timeoutManager, 9999);
        
        // Create mock partitioned repository
        MockPartitionedRepository<SimpleCallEntity, String> repository = 
            new MockPartitionedRepository<>("call_states");
        
        // Note: In production, you would configure the registry with:
        // registry.usePartitionedPersistence(repository, SimpleCallEntity.class);
        // or
        // registry.setPersistenceProvider(provider);
        
        System.out.println("✅ Using mock partitioned repository for demonstration");
        
        // Store machine factories for rehydration
        Map<String, Supplier<GenericStateMachine<SimpleCallEntity, Object>>> factories = new ConcurrentHashMap<>();
        
        long startTime = System.currentTimeMillis();
        
        // Phase 1: Create machines in batches
        System.out.println("\n📋 Creating " + String.format("%,d", TOTAL_MACHINES) + " state machines...");
        
        for (int batch = 0; batch < TOTAL_MACHINES / BATCH_SIZE; batch++) {
            int batchStart = batch * BATCH_SIZE;
            int batchEnd = Math.min(batchStart + BATCH_SIZE, TOTAL_MACHINES);
            
            System.out.print("  Batch " + (batch + 1) + "/" + (TOTAL_MACHINES/BATCH_SIZE) + 
                " [" + batchStart + "-" + batchEnd + "]... ");
            
            for (int i = batchStart; i < batchEnd; i++) {
                String machineId = "call-" + i;
                
                // Create entity
                SimpleCallEntity entity = new SimpleCallEntity(machineId);
                entity.setCallerId("+1-555-" + String.format("%04d", i % 10000));
                entity.setCalleeId("+1-555-" + String.format("%04d", (i + 1) % 10000));
                
                // Create machine using factory
                GenericStateMachine<SimpleCallEntity, Object> machine = createStateMachine(machineId);
                
                // Store factory for rehydration
                factories.put(machineId, () -> createStateMachine(machineId));
                machine.setPersistingEntity(entity);
                
                // Register with registry
                registry.register(machineId, machine);
                
                // Manually persist to mock repository
                repository.insert(entity);
                
                // Update state counter
                stateCounters.get(machine.getCurrentState()).incrementAndGet();
            }
            
            System.out.println("✓");
            
            // Periodically remove machines from memory to test rehydration
            if (batch % 5 == 4) {
                removeOldMachinesFromMemory(registry, batchStart - MEMORY_LIMIT, batchStart);
            }
        }
        
        long creationTime = System.currentTimeMillis() - startTime;
        System.out.println("\n✅ Created " + String.format("%,d", TOTAL_MACHINES) + 
            " machines in " + (creationTime/1000.0) + " seconds");
        
        // Print initial statistics
        repository.printStatistics();
        printStateDistribution("After Creation");
        
        // Phase 2: Send events to random machines
        System.out.println("\n📋 Phase 2: Sending Events to Random Machines");
        System.out.println("-".repeat(50));
        
        Random random = new Random();
        int eventsSent = 0;
        
        for (int i = 0; i < 100_000; i++) {
            int machineIndex = random.nextInt(TOTAL_MACHINES);
            String machineId = "call-" + machineIndex;
            
            // Send random event
            Object event = generateRandomEvent(random);
            boolean result = registry.routeEvent(machineId, event);
            
            if (result) {
                eventsSent++;
                totalEvents.incrementAndGet();
                
                // Check if machine was rehydrated
                if (!registry.isInMemory(machineId)) {
                    rehydrationCount.incrementAndGet();
                }
            }
            
            if (i % 10000 == 0) {
                System.out.println("  Sent " + String.format("%,d", i) + " events, " +
                    "Rehydrations: " + rehydrationCount.get());
            }
        }
        
        System.out.println("\n✅ Sent " + String.format("%,d", eventsSent) + " events");
        System.out.println("   Total rehydrations: " + rehydrationCount.get());
        
        // Phase 3: Test targeted rehydration
        System.out.println("\n📋 Phase 3: Testing Targeted Rehydration");
        System.out.println("-".repeat(50));
        
        // Clear memory to force rehydration
        System.out.println("Clearing all machines from memory...");
        for (int i = 0; i < TOTAL_MACHINES; i++) {
            registry.removeMachine("call-" + i);
        }
        
        // Test rehydration of specific machines
        System.out.println("Testing rehydration of " + REHYDRATION_TEST_COUNT + " machines...");
        long rehydrationStart = System.currentTimeMillis();
        int successfulRehydrations = 0;
        
        for (int i = 0; i < REHYDRATION_TEST_COUNT; i++) {
            int machineIndex = random.nextInt(TOTAL_MACHINES);
            String machineId = "call-" + machineIndex;
            
            // Simulate rehydration from repository
            SimpleCallEntity entity = repository.findById(machineId);
            if (entity != null) {
                // Recreate machine from persisted entity
                GenericStateMachine<SimpleCallEntity, Object> machine = createStateMachine(machineId);
                machine.setPersistingEntity(entity);
                // Note: State would be restored from entity in production
                registry.register(machineId, machine);
                
                // Now send event
                boolean result = registry.routeEvent(machineId, new Ring());
                if (result) {
                    successfulRehydrations++;
                }
            }
            
            if (i % 1000 == 0 && i > 0) {
                System.out.println("  Rehydrated " + i + " machines...");
            }
        }
        
        long rehydrationTime = System.currentTimeMillis() - rehydrationStart;
        System.out.println("\n✅ Rehydration complete:");
        System.out.println("   Successful: " + successfulRehydrations + "/" + REHYDRATION_TEST_COUNT);
        System.out.println("   Time: " + (rehydrationTime/1000.0) + " seconds");
        System.out.println("   Rate: " + String.format("%.2f", REHYDRATION_TEST_COUNT/(rehydrationTime/1000.0)) + " machines/sec");
        
        // Phase 4: Simulate some machines going offline
        System.out.println("\n📋 Phase 4: Testing Offline State");
        System.out.println("-".repeat(50));
        
        int offlineCount = 10_000;
        System.out.println("Sending " + offlineCount + " machines offline...");
        
        for (int i = 0; i < offlineCount; i++) {
            int machineIndex = random.nextInt(TOTAL_MACHINES);
            String machineId = "call-" + machineIndex;
            
            if (registry.routeEvent(machineId, new GoOffline())) {
                offlineMachines.incrementAndGet();
            }
        }
        
        System.out.println("✅ Offline machines: " + offlineMachines.get());
        
        // Final statistics
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   FINAL STATISTICS");
        System.out.println("=".repeat(80));
        
        repository.printStatistics();
        
        // Calculate actual state distribution by sampling
        System.out.println("Sampling state distribution (1000 random machines)...");
        Map<String, Integer> sampledStates = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            int machineIndex = random.nextInt(TOTAL_MACHINES);
            String machineId = "call-" + machineIndex;
            
            // Trigger rehydration to get current state
            GenericStateMachine<?, ?> machine = registry.getMachine(machineId);
            if (machine != null) {
                String state = machine.getCurrentState();
                sampledStates.merge(state, 1, Integer::sum);
            }
        }
        
        System.out.println("\n📊 State Distribution (Sample of 1000):");
        System.out.println("-".repeat(50));
        System.out.println(String.format("%-15s | %-10s | %-10s", "State", "Count", "Percentage"));
        System.out.println("-".repeat(50));
        
        for (Map.Entry<String, Integer> entry : sampledStates.entrySet()) {
            double percentage = (entry.getValue() / 1000.0) * 100;
            System.out.println(String.format("%-15s | %-10d | %.2f%%", 
                entry.getKey(), entry.getValue(), percentage));
        }
        
        System.out.println("\n📊 Performance Metrics:");
        System.out.println("-".repeat(50));
        System.out.println("Total Machines Created: " + String.format("%,d", TOTAL_MACHINES));
        System.out.println("Total Events Sent: " + String.format("%,d", totalEvents.get()));
        System.out.println("Total Rehydrations: " + String.format("%,d", rehydrationCount.get()));
        System.out.println("Machines/second (creation): " + 
            String.format("%.2f", TOTAL_MACHINES / (creationTime / 1000.0)));
        System.out.println("Rehydrations/second: " + 
            String.format("%.2f", REHYDRATION_TEST_COUNT / (rehydrationTime / 1000.0)));
        
        // Cleanup
        registry.shutdown();
        timeoutManager.shutdown();
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   TEST COMPLETE");
        System.out.println("=".repeat(80));
    }
    
    private static GenericStateMachine<SimpleCallEntity, Object> createStateMachine(String machineId) {
        return EnhancedFluentBuilder.<SimpleCallEntity, Object>create(machineId)
            .initialState("IDLE")
            .state("IDLE")
                .on(InitCall.class).to("RINGING")
                .on(GoOffline.class).to("OFFLINE")
            .done()
            .state("RINGING")
                .on(Answer.class).to("CONNECTED")
                .on(Disconnect.class).to("IDLE")
                .on(GoOffline.class).to("OFFLINE")
            .done()
            .state("CONNECTED")
                .on(Hold.class).to("ON_HOLD")
                .on(Disconnect.class).to("IDLE")
                .on(GoOffline.class).to("OFFLINE")
            .done()
            .state("ON_HOLD")
                .on(Resume.class).to("CONNECTED")
                .on(Disconnect.class).to("IDLE")
                .on(GoOffline.class).to("OFFLINE")
            .done()
            .state("OFFLINE")
                .on(ComeOnline.class).to("IDLE")
            .done()
            .build();
    }
    
    private static Object generateRandomEvent(Random random) {
        int eventType = random.nextInt(8);
        switch (eventType) {
            case 0: return new InitCall("+1-555-0001", "+1-555-0002");
            case 1: return new Ring();
            case 2: return new Answer();
            case 3: return new Hold();
            case 4: return new Resume();
            case 5: return new Disconnect();
            case 6: return new GoOffline();
            case 7: return new ComeOnline();
            default: return new Ring();
        }
    }
    
    private static void removeOldMachinesFromMemory(StateMachineRegistry registry, int start, int end) {
        if (start < 0) return;
        
        System.out.print("  Removing machines " + start + "-" + end + " from memory... ");
        for (int i = start; i < end; i++) {
            registry.removeMachine("call-" + i);
        }
        System.out.println("✓");
    }
    
    private static void printStateDistribution(String phase) {
        System.out.println("\n📊 State Distribution - " + phase + ":");
        System.out.println("-".repeat(50));
        System.out.println(String.format("%-15s | %-10s", "State", "Count"));
        System.out.println("-".repeat(50));
        
        int total = 0;
        for (Map.Entry<String, AtomicInteger> entry : stateCounters.entrySet()) {
            int count = entry.getValue().get();
            total += count;
            System.out.println(String.format("%-15s | %-10d", entry.getKey(), count));
        }
        System.out.println("-".repeat(50));
        System.out.println(String.format("%-15s | %-10d", "TOTAL", total));
    }
}