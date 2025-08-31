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
import com.telcobright.statemachine.db.PartitionedRepository;
import com.telcobright.statemachine.state.EnhancedStateConfig;
import com.telcobright.statemachine.timeout.TimeUnit;
import com.telcobright.statemachine.timeout.TimeoutConfig;
import com.telcobright.statemachine.monitoring.SnapshotRecorder;
import com.telcobright.statemachine.monitoring.SnapshotConfig;
import com.telcobright.statemachine.monitoring.DefaultSnapshotRecorder;
import com.telcobright.statemachine.monitoring.DatabaseSnapshotRecorder;
import com.telcobright.statemachine.persistence.StateMachineSnapshotRepository;

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
    
    // Debug and monitoring configuration
    private boolean debugEnabled = false;
    private SnapshotRecorder<TPersistingEntity, TContext> snapshotRecorder;
    private String runId;
    private String correlationId;
    private String debugSessionId;
    
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
     * Enable debug mode with default snapshot recorder
     */
    public FluentStateMachineBuilder<TPersistingEntity, TContext> enableDebug() {
        return enableDebug(new DefaultSnapshotRecorder<>());
    }
    
    /**
     * Enable debug mode with comprehensive snapshot configuration
     */
    public FluentStateMachineBuilder<TPersistingEntity, TContext> enableDebugComprehensive() {
        return enableDebug(new DefaultSnapshotRecorder<>(SnapshotConfig.comprehensiveConfig()));
    }
    
    /**
     * Enable debug mode with production-safe snapshot configuration
     */
    public FluentStateMachineBuilder<TPersistingEntity, TContext> enableDebugProduction() {
        return enableDebug(new DefaultSnapshotRecorder<>(SnapshotConfig.productionConfig()));
    }
    
    /**
     * Enable debug mode with custom snapshot recorder
     */
    public FluentStateMachineBuilder<TPersistingEntity, TContext> enableDebug(SnapshotRecorder<TPersistingEntity, TContext> snapshotRecorder) {
        this.debugEnabled = true;
        this.snapshotRecorder = snapshotRecorder;
        return this;
    }
    
    /**
     * Disable debug mode
     */
    public FluentStateMachineBuilder<TPersistingEntity, TContext> disableDebug() {
        this.debugEnabled = false;
        this.snapshotRecorder = null;
        return this;
    }
    
    /**
     * Enable debug mode with database persistence using partitioned repository
     * Auto-generates entity-specific snapshot classes (e.g., CallEntity -> CallEntitySnapshot)
     * Snapshots are encoded as JSON+Base64 for efficient storage
     */
    public FluentStateMachineBuilder<TPersistingEntity, TContext> enableDebugWithDatabase(
            StateMachineSnapshotRepository repository, 
            Class<TPersistingEntity> entityClass) {
        return enableDebugWithDatabase(repository, entityClass, SnapshotConfig.comprehensiveConfig());
    }
    
    /**
     * Enable debug mode with database persistence and custom configuration
     */
    public FluentStateMachineBuilder<TPersistingEntity, TContext> enableDebugWithDatabase(
            StateMachineSnapshotRepository repository,
            Class<TPersistingEntity> entityClass,
            SnapshotConfig config) {
        
        this.debugEnabled = true;
        this.entityClass = entityClass;
        
        // Create database-backed recorder with automatic entity snapshot generation
        DatabaseSnapshotRecorder<TPersistingEntity, TContext> dbRecorder = 
            new DatabaseSnapshotRecorder<>(config, repository, entityClass);
        
        this.snapshotRecorder = dbRecorder;
        
        System.out.println("📊 Database debug mode enabled:");
        System.out.println("   • Entity Type: " + entityClass.getSimpleName());
        System.out.println("   • Expected Snapshot Class: " + entityClass.getSimpleName() + "Snapshot");
        System.out.println("   • Encoding: JSON + Base64");
        System.out.println("   • Repository: " + repository.getClass().getSimpleName());
        
        return this;
    }
    
    /**
     * Enable debug mode with automatic run ID generation based on current timestamp
     * Perfect for tracking complete runtime history like XState
     */
    public FluentStateMachineBuilder<TPersistingEntity, TContext> enableDebugWithAutoRunId() {
        this.debugEnabled = true;
        
        // Auto-generate timestamp-based run ID
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        String timestamp = java.time.LocalDateTime.now().format(formatter);
        String randomSuffix = String.valueOf(System.nanoTime()).substring(8);
        this.runId = this.machineId.toLowerCase() + "-" + timestamp + "-" + randomSuffix;
        
        // Use default comprehensive recorder if none specified
        if (this.snapshotRecorder == null) {
            this.snapshotRecorder = new DefaultSnapshotRecorder<>(SnapshotConfig.comprehensiveConfig());
        }
        
        System.out.println("🔍 Auto-debug mode enabled with Run ID: " + this.runId);
        
        return this;
    }
    
    /**
     * Set run ID for correlation
     */
    public FluentStateMachineBuilder<TPersistingEntity, TContext> withRunId(String runId) {
        this.runId = runId;
        return this;
    }
    
    /**
     * Set correlation ID for tracking
     */
    public FluentStateMachineBuilder<TPersistingEntity, TContext> withCorrelationId(String correlationId) {
        this.correlationId = correlationId;
        return this;
    }
    
    /**
     * Set debug session ID
     */
    public FluentStateMachineBuilder<TPersistingEntity, TContext> withDebugSessionId(String debugSessionId) {
        this.debugSessionId = debugSessionId;
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
        
        // Configure debug mode if enabled
        if (debugEnabled && snapshotRecorder != null) {
            stateMachine.enableDebug(snapshotRecorder);
            if (runId != null) {
                stateMachine.setRunId(runId);
            }
            if (correlationId != null) {
                stateMachine.setCorrelationId(correlationId);
            }
            if (debugSessionId != null) {
                stateMachine.setDebugSessionId(debugSessionId);
            }
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
         * Set timeout for this state (uses default TIMEOUT event)
         */
        public StateBuilder timeout(long duration, TimeUnit unit) {
            TimeoutConfig timeout = new TimeoutConfig(duration, unit, "TIMEOUT");
            stateConfig.timeout(timeout);
            return this;
        }
        
        /**
         * Set timeout for this state with specific target state
         */
        public StateBuilder timeout(long duration, TimeUnit unit, String targetState) {
            TimeoutConfig timeout = new TimeoutConfig(duration, unit, targetState);
            stateConfig.timeout(timeout);
            return this;
        }
        
        /**
         * Set timeout with specific target state and action
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
         * Set entry action for this state
         */
        public StateBuilder onEntry(Runnable entryAction) {
            stateConfig.onEntry(state -> entryAction.run());
            return this;
        }
        
        /**
         * Set entry action for this state with machine access
         */
        public StateBuilder onEntry(java.util.function.Consumer<GenericStateMachine<TPersistingEntity, TContext>> entryAction) {
            stateConfig.onEntry(state -> entryAction.accept(stateMachine));
            return this;
        }
        
        /**
         * Set exit action for this state
         */
        public StateBuilder onExit(Runnable exitAction) {
            stateConfig.onExit(state -> exitAction.run());
            return this;
        }
        
        /**
         * Set exit action for this state with machine access
         */
        public StateBuilder onExit(java.util.function.Consumer<GenericStateMachine<TPersistingEntity, TContext>> exitAction) {
            stateConfig.onExit(state -> exitAction.accept(stateMachine));
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
                System.out.println("[Builder] Registering transition: " + stateId + " --[" + entry.getKey().getSimpleName() + "]--> " + entry.getValue());
                stateMachine.transition(stateId, entry.getKey(), entry.getValue());
            }
        }
        
        /**
         * Automatically register OnEntry and OnExit handlers based on package structure
         */
        private void registerImplicitHandlers() {
            // Use a naming convention: com.telcobright.examples.callmachine.<statename>
            String basePackage = "com.telcobright.examples.callmachine";
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
         * Set the target state for this transition
         */
        public StateBuilder target(String targetState) {
            stateBuilder.addEventTransition(eventType, targetState);
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
