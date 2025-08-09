package com.telcobright.statemachine.persistence;

/**
 * Defines lookup modes for state machine persistence
 */
public enum IdLookUpMode {
    /**
     * Look up by ID only - uses simple findById and updateById operations
     */
    ById,
    
    /**
     * Look up by ID and date range - uses date-based partitioning for efficient lookup
     * Requires ID to be of long type where timestamp can be extracted
     */
    ByIdAndDateRange
}