package com.telcobright.statemachine.persistence;

import com.telcobright.statemachine.persistence.config.DatabaseConfig;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * MySQL-based implementation of StateMachineSnapshotRepository with custom SQL execution support
 */
public class DatabaseStateMachineSnapshotRepository implements StateMachineSnapshotRepository {
    private final DatabaseConnectionManager connectionManager;
    private final ScheduledExecutorService executorService;
    private final String tableName;
    
    // Custom SQL execution functions
    private BiFunction<Connection, StateMachineSnapshotEntity, Boolean> customSaveFunction;
    private BiFunction<Connection, String, StateMachineSnapshotEntity> customLoadFunction;
    private Function<Connection, Boolean> customInitFunction;
    
    // SQL statements
    private String createTableSql;
    private String insertSql;
    private String selectLatestSql;
    private String selectAllByMachineIdSql;
    private String deleteSql;
    private String selectOfflineSql;
    
    /**
     * Create repository from DatabaseConfig
     */
    public DatabaseStateMachineSnapshotRepository(DatabaseConfig config) {
        this.connectionManager = new DatabaseConnectionManager(config);
        this.tableName = config.getTableName();
        this.executorService = Executors.newScheduledThreadPool(2);
        
        initializeSqlStatements();
        initializeDatabase();
    }
    
    public DatabaseStateMachineSnapshotRepository(DatabaseConnectionManager connectionManager) {
        this(connectionManager, "state_machine_snapshots");
    }
    
    public DatabaseStateMachineSnapshotRepository(DatabaseConnectionManager connectionManager, String tableName) {
        this.connectionManager = connectionManager;
        this.tableName = tableName;
        this.executorService = Executors.newScheduledThreadPool(2);
        
        initializeSqlStatements();
        initializeDatabase();
    }
    
    private void initializeSqlStatements() {
        createTableSql = String.format("""
            CREATE TABLE IF NOT EXISTS %s (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                machine_id VARCHAR(255) NOT NULL,
                state_id VARCHAR(255) NOT NULL,
                context TEXT,
                is_offline BOOLEAN DEFAULT FALSE,
                timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_machine_id (machine_id),
                INDEX idx_timestamp (timestamp),
                INDEX idx_offline (is_offline)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """, tableName);
        
        insertSql = String.format("""
            INSERT INTO %s (machine_id, state_id, context, is_offline, timestamp) 
            VALUES (?, ?, ?, ?, ?)
            """, tableName);
        
        selectLatestSql = String.format("""
            SELECT * FROM %s 
            WHERE machine_id = ? 
            ORDER BY timestamp DESC 
            LIMIT 1
            """, tableName);
        
        selectAllByMachineIdSql = String.format("""
            SELECT * FROM %s 
            WHERE machine_id = ? 
            ORDER BY timestamp DESC
            """, tableName);
        
        deleteSql = String.format("DELETE FROM %s WHERE machine_id = ?", tableName);
        
        selectOfflineSql = String.format("""
            SELECT DISTINCT t1.* FROM %s t1
            INNER JOIN (
                SELECT machine_id, MAX(timestamp) as max_timestamp
                FROM %s
                GROUP BY machine_id
            ) t2 ON t1.machine_id = t2.machine_id AND t1.timestamp = t2.max_timestamp
            WHERE t1.is_offline = TRUE
            """, tableName, tableName);
    }
    
    private void initializeDatabase() {
        try (Connection conn = connectionManager.getConnection()) {
            // Run custom initialization if provided
            if (customInitFunction != null) {
                if (!customInitFunction.apply(conn)) {
                    throw new RuntimeException("Custom initialization function failed");
                }
            }
            
            // Create table
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createTableSql);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }
    
