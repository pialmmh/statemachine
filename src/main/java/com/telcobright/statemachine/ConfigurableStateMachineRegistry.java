package com.telcobright.statemachine;

import com.telcobright.statemachine.config.RegistryConfig;
import com.telcobright.statemachine.timeout.TimeoutManager;

/**
 * Configurable implementation of StateMachineRegistry that uses RegistryConfig
 * This replaces the old enableDebugMode with separate snapshotDebug and liveDebug flags
 */
public class ConfigurableStateMachineRegistry extends StateMachineRegistry {
    
    private final RegistryConfig config;
    
    /**
     * Create registry with configuration
     */
    public ConfigurableStateMachineRegistry(RegistryConfig config) {
        super(config.getTimeoutManager(), config.getWebSocketPort());
        this.config = config;
        
        // Apply configuration
        applyConfiguration();
    }
    
    /**
     * Apply configuration settings
     */
    private void applyConfiguration() {
        // Apply debug mode if enabled
        if (config.isSnapshotDebugEnabled() || config.isLiveDebugEnabled()) {
            enableDebugMode(config.getWebSocketPort());
        }
        
        // Log configuration
        logConfiguration();
    }
    
    /**
     * Log the current configuration
     */
    private void logConfiguration() {
        System.out.println("\n╔════════════════════════════════════════╗");
        System.out.println("║   Configurable Registry Initialized    ║");
        System.out.println("╠════════════════════════════════════════╣");
        System.out.println("║ 📸 Snapshot Debug: " + formatStatus(config.isSnapshotDebugEnabled()));
        System.out.println("║ 🔴 Live Debug:     " + formatStatus(config.isLiveDebugEnabled()));
        
        if (config.isLiveDebugEnabled()) {
            System.out.println("║ 🌐 WebSocket Port: " + String.format("%-18d", config.getWebSocketPort()) + " ║");
        }
        
        System.out.println("║ 📊 Metrics:        " + formatStatus(config.isMetricsEnabled()));
        System.out.println("║ 📝 Event Logging:  " + formatStatus(config.isEventLoggingEnabled()));
        System.out.println("╚════════════════════════════════════════╝\n");
    }
    
    private String formatStatus(boolean enabled) {
        return enabled ? "✅ ENABLED          ║" : "❌ DISABLED         ║";
    }
    
    /**
     * Get the configuration
     */
    public RegistryConfig getConfig() {
        return config;
    }
    
    /**
     * Builder for ConfigurableStateMachineRegistry
     */
    public static class Builder {
        private final RegistryConfig.Builder configBuilder = new RegistryConfig.Builder();
        
        public Builder withTimeoutManager(TimeoutManager timeoutManager) {
            configBuilder.withTimeoutManager(timeoutManager);
            return this;
        }
        
        public Builder withSnapshotDebug(boolean enable) {
            configBuilder.withSnapshotDebug(enable);
            return this;
        }
        
        public Builder withLiveDebug(boolean enable) {
            configBuilder.withLiveDebug(enable);
            return this;
        }
        
        public Builder withFullDebug() {
            configBuilder.withFullDebug();
            return this;
        }
        
        public Builder withNoDebug() {
            configBuilder.withNoDebug();
            return this;
        }
        
        public Builder withWebSocketPort(int port) {
            configBuilder.withWebSocketPort(port);
            return this;
        }
        
        public Builder withAutoStartWebSocket(boolean autoStart) {
            configBuilder.withAutoStartWebSocket(autoStart);
            return this;
        }
        
        // Snapshot recorder removed - using History instead
        
        public Builder withMetrics(boolean enable) {
            configBuilder.withMetrics(enable);
            return this;
        }
        
        public Builder withEventLogging(boolean enable) {
            configBuilder.withEventLogging(enable);
            return this;
        }
        
        public ConfigurableStateMachineRegistry build() {
            return new ConfigurableStateMachineRegistry(configBuilder.build());
        }
    }
    
    /**
     * Factory methods for common configurations
     */
    
    /**
     * Create a registry for production use (no debugging)
     */
    public static ConfigurableStateMachineRegistry production() {
        return new ConfigurableStateMachineRegistry(RegistryConfig.productionConfig());
    }
    
    /**
     * Create a registry for development (full debugging)
     */
    public static ConfigurableStateMachineRegistry development() {
        return new ConfigurableStateMachineRegistry(RegistryConfig.developmentConfig());
    }
    
    /**
     * Create a registry with only snapshot debugging
     */
    public static ConfigurableStateMachineRegistry withSnapshotOnly() {
        return new Builder()
            .withSnapshotDebug(true)
            .withLiveDebug(false)
            .build();
    }
    
    /**
     * Create a registry with only live debugging
     */
    public static ConfigurableStateMachineRegistry withLiveOnly(int port) {
        return new Builder()
            .withSnapshotDebug(false)
            .withLiveDebug(true)
            .withWebSocketPort(port)
            .build();
    }
}