package com.telcobright.statemachine.extendedtest;

import com.telcobright.core.*;
import com.telcobright.core.timeout.TimeoutManager;
import com.telcobright.debugger.CallMachineRunnerEnhanced;
import com.telcobright.examples.callmachine.events.*;
import com.telcobright.examples.callmachine.CallState;
import com.telcobright.core.events.EventTypeRegistry;

/**
 * Simple test to verify state transitions work correctly
 */
public class SimpleTransitionTest {
    
    public static void main(String[] args) throws Exception {
        System.out.println("🧪 Simple Transition Test");
        System.out.println("=========================");
        
        // Register event types first
        EventTypeRegistry.register(IncomingCall.class, "INCOMING_CALL");
        EventTypeRegistry.register(Answer.class, "ANSWER");  
        EventTypeRegistry.register(Hangup.class, "HANGUP");
        System.out.println("[Init] Registered event types");
        
        // Create timeout manager and registry
        TimeoutManager timeoutManager = new TimeoutManager();
        StateMachineRegistry registry = new StateMachineRegistry("simple_test", timeoutManager, 19998);
        
        // Create contexts with unique ID to avoid rehydration
        String uniqueId = "fresh-test-" + System.currentTimeMillis();
        CallMachineRunnerEnhanced.CallPersistentContext persistentContext = 
            new CallMachineRunnerEnhanced.CallPersistentContext(uniqueId, 
                "+1-555-1234", "+1-555-5678");
        CallMachineRunnerEnhanced.CallVolatileContext volatileContext = 
            new CallMachineRunnerEnhanced.CallVolatileContext();
        
        System.out.println("📋 Building simple state machine...");
        System.out.println("   States: " + CallState.ADMISSION.name() + " -> " + CallState.RINGING.name() + " -> " + CallState.HUNGUP.name());
        
        // Build simple machine using exact same pattern as CallMachineRunnerEnhanced
        GenericStateMachine<CallMachineRunnerEnhanced.CallPersistentContext, CallMachineRunnerEnhanced.CallVolatileContext> machine = 
            EnhancedFluentBuilder.<CallMachineRunnerEnhanced.CallPersistentContext, CallMachineRunnerEnhanced.CallVolatileContext>create(uniqueId)
                .withPersistentContext(persistentContext)
                .withVolatileContext(volatileContext)
                .withVolatileContextFactory(() -> CallMachineRunnerEnhanced.CallVolatileContext.createFromPersistent(persistentContext))
                .withSampleLogging(1000) // Disable sampling with high number
                .initialState(CallState.ADMISSION.name())
                .state(CallState.ADMISSION.name())
                    .onEntry(() -> {
                        System.out.println("🔵 Entered IDLE state");
                    })
                    .onExit(() -> {
                        System.out.println("🔵 Exiting IDLE state");
                    })
                    .on(IncomingCall.class).to(CallState.RINGING.name())
                    .done()
                .state(CallState.RINGING.name())
                    .onEntry(() -> {
                        System.out.println("🔔 Entered RINGING state - Call is ringing!");
                    })
                    .onExit(() -> {
                        System.out.println("🔔 Exiting RINGING state");
                    })
                    .on(Hangup.class).to(CallState.HUNGUP.name())
                    .done()
                .state(CallState.HUNGUP.name())
                    .onEntry(() -> {
                        System.out.println("📵 Entered HUNGUP state - Call ended");
                    })
                    .finalState() // Mark as final state like in working example
                    .done()
                .build();
        
        // Register and start
        registry.register(uniqueId, machine);
        machine.start();
        
        System.out.println("✅ Machine created and started");
        System.out.println("🏃 Current state: " + machine.getCurrentState());
        
        // Test transition 1: IDLE -> RINGING
        System.out.println("\n📞 Sending IncomingCall event...");
        boolean sent1 = registry.sendEvent(uniqueId, new IncomingCall());
        System.out.println("   Event sent: " + sent1);
        Thread.sleep(500); // Allow processing time
        System.out.println("   Current state: " + machine.getCurrentState());
        
        // Test transition 2: RINGING -> HUNGUP
        System.out.println("\n📵 Sending Hangup event...");
        boolean sent2 = registry.sendEvent(uniqueId, new Hangup());
        System.out.println("   Event sent: " + sent2);
        Thread.sleep(500); // Allow processing time  
        System.out.println("   Current state: " + machine.getCurrentState());
        
        // Test invalid transition: HUNGUP -> anything
        System.out.println("\n🚫 Sending IncomingCall to HUNGUP machine (should be ignored)...");
        boolean sent3 = registry.sendEvent(uniqueId, new IncomingCall());
        System.out.println("   Event sent: " + sent3);
        Thread.sleep(500);
        System.out.println("   Current state: " + machine.getCurrentState() + " (should still be HUNGUP)");
        
        // Results
        String finalState = machine.getCurrentState();
        boolean testPassed = CallState.HUNGUP.name().equals(finalState);
        
        System.out.println("\n📊 Test Results:");
        System.out.println("   Final state: " + finalState);
        System.out.println("   Expected: " + CallState.HUNGUP.name());
        System.out.println("   Test result: " + (testPassed ? "✅ PASSED" : "❌ FAILED"));
        
        // Cleanup
        registry.shutdownAsyncLogging();
        timeoutManager.shutdown();
        
        if (!testPassed) {
            System.exit(1);
        }
    }
}