package com.telcobright.statemachine.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Provides MySQL connections using HikariCP connection pool
 * Uses hardcoded credentials for simplicity (no config files)
 */
public class MysqlConnectionProvider {
    private final HikariDataSource dataSource;
    
    // Hardcoded database credentials
    private static final String DEFAULT_JDBC_URL = "jdbc:mysql://127.0.0.1:3306/statedb";
    private static final String DEFAULT_USERNAME = "root";
    private static final String DEFAULT_PASSWORD = "123456";
    private static final String DEFAULT_DRIVER = "com.mysql.cj.jdbc.Driver";
    
    private final String jdbcUrl;
    private final String username;
    private final String password;
    
    /**
     * Create provider with default hardcoded configuration
     */
    public MysqlConnectionProvider() {
        this(DEFAULT_JDBC_URL, DEFAULT_USERNAME, DEFAULT_PASSWORD);
    }
    
    /**
     * Create provider with custom credentials
     */
    public MysqlConnectionProvider(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.dataSource = createDataSource();
    }
    
    /**
     * Create HikariCP data source with hardcoded settings
     */
    private HikariDataSource createDataSource() {
        HikariConfig hikariConfig = new HikariConfig();
        
        // Basic connection properties
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setDriverClassName(DEFAULT_DRIVER);
        
        // Pool configuration (hardcoded optimal values)
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setConnectionTimeout(30000); // 30 seconds
        hikariConfig.setIdleTimeout(600000); // 10 minutes
        hikariConfig.setMaxLifetime(1800000); // 30 minutes
        hikariConfig.setAutoCommit(true);
        hikariConfig.setValidationTimeout(5000); // 5 seconds
        
        // MySQL specific optimizations (hardcoded)
        hikariConfig.addDataSourceProperty("cachePrepStmts", true);
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", 250);
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", 2048);
        hikariConfig.addDataSourceProperty("useServerPrepStmts", true);
        hikariConfig.addDataSourceProperty("useLocalSessionState", true);
        hikariConfig.addDataSourceProperty("rewriteBatchedStatements", true);
        hikariConfig.addDataSourceProperty("cacheResultSetMetadata", true);
        hikariConfig.addDataSourceProperty("cacheServerConfiguration", true);
        hikariConfig.addDataSourceProperty("elideSetAutoCommits", true);
        hikariConfig.addDataSourceProperty("maintainTimeStats", false);
        
        // Set pool name for monitoring
        hikariConfig.setPoolName("StateMachine-MySQL-Pool");
        
        System.out.println("[MySQL] Creating connection pool to: " + jdbcUrl);
        
        return new HikariDataSource(hikariConfig);
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
            System.out.println("[MySQL] Connection pool closed");
        }
    }
    
    /**
     * Check if the connection pool is running
     */
    public boolean isRunning() {
        return dataSource != null && !dataSource.isClosed();
    }
    
    /**
     * Get pool statistics
     */
    public String getPoolStats() {
        if (dataSource != null) {
            return String.format("Active: %d, Idle: %d, Total: %d, Waiting: %d",
                dataSource.getHikariPoolMXBean().getActiveConnections(),
                dataSource.getHikariPoolMXBean().getIdleConnections(),
                dataSource.getHikariPoolMXBean().getTotalConnections(),
                dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection());
        }
        return "Pool not initialized";
    }
    
    /**
     * Get JDBC URL
     */
    public String getJdbcUrl() {
        return jdbcUrl;
    }
}