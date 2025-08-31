package com.telcobright.statemachine;

import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachine.timeout.TimeoutManager;
import com.telcobright.statemachine.persistence.MysqlConnectionProvider;
import com.telcobright.statemachine.persistence.PersistenceProvider;
import com.telcobright.statemachine.persistence.OptimizedMySQLPersistenceProvider;
import com.telcobright.statemachine.events.EventTypeRegistry;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.BiConsumer;

/**
 * **THE ONLY WAY TO USE THE STATE MACHINE LIBRARY**
 * 
 * Unified builder that creates and configures both registry and state machines together
 * in a single place to avoid confusion and mistakes. This builder is mandatory -
 * the library cannot be used without it.
 * 
 * Usage:
 * ```java
 * var library = StateMachineLibraryBuilder.create("my-system")
 *     .registryConfig()
 *         .targetTps(1000)
 *         .maxConcurrentMachines(5000)
 *         .done()
 *     .stateMachineTemplate()
 *         .initialState("IDLE")
 *         .state("IDLE").on(StartEvent.class).to("ACTIVE").done()
 *         .state("ACTIVE").on(StopEvent.class).to("STOPPED").done()
 *         .state("STOPPED").finalState().done()
 *         .done()
 *     .build();
 * 
 * // Use the library
 * library.createMachine("machine-1", persistentCtx, volatileCtx, callback);
 * library.sendEvent("machine-1", new StartEvent());
 * ```
 */
public final class StateMachineLibraryBuilder<TPersistent extends StateMachineContextEntity<?>, TVolatile> {
    
    // Core configuration
    private final String systemId;
    private RegistryPerformanceConfig registryConfig;
    private TimeoutManager timeoutManager;
    private MysqlConnectionProvider databaseConnection;
    private PersistenceProvider<StateMachineContextEntity<?>> persistenceProvider;
    private SampleLoggingConfig sampleLogging = SampleLoggingConfig.DISABLED;
    
    // Debug mode configuration
    private boolean debugMode = false;
    private int webSocketPort = 9999; // Default WebSocket port
    
    // State machine template
    private StateMachineTemplate<TPersistent, TVolatile> machineTemplate;
    
    // Event type mappings for reflection-free processing
    private final Map<Class<? extends StateMachineEvent>, String> eventTypeRegistry = new HashMap<>();
    
    // Machine creation triggers - events that auto-create new machines
    private final Set<String> machineCreationEventTypes = new HashSet<>();
    private final Map<String, Supplier<TPersistent>> machineCreationEntityFactories = new HashMap<>();
    private final Map<String, Supplier<TVolatile>> machineCreationContextFactories = new HashMap<>();
    
    // Callbacks and handlers
    private Consumer<String> onMachineCreated;
    private BiConsumer<String, String> onMachineCreationFailed;
    private Consumer<StateMachineLibrary<TPersistent, TVolatile>> onLibraryReady;
    
    // Private constructor - force use of static factory
    private StateMachineLibraryBuilder(String systemId) {
        this.systemId = systemId;
        
        // Set reasonable defaults
        this.registryConfig = RegistryPerformanceConfig.builder()
            .targetTps(1000)
            .maxConcurrentMachines(10000)
            .enablePerformanceMetrics(true)
            .build();
            
        this.timeoutManager = new TimeoutManager(systemId + "-timeouts", 4);
    }
    
    /**
     * Create a new state machine library builder
     * @param systemId Unique identifier for your system/application
     */
    public static <P extends StateMachineContextEntity<?>, V> StateMachineLibraryBuilder<P, V> create(String systemId) {
        if (systemId == null || systemId.trim().isEmpty()) {
            throw new IllegalArgumentException("System ID cannot be null or empty");
        }
        return new StateMachineLibraryBuilder<>(systemId.trim());
    }
    
    /**
     * Configure registry performance parameters
     */
    public RegistryConfigBuilder registryConfig() {
        return new RegistryConfigBuilder();
    }
    
    /**
     * Configure database connection for persistence
     */
    public StateMachineLibraryBuilder<TPersistent, TVolatile> database(String jdbcUrl, String username, String password) {
        this.databaseConnection = new MysqlConnectionProvider(jdbcUrl, username, password);
        return this;
    }
    
    /**
     * Configure database with custom connection provider
     */
    public StateMachineLibraryBuilder<TPersistent, TVolatile> database(MysqlConnectionProvider connectionProvider) {
        this.databaseConnection = connectionProvider;
        return this;
    }
    
