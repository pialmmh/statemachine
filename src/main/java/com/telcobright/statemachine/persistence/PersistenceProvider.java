package com.telcobright.statemachine.persistence;

import com.telcobright.statemachine.StateMachineContextEntity;
import java.util.function.Supplier;

/**
 * Interface for persisting and loading state machine contexts.
 * Implementations can use different storage backends (MySQL, MongoDB, etc.)
 * 
 * @param <T> The type of the persisting entity
 */
public interface PersistenceProvider<T extends StateMachineContextEntity<?>> {
    
    /**
     * Initialize the persistence provider (e.g., create database tables)
     * Should be called before using the provider
     */
    void initialize();
    
    /**
     * Save or update a state machine context
     * 
     * @param machineId The unique identifier of the state machine
     * @param context The context to persist
     */
    void save(String machineId, T context);
    
    /**
     * Load a state machine context from persistence
     * 
     * @param machineId The unique identifier of the state machine
     * @param contextType The class type of the context
     * @return The loaded context, or null if not found
     */
    T load(String machineId, Class<T> contextType);
    
    /**
     * Check if a state machine exists in persistence
     * 
     * @param machineId The unique identifier of the state machine
     * @return true if the machine exists, false otherwise
     */
    boolean exists(String machineId);
    
    /**
     * Delete a state machine from persistence
     * 
     * @param machineId The unique identifier of the state machine
     */
    void delete(String machineId);
    
    /**
     * Load a context and check if it's complete
     * 
     * @param machineId The unique identifier of the state machine
     * @return true if the machine exists and is complete, false otherwise
     */
    default boolean isComplete(String machineId) {
        T context = load(machineId, null);
        return context != null && context.isComplete();
    }
    
    /**
     * Create or get a context, using the supplier if not found
     * 
     * @param machineId The unique identifier of the state machine
     * @param contextType The class type of the context
     * @param contextSupplier Supplier to create new context if not found
     * @return The loaded or newly created context
     */
    default T loadOrCreate(String machineId, Class<T> contextType, Supplier<T> contextSupplier) {
        T existing = load(machineId, contextType);
        if (existing != null) {
            return existing;
        }
        
        T newContext = contextSupplier.get();
        save(machineId, newContext);
        return newContext;
    }
}