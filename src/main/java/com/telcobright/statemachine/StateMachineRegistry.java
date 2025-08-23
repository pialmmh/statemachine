package com.telcobright.statemachine;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.function.Function;
import com.telcobright.statemachine.timeout.TimeoutManager;
import com.telcobright.statemachine.StateMachineContextEntity;
import com.telcobright.statemachine.persistence.MysqlConnectionProvider;
import com.telcobright.statemachine.persistence.PersistenceProvider;
import com.telcobright.statemachine.persistence.MySQLPersistenceProvider;
import com.telcobright.statemachine.persistence.ShardingEntityStateMachineRepository;
import com.telcobright.statemachine.persistence.IdLookUpMode;
import com.telcobright.db.PartitionedRepository;
import com.telcobright.db.entity.ShardingEntity;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;

/**
 * Registry for managing state machine instances
 * Used for testing and lifecycle management
 */
public class StateMachineRegistry extends AbstractStateMachineRegistry {
    
    // MySQL connection provider for history tracking
    private MysqlConnectionProvider connectionProvider;
    
    // Persistence provider for state machine contexts
    private PersistenceProvider<StateMachineContextEntity<?>> persistenceProvider;
    
    // Optional ShardingEntity repository for advanced persistence
    private ShardingEntityStateMachineRepository<? extends ShardingEntity<?>, ?> shardingRepository;
    
