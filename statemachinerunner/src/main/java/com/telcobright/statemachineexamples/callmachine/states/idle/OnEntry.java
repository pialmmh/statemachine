package com.telcobright.statemachineexamples.callmachine.states.idle;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachineexamples.callmachine.CallContext;

/**
 * Entry handler for IDLE state
 */
public class OnEntry {
    public static void handle(GenericStateMachine<?, ?> machine, StateMachineEvent event) {
        CallContext context = (CallContext) machine.getContext();
        
        if (context != null) {
            // Reset call status and log the transition
            context.setCallStatus("IDLE");
            context.addSessionEvent("Entered IDLE state - ready for calls");
            
            // Business logic using context
            if (context.getEndTime() != null) {
                // This was a call that ended
                long duration = context.getCallDuration().toSeconds();
                System.out.println("📞 Call " + context.getCallId() + " ended after " + duration + " seconds");
                
                // Log call completion
                context.addSessionEvent("Call completed - total duration: " + duration + "s");
                
                // Check if it was a long call for analytics
                if (context.isLongCall()) {
                    System.out.println("📊 Long call detected for analytics");
                    context.addSessionEvent("Long call flagged for quality review");
                }
            } else {
                // Fresh idle state
                System.out.println("📞 Call machine initialized and ready for incoming calls");
            }
        } else {
            System.out.println("📞 Call machine is now IDLE and ready for incoming calls");
        }
    }
}
