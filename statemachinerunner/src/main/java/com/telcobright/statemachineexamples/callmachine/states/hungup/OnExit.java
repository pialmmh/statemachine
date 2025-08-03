package com.telcobright.statemachineexamples.callmachine.states.hungup;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.events.StateMachineEvent;

/**
 * Exit handler for HUNGUP state
 */
public class OnExit {
    public static void handle(GenericStateMachine ctx, StateMachineEvent e) {
        // TODO: Replace this with your business logic
        System.out.println("📵 Cleaning up after call end");
        
        // Example business logic:
        // - Final cleanup of call session
        // - Archive call logs
        // - Reset state variables
    }
}