    // Executor for async operations to avoid blocking real-time performance
    private ExecutorService asyncExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "StateMachine-Async");
        t.setDaemon(true);
        return t;
    });
    
    /**
     * Default constructor for testing
     */
    public StateMachineRegistry() {
        super();
        initializeConnectionProvider();
    }
    
    /**
     * Constructor with timeout manager
     */
    public StateMachineRegistry(TimeoutManager timeoutManager) {
        super(timeoutManager);
        initializeConnectionProvider();
    }
    
    /**
     * Constructor with timeout manager and WebSocket port
     */
    public StateMachineRegistry(TimeoutManager timeoutManager, int webSocketPort) {
        super(timeoutManager, webSocketPort);
        initializeConnectionProvider();
    }
    
    /**
     * Register a state machine
     * Automatically applies debug mode if enabled
     */
    @Override
    public void register(String id, GenericStateMachine<?, ?> machine) {
        activeMachines.put(id, machine);
        lastAddedMachine = id; // Track last added machine
        System.out.println("[Registry] Machine registered: " + id + " - setting lastAddedMachine to: " + id);
        
        // Set up state transition callback to notify listeners (including WebSocket)
        // This ensures timeout transitions are also broadcast
        String[] previousState = {machine.getCurrentState()};
        machine.setOnStateTransition(newState -> {
            String oldState = previousState[0];
            previousState[0] = newState;
            
            // Notify all listeners of the state change
            // This will trigger WebSocket broadcast if live debug is enabled
            notifyStateMachineEvent(id, oldState, newState, 
                machine.getPersistingEntity(), machine.getContext());
        });
        
        // Set up offline transition callback to persist and evict machine
        machine.setOnOfflineTransition(m -> {
            System.out.println("[Registry] Machine " + id + " entering offline state");
            
            // Persist the machine state before removing from memory
            if (persistenceProvider != null && m.getPersistingEntity() != null) {
                try {
                    persistenceProvider.save(id, (StateMachineContextEntity<?>) m.getPersistingEntity());
                    System.out.println("[Registry] Persisted offline machine " + id + " (state: " + m.getCurrentState() + ")");
                } catch (Exception e) {
                    System.err.println("[Registry] Failed to persist offline machine " + id + ": " + e.getMessage());
                }
            }
            
            // Remove from active machines
            evict(id);
        });
        
        // Debug mode tracking is handled through listeners
        if (debugMode) {
            System.out.println("🔍 Debug mode active for machine: " + id);
        }
        
        // Notify listeners
        notifyRegistryCreate(id);
        
        // Registry events logged via MySQL history
        
        // Send event metadata update if WebSocket server is running
        if (isWebSocketServerRunning()) {
            sendEventMetadataUpdate();
        }
    }
    
    /**
     * Remove a state machine from the registry
     */
    @Override
    public void removeMachine(String id) {
        GenericStateMachine<?, ?> machine = activeMachines.remove(id);
        if (machine != null) {
            lastRemovedMachine = id; // Track last removed machine
        }
        
        // Close history tracker if machine has one
        if (machine != null) {
            machine.closeHistoryTracker();
        }
        
        notifyRegistryRemove(id);
        
        // Registry events logged via MySQL history
    }
    
    /**
     * Check if a machine is in memory
     */
    public boolean isInMemory(String id) {
        return activeMachines.containsKey(id);
    }
    
    /**
     * Get a machine from memory
     */
    public GenericStateMachine<?, ?> getActiveMachine(String id) {
        return activeMachines.get(id);
    }
    
    /**
     * Get a machine from the registry (implements abstract method)
     */
    @Override
    public GenericStateMachine<?, ?> getMachine(String id) {
        // First check active machines
        GenericStateMachine<?, ?> machine = activeMachines.get(id);
        
        // If not found and WebSocket clients are connected, check offline debug cache
        if (machine == null && hasWebSocketClients()) {
            machine = offlineMachinesForDebug.get(id);
            if (machine != null) {
                System.out.println("[Registry] Retrieved machine " + id + " from offline debug cache");
            }
        }
        
        return machine;
    }
    
    /**
     * Create a new state machine directly without checking persistence
     * Use this method when you know the machine is new (e.g., incoming SMS/calls)
     * to avoid unnecessary database lookups and improve performance
     */
    public <TPersistingEntity extends StateMachineContextEntity<?>, TContext> GenericStateMachine<TPersistingEntity, TContext> create(String id, Supplier<GenericStateMachine<TPersistingEntity, TContext>> factory) {
        // Check if already in memory - if so, throw exception as this should be a new machine
        if (activeMachines.containsKey(id)) {
            throw new IllegalStateException("State machine with ID " + id + " already exists. Use createOrGet() instead.");
        }
        
        // Create new machine directly without persistence lookup
        GenericStateMachine<TPersistingEntity, TContext> machine = factory.get();
        register(id, machine);
        System.out.println("Created new StateMachine " + id + " (skipping DB lookup for performance)");
        return machine;
    }
    
    /**
     * Create or get a state machine
     * If exists in memory, return it
     * If not, try to load from persistence
     * If not in persistence, create new one
     */
    public <TPersistingEntity extends StateMachineContextEntity<?>, TContext> GenericStateMachine<TPersistingEntity, TContext> createOrGet(String id, Supplier<GenericStateMachine<TPersistingEntity, TContext>> factory) {
        // Check if already in memory
        @SuppressWarnings("unchecked")
        GenericStateMachine<TPersistingEntity, TContext> existing = (GenericStateMachine<TPersistingEntity, TContext>) activeMachines.get(id);
        if (existing != null) {
            System.out.println("[Registry] Machine " + id + " found in memory");
            return existing;
        }
        
        // Try to load from persistence if provider is available
        if (persistenceProvider != null) {
            try {
                @SuppressWarnings("unchecked")
                TPersistingEntity loadedContext = (TPersistingEntity) persistenceProvider.load(id, null);
                
                if (loadedContext != null) {
                    // Check if machine is complete - don't rehydrate completed machines
                    if (loadedContext.isComplete()) {
                        System.out.println("[Registry] Machine " + id + " is complete - not rehydrating");
                        return null;
                    }
                    
                    System.out.println("[Registry] Rehydrating machine " + id + " from persistence (state: " + loadedContext.getCurrentState() + ")");
                    
                    // Create new machine instance
                    GenericStateMachine<TPersistingEntity, TContext> machine = factory.get();
                    
                    // Set the loaded persistent context
                    machine.setPersistingEntity(loadedContext);
                    
                    // Restore the state (this will check timeouts)
                    machine.restoreState(loadedContext.getCurrentState());
                    
                    // Register the rehydrated machine
                    register(id, machine);
                    lastAddedMachine = id; // Track as rehydrated
                    
                    // Notify listeners of rehydration
                    for (StateMachineListener listener : listeners) {
                        try {
                            listener.onRegistryRehydrate(id);
                        } catch (Exception e) {
                            System.err.println("Error notifying listener of registry rehydrate: " + e.getMessage());
                        }
                    }
                    
                    return machine;
                }
            } catch (Exception e) {
                System.err.println("[Registry] Failed to load from persistence for machine " + id + ": " + e.getMessage());
            }
        }
        
        // Not in persistence or persistence unavailable - create new machine
        System.out.println("[Registry] Creating new machine " + id);
        GenericStateMachine<TPersistingEntity, TContext> machine = factory.get();
        register(id, machine);
        return machine;
    }
    
    /**
     * Create or get a state machine with completion checking
     * If the persisting entity is complete, don't rehydrate the machine
     */
    public <TPersistingEntity extends StateMachineContextEntity<?>, TContext> GenericStateMachine<TPersistingEntity, TContext> createOrGet(
            String id, 
            Supplier<GenericStateMachine<TPersistingEntity, TContext>> factory,
            Function<String, TPersistingEntity> entityLoader) {
        
        // Check if already in memory
        @SuppressWarnings("unchecked")
        GenericStateMachine<TPersistingEntity, TContext> existing = (GenericStateMachine<TPersistingEntity, TContext>) activeMachines.get(id);
        if (existing != null) {
            return existing;
        }
        
        // Check if entity exists and is complete
        if (entityLoader != null) {
            TPersistingEntity entity = entityLoader.apply(id);
            if (entity != null && entity instanceof StateMachineContextEntity && ((StateMachineContextEntity) entity).isComplete()) {
                System.out.println("StateMachine " + id + " is complete - not rehydrating");
                return null; // Don't create/rehydrate completed machines
            }
        }
        
        // Create new machine
        GenericStateMachine<TPersistingEntity, TContext> machine = factory.get();
        register(id, machine);
        return machine;
    }
    
    
    /**
     * Get all active machines
     */
    public Map<String, GenericStateMachine<?, ?>> getActiveMachines() {
        return new ConcurrentHashMap<>(activeMachines);
    }
    
    /**
     * Clear all machines
     */
    public void clear() {
        activeMachines.clear();
    }
    
    
    /**
     * Notify listeners of registry create event
     */
    @SuppressWarnings("unchecked")
    private void notifyRegistryCreate(String machineId) {
        // Update history asynchronously if debug mode is enabled
        if (debugMode && history != null) {
            GenericStateMachine<?, ?> machine = activeMachines.get(machineId);
            if (machine != null) {
                CompletableFuture.runAsync(() -> {
                    try {
                        history.recordMachineStart(machineId, machine.getCurrentState());
                    } catch (Exception e) {
                        System.err.println("[Registry] Error recording machine start: " + e.getMessage());
                    }
                }, asyncExecutor);
            }
        }
        
        for (StateMachineListener listener : listeners) {
            try {
                listener.onRegistryCreate(machineId);
            } catch (Exception e) {
                System.err.println("Error notifying listener of registry create: " + e.getMessage());
            }
        }
    }
    
    /**
     * Notify listeners of registry remove event
     */
    @SuppressWarnings("unchecked")
    private void notifyRegistryRemove(String machineId) {
        // Update history asynchronously if debug mode is enabled
        if (debugMode && history != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    history.recordMachineRemoval(machineId);
                } catch (Exception e) {
                    System.err.println("[Registry] Error recording machine removal: " + e.getMessage());
                }
            }, asyncExecutor);
        }
        
        for (StateMachineListener listener : listeners) {
            try {
                listener.onRegistryRemove(machineId);
            } catch (Exception e) {
                System.err.println("Error notifying listener of registry remove: " + e.getMessage());
            }
        }
    }
    
    /**
     * Notify listeners of state machine event
     */
    @SuppressWarnings("unchecked")
    public void notifyStateMachineEvent(String machineId, String oldState, String newState, 
                                       StateMachineContextEntity contextEntity, Object volatileContext) {
        // Update history asynchronously if debug mode is enabled
        if (debugMode && history != null) {
            // Get the machine to check for event info
            GenericStateMachine<?, ?> machine = activeMachines.get(machineId);
            if (machine != null) {
                // Record history asynchronously to avoid blocking real-time performance
                CompletableFuture.runAsync(() -> {
                    try {
                        System.out.println("[Registry] Recording transition in History for machine " + machineId);
                        // TODO: Get the actual event from machine if available
                        history.recordTransition(machineId, oldState, newState, null, 
                            contextEntity, contextEntity, 0);
                    } catch (Exception e) {
                        System.err.println("[Registry] Error recording history: " + e.getMessage());
                    }
                }, asyncExecutor);
            } else {
                System.out.println("[Registry] Machine not found in activeMachines: " + machineId);
            }
        }
        
        // Notify listeners synchronously for real-time updates
        for (StateMachineListener listener : listeners) {
            try {
                listener.onStateMachineEvent(machineId, oldState, newState, contextEntity, volatileContext);
            } catch (Exception e) {
                System.err.println("Error notifying listener of state machine event: " + e.getMessage());
            }
        }
    }
    
    /**
     * Get count of active machines
     */
    public int size() {
        return activeMachines.size();
    }
    
    /**
     * Check if a machine is active (same as isInMemory)
     */
    public boolean isActive(String id) {
        return activeMachines.containsKey(id);
    }
    
    /**
     * Remove a machine from active registry (evict from memory)
     */
    public void evict(String id) {
        GenericStateMachine<?, ?> machine = activeMachines.remove(id);
        if (machine != null) {
            lastRemovedMachine = id; // Track as removed/offline
            
            // Mark as offline in history asynchronously if debug mode is enabled
            if (debugMode && history != null) {
                CompletableFuture.runAsync(() -> {
                    try {
                        history.recordMachineOffline(id);
                    } catch (Exception e) {
                        System.err.println("[Registry] Error marking machine offline: " + e.getMessage());
                    }
                }, asyncExecutor);
            }
            
            // If WebSocket has connected clients, keep machine in debug cache
            if (hasWebSocketClients()) {
                offlineMachinesForDebug.put(id, machine);
                System.out.println("[Registry] Machine " + id + " moved to offline debug cache (WebSocket clients connected)");
                
                // Broadcast the updated offline machines list AND updated active machines list to all connected clients
                if (webSocketServer != null) {
                    webSocketServer.broadcastOfflineMachines();
                    // Also broadcast updated active machines list since count has changed
                    webSocketServer.broadcastMachinesList();
                }
            } else {
                System.out.println("[Registry] Machine " + id + " evicted completely (no WebSocket clients)");
            }
        }
    }
    
    /**
     * Rehydrate a machine from persistence (implements abstract method)
     */
    @Override
    public <T extends StateMachineContextEntity<?>> T rehydrateMachine(
            String machineId, 
            Class<T> contextClass, 
            Supplier<T> contextSupplier, 
            Function<T, GenericStateMachine<T, ?>> machineBuilder) {
        
        // Check if already in memory
        GenericStateMachine<?, ?> existing = activeMachines.get(machineId);
        if (existing != null && existing.getPersistingEntity() != null) {
            try {
                return contextClass.cast(existing.getPersistingEntity());
            } catch (ClassCastException e) {
                // Machine exists but with different context type
                throw new IllegalStateException("Machine " + machineId + " exists with different context type");
            }
        }
        
        // Create new context and machine
        T context = contextSupplier.get();
        GenericStateMachine<T, ?> machine = machineBuilder.apply(context);
        register(machineId, machine);
        lastAddedMachine = machineId; // Track as rehydrated
        
        // Notify listeners
        for (StateMachineListener listener : listeners) {
            try {
                listener.onRegistryRehydrate(machineId);
            } catch (Exception e) {
                System.err.println("Error notifying listener of registry rehydrate: " + e.getMessage());
            }
        }
        
        // Registry events logged via MySQL history
        
        return context;
    }
    
    /**
     * Initialize MySQL connection provider
     */
    private void initializeConnectionProvider() {
        // Always try to initialize the connection provider
        // It will be used when debug mode is enabled later
        try {
            connectionProvider = new MysqlConnectionProvider();
            System.out.println("[Registry] Initialized MySQL connection provider for history tracking");
            
            // Initialize persistence provider for rehydration
            persistenceProvider = new MySQLPersistenceProvider(connectionProvider);
            ((MySQLPersistenceProvider) persistenceProvider).initialize();
            System.out.println("[Registry] Initialized persistence provider for state rehydration");
            
        } catch (Exception e) {
            System.err.println("[Registry] Failed to initialize MySQL connection provider: " + e.getMessage());
            connectionProvider = null;
            persistenceProvider = null;
        }
    }
    
    /**
     * Get the MySQL connection provider
     */
    public MysqlConnectionProvider getConnectionProvider() {
        return connectionProvider;
    }
    
    /**
     * Get the persistence provider
     */
    public PersistenceProvider<StateMachineContextEntity<?>> getPersistenceProvider() {
        return persistenceProvider;
    }
    
    /**
     * Set a ShardingEntity repository for advanced persistence scenarios
     * This is optional and provides integration with PartitionedRepository
     * 
     * @param repository The ShardingEntity repository to use
     */
    public <T extends ShardingEntity<K>, K> void setShardingRepository(
            ShardingEntityStateMachineRepository<T, K> repository) {
        this.shardingRepository = repository;
        System.out.println("[Registry] ShardingEntity repository configured (mode: " + repository.getLookupMode() + ")");
    }
    
    /**
     * Create or get a state machine with ShardingEntity support
     * Uses the ShardingEntity repository if configured
     */
    @SuppressWarnings("unchecked")
    public <T extends ShardingEntity<K> & StateMachineContextEntity<?>, K, TContext> GenericStateMachine<T, TContext> createOrGetWithSharding(
            K machineId,
            Supplier<GenericStateMachine<T, TContext>> factory,
            Class<T> entityClass) {
        
        String id = machineId.toString();
        
        // Check if already in memory
        GenericStateMachine<T, TContext> existing = (GenericStateMachine<T, TContext>) activeMachines.get(id);
        if (existing != null) {
            return existing;
        }
        
        // Try to load from ShardingEntity repository if configured
        if (shardingRepository != null) {
            try {
                ShardingEntityStateMachineRepository<T, K> typedRepo = 
                    (ShardingEntityStateMachineRepository<T, K>) shardingRepository;
                    
                T loadedEntity = typedRepo.findByMachineId(machineId);
                
                if (loadedEntity != null && loadedEntity instanceof StateMachineContextEntity) {
                    StateMachineContextEntity<?> contextEntity = (StateMachineContextEntity<?>) loadedEntity;
                    
                    if (contextEntity.isComplete()) {
                        System.out.println("[Registry] ShardingEntity machine " + id + " is complete - not rehydrating");
                        return null;
                    }
                    
                    System.out.println("[Registry] Rehydrating ShardingEntity machine " + id + " (state: " + contextEntity.getCurrentState() + ")");
                    
                    // Create new machine and set loaded entity
                    GenericStateMachine<T, TContext> machine = factory.get();
                    machine.setPersistingEntity(loadedEntity);
                    machine.restoreState(contextEntity.getCurrentState());
                    
                    register(id, machine);
                    lastAddedMachine = id;
                    
                    return machine;
                }
            } catch (Exception e) {
                System.err.println("[Registry] Failed to load from ShardingEntity repository for machine " + id + ": " + e.getMessage());
            }
        }
        
        // Fall back to regular persistence provider or create new
        return createOrGet(id, factory);
    }
    
    /**
     * Route an event to a state machine, rehydrating if necessary
     * This is the main entry point for event-driven rehydration
     * 
     * @param machineId The machine ID to route the event to
     * @param event The event to fire
     * @param machineFactory Factory to create the machine if needed for rehydration
     * @return true if event was successfully routed, false otherwise
     */
    public <TPersistingEntity extends StateMachineContextEntity<?>, TContext> boolean routeEvent(
            String machineId, 
            Object event,
            Supplier<GenericStateMachine<TPersistingEntity, TContext>> machineFactory) {
        
        System.out.println("[Registry] Routing event to machine " + machineId + ": " + event.getClass().getSimpleName());
        
        // Try to get or rehydrate the machine
        GenericStateMachine<TPersistingEntity, TContext> machine = createOrGet(machineId, machineFactory);
        
        if (machine == null) {
            System.out.println("[Registry] Machine " + machineId + " is complete or could not be created/rehydrated");
            return false;
        }
        
        // Fire the event
        try {
            // Cast event to appropriate type
            if (event instanceof com.telcobright.statemachine.events.StateMachineEvent) {
                machine.fire((com.telcobright.statemachine.events.StateMachineEvent) event);
            } else if (event instanceof String) {
                machine.fire((String) event);
            } else {
                System.err.println("[Registry] Unsupported event type: " + event.getClass().getName());
                return false;
            }
            System.out.println("[Registry] Event routed successfully to machine " + machineId);
            return true;
        } catch (Exception e) {
            System.err.println("[Registry] Failed to route event to machine " + machineId + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Simplified event routing when machine type is known
     */
    public boolean routeEvent(String machineId, Object event) {
        GenericStateMachine<?, ?> machine = getMachine(machineId);
        
        if (machine == null) {
            System.out.println("[Registry] Machine " + machineId + " not found in memory (rehydration requires factory)");
            return false;
        }
        
        try {
            // Cast event to appropriate type
            if (event instanceof com.telcobright.statemachine.events.StateMachineEvent) {
                machine.fire((com.telcobright.statemachine.events.StateMachineEvent) event);
            } else if (event instanceof String) {
                machine.fire((String) event);
            } else {
                System.err.println("[Registry] Unsupported event type: " + event.getClass().getName());
                return false;
            }
            System.out.println("[Registry] Event routed to existing machine " + machineId);
            return true;
        } catch (Exception e) {
            System.err.println("[Registry] Failed to route event to machine " + machineId + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Shutdown the registry and cleanup resources
     */
    @Override
    public void shutdown() {
        // Persist all active machines before shutdown
        if (persistenceProvider != null) {
            for (Map.Entry<String, GenericStateMachine<?, ?>> entry : activeMachines.entrySet()) {
                String machineId = entry.getKey();
                GenericStateMachine<?, ?> machine = entry.getValue();
                
                if (machine.getPersistingEntity() != null) {
                    try {
                        persistenceProvider.save(machineId, (StateMachineContextEntity<?>) machine.getPersistingEntity());
                        System.out.println("[Registry] Persisted machine " + machineId + " during shutdown");
                    } catch (Exception e) {
                        System.err.println("[Registry] Failed to persist machine " + machineId + " during shutdown: " + e.getMessage());
                    }
                }
            }
        }
        
        // Close connection provider if it exists
        if (connectionProvider != null) {
            connectionProvider.close();
            System.out.println("[Registry] Closed MySQL connection provider");
        }
        
        // Shutdown async executor
        if (asyncExecutor != null) {
            asyncExecutor.shutdown();
            try {
                if (!asyncExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    asyncExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                asyncExecutor.shutdownNow();
            }
        }
        
        // Call parent shutdown which handles WebSocket and other cleanup
        super.shutdown();
        
        // Additional cleanup specific to this implementation
        System.out.println("StateMachineRegistry shutdown complete.");
    }
}