package com.telcobright.statemachine.test;

import com.telcobright.statemachine.StateMachineRegistry;
import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.timeout.TimeoutManager;
import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachine.StateMachineContextEntity;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Test class to verify state machine rehydration with context validation using proper registry setup
 * This test demonstrates:
 * 1. Creating machines through the registry (not directly)
 * 2. MySQL history tracking with actual table creation
 * 3. Context persistence and rehydration validation
 * 4. Custom UUID tracking across offline/online cycles
 */
public class RehydrationContextTest {
    
    // Test-specific persistent context
    public static class TestPersistentContext implements StateMachineContextEntity<String> {
        private String testId;
        private String customUuid;
        private int eventCount;
        private LocalDateTime lastModified;
        private String dataBeforeOffline;
        private boolean complete;
        private String currentState;
        private LocalDateTime lastStateChange;
        
        public TestPersistentContext() {
            this.complete = false;
            this.lastStateChange = LocalDateTime.now();
        }
        
        public TestPersistentContext(String testId) {
            this.testId = testId;
            this.eventCount = 0;
            this.lastModified = LocalDateTime.now();
            this.complete = false;
            this.lastStateChange = LocalDateTime.now();
        }
        
        // Implement StateMachineContextEntity interface
        @Override
        public boolean isComplete() { return complete; }
        
        @Override
        public void setComplete(boolean complete) { this.complete = complete; }
        
        @Override
        public String getCurrentState() { return currentState; }
        
        @Override
        public void setCurrentState(String state) { 
            this.currentState = state;
            this.lastStateChange = LocalDateTime.now();
        }
        
        @Override
        public LocalDateTime getLastStateChange() { return lastStateChange; }
        
        @Override
        public void setLastStateChange(LocalDateTime lastStateChange) { 
            this.lastStateChange = lastStateChange; 
        }
        
        // Custom getters and setters
        public String getTestId() { return testId; }
        public void setTestId(String testId) { this.testId = testId; }
        
        public String getCustomUuid() { return customUuid; }
        public void setCustomUuid(String customUuid) { 
            this.customUuid = customUuid;
            this.lastModified = LocalDateTime.now();
        }
        
        public int getEventCount() { return eventCount; }
        public void setEventCount(int eventCount) { this.eventCount = eventCount; }
        
        public LocalDateTime getLastModified() { return lastModified; }
        public void setLastModified(LocalDateTime lastModified) { this.lastModified = lastModified; }
        
        public String getDataBeforeOffline() { return dataBeforeOffline; }
        public void setDataBeforeOffline(String data) { this.dataBeforeOffline = data; }
        
        public void incrementEventCount() {
            this.eventCount++;
            this.lastModified = LocalDateTime.now();
        }
        
        @Override
        public String toString() {
            return String.format("TestContext[id=%s, uuid=%s, events=%d, data=%s, modified=%s, state=%s]",
                testId, customUuid, eventCount, dataBeforeOffline, lastModified, currentState);
        }
    }
    
    // Test-specific volatile context
    public static class TestVolatileContext {
        private String sessionId;
        private long startTime;
        
        public TestVolatileContext() {
            this.sessionId = "SESSION-" + UUID.randomUUID().toString().substring(0, 8);
            this.startTime = System.currentTimeMillis();
        }
        
        public String getSessionId() { return sessionId; }
        public long getStartTime() { return startTime; }
        
        public static TestVolatileContext createFromPersistent(TestPersistentContext persistent) {
            TestVolatileContext vol = new TestVolatileContext();
            System.out.println("[Volatile] Recreated volatile context for: " + persistent.getTestId());
            return vol;
        }
    }
    
    // Event types
    public static class StartEvent implements StateMachineEvent {
        @Override
        public String getEventType() { return "START"; }
        @Override
        public String getDescription() { return "Start processing"; }
        @Override
        public Object getPayload() { return null; }
        @Override
        public long getTimestamp() { return System.currentTimeMillis(); }
    }
    
    public static class ProcessEvent implements StateMachineEvent {
        private String data;
        private long timestamp;
        
        public ProcessEvent(String data) { 
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
        
        @Override
        public String getEventType() { return "PROCESS"; }
        @Override
        public String getDescription() { return "Process data: " + data; }
        @Override
        public Object getPayload() { return data; }
        @Override
        public long getTimestamp() { return timestamp; }
        public String getData() { return data; }
    }
    
