package com.telcobright.statemachine.config;

import com.telcobright.statemachine.timeout.TimeoutManager;
import com.telcobright.statemachine.monitoring.SimpleDatabaseSnapshotRecorder;

/**
 * Configuration for StateMachineRegistry with builder pattern
 * Allows flexible configuration of debugging and monitoring features
 */
public class RegistryConfig {
    
    // Core configuration
    private final TimeoutManager timeoutManager;
    
    // Debug flags
    private final boolean snapshotDebug;
    private final boolean liveDebug;
    
    // WebSocket configuration
    private final int webSocketPort;
    private final boolean autoStartWebSocket;
    
    // Snapshot configuration
    private final SimpleDatabaseSnapshotRecorder customSnapshotRecorder;
    private final String snapshotSessionPrefix;
    
    // Monitoring configuration
    private final boolean enableMetrics;
    private final boolean enableEventLogging;
    
    private RegistryConfig(Builder builder) {
        this.timeoutManager = builder.timeoutManager;
        this.snapshotDebug = builder.snapshotDebug;
        this.liveDebug = builder.liveDebug;
        this.webSocketPort = builder.webSocketPort;
        this.autoStartWebSocket = builder.autoStartWebSocket;
        this.customSnapshotRecorder = builder.customSnapshotRecorder;
        this.snapshotSessionPrefix = builder.snapshotSessionPrefix;
        this.enableMetrics = builder.enableMetrics;
        this.enableEventLogging = builder.enableEventLogging;
    }
    
    // Getters
    public TimeoutManager getTimeoutManager() { return timeoutManager; }
    public boolean isSnapshotDebugEnabled() { return snapshotDebug; }
    public boolean isLiveDebugEnabled() { return liveDebug; }
    public int getWebSocketPort() { return webSocketPort; }
    public boolean isAutoStartWebSocket() { return autoStartWebSocket; }
    public SimpleDatabaseSnapshotRecorder getCustomSnapshotRecorder() { return customSnapshotRecorder; }
    public String getSnapshotSessionPrefix() { return snapshotSessionPrefix; }
    public boolean isMetricsEnabled() { return enableMetrics; }
    public boolean isEventLoggingEnabled() { return enableEventLogging; }
    
    // Check if any debug mode is enabled
    public boolean isDebugEnabled() {
        return snapshotDebug || liveDebug;
    }
    
    /**
     * Builder for RegistryConfig
     */
    public static class Builder {
        // Core configuration
        private TimeoutManager timeoutManager;
        
        // Debug flags - disabled by default
        private boolean snapshotDebug = false;
        private boolean liveDebug = false;
        
        // WebSocket configuration
        private int webSocketPort = 9999;
        private boolean autoStartWebSocket = true;
        
        // Snapshot configuration
        private SimpleDatabaseSnapshotRecorder customSnapshotRecorder;
        private String snapshotSessionPrefix = "session";
        
        // Monitoring configuration
        private boolean enableMetrics = false;
        private boolean enableEventLogging = false;
        
        public Builder() {}
        
        public Builder withTimeoutManager(TimeoutManager timeoutManager) {
            this.timeoutManager = timeoutManager;
            return this;
        }
        
        /**
         * Enable snapshot debugging - records state transitions to database
         */
        public Builder withSnapshotDebug(boolean enable) {
            this.snapshotDebug = enable;
            return this;
        }
        
        /**
         * Enable live debugging - WebSocket server for real-time monitoring
         */
        public Builder withLiveDebug(boolean enable) {
            this.liveDebug = enable;
            return this;
        }
        
        /**
         * Enable both snapshot and live debugging
         */
        public Builder withFullDebug() {
            this.snapshotDebug = true;
            this.liveDebug = true;
            return this;
        }
        
        /**
         * Disable all debugging
         */
        public Builder withNoDebug() {
            this.snapshotDebug = false;
            this.liveDebug = false;
            return this;
        }
        
        public Builder withWebSocketPort(int port) {
            this.webSocketPort = port;
            return this;
        }
        
        public Builder withAutoStartWebSocket(boolean autoStart) {
            this.autoStartWebSocket = autoStart;
            return this;
        }
        
        public Builder withCustomSnapshotRecorder(SimpleDatabaseSnapshotRecorder recorder) {
            this.customSnapshotRecorder = recorder;
            return this;
        }
        
        public Builder withSnapshotSessionPrefix(String prefix) {
            this.snapshotSessionPrefix = prefix;
            return this;
        }
        
        public Builder withMetrics(boolean enable) {
            this.enableMetrics = enable;
            return this;
        }
        
        public Builder withEventLogging(boolean enable) {
            this.enableEventLogging = enable;
            return this;
        }
        
        public RegistryConfig build() {
            // Validate configuration
            if (liveDebug && webSocketPort <= 0) {
                throw new IllegalStateException("Invalid WebSocket port for live debugging: " + webSocketPort);
            }
            
            return new RegistryConfig(this);
        }
    }
    
    /**
     * Create a default configuration with no debugging
     */
    public static RegistryConfig defaultConfig() {
        return new Builder().build();
    }
    
    /**
     * Create a configuration for development with full debugging
     */
    public static RegistryConfig developmentConfig() {
        return new Builder()
            .withFullDebug()
            .withMetrics(true)
            .withEventLogging(true)
            .build();
    }
    
    /**
     * Create a configuration for production (no debugging by default)
     */
    public static RegistryConfig productionConfig() {
        return new Builder()
            .withSnapshotDebug(false)
            .withLiveDebug(false)
            .withMetrics(true)
            .withEventLogging(false)
            .build();
    }
    
    @Override
    public String toString() {
        return "RegistryConfig{" +
            "snapshotDebug=" + snapshotDebug +
            ", liveDebug=" + liveDebug +
            ", webSocketPort=" + webSocketPort +
            ", metrics=" + enableMetrics +
            ", eventLogging=" + enableEventLogging +
            '}';
    }
}