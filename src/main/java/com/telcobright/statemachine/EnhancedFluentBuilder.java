package com.telcobright.statemachine;

import com.telcobright.db.PartitionedRepository;
import com.telcobright.statemachine.persistence.IdLookUpMode;
import com.telcobright.statemachine.persistence.PersistenceProvider;
import com.telcobright.statemachine.persistence.BaseStateMachineEntity;
import com.telcobright.statemachine.persistence.OptimizedMySQLPersistenceProvider;
import com.telcobright.statemachine.persistence.MysqlConnectionProvider;
import java.util.function.Supplier;

/**
 * Enhanced Fluent Builder for State Machines with clear separation of persistent and volatile contexts
 * 
 * @param <TPersistingEntity> The entity type that gets persisted to database
 * @param <TVolatileContext> The runtime context that gets recreated on rehydration
 */
public class EnhancedFluentBuilder<TPersistingEntity extends StateMachineContextEntity<?>, TVolatileContext> {
    
    private final String machineId;
    private final FluentStateMachineBuilder<TPersistingEntity, TVolatileContext> delegateBuilder;
    private StateBuilder currentStateBuilder;
    
    // Context providers
    private TPersistingEntity persistingEntity;
    private Supplier<TVolatileContext> volatileContextFactory;
    private TVolatileContext volatileContext;
    
    // Rehydration configuration
    private boolean autoRehydrateVolatileContext = true;
    private PartitionedRepository<TPersistingEntity, ?> persistenceRepository;
    private PersistenceProvider<TPersistingEntity> persistenceProvider;
    
    // Sample logging configuration
    private SampleLoggingConfig sampleLoggingConfig = SampleLoggingConfig.ALL;
    
    private EnhancedFluentBuilder(String machineId) {
        this.machineId = machineId;
        this.delegateBuilder = FluentStateMachineBuilder.<TPersistingEntity, TVolatileContext>create(machineId);
    }
    
    /**
     * Create a new enhanced builder
     */
    public static <P extends StateMachineContextEntity<?>, V> EnhancedFluentBuilder<P, V> create(String machineId) {
        return new EnhancedFluentBuilder<>(machineId);
    }
    
    /**
     * Set the persistent entity that will be saved to database
     * This entity should contain all state that needs to survive restarts
     */
    public EnhancedFluentBuilder<TPersistingEntity, TVolatileContext> withPersistentContext(TPersistingEntity entity) {
        this.persistingEntity = entity;
        return this;
    }
    
    /**
     * Set the volatile context directly
     * This context is NOT persisted and will be lost on restart
     */
    public EnhancedFluentBuilder<TPersistingEntity, TVolatileContext> withVolatileContext(TVolatileContext context) {
        this.volatileContext = context;
        this.volatileContextFactory = () -> context; // Simple factory returning the same instance
        return this;
    }
    
    /**
     * Set a factory for creating volatile context
     * This factory will be called during rehydration to recreate the volatile context
     */
    public EnhancedFluentBuilder<TPersistingEntity, TVolatileContext> withVolatileContextFactory(
            Supplier<TVolatileContext> factory) {
        this.volatileContextFactory = factory;
        return this;
    }
    
    /**
     * Configure persistence repository for automatic persistence
     */
    public EnhancedFluentBuilder<TPersistingEntity, TVolatileContext> withPersistence(
            PartitionedRepository<TPersistingEntity, ?> repository, 
            IdLookUpMode lookupMode,
            Class<TPersistingEntity> entityClass) {
        this.persistenceRepository = repository;
        this.delegateBuilder.withShardingRepo(repository, lookupMode, entityClass);
        return this;
    }
    
    /**
     * Configure optimized persistence provider with auto-inferred table name
     * Table name is derived from entity class name (CamelCase -> snake_case)
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public EnhancedFluentBuilder<TPersistingEntity, TVolatileContext> withOptimizedPersistence(
            MysqlConnectionProvider connectionProvider,
            Class<? extends StateMachineContextEntity<?>> entityClass) {
        String tableName = OptimizedMySQLPersistenceProvider.inferTableName(entityClass);
        return withOptimizedPersistence(connectionProvider, entityClass, tableName);
    }
    
    /**
     * Configure optimized persistence provider with explicit table name
     * All writes are performed asynchronously for optimal performance
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public EnhancedFluentBuilder<TPersistingEntity, TVolatileContext> withOptimizedPersistence(
            MysqlConnectionProvider connectionProvider,
            Class<? extends StateMachineContextEntity<?>> entityClass,
            String tableName) {
        if (entityClass.isAssignableFrom(persistingEntity.getClass())) {
            OptimizedMySQLPersistenceProvider provider = 
                new OptimizedMySQLPersistenceProvider(connectionProvider, entityClass, tableName);
            this.persistenceProvider = provider;
            provider.initialize();
            System.out.println("[Builder] Configured optimized persistence for table: " + tableName);
        }
        return this;
    }
    
    /**
     * Configure sample logging - logs only 1 in N events to reduce database load
     * @param config Sample logging configuration
     */
    public EnhancedFluentBuilder<TPersistingEntity, TVolatileContext> withSampleLogging(SampleLoggingConfig config) {
        this.sampleLoggingConfig = config != null ? config : SampleLoggingConfig.DISABLED;
        System.out.println("[Builder] Configured sample logging: " + this.sampleLoggingConfig);
        return this;
    }
    
    /**
     * Convenience method for sample logging - 1 in N events
     */
    public EnhancedFluentBuilder<TPersistingEntity, TVolatileContext> withSampleLogging(int oneInN) {
        return withSampleLogging(SampleLoggingConfig.oneInN(oneInN));
    }
    
