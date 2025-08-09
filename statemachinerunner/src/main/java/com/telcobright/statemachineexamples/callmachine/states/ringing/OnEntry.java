package com.telcobright.statemachineexamples.callmachine.states.ringing;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachineexamples.callmachine.CallContext;

/**
 * Entry handler for RINGING state
 */
public class OnEntry {
    public static void handle(GenericStateMachine<?, ?> machine, StateMachineEvent event) {
        CallContext context = (CallContext) machine.getContext();
        
        if (context != null) {
            // Update context to track ringing
            context.startRinging();
            
            System.out.println("📞 Phone ringing: " + context.getFromNumber() + " → " + context.getToNumber());
            System.out.println("   📋 Call ID: " + context.getCallId());
            System.out.println("   🔔 Ring count: " + context.getRingCount());
            
            // Business logic based on context
            if (context.getRingCount() > 3) {
                System.out.println("⚠️ Multiple rings detected - may need attention");
                context.addSessionEvent("High ring count - potential issue");
            }
            
            // Enable recording for important calls (could be based on caller)
            if (context.getFromNumber().startsWith("+1800")) {
                context.setRecordingEnabled(true);
                System.out.println("🎙️ Recording enabled for toll-free caller");
            }
            
        } else {
            System.out.println("📞 Phone is ringing...");
        }
    }
}
