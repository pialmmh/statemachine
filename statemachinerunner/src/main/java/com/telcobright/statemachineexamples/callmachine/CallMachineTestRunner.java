package com.telcobright.statemachineexamples.callmachine;

import com.telcobright.statemachineexamples.callmachine.events.Answer;
import com.telcobright.statemachineexamples.callmachine.events.Hangup;
import com.telcobright.statemachineexamples.callmachine.events.IncomingCall;
import com.telcobright.statemachineexamples.callmachine.events.SessionProgress;
import com.telcobright.statemachine.GenericStateMachine;

/**
 * Comprehensive CallMachine example demonstrating:
 * 1. FluentStateMachineBuilder usage
 * 2. Rich context implementation
 * 3. Real-world call flow scenarios
 */
public class CallMachineTestRunner {
    
    public static void main(String[] args) {
        try {
            System.out.println("🎯 === CallMachine: Builder + Context Demo ===\n");
            
            // Demo 1: Regular call with context
            System.out.println("📞 === DEMO 1: Regular Call Flow ===");
            runRegularCall();
            
            Thread.sleep(1000);
            
            // Demo 2: Special caller (toll-free) - shows conditional logic
            System.out.println("\n📞 === DEMO 2: Special Caller (Recording Enabled) ===");
            runSpecialCallerFlow();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Regular call demonstration
     */
    private static void runRegularCall() {
        try {
            // Create rich call context
            CallContext context = new CallContext("CALL-001", "+1234567890", "+0987654321");
            context.setCallDirection("INBOUND");
            
            // Create CallMachine using FluentStateMachineBuilder (see CallMachine.create())
            CallMachine machine = CallMachine.create("call-machine-001");
            machine.setContext(context);
            
            System.out.println("📋 Initial Context: " + context);
            runCallFlow(machine, context);
            showCallSummary(context);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Special caller demo - toll-free number triggers recording
     */
    private static void runSpecialCallerFlow() {
        try {
            // Toll-free number will trigger recording in RINGING OnEntry
            CallContext context = new CallContext("CALL-002", "+18005551234", "+0987654321");
            context.setCallDirection("INBOUND");
            
            CallMachine machine = CallMachine.create("call-machine-002");
            machine.setContext(context);
            
            System.out.println("📋 Initial Context: " + context);
            runCallFlow(machine, context);
            showCallSummary(context);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Execute the call flow with context-aware state changes
     */
    private static void runCallFlow(GenericStateMachine machine, CallContext context) {
        try {
            System.out.println("🔄 Initial state: " + machine.getCurrentState());
            
            System.out.println("\n--- Incoming Call ---");
            machine.fire(new IncomingCall());
            System.out.println("🔄 State: " + machine.getCurrentState());
            Thread.sleep(1000); // Ring time
            
            System.out.println("\n--- Session Progress (Early Media) ---");
            machine.fire(new SessionProgress("183 Session Progress", 50));
            System.out.println("🔄 State: " + machine.getCurrentState() + " (stay event)");
            Thread.sleep(500);
            
            System.out.println("\n--- Call Answered ---");
            machine.fire(new Answer());
            System.out.println("🔄 State: " + machine.getCurrentState());
            Thread.sleep(2000); // Conversation time
            
            System.out.println("\n--- Call Hangup ---");
            machine.fire(new Hangup());
            System.out.println("🔄 State: " + machine.getCurrentState());
            
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
        System.out.println("⏱️ Ring Duration: " + context.getRingDuration().toSeconds() + "s");
        System.out.println("⏱️ Call Duration: " + context.getCallDuration().toSeconds() + "s");
        System.out.println("🔔 Ring Count: " + context.getRingCount());
        System.out.println("🎙️ Recording: " + (context.isRecordingEnabled() ? "Yes" : "No"));
        System.out.println("📈 Long Call: " + (context.isLongCall() ? "Yes" : "No"));
        System.out.println("💡 Disconnect: " + context.getDisconnectReason());
        
        System.out.println("\n📝 Session Events:");
        for (String event : context.getSessionEvents()) {
            System.out.println("   " + event);
        }
        
        System.out.println("=" + "=".repeat(50));
    }
}