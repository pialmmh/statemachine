package com.telcobright.statemachineexamples.callmachine.states.connected;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachineexamples.callmachine.CallContext;
import com.telcobright.statemachineexamples.callmachine.events.Hangup;

/**
 * Exit handler for CONNECTED state
 */
public class OnExit {
    public static void handle(GenericStateMachine<CallContext> machine, StateMachineEvent event) {
        CallContext context = machine.getContext();
        
        if (context != null) {
            // Finalize the call in context
            context.endCall();
            
            // Determine disconnect reason
            String reason = "Normal disconnect";
            if (event instanceof Hangup) {
                reason = "User hangup";
            }
            context.setDisconnectReason(reason);
            
            long duration = context.getCallDuration().toSeconds();
            System.out.println("📴 Call ending: " + context.getFromNumber() + " ↔ " + context.getToNumber());
            System.out.println("   📋 Call ID: " + context.getCallId());
            System.out.println("   ⏱️ Call duration: " + duration + " seconds");
            System.out.println("   💡 Disconnect reason: " + reason);
            
            // Business logic based on call context
            if (context.isRecordingEnabled()) {
                System.out.println("🎙️ Stopping call recording");
                context.addSessionEvent("Call recording stopped");
            }
            
            // Generate call summary
            long ringTime = context.getRingDuration().toSeconds();
            System.out.println("📊 Call Summary:");
            System.out.println("   - Ring time: " + ringTime + "s");
            System.out.println("   - Talk time: " + duration + "s");
            System.out.println("   - Ring count: " + context.getRingCount());
            
            // Call analytics
            if (context.isLongCall()) {
                System.out.println("📈 Long call completed - updating analytics");
            }
            
            context.addSessionEvent("Call disconnected - all resources released");
            
        } else {
            System.out.println("📴 Call ending");
        }
    }
}
