package com.telcobright.statemachine;

import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachine.events.GenericStateMachineEvent;
import com.telcobright.statemachine.persistence.MysqlConnectionProvider;
import com.telcobright.statemachine.persistence.PersistenceProvider;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * **PRIMARY PUBLIC API FOR THE STATE MACHINE LIBRARY**
 * 
 * This is the main interface consumers use to interact with state machines.
 * Created only through StateMachineLibraryBuilder - cannot be instantiated directly.
 * 
 * Provides high-level operations for:
 * - Creating state machines with callbacks
 * - Sending events to machines  
 * - Managing machine lifecycle
 * - Monitoring performance
 * - Graceful shutdown
 */
public final class StateMachineLibrary<TPersistent extends StateMachineContextEntity<?>, TVolatile> {
    
    private final String systemId;
    private final StateMachineRegistry registry;
    private final MysqlConnectionProvider databaseConnection;
    private final PersistenceProvider<StateMachineContextEntity<?>> persistenceProvider;
    private final SampleLoggingConfig sampleLogging;
    
    // Machine auto-creation configuration
    private final Set<String> machineCreationEventTypes;
    private final Map<String, Supplier<TPersistent>> machineCreationEntityFactories;
    private final Map<String, Supplier<TVolatile>> machineCreationContextFactories;
    private final StateMachineLibraryBuilder.StateMachineTemplate<TPersistent, TVolatile> machineTemplate;
    
    // Callbacks
    private final Consumer<String> onMachineCreated;
    private final Consumer<String> onMachineCreationFailed;
    
    // Package-private constructor - only StateMachineLibraryBuilder can create this
    StateMachineLibrary(String systemId, 
                       StateMachineRegistry registry,
                       MysqlConnectionProvider databaseConnection,
                       PersistenceProvider<StateMachineContextEntity<?>> persistenceProvider,
                       SampleLoggingConfig sampleLogging,
                       Set<String> machineCreationEventTypes,
                       Map<String, Supplier<TPersistent>> machineCreationEntityFactories,
                       Map<String, Supplier<TVolatile>> machineCreationContextFactories,
                       StateMachineLibraryBuilder.StateMachineTemplate<TPersistent, TVolatile> machineTemplate,
                       Consumer<String> onMachineCreated,
                       Consumer<String> onMachineCreationFailed) {
        this.systemId = systemId;
        this.registry = registry;
        this.databaseConnection = databaseConnection;
        this.persistenceProvider = persistenceProvider;
        this.sampleLogging = sampleLogging;
        this.machineCreationEventTypes = machineCreationEventTypes != null ? machineCreationEventTypes : Set.of();
        this.machineCreationEntityFactories = machineCreationEntityFactories != null ? machineCreationEntityFactories : Map.of();
        this.machineCreationContextFactories = machineCreationContextFactories != null ? machineCreationContextFactories : Map.of();
        this.machineTemplate = machineTemplate;
        this.onMachineCreated = onMachineCreated;
        this.onMachineCreationFailed = onMachineCreationFailed;
    }
    
    // =============================================================================
    // MACHINE LIFECYCLE OPERATIONS
    // =============================================================================
    
    /**
     * Create a new state machine using the configured template
     * 
     * @param machineId Unique identifier for the machine
     * @param machineBuilder Pre-configured machine builder from template
     * @param callback Optional callback for creation success/failure
     * @return The created machine, or null if creation failed
     */
    public GenericStateMachine<TPersistent, TVolatile> createMachine(String machineId,
                                                                    EnhancedFluentBuilder<TPersistent, TVolatile> machineBuilder,
                                                                    MachineCreationCallback callback) {
        
        // Create combined callback that includes both user callback and library callbacks
        MachineCreationCallback combinedCallback = new MachineCreationCallback() {
            @Override
            public void onMachineCreated(String id, GenericStateMachine<?, ?> machine) {
                // Library callback
                if (onMachineCreated != null) {
                    onMachineCreated.accept(id);
                }
                
                // User callback
                if (callback != null) {
                    callback.onMachineCreated(id, machine);
                }
            }
            
            @Override
            public void onMachineCreationFailed(String id, String reason, Throwable exception) {
                // Library callback
                if (onMachineCreationFailed != null) {
                    onMachineCreationFailed.accept(id + ": " + reason);
                }
                
                // User callback
                if (callback != null) {
                    callback.onMachineCreationFailed(id, reason, exception);
                }
            }
        };
        
        // Configure the builder with library settings and build/register
        return machineBuilder
            .withCreationCallback(combinedCallback)
            .withSampleLogging(sampleLogging)
            .buildAndRegister(registry);
    }
    
