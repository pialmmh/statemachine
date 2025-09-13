package com.telcobright.statemachine.persistence;

import com.telcobright.statemachine.StateMachineContextEntity;
import com.telcobright.core.repository.SplitVerseRepository;
import com.telcobright.splitverse.config.ShardConfig;
import com.telcobright.core.partition.PartitionType;
import com.telcobright.core.entity.ShardingEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Split-Verse based persistence provider for StateMachine
 * Uses the new split-verse library for partitioned database storage
 */
public class SplitVersePersistenceProvider<T extends StateMachineContextEntity<?> & ShardingEntity>
        implements PersistenceProvider<T> {

    private static final Logger logger = Logger.getLogger(SplitVersePersistenceProvider.class.getName());

    private final SplitVerseRepository<T> repository;
    private final Class<T> entityClass;
    private final String entityName;

    /**
     * Create a new SplitVersePersistenceProvider using builder pattern
     */
    public static <T extends StateMachineContextEntity<?> & ShardingEntity> Builder<T> builder() {
        return new Builder<>();
    }

    private SplitVersePersistenceProvider(Builder<T> builder) {
        this.entityClass = builder.entityClass;
        this.entityName = entityClass.getSimpleName();

        // Build the SplitVerseRepository
        this.repository = SplitVerseRepository.<T>builder()
            .withSingleShard(builder.shardConfig)
            .withEntityClass(builder.entityClass)
            .withPartitionType(builder.partitionType)
            .withPartitionKeyColumn(builder.partitionKeyColumn)
            .build();

        logger.info("Initialized SplitVersePersistenceProvider for " + entityName);
    }

    @Override
    public void initialize() {
        // Split-verse automatically initializes tables on first use
        logger.info("SplitVersePersistenceProvider initialized for " + entityName);
    }

    @Override
    public void save(String machineId, T context) {
        try {
            // Ensure entity has required fields
            // Since T extends both StateMachineContextEntity and ShardingEntity,
            // we can cast and use ShardingEntity methods
            if (context instanceof com.telcobright.core.entity.ShardingEntity) {
                ((com.telcobright.core.entity.ShardingEntity) context).setId(machineId);

                // Ensure created_at is set (required for partitioning)
                if (((com.telcobright.core.entity.ShardingEntity) context).getCreatedAt() == null) {
                    ((com.telcobright.core.entity.ShardingEntity) context).setCreatedAt(LocalDateTime.now());
                }
            }

            // Save using split-verse repository
            repository.insert(context);
            logger.fine("Saved " + entityName + " with ID: " + machineId);

        } catch (Exception e) {
            logger.severe("Failed to save " + entityName + ": " + e.getMessage());
            throw new RuntimeException("Failed to save entity", e);
        }
    }

    @Override
    public T load(String machineId, Class<T> contextType) {
        try {
            T entity = repository.findById(machineId);

            if (entity != null) {
                logger.fine("Loaded " + entityName + " with ID: " + machineId);
            } else {
                logger.fine(entityName + " not found with ID: " + machineId);
            }

            return entity;

        } catch (Exception e) {
            logger.severe("Failed to load " + entityName + " with ID " + machineId + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean exists(String machineId) {
        return load(machineId, entityClass) != null;
    }

    @Override
    public void delete(String machineId) {
        try {
            repository.deleteById(machineId);
            logger.fine("Deleted " + entityName + " with ID: " + machineId);

        } catch (Exception e) {
            logger.severe("Failed to delete " + entityName + " with ID " + machineId + ": " + e.getMessage());
            throw new RuntimeException("Failed to delete entity", e);
        }
    }

    /**
     * Load entities within a date range
     */
    public List<T> loadByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        try {
            return repository.findAllByDateRange(startDate, endDate);
        } catch (Exception e) {
            logger.severe("Failed to load entities by date range: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Load batch of entities using cursor-based pagination
     */
    public List<T> loadBatch(String lastId, int batchSize) {
        try {
            return repository.findBatchByIdGreaterThan(lastId, batchSize);
        } catch (Exception e) {
            logger.severe("Failed to load batch: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Builder for SplitVersePersistenceProvider
     */
    public static class Builder<T extends StateMachineContextEntity<?> & ShardingEntity> {
        private Class<T> entityClass;
        private ShardConfig shardConfig;
        private PartitionType partitionType = PartitionType.DATE_BASED;
        private String partitionKeyColumn = "created_at";

        public Builder<T> withEntityClass(Class<T> entityClass) {
            this.entityClass = entityClass;
            return this;
        }

        public Builder<T> withShardConfig(ShardConfig config) {
            this.shardConfig = config;
            return this;
        }

        public Builder<T> withDatabaseConfig(String host, int port, String database,
                                            String username, String password) {
            this.shardConfig = ShardConfig.builder()
                .shardId("primary")
                .host(host)
                .port(port)
                .database(database)
                .username(username)
                .password(password)
                .connectionPoolSize(10)
                .enabled(true)
                .build();
            return this;
        }

        public Builder<T> withPartitionType(PartitionType type) {
            this.partitionType = type;
            return this;
        }

        public Builder<T> withPartitionKeyColumn(String column) {
            this.partitionKeyColumn = column;
            return this;
        }

        public SplitVersePersistenceProvider<T> build() {
            if (entityClass == null) {
                throw new IllegalArgumentException("Entity class is required");
            }
            if (shardConfig == null) {
                throw new IllegalArgumentException("Shard configuration is required");
            }
            return new SplitVersePersistenceProvider<>(this);
        }
    }
}