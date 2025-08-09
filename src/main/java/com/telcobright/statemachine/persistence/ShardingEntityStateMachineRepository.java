package com.telcobright.statemachine.persistence;

import com.telcobright.db.PartitionedRepository;
import com.telcobright.db.entity.ShardingEntity;
import com.telcobright.db.entity.Id;
import com.telcobright.db.entity.ShardingKey;
import com.telcobright.idkit.IdGenerator;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * Generic repository for persisting ShardingEntity-based state machines
 * 
 * @param <TPersistingEntity> The ShardingEntity type that contains state machine data
 * @param <K> The primary key type of the entity
 */
public class ShardingEntityStateMachineRepository<TPersistingEntity extends ShardingEntity<K>, K> {
    
    private final PartitionedRepository<TPersistingEntity, K> repository;
    private final IdLookUpMode lookupMode;
    private final Class<TPersistingEntity> entityClass;
    
    /**
     * Constructor with lookup mode configuration
     * 
     * @param repository The partitioned repository instance
     * @param lookupMode The lookup mode (ById or ByIdAndDateRange)
     * @param entityClass The entity class for reflection operations
     */
    public ShardingEntityStateMachineRepository(
            PartitionedRepository<TPersistingEntity, K> repository, 
            IdLookUpMode lookupMode,
            Class<TPersistingEntity> entityClass) {
        this.repository = repository;
        this.lookupMode = lookupMode;
        this.entityClass = entityClass;
    }
    
    /**
     * Save entity asynchronously
     */
    public void saveAsync(TPersistingEntity entity) {
        CompletableFuture.runAsync(() -> saveEntity(entity));
    }
    
    /**
     * Find entity by machine ID (which is the entity's primary key)
     */
    public TPersistingEntity findByMachineId(K machineId) {
        try {
            switch (lookupMode) {
                case ById:
                    return repository.findById(machineId);
                    
                case ByIdAndDateRange:
                    return loadByIdAndDateRange(machineId);
                    
                default:
                    throw new IllegalStateException("Unsupported lookup mode: " + lookupMode);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load entity for machine ID: " + machineId, e);
        }
    }
    
    /**
     * Internal method to save entity based on lookup mode
     */
    private void saveEntity(TPersistingEntity entity) {
        try {
            K machineId = extractIdFromEntity(entity);
            TPersistingEntity existing = findByMachineId(machineId);
            
            if (existing != null) {
                updateEntity(entity);
            } else {
                repository.insert(entity);
            }
            
            System.out.println("Saved entity for machine " + machineId + " in state " + getCurrentStateFromEntity(entity));
        } catch (Exception e) {
            K machineId = extractIdFromEntity(entity);
            throw new RuntimeException("Failed to save entity for machine ID: " + machineId, e);
        }
    }
    
    /**
     * Load entity by ID and date range for efficient partitioned lookup
     */
    private TPersistingEntity loadByIdAndDateRange(K machineId) {
        try {
            // For ByIdAndDateRange, we assume the ID contains timestamp information
            // Extract timestamp from the ID (assuming Long type with embedded timestamp)
            if (machineId instanceof Long) {
                long longId = (Long) machineId;
                LocalDateTime timestamp = IdGenerator.extractTimestampLocal(longId);
                
                // Create a date range around the timestamp (±1 day for safety)
                LocalDateTime startDate = timestamp.minusDays(1);
                LocalDateTime endDate = timestamp.plusDays(1);
                
                // Use date range lookup for efficient partitioned search
                return repository.findByIdAndDateRange(startDate, endDate);
            } else if (machineId instanceof String) {
                // Try to parse as long if it's a string representation
                try {
                    long longId = Long.parseLong(machineId.toString());
                    LocalDateTime timestamp = IdGenerator.extractTimestampLocal(longId);
                    
                    LocalDateTime startDate = timestamp.minusDays(1);
                    LocalDateTime endDate = timestamp.plusDays(1);
                    
                    return repository.findByIdAndDateRange(startDate, endDate);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Machine ID must be a valid long for ByIdAndDateRange mode: " + machineId, e);
                }
            } else {
                throw new IllegalArgumentException("ByIdAndDateRange mode requires Long or String (parseable as Long) machine ID, got: " + machineId.getClass().getSimpleName());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load entity by ID and date range for machine ID: " + machineId, e);
        }
    }
    
    /**
     * Update existing entity based on lookup mode
     */
    private void updateEntity(TPersistingEntity entity) {
        K machineId = extractIdFromEntity(entity);
        
        switch (lookupMode) {
            case ById:
                repository.updateById(machineId, entity);
                break;
                
            case ByIdAndDateRange:
                updateByIdAndDateRange(entity);
                break;
                
            default:
                throw new IllegalStateException("Unsupported lookup mode: " + lookupMode);
        }
    }
    
    /**
     * Update entity by ID and date range
     */
    private void updateByIdAndDateRange(TPersistingEntity entity) {
        try {
            K machineId = extractIdFromEntity(entity);
            
            if (machineId instanceof Long) {
                long longId = (Long) machineId;
                LocalDateTime timestamp = IdGenerator.extractTimestampLocal(longId);
                
                LocalDateTime startDate = timestamp.minusDays(1);
                LocalDateTime endDate = timestamp.plusDays(1);
                
                repository.updateByIdAndDateRange(machineId, entity, startDate, endDate);
            } else if (machineId instanceof String) {
                long longId = Long.parseLong(machineId.toString());
                LocalDateTime timestamp = IdGenerator.extractTimestampLocal(longId);
                
                LocalDateTime startDate = timestamp.minusDays(1);
                LocalDateTime endDate = timestamp.plusDays(1);
                
                repository.updateByIdAndDateRange(machineId, entity, startDate, endDate);
            } else {
                throw new IllegalArgumentException("ByIdAndDateRange mode requires Long or String machine ID, got: " + machineId.getClass().getSimpleName());
            }
        } catch (Exception e) {
            K machineId = extractIdFromEntity(entity);
            throw new RuntimeException("Failed to update entity by ID and date range for machine ID: " + machineId, e);
        }
    }
    
    /**
     * Extract the ID from the entity using reflection to find @Id annotated field
     */
    @SuppressWarnings("unchecked")
    private K extractIdFromEntity(TPersistingEntity entity) {
        try {
            Field[] fields = entityClass.getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(Id.class)) {
                    field.setAccessible(true);
                    return (K) field.get(entity);
                }
            }
            throw new IllegalStateException("No @Id annotated field found in entity class: " + entityClass.getSimpleName());
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract ID from entity", e);
        }
    }
    
    /**
     * Extract current state from entity (assumes a field named 'currentState' or similar)
     * This is a helper method for logging - can be customized based on entity structure
     */
    private String getCurrentStateFromEntity(TPersistingEntity entity) {
        try {
            Field[] fields = entityClass.getDeclaredFields();
            for (Field field : fields) {
                if (field.getName().toLowerCase().contains("state") || 
                    field.getName().toLowerCase().contains("status")) {
                    field.setAccessible(true);
                    Object value = field.get(entity);
                    return value != null ? value.toString() : "UNKNOWN";
                }
            }
            return "UNKNOWN";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
    
    /**
     * Get the configured lookup mode
     */
    public IdLookUpMode getLookupMode() {
        return lookupMode;
    }
}