    /**
     * Create a machine with simplified callback (success only)
     */
    public GenericStateMachine<TPersistent, TVolatile> createMachine(String machineId,
                                                                    EnhancedFluentBuilder<TPersistent, TVolatile> machineBuilder,
                                                                    Consumer<String> onSuccess) {
        MachineCreationCallback callback = new MachineCreationCallback() {
            @Override
            public void onMachineCreated(String id, GenericStateMachine<?, ?> machine) {
                if (onSuccess != null) onSuccess.accept(id);
            }
            
            @Override
            public void onMachineCreationFailed(String id, String reason, Throwable exception) {
                // Use library's default failure handling
            }
        };
        
        return createMachine(machineId, machineBuilder, callback);
    }
    
    /**
     * Create a machine without callbacks
     */
    public GenericStateMachine<TPersistent, TVolatile> createMachine(String machineId,
                                                                    EnhancedFluentBuilder<TPersistent, TVolatile> machineBuilder) {
        return createMachine(machineId, machineBuilder, (MachineCreationCallback) null);
    }
    
    // =============================================================================
    // EVENT OPERATIONS
    // =============================================================================
    
    /**
     * Send an event to a specific state machine
     * Auto-creates machine if event type is configured for machine creation
     * 
     * @param machineId The target machine ID
     * @param event The event to send
     * @return true if event was sent successfully, false if machine doesn't exist and couldn't be created
     */
    public boolean sendEvent(String machineId, StateMachineEvent event) {
        // Check if machine exists
        if (!machineExists(machineId)) {
            String eventType = event.getEventType();
            
            // Check if this event type triggers machine creation
            if (machineCreationEventTypes.contains(eventType)) {
                System.out.printf("[StateMachineLibrary] Auto-creating machine %s for event %s%n", 
                    machineId, eventType);
                
                // Auto-create the machine
                if (!autoCreateMachine(machineId, eventType)) {
                    System.err.printf("[StateMachineLibrary] Failed to auto-create machine %s for event %s%n", 
                        machineId, eventType);
                    return false;
                }
            } else {
                // Machine doesn't exist and this event doesn't trigger creation
                System.err.printf("[StateMachineLibrary] Machine %s does not exist. Ignoring event %s. " +
                    "Configure newMachineCreationEvent(\"%s\") to auto-create machines for this event type.%n", 
                    machineId, eventType, eventType);
                return false;
            }
        }
        
        // Send the event to the machine (either existing or newly created)
        try {
            registry.sendEvent(machineId, event);
            return true;
        } catch (Exception e) {
            System.err.printf("[StateMachineLibrary] Failed to send %s to machine %s: %s%n", 
                event.getClass().getSimpleName(), machineId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Auto-create a machine for a specific event type
     */
    private boolean autoCreateMachine(String machineId, String eventType) {
        try {
            // Get factories for this event type
            Supplier<TPersistent> entityFactory = machineCreationEntityFactories.get(eventType);
            Supplier<TVolatile> contextFactory = machineCreationContextFactories.get(eventType);
            
            // Use default factories if not specified
            TPersistent entity = entityFactory != null ? entityFactory.get() : null;
            TVolatile context = contextFactory != null ? contextFactory.get() : null;
            
            // Create machine builder from template
            if (machineTemplate == null) {
                System.err.println("[StateMachineLibrary] No machine template defined for auto-creation");
                return false;
            }
            
            // Build machine from template
            EnhancedFluentBuilder<TPersistent, TVolatile> builder = 
                EnhancedFluentBuilder.<TPersistent, TVolatile>create(machineId)
                    .withPersistentContext(entity)
                    .withVolatileContext(context)
                    .initialState(machineTemplate.initialState);
            
            // Add states from template
            for (var stateEntry : machineTemplate.states.entrySet()) {
                var stateConfig = stateEntry.getValue();
                var stateBuilder = builder.state(stateEntry.getKey());
                
                // Add class-based transitions
                for (var transition : stateConfig.transitions.entrySet()) {
                    stateBuilder.on(transition.getKey()).to(transition.getValue());
                }
                
                // Add named transitions (string events) - DEPRECATED
                // for (var transition : stateConfig.namedTransitions.entrySet()) {
                //     stateBuilder.on(transition.getKey()).to(transition.getValue());
                // }
                
                // Add entry actions
                for (var action : stateConfig.entryActions) {
                    stateBuilder.onEntry(action);
                }
                
                // Add exit actions  
                for (var action : stateConfig.exitActions) {
                    stateBuilder.onExit(action);
                }
                
                // Mark as final state if needed
                if (stateConfig.isFinalState) {
                    stateBuilder.finalState();
                }
                
                stateBuilder.done();
            }
            
            // Create the machine
            GenericStateMachine<TPersistent, TVolatile> machine = createMachine(machineId, builder);
            return machine != null;
            
        } catch (Exception e) {
            System.err.printf("[StateMachineLibrary] Error auto-creating machine %s: %s%n", 
                machineId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Send an event and execute callback based on success/failure
     */
    public void sendEvent(String machineId, StateMachineEvent event, 
                         Runnable onSuccess, Consumer<Exception> onFailure) {
        try {
            registry.sendEvent(machineId, event);
            if (onSuccess != null) {
                onSuccess.run();
            }
        } catch (Exception e) {
            if (onFailure != null) {
                onFailure.accept(e);
            }
        }
    }
    
    // =============================================================================
    // MACHINE MANAGEMENT
    // =============================================================================
    
    /**
     * Remove a machine from the registry
     * 
     * @param machineId The machine to remove
     * @return true if machine was removed, false if it didn't exist
     */
    public boolean removeMachine(String machineId) {
        try {
            registry.removeMachine(machineId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if a machine exists in the registry
     */
    public boolean machineExists(String machineId) {
        return registry.getMachine(machineId) != null;
    }
    
    /**
     * Get a machine by ID (for advanced operations)
     */
    public GenericStateMachine<TPersistent, TVolatile> getMachine(String machineId) {
        @SuppressWarnings("unchecked")
        GenericStateMachine<TPersistent, TVolatile> machine = 
            (GenericStateMachine<TPersistent, TVolatile>) registry.getMachine(machineId);
        return machine;
    }
    
    /**
     * Get the current state of a machine
     */
    public String getMachineState(String machineId) {
        GenericStateMachine<TPersistent, TVolatile> machine = getMachine(machineId);
        return machine != null ? machine.getCurrentState() : null;
    }
    
    // =============================================================================
    // MONITORING AND PERFORMANCE
    // =============================================================================
    
    /**
     * Get comprehensive performance statistics
     */
    public LibraryPerformanceStats getPerformanceStats() {
        Map<String, Object> registryStats = registry.getPerformanceStats();
        return new LibraryPerformanceStats(systemId, registryStats);
    }
    
    /**
     * Get count of active machines
     */
    public int getActiveMachineCount() {
        return (Integer) registry.getPerformanceStats().get("activeMachines");
    }
    
    /**
     * Get available machine capacity
     */
    public int getAvailableMachineCapacity() {
        return (Integer) registry.getPerformanceStats().get("availableMachinePermits");
    }
    
    /**
     * Check if registry is at capacity
     */
    public boolean isAtCapacity() {
        return getAvailableMachineCapacity() == 0;
    }
    
    // =============================================================================
    // LIFECYCLE MANAGEMENT
    // =============================================================================
    
    /**
     * Enable debug mode with WebSocket monitoring
     */
    public void enableDebugMode(int webSocketPort) {
        registry.enableDebugMode(webSocketPort);
        System.out.println("[StateMachineLibrary] Debug mode enabled on port " + webSocketPort);
    }
    
    /**
     * Gracefully shutdown the library
     * - Stops accepting new machines
     * - Processes remaining events
     * - Persists all machine states
     * - Closes connections
     */
    public void shutdown() {
        System.out.println("[StateMachineLibrary] Shutting down '" + systemId + "'...");
        
        try {
            // Shutdown registry (handles machine persistence)
            registry.shutdown();
            
            // Close database connections
            if (databaseConnection != null) {
                databaseConnection.close();
            }
            
            System.out.println("[StateMachineLibrary] '" + systemId + "' shutdown completed");
        } catch (Exception e) {
            System.err.println("[StateMachineLibrary] Error during shutdown: " + e.getMessage());
        }
    }
    
    // =============================================================================
    // PERFORMANCE STATS CLASS
    // =============================================================================
    
    /**
     * Comprehensive performance statistics for the library
     */
    public static class LibraryPerformanceStats {
        private final String systemId;
        private final Map<String, Object> registryStats;
        
        LibraryPerformanceStats(String systemId, Map<String, Object> registryStats) {
            this.systemId = systemId;
            this.registryStats = registryStats;
        }
        
        public String getSystemId() { return systemId; }
        public int getActiveMachines() { return (Integer) registryStats.get("activeMachines"); }
        public int getAvailableMachinePermits() { return (Integer) registryStats.get("availableMachinePermits"); }
        public int getEventQueueCapacity() { return (Integer) registryStats.get("eventQueueCapacity"); }
        public int getVirtualThreadPoolSize() { return (Integer) registryStats.get("virtualThreadPoolSize"); }
        public boolean isPerformanceMetricsEnabled() { return (Boolean) registryStats.get("performanceMetricsEnabled"); }
        
        @Override
        public String toString() {
            return String.format("LibraryStats[system=%s, active=%d, available=%d, queueCapacity=%d]",
                systemId, getActiveMachines(), getAvailableMachinePermits(), getEventQueueCapacity());
        }
    }
}