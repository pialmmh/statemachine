package com.telcobright.statemachine;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.function.Supplier;
import com.telcobright.statemachine.persistence.StateMachineSnapshotRepository;
import com.telcobright.statemachine.timeout.TimeoutManager;

/**
 * Registry for managing state machine instances
 * Used for testing and lifecycle management
 */
public class StateMachineRegistry {
    
    private final Map<String, GenericStateMachine> activeMachines = new ConcurrentHashMap<>();
    private final StateMachineSnapshotRepository repository;
    private final TimeoutManager timeoutManager;
    
    /**
     * Default constructor for testing
     */
    public StateMachineRegistry() {
        this.repository = null;
        this.timeoutManager = null;
    }
    
    /**
     * Constructor with repository and timeout manager
     */
    public StateMachineRegistry(StateMachineSnapshotRepository repository, TimeoutManager timeoutManager) {
        this.repository = repository;
        this.timeoutManager = timeoutManager;
    }
    
    /**
     * Register a state machine
     */
    public void register(String id, GenericStateMachine machine) {
        activeMachines.put(id, machine);
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
    public GenericStateMachine getActiveMachine(String id) {
        return activeMachines.get(id);
    }
    
    /**
     * Create or get a state machine
     * If exists in memory, return it
     * If not, try to load from persistence
     * If not in persistence, create new one
     */
    public GenericStateMachine createOrGet(String id, Supplier<GenericStateMachine> factory) {
        // Check if already in memory
        GenericStateMachine existing = activeMachines.get(id);
        if (existing != null) {
            return existing;
        }
        
        // TODO: Add persistence logic here
        // For now, just create new machine
        GenericStateMachine machine = factory.get();
        register(id, machine);
        return machine;
    }
    
    /**
     * Create or get a state machine (legacy method)
     */
    public GenericStateMachine createOrGet(String id) {
        // Check if already in memory
        GenericStateMachine existing = activeMachines.get(id);
        if (existing != null) {
            return existing;
        }
        
        // Create new machine with default factory
        GenericStateMachine machine = new GenericStateMachine(id, repository, timeoutManager, this);
        register(id, machine);
        return machine;
    }
    
    /**
     * Get all active machines
     */
    public Map<String, GenericStateMachine> getActiveMachines() {
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