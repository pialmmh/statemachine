package com.telcobright.statemachine;

import com.telcobright.statemachine.timeout.TimeoutManager;

/**
 * Factory for creating StateMachineRegistry with all required dependencies
 * Supports different persistence configurations: in-memory, file-based, and hybrid
 */
public class StateMachineFactory {
    
    private static StateMachineRegistry defaultRegistry;
    private static TimeoutManager defaultTimeoutManager;
    
    /**
     * Persistence configuration types
     */
    public enum PersistenceType {
        IN_MEMORY,
        FILE_BASED,
        DATABASE,
        HYBRID
    }
    
    /**
     * Get or create the default StateMachineRegistry with in-memory persistence
     */
    public static synchronized StateMachineRegistry getDefaultRegistry() {
        if (defaultRegistry == null) {
            defaultTimeoutManager = new TimeoutManager();
            defaultRegistry = new StateMachineRegistry(defaultTimeoutManager);
        }
        return defaultRegistry;
    }
    
    /**
     * Get or create a StateMachineRegistry with specified persistence type
     * @deprecated Legacy method - ShardingEntity-based persistence is now preferred
     */
    @Deprecated
    public static synchronized StateMachineRegistry getRegistry(PersistenceType persistenceType) {
        // For now, just return a simple registry - full persistence integration is handled via ShardingEntity
        TimeoutManager timeoutManager = new TimeoutManager();
        return new StateMachineRegistry(timeoutManager);
    }
    
    /**
     * Get or create a StateMachineRegistry with specified persistence type and configuration
     * @deprecated Legacy method - ShardingEntity-based persistence is now preferred
     */
    @Deprecated
    public static synchronized StateMachineRegistry getRegistry(PersistenceType persistenceType, String persistenceConfig) {
        // For now, just return a simple registry - full persistence integration is handled via ShardingEntity
        TimeoutManager timeoutManager = new TimeoutManager();
        return new StateMachineRegistry(timeoutManager);
    }
    
    /**
     * Create a new StateMachineRegistry with timeout manager
     */
    public static StateMachineRegistry createRegistry(TimeoutManager timeoutManager) {
        return new StateMachineRegistry(timeoutManager);
    }
    
    /**
     * Create a StateMachineWrapper using the default registry
     * @deprecated StateMachineWrapper should be created directly with the new dual generic structure
     */
    @Deprecated
    public static StateMachineWrapper createWrapper(String id) {
        // Cannot create generic wrapper without knowing entity and context types
        throw new UnsupportedOperationException("createWrapper(String) is deprecated. Use FluentStateMachineBuilder instead.");
    }
    
    /**
     * Create a StateMachineWrapper using a custom registry
     * @deprecated StateMachineWrapper should be created directly with the new dual generic structure
     */
    @Deprecated
    public static StateMachineWrapper createWrapper(String id, StateMachineRegistry registry) {
        // Cannot create generic wrapper without knowing entity and context types
        throw new UnsupportedOperationException("createWrapper(String, StateMachineRegistry) is deprecated. Use FluentStateMachineBuilder instead.");
    }
    
    /**
     * Shutdown the default registry and resources
     */
    public static synchronized void shutdown() {
        if (defaultRegistry != null) {
            defaultRegistry.shutdown();
            defaultRegistry = null;
        }
        if (defaultTimeoutManager != null) {
            defaultTimeoutManager.shutdown();
            defaultTimeoutManager = null;
        }
    }
    
    /**
     * Get the default timeout manager
     */
    public static TimeoutManager getDefaultTimeoutManager() {
        getDefaultRegistry(); // Ensure initialized
        return defaultTimeoutManager;
    }
    
    /**
     * Set the default instances to be used by the factory
     * This allows external code to configure the factory with specific instances
     */
    public static synchronized void setDefaultInstances(TimeoutManager timeoutManager, StateMachineRegistry registry) {
        defaultTimeoutManager = timeoutManager;
        defaultRegistry = registry;
    }
    
}
