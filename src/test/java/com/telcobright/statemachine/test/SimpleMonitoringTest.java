package com.telcobright.statemachine.test;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.events.GenericStateMachineEvent;
import com.telcobright.statemachine.monitoring.*;

import java.util.UUID;

/**
 * Simple monitoring test without external dependencies.
 * Tests the basic monitoring functionality of state machines.
 */
public class SimpleMonitoringTest {
    
    public static void main(String[] args) {
        System.out.println("🧪 Starting Simple Monitoring Test");
        
        testBasicMonitoring();
        
        System.out.println("✅ Simple monitoring test completed successfully!");
    }
    
    /**
     * Test basic monitoring functionality with a simple entity
     */
    private static void testBasicMonitoring() {
        System.out.println("\n📋 Test: Basic Monitoring");
        
        String testRunId = "monitoring-test-" + UUID.randomUUID().toString().substring(0, 8);
        
        // Create a simple test entity
        TestEntity entity = new TestEntity("test-entity-001");
        
        // Create a simple test context
        TestContext context = new TestContext("test-data");
        
        // Create a state machine with debug enabled
        GenericStateMachine<TestEntity, TestContext> machine = FluentStateMachineBuilder
            .<TestEntity, TestContext>create("simple-test-machine")
            .enableDebug() // Use default snapshot recorder
            .withRunId(testRunId)
            .withCorrelationId("test-correlation-123")
            .withDebugSessionId("debug-session-abc")
            .initialState("START")
            .finalState("END")
            
            .state("START")
                .on("BEGIN").to("PROCESSING")
            .done()
            
            .state("PROCESSING")
                .on("FINISH").to("END")
            .done()
            
            .state("END")
                .finalState()
            .done()
            
            .build();
        
        machine.setPersistingEntity(entity);
        machine.setContext(context);
        
        // Start the machine and fire events
        machine.start();
        
        System.out.println("🔥 Firing BEGIN event...");
        machine.fire(new GenericStateMachineEvent("BEGIN"));
        
        // Update context to test context change recording
        context.setTestData("updated-data");
        
        System.out.println("🔥 Firing FINISH event...");
        machine.fire(new GenericStateMachineEvent("FINISH"));
        
        // Verify final state
        System.out.println("Final state: " + machine.getCurrentState());
        System.out.println("Debug enabled: " + machine.isDebugEnabled());
        System.out.println("Machine complete: " + machine.isComplete());
        
        machine.stop();
        System.out.println("✅ Basic monitoring test completed");
    }
    
    /**
     * Simple test entity for monitoring tests
     */
    public static class TestEntity implements StateMachineContextEntity<String> {
        private String id;
        private String currentState;
        private boolean complete = false;
        
        public TestEntity(String id) {
            this.id = id;
        }
        
        @Override
        public boolean isComplete() {
            return complete;
        }
        
        @Override
        public void setComplete(boolean complete) {
            this.complete = complete;
        }
        
        public String getId() {
            return id;
        }
        
        public void setCurrentState(String currentState) {
            this.currentState = currentState;
        }
        
        public String getCurrentState() {
            return currentState;
        }
    }
    
    /**
     * Simple test context for monitoring tests
     */
    public static class TestContext {
        private String testData;
        private long timestamp = System.currentTimeMillis();
        
        public TestContext(String testData) {
            this.testData = testData;
        }
        
        public String getTestData() {
            return testData;
        }
        
        public void setTestData(String testData) {
            this.testData = testData;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
    }
}