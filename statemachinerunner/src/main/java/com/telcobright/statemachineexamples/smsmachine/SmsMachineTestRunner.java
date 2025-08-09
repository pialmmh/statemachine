package com.telcobright.statemachineexamples.smsmachine;

import com.telcobright.statemachineexamples.smsmachine.entity.SmsEntity;
import com.telcobright.statemachineexamples.smsmachine.events.*;
import com.telcobright.statemachine.GenericStateMachine;

/**
 * SmsMachine Test Runner demonstrating ByIdAndDateRange lookup mode
 * 
 * Features shown:
 * - Long IDs with embedded timestamps for high-volume processing
 * - ByIdAndDateRange lookup mode for efficient partitioned storage
 * - IdGenerator usage for timestamp-embedded IDs
 * - Rich SmsContext usage throughout state transitions
 * - Builder syntax with typed context
 */
public class SmsMachineTestRunner {
    
    public static void main(String[] args) {
        try {
            System.out.println("📱 === SmsMachine: ByIdAndDateRange Lookup Mode Demo ===\n");
            
            // Demo 1: Regular SMS with long ID
            System.out.println("📤 === DEMO 1: Regular SMS Flow with Long ID ===");
            runRegularSmsFlow();
            
            Thread.sleep(1000);
            
            // Demo 2: High priority SMS with retry
            System.out.println("\n📤 === DEMO 2: High Priority SMS with Retry ===");
            runHighPrioritySmsFlow();
            
            Thread.sleep(1000);
            
            // Demo 3: Generated ID demonstration
            System.out.println("\n📤 === DEMO 3: Generated ID Demo ===");
            runGeneratedIdDemo();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Demonstrate regular SMS flow with long ID for ByIdAndDateRange lookup
     */
    private static void runRegularSmsFlow() {
        try {
            // Generate long ID with embedded timestamp using IdGenerator
            long generatedId = com.telcobright.idkit.IdGenerator.generateId();
            String machineId = String.valueOf(generatedId);
            
            System.out.println("📋 Generated Machine ID: " + machineId);
            System.out.println("📋 Embedded Timestamp: " + com.telcobright.idkit.IdGenerator.extractTimestampLocal(generatedId));
            
            // Create rich SMS context
            SmsContext context = new SmsContext("SMS-001", "+1234567890", "+0987654321", "Hello! This is a test message.");
            
            // Create SmsMachine using long ID for ByIdAndDateRange lookup
            GenericStateMachine<SmsEntity, SmsContext> machine = SmsMachine.create(machineId);
            machine.setContext(context);
            
            System.out.println("📋 Initial Context: " + context);
            System.out.println("🔄 Initial state: " + machine.getCurrentState());
            
            runSmsFlow(machine, context, false);
            showSmsSummary(context, machineId);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Demonstrate high priority SMS with retry scenario
     */
    private static void runHighPrioritySmsFlow() {
        try {
            // Generate another ID for high priority message
            long generatedId = com.telcobright.idkit.IdGenerator.generateId();
            String machineId = String.valueOf(generatedId);
            
            System.out.println("📋 Generated Machine ID: " + machineId);
            
            // Emergency message will be flagged as high priority
            SmsContext context = new SmsContext("SMS-002", "+1800911911", "+0987654321", "URGENT: Emergency alert notification");
            
            GenericStateMachine<SmsEntity, SmsContext> machine = SmsMachine.create(machineId);
            machine.setContext(context);
            
            System.out.println("📋 Initial Context: " + context);
            System.out.println("🔄 Initial state: " + machine.getCurrentState());
            
            runSmsFlow(machine, context, true); // Force failure for retry demo
            showSmsSummary(context, machineId);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Demonstrate the createWithGeneratedId method
     */
    private static void runGeneratedIdDemo() {
        try {
            System.out.println("📋 Demonstrating automatic ID generation for ByIdAndDateRange mode");
            
            // This would normally use a real PartitionedRepository
            // For demo, we'll simulate with regular create
            long demoId = com.telcobright.idkit.IdGenerator.generateId();
            String machineId = String.valueOf(demoId);
            
            System.out.println("📋 Auto-generated Machine ID: " + machineId);
            System.out.println("📋 Extracted Timestamp: " + com.telcobright.idkit.IdGenerator.extractTimestampLocal(demoId));
            
            // Long message requiring segmentation
            String longMessage = "This is a very long SMS message that exceeds the normal 160 character limit and will need to be segmented into multiple parts for delivery across the SMS network infrastructure.";
            SmsContext context = new SmsContext("SMS-003", "+1555123456", "+0987654321", longMessage);
            
            GenericStateMachine<SmsEntity, SmsContext> machine = SmsMachine.create(machineId);
            machine.setContext(context);
            
            System.out.println("📋 Initial Context: " + context);
            runSmsFlow(machine, context, false);
            showSmsSummary(context, machineId);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Execute SMS flow with context-aware state changes
     */
    private static void runSmsFlow(GenericStateMachine<SmsEntity, SmsContext> machine, SmsContext context, boolean simulateFailure) {
        try {
            System.out.println("🔄 Initial state: " + machine.getCurrentState());
            
            System.out.println("\n--- Send Attempt ---");
            machine.fire(new SendAttempt());
            System.out.println("🔄 State: " + machine.getCurrentState());
            Thread.sleep(1000);
            
            // Send status update while sending
            System.out.println("\n--- Status Update ---");
            machine.fire(new StatusUpdate("Gateway processing message"));
            Thread.sleep(500);
            
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
     * Display comprehensive SMS summary from context with ID info
     */
    private static void showSmsSummary(SmsContext context, String machineId) {
        System.out.println("\n📊 === SMS Summary ===");
        System.out.println("🆔 Machine ID: " + machineId + " (Long format for date-based partitioning)");
        
        try {
            long longId = Long.parseLong(machineId);
            System.out.println("🕒 ID Timestamp: " + com.telcobright.idkit.IdGenerator.extractTimestampLocal(longId));
        } catch (NumberFormatException e) {
            System.out.println("🕒 ID Format: String (not optimized for ByIdAndDateRange)");
        }
        
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
        
        System.out.println("\n💡 ByIdAndDateRange Benefits:");
        System.out.println("   - Efficient date-based partitioning for high volume");
        System.out.println("   - Partition pruning reduces query time");
        System.out.println("   - Optimal for SMS processing with timestamp-embedded IDs");
        
        System.out.println("=" + "=".repeat(60));
    }
}