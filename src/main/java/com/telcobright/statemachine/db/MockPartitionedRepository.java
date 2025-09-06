package com.telcobright.statemachine.db;

import com.telcobright.statemachine.db.entity.ShardingEntity;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock implementation of PartitionedRepository for testing
 * Simulates partitioned storage using in-memory maps
 */
public class MockPartitionedRepository<TEntity, TKey> 
        implements PartitionedRepository<TEntity, TKey> {
    
    // Simulated partitioned storage - partition key -> (entity id -> entity)
    private final Map<String, Map<TKey, TEntity>> partitions = new ConcurrentHashMap<>();
    private final String tableName;
    private long insertCount = 0;
    private long updateCount = 0;
    private long findCount = 0;
    
    public MockPartitionedRepository(String tableName) {
        this.tableName = tableName;
    }
    
    @Override
    public void insert(TEntity entity) {
        // Try to get sharding key via reflection
        String partitionKey = "default";
        try {
            // Try getShardingKey method if it exists
            partitionKey = (String) entity.getClass().getMethod("getShardingKey").invoke(entity);
        } catch (Exception e) {
            // Try getPartitionId if getShardingKey doesn't exist
            try {
                Integer partitionId = (Integer) entity.getClass().getMethod("getPartitionId").invoke(entity);
                partitionKey = "p" + partitionId;
            } catch (Exception e2) {
                // Use default partition
            }
        }
        
        partitions.computeIfAbsent(partitionKey, k -> new ConcurrentHashMap<>());
        
        // Extract ID from entity (assuming it has getId() method)
        try {
            TKey id = (TKey) entity.getClass().getMethod("getId").invoke(entity);
            partitions.get(partitionKey).put(id, entity);
            insertCount++;
            
            if (insertCount % 100000 == 0) {
                System.out.println("[MockPartitionedRepo] Inserted " + insertCount + 
                    " entities across " + partitions.size() + " partitions");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert entity", e);
        }
    }
    
    @Override
    public void insertMultiple(List<TEntity> entities) {
        for (TEntity entity : entities) {
            insert(entity);
        }
    }
    
    @Override
    public TEntity findById(TKey id) {
        findCount++;
        // Search across all partitions
        for (Map<TKey, TEntity> partition : partitions.values()) {
            TEntity entity = partition.get(id);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }
    
    @Override
    public TEntity findByIdAndDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        // Not implemented for mock
        return null;
    }
    
    @Override
    public void updateById(TKey id, TEntity entity) {
        // Try to get sharding key via reflection
        String partitionKey = "default";
        try {
            partitionKey = (String) entity.getClass().getMethod("getShardingKey").invoke(entity);
        } catch (Exception e) {
            try {
                Integer partitionId = (Integer) entity.getClass().getMethod("getPartitionId").invoke(entity);
                partitionKey = "p" + partitionId;
            } catch (Exception e2) {
                // Use default partition
            }
        }
        
        Map<TKey, TEntity> partition = partitions.get(partitionKey);
        if (partition != null) {
            partition.put(id, entity);
            updateCount++;
        }
    }
    
    @Override
    public void updateByIdAndDateRange(TKey id, TEntity entity, LocalDateTime startDate, LocalDateTime endDate) {
        updateById(id, entity);
    }
    
    // Statistics methods
    public void printStatistics() {
        System.out.println("\n=== MockPartitionedRepository Statistics ===");
        System.out.println("Table: " + tableName);
        System.out.println("Total Partitions: " + partitions.size());
        System.out.println("Total Inserts: " + insertCount);
        System.out.println("Total Updates: " + updateCount);
        System.out.println("Total Finds: " + findCount);
        
        // Show partition distribution
        System.out.println("\nPartition Distribution:");
        int count = 0;
        for (Map.Entry<String, Map<TKey, TEntity>> entry : partitions.entrySet()) {
            System.out.println("  Partition '" + entry.getKey() + "': " + 
                entry.getValue().size() + " entities");
            if (++count >= 10) {
                System.out.println("  ... and " + (partitions.size() - 10) + " more partitions");
                break;
            }
        }
        System.out.println("============================================\n");
    }
    
    public void clearStatistics() {
        insertCount = 0;
        updateCount = 0;
        findCount = 0;
    }
}