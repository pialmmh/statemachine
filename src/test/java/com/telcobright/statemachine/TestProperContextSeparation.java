package com.telcobright.statemachine;

import com.telcobright.statemachine.examples.ProperContextExample;
import com.telcobright.statemachine.examples.ProperContextExample.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test proper separation of persistent and volatile contexts
 */
public class TestProperContextSeparation {
    
    @Test
    public void testContextCreation() {
        String orderId = "TEST-ORDER-001";
        
        // Create machine with proper context separation
        GenericStateMachine<OrderPersistentContext, OrderVolatileContext> machine = 
            ProperContextExample.createOrderStateMachine(orderId);
        
        // Start the machine
        machine.start();
        
        // Verify initial state
        assertEquals("PENDING", machine.getCurrentState());
        
        // Verify persistent context
        OrderPersistentContext persistent = machine.getPersistingEntity();
        assertNotNull(persistent);
        assertEquals(orderId, persistent.getOrderId());
        assertEquals("PENDING", persistent.getCurrentState());
        assertFalse(persistent.isComplete());
        
        // Verify volatile context
        OrderVolatileContext volatileCtx = machine.getContext();
        assertNotNull(volatileCtx);
        assertNotNull(volatileCtx.getRuntimeCache());
        assertTrue(volatileCtx.getRuntimeCache().containsKey("orderId"));
        assertEquals(orderId, volatileCtx.getRuntimeCache().get("orderId"));
        
        // Test state transition
        machine.fire(new OrderPlaced("CUST-TEST", 199.99));
        assertEquals("AWAITING_PAYMENT", machine.getCurrentState());
        assertEquals("AWAITING_PAYMENT", persistent.getCurrentState());
        
        // Verify persistent context was updated
        assertEquals("CUST-TEST", persistent.getCustomerId());
        assertEquals(199.99, persistent.getTotalAmount());
        
        System.out.println("✅ Context separation test passed!");
    }
    
    @Test
    public void testRehydrationWithVolatileRecreation() {
        String orderId = "TEST-ORDER-002";
        
        // Create a registry for testing rehydration
        StateMachineRegistry registry = new StateMachineRegistry();
        
        // Create machine using rehydration factory
        GenericStateMachine<OrderPersistentContext, OrderVolatileContext> machine = 
            registry.createOrGet(orderId, ProperContextExample.createRehydrationFactory(orderId));
        
        machine.start();
        
        // Process some events to change state
        machine.fire(new OrderPlaced("CUST-002", 299.99));
        assertEquals("AWAITING_PAYMENT", machine.getCurrentState());
        
        // Get persistent context before simulating eviction
        OrderPersistentContext persistentBeforeEviction = machine.getPersistingEntity();
        assertNotNull(persistentBeforeEviction);
        assertEquals("CUST-002", persistentBeforeEviction.getCustomerId());
        
        // Get volatile context before eviction
        OrderVolatileContext volatileBeforeEviction = machine.getContext();
        assertNotNull(volatileBeforeEviction);
        long processingDurationBefore = volatileBeforeEviction.getProcessingDuration();
        
        // Simulate machine eviction from memory
        registry.evict(orderId);
        
        // Wait a bit to ensure time passes
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {}
        
        // Simulate rehydration - machine is loaded from persistence
        GenericStateMachine<OrderPersistentContext, OrderVolatileContext> rehydratedMachine = 
            registry.createOrGet(orderId, ProperContextExample.createRehydrationFactory(orderId));
        
        // The rehydration factory should have been called
        // and volatile context should be recreated
        
        // Verify persistent context was restored
        OrderPersistentContext persistentAfterRehydration = rehydratedMachine.getPersistingEntity();
        assertNotNull(persistentAfterRehydration);
        assertEquals("CUST-002", persistentAfterRehydration.getCustomerId());
        assertEquals(299.99, persistentAfterRehydration.getTotalAmount());
        assertEquals("AWAITING_PAYMENT", persistentAfterRehydration.getCurrentState());
        
        // Verify volatile context was recreated (not the same instance)
        OrderVolatileContext volatileAfterRehydration = rehydratedMachine.getContext();
        assertNotNull(volatileAfterRehydration);
        assertNotSame(volatileBeforeEviction, volatileAfterRehydration); // Different instance
        
        // Verify volatile context was properly initialized
        assertNotNull(volatileAfterRehydration.getRuntimeCache());
        assertTrue(volatileAfterRehydration.getRuntimeCache().containsKey("orderId"));
        assertEquals(orderId, volatileAfterRehydration.getRuntimeCache().get("orderId"));
        
        // Processing duration should be reset (new volatile context)
        long processingDurationAfter = volatileAfterRehydration.getProcessingDuration();
        assertTrue(processingDurationAfter < processingDurationBefore, 
            "Processing duration should be reset after rehydration");
        
        // Continue processing after rehydration
        rehydratedMachine.fire(new PaymentReceived("TXN-TEST"));
        assertEquals("PROCESSING", rehydratedMachine.getCurrentState());
        
        System.out.println("✅ Rehydration with volatile context recreation test passed!");
    }
    
    @Test
    public void testOfflineStateRehydration() {
        String orderId = "TEST-ORDER-003";
        
        StateMachineRegistry registry = new StateMachineRegistry();
        
        // Create and advance machine to offline state
        GenericStateMachine<OrderPersistentContext, OrderVolatileContext> machine = 
            registry.createOrGet(orderId, ProperContextExample.createRehydrationFactory(orderId));
        
        machine.start();
        machine.fire(new OrderPlaced("CUST-003", 399.99));
        machine.fire(new PaymentReceived("TXN-003"));
        
        // Machine should now be in PROCESSING state (which is marked as offline)
        assertEquals("PROCESSING", machine.getCurrentState());
        
        // Simulate machine being evicted (offline state behavior)
        registry.evict(orderId);
        
        // Later, rehydrate the machine to continue processing
        GenericStateMachine<OrderPersistentContext, OrderVolatileContext> rehydratedMachine = 
            registry.createOrGet(orderId, ProperContextExample.createRehydrationFactory(orderId));
        
        // Verify state was preserved
        assertEquals("PROCESSING", rehydratedMachine.getCurrentState());
        
        // Verify persistent context
        OrderPersistentContext persistent = rehydratedMachine.getPersistingEntity();
        assertNotNull(persistent);
        assertEquals("CUST-003", persistent.getCustomerId());
        assertEquals(399.99, persistent.getTotalAmount());
        
        // Verify volatile context was recreated
        OrderVolatileContext volatileCtx = rehydratedMachine.getContext();
        assertNotNull(volatileCtx);
        assertNotNull(volatileCtx.getRuntimeCache());
        
        // Continue processing
        rehydratedMachine.fire(new OrderShipped("TRACK-003"));
        assertEquals("SHIPPED", rehydratedMachine.getCurrentState());
        
        System.out.println("✅ Offline state rehydration test passed!");
    }
}