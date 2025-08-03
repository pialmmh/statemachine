package com.telcobright.statemachineexamples.callmachine.events;

import java.util.HashMap;
import java.util.Map;

import com.telcobright.statemachine.events.StateMachineEvent;

/**
 * Registry to map call event classes to their event type strings without reflection
 * Domain-specific registry for call-related events
 */
public class CallEventTypeRegistry {
    private static final Map<Class<? extends StateMachineEvent>, String> CALL_EVENT_TYPE_MAP = new HashMap<>();
    
    static {
        // Register all call-related event types
        CALL_EVENT_TYPE_MAP.put(Answer.class, Answer.EVENT_TYPE);
        CALL_EVENT_TYPE_MAP.put(Hangup.class, Hangup.EVENT_TYPE);
        CALL_EVENT_TYPE_MAP.put(IncomingCall.class, IncomingCall.EVENT_TYPE);
        CALL_EVENT_TYPE_MAP.put(Timeout.class, Timeout.EVENT_TYPE);
        CALL_EVENT_TYPE_MAP.put(SessionProgress.class, SessionProgress.EVENT_TYPE);
    }
    
    /**
     * Get event type string for a call event class without reflection
     */
    public static String getEventType(Class<? extends StateMachineEvent> eventClass) {
        String eventType = CALL_EVENT_TYPE_MAP.get(eventClass);
        if (eventType != null) {
            return eventType;
        }
        
        // Fallback to class name if not registered
        return eventClass.getSimpleName().toUpperCase();
    }
    
    /**
     * Register a new call event type
     */
    public static void register(Class<? extends StateMachineEvent> eventClass, String eventType) {
        CALL_EVENT_TYPE_MAP.put(eventClass, eventType);
    }
    
    /**
     * Check if an event class is registered in this call domain
     */
    public static boolean isCallEvent(Class<? extends StateMachineEvent> eventClass) {
        return CALL_EVENT_TYPE_MAP.containsKey(eventClass);
    }
}