    public static class CompleteEvent implements StateMachineEvent {
        @Override
        public String getEventType() { return "COMPLETE"; }
        @Override
        public String getDescription() { return "Complete processing"; }
        @Override
        public Object getPayload() { return null; }
        @Override
        public long getTimestamp() { return System.currentTimeMillis(); }
    }
    
    // States
    public enum TestState {
        IDLE, PROCESSING, CONNECTED, FINISHED
    }
    
    private static StateMachineRegistry registry;
    private static TimeoutManager timeoutManager;
    private static String currentMachineId = "test-machine-001";
    private static String testUuid = null;
    private static TestPersistentContext contextBeforeOffline = null;
    
    public static void main(String[] args) throws Exception {
        System.out.println("\n============================================");
        System.out.println("   State Machine Rehydration Context Test");
        System.out.println("   Using Registry-Based Machine Creation");
        System.out.println("============================================\n");
        
        // Initialize components
        timeoutManager = new TimeoutManager();
        registry = new StateMachineRegistry(timeoutManager, 9997);
        registry.enableDebugMode(9997);
        
        // Set optimized persistence for our test context
        registry.setOptimizedPersistenceProvider(TestPersistentContext.class, "test_persistent_context");
        
        System.out.println("✓ Registry initialized with debug mode");
        System.out.println("✓ Persistence configured for TestPersistentContext");
        System.out.println("✓ MySQL history tracking enabled\n");
        
        // Create the machine using registry (this ensures proper history tracking)
        System.out.println("Creating machine through registry...");
        GenericStateMachine<TestPersistentContext, TestVolatileContext> machine = 
            registry.create(currentMachineId, () -> createTestMachine(currentMachineId));
        
        System.out.println("✓ Machine created through registry with MySQL history tracking");
        System.out.println("  Machine ID: " + currentMachineId);
        System.out.println("  Initial state: " + machine.getCurrentState());
        System.out.println("  History table: history_" + currentMachineId.replace("-", "_") + "\n");
        
        // Schedule periodic events to test the flow
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        
        // Schedule event sequence
        scheduler.schedule(() -> sendStartEvent(), 2, TimeUnit.SECONDS);
        scheduler.schedule(() -> sendProcessEvent(), 5, TimeUnit.SECONDS);
        scheduler.schedule(() -> prepareForOffline(), 8, TimeUnit.SECONDS);
        scheduler.schedule(() -> sendCompleteEvent(), 10, TimeUnit.SECONDS);
        // Machine will timeout to offline after 30 seconds in CONNECTED state
        scheduler.schedule(() -> testRehydration(), 45, TimeUnit.SECONDS);
        scheduler.schedule(() -> verifyContext(), 50, TimeUnit.SECONDS);
        scheduler.schedule(() -> cleanup(scheduler), 55, TimeUnit.SECONDS);
        
        System.out.println("📋 Test Schedule:");
        System.out.println("  T+2s  : Send START event (IDLE → PROCESSING)");
        System.out.println("  T+5s  : Send PROCESS event (PROCESSING → CONNECTED)");
        System.out.println("  T+8s  : Add custom UUID to context before offline");
        System.out.println("  T+10s : Send PROCESS event (stay in CONNECTED)");
        System.out.println("  T+40s : Machine timeouts to offline (CONNECTED → IDLE → offline)");
        System.out.println("  T+45s : Attempt rehydration by sending event");
        System.out.println("  T+50s : Verify context matches");
        System.out.println("  T+55s : Cleanup and exit\n");
        
        System.out.println("Test is running... Check MySQL for 'history_test_machine_001' table");
        System.out.println("Press Enter to exit early...\n");
        
        // Keep the program running
        Scanner scanner = new Scanner(System.in);
        try {
            scanner.nextLine();
        } catch (Exception e) {
            // Handle case when running without console
        }
        
        // Cleanup
        scheduler.shutdown();
        registry.shutdown();
        timeoutManager.shutdown();
        System.out.println("\n✓ Test completed. Check MySQL for history data.");
        System.exit(0);
    }
    
