package com.telcobright.statemachine.events;

import com.telcobright.statemachineexamples.callmachine.events.CallEventTypeRegistry;

/**
 * Master registry that delegates to domain-specific registries
 * This allows for organized event management by domain (call, sms, etc.)
 */
public class EventTypeRegistry {
    
    /**
     * Get event type string for any event class by delegating to appropriate domain registry
     */
    public static String getEventType(Class<? extends StateMachineEvent> eventClass) {
        // Try call events first
        if (CallEventTypeRegistry.isCallEvent(eventClass)) {
            return CallEventTypeRegistry.getEventType(eventClass);
        }
        
        // Add other domain registries here as they are created:
        // if (SmsEventTypeRegistry.isSmsEvent(eventClass)) {
        //     return SmsEventTypeRegistry.getEventType(eventClass);
        // }
        
        // Fallback to class name if not found in any domain registry
        return eventClass.getSimpleName().toUpperCase();
    }
    
    /**
     * Register a new event type in the appropriate domain
     * This method can be enhanced to auto-detect domain based on package
     */
    public static void register(Class<? extends StateMachineEvent> eventClass, String eventType) {
        // Determine domain by package name
        String packageName = eventClass.getPackage().getName();
        
        if (packageName.contains(".call")) {
            CallEventTypeRegistry.register(eventClass, eventType);
        }
        // Add other domain routing here:
        // else if (packageName.contains(".sms")) {
        //     SmsEventTypeRegistry.register(eventClass, eventType);
        // }
        else {
            throw new IllegalArgumentException("Unknown event domain for class: " + eventClass.getName());
        }
    }
}