    /**
     * Set initial state
     */
    public EnhancedFluentBuilder<TPersistingEntity, TVolatileContext> initialState(String state) {
        delegateBuilder.initialState(state);
        return this;
    }
    
    /**
     * Define a state
     */
    public StateBuilder state(String stateName) {
        return new StateBuilder(stateName);
    }
    
    /**
     * Build the state machine with proper context setup
     */
    public GenericStateMachine<TPersistingEntity, TVolatileContext> build() {
        GenericStateMachine<TPersistingEntity, TVolatileContext> machine = delegateBuilder.build();
        
        // Set persistent entity if provided
        if (persistingEntity != null) {
            machine.setPersistingEntity(persistingEntity);
        }
        
        // Set or create volatile context
        if (volatileContext != null) {
            machine.setContext(volatileContext);
        } else if (volatileContextFactory != null) {
            machine.setContext(volatileContextFactory.get());
        }
        
        // Configure sample logging
        machine.setSampleLoggingConfig(sampleLoggingConfig);
        
        return machine;
    }
    
    /**
     * Build with rehydration support
     * Returns a factory that can be used with StateMachineRegistry.createOrGet()
     */
    public Supplier<GenericStateMachine<TPersistingEntity, TVolatileContext>> buildFactory() {
        return () -> {
            GenericStateMachine<TPersistingEntity, TVolatileContext> machine = build();
            
            // When rehydrating, the registry will:
            // 1. Load the persistent entity from database
            // 2. Call this factory to create the machine
            // 3. Set the loaded persistent entity
            // 4. We need to ensure volatile context is also created
            
            if (volatileContextFactory != null && machine.getContext() == null) {
                machine.setContext(volatileContextFactory.get());
            }
            
            return machine;
        };
    }
    
    /**
     * Create a rehydration-aware factory with custom volatile context initialization
     * based on the loaded persistent entity
     */
    public static <P extends StateMachineContextEntity<?>, V> 
            Supplier<GenericStateMachine<P, V>> createRehydrationFactory(
                String machineId,
                Supplier<FluentStateMachineBuilder<P, V>> machineDefinition,
                java.util.function.Function<P, V> volatileContextFromPersistent) {
        
        return () -> {
            GenericStateMachine<P, V> machine = machineDefinition.get().build();
            
            // After the registry sets the persistent entity, we can create the volatile context
            machine.setOnRehydration(() -> {
                P persistentEntity = machine.getPersistingEntity();
                if (persistentEntity != null && volatileContextFromPersistent != null) {
                    V volatileCtx = volatileContextFromPersistent.apply(persistentEntity);
                    machine.setContext(volatileCtx);
                }
            });
            
            return machine;
        };
    }
    
    /**
     * State builder delegate
     */
    public class StateBuilder {
        private final FluentStateMachineBuilder<TPersistingEntity, TVolatileContext>.StateBuilder delegateState;
        
        StateBuilder(String stateName) {
            this.delegateState = delegateBuilder.state(stateName);
            currentStateBuilder = this;
        }
        
        public StateBuilder offline() {
            delegateState.offline();
            return this;
        }
        
        public StateBuilder timeout(long duration, com.telcobright.statemachine.timeout.TimeUnit unit, String targetState) {
            delegateState.timeout(duration, unit, targetState);
            return this;
        }
        
        public StateBuilder onEntry(Runnable entryAction) {
            delegateState.onEntry(entryAction);
            return this;
        }
        
        public StateBuilder onExit(Runnable exitAction) {
            delegateState.onExit(exitAction);
            return this;
        }
        
        public StateBuilder onEntry(java.util.function.Consumer<GenericStateMachine<TPersistingEntity, TVolatileContext>> entryAction) {
            delegateState.onEntry(entryAction);
            return this;
        }
        
        public StateBuilder onExit(java.util.function.Consumer<GenericStateMachine<TPersistingEntity, TVolatileContext>> exitAction) {
            delegateState.onExit(exitAction);
            return this;
        }
        
        public TransitionBuilder on(Class<? extends com.telcobright.statemachine.events.StateMachineEvent> eventType) {
            return new TransitionBuilder(delegateState.on(eventType));
        }
        
        public TransitionBuilder on(String eventName) {
            return new TransitionBuilder(delegateState.on(eventName));
        }
        
        public StateBuilder stay(Class<? extends com.telcobright.statemachine.events.StateMachineEvent> eventType, 
                                 java.util.function.BiConsumer<GenericStateMachine<TPersistingEntity, TVolatileContext>, com.telcobright.statemachine.events.StateMachineEvent> action) {
            delegateState.stay(eventType, action);
            return this;
        }
        
        public StateBuilder finalState() {
            delegateState.finalState();
            return this;
        }
        
        public EnhancedFluentBuilder<TPersistingEntity, TVolatileContext> done() {
            delegateState.done();
            return EnhancedFluentBuilder.this;
        }
    }
    
    /**
     * Transition builder delegate
     */
    public class TransitionBuilder {
        private final FluentStateMachineBuilder<TPersistingEntity, TVolatileContext>.TransitionBuilder delegateTransition;
        
        TransitionBuilder(FluentStateMachineBuilder<TPersistingEntity, TVolatileContext>.TransitionBuilder delegateTransition) {
            this.delegateTransition = delegateTransition;
        }
        
        public StateBuilder to(String targetState) {
            delegateTransition.to(targetState);
            return currentStateBuilder;
        }
        
        public StateBuilder to(Enum<?> targetState) {
            delegateTransition.to(targetState);
            return currentStateBuilder;
        }
    }
}