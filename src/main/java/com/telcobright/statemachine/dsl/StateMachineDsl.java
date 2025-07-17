package com.telcobright.statemachine.dsl;

public class StateMachineDsl {
    public static StateMachineBuilderDsl packageBase(String basePackage) {
        return new StateMachineBuilderDsl();
    }
}
