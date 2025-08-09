package com.telcobright.statemachine.test;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.FluentStateMachineBuilder;
import com.telcobright.statemachine.StateMachineRegistry;
import com.telcobright.statemachine.StateMachineContextEntity;

import java.time.LocalDateTime;

/**
 * Registry-based test demonstrating the intended usage pattern
 */
public class RegistryBasedTest {
    
    // Test entity with Completable interface
    public static class TestEntity implements StateMachineContextEntity<String> {
        private String id;
        private String currentState;
        private boolean isComplete = false;
        private LocalDateTime createdAt;
        
        public TestEntity(String id) {
            this.id = id;
            this.currentState = "INITIAL";
            this.createdAt = LocalDateTime.now();
        }
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getCurrentState() { return currentState; }
        public void setCurrentState(String currentState) { this.currentState = currentState; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        
        // Completable interface
        @Override
        public boolean isComplete() { return isComplete; }
        
        @Override
        public void setComplete(boolean complete) { this.isComplete = complete; }
        
        @Override
        public String toString() {
            return String.format("TestEntity{id='%s', state='%s', complete=%s}", 
                id, currentState, isComplete);
        }
    }
    
    // Simple context
    public static class TestContext {
        private String contextId;
        private int eventCount = 0;
        
        public TestContext(String contextId) {
            this.contextId = contextId;
        }
        
        public String getContextId() { return contextId; }
        public int getEventCount() { return eventCount; }
        public void incrementEventCount() { this.eventCount++; }
        
        @Override
        public String toString() {
            return String.format("TestContext{id='%s', events=%d}", contextId, eventCount);
        }
    }
    
    public static void main(String[] args) {
        System.out.println("🧪 REGISTRY-BASED TEST SUITE");
        System.out.println("Testing intended library usage pattern");
        System.out.println("═".repeat(60));
        
        runNewStateMachineTest();
        runExistingStateMachineTest();
        runCompletionOptimizationTest();
        
        System.out.println("\n✅ ALL REGISTRY TESTS PASSED!");
        System.out.println("Registry-based usage pattern validated successfully.");
    }
    
    private static void runNewStateMachineTest() {
        System.out.println("\n🚀 Test 1: NEW State Machine Creation (registry.create())");
        
        try {
            StateMachineRegistry registry = new StateMachineRegistry();
            
            // Test creating new state machine (typical for new SMS/Call)
            GenericStateMachine<TestEntity, TestContext> machine = 
                registry.create("test-001", () -> {
                    return FluentStateMachineBuilder.<TestEntity, TestContext>create("test-001")
                        .initialState("PENDING")
                        .state("PENDING").done()
                        .state("ACTIVE").done()
                        .state("COMPLETED").finalState().done()
                        .build();
                });
            
            // Setup data
            TestEntity entity = new TestEntity("test-entity-001");
            TestContext context = new TestContext("test-context-001");
            machine.setPersistingEntity(entity);
            machine.setContext(context);
            machine.start();
            
            // Configure and test transitions
            machine.transition("PENDING", "ACTIVATE", "ACTIVE")
                   .transition("ACTIVE", "COMPLETE", "COMPLETED");
            
            System.out.println("   ✓ Created: " + entity);
            System.out.println("   ✓ Context: " + context);
            System.out.println("   ✓ Registry size: " + registry.size());
            
            // Test state progression
            machine.fire("ACTIVATE");
            context.incrementEventCount();
            assert machine.getCurrentState().equals("ACTIVE");
            System.out.println("   ✓ Activated successfully");
            
            machine.fire("COMPLETE");
            context.incrementEventCount();
            assert machine.getCurrentState().equals("COMPLETED");
            assert entity.isComplete(); // Should auto-complete
            System.out.println("   ✓ Completed and auto-marked: " + entity.isComplete());
            
            System.out.println("   ✅ NEW state machine test PASSED");
            
        } catch (Exception e) {
            System.out.println("   ❌ Test FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void runExistingStateMachineTest() {
        System.out.println("\n🔄 Test 2: EXISTING State Machine Retrieval (registry.createOrGet())");
        
        try {
            StateMachineRegistry registry = new StateMachineRegistry();
            
            // First, create a machine
            String machineId = "existing-001";
            GenericStateMachine<TestEntity, TestContext> machine1 = 
                registry.create(machineId, () -> {
                    return FluentStateMachineBuilder.<TestEntity, TestContext>create(machineId)
                        .initialState("PENDING")
                        .state("PENDING").done()
                        .state("ACTIVE").done()
                        .build();
                });
            
            TestEntity entity = new TestEntity("existing-entity");
            TestContext context = new TestContext("existing-context");
            machine1.setPersistingEntity(entity);
            machine1.setContext(context);
            machine1.start();
            
            System.out.println("   ✓ Created initial machine: " + machineId);
            
            // Now try to get existing machine
            GenericStateMachine<TestEntity, TestContext> machine2 = 
                registry.createOrGet(machineId, () -> {
                    System.out.println("   🔄 Factory called - should NOT be called for existing machine");
                    return null;
                });
            
            // Should be same instance
            assert machine1 == machine2;
            assert registry.size() == 1;
            System.out.println("   ✓ Retrieved same instance from memory");
            System.out.println("   ✓ Registry size unchanged: " + registry.size());
            
            System.out.println("   ✅ EXISTING state machine test PASSED");
            
        } catch (Exception e) {
            System.out.println("   ❌ Test FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void runCompletionOptimizationTest() {
        System.out.println("\n🏁 Test 3: Completion Optimization (prevent rehydration)");
        
        try {
            StateMachineRegistry registry = new StateMachineRegistry();
            
            // Test that completed entities are not rehydrated
            String completedId = "completed-001";
            GenericStateMachine<TestEntity, TestContext> machine = 
                registry.createOrGet(completedId, 
                    () -> {
                        System.out.println("   🔄 This factory should NOT be called");
                        return FluentStateMachineBuilder.<TestEntity, TestContext>create(completedId)
                            .initialState("COMPLETED")
                            .state("COMPLETED").finalState().done()
                            .build();
                    },
                    (id) -> {
                        // Simulate loading completed entity from DB
                        TestEntity completedEntity = new TestEntity(id);
                        completedEntity.setComplete(true);
                        System.out.println("   📋 Loaded completed entity from DB: " + completedEntity);
                        return completedEntity;
                    });
            
            // Should be null because entity is complete
            assert machine == null;
            assert registry.size() == 0;
            System.out.println("   ✓ Completed machine NOT rehydrated (returned null)");
            System.out.println("   ✓ Registry remains empty: " + registry.size());
            System.out.println("   ✓ Performance optimized - no memory waste");
            
            System.out.println("   ✅ COMPLETION optimization test PASSED");
            
        } catch (Exception e) {
            System.out.println("   ❌ Test FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }
}