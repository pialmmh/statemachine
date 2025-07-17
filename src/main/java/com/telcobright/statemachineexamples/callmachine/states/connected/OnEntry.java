package com.telcobright.statemachineexamples.callmachine.states.connected;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.events.StateMachineEvent;

/**
 * Entry handler for CONNECTED state
 */
public class OnEntry {
    public static void handle(GenericStateMachine ctx, StateMachineEvent e) {
        // TODO: Replace this with your business logic
        System.out.println("✅ Call connected - conversation started");
        
        // Example business logic:
        // - Establish media streams
        // - Start call duration tracking
        // - Initialize voice processing
        // - Enable call recording if configured
    }
}
