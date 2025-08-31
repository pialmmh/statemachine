package com.telcobright.statemachine.db;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Stub interface for PartitionedRepository from telcobright-partitioned-repo
 * This is a placeholder until the actual dependency is available
 */
public interface PartitionedRepository<TEntity, TKey> {
    
    /**
     * Insert single entity with automatic SQL generation (auto-creates tables)
     */
    void insert(TEntity entity);
    
    /**
     * Insert multiple entities with batch processing and automatic table creation
     */
    void insertMultiple(List<TEntity> entities);
    
    /**
     * Find entity by primary key (searches across all tables)
     */
    TEntity findById(TKey id);
    
    /**
     * Find first entity within the specified date range
     */
    TEntity findByIdAndDateRange(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Update entity by primary key (finds the correct table automatically)
     */
    void updateById(TKey id, TEntity entity);
    
    /**
     * Update entity by primary key within a specific date range (optimizes table search)
     */
    void updateByIdAndDateRange(TKey id, TEntity entity, LocalDateTime startDate, LocalDateTime endDate);
}