    /**
     * Enable optimized persistence for the entity class
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public StateMachineLibraryBuilder<TPersistent, TVolatile> enablePersistence(
            Class<? extends StateMachineContextEntity<?>> entityClass, String tableName) {
        if (databaseConnection == null) {
            throw new IllegalStateException("Database connection must be configured before enabling persistence");
        }
        this.persistenceProvider = new OptimizedMySQLPersistenceProvider(databaseConnection, entityClass, tableName);
        ((OptimizedMySQLPersistenceProvider) persistenceProvider).initialize();
        return this;
    }
    
    /**
     * Enable optimized persistence with auto-inferred table name
     */
    public StateMachineLibraryBuilder<TPersistent, TVolatile> enablePersistence(
            Class<? extends StateMachineContextEntity<?>> entityClass) {
        String tableName = OptimizedMySQLPersistenceProvider.inferTableName(entityClass);
        return enablePersistence(entityClass, tableName);
    }
    
    /**
     * Configure sample logging (1 in N events logged to reduce database load)
     */
    public StateMachineLibraryBuilder<TPersistent, TVolatile> sampleLogging(int oneInN) {
        this.sampleLogging = SampleLoggingConfig.oneInN(oneInN);
        return this;
    }
    
    /**
     * Disable sample logging (log all events)
     */
    public StateMachineLibraryBuilder<TPersistent, TVolatile> noSampleLogging() {
        this.sampleLogging = SampleLoggingConfig.ALL;
        return this;
    }
    
    /**
     * Register event types for reflection-free event processing
     */
    public StateMachineLibraryBuilder<TPersistent, TVolatile> registerEvent(
            Class<? extends StateMachineEvent> eventClass, String eventType) {
        eventTypeRegistry.put(eventClass, eventType);
        EventTypeRegistry.register(eventClass, eventType);
        return this;
    }
    
    /**
     * Register multiple event types at once
     */
    @SafeVarargs
    public final StateMachineLibraryBuilder<TPersistent, TVolatile> registerEvents(
            EventRegistration<? extends StateMachineEvent>... registrations) {
        for (EventRegistration<? extends StateMachineEvent> reg : registrations) {
            registerEvent(reg.eventClass, reg.eventType);
        }
        return this;
    }
    
    /**
     * Configure an event type that triggers automatic machine creation
     * When this event is received and no machine exists, a new one will be created
     * 
     * @param eventType The event type that triggers machine creation (e.g., "INCOMING_CALL", "INCOMING_SMS")
     * @param entityFactory Factory to create the persistent entity for new machines
     * @param contextFactory Factory to create the volatile context for new machines
     */
    public StateMachineLibraryBuilder<TPersistent, TVolatile> newMachineCreationEvent(
            String eventType, 
            Supplier<TPersistent> entityFactory,
            Supplier<TVolatile> contextFactory) {
        machineCreationEventTypes.add(eventType);
        machineCreationEntityFactories.put(eventType, entityFactory);
        machineCreationContextFactories.put(eventType, contextFactory);
        return this;
    }
    
    /**
     * Configure an event type that triggers automatic machine creation with simple defaults
     * Uses default constructors for entity and context
     * 
     * @param eventType The event type that triggers machine creation
     */
    public StateMachineLibraryBuilder<TPersistent, TVolatile> newMachineCreationEvent(String eventType) {
        machineCreationEventTypes.add(eventType);
        // Entity and context factories will need to be provided separately or use defaults
        return this;
    }
    
    /**
     * Configure multiple event types that trigger machine creation
     */
    public StateMachineLibraryBuilder<TPersistent, TVolatile> newMachineCreationEvents(String... eventTypes) {
        for (String eventType : eventTypes) {
            machineCreationEventTypes.add(eventType);
        }
        return this;
    }
    
    /**
     * Set callback for successful machine creation
     */
    public StateMachineLibraryBuilder<TPersistent, TVolatile> onMachineCreated(Consumer<String> callback) {
        this.onMachineCreated = callback;
        return this;
    }
    
    /**
     * Set callback for failed machine creation
     */
    public StateMachineLibraryBuilder<TPersistent, TVolatile> onMachineCreationFailed(BiConsumer<String, String> callback) {
        this.onMachineCreationFailed = callback;
        return this;
    }
    
    /**
     * Set callback for when library is fully initialized and ready
     */
    public StateMachineLibraryBuilder<TPersistent, TVolatile> onReady(Consumer<StateMachineLibrary<TPersistent, TVolatile>> callback) {
        this.onLibraryReady = callback;
        return this;
    }
    
    /**
     * Define the state machine template (states, transitions, actions)
     */
    public StateMachineTemplateBuilder stateMachineTemplate() {
        return new StateMachineTemplateBuilder();
    }
    
