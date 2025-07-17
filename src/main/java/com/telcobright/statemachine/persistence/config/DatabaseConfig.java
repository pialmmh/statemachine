package com.telcobright.statemachine.persistence.config;

import java.util.Properties;

/**
 * Database configuration holder for state machine persistence
 */
public class DatabaseConfig {
    
    // Database connection properties
    private String jdbcUrl;
    private String username;
    private String password;
    private String driverClassName = "com.mysql.cj.jdbc.Driver";
    private String tableName = "state_machine_snapshots";
    
    // HikariCP connection pool properties
    private int minimumIdle = 2;
    private int maximumPoolSize = 10;
    private long connectionTimeout = 30000; // 30 seconds
    private long idleTimeout = 600000; // 10 minutes
    private long maxLifetime = 1800000; // 30 minutes
    private boolean autoCommit = true;
    private long validationTimeout = 5000; // 5 seconds
    private long leakDetectionThreshold = 0; // disabled by default
    
    // MySQL specific properties
    private boolean cachePrepStmts = true;
    private int prepStmtCacheSize = 250;
    private int prepStmtCacheSqlLimit = 2048;
    private boolean useServerPrepStmts = true;
    private boolean useLocalSessionState = true;
    private boolean rewriteBatchedStatements = true;
    private boolean cacheResultSetMetadata = true;
    private boolean cacheServerConfiguration = true;
    private boolean elideSetAutoCommits = true;
    private boolean maintainTimeStats = false;
    
    // Additional custom properties
    private Properties additionalProperties = new Properties();
    
    /**
     * Create config from properties
     */
    public static DatabaseConfig fromProperties(Properties props) {
        DatabaseConfig config = new DatabaseConfig();
        
        // Database connection properties
        config.jdbcUrl = props.getProperty("statemachine.db.url", 
            props.getProperty("statemachine.db.jdbcUrl"));
        config.username = props.getProperty("statemachine.db.username", 
            props.getProperty("statemachine.db.user"));
        config.password = props.getProperty("statemachine.db.password");
        config.driverClassName = props.getProperty("statemachine.db.driver", 
            config.driverClassName);
        config.tableName = props.getProperty("statemachine.db.tableName", 
            config.tableName);
        
        // HikariCP properties
        config.minimumIdle = Integer.parseInt(
            props.getProperty("statemachine.db.pool.minimumIdle", 
                String.valueOf(config.minimumIdle)));
        config.maximumPoolSize = Integer.parseInt(
            props.getProperty("statemachine.db.pool.maximumPoolSize", 
                String.valueOf(config.maximumPoolSize)));
        config.connectionTimeout = Long.parseLong(
            props.getProperty("statemachine.db.pool.connectionTimeout", 
                String.valueOf(config.connectionTimeout)));
        config.idleTimeout = Long.parseLong(
            props.getProperty("statemachine.db.pool.idleTimeout", 
                String.valueOf(config.idleTimeout)));
        config.maxLifetime = Long.parseLong(
            props.getProperty("statemachine.db.pool.maxLifetime", 
                String.valueOf(config.maxLifetime)));
        config.autoCommit = Boolean.parseBoolean(
            props.getProperty("statemachine.db.pool.autoCommit", 
                String.valueOf(config.autoCommit)));
        config.validationTimeout = Long.parseLong(
            props.getProperty("statemachine.db.pool.validationTimeout", 
                String.valueOf(config.validationTimeout)));
        config.leakDetectionThreshold = Long.parseLong(
            props.getProperty("statemachine.db.pool.leakDetectionThreshold", 
                String.valueOf(config.leakDetectionThreshold)));
        
        // MySQL specific properties
        config.cachePrepStmts = Boolean.parseBoolean(
            props.getProperty("statemachine.db.mysql.cachePrepStmts", 
                String.valueOf(config.cachePrepStmts)));
        config.prepStmtCacheSize = Integer.parseInt(
            props.getProperty("statemachine.db.mysql.prepStmtCacheSize", 
                String.valueOf(config.prepStmtCacheSize)));
        config.prepStmtCacheSqlLimit = Integer.parseInt(
            props.getProperty("statemachine.db.mysql.prepStmtCacheSqlLimit", 
                String.valueOf(config.prepStmtCacheSqlLimit)));
        config.useServerPrepStmts = Boolean.parseBoolean(
            props.getProperty("statemachine.db.mysql.useServerPrepStmts", 
                String.valueOf(config.useServerPrepStmts)));
        config.useLocalSessionState = Boolean.parseBoolean(
            props.getProperty("statemachine.db.mysql.useLocalSessionState", 
                String.valueOf(config.useLocalSessionState)));
        config.rewriteBatchedStatements = Boolean.parseBoolean(
            props.getProperty("statemachine.db.mysql.rewriteBatchedStatements", 
                String.valueOf(config.rewriteBatchedStatements)));
        config.cacheResultSetMetadata = Boolean.parseBoolean(
            props.getProperty("statemachine.db.mysql.cacheResultSetMetadata", 
                String.valueOf(config.cacheResultSetMetadata)));
        config.cacheServerConfiguration = Boolean.parseBoolean(
            props.getProperty("statemachine.db.mysql.cacheServerConfiguration", 
                String.valueOf(config.cacheServerConfiguration)));
        config.elideSetAutoCommits = Boolean.parseBoolean(
            props.getProperty("statemachine.db.mysql.elideSetAutoCommits", 
                String.valueOf(config.elideSetAutoCommits)));
        config.maintainTimeStats = Boolean.parseBoolean(
            props.getProperty("statemachine.db.mysql.maintainTimeStats", 
                String.valueOf(config.maintainTimeStats)));
        
        // Collect any additional properties with custom prefix
        props.forEach((key, value) -> {
            String keyStr = key.toString();
            if (keyStr.startsWith("statemachine.db.custom.")) {
                String customKey = keyStr.substring("statemachine.db.custom.".length());
                config.additionalProperties.setProperty(customKey, value.toString());
            }
        });
        
        return config;
    }
    
