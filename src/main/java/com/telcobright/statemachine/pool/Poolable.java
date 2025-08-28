package com.telcobright.statemachine.pool;

/**
 * Interface for objects that can be pooled and reused
 * Implementations must ensure thread-safety if used concurrently
 */
public interface Poolable {
    
    /**
     * Reset the object state for reuse from pool
     * This method is called before returning object to pool
     * Implementation should clear all mutable state
     */
    void resetForReuse();
    
    /**
     * Check if object is in a valid state for pooling
     * @return true if object can be safely reused
     */
    default boolean isReusable() {
        return true;
    }
}