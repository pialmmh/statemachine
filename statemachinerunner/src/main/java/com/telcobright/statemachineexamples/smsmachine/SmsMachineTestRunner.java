package com.telcobright.statemachineexamples.smsmachine;

import com.telcobright.statemachineexamples.smsmachine.events.*;
import com.telcobright.statemachine.GenericStateMachine;

/**
 * Comprehensive SmsMachine example demonstrating:
 * 1. FluentStateMachineBuilder usage
 * 2. Rich context implementation
 * 3. Real-world SMS delivery scenarios
 */
public class SmsMachineTestRunner {
    
    public static void main(String[] args) {
        try {
            System.out.println("📱 === SmsMachine: Builder + Context Demo ===\n");
            
            // Demo 1: Regular SMS delivery
            System.out.println("📤 === DEMO 1: Regular SMS Flow ===");
            runRegularSmsFlow();
            
            Thread.sleep(1000);
            
            // Demo 2: High priority SMS with retry
            System.out.println("\n📤 === DEMO 2: High Priority SMS with Retry ===");
            runHighPrioritySmsFlow();
            
            Thread.sleep(1000);
            
            // Demo 3: Long message delivery  
            System.out.println("\n📤 === DEMO 3: Long Message Delivery ===");
            runLongMessageFlow();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Regular SMS delivery demonstration
     */
    private static void runRegularSmsFlow() {
        try {
            // Create rich SMS context
            SmsContext context = new SmsContext("SMS-001", "+1234567890", "+0987654321", "Hello! This is a test message.");
            
            // Create SmsMachine using FluentStateMachineBuilder (see SmsMachine.create())
            SmsMachine machine = SmsMachine.create("sms-machine-001");
            machine.setContext(context);
            
            System.out.println("📋 Initial Context: " + context);
            runSmsFlow(machine, context, false);
            showSmsSummary(context);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * High priority SMS with retry demonstration
     */
    private static void runHighPrioritySmsFlow() {
        try {
            // Emergency message will be flagged as high priority
            SmsContext context = new SmsContext("SMS-002", "+1800911911", "+0987654321", "URGENT: Emergency alert notification");
            
            SmsMachine machine = SmsMachine.create("sms-machine-002");
            machine.setContext(context);
            
            System.out.println("📋 Initial Context: " + context);
            runSmsFlow(machine, context, true); // Force failure for retry demo
            showSmsSummary(context);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Long message delivery demonstration
     */
    private static void runLongMessageFlow() {
        try {
            // Long message requiring segmentation
            String longMessage = "This is a very long SMS message that exceeds the normal 160 character limit and will need to be segmented into multiple parts for delivery across the SMS network infrastructure.";
            SmsContext context = new SmsContext("SMS-003", "+1555123456", "+0987654321", longMessage);
            
            SmsMachine machine = SmsMachine.create("sms-machine-003");
            machine.setContext(context);
            
            System.out.println("📋 Initial Context: " + context);
            runSmsFlow(machine, context, false);
            showSmsSummary(context);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Execute SMS flow with context-aware state changes
     */
    private static void runSmsFlow(GenericStateMachine machine, SmsContext context, boolean simulateFailure) {
        try {
            System.out.println("🔄 Initial state: " + machine.getCurrentState());
            
            System.out.println("\n--- Send Attempt ---");
            machine.fire(new SendAttempt());
            System.out.println("🔄 State: " + machine.getCurrentState());
            Thread.sleep(1000);
            
            if (simulateFailure) {
                System.out.println("\n--- Send Failed (Simulating Network Issue) ---");
                machine.fire(new SendFailed());
                System.out.println("🔄 State: " + machine.getCurrentState());
                Thread.sleep(1000);
                
                if (context.canRetry()) {
                    System.out.println("\n--- Retry Attempt ---");
                    machine.fire(new Retry());
                    System.out.println("🔄 State: " + machine.getCurrentState());
                    Thread.sleep(500);
                    
                    System.out.println("\n--- Second Send Attempt ---");
                    machine.fire(new SendAttempt());
                    System.out.println("🔄 State: " + machine.getCurrentState());
                    Thread.sleep(1000);
                }
            }
            
            System.out.println("\n--- Delivery Report ---");
            machine.fire(new DeliveryReport());
            System.out.println("🔄 State: " + machine.getCurrentState());
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Display comprehensive SMS summary from context
     */
    private static void showSmsSummary(SmsContext context) {
        System.out.println("\n📊 === SMS Summary ===");
        System.out.println("📋 " + context);
        System.out.println("⏱️ Delivery Time: " + context.getDeliveryTime().toSeconds() + "s");
        System.out.println("🔄 Total Attempts: " + context.getAttemptCount() + "/" + context.getMaxRetries());
        System.out.println("⚡ Priority: " + context.getPriority());
        System.out.println("📏 Message Length: " + context.getMessageText().length() + " chars");
        System.out.println("📄 Long Message: " + (context.isLongMessage() ? "Yes" : "No"));
        System.out.println("🚨 High Priority: " + (context.isHighPriority() ? "Yes" : "No"));
        System.out.println("✅ Can Retry: " + (context.canRetry() ? "Yes" : "No"));
        
        if (context.getFailureReason() != null) {
            System.out.println("💥 Failure Reason: " + context.getFailureReason());
        }
        
        System.out.println("\n📝 Delivery Events:");
        for (String event : context.getDeliveryEvents()) {
            System.out.println("   " + event);
        }
        
        System.out.println("=" + "=".repeat(50));
    }
}