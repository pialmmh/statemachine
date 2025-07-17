package com.telcobright.statemachine;

import com.telcobright.statemachine.persistence.FileBasedStateMachineSnapshotRepository;
import com.telcobright.statemachine.persistence.HybridStateMachineSnapshotRepository;
import com.telcobright.statemachine.persistence.InMemoryStateMachineSnapshotRepository;
import com.telcobright.statemachine.persistence.StateMachineSnapshotRepository;
import com.telcobright.statemachine.timeout.TimeoutManager;

/**
 * Factory for creating StateMachineRegistry with all required dependencies
 * Supports different persistence configurations: in-memory, file-based, and hybrid
 */
public class StateMachineFactory {
    
    private static StateMachineRegistry defaultRegistry;
    private static TimeoutManager defaultTimeoutManager;
    private static StateMachineSnapshotRepository defaultRepository;
    
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
            defaultRepository = new InMemoryStateMachineSnapshotRepository();
            defaultRegistry = new StateMachineRegistry(defaultRepository, defaultTimeoutManager);
        }
        return defaultRegistry;
    }
    
    /**
     * Get or create a StateMachineRegistry with specified persistence type
     */
    public static synchronized StateMachineRegistry getRegistry(PersistenceType persistenceType) {
        return getRegistry(persistenceType, "./state_machine_snapshots");
    }
    
    /**
     * Get or create a StateMachineRegistry with specified persistence type and configuration
     */
    public static synchronized StateMachineRegistry getRegistry(PersistenceType persistenceType, String persistenceConfig) {
        StateMachineSnapshotRepository repository;
        
        switch (persistenceType) {
            case FILE_BASED:
                repository = new FileBasedStateMachineSnapshotRepository(persistenceConfig);
                break;
            case HYBRID:
                // Use file-based as primary, in-memory as fallback
                StateMachineSnapshotRepository primary = new FileBasedStateMachineSnapshotRepository(persistenceConfig);
                StateMachineSnapshotRepository fallback = new InMemoryStateMachineSnapshotRepository();
                repository = new HybridStateMachineSnapshotRepository(primary, fallback);
                break;
            case IN_MEMORY:
            default:
                repository = new InMemoryStateMachineSnapshotRepository();
                break;
        }
        
        TimeoutManager timeoutManager = new TimeoutManager();
        return new StateMachineRegistry(repository, timeoutManager);
    }
    
    /**
     * Create a new StateMachineRegistry with custom dependencies
     */
    public static StateMachineRegistry createRegistry(StateMachineSnapshotRepository repository,
                                                     TimeoutManager timeoutManager) {
        return new StateMachineRegistry(repository, timeoutManager);
    }
    
    /**
     * Create a new StateMachineRegistry with default timeout manager
     */
    public static StateMachineRegistry createRegistry(StateMachineSnapshotRepository repository) {
        return new StateMachineRegistry(repository, new TimeoutManager());
    }
    
    /**
     * Create a StateMachineWrapper using the default registry
     */
    public static StateMachineWrapper createWrapper(String id) {
        GenericStateMachine machine = getDefaultRegistry().createOrGet(id);
        return StateMachineWrapper.create(machine);
    }
    
    /**
     * Create a StateMachineWrapper using a custom registry
     */
    public static StateMachineWrapper createWrapper(String id, StateMachineRegistry registry) {
        GenericStateMachine machine = registry.createOrGet(id);
        return StateMachineWrapper.create(machine);
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
        if (defaultRepository instanceof InMemoryStateMachineSnapshotRepository) {
            ((InMemoryStateMachineSnapshotRepository) defaultRepository).shutdown();
        }
        defaultRepository = null;
    }
    
    /**
     * Get the default timeout manager
     */
    public static TimeoutManager getDefaultTimeoutManager() {
        getDefaultRegistry(); // Ensure initialized
        return defaultTimeoutManager;
    }
    
    /**
     * Get the default repository
     */
    public static StateMachineSnapshotRepository getDefaultRepository() {
        getDefaultRegistry(); // Ensure initialized
        return defaultRepository;
    }
}
