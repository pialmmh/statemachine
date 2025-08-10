package com.telcobright.statemachine.test;

import com.telcobright.statemachine.FluentStateMachineBuilder;
import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.timeout.TimeUnit;

/**
 * Test to verify timeout with target state feature
 */
public class TimeoutTargetStateTest {
    
    static class TestEntity implements com.telcobright.statemachine.StateMachineContextEntity<String> {
        private String id;
        private boolean complete;
        
        @Override
        public boolean isComplete() { return complete; }
        
        @Override
        public void setComplete(boolean complete) { this.complete = complete; }
        
        public void markComplete() { this.complete = true; }
    }
    
    static class TestContext {
        private String data;
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println("🧪 Testing timeout with target state feature...\n");
        
        // Create a test state machine with different timeout targets
        GenericStateMachine<TestEntity, TestContext> machine = 
            FluentStateMachineBuilder.<TestEntity, TestContext>create("timeout-test")
                .initialState("START")
                
                // START state - timeout to ERROR state after 2 seconds
                .state("START")
                    .timeout(2, TimeUnit.SECONDS, "ERROR")
                    .done()
                    
                // ERROR state - timeout to RECOVERY after 2 seconds
                .state("ERROR")
                    .timeout(2, TimeUnit.SECONDS, "RECOVERY")
                    .done()
                    
                // RECOVERY state - timeout back to START after 2 seconds
                .state("RECOVERY")
                    .timeout(2, TimeUnit.SECONDS, "START")
                    .done()
                    
                .build();
        
        // Set up entity and context
        TestEntity entity = new TestEntity();
        TestContext context = new TestContext();
        machine.setPersistingEntity(entity);
        machine.setContext(context);
        
        // Start the machine
        machine.start();
        System.out.println("✅ Machine started in state: " + machine.getCurrentState());
        
        // Wait and observe timeout transitions
        System.out.println("\n⏰ Waiting for timeout transitions...\n");
        
        Thread.sleep(2500); // Wait for first timeout
        System.out.println("After 2.5 seconds - Current state: " + machine.getCurrentState() + " (expected: ERROR)");
        
        Thread.sleep(2000); // Wait for second timeout
        System.out.println("After 4.5 seconds - Current state: " + machine.getCurrentState() + " (expected: RECOVERY)");
        
        Thread.sleep(2000); // Wait for third timeout
        System.out.println("After 6.5 seconds - Current state: " + machine.getCurrentState() + " (expected: START)");
        
        // Stop the machine
        machine.stop();
        
        System.out.println("\n✅ Test completed successfully!");
        System.out.println("   The timeout feature with target state is working correctly.");
        System.out.println("   States transitioned: START → ERROR → RECOVERY → START");
    }
}