package com.telcobright.statemachine.websocket;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.events.EventTypeRegistry;
import com.telcobright.statemachineexamples.callmachine.CallContext;
import com.telcobright.statemachineexamples.callmachine.CallState;
import com.telcobright.statemachineexamples.callmachine.events.*;

/**
 * Test timeout functionality
 */
public class TestTimeout {
    public static void main(String[] args) throws Exception {
        System.out.println("=== Testing Timeout Functionality ===\n");
        
        // Register event types
        EventTypeRegistry.register(IncomingCall.class, "INCOMING_CALL");
        EventTypeRegistry.register(Answer.class, "ANSWER");
        EventTypeRegistry.register(Hangup.class, "HANGUP");
        EventTypeRegistry.register(SessionProgress.class, "SESSION_PROGRESS");
        
        // Create timeout manager
        com.telcobright.statemachine.timeout.TimeoutManager timeoutManager = 
            new com.telcobright.statemachine.timeout.TimeoutManager();
        
        // Create registry
        StateMachineRegistry registry = new StateMachineRegistry(timeoutManager, 9998);
        
        // Set factory defaults
        StateMachineFactory.setDefaultInstances(timeoutManager, registry);
        
        // Create a test machine with 5 second timeout for quick testing
        String machineId = "test-timeout";
        CallContext context = new CallContext(machineId, "+1-555-0001", "+1-555-1001");
        
        GenericStateMachine<CallContext, Void> machine = FluentStateMachineBuilder.<CallContext, Void>create(machineId)
            .initialState(CallState.IDLE)
            
            .state(CallState.IDLE)
                .on(IncomingCall.class).to(CallState.RINGING)
                .done()
                
            .state(CallState.RINGING)
                .timeout(5, com.telcobright.statemachine.timeout.TimeUnit.SECONDS, CallState.IDLE.name())
                .on(Answer.class).to(CallState.CONNECTED)
                .on(Hangup.class).to(CallState.IDLE)
                .done()
                
            .state(CallState.CONNECTED)
                .on(Hangup.class).to(CallState.IDLE)
                .done()
            .build();
        
        machine.setPersistingEntity(context);
        machine.start();
        
        System.out.println("Machine started in state: " + machine.getCurrentState());
        System.out.println();
        
        // Fire INCOMING_CALL event to transition to RINGING
        System.out.println(">>> Firing INCOMING_CALL event...");
        machine.fire(new IncomingCall("call-from-test"));
        
        System.out.println("Current state: " + machine.getCurrentState());
        System.out.println("\n⏳ Waiting 7 seconds for timeout to trigger (timeout is set to 5 seconds)...\n");
        
        // Wait for timeout
        Thread.sleep(7000);
        
        System.out.println("\n=== After waiting ===");
        System.out.println("Final state: " + machine.getCurrentState());
        
        if (machine.getCurrentState().equals("IDLE")) {
            System.out.println("✅ SUCCESS: Timeout worked! Machine transitioned back to IDLE");
        } else {
            System.out.println("❌ FAILURE: Timeout did not work. Machine is still in " + machine.getCurrentState());
        }
        
        // Cleanup
        timeoutManager.shutdown();
        System.exit(0);
    }
}