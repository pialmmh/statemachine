package com.telcobright.statemachine.persistence;

import com.telcobright.statemachine.StateMachineContextEntity;
import com.telcobright.db.PartitionedRepository;
import com.telcobright.db.entity.ShardingEntity;

import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * Persistence provider implementation using PartitionedRepository
 * Supports automatic table partitioning based on date/time or custom sharding keys
 * 
 * @param <T> The entity type that extends both StateMachineContextEntity and ShardingEntity
 */
public class PartitionedRepositoryPersistenceProvider<T extends StateMachineContextEntity<?> & ShardingEntity<?>> 
        implements PersistenceProvider<T> {
    
    private final PartitionedRepository<T, String> partitionedRepository;
    private final Class<T> entityClass;
    private final String entityName;
    
    /**
     * Constructor with partitioned repository
     * 
     * @param partitionedRepository The partitioned repository instance
     * @param entityClass The entity class for type safety
     */
    public PartitionedRepositoryPersistenceProvider(
            PartitionedRepository<T, String> partitionedRepository, 
            Class<T> entityClass) {
        this.partitionedRepository = partitionedRepository;
        this.entityClass = entityClass;
        this.entityName = entityClass.getSimpleName();
        System.out.println("[PartitionedPersistence] Initialized for entity: " + entityName);
    }
    
    /**
     * Initialize the persistence provider
     * For partitioned repository, this is typically handled automatically
     */
    @Override
    public void initialize() {
        System.out.println("[PartitionedPersistence] Provider initialized for " + entityName);
        System.out.println("[PartitionedPersistence] Tables will be created automatically on first insert");
    }
    
    /**
     * Save or update a state machine context using partitioned storage
     * 
     * @param machineId The unique identifier of the state machine
     * @param context The context to persist
     */
    @Override
    public void save(String machineId, T context) {
        try {
            // Check if entity already exists
            T existing = partitionedRepository.findById(machineId);
            
            if (existing != null) {
                // Update existing entity
                // For partitioned repo, we need to provide date range for optimization
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime oneDayAgo = now.minusDays(1);
                partitionedRepository.updateByIdAndDateRange(machineId, context, oneDayAgo, now);
                System.out.println("[PartitionedPersistence] Updated context for machine: " + machineId + 
                    " (state: " + context.getCurrentState() + ", complete: " + context.isComplete() + ")");
            } else {
                // Insert new entity - partitioned repo will auto-create tables as needed
                partitionedRepository.insert(context);
                System.out.println("[PartitionedPersistence] Saved new context for machine: " + machineId + 
                    " (state: " + context.getCurrentState() + ", complete: " + context.isComplete() + ")");
            }
        } catch (Exception e) {
            System.err.println("[PartitionedPersistence] Failed to save context for machine " + machineId + ": " + e.getMessage());
            throw new RuntimeException("Failed to save context for machine: " + machineId, e);
        }
    }
    
    /**
     * Load a state machine context from partitioned storage
     * 
     * @param machineId The unique identifier of the state machine
     * @param contextType The class type of the context (must match T)
     * @return The loaded context, or null if not found
     */
    @Override
    public T load(String machineId, Class<T> contextType) {
        try {
            if (contextType != null && !contextType.equals(entityClass)) {
                System.err.println("[PartitionedPersistence] Context type mismatch. Expected: " + 
                    entityClass.getSimpleName() + ", Got: " + contextType.getSimpleName());
                return null;
            }
            
            // Search across all partitioned tables
            T context = partitionedRepository.findById(machineId);
            
            if (context != null) {
                System.out.println("[PartitionedPersistence] Loaded context for machine: " + machineId + 
                    " (state: " + context.getCurrentState() + ", complete: " + context.isComplete() + ")");
            } else {
                System.out.println("[PartitionedPersistence] No context found for machine: " + machineId);
            }
            
            return context;
        } catch (Exception e) {
            System.err.println("[PartitionedPersistence] Failed to load context for machine " + machineId + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Check if a state machine exists in partitioned storage
     * 
     * @param machineId The unique identifier of the state machine
     * @return true if the machine exists, false otherwise
     */
    @Override
    public boolean exists(String machineId) {
        try {
            T context = partitionedRepository.findById(machineId);
            boolean exists = context != null;
            System.out.println("[PartitionedPersistence] Machine " + machineId + " exists: " + exists);
            return exists;
        } catch (Exception e) {
            System.err.println("[PartitionedPersistence] Failed to check existence for machine " + machineId + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Delete a state machine from partitioned storage
     * Note: PartitionedRepository interface doesn't include delete method in the stub
     * This is a placeholder for when the actual implementation is available
     * 
     * @param machineId The unique identifier of the state machine
     */
    @Override
    public void delete(String machineId) {
        System.out.println("[PartitionedPersistence] Delete operation not implemented in PartitionedRepository stub");
        System.out.println("[PartitionedPersistence] Would delete machine: " + machineId);
        // TODO: Implement when PartitionedRepository includes delete method
    }
    
    /**
     * Load a context and check if it's complete using partitioned storage
     * 
     * @param machineId The unique identifier of the state machine
     * @return true if the machine exists and is complete, false otherwise
     */
    @Override
    public boolean isComplete(String machineId) {
        try {
            T context = load(machineId, entityClass);
            boolean complete = context != null && context.isComplete();
            System.out.println("[PartitionedPersistence] Machine " + machineId + " is complete: " + complete);
            return complete;
        } catch (Exception e) {
            System.err.println("[PartitionedPersistence] Failed to check completion for machine " + machineId + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Load context with date range optimization
     * Uses partitioned repository's optimized search when you know the approximate time range
     * 
     * @param machineId The machine ID
     * @param startDate Start of the search range
     * @param endDate End of the search range
     * @return The loaded context or null
     */
    public T loadWithDateRange(String machineId, LocalDateTime startDate, LocalDateTime endDate) {
        try {
            T context = partitionedRepository.findByIdAndDateRange(startDate, endDate);
            
            if (context != null) {
                System.out.println("[PartitionedPersistence] Loaded context for machine " + machineId + 
                    " within date range " + startDate + " to " + endDate + 
                    " (state: " + context.getCurrentState() + ")");
            } else {
                System.out.println("[PartitionedPersistence] No context found for machine " + machineId + 
                    " within date range " + startDate + " to " + endDate);
            }
            
            return context;
        } catch (Exception e) {
            System.err.println("[PartitionedPersistence] Failed to load context with date range for machine " + 
                machineId + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Get the entity class this provider handles
     */
    public Class<T> getEntityClass() {
        return entityClass;
    }
    
    /**
     * Get the underlying partitioned repository
     */
    public PartitionedRepository<T, String> getPartitionedRepository() {
        return partitionedRepository;
    }
}