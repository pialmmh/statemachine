package com.telcobright.statemachine;

import com.telcobright.db.entity.ShardingEntity;

/**
 * Interface for state machine context entities that combines persistence and completion capabilities.
 * Extends ShardingEntity for database persistence and adds completion tracking functionality.
 * 
 * When an entity is marked as complete, the state machine has reached its final state
 * and will not be rehydrated from the repository for further processing.
 * 
 * This optimization prevents unnecessary loading of completed state machines,
 * improving performance in high-volume scenarios.
 * 
 * @param <TKey> The type of the entity's primary key
 */
public interface StateMachineContextEntity<TKey> extends ShardingEntity<TKey> {
    
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
}