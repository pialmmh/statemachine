package com.telcobright.statemachine.db;

import com.telcobright.statemachine.db.entity.ShardingEntity;
import com.telcobright.statemachine.StateMachineContextEntity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MySQL implementation of PartitionedRepository
 * Supports automatic table partitioning based on sharding key
 * 
 * Partition strategies:
 * - MONTHLY: Creates monthly partitions (table_2024_01, table_2024_02, etc.)
 * - HASH: Creates hash-based partitions (table_p0, table_p1, etc.)
 * - RANGE: Creates range-based partitions
 */
public class MySQLPartitionedRepository<TEntity extends StateMachineContextEntity<?> & ShardingEntity<String>, TKey> 
        implements PartitionedRepository<TEntity, TKey> {
    
    private final DataSource dataSource;
    private final Class<TEntity> entityClass;
    private final String baseTableName;
    private final PartitionStrategy strategy;
    private final Gson gson;
    
    // Cache of created partitions
    private final Set<String> createdPartitions = ConcurrentHashMap.newKeySet();
    
    // Partition configuration
    private int hashPartitionCount = 10; // For HASH strategy
    private boolean autoCreatePartitions = true;
    private int retentionMonths = 6; // For MONTHLY strategy
    
    public enum PartitionStrategy {
        MONTHLY,      // Multiple physical tables (table_2024_01, table_2024_02, etc.)
        RANGE,        // Native MySQL RANGE partitioning by date
        HASH          // Hash-based partitioning (for non-time-based sharding)
    }
    
    public MySQLPartitionedRepository(DataSource dataSource, Class<TEntity> entityClass, 
                                     String baseTableName, PartitionStrategy strategy) {
        this.dataSource = dataSource;
        this.entityClass = entityClass;
        this.baseTableName = baseTableName;
        this.strategy = strategy;
        this.gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();
        
        // Initialize base partition structure
        initializePartitions();
    }
    
    /**
     * Initialize the partitioning structure
     */
    private void initializePartitions() {
        if (strategy == PartitionStrategy.RANGE) {
            createRangePartitionedTable();
        } else if (strategy == PartitionStrategy.HASH) {
            createHashPartitionedTable();
        } else if (strategy == PartitionStrategy.MONTHLY) {
            createMonthlyPartitionedTable();
        }
    }
    
    /**
     * Create RANGE-partitioned table for time-based data
     */
    private void createRangePartitionedTable() {
        // Create table with RANGE COLUMNS partitioning by date
        // Pre-create partitions for next 12 months
        LocalDateTime now = LocalDateTime.now();
        
        StringBuilder partitionDef = new StringBuilder();
        // First partition for historical data
        partitionDef.append("    PARTITION p_history VALUES LESS THAN ('2024-01-01'),\n");
        
        // Create partitions for next 12 months
        for (int i = 0; i <= 12; i++) {
            LocalDateTime partitionDate = now.plusMonths(i).withDayOfMonth(1);
            String partitionName = "p" + partitionDate.format(DateTimeFormatter.ofPattern("yyyyMM"));
            String partitionValue = partitionDate.plusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            
            partitionDef.append(String.format("    PARTITION %s VALUES LESS THAN ('%s')", 
                partitionName, partitionValue));
            
            if (i < 12) {
                partitionDef.append(",\n");
            }
        }
        
        String sql = String.format("""
            CREATE TABLE IF NOT EXISTS %s (
                id VARCHAR(255) NOT NULL,
                machine_id VARCHAR(255) NOT NULL,
                current_state VARCHAR(100),
                last_state_change TIMESTAMP NULL,
                is_complete BOOLEAN DEFAULT FALSE,
                entity_data JSON,
                created_at DATE NOT NULL,
                updated_at TIMESTAMP NOT NULL DEFAULT '2024-01-01 00:00:00',
                PRIMARY KEY (id, created_at),
                INDEX idx_machine_id (machine_id),
                INDEX idx_state (current_state),
                INDEX idx_created (created_at)
            ) ENGINE=InnoDB
            PARTITION BY RANGE COLUMNS(created_at) (
%s
            )
            """, baseTableName, partitionDef.toString());
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("[PartitionedRepo] Created RANGE-partitioned table: " + baseTableName);
        } catch (SQLException e) {
            System.err.println("[PartitionedRepo] Error creating RANGE-partitioned table: " + e.getMessage());
        }
    }
    
    /**
     * Create hash-partitioned table structure
     */
    private void createHashPartitionedTable() {
        String sql = String.format("""
            CREATE TABLE IF NOT EXISTS %s (
                id VARCHAR(255) NOT NULL,
                machine_id VARCHAR(255) NOT NULL,
                current_state VARCHAR(100),
                last_state_change TIMESTAMP,
                is_complete BOOLEAN DEFAULT FALSE,
                entity_data JSON,
                partition_key VARCHAR(50),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                PRIMARY KEY (id, partition_key),
                INDEX idx_machine_id (machine_id),
                INDEX idx_partition (partition_key),
                INDEX idx_state (current_state)
            ) ENGINE=InnoDB
            PARTITION BY KEY(partition_key)
            PARTITIONS %d
            """, baseTableName, hashPartitionCount);
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("[PartitionedRepo] Created hash-partitioned table: " + baseTableName);
        } catch (SQLException e) {
            System.err.println("[PartitionedRepo] Error creating partitioned table: " + e.getMessage());
        }
    }
    
    /**
     * Create monthly partitioned table structure
     */
    private void createMonthlyPartitionedTable() {
        // For monthly partitioning, we create tables dynamically as needed
        // Each month gets its own table: tablename_2024_01, tablename_2024_02, etc.
        String currentMonth = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM"));
        createMonthlyTable(currentMonth);
    }
    
    /**
     * Create a table for a specific month
     */
    private void createMonthlyTable(String monthKey) {
        String tableName = baseTableName + "_" + monthKey;
        
        if (createdPartitions.contains(tableName)) {
            return; // Already created
        }
        
        String sql = String.format("""
            CREATE TABLE IF NOT EXISTS %s (
                id VARCHAR(255) PRIMARY KEY,
                machine_id VARCHAR(255) NOT NULL,
                current_state VARCHAR(100),
                last_state_change TIMESTAMP,
                is_complete BOOLEAN DEFAULT FALSE,
                entity_data JSON,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_machine_id (machine_id),
                INDEX idx_state (current_state),
                INDEX idx_created (created_at)
            ) ENGINE=InnoDB
            """, tableName);
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            createdPartitions.add(tableName);
            System.out.println("[PartitionedRepo] Created monthly partition table: " + tableName);
        } catch (SQLException e) {
            System.err.println("[PartitionedRepo] Error creating partition table: " + e.getMessage());
        }
    }
    
    /**
     * Get the table name for an entity based on its sharding key
     */
    private String getTableName(TEntity entity) {
        if (strategy == PartitionStrategy.HASH || strategy == PartitionStrategy.RANGE) {
            // Both HASH and RANGE use single table with native partitions
            return baseTableName;
        } else if (strategy == PartitionStrategy.MONTHLY) {
            String shardingKey = getShardingKey(entity);
            // Ensure sharding key is in yyyy-MM format and convert to yyyy_MM
            String monthKey = shardingKey.replace("-", "_");
            
            // Auto-create table if needed
            if (autoCreatePartitions) {
                createMonthlyTable(monthKey);
            }
            
            return baseTableName + "_" + monthKey;
        }
        return baseTableName;
    }
    
    /**
     * Get sharding key from entity using reflection
     */
    private String getShardingKey(TEntity entity) {
        try {
            // Try to call getShardingKey method if it exists
            return (String) entity.getClass().getMethod("getShardingKey").invoke(entity);
        } catch (Exception e) {
            // Default to current month
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }
    }
    
    /**
     * Get machine ID from entity
     */
    private String getMachineId(TEntity entity) {
        try {
            // StateMachineContextEntity has getId() method
            Object id = entity.getClass().getMethod("getId").invoke(entity);
            if (id != null) return String.valueOf(id);
            
            // Try getMachineId
            id = entity.getClass().getMethod("getMachineId").invoke(entity);
            if (id != null) return String.valueOf(id);
        } catch (Exception e) {
            // Try another approach
        }
        return "unknown";
    }
    
    @Override
    public void insert(TEntity entity) {
        String tableName = getTableName(entity);
        String sql;
        
        if (strategy == PartitionStrategy.HASH) {
            sql = String.format("""
                INSERT INTO %s (id, machine_id, current_state, last_state_change, 
                               is_complete, entity_data, partition_key)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    current_state = VALUES(current_state),
                    last_state_change = VALUES(last_state_change),
                    is_complete = VALUES(is_complete),
                    entity_data = VALUES(entity_data),
                    updated_at = CURRENT_TIMESTAMP
                """, tableName);
        } else if (strategy == PartitionStrategy.RANGE) {
            // RANGE partitioning doesn't need explicit partition_key
            sql = String.format("""
                INSERT INTO %s (id, machine_id, current_state, last_state_change, 
                               is_complete, entity_data, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    current_state = VALUES(current_state),
                    last_state_change = VALUES(last_state_change),
                    is_complete = VALUES(is_complete),
                    entity_data = VALUES(entity_data),
                    updated_at = VALUES(updated_at)
                """, tableName);
        } else {
            sql = String.format("""
                INSERT INTO %s (id, machine_id, current_state, last_state_change, 
                               is_complete, entity_data)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    current_state = VALUES(current_state),
                    last_state_change = VALUES(last_state_change),
                    is_complete = VALUES(is_complete),
                    entity_data = VALUES(entity_data),
                    updated_at = CURRENT_TIMESTAMP
                """, tableName);
        }
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            String machineId = getMachineId(entity);
            pstmt.setString(1, machineId);
            pstmt.setString(2, machineId);
            pstmt.setString(3, entity.getCurrentState());
            pstmt.setTimestamp(4, entity.getLastStateChange() != null ? 
                Timestamp.valueOf(entity.getLastStateChange()) : null);
            pstmt.setBoolean(5, entity.isComplete());
            pstmt.setString(6, gson.toJson(entity));
            
            if (strategy == PartitionStrategy.HASH) {
                pstmt.setString(7, getShardingKey(entity));
            } else if (strategy == PartitionStrategy.RANGE) {
                // For RANGE partitioning, set the created_at date and updated_at timestamp
                LocalDateTime now = LocalDateTime.now();
                pstmt.setDate(7, java.sql.Date.valueOf(now.toLocalDate()));
                pstmt.setTimestamp(8, Timestamp.valueOf(now));
            }
            
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert entity", e);
        }
    }
    
    @Override
    public void insertMultiple(List<TEntity> entities) {
        // Group entities by table for batch insert
        Map<String, List<TEntity>> entitiesByTable = new HashMap<>();
        for (TEntity entity : entities) {
            String tableName = getTableName(entity);
            entitiesByTable.computeIfAbsent(tableName, k -> new ArrayList<>()).add(entity);
        }
        
        // Batch insert for each table
        for (Map.Entry<String, List<TEntity>> entry : entitiesByTable.entrySet()) {
            insertBatch(entry.getKey(), entry.getValue());
        }
    }
    
    private void insertBatch(String tableName, List<TEntity> entities) {
        String sql;
        
        if (strategy == PartitionStrategy.HASH) {
            sql = String.format("""
                INSERT INTO %s (id, machine_id, current_state, last_state_change, 
                               is_complete, entity_data, partition_key)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    current_state = VALUES(current_state),
                    last_state_change = VALUES(last_state_change),
                    is_complete = VALUES(is_complete),
                    entity_data = VALUES(entity_data),
                    updated_at = CURRENT_TIMESTAMP
                """, tableName);
        } else if (strategy == PartitionStrategy.RANGE) {
            sql = String.format("""
                INSERT INTO %s (id, machine_id, current_state, last_state_change, 
                               is_complete, entity_data, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    current_state = VALUES(current_state),
                    last_state_change = VALUES(last_state_change),
                    is_complete = VALUES(is_complete),
                    entity_data = VALUES(entity_data),
                    updated_at = VALUES(updated_at)
                """, tableName);
        } else {
            sql = String.format("""
                INSERT INTO %s (id, machine_id, current_state, last_state_change, 
                               is_complete, entity_data)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    current_state = VALUES(current_state),
                    last_state_change = VALUES(last_state_change),
                    is_complete = VALUES(is_complete),
                    entity_data = VALUES(entity_data),
                    updated_at = CURRENT_TIMESTAMP
                """, tableName);
        }
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            for (TEntity entity : entities) {
                String machineId = getMachineId(entity);
                pstmt.setString(1, machineId);
                pstmt.setString(2, machineId);
                pstmt.setString(3, entity.getCurrentState());
                pstmt.setTimestamp(4, entity.getLastStateChange() != null ? 
                    Timestamp.valueOf(entity.getLastStateChange()) : null);
                pstmt.setBoolean(5, entity.isComplete());
                pstmt.setString(6, gson.toJson(entity));
                
                if (strategy == PartitionStrategy.HASH) {
                    pstmt.setString(7, getShardingKey(entity));
                } else if (strategy == PartitionStrategy.RANGE) {
                    LocalDateTime now = LocalDateTime.now();
                    pstmt.setDate(7, java.sql.Date.valueOf(now.toLocalDate()));
                    pstmt.setTimestamp(8, Timestamp.valueOf(now));
                }
                
                pstmt.addBatch();
            }
            
            pstmt.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to batch insert entities", e);
        }
    }
    
    @Override
    public TEntity findById(TKey id) {
        // For finding by ID, we need to search across all partitions
        if (strategy == PartitionStrategy.MONTHLY) {
            // Search recent partitions first (last 3 months)
            for (int i = 0; i < 3; i++) {
                LocalDateTime date = LocalDateTime.now().minusMonths(i);
                String monthKey = date.format(DateTimeFormatter.ofPattern("yyyy_MM"));
                String tableName = baseTableName + "_" + monthKey;
                
                if (!tableExists(tableName)) continue;
                
                TEntity entity = findInTable(tableName, id);
                if (entity != null) return entity;
            }
        } else {
            // For HASH and RANGE partitioning, query the main table
            // MySQL will automatically search relevant partitions
            return findInTable(baseTableName, id);
        }
        
        return null;
    }
    
    private TEntity findInTable(String tableName, TKey id) {
        String sql = String.format("SELECT entity_data FROM %s WHERE id = ?", tableName);
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, String.valueOf(id));
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                String json = rs.getString("entity_data");
                return gson.fromJson(json, entityClass);
            }
        } catch (SQLException e) {
            // Table might not exist
        }
        
        return null;
    }
    
    private boolean tableExists(String tableName) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet rs = meta.getTables(null, null, tableName, new String[] {"TABLE"});
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }
    
    @Override
    public TEntity findByIdAndDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        // For date range queries, we need to search multiple tables/partitions
        List<TEntity> results = findAllInDateRange(startDate, endDate);
        return results.isEmpty() ? null : results.get(0);
    }
    
    /**
     * Find all entities within a date range (searches multiple partitions)
     */
    public List<TEntity> findAllInDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        List<TEntity> results = new ArrayList<>();
        
        if (strategy == PartitionStrategy.MONTHLY) {
            // Calculate which monthly partitions to search
            LocalDateTime current = startDate.withDayOfMonth(1);
            LocalDateTime endMonth = endDate.withDayOfMonth(1);
            
            while (!current.isAfter(endMonth)) {
                String monthKey = current.format(DateTimeFormatter.ofPattern("yyyy_MM"));
                String tableName = baseTableName + "_" + monthKey;
                
                if (tableExists(tableName)) {
                    results.addAll(findInTableByDateRange(tableName, startDate, endDate));
                }
                
                current = current.plusMonths(1);
            }
        } else if (strategy == PartitionStrategy.HASH || strategy == PartitionStrategy.RANGE) {
            // For HASH and RANGE partitioning, query the main table with date filter
            // MySQL will automatically search only relevant partitions for RANGE
            results.addAll(findInTableByDateRange(baseTableName, startDate, endDate));
        }
        
        return results;
    }
    
    /**
     * Find entities in a specific table within date range
     */
    private List<TEntity> findInTableByDateRange(String tableName, LocalDateTime startDate, LocalDateTime endDate) {
        List<TEntity> results = new ArrayList<>();
        String sql = String.format(
            "SELECT entity_data FROM %s WHERE created_at >= ? AND created_at <= ?", 
            tableName
        );
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            // For RANGE partitioning, use DATE comparison
            if (strategy == PartitionStrategy.RANGE) {
                pstmt.setDate(1, java.sql.Date.valueOf(startDate.toLocalDate()));
                pstmt.setDate(2, java.sql.Date.valueOf(endDate.toLocalDate()));
            } else {
                pstmt.setTimestamp(1, Timestamp.valueOf(startDate));
                pstmt.setTimestamp(2, Timestamp.valueOf(endDate));
            }
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String json = rs.getString("entity_data");
                TEntity entity = gson.fromJson(json, entityClass);
                results.add(entity);
            }
        } catch (SQLException e) {
            // Table might not exist or other error
            System.err.println("[PartitionedRepo] Error querying table " + tableName + ": " + e.getMessage());
        }
        
        return results;
    }
    
    /**
     * Find entity by ID within date range (optimized search)
     */
    public TEntity findByIdInDateRange(TKey id, LocalDateTime startDate, LocalDateTime endDate) {
        if (strategy == PartitionStrategy.MONTHLY) {
            // Search only the relevant monthly partitions
            LocalDateTime current = startDate.withDayOfMonth(1);
            LocalDateTime endMonth = endDate.withDayOfMonth(1);
            
            while (!current.isAfter(endMonth)) {
                String monthKey = current.format(DateTimeFormatter.ofPattern("yyyy_MM"));
                String tableName = baseTableName + "_" + monthKey;
                
                if (tableExists(tableName)) {
                    TEntity entity = findInTableByIdAndDate(tableName, id, startDate, endDate);
                    if (entity != null) return entity;
                }
                
                current = current.plusMonths(1);
            }
        } else if (strategy == PartitionStrategy.HASH) {
            // For hash partitioning, query with both ID and date filter
            return findInTableByIdAndDate(baseTableName, id, startDate, endDate);
        }
        
        return null;
    }
    
    /**
     * Find entity by ID and date range in specific table
     */
    private TEntity findInTableByIdAndDate(String tableName, TKey id, LocalDateTime startDate, LocalDateTime endDate) {
        String sql = String.format(
            "SELECT entity_data FROM %s WHERE id = ? AND created_at >= ? AND created_at <= ?",
            tableName
        );
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, String.valueOf(id));
            pstmt.setTimestamp(2, Timestamp.valueOf(startDate));
            pstmt.setTimestamp(3, Timestamp.valueOf(endDate));
            
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String json = rs.getString("entity_data");
                return gson.fromJson(json, entityClass);
            }
        } catch (SQLException e) {
            // Handle error
        }
        
        return null;
    }
    
    @Override
    public void updateById(TKey id, TEntity entity) {
        insert(entity); // Our insert uses ON DUPLICATE KEY UPDATE
    }
    
    @Override
    public void updateByIdAndDateRange(TKey id, TEntity entity, LocalDateTime startDate, LocalDateTime endDate) {
        updateById(id, entity);
    }
    
    /**
     * Count entities across all partitions
     */
    public long countAll() {
        long total = 0;
        
        if (strategy == PartitionStrategy.MONTHLY) {
            // Count across all monthly tables
            for (String partition : createdPartitions) {
                total += countInTable(partition);
            }
        } else if (strategy == PartitionStrategy.HASH) {
            total = countInTable(baseTableName);
        }
        
        return total;
    }
    
    /**
     * Count entities in a specific date range
     */
    public long countInDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        long total = 0;
        
        if (strategy == PartitionStrategy.MONTHLY) {
            LocalDateTime current = startDate.withDayOfMonth(1);
            LocalDateTime endMonth = endDate.withDayOfMonth(1);
            
            while (!current.isAfter(endMonth)) {
                String monthKey = current.format(DateTimeFormatter.ofPattern("yyyy_MM"));
                String tableName = baseTableName + "_" + monthKey;
                
                if (tableExists(tableName)) {
                    total += countInTableByDateRange(tableName, startDate, endDate);
                }
                
                current = current.plusMonths(1);
            }
        } else if (strategy == PartitionStrategy.HASH) {
            total = countInTableByDateRange(baseTableName, startDate, endDate);
        }
        
        return total;
    }
    
    /**
     * Count entities in a specific table
     */
    private long countInTable(String tableName) {
        String sql = String.format("SELECT COUNT(*) FROM %s", tableName);
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            // Table might not exist
        }
        
        return 0;
    }
    
    /**
     * Count entities in table within date range
     */
    private long countInTableByDateRange(String tableName, LocalDateTime startDate, LocalDateTime endDate) {
        String sql = String.format(
            "SELECT COUNT(*) FROM %s WHERE created_at >= ? AND created_at <= ?",
            tableName
        );
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setTimestamp(1, Timestamp.valueOf(startDate));
            pstmt.setTimestamp(2, Timestamp.valueOf(endDate));
            
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            // Handle error
        }
        
        return 0;
    }
    
    /**
     * Delete old partitions (for monthly strategy)
     */
    public void deletePartitionsOlderThan(LocalDateTime cutoffDate) {
        if (strategy != PartitionStrategy.MONTHLY) {
            System.err.println("[PartitionedRepo] Delete partitions only works with MONTHLY strategy");
            return;
        }
        
        String cutoffMonth = cutoffDate.format(DateTimeFormatter.ofPattern("yyyy_MM"));
        List<String> toDelete = new ArrayList<>();
        
        for (String partition : createdPartitions) {
            // Extract month from partition name
            String monthPart = partition.replace(baseTableName + "_", "");
            if (monthPart.compareTo(cutoffMonth) < 0) {
                toDelete.add(partition);
            }
        }
        
        for (String tableName : toDelete) {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                
                stmt.execute("DROP TABLE IF EXISTS " + tableName);
                createdPartitions.remove(tableName);
                System.out.println("[PartitionedRepo] Dropped old partition: " + tableName);
                
            } catch (SQLException e) {
                System.err.println("[PartitionedRepo] Failed to drop table " + tableName + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Print statistics about partitions
     */
    public void printStatistics() {
        System.out.println("\n=== MySQL Partitioned Repository Statistics ===");
        System.out.println("Base Table: " + baseTableName);
        System.out.println("Strategy: " + strategy);
        
        if (strategy == PartitionStrategy.MONTHLY) {
            System.out.println("Created Partitions: " + createdPartitions.size());
            for (String partition : createdPartitions) {
                System.out.println("  - " + partition);
            }
        } else if (strategy == PartitionStrategy.HASH) {
            System.out.println("Hash Partitions: " + hashPartitionCount);
        }
        
        System.out.println("================================================\n");
    }
    
    // Getters and setters
    public void setHashPartitionCount(int count) {
        this.hashPartitionCount = count;
    }
    
    public void setAutoCreatePartitions(boolean autoCreate) {
        this.autoCreatePartitions = autoCreate;
    }
    
    public void setRetentionMonths(int months) {
        this.retentionMonths = months;
    }
    
    /**
     * LocalDateTime adapter for Gson
     */
    private static class LocalDateTimeAdapter extends com.google.gson.TypeAdapter<LocalDateTime> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        
        @Override
        public void write(com.google.gson.stream.JsonWriter out, LocalDateTime value) throws java.io.IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(formatter.format(value));
            }
        }
        
        @Override
        public LocalDateTime read(com.google.gson.stream.JsonReader in) throws java.io.IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return LocalDateTime.parse(in.nextString(), formatter);
        }
    }
}