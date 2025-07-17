package com.telcobright.statemachineexamples.callmachine.states.idle;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.events.StateMachineEvent;

/**
 * Entry handler for IDLE state
 */
public class OnEntry {
    public static void handle(GenericStateMachine ctx, StateMachineEvent e) {
        // TODO: Replace this with your business logic
        System.out.println("📞 Call session ready - waiting for incoming call");
        
        // Example business logic:
        // - Initialize call session resources
        // - Reset call statistics
        // - Prepare telephony hardware
    }
}
