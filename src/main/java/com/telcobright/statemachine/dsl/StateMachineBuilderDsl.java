package com.telcobright.statemachine.dsl;

public class StateMachineBuilderDsl {

    public StateMachineBuilderDsl define(String machineName) {
        return this;
    }

    public StateMachineBuilderDsl persistedIn(String persistenceType) {
        return this;
    }

    public StateMachineBuilderDsl onSave(String methodName) {
        return this;
    }

    public StateMachineBuilderDsl onLoad(String methodName) {
        return this;
    }

    public StateMachineBuilderDsl onInit(String methodName) {
        return this;
    }

    public StateMachineBuilderDsl startWith(String stateName) {
        return this;
    }

    public StateDefinitionDsl state(String stateName) {
        return new StateDefinitionDsl(this);
    }

    public StateMachineBuilderDsl contextFields(String... fields) {
        return this;
    }

    public void build() {
        // No-op
    }
}
