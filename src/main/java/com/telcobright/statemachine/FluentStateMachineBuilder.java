package com.telcobright.statemachine;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachine.events.EventTypeRegistry;
import com.telcobright.statemachine.persistence.ShardingEntityStateMachineRepository;
import com.telcobright.statemachine.StateMachineContextEntity;
import com.telcobright.statemachine.persistence.IdLookUpMode;
import com.telcobright.db.PartitionedRepository;
import com.telcobright.statemachine.state.EnhancedStateConfig;
import com.telcobright.statemachine.timeout.TimeUnit;
import com.telcobright.statemachine.timeout.TimeoutConfig;

/**
 * Fluent builder for creating state machines with expressive syntax
 * @param <TPersistingEntity> the StateMachineContextEntity type that gets persisted
 * @param <TContext> the volatile context type (not persisted)
 */
public class FluentStateMachineBuilder<TPersistingEntity extends StateMachineContextEntity<?>, TContext> {
    private final String machineId;
    private GenericStateMachine<TPersistingEntity, TContext> stateMachine;
    private String initialState;
    private String finalState;
    private Class<? extends StateMachineEvent> timeoutEventType;
    
    
    // ShardingEntity persistence configuration
    private PartitionedRepository<TPersistingEntity, Object> shardingRepository;
    private IdLookUpMode shardingLookupMode;
    private Class<TPersistingEntity> entityClass;
    
    // Current state being configured
    private StateBuilder currentStateBuilder;
    private TransitionBuilder currentTransitionBuilder;
    
    private FluentStateMachineBuilder(String machineId) {
        this.machineId = machineId;
        // Don't create the state machine yet - wait until we know the persistence configuration
    }
    
    /**
     * Create a new fluent builder
     */
    public static <TPersistingEntity extends StateMachineContextEntity<?>, TContext> FluentStateMachineBuilder<TPersistingEntity, TContext> create(String machineId) {
        return new FluentStateMachineBuilder<>(machineId);
    }
    
    
    
    /**
     * Configure partitioned repository persistence with ById lookup mode for StateMachineContextEntity
     */
    public FluentStateMachineBuilder<TPersistingEntity, TContext> withShardingRepo(PartitionedRepository<TPersistingEntity, ?> partitionedRepo, Class<TPersistingEntity> entityClass) {
        return withShardingRepo(partitionedRepo, IdLookUpMode.ById, entityClass);
    }
    
    /**
     * Configure partitioned repository persistence with specified lookup mode for StateMachineContextEntity
     */
    @SuppressWarnings("unchecked")
    public FluentStateMachineBuilder<TPersistingEntity, TContext> withShardingRepo(PartitionedRepository<TPersistingEntity, ?> partitionedRepo, IdLookUpMode lookupMode, Class<TPersistingEntity> entityClass) {
        // Store the sharding repository for use in build()
        this.shardingRepository = (PartitionedRepository<TPersistingEntity, Object>) partitionedRepo;
        this.shardingLookupMode = lookupMode;
        this.entityClass = entityClass;
        return this;
    }
    
    
    /**
     * Set the timeout event type for all states
     */
    public FluentStateMachineBuilder<TPersistingEntity, TContext> withTimeoutEventType(Class<? extends StateMachineEvent> eventType) {
        this.timeoutEventType = eventType;
        return this;
    }
    
    /**
     * Set the initial state
     */
    public FluentStateMachineBuilder<TPersistingEntity, TContext> initialState(String state) {
        this.initialState = state;
        return this;
    }
    
    /**
     * Set the initial state using enum
     */
    public FluentStateMachineBuilder<TPersistingEntity, TContext> initialState(Enum<?> state) {
        this.initialState = state.toString();
        return this;
    }
    
    /**
     * Set the final state
     */
    public FluentStateMachineBuilder<TPersistingEntity, TContext> finalState(String state) {
        this.finalState = state;
        return this;
    }
    
    /**
     * Set the final state using enum
     */
    public FluentStateMachineBuilder<TPersistingEntity, TContext> finalState(Enum<?> state) {
        this.finalState = state.toString();
        return this;
    }
    
    /**
     * Start configuring a state
     */
    public StateBuilder state(String stateId) {
        // Ensure state machine is created
        ensureStateMachineCreated();
        
        // Finish previous state if any
        if (currentStateBuilder != null) {
            currentStateBuilder.finishState();
        }
        
        currentStateBuilder = new StateBuilder(stateId);
        return currentStateBuilder;
    }
    
