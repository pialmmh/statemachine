package com.telcobright.statewalk.persistence;

import com.telcobright.core.repository.SplitVerseRepository;
import com.telcobright.splitverse.config.ShardConfig;
import com.telcobright.splitverse.config.RepositoryMode;
import com.telcobright.core.partition.PartitionType;
import com.telcobright.statemachine.db.entity.ShardingEntity;
import com.telcobright.statewalk.persistence.EntityGraphMapper.EntityMetadata;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter for persisting entity graphs using Split-Verse
 * Manages multiple Split-Verse repositories for different entity types
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class SplitVerseGraphAdapter {

    private final String databaseName;
    private final ShardConfig shardConfig;
    private final Map<Class<?>, SplitVerseRepository> repositories = new ConcurrentHashMap<>();
    private final Map<String, TableMetadata> tableMetadata = new ConcurrentHashMap<>();
    private final boolean initialized;

    /**
     * Table metadata for schema management
     */
    private static class TableMetadata {
        String tableName;
        Class<?> entityClass;
        List<ColumnInfo> columns = new ArrayList<>();
        String primaryKey;
        Map<String, String> foreignKeys = new HashMap<>();
    }

    /**
     * Column information
     */
    private static class ColumnInfo {
        String name;
        String type;
        boolean nullable;
        boolean unique;

        ColumnInfo(String name, String type, boolean nullable, boolean unique) {
            this.name = name;
            this.type = type;
            this.nullable = nullable;
            this.unique = unique;
        }
    }

    /**
     * Create adapter with registry-named database
     */
    public SplitVerseGraphAdapter(String registryName, ShardConfig config) {
        this.databaseName = registryName;

        // Ensure shard config uses registry name as database
        this.shardConfig = ShardConfig.builder()
            .shardId(config.getShardId())
            .host(config.getHost())
            .port(config.getPort())
            .database(registryName) // Force database name
            .username(config.getUsername())
            .password(config.getPassword())
            .connectionPoolSize(config.getConnectionPoolSize())
            .enabled(config.isEnabled())
            .build();

        this.initialized = initializeDatabase();
    }

    /**
     * Initialize database and verify it exists
     */
    private boolean initializeDatabase() {
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
            shardConfig.getHost(), shardConfig.getPort());

        try (Connection conn = DriverManager.getConnection(jdbcUrl,
                shardConfig.getUsername(), shardConfig.getPassword());
             Statement stmt = conn.createStatement()) {

            // Create database if not exists
            stmt.execute("CREATE DATABASE IF NOT EXISTS `" + databaseName + "` " +
                        "DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");

            System.out.println("[SplitVerseGraphAdapter] Initialized database: " + databaseName);
            return true;

        } catch (SQLException e) {
            System.err.println("[SplitVerseGraphAdapter] Failed to initialize database: " + e.getMessage());
            return false;
        }
    }

    /**
     * Create tables for all entities in the object graph
     */
    public void createTablesForGraph(EntityGraphMapper mapper) {
        if (!initialized) {
            throw new IllegalStateException("Database not initialized");
        }

        Collection<EntityMetadata> allMetadata = mapper.getAllMetadata();
        System.out.println("[SplitVerseGraphAdapter] Creating tables for " + allMetadata.size() + " entities");

        for (EntityMetadata metadata : allMetadata) {
            createTableForEntity(metadata);
            createRepositoryForEntity(metadata);
        }

        // Create relationship tables for many-to-many relationships
        createRelationshipTables(mapper);
    }

    /**
     * Create table for a single entity
     */
    private void createTableForEntity(EntityMetadata metadata) {
        String tableName = metadata.tableName;
        Class<?> entityClass = metadata.entityClass;

        TableMetadata tableInfo = analyzeEntityClass(entityClass, tableName);
        tableMetadata.put(tableName, tableInfo);

        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
            shardConfig.getHost(), shardConfig.getPort(), databaseName);

        try (Connection conn = DriverManager.getConnection(jdbcUrl,
                shardConfig.getUsername(), shardConfig.getPassword());
             Statement stmt = conn.createStatement()) {

            // Build CREATE TABLE statement
            StringBuilder createSql = new StringBuilder();
            createSql.append("CREATE TABLE IF NOT EXISTS `").append(tableName).append("` (\n");

            // Add ID column (required for Split-Verse)
            createSql.append("  `id` VARCHAR(255) NOT NULL,\n");

            // Add created_at column (required for Split-Verse partitioning)
            createSql.append("  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,\n");

            // Add columns based on entity fields
            for (ColumnInfo col : tableInfo.columns) {
                createSql.append("  `").append(col.name).append("` ").append(col.type);
                if (!col.nullable) {
                    createSql.append(" NOT NULL");
                }
                if (col.unique) {
                    createSql.append(" UNIQUE");
                }
                createSql.append(",\n");
            }

            // Add foreign keys
            for (Map.Entry<String, String> fk : tableInfo.foreignKeys.entrySet()) {
                createSql.append("  `").append(fk.getKey()).append("` VARCHAR(255),\n");
            }

            // Primary key
            createSql.append("  PRIMARY KEY (`id`, `created_at`),\n");

            // Indexes
            createSql.append("  INDEX idx_created_at (`created_at`)");

            // Add foreign key indexes
            for (String fkColumn : tableInfo.foreignKeys.keySet()) {
                createSql.append(",\n  INDEX idx_").append(fkColumn).append(" (`").append(fkColumn).append("`)");
            }

            createSql.append("\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            // Add partitioning for time-based data
            createSql.append("\nPARTITION BY RANGE (TO_DAYS(created_at)) (\n");
            createSql.append("  PARTITION p0 VALUES LESS THAN (TO_DAYS('2025-01-01')),\n");

            // Create monthly partitions for next 12 months
            LocalDateTime partitionDate = LocalDateTime.now().withDayOfMonth(1);
            for (int i = 1; i <= 12; i++) {
                partitionDate = partitionDate.plusMonths(1);
                String partitionName = "p" + partitionDate.getYear() + String.format("%02d", partitionDate.getMonthValue());
                String partitionBoundary = partitionDate.plusMonths(1).toLocalDate().toString();
                createSql.append("  PARTITION ").append(partitionName)
                        .append(" VALUES LESS THAN (TO_DAYS('").append(partitionBoundary).append("'))");
                if (i < 12) {
                    createSql.append(",");
                }
                createSql.append("\n");
            }
            createSql.append(")");

            // Execute table creation
            stmt.execute(createSql.toString());
            System.out.println("[SplitVerseGraphAdapter] Created/verified table: " + tableName);

        } catch (SQLException e) {
            System.err.println("[SplitVerseGraphAdapter] Failed to create table " + tableName + ": " + e.getMessage());
        }
    }

    /**
     * Analyze entity class to determine table structure
     */
    private TableMetadata analyzeEntityClass(Class<?> entityClass, String tableName) {
        TableMetadata metadata = new TableMetadata();
        metadata.tableName = tableName;
        metadata.entityClass = entityClass;
        metadata.primaryKey = "id";

        // Analyze fields using reflection (only at initialization)
        for (java.lang.reflect.Field field : entityClass.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) ||
                java.lang.reflect.Modifier.isTransient(field.getModifiers())) {
                continue;
            }

            String fieldName = field.getName();
            Class<?> fieldType = field.getType();

            // Skip complex types and collections (they go in separate tables)
            if (isSimpleType(fieldType)) {
                ColumnInfo col = new ColumnInfo(
                    toSnakeCase(fieldName),
                    getSqlType(fieldType),
                    true,
                    false
                );
                metadata.columns.add(col);
            }
        }

        return metadata;
    }

    /**
     * Check if type is simple (can be stored in a column)
     */
    private boolean isSimpleType(Class<?> type) {
        return type.isPrimitive() ||
               type == String.class ||
               type == Integer.class ||
               type == Long.class ||
               type == Double.class ||
               type == Float.class ||
               type == Boolean.class ||
               type == LocalDateTime.class ||
               type.isEnum();
    }

    /**
     * Get SQL type for Java type
     */
    private String getSqlType(Class<?> javaType) {
        if (javaType == String.class) return "VARCHAR(255)";
        if (javaType == Integer.class || javaType == int.class) return "INT";
        if (javaType == Long.class || javaType == long.class) return "BIGINT";
        if (javaType == Double.class || javaType == double.class) return "DOUBLE";
        if (javaType == Float.class || javaType == float.class) return "FLOAT";
        if (javaType == Boolean.class || javaType == boolean.class) return "BOOLEAN";
        if (javaType == LocalDateTime.class) return "TIMESTAMP";
        if (javaType.isEnum()) return "VARCHAR(50)";
        return "VARCHAR(255)";
    }

    /**
     * Convert camelCase to snake_case
     */
    private String toSnakeCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    }

    /**
     * Create Split-Verse repository for an entity
     */
    private void createRepositoryForEntity(EntityMetadata metadata) {
        if (!ShardingEntity.class.isAssignableFrom(metadata.entityClass)) {
            System.out.println("[SplitVerseGraphAdapter] Skipping repository creation for non-ShardingEntity: " +
                             metadata.entityClass.getSimpleName());
            return;
        }

        try {
            SplitVerseRepository repository = SplitVerseRepository.builder()
                .withSingleShard(shardConfig)
                .withEntityClass(metadata.entityClass)
                .withRepositoryMode(RepositoryMode.PARTITIONED)
                .withPartitionType(PartitionType.DATE_BASED)
                .withPartitionKeyColumn("created_at")
                .withRetentionDays(365)
                .build();

            repositories.put(metadata.entityClass, repository);
            System.out.println("[SplitVerseGraphAdapter] Created repository for: " + metadata.entityClass.getSimpleName());

        } catch (Exception e) {
            System.err.println("[SplitVerseGraphAdapter] Failed to create repository for " +
                             metadata.entityClass.getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Create relationship tables for many-to-many relationships
     */
    private void createRelationshipTables(EntityGraphMapper mapper) {
        // Implementation would create junction tables for many-to-many relationships
        // Simplified for brevity
    }

    /**
     * Persist an entity to the appropriate table
     */
    public void persistEntity(Class<?> entityClass, Object entity) {
        SplitVerseRepository repository = repositories.get(entityClass);

        if (repository != null && entity instanceof ShardingEntity) {
            try {
                repository.insert((ShardingEntity) entity);
            } catch (SQLException e) {
                System.err.println("[SplitVerseGraphAdapter] Failed to persist entity: " + e.getMessage());
            }
        } else {
            // For non-ShardingEntity, use direct SQL
            persistNonShardingEntity(entityClass, entity);
        }
    }

    /**
     * Persist non-ShardingEntity using direct SQL
     */
    private void persistNonShardingEntity(Class<?> entityClass, Object entity) {
        // Implementation would use prepared statements to insert the entity
        // This is simplified for brevity
        System.out.println("[SplitVerseGraphAdapter] Would persist non-ShardingEntity: " + entityClass.getSimpleName());
    }

    /**
     * Load an entity from persistence
     */
    public <T> T loadEntity(String id, Class<T> entityClass) {
        SplitVerseRepository repository = repositories.get(entityClass);

        if (repository != null) {
            try {
                return (T) repository.findById(id);
            } catch (SQLException e) {
                System.err.println("[SplitVerseGraphAdapter] Failed to load entity: " + e.getMessage());
                return null;
            }
        }

        // For non-ShardingEntity, use direct SQL
        return loadNonShardingEntity(id, entityClass);
    }

    /**
     * Load non-ShardingEntity using direct SQL
     */
    private <T> T loadNonShardingEntity(String id, Class<T> entityClass) {
        // Implementation would use prepared statements to query the entity
        // This is simplified for brevity
        System.out.println("[SplitVerseGraphAdapter] Would load non-ShardingEntity: " + entityClass.getSimpleName());
        return null;
    }

    /**
     * Execute batch operations
     */
    public void executeBatch(List<Object> entities) {
        Map<Class<?>, List<Object>> entityByClass = new HashMap<>();

        // Group entities by class
        for (Object entity : entities) {
            entityByClass.computeIfAbsent(entity.getClass(), k -> new ArrayList<>()).add(entity);
        }

        // Execute batch for each class
        for (Map.Entry<Class<?>, List<Object>> entry : entityByClass.entrySet()) {
            SplitVerseRepository repository = repositories.get(entry.getKey());
            if (repository != null) {
                try {
                    List<ShardingEntity> shardingEntities = new ArrayList<>();
                    for (Object entity : entry.getValue()) {
                        if (entity instanceof ShardingEntity) {
                            shardingEntities.add((ShardingEntity) entity);
                        }
                    }
                    if (!shardingEntities.isEmpty()) {
                        repository.insertMultiple(shardingEntities);
                    }
                } catch (SQLException e) {
                    System.err.println("[SplitVerseGraphAdapter] Batch insert failed: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Shutdown all repositories
     */
    public void shutdown() {
        for (SplitVerseRepository repository : repositories.values()) {
            repository.shutdown();
        }
        repositories.clear();
    }

    /**
     * Get repository for a specific entity class
     */
    public SplitVerseRepository getRepository(Class<?> entityClass) {
        return repositories.get(entityClass);
    }

    /**
     * Get database name
     */
    public String getDatabaseName() {
        return databaseName;
    }
}