package com.telcobright.statewalk.core;

import com.telcobright.splitverse.config.ShardConfig;
import com.telcobright.statewalk.persistence.EntityGraphMapper;
import com.telcobright.statewalk.persistence.SplitVerseGraphAdapter;
import com.telcobright.statemachine.StateMachineContextEntity;
import com.telcobright.statemachine.RegistryPerformanceConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * Unified builder for State-Walk configuration
 * Supports both legacy single-entity mode and new multi-entity graph mode
 */
public class StateWalkBuilder<T extends StateMachineContextEntity<?>> {

    private String registryName;
    private Class<T> contextClass;
    private ShardConfig shardConfig;
    private boolean enablePlayback = true;
    private boolean legacyMode = false;
    private RegistryPerformanceConfig performanceConfig;
    private EntityGraphMapper graphMapper;
    private Map<String, Object> additionalConfig = new HashMap<>();

    private StateWalkBuilder() {
        this.performanceConfig = RegistryPerformanceConfig.forDevelopment();
    }

    /**
     * Create a new State-Walk builder
     */
    public static <T extends StateMachineContextEntity<?>> StateWalkBuilder<T> create(String registryName) {
        if (registryName == null || registryName.trim().isEmpty()) {
            throw new IllegalArgumentException("Registry name cannot be null or empty");
        }

        StateWalkBuilder<T> builder = new StateWalkBuilder<>();
        builder.registryName = registryName;
        return builder;
    }

    /**
     * Set the context class for persistence
     */
    public StateWalkBuilder<T> withContextClass(Class<T> contextClass) {
        if (contextClass == null) {
            throw new IllegalArgumentException("Context class cannot be null");
        }

        this.contextClass = contextClass;

        // Analyze object graph at build time if not in legacy mode
        if (!legacyMode) {
            this.graphMapper = new EntityGraphMapper();
            this.graphMapper.analyzeGraph(contextClass);
        }

        return this;
    }

    /**
     * Configure shard settings for Split-Verse
     */
    public StateWalkBuilder<T> withShardConfig(ShardConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Shard config cannot be null");
        }

        // Validate that database name is not set in shard config
        if (config.getDatabase() != null && !config.getDatabase().equals(registryName)) {
            throw new IllegalArgumentException(
                "Database name in ShardConfig must not be set. It will be automatically set to registry name: " + registryName
            );
        }

        // Create new config with registry name as database
        this.shardConfig = ShardConfig.builder()
            .shardId(config.getShardId())
            .host(config.getHost())
            .port(config.getPort())
            .database(registryName) // Force database to be registry name
            .username(config.getUsername())
            .password(config.getPassword())
            .connectionPoolSize(config.getConnectionPoolSize())
            .enabled(config.isEnabled())
            .build();

        return this;
    }

    /**
     * Enable or disable event playback functionality
     */
    public StateWalkBuilder<T> withPlayback(boolean enable) {
        this.enablePlayback = enable;
        return this;
    }

    /**
     * Enable legacy mode for backward compatibility (single entity persistence)
     */
    public StateWalkBuilder<T> enableLegacyMode() {
        this.legacyMode = true;
        return this;
    }

    /**
     * Set performance configuration
     */
    public StateWalkBuilder<T> withPerformanceConfig(RegistryPerformanceConfig config) {
        this.performanceConfig = config != null ? config : RegistryPerformanceConfig.forDevelopment();
        return this;
    }

    /**
     * Add custom configuration parameter
     */
    public StateWalkBuilder<T> withConfig(String key, Object value) {
        this.additionalConfig.put(key, value);
        return this;
    }

    /**
     * Build and initialize the State-Walk registry
     */
    public StateWalkRegistry<T> build() {
        // Validate required parameters
        validate();

        // Initialize database if not in legacy mode
        if (!legacyMode) {
            initializeDatabase();

            // Pre-compile persistence mappings
            if (graphMapper != null) {
                graphMapper.compilePersistencePaths();
            }
        }

        // Create and return the registry
        return new StateWalkRegistry<>(this);
    }

    /**
     * Validate builder configuration
     */
    private void validate() {
        if (registryName == null || registryName.trim().isEmpty()) {
            throw new IllegalStateException("Registry name must be set");
        }

        if (!legacyMode && shardConfig == null) {
            throw new IllegalStateException("Shard configuration is required for multi-entity mode");
        }

        if (!legacyMode && contextClass == null) {
            throw new IllegalStateException("Context class must be set for multi-entity mode");
        }
    }

    /**
     * Initialize database with registry name
     */
    private void initializeDatabase() {
        if (shardConfig == null) {
            return;
        }

        String jdbcUrl = String.format("jdbc:mysql://%s:%d/?useSSL=false&serverTimezone=UTC",
            shardConfig.getHost(), shardConfig.getPort());

        try (Connection conn = DriverManager.getConnection(jdbcUrl,
                shardConfig.getUsername(), shardConfig.getPassword());
             Statement stmt = conn.createStatement()) {

            // Create database if not exists
            stmt.execute("CREATE DATABASE IF NOT EXISTS " + registryName);
            System.out.println("[StateWalk] Created/verified database: " + registryName);

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize database: " + registryName, e);
        }
    }

    // Getters for registry to access configuration
    public String getRegistryName() {
        return registryName;
    }

    public Class<T> getContextClass() {
        return contextClass;
    }

    public ShardConfig getShardConfig() {
        return shardConfig;
    }

    public boolean isPlaybackEnabled() {
        return enablePlayback;
    }

    public boolean isLegacyMode() {
        return legacyMode;
    }

    public RegistryPerformanceConfig getPerformanceConfig() {
        return performanceConfig;
    }

    public EntityGraphMapper getGraphMapper() {
        return graphMapper;
    }

    public Map<String, Object> getAdditionalConfig() {
        return new HashMap<>(additionalConfig);
    }
}