    /**
     * Ensure the state machine is created with proper persistence
     */
    private void ensureStateMachineCreated() {
        if (stateMachine == null) {
            if (shardingRepository != null && entityClass != null) {
                // Using ShardingEntity-based persistence - create directly
                stateMachine = createStateMachineWithShardingRepo();
            } else {
                // Use default registry (in-memory) - create directly for simplicity
                stateMachine = new GenericStateMachine<>(
                    machineId,
                    StateMachineFactory.getDefaultTimeoutManager(),
                    StateMachineFactory.getDefaultRegistry()
                );
            }
        }
    }
    
    /**
     * Create state machine with ShardingEntity-based persistence
     * TODO: Implement full ShardingEntity integration
     */
    private GenericStateMachine<TPersistingEntity, TContext> createStateMachineWithShardingRepo() {
        // For now, create a simple state machine without full ShardingEntity integration
        // This will be enhanced in future iterations
        return new GenericStateMachine<>(
            machineId,
            StateMachineFactory.getDefaultTimeoutManager(),
            StateMachineFactory.getDefaultRegistry()
        );
    }
    
    /**
     * Start configuring a state using enum
     */
    public StateBuilder state(Enum<?> stateId) {
        return state(stateId.toString());
    }
    
    /**
     * Direct enum-based state configuration - simplified API
     */
    public StateBuilder from(Enum<?> stateId) {
        return state(stateId.toString());
    }

    /**
     * Build and return the configured state machine
     */
    public GenericStateMachine<TPersistingEntity, TContext> build() {
        // Ensure state machine is created
        ensureStateMachineCreated();
        
        // Finish any pending state configuration
        if (currentStateBuilder != null) {
            currentStateBuilder.finishState();
        }
        
        // Set initial state
        if (initialState != null) {
            stateMachine.initialState(initialState);
        }
        
        return stateMachine;
    }
    
