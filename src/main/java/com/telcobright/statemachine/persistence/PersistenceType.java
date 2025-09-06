package com.telcobright.statemachine.persistence;

/**
 * Enum to specify the type of persistence provider to use
 */
public enum PersistenceType {
    
    /**
     * Direct MySQL persistence - stores data directly in MySQL tables
     * Simple and straightforward, good for small to medium scale
     */
    MYSQL_DIRECT("Direct MySQL Persistence"),
    
    /**
     * Optimized MySQL persistence with batching and caching
     * Better performance for high-throughput scenarios
     */
    MYSQL_OPTIMIZED("Optimized MySQL Persistence"),
    
    /**
     * Partitioned repository - uses partitioned tables for better scalability
     * Automatically creates partitions based on time or sharding key
     * Best for very high volume and long-term data retention
     */
    PARTITIONED_REPO("Partitioned Repository"),
    
    /**
     * No persistence - in-memory only
     * Useful for testing or when persistence is not needed
     */
    NONE("No Persistence");
    
    private final String description;
    
    PersistenceType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return name() + " (" + description + ")";
    }
}