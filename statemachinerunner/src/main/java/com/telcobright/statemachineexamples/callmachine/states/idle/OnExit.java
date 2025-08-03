package com.telcobright.statemachineexamples.callmachine.states.idle;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.events.StateMachineEvent;

/**
 * Exit handler for IDLE state
 */
public class OnExit {
    public static void handle(GenericStateMachine ctx, StateMachineEvent e) {
        // TODO: Replace this with your business logic
        System.out.println("📱 Leaving idle state");
        
        // Example business logic:
        // - Log state transition
        // - Cleanup idle resources
        // - Prepare for active call state
    }
}
