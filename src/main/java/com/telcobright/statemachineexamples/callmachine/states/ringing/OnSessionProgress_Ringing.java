package com.telcobright.statemachineexamples.callmachine.states.ringing;

import com.telcobright.statemachine.events.StateMachineEvent;

/**
 * DTMF event handler for RINGING state - stays in same state
 */
public class OnSessionProgress_Ringing {
    public static void handle(StateMachineEvent event) {
        System.out.println("🎵 DTMF tone received during RINGING state: " + event.getPayload());
        System.out.println("   → Processing early media DTMF");
    }
}