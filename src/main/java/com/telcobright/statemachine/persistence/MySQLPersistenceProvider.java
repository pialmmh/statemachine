package com.telcobright.statemachine.persistence;

import com.telcobright.statemachine.StateMachineContextEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * MySQL-based implementation of PersistenceProvider
 * Stores state machine contexts in a MySQL database
 */
public class MySQLPersistenceProvider<T extends StateMachineContextEntity<?>> implements PersistenceProvider<T> {
    
    private final MysqlConnectionProvider connectionProvider;
    private final ObjectMapper objectMapper;
    private final String tableName;
    
    /**
     * Constructor with connection provider and table name
     */
    public MySQLPersistenceProvider(MysqlConnectionProvider connectionProvider, String tableName) {
        this.connectionProvider = connectionProvider;
        this.tableName = tableName;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    /**
     * Constructor with default table name
     */
    public MySQLPersistenceProvider(MysqlConnectionProvider connectionProvider) {
        this(connectionProvider, "state_machine_contexts");
    }
    
    /**
     * Initialize the database table if it doesn't exist
     */
    public void initialize() {
        String createTableSQL = String.format("""
            CREATE TABLE IF NOT EXISTS %s (
                machine_id VARCHAR(255) NOT NULL PRIMARY KEY,
                context_class VARCHAR(500) NOT NULL,
                context_data TEXT NOT NULL,
                current_state VARCHAR(100) NOT NULL,
                last_state_change TIMESTAMP NOT NULL,
                is_complete BOOLEAN DEFAULT FALSE,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_current_state (current_state),
                INDEX idx_is_complete (is_complete),
                INDEX idx_last_state_change (last_state_change)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """, tableName);
        
        try (Connection conn = connectionProvider.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
            System.out.println("[MySQLPersistenceProvider] Table initialized: " + tableName);
        } catch (SQLException e) {
            System.err.println("[MySQLPersistenceProvider] Failed to initialize table: " + e.getMessage());
        }
    }
    
    @Override
    public void save(String machineId, T context) {
        if (context == null) {
            System.err.println("[MySQLPersistenceProvider] Cannot save null context for machine: " + machineId);
            return;
        }
        
        String sql = String.format("""
            INSERT INTO %s (machine_id, context_class, context_data, current_state, last_state_change, is_complete)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                context_data = VALUES(context_data),
                current_state = VALUES(current_state),
                last_state_change = VALUES(last_state_change),
                is_complete = VALUES(is_complete),
                updated_at = CURRENT_TIMESTAMP
            """, tableName);
        
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            // Serialize context to JSON
            String contextJson = objectMapper.writeValueAsString(context);
            
            stmt.setString(1, machineId);
            stmt.setString(2, context.getClass().getName());
            stmt.setString(3, contextJson);
            stmt.setString(4, context.getCurrentState());
            stmt.setTimestamp(5, Timestamp.valueOf(
                context.getLastStateChange() != null ? context.getLastStateChange() : LocalDateTime.now()
            ));
            stmt.setBoolean(6, context.isComplete());
            
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                System.out.println("[MySQLPersistenceProvider] Saved context for machine: " + machineId + 
                                 " (state: " + context.getCurrentState() + ", complete: " + context.isComplete() + ")");
            }
            
        } catch (Exception e) {
            System.err.println("[MySQLPersistenceProvider] Failed to save context for machine " + machineId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public T load(String machineId, Class<T> contextType) {
        String sql = String.format("""
            SELECT context_class, context_data, current_state, last_state_change, is_complete
            FROM %s
            WHERE machine_id = ?
            """, tableName);
        
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, machineId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String contextClassName = rs.getString("context_class");
                    String contextJson = rs.getString("context_data");
                    
                    // Load the context class dynamically if not provided
                    Class<?> actualContextClass = contextType;
                    if (actualContextClass == null) {
                        try {
                            actualContextClass = Class.forName(contextClassName);
                        } catch (ClassNotFoundException e) {
                            System.err.println("[MySQLPersistenceProvider] Context class not found: " + contextClassName);
                            return null;
                        }
                    }
                    
                    // Deserialize context from JSON
                    T context = (T) objectMapper.readValue(contextJson, actualContextClass);
                    
                    // Ensure state fields are set (in case they're not in JSON)
                    context.setCurrentState(rs.getString("current_state"));
                    context.setLastStateChange(rs.getTimestamp("last_state_change").toLocalDateTime());
                    context.setComplete(rs.getBoolean("is_complete"));
                    
                    System.out.println("[MySQLPersistenceProvider] Loaded context for machine: " + machineId + 
                                     " (state: " + context.getCurrentState() + ", complete: " + context.isComplete() + ")");
                    
                    return context;
                }
            }
            
        } catch (Exception e) {
            System.err.println("[MySQLPersistenceProvider] Failed to load context for machine " + machineId + ": " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }
    
    @Override
    public boolean exists(String machineId) {
        String sql = String.format("SELECT 1 FROM %s WHERE machine_id = ? LIMIT 1", tableName);
        
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, machineId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
            
        } catch (SQLException e) {
            System.err.println("[MySQLPersistenceProvider] Failed to check existence for machine " + machineId + ": " + e.getMessage());
        }
        
        return false;
    }
    
    @Override
    public void delete(String machineId) {
        String sql = String.format("DELETE FROM %s WHERE machine_id = ?", tableName);
        
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, machineId);
            
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                System.out.println("[MySQLPersistenceProvider] Deleted context for machine: " + machineId);
            }
            
        } catch (SQLException e) {
            System.err.println("[MySQLPersistenceProvider] Failed to delete context for machine " + machineId + ": " + e.getMessage());
        }
    }
    
    @Override
    public boolean isComplete(String machineId) {
        String sql = String.format("SELECT is_complete FROM %s WHERE machine_id = ?", tableName);
        
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, machineId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("is_complete");
                }
            }
            
        } catch (SQLException e) {
            System.err.println("[MySQLPersistenceProvider] Failed to check completion for machine " + machineId + ": " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Get statistics about stored contexts
     */
    public void printStatistics() {
        String sql = String.format("""
            SELECT 
                COUNT(*) as total,
                SUM(is_complete) as completed,
                COUNT(DISTINCT current_state) as unique_states
            FROM %s
            """, tableName);
        
        try (Connection conn = connectionProvider.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                System.out.println("[MySQLPersistenceProvider] Statistics:");
                System.out.println("  Total contexts: " + rs.getInt("total"));
                System.out.println("  Completed: " + rs.getInt("completed"));
                System.out.println("  Unique states: " + rs.getInt("unique_states"));
            }
            
        } catch (SQLException e) {
            System.err.println("[MySQLPersistenceProvider] Failed to get statistics: " + e.getMessage());
        }
    }
}