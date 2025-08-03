package com.telcobright.statemachineexamples.callmachine.states.ringing;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachineexamples.callmachine.CallContext;
import com.telcobright.statemachineexamples.callmachine.events.SessionProgress;

/**
 * SessionProgress event handler for RINGING state - stays in same state
 * Handles early media and progress updates during ringing
 */
public class OnSessionProgress_Ringing {
    public static void handle(GenericStateMachine<CallContext> machine, StateMachineEvent event) {
        if (event instanceof SessionProgress) {
            SessionProgress progress = (SessionProgress) event;
            
            System.out.println("📡 Session progress during RINGING: " + progress.getDescription());
            System.out.println("   📊 Progress type: " + progress.getProgressType());
            System.out.println("   📈 Percentage: " + progress.getPercentage() + "%");
            
            // Handle different types of session progress by type or percentage
            String progressType = progress.getProgressType();
            if (progressType.contains("180") || progressType.toLowerCase().contains("ringing")) {
                System.out.println("   🔔 Ringing confirmation received");
            } else if (progressType.contains("183") || progressType.toLowerCase().contains("progress")) {
                System.out.println("   🎵 Early media established");
            } else if (progressType.contains("100") || progressType.toLowerCase().contains("trying")) {
                System.out.println("   📞 Call being processed");
            } else {
                System.out.println("   ℹ️ Other progress: " + progressType);
            }
        } else {
            System.out.println("🎵 Session progress during RINGING: " + event.getPayload());
        }
    }
}