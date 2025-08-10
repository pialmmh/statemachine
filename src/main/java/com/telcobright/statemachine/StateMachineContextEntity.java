package com.telcobright.statemachine;

import java.time.LocalDateTime;

/**
 * Interface for state machine context entities that combines persistence and completion capabilities.
 * Provides database persistence capabilities and adds completion tracking functionality.
 * 
 * When an entity is marked as complete, the state machine has reached its final state
 * and will not be rehydrated from the repository for further processing.
 * 
 * This optimization prevents unnecessary loading of completed state machines,
 * improving performance in high-volume scenarios.
 * 
 * @param <TKey> The type of the entity's primary key
 */
public interface StateMachineContextEntity<TKey> {
    
    /**
     * Check if this entity/state machine has completed its lifecycle.
     * 
     * @return true if the state machine has reached a final state and is complete
     */
    boolean isComplete();
    
    /**
     * Mark this entity/state machine as complete.
     * This should be called when the state machine reaches a final state.
     * 
     * @param complete true to mark as complete, false to mark as incomplete
     */
    void setComplete(boolean complete);
    
    /**
     * Convenience method to mark as complete.
     * Equivalent to setComplete(true).
     */
    default void markComplete() {
        setComplete(true);
    }
    
    /**
     * Convenience method to check if not complete.
     * 
     * @return true if the state machine is still active (not complete)
     */
    default boolean isActive() {
        return !isComplete();
    }
    
    /**
     * Get the current state of the state machine.
     * This field is persisted and updated after each state transition.
     * 
     * @return the current state name
     */
    String getCurrentState();
    
    /**
     * Set the current state of the state machine.
     * This should be updated automatically by the state machine after each transition.
     * 
     * @param state the new state name
     */
    void setCurrentState(String state);
    
    /**
     * Get the timestamp of the last state change.
     * This field is persisted and updated after each state transition.
     * 
     * @return the datetime when the state was last changed
     */
    LocalDateTime getLastStateChange();
    
    /**
     * Set the timestamp of the last state change.
     * This should be updated automatically by the state machine after each transition.
     * 
     * @param lastStateChange the datetime when the state was changed
     */
    void setLastStateChange(LocalDateTime lastStateChange);
}