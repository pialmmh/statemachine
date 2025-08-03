package com.telcobright.statemachineexamples.callmachine.states.connected;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachineexamples.callmachine.CallContext;

/**
 * Entry handler for CONNECTED state
 */
public class OnEntry {
    public static void handle(GenericStateMachine<CallContext> machine, StateMachineEvent event) {
        CallContext context = machine.getContext();
        
        if (context != null) {
            // Mark call as answered and connected
            context.answerCall();
            
            long ringTime = context.getRingDuration().toSeconds();
            System.out.println("✅ Call connected: " + context.getFromNumber() + " ↔ " + context.getToNumber());
            System.out.println("   📋 Call ID: " + context.getCallId());
            System.out.println("   ⏱️ Ring duration: " + ringTime + " seconds");
            
            // Business logic based on context
            if (ringTime > 30) {
                System.out.println("⏰ Long ring time detected - caller was very patient!");
                context.addSessionEvent("Long ring time: " + ringTime + "s");
            }
            
            // Initialize call features based on context
            if (context.isRecordingEnabled()) {
                System.out.println("🎙️ Call recording started");
                context.addSessionEvent("Call recording initiated");
            }
            
            // Set up call direction display
            String direction = context.getCallDirection();
            System.out.println("📞 Direction: " + direction + " call");
            
            // Log successful connection
            context.addSessionEvent("Call successfully connected after " + ringTime + "s ring time");
            
        } else {
            System.out.println("✅ Call connected - conversation started");
        }
    }
}
