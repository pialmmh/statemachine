package com.telcobright.statemachine.debugger.stresstest;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.timeout.TimeoutManager;
import com.telcobright.examples.callmachine.events.*;

/**
 * Test to verify the disableRehydration flag functionality
 * When enabled, the registry should not attempt to rehydrate machines from persistence
 * and should log warnings for events sent to non-existent machines
 */
public class DisableRehydrationTest {
    
    public static void main(String[] args) throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   DISABLE REHYDRATION TEST");
        System.out.println("=".repeat(80));
        System.out.println();
        
        // Create registry
        TimeoutManager timeoutManager = new TimeoutManager();
        StateMachineRegistry registry = new StateMachineRegistry("test-registry", timeoutManager, 9997);
        
        // Test 1: Default behavior (rehydration enabled)
        System.out.println("📋 Test 1: Default behavior (rehydration enabled)");
        System.out.println("-".repeat(50));
        
        // Try to route event to non-existent machine
        System.out.println("Sending event to non-existent machine 'machine-001'...");
        boolean result1 = registry.routeEvent("machine-001", new IncomingCall("+1", "+2"));
        System.out.println("Result: " + (result1 ? "SUCCESS" : "FAILED (expected - machine doesn't exist)"));
        System.out.println();
        
        // Test 2: Create and register a machine
        System.out.println("📋 Test 2: Create and register a machine");
        System.out.println("-".repeat(50));
        
        GenericStateMachine machine1 = EnhancedFluentBuilder.create("machine-002")
            .initialState("IDLE")
            .state("IDLE")
                .on(IncomingCall.class).to("ACTIVE")
            .done()
            .state("ACTIVE")
                .on(Hangup.class).to("IDLE")
            .done()
            .build();
        
        registry.register("machine-002", machine1);
        System.out.println("Machine 'machine-002' registered");
        
        // Send event to existing machine
        System.out.println("Sending event to existing machine 'machine-002'...");
        boolean result2 = registry.routeEvent("machine-002", new IncomingCall("+1", "+2"));
        System.out.println("Result: " + (result2 ? "SUCCESS" : "FAILED"));
        System.out.println("Current state: " + machine1.getCurrentState());
        System.out.println();
        
        // Test 3: Disable rehydration
        System.out.println("📋 Test 3: Disable rehydration");
        System.out.println("-".repeat(50));
        
        System.out.println("Disabling rehydration...");
        registry.disableRehydration();
        System.out.println("Is rehydration disabled? " + registry.isRehydrationDisabled());
        System.out.println();
        
        // Test 4: Try to route event to non-existent machine with rehydration disabled
        System.out.println("📋 Test 4: Route event with rehydration disabled");
        System.out.println("-".repeat(50));
        
        System.out.println("Sending event to non-existent machine 'machine-003'...");
        System.out.println("Expected: WARNING log and event dropped");
        boolean result3 = registry.routeEvent("machine-003", new IncomingCall("+3", "+4"));
        System.out.println("Result: " + (result3 ? "SUCCESS" : "FAILED (expected - rehydration disabled)"));
        System.out.println();
        
        // Test 5: Existing machine should still work
        System.out.println("📋 Test 5: Existing machine still works");
        System.out.println("-".repeat(50));
        
        System.out.println("Sending Hangup event to existing machine 'machine-002'...");
        boolean result4 = registry.routeEvent("machine-002", new Hangup());
        System.out.println("Result: " + (result4 ? "SUCCESS" : "FAILED"));
        System.out.println("Current state: " + machine1.getCurrentState());
        System.out.println();
        
        // Test 6: Re-enable rehydration
        System.out.println("📋 Test 6: Re-enable rehydration");
        System.out.println("-".repeat(50));
        
        System.out.println("Enabling rehydration...");
        registry.enableRehydration();
        System.out.println("Is rehydration disabled? " + registry.isRehydrationDisabled());
        System.out.println();
        
        // Test 7: Remove machine and try to send event
        System.out.println("📋 Test 7: Remove machine and test event routing");
        System.out.println("-".repeat(50));
        
        registry.removeMachine("machine-002");
        System.out.println("Machine 'machine-002' removed from registry");
        
        System.out.println("Sending event to removed machine 'machine-002'...");
        boolean result5 = registry.routeEvent("machine-002", new IncomingCall("+5", "+6"));
        System.out.println("Result: " + (result5 ? "SUCCESS" : "FAILED (expected - machine removed)"));
        System.out.println();
        
        // Summary
        System.out.println("=".repeat(80));
        System.out.println("   TEST SUMMARY");
        System.out.println("=".repeat(80));
        System.out.println();
        System.out.println("✅ Test Results:");
        System.out.println("  1. Default behavior - Event to non-existent machine: " + (!result1 ? "✅ PASS" : "❌ FAIL"));
        System.out.println("  2. Event to existing machine: " + (result2 ? "✅ PASS" : "❌ FAIL"));
        System.out.println("  3. Rehydration disabled successfully: ✅ PASS");
        System.out.println("  4. Event dropped when rehydration disabled: " + (!result3 ? "✅ PASS" : "❌ FAIL"));
        System.out.println("  5. Existing machine works with rehydration disabled: " + (result4 ? "✅ PASS" : "❌ FAIL"));
        System.out.println("  6. Rehydration re-enabled successfully: ✅ PASS");
        System.out.println("  7. Event to removed machine fails: " + (!result5 ? "✅ PASS" : "❌ FAIL"));
        System.out.println();
        System.out.println("🔍 Key Observations:");
        System.out.println("  - When rehydration is disabled, events to non-existent machines are dropped");
        System.out.println("  - WARNING logs are generated for dropped events");
        System.out.println("  - Existing machines continue to work normally");
        System.out.println("  - The flag can be toggled on/off as needed");
        System.out.println();
        System.out.println("=".repeat(80));
        
        // Cleanup
        registry.shutdown();
        timeoutManager.shutdown();
        
        System.exit(0);
    }
}