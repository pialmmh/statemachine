package com.telcobright.statemachine.persistence;

import com.telcobright.statemachine.persistence.config.DatabaseConfig;
import com.telcobright.statemachine.persistence.config.DatabaseConfigLoader;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Provides MySQL connections using HikariCP connection pool
 */
public class MysqlConnectionProvider {
    private final HikariDataSource dataSource;
    private final DatabaseConfig databaseConfig;
    
    /**
     * Create provider with default configuration
     */
    public MysqlConnectionProvider() {
        this(DatabaseConfigLoader.loadConfig());
    }
    
    /**
     * Create provider with specific configuration
     */
    public MysqlConnectionProvider(DatabaseConfig config) {
        this.databaseConfig = config;
        this.dataSource = createDataSource(config);
    }
    
    /**
     * Create HikariCP data source from config
     */
    private HikariDataSource createDataSource(DatabaseConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        
        // Basic connection properties
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setDriverClassName(config.getDriverClassName());
        
        // Pool configuration
        hikariConfig.setMinimumIdle(config.getMinimumIdle());
        hikariConfig.setMaximumPoolSize(config.getMaximumPoolSize());
        hikariConfig.setConnectionTimeout(config.getConnectionTimeout());
        hikariConfig.setIdleTimeout(config.getIdleTimeout());
        hikariConfig.setMaxLifetime(config.getMaxLifetime());
        hikariConfig.setAutoCommit(config.isAutoCommit());
        hikariConfig.setValidationTimeout(config.getValidationTimeout());
        
        if (config.getLeakDetectionThreshold() > 0) {
            hikariConfig.setLeakDetectionThreshold(config.getLeakDetectionThreshold());
        }
        
        // MySQL specific optimizations
        hikariConfig.addDataSourceProperty("cachePrepStmts", config.isCachePrepStmts());
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", config.getPrepStmtCacheSize());
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", config.getPrepStmtCacheSqlLimit());
        hikariConfig.addDataSourceProperty("useServerPrepStmts", config.isUseServerPrepStmts());
        hikariConfig.addDataSourceProperty("useLocalSessionState", config.isUseLocalSessionState());
        hikariConfig.addDataSourceProperty("rewriteBatchedStatements", config.isRewriteBatchedStatements());
        hikariConfig.addDataSourceProperty("cacheResultSetMetadata", config.isCacheResultSetMetadata());
        hikariConfig.addDataSourceProperty("cacheServerConfiguration", config.isCacheServerConfiguration());
        hikariConfig.addDataSourceProperty("elideSetAutoCommits", config.isElideSetAutoCommits());
        hikariConfig.addDataSourceProperty("maintainTimeStats", config.isMaintainTimeStats());
        
        // Additional custom properties
        config.getAdditionalProperties().forEach((key, value) -> {
            hikariConfig.addDataSourceProperty(key.toString(), value);
        });
        
        // Set pool name for monitoring
        hikariConfig.setPoolName("StateMachine-MySQL-Pool");
        
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
        }
    }
    
    /**
     * Check if the connection pool is running
     */
    public boolean isRunning() {
        return dataSource != null && !dataSource.isClosed();
    }
    
    /**
     * Get the database configuration
     */
    public DatabaseConfig getDatabaseConfig() {
        return databaseConfig;
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
}