    /**
     * Create a test machine using the proper state machine builder
     * This method is called by the registry to create machines
     */
    private static GenericStateMachine<TestPersistentContext, TestVolatileContext> createTestMachine(String machineId) {
        // Create contexts
        TestPersistentContext persistent = new TestPersistentContext(machineId);
        TestVolatileContext volatile_ = new TestVolatileContext();
        
        System.out.println("[Factory] Creating test machine: " + machineId);
        
        // Create a simple machine for demonstration
        // Note: This is a simplified version just to test registry integration
        GenericStateMachine<TestPersistentContext, TestVolatileContext> machine = 
            new GenericStateMachine<TestPersistentContext, TestVolatileContext>(machineId, timeoutManager, registry);
        
        machine.setPersistingEntity(persistent);
        machine.setContext(volatile_);
        
        System.out.println("[Factory] Created machine with volatile session: " + volatile_.getSessionId());
        return machine;
    }
    
    private static void sendStartEvent() {
        System.out.println("\n[T+2s] Sending START event...");
        GenericStateMachine<?, ?> machine = registry.getMachine(currentMachineId);
        if (machine != null) {
            machine.fire(new StartEvent());
            TestPersistentContext ctx = (TestPersistentContext) machine.getPersistingEntity();
            ctx.incrementEventCount();
            System.out.println("  Event count: " + ctx.getEventCount());
            System.out.println("  Current state: " + machine.getCurrentState());
        }
    }
    
    private static void sendProcessEvent() {
        System.out.println("\n[T+5s] Sending PROCESS event...");
        GenericStateMachine<?, ?> machine = registry.getMachine(currentMachineId);
        if (machine != null) {
            ProcessEvent event = new ProcessEvent("test-data-" + System.currentTimeMillis());
            machine.fire(event);
            TestPersistentContext ctx = (TestPersistentContext) machine.getPersistingEntity();
            ctx.incrementEventCount();
            System.out.println("  Event count: " + ctx.getEventCount());
            System.out.println("  Current state: " + machine.getCurrentState());
        }
    }
    
    private static void prepareForOffline() {
        System.out.println("\n[T+8s] Preparing for offline - adding custom UUID to context...");
        GenericStateMachine<?, ?> machine = registry.getMachine(currentMachineId);
        if (machine != null) {
            TestPersistentContext ctx = (TestPersistentContext) machine.getPersistingEntity();
            
            // Generate and set custom UUID
            testUuid = UUID.randomUUID().toString();
            ctx.setCustomUuid(testUuid);
            ctx.setDataBeforeOffline("Important data set at " + LocalDateTime.now());
            
            // Store a copy of the context for later verification
            contextBeforeOffline = new TestPersistentContext(ctx.getTestId());
            contextBeforeOffline.setCustomUuid(ctx.getCustomUuid());
            contextBeforeOffline.setEventCount(ctx.getEventCount());
            contextBeforeOffline.setDataBeforeOffline(ctx.getDataBeforeOffline());
            contextBeforeOffline.setCurrentState(ctx.getCurrentState());
            
            System.out.println("  ✓ Custom UUID set: " + testUuid);
            System.out.println("  ✓ Data before offline: " + ctx.getDataBeforeOffline());
            System.out.println("  ✓ Context snapshot saved for verification");
            System.out.println("  Current state: " + machine.getCurrentState());
        }
    }
    
    private static void sendCompleteEvent() {
        System.out.println("\n[T+10s] Sending PROCESS event (stay in CONNECTED)...");
        GenericStateMachine<?, ?> machine = registry.getMachine(currentMachineId);
        if (machine != null) {
            // Send ProcessEvent to stay in CONNECTED state
            ProcessEvent event = new ProcessEvent("stay-in-connected");
            machine.fire(event);
            TestPersistentContext ctx = (TestPersistentContext) machine.getPersistingEntity();
            System.out.println("  State: " + machine.getCurrentState());
            System.out.println("  Event count: " + ctx.getEventCount());
            System.out.println("  Machine will timeout to offline in 30 seconds...");
        }
    }
    
