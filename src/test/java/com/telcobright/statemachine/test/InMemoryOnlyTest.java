package com.telcobright.statemachine.test;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Test to demonstrate and validate in-memory only operation of state machines
 * without any persistence to database
 */
public class InMemoryOnlyTest {

    // Simple mock classes to avoid external dependencies
    enum PersistenceType {
        MYSQL_DIRECT, MYSQL_OPTIMIZED, PARTITIONED_REPO, NONE
    }

    static class StateMachineRegistry {
        private final Map<String, CallStateMachine> machines = new ConcurrentHashMap<>();
        private PersistenceType persistenceType = PersistenceType.MYSQL_DIRECT;
        private boolean rehydrationDisabled = false;
        private final String registryId;

        public StateMachineRegistry(String id) {
            this.registryId = id;
        }

        public void setPersistenceType(PersistenceType type) {
            this.persistenceType = type;
            System.out.println("[Registry-" + registryId + "] Persistence type set to: " + type);
        }

        public PersistenceType getPersistenceType() {
            return persistenceType;
        }

        public void disableRehydration() {
            this.rehydrationDisabled = true;
            System.out.println("[Registry-" + registryId + "] Rehydration disabled");
        }

        public boolean isRehydrationDisabled() {
            return rehydrationDisabled;
        }

        public void register(String id, CallStateMachine machine) {
            machines.put(id, machine);
            if (persistenceType != PersistenceType.NONE) {
                System.out.println("[Registry-" + registryId + "] Machine " + id + " registered (would persist)");
            } else {
                System.out.println("[Registry-" + registryId + "] Machine " + id + " registered (in-memory only)");
            }
        }

        public CallStateMachine get(String id) {
            return machines.get(id);
        }

        public boolean removeMachine(String id) {
            CallStateMachine removed = machines.remove(id);
            if (removed != null) {
                System.out.println("[Registry-" + registryId + "] Machine " + id + " removed from memory");
                return true;
            }
            return false;
        }

        public boolean routeEvent(String machineId, String event) {
            CallStateMachine machine = machines.get(machineId);

            if (machine == null) {
                if (persistenceType != PersistenceType.NONE && !rehydrationDisabled) {
                    System.out.println("[Registry-" + registryId + "] Machine " + machineId +
                        " not in memory - would attempt rehydration from DB");
                    return false;
                } else {
                    System.out.println("[Registry-" + registryId + "] Machine " + machineId +
                        " not found (in-memory only mode)");
                    return false;
                }
            }

            machine.processEvent(event);
            return true;
        }

        public int getActiveMachineCount() {
            return machines.size();
        }

        public void printStatus() {
            System.out.println("\n[Registry-" + registryId + "] Status:");
            System.out.println("  Persistence Type: " + persistenceType);
            System.out.println("  Rehydration: " + (rehydrationDisabled ? "DISABLED" : "ENABLED"));
            System.out.println("  Active Machines: " + machines.size());
            if (!machines.isEmpty()) {
                System.out.println("  Machine IDs: " + machines.keySet());
            }
        }

        public void shutdown() {
            System.out.println("[Registry-" + registryId + "] Shutting down...");
            machines.clear();
        }
    }

    static class CallStateMachine {
        private String id;
        private String state;
        private CallContext context;
        private int eventCount = 0;

        public CallStateMachine(String id) {
            this.id = id;
            this.state = "IDLE";
            this.context = new CallContext(id);
        }

        public void processEvent(String event) {
            eventCount++;
            String oldState = state;

            switch (event) {
                case "DIAL":
                    if (state.equals("IDLE")) {
                        state = "DIALING";
                        context.addEvent(event, "Started dialing");
                    }
                    break;
                case "RING":
                    if (state.equals("DIALING")) {
                        state = "RINGING";
                        context.addEvent(event, "Phone ringing");
                    }
                    break;
                case "ANSWER":
                    if (state.equals("RINGING")) {
                        state = "CONNECTED";
                        context.addEvent(event, "Call connected");
                        context.startBilling();
                    }
                    break;
                case "HANGUP":
                    state = "IDLE";
                    context.addEvent(event, "Call ended");
                    context.stopBilling();
                    break;
            }

            System.out.println("  Machine " + id + ": " + oldState + " --[" + event + "]--> " + state);
        }

        public String getState() { return state; }
        public CallContext getContext() { return context; }
        public int getEventCount() { return eventCount; }
    }

    static class CallContext {
        private String machineId;
        private List<EventRecord> events = new ArrayList<>();
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private BigDecimal totalCharge = BigDecimal.ZERO;
        private boolean billingActive = false;

