package com.telcobright.statemachineexamples.callmachine.states.connected;

import com.telcobright.statemachine.events.StateMachineEvent;

/**
 * DTMF event handler for CONNECTED state - stays in same state
 */
public class OnDtmf_Connected {
    public static void handle(StateMachineEvent event) {
        System.out.println("🎵 DTMF tone received during CONNECTED state: " + event.getPayload());
        System.out.println("   → Processing in-call DTMF for IVR/menu navigation");
    }
}