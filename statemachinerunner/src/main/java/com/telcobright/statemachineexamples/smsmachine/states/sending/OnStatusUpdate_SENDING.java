package com.telcobright.statemachineexamples.smsmachine.states.sending;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachineexamples.smsmachine.SmsContext;

/**
 * StatusUpdate event handler for SENDING state - stays in same state
 */
public class OnStatusUpdate_SENDING {
    public static void handle(GenericStateMachine<SmsContext> machine, StateMachineEvent event) {
        System.out.println("🔄 StatusUpdate received in SENDING state: " + event.getPayload());
        // TODO: Implement handleStatusUpdate logic
    }
}