package com.telcobright.statemachineexamples.callmachine.states.ringing;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.events.StateMachineEvent;

/**
 * Entry handler for RINGING state
 */
public class OnEntry {
    public static void handle(GenericStateMachine ctx, StateMachineEvent e) {
        // TODO: Replace this with your business logic
        System.out.println("📞 Phone is ringing...");
        
        // Example business logic:
        // - Start ring tone generation
        // - Begin timeout timer management
        // - Send ringing signal to caller
        // - Log call attempt
    }
}
