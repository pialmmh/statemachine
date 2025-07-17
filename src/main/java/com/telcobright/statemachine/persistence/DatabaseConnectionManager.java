package com.telcobright.statemachine.persistence;

import com.telcobright.statemachine.persistence.config.DatabaseConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Manages database connections using HikariCP connection pool
 */
public class DatabaseConnectionManager {
    private final HikariDataSource dataSource;
    
    /**
     * Create connection manager from DatabaseConfig
     */
    public DatabaseConnectionManager(DatabaseConfig config) {
        config.validate();
        HikariConfig hikariConfig = new HikariConfig(config.toHikariProperties());
        this.dataSource = new HikariDataSource(hikariConfig);
    }
    
    /**
     * Legacy constructor for backward compatibility
     */
    public DatabaseConnectionManager(String jdbcUrl, String username, String password) {
        this(jdbcUrl, username, password, new Properties());
    }
    
    /**
     * Legacy constructor for backward compatibility
     */
    public DatabaseConnectionManager(String jdbcUrl, String username, String password, Properties additionalProperties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        
        // Default pool settings
        config.setMinimumIdle(2);
        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(30000); // 30 seconds
        config.setIdleTimeout(600000); // 10 minutes
        config.setMaxLifetime(1800000); // 30 minutes
        config.setAutoCommit(true);
        
        // MySQL specific optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        
        // Apply additional properties
        additionalProperties.forEach((key, value) -> {
            if (key.toString().startsWith("hikari.")) {
                String propertyName = key.toString().substring(7);
                config.addDataSourceProperty(propertyName, value);
            }
        });
        
        this.dataSource = new HikariDataSource(config);
    }
    
    /**
     * Get a connection from the pool
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
    
    /**
     * Close the connection pool
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
    
    /**
     * Check if the connection pool is closed
     */
    public boolean isClosed() {
        return dataSource == null || dataSource.isClosed();
    }
    
    /**
     * Get pool statistics
     */
    public String getPoolStats() {
        if (dataSource != null && !dataSource.isClosed()) {
            return String.format("Active: %d, Idle: %d, Total: %d, Waiting: %d",
                dataSource.getHikariPoolMXBean().getActiveConnections(),
                dataSource.getHikariPoolMXBean().getIdleConnections(),
                dataSource.getHikariPoolMXBean().getTotalConnections(),
                dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()
            );
        }
        return "Pool closed";
    }
}