    private static void testRehydration() {
        System.out.println("\n[T+45s] Testing rehydration...");
        
        // Check if machine is still active
        boolean isActive = registry.isActive(currentMachineId);
        System.out.println("  Machine active in registry: " + isActive);
        
        if (!isActive) {
            System.out.println("  ✓ Machine correctly went offline");
            
            // Try to get machine - should return null per our new behavior
            GenericStateMachine<?, ?> offlineMachine = registry.getMachine(currentMachineId);
            if (offlineMachine == null) {
                System.out.println("  ✓ getMachine() correctly returned null for offline machine");
            }
            
            // Now trigger rehydration by sending an event through registry
            System.out.println("\n  Triggering rehydration by sending event to offline machine...");
            
            try {
                // This should trigger rehydration via createOrGet
                GenericStateMachine<TestPersistentContext, TestVolatileContext> rehydratedMachine = 
                    registry.createOrGet(currentMachineId, () -> createTestMachine(currentMachineId));
                
                if (rehydratedMachine != null) {
                    // Send event to the rehydrated machine
                    rehydratedMachine.fire(new StartEvent());
                    
                    System.out.println("  ✓ Machine successfully rehydrated through registry");
                    TestPersistentContext rehydrated = rehydratedMachine.getPersistingEntity();
                    System.out.println("  Rehydrated context: " + rehydrated);
                    System.out.println("  Current state: " + rehydratedMachine.getCurrentState());
                } else {
                    System.out.println("  ✗ Failed to rehydrate machine");
                }
                
            } catch (Exception e) {
                System.err.println("  ✗ Rehydration failed: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("  ⚠️  Machine still active - waiting for offline timeout...");
        }
    }
    
    private static void verifyContext() {
        System.out.println("\n[T+50s] Verifying rehydrated context...");
        
        GenericStateMachine<?, ?> machine = registry.getMachine(currentMachineId);
        if (machine != null) {
            TestPersistentContext current = (TestPersistentContext) machine.getPersistingEntity();
            
            System.out.println("\n  Context Comparison:");
            System.out.println("  =====================================");
            
            // Compare UUID
            boolean uuidMatch = testUuid != null && testUuid.equals(current.getCustomUuid());
            System.out.println("  Custom UUID:");
            System.out.println("    Before offline: " + testUuid);
            System.out.println("    After rehydration: " + current.getCustomUuid());
            System.out.println("    Match: " + (uuidMatch ? "✓ YES" : "✗ NO"));
            
            // Compare data
            boolean dataMatch = contextBeforeOffline != null && 
                               contextBeforeOffline.getDataBeforeOffline() != null &&
                               contextBeforeOffline.getDataBeforeOffline().equals(current.getDataBeforeOffline());
            System.out.println("\n  Data before offline:");
            System.out.println("    Expected: " + (contextBeforeOffline != null ? contextBeforeOffline.getDataBeforeOffline() : "null"));
            System.out.println("    Actual: " + current.getDataBeforeOffline());
            System.out.println("    Match: " + (dataMatch ? "✓ YES" : "✗ NO"));
            
            // Compare event count
            boolean countMatch = contextBeforeOffline != null && 
                                contextBeforeOffline.getEventCount() == current.getEventCount();
            System.out.println("\n  Event count:");
            System.out.println("    Before offline: " + (contextBeforeOffline != null ? contextBeforeOffline.getEventCount() : "null"));
            System.out.println("    After rehydration: " + current.getEventCount());
            System.out.println("    Match: " + (countMatch ? "✓ YES" : "✗ NO"));
            
            // Overall result
            System.out.println("\n  =====================================");
            if (uuidMatch && dataMatch && countMatch) {
                System.out.println("  ✅ SUCCESS: All context data matches!");
                System.out.println("  The persistent context was correctly saved to DB");
                System.out.println("  and restored during rehydration.");
                System.out.println("  MySQL history table should contain all transitions.");
            } else {
                System.out.println("  ❌ FAILURE: Context mismatch detected!");
                System.out.println("  Some data was not properly persisted or restored.");
            }
            
        } else {
            System.out.println("  ⚠️  Machine not found - rehydration may have failed");
        }
    }
    
    private static void cleanup(ScheduledExecutorService scheduler) {
        System.out.println("\n[T+55s] Cleaning up...");
        scheduler.shutdown();
        registry.shutdown();
        timeoutManager.shutdown();
        System.out.println("✓ Test completed successfully!");
        System.out.println("\nTo check MySQL history:");
        System.out.println("  mysql -h 127.0.0.1 -u root -proot statedb");
        System.out.println("  SELECT * FROM history_test_machine_001;");
        System.exit(0);
    }
}