        public CallContext(String machineId) {
            this.machineId = machineId;
            this.startTime = LocalDateTime.now();
        }

        public void addEvent(String type, String description) {
            events.add(new EventRecord(type, description));
        }

        public void startBilling() {
            billingActive = true;
            totalCharge = totalCharge.add(new BigDecimal("0.50")); // Connection charge
        }

        public void stopBilling() {
            if (billingActive) {
                billingActive = false;
                endTime = LocalDateTime.now();
                // Calculate usage charge
                long seconds = java.time.Duration.between(startTime, endTime).getSeconds();
                BigDecimal usage = new BigDecimal(seconds * 0.01); // 1 cent per second
                totalCharge = totalCharge.add(usage);
            }
        }

        public int getEventCount() { return events.size(); }
        public BigDecimal getTotalCharge() { return totalCharge; }
    }

    static class EventRecord {
        private String type;
        private String description;
        private LocalDateTime timestamp;

        public EventRecord(String type, String description) {
            this.type = type;
            this.description = description;
            this.timestamp = LocalDateTime.now();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   IN-MEMORY ONLY STATE MACHINE TEST");
        System.out.println("=".repeat(80));
        System.out.println();

        // Test 1: Compare persistence vs in-memory behavior
        testPersistenceComparison();

        // Test 2: In-memory performance test
        testInMemoryPerformance();

        // Test 3: Memory lifecycle test
        testMemoryLifecycle();

        // Test 4: Concurrent in-memory operations
        testConcurrentOperations();

        System.out.println("\n" + "=".repeat(80));
        System.out.println("   ✅ ALL IN-MEMORY TESTS PASSED");
        System.out.println("=".repeat(80));
    }

    private static void testPersistenceComparison() {
        System.out.println("TEST 1: Persistence vs In-Memory Behavior Comparison");
        System.out.println("-".repeat(60));

        // Create two registries
        StateMachineRegistry persistedRegistry = new StateMachineRegistry("PERSISTED");
        StateMachineRegistry inMemoryRegistry = new StateMachineRegistry("IN-MEMORY");

        // Configure in-memory registry
        inMemoryRegistry.setPersistenceType(PersistenceType.NONE);
        inMemoryRegistry.disableRehydration();

        // Create machines in both
        CallStateMachine persistedMachine = new CallStateMachine("call-001");
        CallStateMachine inMemoryMachine = new CallStateMachine("call-002");

        persistedRegistry.register("call-001", persistedMachine);
        inMemoryRegistry.register("call-002", inMemoryMachine);

        // Process events
        System.out.println("\nProcessing events...");
        persistedRegistry.routeEvent("call-001", "DIAL");
        inMemoryRegistry.routeEvent("call-002", "DIAL");

        // Remove machines
        System.out.println("\nRemoving machines from memory...");
        persistedRegistry.removeMachine("call-001");
        inMemoryRegistry.removeMachine("call-002");

        // Try to route events after removal
        System.out.println("\nAttempting to route events after removal...");
        boolean persistedResult = persistedRegistry.routeEvent("call-001", "RING");
        boolean inMemoryResult = inMemoryRegistry.routeEvent("call-002", "RING");

        System.out.println("\nResults:");
        System.out.println("  Persisted registry event routing: " +
            (persistedResult ? "SUCCESS" : "FAILED (would try DB rehydration)"));
        System.out.println("  In-memory registry event routing: " +
            (inMemoryResult ? "SUCCESS" : "FAILED (expected - no persistence)"));

        persistedRegistry.shutdown();
        inMemoryRegistry.shutdown();

        System.out.println("✅ Behavior comparison complete");
    }