    /**
     * Build and initialize the complete state machine library
     */
    public StateMachineLibrary<TPersistent, TVolatile> build() {
        validateConfiguration();
        
        // Create registry
        StateMachineRegistry registry = new StateMachineRegistry(systemId, registryConfig, timeoutManager);
        
        // Configure registry with persistence if enabled
        if (persistenceProvider != null) {
            registry.setRegistrySampleLogging(sampleLogging);
        }
        
        // Enable debug mode if configured
        if (debugMode) {
            registry.enableDebugMode(webSocketPort);
            System.out.println("[StateMachineLibrary] Debug mode enabled with WebSocket on port " + webSocketPort);
        }
        
        // Create the library facade with machine creation configuration
        StateMachineLibrary<TPersistent, TVolatile> library = new StateMachineLibrary<>(
            systemId, registry, databaseConnection, persistenceProvider, sampleLogging,
            machineCreationEventTypes, machineCreationEntityFactories, machineCreationContextFactories,
            machineTemplate,  // Pass the template for auto-created machines
            onMachineCreated, onMachineCreationFailed != null ? 
                (reason) -> onMachineCreationFailed.accept("unknown", reason) : null);
        
        // Notify ready callback
        if (onLibraryReady != null) {
            onLibraryReady.accept(library);
        }
        
        System.out.println("[StateMachineLibrary] '" + systemId + "' initialized successfully");
        System.out.println("   Registry TPS: " + registryConfig.getTargetTps());
        System.out.println("   Max Machines: " + registryConfig.getMaxConcurrentMachines());
        System.out.println("   Persistence: " + (persistenceProvider != null ? "ENABLED" : "DISABLED"));
        System.out.println("   Sample Logging: " + sampleLogging);
        System.out.println("   Debug Mode: " + (debugMode ? "ENABLED (WebSocket port: " + webSocketPort + ")" : "DISABLED"));
        
        return library;
    }
    
    /**
     * Validate the complete configuration before building
     */
    private void validateConfiguration() {
        if (machineTemplate == null) {
            throw new IllegalStateException("State machine template must be defined using stateMachineTemplate()");
        }
        
        if (machineTemplate.initialState == null) {
            throw new IllegalStateException("Initial state must be defined in state machine template");
        }
        
        if (machineTemplate.states.isEmpty()) {
            throw new IllegalStateException("At least one state must be defined in state machine template");
        }
        
        // Validate that initial state exists
        if (!machineTemplate.states.containsKey(machineTemplate.initialState)) {
            throw new IllegalStateException("Initial state '" + machineTemplate.initialState + "' is not defined");
        }
        
        // Validate that there's at least one final state
        boolean hasFinalState = machineTemplate.states.values().stream()
            .anyMatch(state -> state.isFinalState);
        if (!hasFinalState) {
            throw new IllegalStateException("At least one final state must be defined using .finalState()");
        }
        
        System.out.println("[StateMachineLibrary] Configuration validated successfully");
    }
    
    // =============================================================================
    // NESTED BUILDERS FOR FLUENT CONFIGURATION
    // =============================================================================
    
    /**
     * Registry configuration builder
     */
    public class RegistryConfigBuilder {
        public RegistryConfigBuilder targetTps(int tps) {
            registryConfig = RegistryPerformanceConfig.builder()
                .targetTps(tps)
                .maxConcurrentMachines(registryConfig.getMaxConcurrentMachines())
                .enablePerformanceMetrics(registryConfig.isEnablePerformanceMetrics())
                .build();
            return this;
        }
        
        public RegistryConfigBuilder maxConcurrentMachines(int max) {
            registryConfig = RegistryPerformanceConfig.builder()
                .targetTps(registryConfig.getTargetTps())
                .maxConcurrentMachines(max)
                .enablePerformanceMetrics(registryConfig.isEnablePerformanceMetrics())
                .build();
            return this;
        }
        
        public RegistryConfigBuilder enablePerformanceMetrics(boolean enable) {
            registryConfig = RegistryPerformanceConfig.builder()
                .targetTps(registryConfig.getTargetTps())
                .maxConcurrentMachines(registryConfig.getMaxConcurrentMachines())
                .enablePerformanceMetrics(enable)
                .build();
            return this;
        }
        
        public RegistryConfigBuilder timeoutThreads(int threads) {
            timeoutManager = new TimeoutManager(systemId + "-timeouts", threads);
            return this;
        }
        
        /**
         * Enable debug mode with WebSocket monitoring
         * @param enable true to enable debug mode
         * @return this builder for chaining
         */
        public RegistryConfigBuilder debugMode(boolean enable) {
            debugMode = enable;
            return this;
        }
        
        /**
         * Set WebSocket port for debug mode (default: 9999)
         * Automatically enables debug mode
         * @param port WebSocket server port
         * @return this builder for chaining
         */
        public RegistryConfigBuilder webSocketPort(int port) {
            debugMode = true; // Automatically enable debug mode when port is set
            webSocketPort = port;
            return this;
        }
        
        public StateMachineLibraryBuilder<TPersistent, TVolatile> done() {
            return StateMachineLibraryBuilder.this;
        }
    }
    
