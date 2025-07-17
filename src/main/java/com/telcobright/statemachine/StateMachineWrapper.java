package com.telcobright.statemachine;

import java.util.function.Consumer;

import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachine.state.EnhancedStateConfig;
import com.telcobright.statemachine.timeout.TimeoutConfig;

/**
 * High-level wrapper for GenericStateMachine providing a fluent API
 */
public class StateMachineWrapper {
    private final GenericStateMachine stateMachine;
    private final FluentStateMachineBuilder builder;
    
    private StateMachineWrapper(GenericStateMachine stateMachine) {
        this.stateMachine = stateMachine;
        this.builder = null;
    }
    
    private StateMachineWrapper(FluentStateMachineBuilder builder) {
        this.builder = builder;
        this.stateMachine = null;
    }
    
    /**
     * Create a new StateMachineWrapper
     */
    public static StateMachineWrapper create(GenericStateMachine stateMachine) {
        return new StateMachineWrapper(stateMachine);
    }
    
    /**
     * Create a new StateMachineWrapper with builder
     */
    public static StateMachineWrapper createWithBuilder(String machineId) {
        return new StateMachineWrapper(FluentStateMachineBuilder.create(machineId));
    }
    
    /**
     * Define a simple state
     */
    public StateMachineWrapper state(String stateId) {
        stateMachine.state(stateId, new EnhancedStateConfig(stateId));
        return this;
    }
    
    /**
     * Define a state with timeout
     */
    public StateMachineWrapper state(String stateId, TimeoutConfig timeout) {
        stateMachine.state(stateId, new EnhancedStateConfig(stateId).timeout(timeout));
        return this;
    }
    
    /**
     * Define an offline state
     */
    public StateMachineWrapper offlineState(String stateId) {
        stateMachine.state(stateId, new EnhancedStateConfig(stateId).offline());
        return this;
    }
    
    /**
     * Define a state with entry and exit actions
     */
    public StateMachineWrapper state(String stateId, Runnable onEntry, Runnable onExit) {
        stateMachine.state(stateId, new EnhancedStateConfig(stateId).onEntry(onEntry).onExit(onExit));
        return this;
    }
    
    /**
     * Define a complete state configuration
     */
    public StateMachineWrapper state(String stateId, EnhancedStateConfig config) {
        stateMachine.state(stateId, config);
        return this;
    }
    
    /**
     * Define a transition
     */
    public StateMachineWrapper transition(String fromState, String event, String toState) {
        stateMachine.transition(fromState, event, toState);
        return this;
    }
    
    /**
     * Set initial state
     */
    public StateMachineWrapper initialState(String state) {
        stateMachine.initialState(state);
        return this;
    }
    
    /**
     * Set state transition callback
     */
    public StateMachineWrapper onStateTransition(Consumer<String> callback) {
        stateMachine.setOnStateTransition(callback);
        return this;
    }
    
    /**
     * Set offline transition callback
     */
    public StateMachineWrapper onOfflineTransition(Consumer<GenericStateMachine> callback) {
        stateMachine.setOnOfflineTransition(callback);
        return this;
    }
    
    /**
     * Build and return the configured state machine
     */
    public GenericStateMachine build() {
        return stateMachine;
    }
    
    /**
     * Build and start the state machine
     */
    public StateMachineWrapper buildAndStart() {
        stateMachine.start();
        return this;
    }
    
    // Delegation methods for common operations
    public void fire(StateMachineEvent event) {
        stateMachine.fire(event);
    }
    
    public void fire(String eventType) {
        stateMachine.fire(eventType);
    }
    
    public void transitionTo(String newState) {
        stateMachine.transitionTo(newState);
    }
    
    public String getCurrentState() {
        return stateMachine.getCurrentState();
    }
    
    public String getId() {
        return stateMachine.getId();
    }
    
    public boolean isInState(String state) {
        return stateMachine.isInState(state);
    }
    
    public void setContext(Object context) {
        stateMachine.setContext(context);
    }
    
    public Object getContext() {
        return stateMachine.getContext();
    }
    
    // Legacy methods for backward compatibility
    public void initialize() {
        stateMachine.start();
        System.out.println("StateMachineWrapper initialized.");
    }
    
    public void execute(String newState) {
        stateMachine.transitionTo(newState);
        System.out.println("StateMachineWrapper executing transition.");
    }
    
    public void inspect() {
        System.out.println("Inspecting StateMachineWrapper: " + stateMachine.getId() + 
                         " current state is " + stateMachine.getCurrentState());
    }
}