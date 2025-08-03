package com.telcobright.statemachineexamples.callmachine.states.timeout;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.events.StateMachineEvent;

/**
 * Entry handler for TIMEOUT state
 */
public class OnEntry {
    public static void handle(GenericStateMachine ctx, StateMachineEvent e) {
        // TODO: Replace this with your business logic
        System.out.println("⏰ Call timed out - missed call");
        
        // Example business logic:
        // - Log missed call event
        // - Send missed call notification
        // - Update statistics
        // - Clean up resources
    }
}
