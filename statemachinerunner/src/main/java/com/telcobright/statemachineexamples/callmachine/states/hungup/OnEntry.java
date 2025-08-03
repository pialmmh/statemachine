package com.telcobright.statemachineexamples.callmachine.states.hungup;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.events.StateMachineEvent;

/**
 * Entry handler for HUNGUP state
 */
public class OnEntry {
    public static void handle(GenericStateMachine ctx, StateMachineEvent e) {
        // TODO: Replace this with your business logic
        System.out.println("📵 Call ended");
        
        // Example business logic:
        // - Log call completion
        // - Release all call resources
        // - Update call statistics
        // - Notify billing system
    }
}
