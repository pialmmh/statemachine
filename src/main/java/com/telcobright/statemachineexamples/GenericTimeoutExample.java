package com.telcobright.statemachineexamples;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.timeout.TimeoutManager;
import com.telcobright.statemachine.timeout.TimeUnit;
import com.telcobright.statemachineexamples.callmachine.CallContext;

/**
 * Demonstrates how the generic TimeoutManager works with different types of state machines
 * including Call, SMS, Order, and custom business processes.
 * 
 * The TimeoutManager is completely generic and can be shared across:
 * - Call state machines (telephony)
 * - SMS state machines (messaging)
 * - Order state machines (e-commerce)
 * - Payment state machines (financial)
 * - Any custom business process state machines
 */
public class GenericTimeoutExample {
    
    // Single shared TimeoutManager for all state machine types
    private static TimeoutManager sharedTimeoutManager;
    private static StateMachineRegistry registry;
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== Generic TimeoutManager Demo ===");
        System.out.println("Demonstrating a single TimeoutManager handling multiple state machine types\n");
        
        // Create a single TimeoutManager that will be shared by all state machine types
        // In production, this would typically be a singleton or injected dependency
        sharedTimeoutManager = new TimeoutManager("Production", 10);
        
        // Create registry with the shared timeout manager
        registry = new StateMachineRegistry(sharedTimeoutManager, 9998);
        
        // Set factory defaults
        StateMachineFactory.setDefaultInstances(sharedTimeoutManager, registry);
        
        System.out.println("Created shared TimeoutManager: " + sharedTimeoutManager);
        System.out.println();
        
        // Demo different types of state machines all using the same TimeoutManager
        demoCallMachine();
        demoSmsMachine();
        demoOrderMachine();
        demoPaymentMachine();
        
        // Show timeout manager statistics
        System.out.println("\n=== TimeoutManager Statistics ===");
        System.out.println(sharedTimeoutManager.getStatistics());
        
        // Wait for all timeouts to complete
        System.out.println("\nWaiting for all timeouts to complete...");
        Thread.sleep(12000);
        
        // Show final statistics
        System.out.println("\n=== Final TimeoutManager Statistics ===");
        System.out.println(sharedTimeoutManager.getStatistics());
        System.out.println("\nNote: All 4 different state machine types used the same TimeoutManager!");
        
