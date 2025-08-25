package com.telcobright.statemachine.persistence;

import com.telcobright.statemachine.StateMachineContextEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Optimized MySQL persistence provider with pre-compiled SQL and async writes.
 * - SQL statements are pre-compiled at initialization
 * - All write operations are async to avoid blocking the fast path
 * - Table names are inferred from entity class names
 * - No reflection at runtime for optimal performance
 */
public class OptimizedMySQLPersistenceProvider<T extends StateMachineContextEntity<?>> implements PersistenceProvider<T> {
    
    private final MysqlConnectionProvider connectionProvider;
    private final ObjectMapper objectMapper;
    private final Class<T> entityClass;
    private String tableName;
    
    // Pre-compiled SQL statements
    private final String insertSql;
    private final String updateSql;
    private final String selectSql;
    private final String existsSql;
    private final String deleteSql;
    private final String selectCompleteSql;
    
    // Async executor for write operations
    private final ExecutorService asyncWriteExecutor;
    
    // Metrics for monitoring
    private final AtomicLong totalSaves = new AtomicLong(0);
    private final AtomicLong successfulSaves = new AtomicLong(0);
    private final AtomicLong failedSaves = new AtomicLong(0);
    private final AtomicLong asyncQueueSize = new AtomicLong(0);
    
    // Write-through cache for recently saved entities (optional)
    private final ConcurrentHashMap<String, CachedEntity> writeCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 60000; // 1 minute cache TTL
    
    private static class CachedEntity {
        final Object entity;
        final long timestamp;
        
        CachedEntity(Object entity) {
            this.entity = entity;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }
    
    /**
     * Constructor with explicit table name
     */
    public OptimizedMySQLPersistenceProvider(MysqlConnectionProvider connectionProvider, 
                                            Class<T> entityClass, 
                                            String tableName) {
        this.connectionProvider = connectionProvider;
        this.entityClass = entityClass;
        this.tableName = tableName;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        
        
        // Pre-compile SQL statements
        this.insertSql = compileInsertSql();
        this.updateSql = compileUpdateSql();
        this.selectSql = compileSelectSql();
        this.existsSql = compileExistsSql();
        this.deleteSql = compileDeleteSql();
        this.selectCompleteSql = compileSelectCompleteSql();
        
        // Create async executor with bounded queue
        this.asyncWriteExecutor = new ThreadPoolExecutor(
            2, // core pool size
            4, // maximum pool size
            60L, TimeUnit.SECONDS, // keep-alive time
            new LinkedBlockingQueue<>(1000), // bounded queue
            new ThreadFactory() {
                private int counter = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "PersistenceAsync-" + counter++);
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // If queue is full, run on caller thread
        );
        
        System.out.println("[OptimizedPersistence] Initialized for table: " + tableName);
        System.out.println("[OptimizedPersistence] Pre-compiled SQL statements ready");
    }
    
    /**
     * Static helper to infer table name from class name
     * Converts CamelCase to snake_case
     * Example: CallPersistentContext -> call_persistent_context
     */
    public static String inferTableName(Class<?> entityClass) {
        String className = entityClass.getSimpleName();
        return className.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
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
            System.out.println("[OptimizedPersistence] Table initialized: " + tableName);
        } catch (SQLException e) {
            System.err.println("[OptimizedPersistence] Failed to initialize table: " + e.getMessage());
        }
    }
    
    private String compileInsertSql() {
        return String.format("""
            INSERT INTO %s (machine_id, context_class, context_data, current_state, last_state_change, is_complete)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                context_data = VALUES(context_data),
                current_state = VALUES(current_state),
                last_state_change = VALUES(last_state_change),
                is_complete = VALUES(is_complete),
                updated_at = CURRENT_TIMESTAMP
            """, tableName);
    }
    
    private String compileUpdateSql() {
        return String.format("""
            UPDATE %s 
            SET context_data = ?, current_state = ?, last_state_change = ?, is_complete = ?, updated_at = CURRENT_TIMESTAMP
            WHERE machine_id = ?
            """, tableName);
    }
    
    private String compileSelectSql() {
        return String.format("""
            SELECT context_class, context_data, current_state, last_state_change, is_complete
            FROM %s
            WHERE machine_id = ?
            """, tableName);
    }
    
    private String compileExistsSql() {
        return String.format("SELECT 1 FROM %s WHERE machine_id = ? LIMIT 1", tableName);
    }
    
    private String compileDeleteSql() {
        return String.format("DELETE FROM %s WHERE machine_id = ?", tableName);
    }
    
    private String compileSelectCompleteSql() {
        return String.format("SELECT is_complete FROM %s WHERE machine_id = ?", tableName);
    }
    