    @Override
    public void saveAsync(StateMachineSnapshotEntity snapshot) {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = connectionManager.getConnection()) {
                boolean saved = false;
                
                // Try custom save function first
                if (customSaveFunction != null) {
                    saved = customSaveFunction.apply(conn, snapshot);
                }
                
                // If custom function didn't save or doesn't exist, use default
                if (!saved) {
                    try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                        pstmt.setString(1, snapshot.getMachineId());
                        pstmt.setString(2, snapshot.getStateId());
                        pstmt.setString(3, snapshot.getContext());
                        pstmt.setBoolean(4, snapshot.getIsOffline());
                        pstmt.setTimestamp(5, Timestamp.valueOf(snapshot.getTimestamp()));
                        pstmt.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save snapshot", e);
            }
        }, executorService);
    }
    
    @Override
    public Optional<StateMachineSnapshotEntity> findLatestByMachineId(String machineId) {
        try (Connection conn = connectionManager.getConnection()) {
            // Try custom load function first
            if (customLoadFunction != null) {
                StateMachineSnapshotEntity result = customLoadFunction.apply(conn, machineId);
                if (result != null) {
                    return Optional.of(result);
                }
            }
            
            // Use default query
            try (PreparedStatement pstmt = conn.prepareStatement(selectLatestSql)) {
                pstmt.setString(1, machineId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSetToEntity(rs));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find latest snapshot", e);
        }
        return Optional.empty();
    }
    
    @Override
    public List<StateMachineSnapshotEntity> findAllByMachineId(String machineId) {
        List<StateMachineSnapshotEntity> results = new ArrayList<>();
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(selectAllByMachineIdSql)) {
            
            pstmt.setString(1, machineId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToEntity(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all snapshots", e);
        }
        return results;
    }
    
    @Override
    public void deleteByMachineId(String machineId) {
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {
            
            pstmt.setString(1, machineId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete snapshots", e);
        }
    }
    
    @Override
    public List<StateMachineSnapshotEntity> findAllOfflineSnapshots() {
        List<StateMachineSnapshotEntity> results = new ArrayList<>();
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(selectOfflineSql);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                results.add(mapResultSetToEntity(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find offline snapshots", e);
        }
        return results;
    }
    
    /**
     * Execute custom SQL query and return results
     */
    public <T> T executeCustomQuery(String sql, Function<ResultSet, T> resultMapper, Object... params) {
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            // Set parameters
            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }
            
            try (ResultSet rs = pstmt.executeQuery()) {
                return resultMapper.apply(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute custom query", e);
        }
    }
    
    /**
     * Execute custom SQL update/insert/delete
     */
    public int executeCustomUpdate(String sql, Object... params) {
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            // Set parameters
            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }
            
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute custom update", e);
        }
    }
    
    /**
     * Execute custom SQL in a transaction
     */
    public <T> T executeInTransaction(Function<Connection, T> transactionFunction) {
        Connection conn = null;
        boolean autoCommit = true;
        try {
            conn = connectionManager.getConnection();
            autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            
            T result = transactionFunction.apply(conn);
            
            conn.commit();
            return result;
        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
            }
            throw new RuntimeException("Transaction failed", e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(autoCommit);
                    conn.close();
                } catch (SQLException closeEx) {
                    // Log but don't throw
                }
            }
        }
    }
    
    private StateMachineSnapshotEntity mapResultSetToEntity(ResultSet rs) throws SQLException {
        StateMachineSnapshotEntity entity = new StateMachineSnapshotEntity(
            rs.getString("machine_id"),
            rs.getString("state_id"),
            rs.getString("context"),
            rs.getBoolean("is_offline")
        );
        entity.setId(rs.getLong("id"));
        entity.setTimestamp(rs.getTimestamp("timestamp").toLocalDateTime());
        entity.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        entity.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return entity;
    }
    
    /**
     * Set custom save function for persisting snapshots
     */
    public void setCustomSaveFunction(BiFunction<Connection, StateMachineSnapshotEntity, Boolean> customSaveFunction) {
        this.customSaveFunction = customSaveFunction;
    }
    
    /**
     * Set custom load function for retrieving snapshots
     */
    public void setCustomLoadFunction(BiFunction<Connection, String, StateMachineSnapshotEntity> customLoadFunction) {
        this.customLoadFunction = customLoadFunction;
    }
    
    /**
     * Set custom initialization function
     */
    public void setCustomInitFunction(Function<Connection, Boolean> customInitFunction) {
        this.customInitFunction = customInitFunction;
    }
    
    /**
     * Get the table name
     */
    public String getTableName() {
        return tableName;
    }
    
    /**
     * Shutdown the repository
     */
    public void shutdown() {
        executorService.shutdown();
        connectionManager.close();
    }
}