    /**
     * State machine template builder
     */
    public class StateMachineTemplateBuilder {
        private StateMachineTemplate<TPersistent, TVolatile> template = new StateMachineTemplate<>();
        
        public StateMachineTemplateBuilder initialState(String stateName) {
            template.initialState = stateName;
            return this;
        }
        
        public StateConfigBuilder state(String stateName) {
            return new StateConfigBuilder(stateName);
        }
        
        public StateMachineLibraryBuilder<TPersistent, TVolatile> done() {
            machineTemplate = template;
            return StateMachineLibraryBuilder.this;
        }
        
        /**
         * Individual state configuration builder
         */
        public class StateConfigBuilder {
            private final String stateName;
            private final StateConfig<TPersistent, TVolatile> stateConfig;
            
            StateConfigBuilder(String stateName) {
                this.stateName = stateName;
                this.stateConfig = template.states.computeIfAbsent(stateName, 
                    k -> new StateConfig<>());
            }
            
            public StateConfigBuilder onEntry(Consumer<GenericStateMachine<TPersistent, TVolatile>> action) {
                stateConfig.entryActions.add(action);
                return this;
            }
            
            public StateConfigBuilder onExit(Consumer<GenericStateMachine<TPersistent, TVolatile>> action) {
                stateConfig.exitActions.add(action);
                return this;
            }
            
            public StateConfigBuilder timeout(long duration, 
                    com.telcobright.statemachine.timeout.TimeUnit unit, String targetState) {
                stateConfig.timeoutDuration = duration;
                stateConfig.timeoutUnit = unit;
                stateConfig.timeoutTargetState = targetState;
                return this;
            }
            
            public TransitionBuilder on(Class<? extends StateMachineEvent> eventClass) {
                return new TransitionBuilder(eventClass);
            }
            
            public TransitionBuilder on(String eventType) {
                return new TransitionBuilder(eventType);
            }
            
            public StateConfigBuilder stay(Class<? extends StateMachineEvent> eventClass,
                    BiConsumer<GenericStateMachine<TPersistent, TVolatile>, StateMachineEvent> action) {
                stateConfig.stayActions.put(eventClass, action);
                return this;
            }
            
            public StateConfigBuilder finalState() {
                stateConfig.isFinalState = true;
                return this;
            }
            
            public StateMachineTemplateBuilder done() {
                return StateMachineTemplateBuilder.this;
            }
            
            /**
             * Transition configuration builder
             */
            public class TransitionBuilder {
                private final Object eventIdentifier;
                
                TransitionBuilder(Object eventIdentifier) {
                    this.eventIdentifier = eventIdentifier;
                }
                
                public StateConfigBuilder to(String targetState) {
                    if (eventIdentifier instanceof Class) {
                        stateConfig.transitions.put((Class<? extends StateMachineEvent>) eventIdentifier, targetState);
                    } else {
                        stateConfig.namedTransitions.put((String) eventIdentifier, targetState);
                    }
                    return StateConfigBuilder.this;
                }
                
                public StateConfigBuilder to(Enum<?> targetState) {
                    return to(targetState.name());
                }
            }
        }
    }
    
    // =============================================================================
    // HELPER CLASSES AND DATA STRUCTURES
    // =============================================================================
    
    /**
     * Event registration helper
     */
    public static class EventRegistration<T extends StateMachineEvent> {
        public final Class<T> eventClass;
        public final String eventType;
        
        public EventRegistration(Class<T> eventClass, String eventType) {
            this.eventClass = eventClass;
            this.eventType = eventType;
        }
        
        public static <T extends StateMachineEvent> EventRegistration<T> of(Class<T> eventClass, String eventType) {
            return new EventRegistration<>(eventClass, eventType);
        }
    }
    
    /**
     * State machine template (package-private for library access)
     */
    static class StateMachineTemplate<TPersistent extends StateMachineContextEntity<?>, TVolatile> {
        String initialState;
        Map<String, StateConfig<TPersistent, TVolatile>> states = new HashMap<>();
    }
    
    /**
     * State configuration (package-private for library access)
     */
    static class StateConfig<TPersistent extends StateMachineContextEntity<?>, TVolatile> {
        List<Consumer<GenericStateMachine<TPersistent, TVolatile>>> entryActions = new ArrayList<>();
        List<Consumer<GenericStateMachine<TPersistent, TVolatile>>> exitActions = new ArrayList<>();
        Map<Class<? extends StateMachineEvent>, String> transitions = new HashMap<>();
        Map<String, String> namedTransitions = new HashMap<>();
        Map<Class<? extends StateMachineEvent>, BiConsumer<GenericStateMachine<TPersistent, TVolatile>, StateMachineEvent>> stayActions = new HashMap<>();
        boolean isFinalState = false;
        long timeoutDuration = -1;
        com.telcobright.statemachine.timeout.TimeUnit timeoutUnit;
        String timeoutTargetState;
    }
}