    @Override
    public void save(String machineId, T context) {
        if (context == null) {
            System.err.println("[OptimizedPersistence] Cannot save null context for machine: " + machineId);
            return;
        }
        
        // Update write-through cache immediately
        writeCache.put(machineId, new CachedEntity(context));
        
        // Increment metrics
        totalSaves.incrementAndGet();
        asyncQueueSize.incrementAndGet();
        
        // Submit async save task
        CompletableFuture.runAsync(() -> {
            try {
                performSave(machineId, context);
                successfulSaves.incrementAndGet();
            } catch (Exception e) {
                failedSaves.incrementAndGet();
                System.err.println("[OptimizedPersistence] Async save failed for machine " + machineId + ": " + e.getMessage());
            } finally {
                asyncQueueSize.decrementAndGet();
            }
        }, asyncWriteExecutor).exceptionally(throwable -> {
            System.err.println("[OptimizedPersistence] Critical error in async save: " + throwable.getMessage());
            return null;
        });
    }
    
    private void performSave(String machineId, T context) throws Exception {
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            
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
                System.out.println("[OptimizedPersistence] Saved context for machine: " + machineId + 
                                 " (state: " + context.getCurrentState() + ", complete: " + context.isComplete() + ")");
            }
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public T load(String machineId, Class<T> contextType) {
        // Check write-through cache first
        CachedEntity cached = writeCache.get(machineId);
        if (cached != null && !cached.isExpired()) {
            try {
                return (T) cached.entity;
            } catch (ClassCastException e) {
                // Cache hit but wrong type, remove from cache
                writeCache.remove(machineId);
            }
        }
        
        // Load from database
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectSql)) {
            
            stmt.setString(1, machineId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String contextClassName = rs.getString("context_class");
                    String contextJson = rs.getString("context_data");
                    
                    // Use provided context type or load dynamically
                    Class<?> actualContextClass = contextType != null ? contextType : Class.forName(contextClassName);
                    
                    // Deserialize context from JSON
                    T context = (T) objectMapper.readValue(contextJson, actualContextClass);
                    
                    // Context loaded from JSON, state fields already set
                    
                    // Ensure state fields are set
                    context.setCurrentState(rs.getString("current_state"));
                    context.setLastStateChange(rs.getTimestamp("last_state_change").toLocalDateTime());
                    context.setComplete(rs.getBoolean("is_complete"));
                    
                    // Update cache
                    writeCache.put(machineId, new CachedEntity(context));
                    
                    System.out.println("[OptimizedPersistence] Loaded context for machine: " + machineId + 
                                     " (state: " + context.getCurrentState() + ", complete: " + context.isComplete() + ")");
                    
                    return context;
                }
            }
            
        } catch (Exception e) {
            System.err.println("[OptimizedPersistence] Failed to load context for machine " + machineId + ": " + e.getMessage());
        }
        
        return null;
    }
    
    @Override
    public boolean exists(String machineId) {
        // Check cache first
        if (writeCache.containsKey(machineId)) {
            CachedEntity cached = writeCache.get(machineId);
            if (cached != null && !cached.isExpired()) {
                return true;
            }
        }
        
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(existsSql)) {
            
            stmt.setString(1, machineId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
            
        } catch (SQLException e) {
            System.err.println("[OptimizedPersistence] Failed to check existence for machine " + machineId + ": " + e.getMessage());
        }
        
        return false;
    }
    
    @Override
    public void delete(String machineId) {
        // Remove from cache
        writeCache.remove(machineId);
        
        // Async delete
        CompletableFuture.runAsync(() -> {
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                
                stmt.setString(1, machineId);
                
                int rows = stmt.executeUpdate();
                if (rows > 0) {
                    System.out.println("[OptimizedPersistence] Deleted context for machine: " + machineId);
                }
                
            } catch (SQLException e) {
                System.err.println("[OptimizedPersistence] Failed to delete context for machine " + machineId + ": " + e.getMessage());
            }
        }, asyncWriteExecutor);
    }
    
    @Override
    public boolean isComplete(String machineId) {
        // Check cache first
        CachedEntity cached = writeCache.get(machineId);
        if (cached != null && !cached.isExpired()) {
            try {
                return ((StateMachineContextEntity<?>) cached.entity).isComplete();
            } catch (ClassCastException e) {
                writeCache.remove(machineId);
            }
        }
        
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectCompleteSql)) {
            
            stmt.setString(1, machineId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("is_complete");
                }
            }
            
        } catch (SQLException e) {
            System.err.println("[OptimizedPersistence] Failed to check completion for machine " + machineId + ": " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Get persistence metrics
     */
    public void printMetrics() {
        System.out.println("[OptimizedPersistence] Metrics for table " + tableName + ":");
        System.out.println("  Total saves: " + totalSaves.get());
        System.out.println("  Successful saves: " + successfulSaves.get());
        System.out.println("  Failed saves: " + failedSaves.get());
        System.out.println("  Async queue size: " + asyncQueueSize.get());
        System.out.println("  Cache size: " + writeCache.size());
        
        // Clean expired cache entries
        writeCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    /**
     * Flush all pending async operations
     */
    public void flush() {
        try {
            asyncWriteExecutor.shutdown();
            if (!asyncWriteExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                asyncWriteExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncWriteExecutor.shutdownNow();
        }
    }
    
    /**
     * Shutdown the provider
     */
    public void shutdown() {
        flush();
        writeCache.clear();
        System.out.println("[OptimizedPersistence] Shutdown complete for table: " + tableName);
    }
}