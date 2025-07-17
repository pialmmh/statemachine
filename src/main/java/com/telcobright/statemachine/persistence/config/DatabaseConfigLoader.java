package com.telcobright.statemachine.persistence.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Loads database configuration from various sources (files, classpath, system properties)
 */
public class DatabaseConfigLoader {
    
    private static final String DEFAULT_CONFIG_FILE = "statemachine-db.properties";
    private static final String[] CONFIG_LOCATIONS = {
        "./statemachine-db.properties",
        "./config/statemachine-db.properties",
        System.getProperty("user.home") + "/.statemachine/db.properties",
        "/etc/statemachine/db.properties"
    };
    
    /**
     * Load configuration from default locations
     */
    public static DatabaseConfig loadConfig() {
        return loadConfig((String) null);
    }
    
    /**
     * Load configuration from specified file path
     */
    public static DatabaseConfig loadConfig(String configFile) {
        Properties props = new Properties();
        
        // 1. Load from system properties first (highest priority)
        loadSystemProperties(props);
        
        // 2. Load from environment variables
        loadEnvironmentVariables(props);
        
        // 3. Load from specified file or default locations
        if (configFile != null) {
            loadFromFile(props, configFile);
        } else {
            loadFromDefaultLocations(props);
        }
        
        // 4. Load from classpath as fallback
        loadFromClasspath(props, DEFAULT_CONFIG_FILE);
        
        return DatabaseConfig.fromProperties(props);
    }
    
    /**
     * Load configuration from Properties object
     */
    public static DatabaseConfig loadConfig(Properties properties) {
        Properties props = new Properties();
        
        // Start with provided properties
        props.putAll(properties);
        
        // Override with system properties if present
        loadSystemProperties(props);
        
        // Override with environment variables if present
        loadEnvironmentVariables(props);
        
        return DatabaseConfig.fromProperties(props);
    }
    
    /**
     * Load from system properties (statemachine.db.*)
     */
    private static void loadSystemProperties(Properties props) {
        System.getProperties().forEach((key, value) -> {
            String keyStr = key.toString();
            if (keyStr.startsWith("statemachine.db.")) {
                props.setProperty(keyStr, value.toString());
            }
        });
    }
    
    /**
     * Load from environment variables (STATEMACHINE_DB_*)
     */
    private static void loadEnvironmentVariables(Properties props) {
        System.getenv().forEach((key, value) -> {
            if (key.startsWith("STATEMACHINE_DB_")) {
                // Convert STATEMACHINE_DB_URL to statemachine.db.url
                String propKey = key.toLowerCase()
                    .replace("statemachine_db_", "statemachine.db.")
                    .replace("_", ".");
                props.setProperty(propKey, value);
            }
        });
    }
    
    /**
     * Load from default file locations
     */
    private static void loadFromDefaultLocations(Properties props) {
        for (String location : CONFIG_LOCATIONS) {
            if (loadFromFile(props, location)) {
                break; // Use first found file
            }
        }
    }
    
    /**
     * Load from specific file path
     */
    private static boolean loadFromFile(Properties props, String filePath) {
        Path path = Paths.get(filePath);
        if (Files.exists(path) && Files.isReadable(path)) {
            try (InputStream input = new FileInputStream(path.toFile())) {
                Properties fileProps = new Properties();
                fileProps.load(input);
                
                // Add all properties, overriding existing ones
                fileProps.forEach((key, value) -> props.setProperty((String) key, (String) value));
                
                System.out.println("Loaded database config from: " + filePath);
                return true;
            } catch (IOException e) {
                System.err.println("Failed to load config from " + filePath + ": " + e.getMessage());
            }
        }
        return false;
    }
    
    /**
     * Load from classpath
     */
    private static boolean loadFromClasspath(Properties props, String resourceName) {
        try (InputStream input = DatabaseConfigLoader.class.getClassLoader()
                .getResourceAsStream(resourceName)) {
            if (input != null) {
                Properties classpathProps = new Properties();
                classpathProps.load(input);
                
                // Only add properties that don't already exist (lower priority)
                classpathProps.forEach((key, value) -> {
                    if (!props.containsKey(key)) {
                        props.setProperty(key.toString(), value.toString());
                    }
                });
                
                System.out.println("Loaded database config from classpath: " + resourceName);
                return true;
            }
        } catch (IOException e) {
            System.err.println("Failed to load config from classpath " + resourceName + ": " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Create a minimal config for testing
     */
    public static DatabaseConfig createTestConfig() {
        Properties props = new Properties();
        props.setProperty("statemachine.db.url", "jdbc:mysql://localhost:3306/statemachine_test");
        props.setProperty("statemachine.db.username", "test");
        props.setProperty("statemachine.db.password", "test");
        props.setProperty("statemachine.db.pool.maximumPoolSize", "5");
        props.setProperty("statemachine.db.pool.minimumIdle", "1");
        return DatabaseConfig.fromProperties(props);
    }
    
    /**
     * Create config from JDBC URL with defaults
     */
    public static DatabaseConfig createConfig(String jdbcUrl, String username, String password) {
        Properties props = new Properties();
        props.setProperty("statemachine.db.url", jdbcUrl);
        props.setProperty("statemachine.db.username", username);
        props.setProperty("statemachine.db.password", password);
        return DatabaseConfig.fromProperties(props);
    }
    
    /**
     * Validate and provide helpful error messages
     */
    public static void validateAndReport(DatabaseConfig config) {
        try {
            config.validate();
            System.out.println("Database configuration validated successfully:");
            System.out.println("  URL: " + maskPassword(config.getJdbcUrl()));
            System.out.println("  Username: " + config.getUsername());
            System.out.println("  Table: " + config.getTableName());
            System.out.println("  Pool Size: " + config.getMinimumIdle() + "-" + config.getMaximumPoolSize());
        } catch (IllegalStateException e) {
            System.err.println("Database configuration validation failed:");
            System.err.println("  Error: " + e.getMessage());
            System.err.println("  Please check your configuration file or system properties");
            throw e;
        }
    }
    
    /**
     * Mask password in URL for logging
     */
    private static String maskPassword(String url) {
        if (url == null) return null;
        return url.replaceAll("password=[^&;]*", "password=***");
    }
    
    /**
     * Print configuration loading help
     */
    public static void printConfigHelp() {
        System.out.println("Database Configuration Loading Order (highest to lowest priority):");
        System.out.println("1. System Properties (-Dstatemachine.db.url=...)");
        System.out.println("2. Environment Variables (STATEMACHINE_DB_URL=...)");
        System.out.println("3. Configuration Files:");
        for (String location : CONFIG_LOCATIONS) {
            System.out.println("   - " + location);
        }
        System.out.println("4. Classpath: " + DEFAULT_CONFIG_FILE);
        System.out.println();
        System.out.println("Required Properties:");
        System.out.println("  statemachine.db.url      - JDBC URL");
        System.out.println("  statemachine.db.username - Database username");
        System.out.println("  statemachine.db.password - Database password");
        System.out.println();
        System.out.println("Optional Properties:");
        System.out.println("  statemachine.db.tableName - Table name (default: state_machine_snapshots)");
        System.out.println("  statemachine.db.pool.maximumPoolSize - Max connections (default: 10)");
        System.out.println("  statemachine.db.pool.minimumIdle - Min idle connections (default: 2)");
        System.out.println("  statemachine.db.mysql.* - MySQL specific settings");
        System.out.println("  statemachine.db.custom.* - Custom data source properties");
    }
}