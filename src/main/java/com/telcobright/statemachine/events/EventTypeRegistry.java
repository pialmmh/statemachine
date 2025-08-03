package com.telcobright.statemachine.events;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Master registry for event types
 * This provides a pluggable architecture for domain-specific event registries
 */
public class EventTypeRegistry {
    
    private static final Map<Class<? extends StateMachineEvent>, String> eventTypeMap = new ConcurrentHashMap<>();
    
    /**
     * Get event type string for any event class
     */
    public static String getEventType(Class<? extends StateMachineEvent> eventClass) {
        String eventType = eventTypeMap.get(eventClass);
        if (eventType != null) {
            return eventType;
        }
        
        // Fallback to class name if not found in registry
        return eventClass.getSimpleName().toUpperCase();
    }
    
    /**
     * Register a new event type
     */
    public static void register(Class<? extends StateMachineEvent> eventClass, String eventType) {
        eventTypeMap.put(eventClass, eventType);
    }
    
    /**
     * Clear all registered event types
     */
    public static void clear() {
        eventTypeMap.clear();
    }
    
    /**
     * Check if an event class is registered
     */
    public static boolean isRegistered(Class<? extends StateMachineEvent> eventClass) {
        return eventTypeMap.containsKey(eventClass);
    }
}
