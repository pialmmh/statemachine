package com.telcobright.statemachine.persistence;

import com.telcobright.statemachine.StateMachineContextEntity;
import com.telcobright.statemachine.db.PartitionedRepository;
import com.telcobright.statemachine.db.entity.ShardingEntity;

/**
 * Factory for creating PartitionedRepositoryPersistenceProvider instances
 * Provides convenience methods for different configuration scenarios
 */
public class PartitionedRepositoryPersistenceProviderFactory {
    
    /**
     * Create a partitioned repository persistence provider
     * 
     * @param partitionedRepository The underlying partitioned repository
     * @param entityClass The entity class that implements both interfaces
     * @return A configured persistence provider
     */
    public static <T extends StateMachineContextEntity<?> & ShardingEntity<?>> 
            PartitionedRepositoryPersistenceProvider<T> create(
                PartitionedRepository<T, String> partitionedRepository,
                Class<T> entityClass) {
        
        return new PartitionedRepositoryPersistenceProvider<>(partitionedRepository, entityClass);
    }
    
    /**
     * Create a partitioned repository persistence provider with validation
     * Validates that the entity class implements both required interfaces
     * 
     * @param partitionedRepository The underlying partitioned repository
     * @param entityClass The entity class to validate and use
     * @return A configured persistence provider
     * @throws IllegalArgumentException if entity class doesn't implement required interfaces
     */
    public static <T extends StateMachineContextEntity<?> & ShardingEntity<?>> 
            PartitionedRepositoryPersistenceProvider<T> createWithValidation(
                PartitionedRepository<T, String> partitionedRepository,
                Class<T> entityClass) {
        
        validateEntityClass(entityClass);
        return create(partitionedRepository, entityClass);
    }
    
    /**
     * Validate that an entity class implements both required interfaces
     * 
     * @param entityClass The class to validate
     * @throws IllegalArgumentException if validation fails
     */
    public static void validateEntityClass(Class<?> entityClass) {
        if (!StateMachineContextEntity.class.isAssignableFrom(entityClass)) {
            throw new IllegalArgumentException("Entity class " + entityClass.getSimpleName() + 
                " must implement StateMachineContextEntity interface");
        }
        
        if (!ShardingEntity.class.isAssignableFrom(entityClass)) {
            throw new IllegalArgumentException("Entity class " + entityClass.getSimpleName() + 
                " must implement ShardingEntity interface for partitioned storage");
        }
        
        System.out.println("[PartitionedFactory] Validated entity class: " + entityClass.getSimpleName());
    }
    
    /**
     * Create a mock partitioned repository for testing purposes
     * This returns a stub implementation that logs operations without actual persistence
     * 
     * @param entityClass The entity class
     * @return A mock persistence provider for testing
     */
    public static <T extends StateMachineContextEntity<?> & ShardingEntity<?>> 
            PartitionedRepositoryPersistenceProvider<T> createMockProvider(Class<T> entityClass) {
        
        PartitionedRepository<T, String> mockRepo = new MockPartitionedRepository<>();
        return create(mockRepo, entityClass);
    }
    
    /**
     * Mock implementation of PartitionedRepository for testing/demonstration
     */
    private static class MockPartitionedRepository<T, K> implements PartitionedRepository<T, K> {
        
        @Override
        public void insert(T entity) {
            System.out.println("[MockPartitionedRepo] INSERT: " + entity);
        }
        
        @Override
        public void insertMultiple(java.util.List<T> entities) {
            System.out.println("[MockPartitionedRepo] INSERT_MULTIPLE: " + entities.size() + " entities");
        }
        
        @Override
        public T findById(K id) {
            System.out.println("[MockPartitionedRepo] FIND_BY_ID: " + id);
            return null; // Mock returns null (not found)
        }
        
        @Override
        public T findByIdAndDateRange(java.time.LocalDateTime startDate, java.time.LocalDateTime endDate) {
            System.out.println("[MockPartitionedRepo] FIND_BY_DATE_RANGE: " + startDate + " to " + endDate);
            return null; // Mock returns null (not found)
        }
        
        @Override
        public void updateById(K id, T entity) {
            System.out.println("[MockPartitionedRepo] UPDATE_BY_ID: " + id + " -> " + entity);
        }
        
        @Override
        public void updateByIdAndDateRange(K id, T entity, java.time.LocalDateTime startDate, java.time.LocalDateTime endDate) {
            System.out.println("[MockPartitionedRepo] UPDATE_BY_DATE_RANGE: " + id + " -> " + entity + 
                " (range: " + startDate + " to " + endDate + ")");
        }
    }
}