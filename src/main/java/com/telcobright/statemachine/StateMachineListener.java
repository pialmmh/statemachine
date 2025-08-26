package com.telcobright.statemachine;

/**
 * Listener interface for state machine events
 * @param <TPersistentContext> The persistent context type
 * @param <TVolatileContext> The volatile context type
 */
public interface StateMachineListener<TPersistentContext extends StateMachineContextEntity, TVolatileContext> {
    
    /**
     * Called when a new state machine is registered
     * @param machineId The ID of the registered machine
     */
    void onRegistryCreate(String machineId);
    
    /**
     * Called when a state machine is rehydrated
     * @param machineId The ID of the rehydrated machine
     */
    void onRegistryRehydrate(String machineId);
    
    /**
     * Called when a state machine is removed from registry
     * @param machineId The ID of the removed machine
     */
    void onRegistryRemove(String machineId);
    
    /**
     * Called when a state machine transitions between states
     * @param machineId The ID of the state machine
     * @param oldState The previous state
     * @param newState The new state
     * @param contextEntity The persistent context
     * @param volatileContext The volatile context
     */
    void onStateMachineEvent(String machineId, String oldState, String newState, 
                             TPersistentContext contextEntity, TVolatileContext volatileContext);
    
    /**
     * Called when an event is ignored (no transition or action defined)
     * @param machineId The ID of the state machine
     * @param currentState The current state
     * @param eventType The type of event that was ignored
     * @param contextEntity The persistent context
     * @param volatileContext The volatile context
     */
    default void onEventIgnored(String machineId, String currentState, String eventType,
                                TPersistentContext contextEntity, TVolatileContext volatileContext) {
        // Default implementation does nothing - backward compatibility
    }
}