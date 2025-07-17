package com.telcobright.statemachine.dsl;

public class StateDefinitionDsl {

    private final StateMachineBuilderDsl parent;

    public StateDefinitionDsl(StateMachineBuilderDsl parent) {
        this.parent = parent;
    }

    public StateDefinitionDsl offline() {
        return this;
    }

    public StateDefinitionDsl onEvent(String eventName) {
        return this;
    }

    public StateDefinitionDsl goTo(String targetState) {
        return this;
    }

    public StateDefinitionDsl stayOn(String eventName, String handlerMethodName) {
        return this;
    }

    public StateMachineBuilderDsl endState() {
        return parent;
    }
}
