package com.telcobright.statemachine.persistence;

import com.telcobright.statemachine.StateMachineContextEntity;
import com.telcobright.statemachine.db.PartitionedRepository;
import com.telcobright.statemachine.db.entity.ShardingEntity;
import javax.sql.DataSource;

/**
 * Factory class to create appropriate persistence provider based on configuration
 */
public class PersistenceProviderFactory {
    
    /**
     * Create a persistence provider based on the specified type
     * 
     * @param type The type of persistence provider to create
     * @param connectionProvider MySQL connection provider (required for MySQL types)
     * @return The created persistence provider
     */
    @SuppressWarnings("unchecked")
    public static <T extends StateMachineContextEntity<?>> PersistenceProvider<T> create(
            PersistenceType type,
            MysqlConnectionProvider connectionProvider) {
        
        switch (type) {
            case MYSQL_DIRECT:
                if (connectionProvider == null) {
                    throw new IllegalArgumentException("MySQL connection provider is required for MYSQL_DIRECT");
                }
                return new MySQLPersistenceProvider<>(connectionProvider);
                
            case MYSQL_OPTIMIZED:
                if (connectionProvider == null) {
                    throw new IllegalArgumentException("MySQL connection provider is required for MYSQL_OPTIMIZED");
                }
                // For now, return regular MySQL provider as optimized version needs more setup
                return new MySQLPersistenceProvider<>(connectionProvider);
                
            case PARTITIONED_REPO:
                // For partitioned repo, we need more setup
                // This is a placeholder - actual implementation would need proper configuration
                throw new UnsupportedOperationException(
                    "PARTITIONED_REPO requires additional configuration. Use createPartitionedProvider() method.");
                
            case NONE:
                // Return a no-op provider
                return new NoOpPersistenceProvider<>();
                
            default:
                throw new IllegalArgumentException("Unknown persistence type: " + type);
        }
    }
    
    /**
     * Create a partitioned repository persistence provider
     * Requires entities that implement both StateMachineContextEntity and ShardingEntity
     * 
     * @param partitionedRepository The configured partitioned repository
     * @param entityClass The entity class
     * @return The partitioned persistence provider
     */
    public static <T extends StateMachineContextEntity<?> & ShardingEntity<?>> 
            PersistenceProvider<T> createPartitionedProvider(
                PartitionedRepository<T, String> partitionedRepository,
                Class<T> entityClass) {
        
        return new PartitionedRepositoryPersistenceProvider<>(partitionedRepository, entityClass);
    }
    
    /**
     * Create a partitioned provider with automatic repository setup
     * 
     * @param dataSource The data source for database connections
     * @param entityClass The entity class
     * @param tableName The base table name for partitions
     * @param partitionStrategy The partitioning strategy (e.g., "MONTHLY", "DAILY", "HASH")
     * @return The configured partitioned persistence provider
     */
    public static <T extends StateMachineContextEntity<?> & ShardingEntity<?>> 
            PersistenceProvider<T> createAutoPartitionedProvider(
                DataSource dataSource,
                Class<T> entityClass,
                String tableName,
                String partitionStrategy) {
        
        // Create the partitioned repository with the specified strategy
        PartitionedRepository<T, String> repository = createPartitionedRepository(
            dataSource, 
            entityClass, 
            tableName, 
            partitionStrategy
        );
        
        return new PartitionedRepositoryPersistenceProvider<>(repository, entityClass);
    }
    
    /**
     * Helper method to create a configured PartitionedRepository
     * This would typically involve more complex configuration based on your requirements
     */
    private static <T extends StateMachineContextEntity<?> & ShardingEntity<?>> 
            PartitionedRepository<T, String> createPartitionedRepository(
                DataSource dataSource,
                Class<T> entityClass,
                String tableName,
                String partitionStrategy) {
        
        // This is a simplified example - actual implementation would need proper configuration
        // based on your PartitionedRepository implementation
        
        // This would need to return an actual implementation
        // For now, throwing exception as the interface cannot be instantiated
        throw new UnsupportedOperationException(
            "PartitionedRepository needs concrete implementation. " +
            "Use MockPartitionedRepository for testing.");
    }
    
    /**
     * No-operation persistence provider for in-memory only operation
     */
    private static class NoOpPersistenceProvider<T extends StateMachineContextEntity<?>> 
            implements PersistenceProvider<T> {
        
        @Override
        public void initialize() {
            // No-op
        }
        
        @Override
        public void save(String machineId, T context) {
            // No-op - don't persist anything
        }
        
        @Override
        public T load(String machineId, Class<T> contextType) {
            // Always return null - nothing persisted
            return null;
        }
        
        @Override
        public boolean exists(String machineId) {
            // Always return false - nothing persisted
            return false;
        }
        
        @Override
        public void delete(String machineId) {
            // No-op
        }
    }
}