    /**
     * Convert to Properties for HikariCP and MySQL
     */
    public Properties toHikariProperties() {
        Properties props = new Properties();
        
        // Basic connection properties
        props.setProperty("jdbcUrl", jdbcUrl);
        props.setProperty("username", username);
        props.setProperty("password", password);
        props.setProperty("driverClassName", driverClassName);
        
        // Pool properties
        props.setProperty("minimumIdle", String.valueOf(minimumIdle));
        props.setProperty("maximumPoolSize", String.valueOf(maximumPoolSize));
        props.setProperty("connectionTimeout", String.valueOf(connectionTimeout));
        props.setProperty("idleTimeout", String.valueOf(idleTimeout));
        props.setProperty("maxLifetime", String.valueOf(maxLifetime));
        props.setProperty("autoCommit", String.valueOf(autoCommit));
        props.setProperty("validationTimeout", String.valueOf(validationTimeout));
        
        if (leakDetectionThreshold > 0) {
            props.setProperty("leakDetectionThreshold", String.valueOf(leakDetectionThreshold));
        }
        
        // MySQL data source properties
        props.setProperty("dataSource.cachePrepStmts", String.valueOf(cachePrepStmts));
        props.setProperty("dataSource.prepStmtCacheSize", String.valueOf(prepStmtCacheSize));
        props.setProperty("dataSource.prepStmtCacheSqlLimit", String.valueOf(prepStmtCacheSqlLimit));
        props.setProperty("dataSource.useServerPrepStmts", String.valueOf(useServerPrepStmts));
        props.setProperty("dataSource.useLocalSessionState", String.valueOf(useLocalSessionState));
        props.setProperty("dataSource.rewriteBatchedStatements", String.valueOf(rewriteBatchedStatements));
        props.setProperty("dataSource.cacheResultSetMetadata", String.valueOf(cacheResultSetMetadata));
        props.setProperty("dataSource.cacheServerConfiguration", String.valueOf(cacheServerConfiguration));
        props.setProperty("dataSource.elideSetAutoCommits", String.valueOf(elideSetAutoCommits));
        props.setProperty("dataSource.maintainTimeStats", String.valueOf(maintainTimeStats));
        
        // Add any additional custom properties
        additionalProperties.forEach((key, value) -> {
            props.setProperty("dataSource." + key, value.toString());
        });
        
        return props;
    }
    
    /**
     * Validate the configuration
     */
    public void validate() {
        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
            throw new IllegalStateException("Database URL is required");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalStateException("Database username is required");
        }
        if (password == null) {
            throw new IllegalStateException("Database password is required");
        }
        if (minimumIdle < 0) {
            throw new IllegalStateException("Minimum idle connections cannot be negative");
        }
        if (maximumPoolSize < 1) {
            throw new IllegalStateException("Maximum pool size must be at least 1");
        }
        if (minimumIdle > maximumPoolSize) {
            throw new IllegalStateException("Minimum idle cannot exceed maximum pool size");
        }
    }
    
    // Getters and setters
    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public String getDriverClassName() { return driverClassName; }
    public void setDriverClassName(String driverClassName) { this.driverClassName = driverClassName; }
    
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    
    public int getMinimumIdle() { return minimumIdle; }
    public void setMinimumIdle(int minimumIdle) { this.minimumIdle = minimumIdle; }
    
    public int getMaximumPoolSize() { return maximumPoolSize; }
    public void setMaximumPoolSize(int maximumPoolSize) { this.maximumPoolSize = maximumPoolSize; }
    
    public long getConnectionTimeout() { return connectionTimeout; }
    public void setConnectionTimeout(long connectionTimeout) { this.connectionTimeout = connectionTimeout; }
    
    public long getIdleTimeout() { return idleTimeout; }
    public void setIdleTimeout(long idleTimeout) { this.idleTimeout = idleTimeout; }
    
    public long getMaxLifetime() { return maxLifetime; }
    public void setMaxLifetime(long maxLifetime) { this.maxLifetime = maxLifetime; }
    
    public boolean isAutoCommit() { return autoCommit; }
    public void setAutoCommit(boolean autoCommit) { this.autoCommit = autoCommit; }
    
    public Properties getAdditionalProperties() { return additionalProperties; }
    public void setAdditionalProperties(Properties additionalProperties) { 
        this.additionalProperties = additionalProperties; 
    }
}