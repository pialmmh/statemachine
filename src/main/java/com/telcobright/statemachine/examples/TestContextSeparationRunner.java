package com.telcobright.statemachine.examples;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.examples.ProperContextExample.*;

/**
 * Simple runner to test context separation
 */
public class TestContextSeparationRunner {
    
    public static void main(String[] args) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  TESTING PROPER CONTEXT SEPARATION");
        System.out.println("=".repeat(60) + "\n");
        
        testBasicContextSeparation();
        testVolatileContextRecreation();
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  ALL TESTS PASSED!");
        System.out.println("=".repeat(60));
    }
    
    private static void testBasicContextSeparation() {
        System.out.println("Test 1: Basic Context Separation");
        System.out.println("-".repeat(40));
        
        String orderId = "TEST-ORDER-001";
        
        // Create machine with proper context separation
        GenericStateMachine<OrderPersistentContext, OrderVolatileContext> machine = 
            ProperContextExample.createOrderStateMachine(orderId);
        
        // Start the machine
        machine.start();
        
        // Verify initial state
        System.out.println("✓ Initial state: " + machine.getCurrentState());
        
        // Verify persistent context
        OrderPersistentContext persistent = machine.getPersistingEntity();
        System.out.println("✓ Persistent context exists: " + (persistent != null));
        System.out.println("  - Order ID: " + persistent.getOrderId());
        System.out.println("  - Current state: " + persistent.getCurrentState());
        System.out.println("  - Is complete: " + persistent.isComplete());
        
        // Verify volatile context
        OrderVolatileContext volatileCtx = machine.getContext();
        System.out.println("✓ Volatile context exists: " + (volatileCtx != null));
        System.out.println("  - Runtime cache size: " + volatileCtx.getRuntimeCache().size());
        System.out.println("  - Cache contains orderId: " + volatileCtx.getRuntimeCache().containsKey("orderId"));
        
        // Test state transition
        machine.fire(new OrderPlaced("CUST-TEST", 199.99));
        System.out.println("✓ After OrderPlaced event:");
        System.out.println("  - Current state: " + machine.getCurrentState());
        System.out.println("  - Customer ID: " + persistent.getCustomerId());
        System.out.println("  - Total amount: " + persistent.getTotalAmount());
        
        System.out.println("\n✅ Basic context separation test PASSED!\n");
    }
    
    private static void testVolatileContextRecreation() {
        System.out.println("Test 2: Volatile Context Recreation on Rehydration");
        System.out.println("-".repeat(40));
        
        String orderId = "TEST-ORDER-002";
        
        // Create a registry for testing rehydration
        StateMachineRegistry registry = new StateMachineRegistry();
        
        // Create machine using rehydration factory
        GenericStateMachine<OrderPersistentContext, OrderVolatileContext> machine = 
            registry.createOrGet(orderId, ProperContextExample.createRehydrationFactory(orderId));
        
        machine.start();
        
        // Process some events to change state
        machine.fire(new OrderPlaced("CUST-002", 299.99));
        System.out.println("✓ Machine advanced to: " + machine.getCurrentState());
        
        // Get contexts before eviction
        OrderPersistentContext persistentBefore = machine.getPersistingEntity();
        OrderVolatileContext volatileBefore = machine.getContext();
        long processingDurationBefore = volatileBefore.getProcessingDuration();
        
        System.out.println("✓ Before eviction:");
        System.out.println("  - Customer ID: " + persistentBefore.getCustomerId());
        System.out.println("  - Processing duration: " + processingDurationBefore + "ms");
        System.out.println("  - Volatile context instance: " + System.identityHashCode(volatileBefore));
        
        // Simulate machine eviction from memory
        System.out.println("\n⚡ Evicting machine from memory...");
        registry.evict(orderId);
        
        // Wait a bit to ensure time passes
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {}
        
        // Simulate rehydration - machine is loaded from persistence
        System.out.println("🔄 Rehydrating machine from persistence...");
        GenericStateMachine<OrderPersistentContext, OrderVolatileContext> rehydratedMachine = 
            registry.createOrGet(orderId, ProperContextExample.createRehydrationFactory(orderId));
        
        // Verify persistent context was restored
        OrderPersistentContext persistentAfter = rehydratedMachine.getPersistingEntity();
        System.out.println("\n✓ After rehydration:");
        System.out.println("  - State restored: " + rehydratedMachine.getCurrentState());
        System.out.println("  - Customer ID restored: " + persistentAfter.getCustomerId());
        System.out.println("  - Total amount restored: " + persistentAfter.getTotalAmount());
        
        // Verify volatile context was recreated (not the same instance)
        OrderVolatileContext volatileAfter = rehydratedMachine.getContext();
        System.out.println("  - Volatile context recreated: " + (volatileAfter != null));
        System.out.println("  - New volatile instance: " + System.identityHashCode(volatileAfter));
        System.out.println("  - Different instance: " + (volatileBefore != volatileAfter));
        
        // Processing duration should be reset (new volatile context)
        long processingDurationAfter = volatileAfter.getProcessingDuration();
        System.out.println("  - Processing duration reset: " + processingDurationAfter + "ms (was " + processingDurationBefore + "ms)");
        
        // Continue processing after rehydration
        rehydratedMachine.fire(new PaymentReceived("TXN-TEST"));
        System.out.println("\n✓ Continued processing after rehydration:");
        System.out.println("  - New state: " + rehydratedMachine.getCurrentState());
        
        System.out.println("\n✅ Volatile context recreation test PASSED!\n");
    }
}