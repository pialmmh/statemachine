package com.telcobright.statemachineexamples.callmachine;

import com.telcobright.statemachine.dsl.StateMachineDsl;

/**
 * Scaffolding specification for the CallMachine state machine.
 * Used by code generation tools to generate the package structure and placeholder classes.
 */
public class CallMachineScaffolder {

    public static void define() {
        StateMachineDsl.packageBase("com.telcobright.statemachine.examples.callmachine")
            .define("CallMachine")
            .persistedIn("mysql")
            .startWith("IDLE")

            .state("IDLE")
                .onEvent("IncomingCall").goTo("RINGING")
                .endState()

            .state("RINGING").offline()
                .onEvent("Answer").goTo("CONNECTED")
                .onEvent("Hangup").goTo("IDLE")
                .stayOn("SessionProgress", "handleSessionProgressInRinging")
                .endState()

            .state("CONNECTED")
                .onEvent("Hangup").goTo("IDLE")
                .stayOn("Dtmf", "handleDtmfInConnected")
                .endState()
            .build();
    }
}
