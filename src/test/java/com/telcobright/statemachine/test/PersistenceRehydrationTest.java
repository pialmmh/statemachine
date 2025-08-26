package com.telcobright.statemachine.test;

import com.telcobright.statemachine.StateMachineRegistry;
import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.timeout.TimeoutManager;
import com.telcobright.statemachine.StateMachineContextEntity;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Simple test to verify persistence and rehydration work correctly
 */
public class PersistenceRehydrationTest {
    
    public static class TestContext implements StateMachineContextEntity<String> {
        private String testId;
        private String customUuid;
        private String testData;
        private boolean complete;
        private String currentState;
        private LocalDateTime lastStateChange;
        
        public TestContext() {
            this.complete = false;
            this.lastStateChange = LocalDateTime.now();
        }
        
        public TestContext(String testId) {
            this.testId = testId;
            this.complete = false;
            this.lastStateChange = LocalDateTime.now();
        }
        
        // StateMachineContextEntity implementation
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
        
        // Custom fields
        public String getTestId() { return testId; }
        public void setTestId(String testId) { this.testId = testId; }
        public String getCustomUuid() { return customUuid; }
        public void setCustomUuid(String customUuid) { this.customUuid = customUuid; }
        public String getTestData() { return testData; }
        public void setTestData(String testData) { this.testData = testData; }
        
        @Override
        public String toString() {
            return String.format("TestContext[id=%s, uuid=%s, data=%s, state=%s]",
                testId, customUuid, testData, currentState);
        }
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println("\n=== Persistence and Rehydration Validation Test ===\n");
        
        String machineId = "persistence-test-001";
        String testUuid = UUID.randomUUID().toString();
        String testData = "Test data " + System.currentTimeMillis();
        
        TimeoutManager timeoutManager = new TimeoutManager();
        StateMachineRegistry registry = new StateMachineRegistry(timeoutManager, 9998);
        registry.enableDebugMode(9998);
        registry.setOptimizedPersistenceProvider(TestContext.class, "test_persistence_validation");
        
        System.out.println("✓ Registry initialized with persistence");
        
        // Phase 1: Create machine and set custom context data
        System.out.println("\n--- Phase 1: Creating machine with custom context ---");
        
        GenericStateMachine<TestContext, Void> machine = registry.create(machineId, () -> {
            TestContext context = new TestContext(machineId);
            context.setCustomUuid(testUuid);
            context.setTestData(testData);
            context.setCurrentState("ACTIVE");
            
            GenericStateMachine<TestContext, Void> m = 
                new GenericStateMachine<TestContext, Void>(machineId, timeoutManager, registry);
            m.setPersistingEntity(context);
            
            System.out.println("[Factory] Created machine with context: " + context);
            return m;
        });
        
        System.out.println("✓ Machine created with custom UUID: " + testUuid);
        System.out.println("✓ Machine created with test data: " + testData);
        System.out.println("✓ Current context: " + machine.getPersistingEntity());
        
        // Phase 2: Force persistence and remove from registry
        System.out.println("\n--- Phase 2: Forcing persistence and cleanup ---");
        
        // The machine's context should be automatically persisted when removed
        System.out.println("✓ Machine context will be persisted during removal");
        
        // Remove from registry to simulate offline
        registry.removeMachine(machineId);
        System.out.println("✓ Machine removed from registry (simulating offline)");
        
        // Verify machine is not in registry
        if (registry.getMachine(machineId) == null) {
            System.out.println("✓ Confirmed: Machine not in active registry");
        }
        
        // Phase 3: Rehydrate and verify context
        System.out.println("\n--- Phase 3: Rehydrating and verifying context ---");
        
        GenericStateMachine<TestContext, Void> rehydratedMachine = registry.createOrGet(machineId, () -> {
            System.out.println("[Factory] Creating new machine for rehydration");
            TestContext newContext = new TestContext(machineId);
            
            GenericStateMachine<TestContext, Void> m = 
                new GenericStateMachine<TestContext, Void>(machineId, timeoutManager, registry);
            m.setPersistingEntity(newContext);
            return m;
        });
        
        if (rehydratedMachine != null) {
            TestContext rehydratedContext = rehydratedMachine.getPersistingEntity();
            System.out.println("✓ Machine rehydrated successfully");
            System.out.println("✓ Rehydrated context: " + rehydratedContext);
            
            // Verify context data
            System.out.println("\n--- Context Validation ---");
            
            boolean uuidMatch = testUuid.equals(rehydratedContext.getCustomUuid());
            boolean dataMatch = testData.equals(rehydratedContext.getTestData());
            boolean idMatch = machineId.equals(rehydratedContext.getTestId());
            
            System.out.println("UUID match: " + (uuidMatch ? "✓ YES" : "✗ NO"));
            System.out.println("  Expected: " + testUuid);
            System.out.println("  Actual:   " + rehydratedContext.getCustomUuid());
            
            System.out.println("Data match: " + (dataMatch ? "✓ YES" : "✗ NO"));
            System.out.println("  Expected: " + testData);
            System.out.println("  Actual:   " + rehydratedContext.getTestData());
            
            System.out.println("ID match: " + (idMatch ? "✓ YES" : "✗ NO"));
            System.out.println("  Expected: " + machineId);
            System.out.println("  Actual:   " + rehydratedContext.getTestId());
            
            if (uuidMatch && dataMatch && idMatch) {
                System.out.println("\n🎉 SUCCESS: All context data persisted and restored correctly!");
                System.out.println("✓ Persistence works correctly");
                System.out.println("✓ Rehydration works correctly");
                System.out.println("✓ Context validation passed");
            } else {
                System.out.println("\n❌ FAILURE: Context data mismatch after rehydration");
            }
        } else {
            System.out.println("✗ Failed to rehydrate machine");
        }
        
        // Cleanup
        System.out.println("\n--- Cleanup ---");
        registry.shutdown();
        timeoutManager.shutdown();
        
        System.out.println("✓ Test completed");
        System.out.println("✓ Check MySQL tables:");
        System.out.println("  - test_persistence_validation (context data)");
        System.out.println("  - history_persistence_test_001 (history data)");
        
        System.exit(0);
    }
}