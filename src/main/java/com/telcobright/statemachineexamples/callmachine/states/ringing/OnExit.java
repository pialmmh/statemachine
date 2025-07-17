package com.telcobright.statemachineexamples.callmachine.states.ringing;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.events.StateMachineEvent;

/**
 * Exit handler for RINGING state
 */
public class OnExit {
    public static void handle(GenericStateMachine ctx, StateMachineEvent e) {
        // TODO: Replace this with your business logic
        System.out.println("📞 Stopping ringing...");
        
        // Example business logic:
        // - Stop ring tone generation
        // - Cancel timeout timer
        // - Log ring duration
    }
}