        // Cleanup
        sharedTimeoutManager.shutdown();
        System.out.println("\nDemo completed!");
    }
    
    /**
     * Demo: Call State Machine with timeout
     * Represents a telephone call flow
     */
    private static void demoCallMachine() {
        System.out.println("1. CALL STATE MACHINE (Telephony)");
        System.out.println("   Type: Telephony/VoIP");
        System.out.println("   States: IDLE -> DIALING -> RINGING -> CONNECTED -> IDLE");
        System.out.println("   Timeout: RINGING -> IDLE after 5 seconds (no answer)");
        
        // Using the existing CallContext class
        CallContext context = new CallContext("call-demo", "+1-555-0001", "+1-555-0002");
        
        GenericStateMachine<CallContext, Void> callMachine = 
            FluentStateMachineBuilder.<CallContext, Void>create("call-demo")
                .initialState("IDLE")
                
                .state("IDLE")
                    .on("DIAL").to("DIALING")
                    .done()
                
                .state("DIALING")
                    .on("RING").to("RINGING")
                    .done()
                
                .state("RINGING")
                    .timeout(5, TimeUnit.SECONDS, "IDLE")  // Using shared TimeoutManager
                    .on("ANSWER").to("CONNECTED")
                    .done()
                
                .state("CONNECTED")
                    .timeout(30, TimeUnit.SECONDS, "IDLE") // Call duration limit
                    .on("HANGUP").to("IDLE")
                    .done()
                
                .build();
        
        callMachine.setPersistingEntity(context);
        callMachine.start();
        
        // Simulate call flow
        callMachine.fire("DIAL");
        callMachine.fire("RING");
        System.out.println("   ✓ Call machine created and running with timeout");
        System.out.println("   → Same TimeoutManager instance: " + (sharedTimeoutManager == StateMachineFactory.getDefaultTimeoutManager()));
        System.out.println();
    }
    
    /**
     * Demo: SMS State Machine with timeout
     * Represents SMS message delivery flow
     */
    private static void demoSmsMachine() {
        System.out.println("2. SMS STATE MACHINE (Messaging)");
        System.out.println("   Type: Messaging/SMS");
        System.out.println("   States: DRAFT -> SENDING -> DELIVERED / FAILED");
        System.out.println("   Timeout: SENDING -> FAILED after 3 seconds (delivery timeout)");
        
        // For SMS, we'll reuse CallContext as a simple entity (in production, you'd have SmsContext)
        CallContext smsContext = new CallContext("sms-demo", "+1-555-0001", "+1-555-0002");
        
        GenericStateMachine<CallContext, Void> smsMachine = 
            FluentStateMachineBuilder.<CallContext, Void>create("sms-demo")
                .initialState("DRAFT")
                
                .state("DRAFT")
                    .on("SEND").to("SENDING")
                    .done()
                
                .state("SENDING")
                    .timeout(3, TimeUnit.SECONDS, "FAILED")  // Using shared TimeoutManager
                    .on("ACK").to("DELIVERED")
                    .done()
                
                .state("DELIVERED")
                    .finalState()
                    .done()
                
                .state("FAILED")
                    .finalState()
                    .on("RETRY").to("SENDING")
                    .done()
                
                .build();
        
        smsMachine.setPersistingEntity(smsContext);
        smsMachine.start();
        
        // Simulate SMS sending
        smsMachine.fire("SEND");
        System.out.println("   ✓ SMS machine created and running with timeout");
        System.out.println("   → Same TimeoutManager instance: " + (sharedTimeoutManager == StateMachineFactory.getDefaultTimeoutManager()));
        System.out.println();
    }
    
    /**
     * Demo: Order State Machine with timeout
     * Represents e-commerce order processing
     */
    private static void demoOrderMachine() {
        System.out.println("3. ORDER STATE MACHINE (E-commerce)");
        System.out.println("   Type: E-commerce/Order Processing");
        System.out.println("   States: NEW -> PAYMENT_PENDING -> PROCESSING -> SHIPPED -> DELIVERED");
        System.out.println("   Timeout: PAYMENT_PENDING -> CANCELLED after 10 seconds (payment timeout)");
        
        // For Order, we'll reuse CallContext as a simple entity (in production, you'd have OrderContext)
        CallContext orderContext = new CallContext("order-demo", "customer-123", "order-456");
        
        GenericStateMachine<CallContext, Void> orderMachine = 
            FluentStateMachineBuilder.<CallContext, Void>create("order-demo")
                .initialState("NEW")
                
                .state("NEW")
                    .on("CHECKOUT").to("PAYMENT_PENDING")
                    .done()
                
                .state("PAYMENT_PENDING")
                    .timeout(10, TimeUnit.SECONDS, "CANCELLED")  // Using shared TimeoutManager
                    .on("PAYMENT_SUCCESS").to("PROCESSING")
                    .on("PAYMENT_FAILED").to("CANCELLED")
                    .done()
                
                .state("PROCESSING")
                    .timeout(60, TimeUnit.SECONDS, "ESCALATED") // Processing SLA
                    .on("SHIP").to("SHIPPED")
                    .done()
                
                .state("SHIPPED")
                    .on("DELIVER").to("DELIVERED")
                    .done()
                
                .state("DELIVERED")
                    .finalState()
                    .done()
                
                .state("CANCELLED")
                    .finalState()
                    .done()
                
                .state("ESCALATED")
                    .on("RESOLVE").to("PROCESSING")
                    .done()
                
                .build();
        
        orderMachine.setPersistingEntity(orderContext);
        orderMachine.start();
        
        // Simulate order flow
        orderMachine.fire("CHECKOUT");
        System.out.println("   ✓ Order machine created and running with timeout");
        System.out.println("   → Same TimeoutManager instance: " + (sharedTimeoutManager == StateMachineFactory.getDefaultTimeoutManager()));
        System.out.println();
    }
    
    /**
     * Demo: Payment State Machine with multiple timeouts
     * Represents payment processing flow
     */
    private static void demoPaymentMachine() {
        System.out.println("4. PAYMENT STATE MACHINE (Financial)");
        System.out.println("   Type: Financial/Payment Processing");
        System.out.println("   States: INITIATED -> AUTHORIZING -> CAPTURING -> COMPLETED");
        System.out.println("   Timeouts: Multiple timeouts for different stages");
        
        // For Payment, we'll reuse CallContext as a simple entity (in production, you'd have PaymentContext)
        CallContext paymentContext = new CallContext("payment-demo", "account-123", "txn-789");
        
        GenericStateMachine<CallContext, Void> paymentMachine = 
            FluentStateMachineBuilder.<CallContext, Void>create("payment-demo")
                .initialState("INITIATED")
                
                .state("INITIATED")
                    .on("AUTHORIZE").to("AUTHORIZING")
                    .done()
                
                .state("AUTHORIZING")
                    .timeout(4, TimeUnit.SECONDS, "AUTH_TIMEOUT")  // Using shared TimeoutManager
                    .on("AUTHORIZED").to("CAPTURING")
                    .on("DECLINED").to("FAILED")
                    .done()
                
                .state("CAPTURING")
                    .timeout(6, TimeUnit.SECONDS, "CAPTURE_TIMEOUT")  // Using shared TimeoutManager
                    .on("CAPTURED").to("SETTLING")
                    .done()
                
                .state("SETTLING")
                    .timeout(8, TimeUnit.SECONDS, "SETTLEMENT_TIMEOUT")  // Using shared TimeoutManager
                    .on("SETTLED").to("COMPLETED")
                    .done()
                
                .state("COMPLETED")
                    .finalState()
                    .done()
                
                .state("FAILED")
                    .finalState()
                    .done()
                
                .state("AUTH_TIMEOUT")
                    .finalState()
                    .done()
                
                .state("CAPTURE_TIMEOUT")
                    .finalState()
                    .done()
                
                .state("SETTLEMENT_TIMEOUT")
                    .finalState()
                    .done()
                
                .build();
        
        paymentMachine.setPersistingEntity(paymentContext);
        paymentMachine.start();
        
        // Simulate payment flow
        paymentMachine.fire("AUTHORIZE");
        System.out.println("   ✓ Payment machine created and running with multiple timeouts");
        System.out.println("   → Same TimeoutManager instance: " + (sharedTimeoutManager == StateMachineFactory.getDefaultTimeoutManager()));
        System.out.println();
        
        // Show that all timeout stages use the same manager
        System.out.println("   Note: This payment flow has 3 different timeout stages,");
        System.out.println("         all managed by the same shared TimeoutManager!");
        System.out.println();
    }
}