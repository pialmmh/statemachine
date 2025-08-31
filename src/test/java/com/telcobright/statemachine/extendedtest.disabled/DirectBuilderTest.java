package com.telcobright.statemachine.extendedtest;

import com.telcobright.core.*;
import com.telcobright.core.timeout.TimeoutManager;
import com.telcobright.debugger.CallMachineRunnerEnhanced;
import com.telcobright.examples.callmachine.events.*;
import com.telcobright.examples.callmachine.CallState;
import com.telcobright.core.events.EventTypeRegistry;

/**
 * Test using FluentStateMachineBuilder directly instead of EnhancedFluentBuilder
 * to isolate if the issue is in the EnhancedFluentBuilder delegation
 */
public class DirectBuilderTest {
    
    public static void main(String[] args) throws Exception {
        System.out.println("🔧 Direct FluentStateMachineBuilder Test");
        System.out.println("=======================================");
        
        // Register event types first
        EventTypeRegistry.register(IncomingCall.class, "INCOMING_CALL");
        EventTypeRegistry.register(Answer.class, "ANSWER");  
        EventTypeRegistry.register(Hangup.class, "HANGUP");
        System.out.println("[Init] Registered event types");
        
        // Create timeout manager and registry
        TimeoutManager timeoutManager = new TimeoutManager();
        StateMachineRegistry registry = new StateMachineRegistry("direct_test", timeoutManager, 19997);
        
        // Create contexts
        String uniqueId = "direct-test-" + System.currentTimeMillis();
        CallMachineRunnerEnhanced.CallPersistentContext persistentContext = 
            new CallMachineRunnerEnhanced.CallPersistentContext(uniqueId, 
                "+1-555-1234", "+1-555-5678");
        CallMachineRunnerEnhanced.CallVolatileContext volatileContext = 
            new CallMachineRunnerEnhanced.CallVolatileContext();
        
        System.out.println("📋 Building with direct FluentStateMachineBuilder...");
        
        // Use FluentStateMachineBuilder directly
        GenericStateMachine<CallMachineRunnerEnhanced.CallPersistentContext, CallMachineRunnerEnhanced.CallVolatileContext> machine = 
            FluentStateMachineBuilder.<CallMachineRunnerEnhanced.CallPersistentContext, CallMachineRunnerEnhanced.CallVolatileContext>create(uniqueId)
                .initialState(CallState.ADMISSION.name())
                .state(CallState.ADMISSION.name())
                    .onEntry(() -> {
                        System.out.println("🔵 ✅ ENTERED IDLE state");
                    })
                    .onExit(() -> {
                        System.out.println("🔵 ⬅️ EXITING IDLE state");
                    })
                    .on(IncomingCall.class).to(CallState.RINGING.name())
                    .done()
                .state(CallState.RINGING.name())
                    .onEntry(() -> {
                        System.out.println("🔔 ✅ ENTERED RINGING state - Call is ringing!");
                    })
                    .onExit(() -> {
                        System.out.println("🔔 ⬅️ EXITING RINGING state");
                    })
                    .on(Hangup.class).to(CallState.HUNGUP.name())
                    .done()
                .state(CallState.HUNGUP.name())
                    .onEntry(() -> {
                        System.out.println("📵 ✅ ENTERED HUNGUP state - Call ended");
                    })
                    .finalState()
                    .done()
                .build();
        
        // Set contexts manually
        machine.setPersistingEntity(persistentContext);
        machine.setContext(volatileContext);
        
        // Register and start
        registry.register(uniqueId, machine);
        machine.start();
        
        System.out.println("✅ Machine created and started with direct builder");
        System.out.println("🏃 Current state: " + machine.getCurrentState());
        
        // Test transition 1: IDLE -> RINGING
        System.out.println("\n📞 Sending IncomingCall event...");
        boolean sent1 = registry.sendEvent(uniqueId, new IncomingCall());
        System.out.println("   Event sent: " + sent1);
        Thread.sleep(500);
        System.out.println("   Current state: " + machine.getCurrentState());
        
        // Test transition 2: RINGING -> HUNGUP
        System.out.println("\n📵 Sending Hangup event...");
        boolean sent2 = registry.sendEvent(uniqueId, new Hangup());
        System.out.println("   Event sent: " + sent2);
        Thread.sleep(500);
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
        System.out.println("   Transitions worked: " + (sent1 && sent2));
        System.out.println("   Final event ignored: " + !sent3);
        System.out.println("   Test result: " + (testPassed ? "✅ PASSED" : "❌ FAILED"));
        
        // Cleanup
        registry.shutdownAsyncLogging();
        timeoutManager.shutdown();
        
        if (!testPassed) {
            System.exit(1);
        }
    }
}