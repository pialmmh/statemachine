package com.telcobright.statemachineexamples.smsmachine;

import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachine.state.EnhancedStateConfig;
import com.telcobright.statemachineexamples.smsmachine.events.*;
import com.telcobright.statemachineexamples.smsmachine.states.sending.OnStatusUpdate_SENDING;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fluent builder for SmsMachine with builder syntax
 */
public class SmsMachineBuilder {
    private final String machineId;
    private SmsMachine machine;
    private StateBuilder currentStateBuilder;
    private String initialState;

    private SmsMachineBuilder(String machineId) {
        this.machineId = machineId;
        this.machine = new SmsMachine(machineId, null, null, null);
    }

    public static SmsMachineBuilder create(String machineId) {
        return new SmsMachineBuilder(machineId);
    }

    public SmsMachineBuilder initialState(SmsMachineState state) {
        this.initialState = state.toString();
        return this;
    }

    public StateBuilder state(SmsMachineState stateId) {
        // Finish previous state if any
        if (currentStateBuilder != null) {
            currentStateBuilder.finishState();
        }
        
        currentStateBuilder = new StateBuilder(stateId.toString());
        return currentStateBuilder;
    }

    public SmsMachine build() {
        // Finish any pending state configuration
        if (currentStateBuilder != null) {
            currentStateBuilder.finishState();
        }
        
        // Set initial state
        if (initialState != null) {
            machine.initialState(initialState);
        }
        
        return machine;
    }

    /**
     * State configuration builder
     */
    public class StateBuilder {
        private final String stateId;
        private final EnhancedStateConfig stateConfig;
        private final Map<Class<? extends StateMachineEvent>, String> eventTransitions = new HashMap<>();
        
        private StateBuilder(String stateId) {
            this.stateId = stateId;
            this.stateConfig = new EnhancedStateConfig(stateId);
        }
        
        /**
         * Mark this state as offline
         */
        public StateBuilder offline() {
            stateConfig.offline();
            return this;
        }
        
        /**
         * Start defining a transition on an event
         */
        public TransitionBuilder on(Class<? extends StateMachineEvent> eventType) {
            return new TransitionBuilder(this, eventType);
        }
        
        /**
         * Define a stay action - handle an event within state without transitioning
         */
        public StateBuilder stay(Class<? extends StateMachineEvent> eventType, Consumer<StateMachineEvent> action) {
            String eventTypeName = eventType.getSimpleName().toUpperCase();
            machine.stayAction(stateId, eventTypeName, action);
            return this;
        }
        
        /**
         * Continue with next state (same as done())
         */
        public SmsMachineBuilder then() {
            return done();
        }
        
        /**
         * Finish configuring this state and return to main builder
         */
        public SmsMachineBuilder done() {
            finishState();
            currentStateBuilder = null;
            return SmsMachineBuilder.this;
        }
        
        private void finishState() {
            // Register the state with the state machine
            machine.state(stateId, stateConfig);
            
            // Register any event transitions
            for (Map.Entry<Class<? extends StateMachineEvent>, String> entry : eventTransitions.entrySet()) {
                String eventType = entry.getKey().getSimpleName();
                machine.transition(stateId, eventType, entry.getValue());
            }
        }
        
        private void addEventTransition(Class<? extends StateMachineEvent> eventType, String targetState) {
            eventTransitions.put(eventType, targetState);
        }
    }
    
    /**
     * Transition configuration builder
     */
    public class TransitionBuilder {
        private final StateBuilder stateBuilder;
        private final Class<? extends StateMachineEvent> eventType;
        
        private TransitionBuilder(StateBuilder stateBuilder, Class<? extends StateMachineEvent> eventType) {
            this.stateBuilder = stateBuilder;
            this.eventType = eventType;
        }
        
        /**
         * Set the target state for this transition using enum
         */
        public StateBuilder to(SmsMachineState targetState) {
            stateBuilder.addEventTransition(eventType, targetState.toString());
            return stateBuilder;
        }
    }
}