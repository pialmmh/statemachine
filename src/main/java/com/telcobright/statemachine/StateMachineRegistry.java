package com.telcobright.statemachine;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.function.Function;
import com.telcobright.statemachine.timeout.TimeoutManager;
import com.telcobright.statemachine.StateMachineContextEntity;
import com.telcobright.statemachine.monitoring.SimpleDatabaseSnapshotRecorder;
import com.telcobright.statemachine.persistence.MysqlConnectionProvider;
import java.util.HashMap;

/**
 * Registry for managing state machine instances
 * Used for testing and lifecycle management
 */
public class StateMachineRegistry extends AbstractStateMachineRegistry {
    
    // MySQL connection provider for history tracking
    private MysqlConnectionProvider connectionProvider;
    
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
        
        // Apply snapshot debugging if enabled
        if (snapshotDebug && snapshotRecorder != null) {
            try {
                @SuppressWarnings("unchecked")
                GenericStateMachine<?, ?> genericMachine = machine;
                genericMachine.enableDebugFromRegistry(snapshotRecorder);
                genericMachine.setDebugSessionId("registry-session-" + System.currentTimeMillis());
                System.out.println("📸 Applied snapshot debug to machine: " + id);
            } catch (Exception e) {
                System.err.println("⚠️ Failed to apply snapshot debug to machine " + id + ": " + e.getMessage());
            }
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
        return activeMachines.get(id);
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
            return existing;
        }
        
        // TODO: Add persistence logic here
        // For now, just create new machine
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
        activeMachines.remove(id);
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
        } catch (Exception e) {
            System.err.println("[Registry] Failed to initialize MySQL connection provider: " + e.getMessage());
            connectionProvider = null;
        }
    }
    
    /**
     * Get the MySQL connection provider
     */
    public MysqlConnectionProvider getConnectionProvider() {
        return connectionProvider;
    }
    
    /**
     * Shutdown the registry and cleanup resources
     */
    @Override
    public void shutdown() {
        // Close connection provider if it exists
        if (connectionProvider != null) {
            connectionProvider.close();
            System.out.println("[Registry] Closed MySQL connection provider");
        }
        
        // Call parent shutdown which handles WebSocket and other cleanup
        super.shutdown();
        
        // Additional cleanup specific to this implementation
        System.out.println("StateMachineRegistry shutdown complete.");
    }
}