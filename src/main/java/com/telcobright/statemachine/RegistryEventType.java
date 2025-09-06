package com.telcobright.statemachine;

/**
 * Enum representing different types of registry-level events
 * These are high-level events that occur at the registry level,
 * separate from individual machine state transitions
 */
public enum RegistryEventType {
    
    /**
     * Machine created and added to registry for the first time
     */
    CREATE("MACHINE_CREATED"),
    
    /**
     * Machine loaded from database into memory (rehydration)
     */
    REHYDRATE("MACHINE_REHYDRATED"),
    
    /**
     * Machine removed from memory due to final state, timeout, or eviction policy
     */
    EVICT("MACHINE_EVICTED"),
    
    /**
     * Event ignored - machine not found, already in final state, or other reasons
     */
    IGNORE("EVENT_IGNORED"),
    
    /**
     * Machine creation refused due to capacity limits or other constraints
     */
    MACHINE_CREATION_REFUSED("MACHINE_CREATION_REFUSED"),
    
    /**
     * Machine registered with the registry (initial registration)
     */
    REGISTER("MACHINE_REGISTERED"),
    
    /**
     * Machine marked as offline (moved to offline storage)
     */
    OFFLINE("MACHINE_OFFLINE"),
    
    /**
     * Registry-level timeout occurred
     */
    TIMEOUT("REGISTRY_TIMEOUT"),
    
    /**
     * Error occurred during registry operations
     */
    ERROR("REGISTRY_ERROR"),
    
    /**
     * Registry started up
     */
    STARTUP("REGISTRY_STARTUP"),
    
    /**
     * Registry shutting down
     */
    SHUTDOWN("REGISTRY_SHUTDOWN"),
    
    /**
     * Persistence operation (save/load) completed
     */
    PERSISTENCE("PERSISTENCE_OPERATION"),
    
    /**
     * Configuration change in registry
     */
    CONFIG("CONFIG_CHANGE"),
    
    /**
     * Warning event (non-critical issues)
     */
    WARNING("WARNING");
    
    private final String eventName;
    
    RegistryEventType(String eventName) {
        this.eventName = eventName;
    }
    
    public String getEventName() {
        return eventName;
    }
    
    @Override
    public String toString() {
        return eventName;
    }
    
    /**
     * Parse event type from string name
     */
    public static RegistryEventType fromString(String eventName) {
        for (RegistryEventType type : values()) {
            if (type.eventName.equalsIgnoreCase(eventName) || 
                type.name().equalsIgnoreCase(eventName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown registry event type: " + eventName);
    }
}