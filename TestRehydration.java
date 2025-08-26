package com.telcobright.statemachine.test;

import com.telcobright.statemachine.StateMachineRegistry;
import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.websocket.CallMachineRunnerEnhanced;
import com.telcobright.statemachine.websocket.CallMachineRunnerEnhanced.CallPersistentContext;
import com.telcobright.statemachine.websocket.CallMachineRunnerEnhanced.CallVolatileContext;
import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachine.timeout.TimeoutManager;

public class TestRehydration {
    public static void main(String[] args) throws Exception {
        System.out.println("\n=== Testing State Machine Rehydration with Context Validation ===\n");
        
        // Create timeout manager and registry
        TimeoutManager timeoutManager = new TimeoutManager();
        StateMachineRegistry registry = new StateMachineRegistry(timeoutManager, 9998); // Different port
        registry.setDebugMode(true);
        
        // Initialize persistence
        registry.setOptimizedPersistenceProvider(CallPersistentContext.class, "call_persistent_context");
        
        System.out.println("1. Creating initial state machine 'test-001'...");
        
        // Create a state machine
        GenericStateMachine<CallPersistentContext, CallVolatileContext> machine = 
            CallMachineRunnerEnhanced.createCallMachine("test-001", 
                new CallPersistentContext("test-001", "+1-555-0001", "+1-555-1001"),
                timeoutManager);
        
        registry.register("test-001", machine);
        
        System.out.println("   State: " + machine.getCurrentState());
        
        // Send an event to move to RINGING
        System.out.println("\n2. Sending INCOMING_CALL event...");
        StateMachineEvent incomingCall = new StateMachineEvent();
        incomingCall.setEventType("INCOMING_CALL");
        incomingCall.setPayload(new java.util.HashMap<String, Object>() {{
            put("caller", "+1234567890");
        }});
        machine.fire(incomingCall);
        
        System.out.println("   State after INCOMING_CALL: " + machine.getCurrentState());
        
        // Send ANSWER to move to CONNECTED
        System.out.println("\n3. Sending ANSWER event...");
        StateMachineEvent answer = new StateMachineEvent();
        answer.setEventType("ANSWER");
        machine.fire(answer);
        
        System.out.println("   State after ANSWER: " + machine.getCurrentState());
        
        // Wait for timeout to trigger offline transition
        System.out.println("\n4. Waiting for timeout (31 seconds) to trigger offline transition...");
        Thread.sleep(31000);
        
        // Check if machine is still in active registry
        boolean isActive = registry.isActive("test-001");
        System.out.println("\n5. Machine 'test-001' is " + (isActive ? "ACTIVE" : "OFFLINE"));
        
        if (!isActive) {
            System.out.println("   ✓ Machine correctly moved to offline state");
            
            // Try to get the machine - should return null
            GenericStateMachine<?, ?> offlineMachine = registry.getMachine("test-001");
            if (offlineMachine == null) {
                System.out.println("   ✓ getMachine() correctly returned null for offline machine");
            } else {
                System.out.println("   ✗ ERROR: getMachine() returned machine instead of null!");
            }
            
            // Now rehydrate the machine by sending an event
            System.out.println("\n6. Rehydrating machine by sending INCOMING_CALL...");
            
            try {
                // This should trigger rehydration
                CallPersistentContext rehydratedContext = registry.rehydrateMachine(
                    "test-001",
                    CallPersistentContext.class,
                    () -> new CallPersistentContext("test-001", "+1-555-0001", "+1-555-1001"),
                    (context) -> {
                        GenericStateMachine<CallPersistentContext, CallVolatileContext> newMachine = 
                            CallMachineRunnerEnhanced.createCallMachine("test-001", context, timeoutManager);
                        return newMachine;
                    }
                );
                
                System.out.println("   ✓ Machine successfully rehydrated");
                System.out.println("   Rehydrated state: " + rehydratedContext.getCurrentState());
                System.out.println("   Call ID: " + rehydratedContext.getCallId());
                
                // Check if machine is back in active registry
                if (registry.isActive("test-001")) {
                    System.out.println("   ✓ Machine is back in active registry");
                }
                
            } catch (IllegalStateException e) {
                System.out.println("   ✗ Context validation failed: " + e.getMessage());
                System.out.println("   This is expected if contexts didn't match!");
            }
        }
        
        System.out.println("\n7. Test completed");
        
        // Shutdown
        registry.shutdown();
        timeoutManager.shutdown();
        
        System.exit(0);
    }
}