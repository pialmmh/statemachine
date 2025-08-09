package com.telcobright.statemachine.test;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.StateMachineRegistry;
import com.telcobright.statemachine.FluentStateMachineBuilder;
import com.telcobright.statemachine.StateMachineContextEntity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Simple Registry-Based Test Runner
 * Demonstrates proper registry-based usage patterns
 */
public class SimpleTestRunner {
    
    private static final String BANNER = """
        ╔═══════════════════════════════════════════════════════════════╗
        ║           🎯 TELCOBRIGHT STATE MACHINE DEMO                   ║
        ║                Registry-Based Test Runner                     ║
        ╚═══════════════════════════════════════════════════════════════╝
        """;
    
    // Simple test entities for demonstration
    public static class SimpleEntity implements StateMachineContextEntity<String> {
        private String id;
        private String currentState;
        private boolean isComplete = false;
        
        public SimpleEntity(String id) {
            this.id = id;
            this.currentState = "INITIAL";
        }
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getCurrentState() { return currentState; }
        public void setCurrentState(String currentState) { this.currentState = currentState; }
        
        @Override
        public boolean isComplete() { return isComplete; }
        
        @Override
        public void setComplete(boolean complete) { this.isComplete = complete; }
        
        @Override
        public String toString() {
            return String.format("SimpleEntity{id='%s', state='%s', complete=%s}", id, currentState, isComplete);
        }
    }
    
    public static class SimpleContext {
        private String contextId;
        private int stepCount = 0;
        
        public SimpleContext(String contextId) {
            this.contextId = contextId;
        }
        
        public String getContextId() { return contextId; }
        public int getStepCount() { return stepCount; }
        public void incrementStepCount() { this.stepCount++; }
        
        @Override
        public String toString() {
            return String.format("SimpleContext{id='%s', steps=%d}", contextId, stepCount);
        }
    }
    
    private int testsRun = 0;
    private int testsPassed = 0;
    private int testsFailed = 0;
    
    public static void main(String[] args) {
        SimpleTestRunner runner = new SimpleTestRunner();
        runner.runDemoTests();
    }
    
    public void runDemoTests() {
        System.out.println(BANNER);
        System.out.println("🚀 Starting registry-based state machine demonstrations");
        System.out.println("Started at: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println("═".repeat(80));
        
        testRegistryBasics();
        testPerformanceOptimization();
        testCompletionHandling();
        
        System.out.println("\n" + "═".repeat(80));
        System.out.println("🏁 === DEMO SUMMARY ===");
        System.out.printf("📊 Total tests: %d | ✅ Passed: %d | ❌ Failed: %d%n", testsRun, testsPassed, testsFailed);
        System.out.printf("🎯 Success rate: %.1f%%%n", (double) testsPassed / testsRun * 100);
        System.out.println("Completed at: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }
    
    private void testRegistryBasics() {
        System.out.println("\n🏛️ === REGISTRY BASICS TEST ===");
        
        try {
            StateMachineRegistry registry = new StateMachineRegistry();
            
            // Test 1: Create new state machine
            System.out.println("🧪 Test 1: Registry create() method");
            GenericStateMachine<SimpleEntity, SimpleContext> machine = 
                registry.create("test-001", () -> {
                    return FluentStateMachineBuilder.<SimpleEntity, SimpleContext>create("test-001")
                        .initialState("START")
                        .state("START").done()
                        .state("END").finalState().done()
                        .build();
                });
            
            SimpleEntity entity = new SimpleEntity("entity-001");
            SimpleContext context = new SimpleContext("context-001");
            machine.setPersistingEntity(entity);
            machine.setContext(context);
            machine.start();
            
            assertTest("Registry create() works", machine != null);
            assertTest("Registry size is 1", registry.size() == 1);
            assertTest("Machine is in memory", registry.isInMemory("test-001"));
            
            // Test 2: Retrieve existing machine
            System.out.println("🧪 Test 2: Registry createOrGet() existing");
            GenericStateMachine<SimpleEntity, SimpleContext> existing = 
                registry.createOrGet("test-001", () -> null);
            
            assertTest("Retrieved same instance", machine == existing);
            assertTest("Registry size unchanged", registry.size() == 1);
            
            System.out.println("✅ Registry basics tests completed");
            
        } catch (Exception e) {
            System.out.println("❌ Registry basics test failed: " + e.getMessage());
            testsFailed++;
        }
    }
    
    private void testPerformanceOptimization() {
        System.out.println("\n⚡ === PERFORMANCE OPTIMIZATION TEST ===");
        
        try {
            StateMachineRegistry registry = new StateMachineRegistry();
            
            // Test: Multiple new creations
            System.out.println("🧪 Test: Multiple new machine creation performance");
            for (int i = 1; i <= 3; i++) {
                String id = "perf-" + i;
                GenericStateMachine<SimpleEntity, SimpleContext> machine = 
                    registry.create(id, () -> {
                        return FluentStateMachineBuilder.<SimpleEntity, SimpleContext>create(id)
                            .initialState("PENDING")
                            .state("PENDING").done()
                            .state("ACTIVE").done()
                            .build();
                    });
                
                SimpleEntity entity = new SimpleEntity("entity-" + i);
                machine.setPersistingEntity(entity);
                machine.setContext(new SimpleContext("context-" + i));
                machine.start();
            }
            
            assertTest("Created 3 machines efficiently", registry.size() == 3);
            assertTest("All machines in memory", 
                registry.isInMemory("perf-1") && 
                registry.isInMemory("perf-2") && 
                registry.isInMemory("perf-3"));
            
            System.out.println("✅ Performance optimization tests completed");
            
        } catch (Exception e) {
            System.out.println("❌ Performance test failed: " + e.getMessage());
            testsFailed++;
        }
    }
    
    private void testCompletionHandling() {
        System.out.println("\n🏁 === COMPLETION HANDLING TEST ===");
        
        try {
            StateMachineRegistry registry = new StateMachineRegistry();
            
            // Test completion optimization
            System.out.println("🧪 Test: Completion prevents rehydration");
            GenericStateMachine<SimpleEntity, SimpleContext> machine = 
                registry.createOrGet("completed-test", 
                    () -> {
                        System.out.println("   🔄 Factory should NOT be called");
                        return null;
                    },
                    (id) -> {
                        // Simulate completed entity
                        SimpleEntity completedEntity = new SimpleEntity(id);
                        completedEntity.setComplete(true);
                        return completedEntity;
                    });
            
            assertTest("Completed machine not rehydrated", machine == null);
            assertTest("Registry remains empty", registry.size() == 0);
            
            System.out.println("✅ Completion handling tests completed");
            
        } catch (Exception e) {
            System.out.println("❌ Completion test failed: " + e.getMessage());
            testsFailed++;
        }
    }
    
    private void assertTest(String testName, boolean condition) {
        testsRun++;
        if (condition) {
            System.out.println("   ✅ " + testName);
            testsPassed++;
        } else {
            System.out.println("   ❌ " + testName);
            testsFailed++;
        }
    }
}