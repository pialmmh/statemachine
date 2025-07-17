package com.telcobright.statemachineexamples.callmachine.states.connected;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.events.StateMachineEvent;

/**
 * Exit handler for CONNECTED state
 */
public class OnExit {
    public static void handle(GenericStateMachine ctx, StateMachineEvent e) {
        // TODO: Replace this with your business logic
        System.out.println("📴 Call ending");
        
        // Example business logic:
        // - Stop call duration tracking
        // - Finalize call recording
        // - Release media resources
        // - Calculate call charges
    }
}
