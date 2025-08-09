package com.telcobright.statemachine;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.Function;
import com.telcobright.statemachine.timeout.TimeoutManager;
import com.telcobright.statemachine.StateMachineContextEntity;
import com.telcobright.statemachine.monitoring.SimpleDatabaseSnapshotRecorder;

/**
 * Registry for managing state machine instances
 * Used for testing and lifecycle management
 */
public class StateMachineRegistry {
    
    private final Map<String, GenericStateMachine<?, ?>> activeMachines = new ConcurrentHashMap<>();
    private final TimeoutManager timeoutManager;
    private boolean debugMode = false;
    private SimpleDatabaseSnapshotRecorder snapshotRecorder;
    
    /**
     * Default constructor for testing
     */
    public StateMachineRegistry() {
        this.timeoutManager = null;
    }
    
    /**
     * Constructor with timeout manager
     */
    public StateMachineRegistry(TimeoutManager timeoutManager) {
        this.timeoutManager = timeoutManager;
    }
    
    /**
     * Enable debug mode with monitoring for all machines managed by this registry
     */
    public void enableDebugMode() {
        this.debugMode = true;
        this.snapshotRecorder = new SimpleDatabaseSnapshotRecorder();
        System.out.println("🐛 Debug mode ENABLED for StateMachineRegistry");
        System.out.println("📊 All machines will automatically use monitoring");
    }
    
    /**
     * Enable debug mode with custom snapshot recorder
     */
    public void enableDebugMode(SimpleDatabaseSnapshotRecorder recorder) {
        this.debugMode = true;
        this.snapshotRecorder = recorder;
        System.out.println("🐛 Debug mode ENABLED for StateMachineRegistry with custom recorder");
    }
    
    /**
     * Disable debug mode
     */
    public void disableDebugMode() {
        this.debugMode = false;
        this.snapshotRecorder = null;
        System.out.println("🐛 Debug mode DISABLED for StateMachineRegistry");
    }
    
    /**
     * Check if debug mode is enabled
     */
    public boolean isDebugMode() {
        return debugMode;
    }
    
    /**
     * Register a state machine
     * Automatically applies debug mode if enabled
     */
    public void register(String id, GenericStateMachine<?, ?> machine) {
        activeMachines.put(id, machine);
        
        // Apply debug mode if enabled
        if (debugMode && snapshotRecorder != null) {
            try {
                @SuppressWarnings("unchecked")
                GenericStateMachine<?, ?> genericMachine = machine;
                genericMachine.enableDebugFromRegistry(snapshotRecorder);
                genericMachine.setDebugSessionId("registry-session-" + System.currentTimeMillis());
                System.out.println("📊 Applied debug mode to machine: " + id);
            } catch (Exception e) {
                System.err.println("⚠️ Failed to apply debug mode to machine " + id + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Remove a state machine from the registry
     */
    public void removeMachine(String id) {
        activeMachines.remove(id);
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
     * Get count of active machines
     */
    public int size() {
        return activeMachines.size();
    }
    
    /**
     * Check if a machine is registered in the registry
     */
    public boolean isRegistered(String id) {
        return activeMachines.containsKey(id);
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
     * Shutdown the registry and cleanup resources
     */
    public void shutdown() {
        // Clear all active machines
        activeMachines.clear();
        
        // Shutdown timeout manager if available
        if (timeoutManager != null) {
            timeoutManager.shutdown();
        }
        
        System.out.println("StateMachineRegistry shutdown complete.");
    }
}