package com.telcobright.statemachine.persistence;

import com.telcobright.statemachine.StateMachineContextEntity;
import java.time.LocalDateTime;

/**
 * Base class for state machine persistent entities.
 * Provides automatic table name inference from class name.
 * Subclasses can override getTableName() to provide custom table names.
 * 
 * @param <TKey> The type of the entity's primary key
 */
public abstract class BaseStateMachineEntity<TKey> implements StateMachineContextEntity<TKey> {
    
    // Core state machine fields
    private String currentState;
    private LocalDateTime lastStateChange;
    private boolean isComplete;
    
    /**
     * Get the table name for this entity.
     * Default implementation converts class name from CamelCase to snake_case.
     * Override this method to provide a custom table name.
     * 
     * Example: CallPersistentContext -> call_persistent_context
     * 
     * @return the database table name
     */
    public String getTableName() {
        String className = this.getClass().getSimpleName();
        // Convert CamelCase to snake_case
        return className.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    }
    
    /**
     * Get the machine ID field name.
     * Override if your entity uses a different field name for the machine ID.
     * 
     * @return the field name for machine ID (default: "machine_id")
     */
    public String getMachineIdFieldName() {
        return "machine_id";
    }
    
    /**
     * Get the machine ID value for this entity.
     * Must be implemented by subclasses to return the actual ID.
     * 
     * @return the machine ID value
     */
    public abstract TKey getMachineId();
    
    /**
     * Set the machine ID value for this entity.
     * Must be implemented by subclasses to set the actual ID.
     * 
     * @param machineId the machine ID to set
     */
    public abstract void setMachineId(TKey machineId);
    
    @Override
    public String getCurrentState() {
        return currentState;
    }
    
    @Override
    public void setCurrentState(String state) {
        this.currentState = state;
    }
    
    @Override
    public LocalDateTime getLastStateChange() {
        return lastStateChange;
    }
    
    @Override
    public void setLastStateChange(LocalDateTime lastStateChange) {
        this.lastStateChange = lastStateChange;
    }
    
    @Override
    public boolean isComplete() {
        return isComplete;
    }
    
    @Override
    public void setComplete(boolean complete) {
        this.isComplete = complete;
    }
    
    /**
     * Create a deep copy of this entity.
     * Subclasses should override this to copy their specific fields.
     * 
     * @return a deep copy of this entity
     */
    @Override
    public abstract StateMachineContextEntity<TKey> deepCopy();
}