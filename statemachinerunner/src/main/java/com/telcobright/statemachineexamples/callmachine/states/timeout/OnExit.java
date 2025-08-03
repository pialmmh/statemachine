package com.telcobright.statemachineexamples.callmachine.states.timeout;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.events.StateMachineEvent;

/**
 * Exit handler for TIMEOUT state
 */
public class OnExit {
    public static void handle(GenericStateMachine ctx, StateMachineEvent e) {
        // TODO: Replace this with your business logic
        System.out.println("⏰ Cleaning up after timeout");
        
        // Example business logic:
        // - Final cleanup of missed call
        // - Archive missed call logs
        // - Reset timeout counters
    }
}
