package com.telcobright.statemachineexamples.callmachine;

import com.telcobright.statemachineexamples.callmachine.entity.CallEntity;
import com.telcobright.statemachineexamples.callmachine.events.*;
import com.telcobright.statemachine.GenericStateMachine;

/**
 * CallMachine Test Runner demonstrating ById lookup mode
 * 
 * Features shown:
 * - Simple string IDs for call machine instances
 * - ById lookup mode for straightforward persistence
 * - Rich CallContext usage throughout state transitions
 * - Builder syntax with typed context
 */
public class CallMachineTestRunner {
    
    public static void main(String[] args) {
        try {
            System.out.println("📞 === CallMachine: ById Lookup Mode Demo ===\n");
            
            // Demo 1: Basic call flow
            System.out.println("📞 === DEMO 1: Incoming Call Flow ===");
            runIncomingCallFlow();
            
            Thread.sleep(1000);
            
            // Demo 2: Call with session progress events
            System.out.println("\n📞 === DEMO 2: Call with Session Progress ===");
            runCallWithSessionProgress();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Demonstrate basic incoming call flow with context
     */
    private static void runIncomingCallFlow() {
        try {
            // Create CallContext with call details
            CallContext context = new CallContext("CALL-001", "+1234567890", "+0987654321");
            
            // Create CallMachine using simple string ID (ById lookup)
            GenericStateMachine<CallEntity, CallContext> machine = CallMachine.create("call-machine-001");
            machine.setContext(context);
            
            System.out.println("📋 Initial Context: " + context);
            System.out.println("🔄 Initial state: " + machine.getCurrentState());
            
            // Simulate incoming call
            System.out.println("\n--- Incoming Call ---");
            machine.fire(new IncomingCall("+1234567890"));
            System.out.println("🔄 State: " + machine.getCurrentState());
            Thread.sleep(1000);
            
            // Answer the call
            System.out.println("\n--- Answer Call ---");
            machine.fire(new Answer());
            System.out.println("🔄 State: " + machine.getCurrentState());
            Thread.sleep(2000);
            
            // Hangup call
            System.out.println("\n--- Hangup Call ---");
            machine.fire(new Hangup());
            System.out.println("🔄 State: " + machine.getCurrentState());
            
            showCallSummary(context);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Demonstrate call with session progress events
     */
    private static void runCallWithSessionProgress() {
        try {
            // Create toll-free call context (will enable recording)
            CallContext context = new CallContext("CALL-002", "+1800555123", "+0987654321");
            
            GenericStateMachine<CallEntity, CallContext> machine = CallMachine.create("call-machine-002");
            machine.setContext(context);
            
            System.out.println("📋 Initial Context: " + context);
            System.out.println("🔄 Initial state: " + machine.getCurrentState());
            
            // Start call
            System.out.println("\n--- Incoming Call ---");
            machine.fire(new IncomingCall("+1800555123"));
            System.out.println("🔄 State: " + machine.getCurrentState());
            Thread.sleep(500);
            
            // Send session progress events while ringing
            System.out.println("\n--- Session Progress Events ---");
            machine.fire(new SessionProgress("100", 10));
            Thread.sleep(300);
            
            machine.fire(new SessionProgress("180", 50));
            Thread.sleep(500);
            
            machine.fire(new SessionProgress("183", 75));
            Thread.sleep(300);
            
            // Answer call
            System.out.println("\n--- Answer Call ---");
            machine.fire(new Answer());
            System.out.println("🔄 State: " + machine.getCurrentState());
            Thread.sleep(1500);
            
            // End call
            System.out.println("\n--- Hangup Call ---");
            machine.fire(new Hangup());
            System.out.println("🔄 State: " + machine.getCurrentState());
            
            showCallSummary(context);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Display comprehensive call summary from context
     */
    private static void showCallSummary(CallContext context) {
        System.out.println("\n📊 === Call Summary ===");
        System.out.println("📋 " + context);
        System.out.println("⏱️ Call Duration: " + context.getCallDuration().toSeconds() + "s");
        System.out.println("🔔 Ring Duration: " + context.getRingDuration().toSeconds() + "s");
        System.out.println("🔢 Ring Count: " + context.getRingCount());
        System.out.println("📞 Call Status: " + context.getCallStatus());
        System.out.println("🎙️ Recording Enabled: " + (context.isRecordingEnabled() ? "Yes" : "No"));
        System.out.println("📈 Long Call: " + (context.isLongCall() ? "Yes" : "No"));
        
        if (context.getDisconnectReason() != null) {
            System.out.println("💡 Disconnect Reason: " + context.getDisconnectReason());
        }
        
        System.out.println("\n📝 Session Events:");
        for (String event : context.getSessionEvents()) {
            System.out.println("   " + event);
        }
        
        System.out.println("=" + "=".repeat(50));
    }
}