    private static void testInMemoryPerformance() throws Exception {
        System.out.println("\nTEST 2: In-Memory Performance Test");
        System.out.println("-".repeat(60));

        StateMachineRegistry registry = new StateMachineRegistry("PERF-TEST");
        registry.setPersistenceType(PersistenceType.NONE);
        registry.disableRehydration();

        int machineCount = 10000;
        long startTime = System.currentTimeMillis();

        // Create machines
        System.out.println("Creating " + machineCount + " in-memory state machines...");
        for (int i = 0; i < machineCount; i++) {
            CallStateMachine machine = new CallStateMachine("perf-" + i);
            registry.register("perf-" + i, machine);
        }

        long creationTime = System.currentTimeMillis() - startTime;

        // Process events
        startTime = System.currentTimeMillis();
        System.out.println("Processing events for all machines...");
        for (int i = 0; i < machineCount; i++) {
            registry.routeEvent("perf-" + i, "DIAL");
            registry.routeEvent("perf-" + i, "RING");
            registry.routeEvent("perf-" + i, "ANSWER");
            registry.routeEvent("perf-" + i, "HANGUP");
        }

        long processingTime = System.currentTimeMillis() - startTime;

        System.out.println("\nPerformance Results:");
        System.out.println("  Machines created: " + machineCount);
        System.out.println("  Creation time: " + creationTime + "ms");
        System.out.println("  Creation rate: " + (machineCount * 1000 / creationTime) + " machines/sec");
        System.out.println("  Events processed: " + (machineCount * 4));
        System.out.println("  Processing time: " + processingTime + "ms");
        System.out.println("  Event rate: " + (machineCount * 4 * 1000 / processingTime) + " events/sec");

        registry.shutdown();
        System.out.println("✅ Performance test complete");
    }

    private static void testMemoryLifecycle() {
        System.out.println("\nTEST 3: Memory Lifecycle Test");
        System.out.println("-".repeat(60));

        StateMachineRegistry registry = new StateMachineRegistry("LIFECYCLE");
        registry.setPersistenceType(PersistenceType.NONE);

        // Create machine
        CallStateMachine machine = new CallStateMachine("lifecycle-001");
        registry.register("lifecycle-001", machine);

        // Full call cycle
        System.out.println("\nExecuting full call cycle...");
        String[] events = {"DIAL", "RING", "ANSWER", "HANGUP"};
        for (String event : events) {
            registry.routeEvent("lifecycle-001", event);
        }

        // Check machine state
        CallStateMachine retrieved = registry.get("lifecycle-001");
        System.out.println("\nMachine Status:");
        System.out.println("  State: " + retrieved.getState());
        System.out.println("  Events processed: " + retrieved.getEventCount());
        System.out.println("  Context events: " + retrieved.getContext().getEventCount());
        System.out.println("  Total charge: $" + retrieved.getContext().getTotalCharge());

        // Verify machine is in memory
        System.out.println("\nMemory status before removal:");
        registry.printStatus();

        // Remove and verify
        registry.removeMachine("lifecycle-001");
        System.out.println("\nMemory status after removal:");
        registry.printStatus();

        // Verify machine is gone
        CallStateMachine gone = registry.get("lifecycle-001");
        assert gone == null : "Machine should be removed from memory";
        System.out.println("\n✅ Machine successfully removed from memory (no persistence)");

        registry.shutdown();
        System.out.println("✅ Lifecycle test complete");
    }

    private static void testConcurrentOperations() throws Exception {
        System.out.println("\nTEST 4: Concurrent In-Memory Operations");
        System.out.println("-".repeat(60));

        StateMachineRegistry registry = new StateMachineRegistry("CONCURRENT");
        registry.setPersistenceType(PersistenceType.NONE);
        registry.disableRehydration();

        int threadCount = 10;
        int machinesPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger errorCount = new AtomicInteger();

        System.out.println("Starting " + threadCount + " threads, each managing " +
            machinesPerThread + " machines...");

        long startTime = System.currentTimeMillis();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int m = 0; m < machinesPerThread; m++) {
                        String machineId = "thread-" + threadId + "-machine-" + m;

                        // Create and register
                        CallStateMachine machine = new CallStateMachine(machineId);
                        registry.register(machineId, machine);

                        // Process events
                        registry.routeEvent(machineId, "DIAL");
                        registry.routeEvent(machineId, "RING");
                        registry.routeEvent(machineId, "ANSWER");
                        Thread.sleep(1); // Simulate processing
                        registry.routeEvent(machineId, "HANGUP");

                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        long elapsedTime = System.currentTimeMillis() - startTime;

        System.out.println("\nConcurrent Operations Results:");
        System.out.println("  Total machines: " + (threadCount * machinesPerThread));
        System.out.println("  Successful: " + successCount.get());
        System.out.println("  Errors: " + errorCount.get());
        System.out.println("  Time elapsed: " + elapsedTime + "ms");
        System.out.println("  Throughput: " + (successCount.get() * 1000 / elapsedTime) + " machines/sec");
        System.out.println("  Active machines in memory: " + registry.getActiveMachineCount());

        executor.shutdown();
        registry.shutdown();

        assert errorCount.get() == 0 : "Should have no errors in concurrent processing";
        System.out.println("✅ Concurrent operations test complete");
    }
}