    /**
     * Build and start the state machine
     */
    public GenericStateMachine<TPersistingEntity, TContext> buildAndStart() {
        GenericStateMachine<TPersistingEntity, TContext> machine = build();
        machine.start();
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
         * Set timeout for this state
         */
        public StateBuilder timeout(long duration, TimeUnit unit) {
            TimeoutConfig timeout = new TimeoutConfig(duration, unit, "TIMEOUT");
            stateConfig.timeout(timeout);
            return this;
        }
        
        /**
         * Set timeout with specific target state
         */
        public StateBuilder onTimeout(String targetState, BiConsumer<GenericStateMachine<TPersistingEntity, TContext>, StateMachineEvent> action) {
            // Store timeout action - we'll need to enhance the state config to support this
            return this;
        }

        // OnEntry and OnExit methods removed - they are now implicit and handled automatically
        
        /**
         * Start defining a transition on an event
         */
        public TransitionBuilder on(Class<? extends StateMachineEvent> eventType) {
            currentTransitionBuilder = new TransitionBuilder(this, eventType);
            return currentTransitionBuilder;
        }
        
        /**
         * Start defining a transition on a string event
         */
        public TransitionBuilder on(String eventName) {
            currentTransitionBuilder = new TransitionBuilder(this, eventName);
            return currentTransitionBuilder;
        }
        
        /**
         * Mark this state as offline
         */
        public StateBuilder offline() {
            stateConfig.offline();
            return this;
        }
        
        /**
         * Mark this state as a final state (terminal state)
         */
        public StateBuilder finalState() {
            stateConfig.finalState();
            return this;
        }
        
        /**
         * Continue with next state (same as done())
         */
        public FluentStateMachineBuilder<TPersistingEntity, TContext> then() {
            return done();
        }
        
        /**
         * Finish configuring this state and return to main builder
         */
        public FluentStateMachineBuilder<TPersistingEntity, TContext> done() {
            finishState();
            currentStateBuilder = null;  // Clear current state
            return FluentStateMachineBuilder.this;
        }
        
        /**
         * Define a stay action - handle an event within state without transitioning
         */
        public StateBuilder stay(Class<? extends StateMachineEvent> eventType, BiConsumer<GenericStateMachine<TPersistingEntity, TContext>, StateMachineEvent> action) {
            // Use EventTypeRegistry to avoid reflection
            String eventTypeName = EventTypeRegistry.getEventType(eventType);
            stateMachine.stayAction(stateId, eventTypeName, action);
            return this;
        }

        /**
         * Define a stay action with string event type
         */
        public StateBuilder stay(String eventType, BiConsumer<GenericStateMachine<TPersistingEntity, TContext>, StateMachineEvent> action) {
            stateMachine.stayAction(stateId, eventType, action);
            return this;
        }
        
        private void finishState() {
            // Automatically register OnEntry and OnExit handlers if they exist
            registerImplicitHandlers();
            
            // Register the state with the state machine
            stateMachine.state(stateId, stateConfig);
            
            // Register any event transitions
            for (Map.Entry<Class<? extends StateMachineEvent>, String> entry : eventTransitions.entrySet()) {
                // Use EventTypeRegistry to avoid reflection
                String eventType = EventTypeRegistry.getEventType(entry.getKey());
                stateMachine.transition(stateId, eventType, entry.getValue());
            }
        }
        
        /**
         * Automatically register OnEntry and OnExit handlers based on package structure
         */
        private void registerImplicitHandlers() {
            // Use a naming convention: com.telcobright.statemachine.examples.callmachine.<statename>
            String basePackage = "com.telcobright.statemachine.examples.callmachine";
            String statePackage = basePackage + "." + stateId.toLowerCase();
            
            // Try to register OnEntry handler
            try {
                Class<?> onEntryClass = Class.forName(statePackage + ".OnEntry");
                java.lang.reflect.Method handleMethod = onEntryClass.getMethod("handle", 
                    com.telcobright.statemachine.GenericStateMachine.class, 
                    com.telcobright.statemachine.events.StateMachineEvent.class);
                
                stateConfig.onEntry(() -> {
                    try {
                        handleMethod.invoke(null, stateMachine, null);
                    } catch (Exception e) {
                        System.err.println("Error invoking OnEntry for state " + stateId + ": " + e.getMessage());
                    }
                });
                System.out.println("✅ Auto-registered OnEntry handler for state: " + stateId);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                // OnEntry handler not found - this is optional
                System.out.println("ℹ️ No OnEntry handler found for state: " + stateId);
            }
            
            // Try to register OnExit handler
            try {
                Class<?> onExitClass = Class.forName(statePackage + ".OnExit");
                java.lang.reflect.Method handleMethod = onExitClass.getMethod("handle", 
                    com.telcobright.statemachine.GenericStateMachine.class, 
                    com.telcobright.statemachine.events.StateMachineEvent.class);
                
                stateConfig.onExit(() -> {
                    try {
                        handleMethod.invoke(null, stateMachine, null);
                    } catch (Exception e) {
                        System.err.println("Error invoking OnExit for state " + stateId + ": " + e.getMessage());
                    }
                });
                System.out.println("✅ Auto-registered OnExit handler for state: " + stateId);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                // OnExit handler not found - this is optional
                System.out.println("ℹ️ No OnExit handler found for state: " + stateId);
            }
        }
        
        private void addEventTransition(Class<? extends StateMachineEvent> eventType, String targetState) {
            eventTransitions.put(eventType, targetState);
        }
        
        private void addEventTransition(String eventName, String targetState) {
            stateMachine.transition(stateId, eventName, targetState);
        }
    }
    
    /**
     * Transition configuration builder
     */
    public class TransitionBuilder {
        private final StateBuilder stateBuilder;
        private final Class<? extends StateMachineEvent> eventType;
        private final String eventName;
        
        private TransitionBuilder(StateBuilder stateBuilder, Class<? extends StateMachineEvent> eventType) {
            this.stateBuilder = stateBuilder;
            this.eventType = eventType;
            this.eventName = null;
        }
        
        private TransitionBuilder(StateBuilder stateBuilder, String eventName) {
            this.stateBuilder = stateBuilder;
            this.eventType = null;
            this.eventName = eventName;
        }
        
        /**
         * Set the target state for this transition
         */
        public StateBuilder target(String targetState) {
            if (eventType != null) {
                stateBuilder.addEventTransition(eventType, targetState);
            } else {
                stateBuilder.addEventTransition(eventName, targetState);
            }
            return stateBuilder;
        }
        
        /**
         * Set the target state for this transition using enum
         */
        public StateBuilder target(Enum<?> targetState) {
            return target(targetState.toString());
        }
        
        /**
         * Set the target state for this transition using enum (alias)
         */
        public StateBuilder to(Enum<?> targetState) {
            return target(targetState.toString());
        }
        
        /**
         * Set the target state for this transition using string (alias)
         */
        public StateBuilder to(String targetState) {
            return target(targetState);
        }
    }
}
