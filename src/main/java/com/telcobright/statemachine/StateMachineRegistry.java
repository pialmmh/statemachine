package com.telcobright.statemachine;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.function.Function;
import com.telcobright.statemachine.timeout.TimeoutManager;
import com.telcobright.statemachine.StateMachineContextEntity;
import com.telcobright.statemachine.persistence.MysqlConnectionProvider;
import com.telcobright.statemachine.persistence.PersistenceProvider;
import com.telcobright.statemachine.persistence.MySQLPersistenceProvider;
import com.telcobright.statemachine.persistence.OptimizedMySQLPersistenceProvider;
import com.telcobright.statemachine.persistence.BaseStateMachineEntity;
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
    
    // Unique registry ID
    private final String registryId;
    
    // MySQL connection provider for history tracking
    private MysqlConnectionProvider connectionProvider;
    
    // Persistence provider for state machine contexts
    private PersistenceProvider<StateMachineContextEntity<?>> persistenceProvider;
    
    // Asynchronous logging executor
    private final ExecutorService asyncLogExecutor;
    
    // Sample logging configuration for registry events
    private SampleLoggingConfig registrySampleLogging = SampleLoggingConfig.ALL;
    
    /**
     * Create async logging executor
     */
    private ExecutorService createAsyncLogExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "RegistryLogger-" + registryId);
            t.setDaemon(true); // Don't prevent JVM shutdown
            return t;
        });
    }
    
    // Track the entity class for persistence loading
    private Class<? extends StateMachineContextEntity<?>> persistenceEntityClass;
    
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
        this.registryId = "default";
        this.asyncLogExecutor = createAsyncLogExecutor();
        initializeConnectionProvider();
    }
    
    /**
     * Constructor with registry ID
     */
    public StateMachineRegistry(String registryId) {
        super();
        this.registryId = registryId;
        this.asyncLogExecutor = createAsyncLogExecutor();
        initializeConnectionProvider();
    }
    
    /**
     * Constructor with timeout manager
     */
    public StateMachineRegistry(TimeoutManager timeoutManager) {
        super(timeoutManager);
        this.registryId = "default";
        this.asyncLogExecutor = createAsyncLogExecutor();
        initializeConnectionProvider();
    }
    
    /**
     * Constructor with registry ID and timeout manager
     */
    public StateMachineRegistry(String registryId, TimeoutManager timeoutManager) {
        super(timeoutManager);
        this.registryId = registryId;
        this.asyncLogExecutor = createAsyncLogExecutor();
        initializeConnectionProvider();
    }
    
    /**
     * Constructor with timeout manager and WebSocket port
     */
    public StateMachineRegistry(TimeoutManager timeoutManager, int webSocketPort) {
        super(timeoutManager, webSocketPort);
        this.registryId = "default";
        this.asyncLogExecutor = createAsyncLogExecutor();
        initializeConnectionProvider();
    }
    
    /**
     * Constructor with registry ID, timeout manager and WebSocket port
     */
    public StateMachineRegistry(String registryId, TimeoutManager timeoutManager, int webSocketPort) {
        super(timeoutManager, webSocketPort);
        this.registryId = registryId;
        
        // Initialize asynchronous logging executor with single thread
        this.asyncLogExecutor = createAsyncLogExecutor();
        
        initializeConnectionProvider();
    }
    
    /**
     * Override to enable registry logging when debug mode is activated
     */
    @Override
    public void enableDebugMode(int port) {
        // Call parent to enable debug mode
        super.enableDebugMode(port);
        
        // Now log registry startup since debug mode is enabled
        logRegistryEventSync(RegistryEventType.STARTUP, null, 
            "Registry '" + registryId + "' debug mode enabled", 
            "WebSocket port: " + port + ", sample logging: " + registrySampleLogging);
    }
    
    /**
     * Configure sample logging for registry events
     */
    public void setRegistrySampleLogging(SampleLoggingConfig config) {
        this.registrySampleLogging = config != null ? config : SampleLoggingConfig.DISABLED;
        System.out.println("[Registry-" + registryId + "] Registry sample logging configured: " + this.registrySampleLogging);
    }
    
    /**
     * Register a state machine
     * Automatically applies debug mode if enabled
     */
    @Override
    public void register(String id, GenericStateMachine<?, ?> machine) {
        activeMachines.put(id, machine);
        lastAddedMachine = id; // Track last added machine
        
        // Log machine registration
        logRegistryEvent(RegistryEventType.REGISTER, id, "Machine registered with initial state: " + machine.getCurrentState(), null);
        
        System.out.println("[Registry-" + registryId + "] Machine registered: " + id + " - setting lastAddedMachine to: " + id);
        
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
                
            // Check if machine should be evicted after reaching final state
            evictIfFinalState(id);
        });
        
        // Set up offline transition callback to persist and evict machine
        machine.setOnOfflineTransition(m -> {
            System.out.println("[Registry] Machine " + id + " entering offline state");
            
            // Log offline transition
            logRegistryEvent(RegistryEventType.OFFLINE, id, "Machine going offline from state: " + m.getCurrentState(), "Moving to offline storage");
            
            // Persist the machine state before removing from memory
            if (persistenceProvider != null && m.getPersistingEntity() != null) {
                try {
                    persistenceProvider.save(id, (StateMachineContextEntity<?>) m.getPersistingEntity());
                    logRegistryEvent(RegistryEventType.PERSISTENCE, id, "Machine state persisted successfully", "State: " + m.getCurrentState());
                    System.out.println("[Registry] Persisted offline machine " + id + " (state: " + m.getCurrentState() + ")");
                } catch (Exception e) {
                    logRegistryEvent(RegistryEventType.ERROR, id, "Failed to persist machine state", e.getMessage());
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
            
            // Persist the machine's context before removal
            if (persistenceProvider != null && machine.getPersistingEntity() != null) {
                try {
                    persistenceProvider.save(id, (StateMachineContextEntity<?>) machine.getPersistingEntity());
                    System.out.println("[Registry-" + registryId + "] Persisted machine " + id + " during removal (state: " + machine.getCurrentState() + ")");
                } catch (Exception e) {
                    System.err.println("[Registry-" + registryId + "] Failed to persist machine " + id + " during removal: " + e.getMessage());
                }
            }
        }
        
        // Close history tracker if machine has one
        if (machine != null) {
            machine.closeHistoryTracker();
        }
        
        notifyRegistryRemove(id);
        
        // Registry events logged via MySQL history
    }
    
    /**
     * Evict machine when it reaches a final state
     */
    public void evictIfFinalState(String machineId) {
        GenericStateMachine<?, ?> machine = activeMachines.get(machineId);
        if (machine != null) {
            // Check if current state is marked as final
            if (isFinalState(machine.getCurrentState())) {
                System.out.println("[Registry-" + registryId + "] Machine " + machineId + " reached final state: " + machine.getCurrentState() + " - evicting from memory");
                
                // Mark machine as complete before eviction
                if (machine.getPersistingEntity() != null) {
                    machine.getPersistingEntity().setComplete(true);
                }
                
                // Log eviction to registry table
                logRegistryEvent(RegistryEventType.EVICT, machineId, "Final state: " + machine.getCurrentState(), "Machine reached final state and was evicted");
                
                // Remove from active machines
                removeMachine(machineId);
                
                System.out.println("[Registry-" + registryId + "] Machine " + machineId + " evicted successfully");
            }
        }
    }
    
    /**
     * Check if a state is marked as final
     */
    private boolean isFinalState(String stateName) {
        // For now, check if state name contains "FINAL" or specific final state names
        if (stateName == null) return false;
        
        String upperState = stateName.toUpperCase();
        return upperState.contains("FINAL") || 
               upperState.equals("HUNGUP") || 
               upperState.equals("COMPLETED") || 
               upperState.equals("TERMINATED") || 
               upperState.equals("FINISHED");
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
        // Only return machines from active registry
        // Offline machines should NOT be returned directly - they need rehydration
        GenericStateMachine<?, ?> machine = activeMachines.get(id);
        
        if (machine == null && hasWebSocketClients() && offlineMachinesForDebug.containsKey(id)) {
            // Machine exists in offline cache but we don't return it
            // It will need to be rehydrated when an event is sent
            System.out.println("[Registry] Machine " + id + " is in offline debug cache - requires rehydration");
            return null;
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
        // Step 1: Check if machine already exists in memory
        GenericStateMachine<TPersistingEntity, TContext> existingMachine = checkIfInMemory(id);
        if (existingMachine != null) {
            return existingMachine;
        }
        
        // Step 2: Check if machine can be rehydrated from persistence
        TPersistingEntity persistedContext = attemptRehydration(id);
        if (persistedContext != null) {
            return rehydrateMachine(id, factory, persistedContext);
        }
        
        // Step 3: Create new machine if not found in memory or persistence
        return createNewMachine(id, factory);
    }
    
    /**
     * Step 1: Check if machine already exists in active memory
     */
    @SuppressWarnings("unchecked")
    private <TPersistingEntity extends StateMachineContextEntity<?>, TContext> GenericStateMachine<TPersistingEntity, TContext> checkIfInMemory(String id) {
        GenericStateMachine<TPersistingEntity, TContext> existing = (GenericStateMachine<TPersistingEntity, TContext>) activeMachines.get(id);
        if (existing != null) {
            System.out.println("[Registry] Machine " + id + " found in memory");
        }
        return existing;
    }
    
    /**
     * Step 2: Attempt to load machine context from persistence
     */
    @SuppressWarnings("unchecked")
    private <TPersistingEntity extends StateMachineContextEntity<?>> TPersistingEntity attemptRehydration(String id) {
        if (persistenceProvider == null || persistenceEntityClass == null) {
            System.out.println("[Registry] No persistence provider configured for machine " + id);
            return null;
        }
        
        try {
            System.out.println("[Registry] Attempting to load context for machine " + id + " using class " + persistenceEntityClass.getSimpleName());
            TPersistingEntity loadedContext = (TPersistingEntity) persistenceProvider.load(id, (Class<StateMachineContextEntity<?>>) persistenceEntityClass);
            
            if (loadedContext != null) {
                System.out.println("[Registry] Successfully loaded context for machine " + id + ": " + loadedContext);
                
                // Don't rehydrate completed machines
                if (loadedContext.isComplete()) {
                    System.out.println("[Registry] Machine " + id + " is complete - not rehydrating");
                    return null;
                }
                
                return loadedContext;
            } else {
                System.out.println("[Registry] No persisted context found for machine " + id);
                return null;
            }
        } catch (Exception e) {
            System.err.println("[Registry] Failed to load from persistence for machine " + id + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Step 3a: Rehydrate machine from persisted context
     */
    private <TPersistingEntity extends StateMachineContextEntity<?>, TContext> GenericStateMachine<TPersistingEntity, TContext> rehydrateMachine(
            String id, 
            Supplier<GenericStateMachine<TPersistingEntity, TContext>> factory, 
            TPersistingEntity persistedContext) {
        
        System.out.println("[Registry] Rehydrating machine " + id + " from persistence (state: " + persistedContext.getCurrentState() + ")");
        
        // Create machine instance
        GenericStateMachine<TPersistingEntity, TContext> machine = factory.get();
        
        // Set persistent context
        setPersistentContext(machine, persistedContext);
        
        // Restore state
        restoreState(machine, persistedContext.getCurrentState());
        
        // Register machine
        registerRehydratedMachine(id, machine);
        
        // Notify rehydration listeners
        notifyRehydrationListeners(id);
        
        return machine;
    }
    
    /**
     * Step 3b: Create completely new machine
     */
    private <TPersistingEntity extends StateMachineContextEntity<?>, TContext> GenericStateMachine<TPersistingEntity, TContext> createNewMachine(
            String id, 
            Supplier<GenericStateMachine<TPersistingEntity, TContext>> factory) {
        
        System.out.println("[Registry] Creating new machine " + id);
        GenericStateMachine<TPersistingEntity, TContext> machine = factory.get();
        register(id, machine);
        return machine;
    }
    
    /**
     * Set the persistent context on a machine
     */
    private <TPersistingEntity extends StateMachineContextEntity<?>, TContext> void setPersistentContext(
            GenericStateMachine<TPersistingEntity, TContext> machine, 
            TPersistingEntity context) {
        machine.setPersistingEntity(context);
        System.out.println("[Registry] Set persistent context for machine: " + context);
    }
    
    /**
     * Set volatile context/data on a machine (non-persisted data)
     * This could be used for runtime data that doesn't need persistence
     */
    private <TPersistingEntity extends StateMachineContextEntity<?>, TContext> void setVolatileContext(
            GenericStateMachine<TPersistingEntity, TContext> machine, 
            TContext volatileContext) {
        if (volatileContext != null) {
            // Note: GenericStateMachine doesn't have a direct setContext method for volatile data
            // This is a placeholder for future extension if needed
            System.out.println("[Registry] Would set volatile context (feature not implemented): " + volatileContext);
        }
    }
    
    /**
     * Restore the machine state (handles timeout checks)
     */
    private <TPersistingEntity extends StateMachineContextEntity<?>, TContext> void restoreState(
            GenericStateMachine<TPersistingEntity, TContext> machine, 
            String state) {
        machine.restoreState(state);
    }
    
    /**
     * Register a rehydrated machine and track it
     */
    private void registerRehydratedMachine(String id, GenericStateMachine<?, ?> machine) {
        register(id, machine);
        lastAddedMachine = id; // Track as rehydrated
    }
    
    /**
     * Notify all listeners of machine rehydration
     */
    private void notifyRehydrationListeners(String id) {
        for (StateMachineListener listener : listeners) {
            try {
                listener.onRegistryRehydrate(id);
            } catch (Exception e) {
                System.err.println("Error notifying listener of registry rehydrate: " + e.getMessage());
            }
        }
    }
    
    /**
     * Check if machine is offline (exists in debug cache but not in active memory)
     */
    private boolean checkIfOffline(String id) {
        boolean inMemory = activeMachines.containsKey(id);
        boolean inOfflineCache = hasWebSocketClients() && offlineMachinesForDebug.containsKey(id);
        
        if (inOfflineCache && !inMemory) {
            System.out.println("[Registry] Machine " + id + " is in offline debug cache - requires rehydration");
            return true;
        }
        return false;
    }
    
    /**
     * Validate that machine context is ready for operation
     */
    private <TPersistingEntity extends StateMachineContextEntity<?>> boolean validateContext(TPersistingEntity context) {
        if (context == null) {
            System.err.println("[Registry] Machine context is null");
            return false;
        }
        
        if (context.isComplete()) {
            System.out.println("[Registry] Machine context is marked as complete");
            return false;
        }
        
        System.out.println("[Registry] Context validation passed");
        return true;
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
     * Bring a machine back online from offline state
     * Moves machine from offline cache back to active machines
     */
    public void bringMachineOnline(String id, GenericStateMachine<?, ?> machine) {
        // Remove from offline cache
        GenericStateMachine<?, ?> offlineMachine = offlineMachinesForDebug.remove(id);
        
        if (offlineMachine != null || machine != null) {
            // Use the provided machine or the one from offline cache
            GenericStateMachine<?, ?> machineToRestore = machine != null ? machine : offlineMachine;
            
            // Add back to active machines
            activeMachines.put(id, machineToRestore);
            
            System.out.println("[Registry] Machine " + id + " brought back online (moved from offline cache to active)");
            
            // Update lastAddedMachine since it's coming back online
            lastAddedMachine = id;
            
            // Broadcast updated machines list and offline list
            if (webSocketServer != null) {
                webSocketServer.broadcastMachinesList();
                webSocketServer.broadcastOfflineMachines();
            }
            
            // Record in history
            if (debugMode && history != null) {
                CompletableFuture.runAsync(() -> {
                    try {
                        history.recordMachineOnline(id);
                    } catch (Exception e) {
                        System.err.println("[Registry] Error recording machine online: " + e.getMessage());
                    }
                }, asyncExecutor);
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
        
        // Check if already in active memory
        GenericStateMachine<?, ?> existing = activeMachines.get(machineId);
        if (existing != null && existing.getPersistingEntity() != null) {
            try {
                return contextClass.cast(existing.getPersistingEntity());
            } catch (ClassCastException e) {
                // Machine exists but with different context type
                throw new IllegalStateException("Machine " + machineId + " exists with different context type");
            }
        }
        
        // Store offline cache reference for validation if in debug mode
        GenericStateMachine<?, ?> offlineMachine = null;
        T offlinePersistentContext = null;
        Object offlineVolatileContext = null;
        
        if (debugMode && offlineMachinesForDebug.containsKey(machineId)) {
            offlineMachine = offlineMachinesForDebug.get(machineId);
            if (offlineMachine != null) {
                try {
                    offlinePersistentContext = contextClass.cast(offlineMachine.getPersistingEntity());
                    // Note: Volatile context is not directly accessible from GenericStateMachine
                    System.out.println("[Registry] Found machine " + machineId + " in offline cache for validation");
                } catch (Exception e) {
                    System.err.println("[Registry] Could not extract contexts from offline machine: " + e.getMessage());
                }
            }
        }
        
        // Load from persistence
        T persistedContext = null;
        if (persistenceProvider != null) {
            try {
                @SuppressWarnings("unchecked")
                T loaded = (T) persistenceProvider.load(machineId, null);
                if (loaded != null) {
                    persistedContext = loaded;
                    System.out.println("[Registry] Loaded context from persistence for " + machineId + " (state: " + loaded.getCurrentState() + ")");
                }
            } catch (Exception e) {
                System.err.println("[Registry] Failed to load from persistence: " + e.getMessage());
            }
        }
        
        // If not found in persistence, use context supplier
        T context = persistedContext != null ? persistedContext : contextSupplier.get();
        
        // Validate contexts if in debug mode and offline machine exists
        if (debugMode && offlinePersistentContext != null && persistedContext != null) {
            validateContextConsistency(machineId, offlinePersistentContext, persistedContext, offlineVolatileContext);
        }
        
        // Create machine with the loaded/supplied context
        GenericStateMachine<T, ?> machine = machineBuilder.apply(context);
        
        // Restore state if we loaded from persistence
        if (persistedContext != null) {
            machine.setPersistingEntity(persistedContext);
            machine.restoreState(persistedContext.getCurrentState());
            System.out.println("[Registry] Restored machine " + machineId + " to state: " + persistedContext.getCurrentState());
        }
        
        // Remove from offline cache if present
        if (offlineMachinesForDebug.remove(machineId) != null) {
            System.out.println("[Registry] Removed " + machineId + " from offline debug cache");
        }
        
        // Register the rehydrated machine
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
        
        return context;
    }
    
    /**
     * Validate context consistency between offline cache and persisted data
     */
    private <T extends StateMachineContextEntity<?>> void validateContextConsistency(
            String machineId, T offlineContext, T persistedContext, Object offlineVolatileContext) {
        
        System.out.println("[Registry] Validating context consistency for machine " + machineId);
        
        // Compare persistent contexts
        boolean persistentMatch = true;
        StringBuilder persistentDiff = new StringBuilder();
        
        // Check current state
        if (!offlineContext.getCurrentState().equals(persistedContext.getCurrentState())) {
            persistentMatch = false;
            persistentDiff.append("\n  - State mismatch: offline=").append(offlineContext.getCurrentState())
                        .append(", persisted=").append(persistedContext.getCurrentState());
        }
        
        // Check completion status
        if (offlineContext.isComplete() != persistedContext.isComplete()) {
            persistentMatch = false;
            persistentDiff.append("\n  - Complete flag mismatch: offline=").append(offlineContext.isComplete())
                        .append(", persisted=").append(persistedContext.isComplete());
        }
        
        // Note: ID checking would require specific context type knowledge
        // For now, we rely on state and completion status checks
        
        // Additional context-specific validation can be added here
        // For now, we do a toString comparison as a catch-all
        String offlineStr = offlineContext.toString();
        String persistedStr = persistedContext.toString();
        if (!offlineStr.equals(persistedStr)) {
            System.out.println("[Registry] WARNING: Context toString mismatch detected");
            System.out.println("  Offline:   " + offlineStr);
            System.out.println("  Persisted: " + persistedStr);
        }
        
        if (!persistentMatch) {
            String errorMsg = "[Registry] ASSERTION FAILED: Persistent context mismatch for machine " + machineId + persistentDiff.toString();
            System.err.println(errorMsg);
            throw new IllegalStateException(errorMsg);
        }
        
        // Log volatile context for debugging (can't really validate it as it's recreated)
        if (offlineVolatileContext != null) {
            System.out.println("[Registry] Offline volatile context exists (will be recreated): " + 
                             offlineVolatileContext.getClass().getSimpleName());
        }
        
        System.out.println("[Registry] ✓ Context validation passed for machine " + machineId);
    }
    
    /**
     * Initialize MySQL connection provider
     */
    private void initializeConnectionProvider() {
        // Always try to initialize the connection provider
        // It will be used when debug mode is enabled later
        try {
            connectionProvider = new MysqlConnectionProvider();
            System.out.println("[Registry-" + registryId + "] Initialized MySQL connection provider for history tracking");
            
            // Initialize persistence provider for rehydration
            persistenceProvider = new MySQLPersistenceProvider(connectionProvider);
            persistenceProvider.initialize();
            System.out.println("[Registry-" + registryId + "] Initialized persistence provider for state rehydration");
            
            // Create registry table for event logging
            createRegistryTable();
            
        } catch (Exception e) {
            System.err.println("[Registry-" + registryId + "] Failed to initialize MySQL connection provider: " + e.getMessage());
            connectionProvider = null;
            persistenceProvider = null;
        }
    }
    
    /**
     * Create simplified registry table for event logging
     * Focuses on registry-level events rather than state tracking
     * Creates table when debug mode OR sampling is configured
     */
    private void createRegistryTable() {
        // Create registry table when debug mode is enabled OR sampling is configured
        if (connectionProvider == null) return;
        
        String tableName = "registry_" + registryId;
        
        try (var connection = connectionProvider.getConnection()) {
            // First, drop the old table if it exists to start fresh with new simplified structure
            String dropTableSQL = String.format("DROP TABLE IF EXISTS %s", tableName);
            try (var stmt = connection.prepareStatement(dropTableSQL)) {
                stmt.execute();
            }
            
            // Create the new simplified table structure
            String createTableSQL = String.format("""
                CREATE TABLE %s (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    event_type VARCHAR(50) NOT NULL,
                    machine_id VARCHAR(255),
                    event_details VARCHAR(500),
                    reason VARCHAR(255),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_event_type (event_type),
                    INDEX idx_machine_id (machine_id),
                    INDEX idx_created_at (created_at)
                )
                """, tableName);
            
            try (var stmt = connection.prepareStatement(createTableSQL)) {
                stmt.execute();
                System.out.println("[Registry-" + registryId + "] Created/verified simplified registry table: " + tableName);
            }
        } catch (Exception e) {
            System.err.println("[Registry-" + registryId + "] Failed to create registry table: " + e.getMessage());
        }
    }
    
    /**
     * Log simplified registry event to table asynchronously 
     * - If sampling is configured, applies sampling regardless of debug mode
     * - In debug mode, logs ALL events (ignores sampling)
     */
    public void logRegistryEvent(RegistryEventType eventType, String machineId, String eventDetails, String reason) {
        // Skip if no connection or executor is shutdown
        if (connectionProvider == null || asyncLogExecutor.isShutdown()) {
            return;
        }
        
        // In debug mode, log everything (ignore sampling)
        if (debugMode) {
            // Debug mode: log all events
        } else {
            // Non-debug mode: apply sampling if configured
            if (!registrySampleLogging.shouldLog()) {
                return;
            }
        }
        
        // Submit logging task asynchronously to avoid blocking main thread
        CompletableFuture.runAsync(() -> {
            String tableName = "registry_" + registryId;
            String insertSQL = String.format("""
                INSERT INTO %s (event_type, machine_id, event_details, reason)
                VALUES (?, ?, ?, ?)
                """, tableName);
            
            try (var connection = connectionProvider.getConnection();
                 var stmt = connection.prepareStatement(insertSQL)) {
                stmt.setString(1, eventType.getEventName());
                stmt.setString(2, machineId);
                stmt.setString(3, eventDetails);
                stmt.setString(4, reason);
                stmt.executeUpdate();
                
                String logMessage = String.format("[Registry-%s] %s", registryId, eventType.getEventName());
                if (machineId != null) {
                    logMessage += " for machine " + machineId;
                }
                if (reason != null) {
                    logMessage += " (reason: " + reason + ")";
                }
                System.out.println(logMessage);
            } catch (Exception e) {
                System.err.println("[Registry-" + registryId + "] Failed to log registry event asynchronously: " + e.getMessage());
            }
        }, asyncLogExecutor);
    }
    
    /**
     * Convenience method to log ignored events
     */
    public void logIgnoredEvent(String machineId, String eventName, String reason) {
        logRegistryEvent(RegistryEventType.IGNORE, machineId, "Event: " + eventName, reason);
    }
    
    /**
     * Shutdown the async logging executor
     */
    public void shutdownAsyncLogging() {
        // Log shutdown event synchronously to ensure it's recorded
        if (connectionProvider != null && !asyncLogExecutor.isShutdown()) {
            logRegistryEventSync(RegistryEventType.SHUTDOWN, null, "Registry '" + registryId + "' shutting down", "Cleanup and resource release");
        }
        
        // Shutdown async executor gracefully
        asyncLogExecutor.shutdown();
        try {
            if (!asyncLogExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncLogExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncLogExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        System.out.println("[Registry-" + registryId + "] Async logging shutdown complete");
    }
    
    /**
     * Synchronous version for critical logging (shutdown, startup)
     * - If sampling is configured, applies sampling regardless of debug mode
     * - In debug mode, logs ALL events (ignores sampling)
     */
    private void logRegistryEventSync(RegistryEventType eventType, String machineId, String eventDetails, String reason) {
        // Skip if no connection
        if (connectionProvider == null) return;
        
        // In debug mode, log everything (ignore sampling)
        if (debugMode) {
            // Debug mode: log all events
        } else {
            // Non-debug mode: apply sampling if configured
            if (!registrySampleLogging.shouldLog()) {
                return;
            }
        }
        
        String tableName = "registry_" + registryId;
        String insertSQL = String.format("""
            INSERT INTO %s (event_type, machine_id, event_details, reason)
            VALUES (?, ?, ?, ?)
            """, tableName);
        
        try (var connection = connectionProvider.getConnection();
             var stmt = connection.prepareStatement(insertSQL)) {
            stmt.setString(1, eventType.getEventName());
            stmt.setString(2, machineId);
            stmt.setString(3, eventDetails);
            stmt.setString(4, reason);
            stmt.executeUpdate();
            
            String logMessage = String.format("[Registry-%s] %s", registryId, eventType.getEventName());
            if (machineId != null) {
                logMessage += " for machine " + machineId;
            }
            if (reason != null) {
                logMessage += " (reason: " + reason + ")";
            }
            System.out.println(logMessage);
        } catch (Exception e) {
            System.err.println("[Registry-" + registryId + "] Failed to log registry event synchronously: " + e.getMessage());
        }
    }
    
    /**
     * Check if machine exists in persistence and get its last state
     */
    private String getPersistedMachineState(String machineId) {
        if (persistenceProvider == null || persistenceEntityClass == null) {
            return null;
        }
        
        try {
            @SuppressWarnings("unchecked")
            StateMachineContextEntity<?> context = persistenceProvider.load(machineId, (Class<StateMachineContextEntity<?>>) persistenceEntityClass);
            return context != null ? context.getCurrentState() : null;
        } catch (Exception e) {
            System.err.println("[Registry-" + registryId + "] Failed to check persisted state for machine " + machineId + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Get registry ID
     */
    public String getRegistryId() {
        return registryId;
    }
    
    /**
     * Send event to a machine with final state checking
     */
    public boolean sendEvent(String machineId, com.telcobright.statemachine.events.StateMachineEvent event) {
        // First check if machine is in active memory
        GenericStateMachine<?, ?> machine = activeMachines.get(machineId);
        
        if (machine != null) {
            // Machine is active - send event normally
            String oldState = machine.getCurrentState();
            machine.fire(event);
            String newState = machine.getCurrentState();
            
            // Log successful event processing - but we don't log every state transition here since that's in history
            // Only log if it's a significant registry event
            
            System.out.println("[Registry-" + registryId + "] Event " + event.getClass().getSimpleName() + " sent to " + machineId + " (" + oldState + " -> " + newState + ")");
            return true;
        }
        
        // Machine not in memory - check persistence
        String persistedState = getPersistedMachineState(machineId);
        
        if (persistedState == null) {
            // Machine doesn't exist in persistence - ignore event
            logIgnoredEvent(machineId, event.getClass().getSimpleName(), "Machine not found in persistence");
            System.out.println("[Registry-" + registryId + "] Event " + event.getClass().getSimpleName() + " ignored for " + machineId + " - machine not found");
            return false;
        }
        
        if (isFinalState(persistedState)) {
            // Machine is in final state - ignore event  
            logIgnoredEvent(machineId, event.getClass().getSimpleName(), "Machine in final state: " + persistedState);
            System.out.println("[Registry-" + registryId + "] Event " + event.getClass().getSimpleName() + " ignored for " + machineId + " - machine in final state: " + persistedState);
            return false;
        }
        
        // Machine exists but not in final state - could rehydrate, but for now just log
        logIgnoredEvent(machineId, event.getClass().getSimpleName(), "Machine not in memory, rehydration needed");
        System.out.println("[Registry-" + registryId + "] Event " + event.getClass().getSimpleName() + " ignored for " + machineId + " - machine not in memory (last state: " + persistedState + ")");
        return false;
    }
    
    /**
     * Set optimized persistence provider for a specific entity type
     * This provider uses pre-compiled SQL and async writes for optimal performance
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends StateMachineContextEntity<?>> void setOptimizedPersistenceProvider(
            Class<T> entityClass, String tableName) {
        if (connectionProvider != null) {
            try {
                OptimizedMySQLPersistenceProvider optimizedProvider = 
                    new OptimizedMySQLPersistenceProvider(connectionProvider, entityClass, tableName);
                optimizedProvider.initialize();
                
                // Replace the default provider with optimized one
                this.persistenceProvider = optimizedProvider;
                this.persistenceEntityClass = entityClass;
                
                System.out.println("[Registry] Switched to optimized persistence provider for entity: " + 
                                 entityClass.getSimpleName() + " (table: " + tableName + ")");
            } catch (Exception e) {
                System.err.println("[Registry] Failed to set optimized persistence provider: " + e.getMessage());
            }
        }
    }
    
    /**
     * Set optimized persistence provider with auto-inferred table name
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends StateMachineContextEntity<?>> void setOptimizedPersistenceProvider(Class<T> entityClass) {
        String tableName = OptimizedMySQLPersistenceProvider.inferTableName(entityClass);
        setOptimizedPersistenceProvider(entityClass, tableName);
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
        
        // Shutdown async logging first
        shutdownAsyncLogging();
        
        // Shutdown async executor
        if (asyncExecutor != null) {
            asyncExecutor.shutdown();
            try {
                if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
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