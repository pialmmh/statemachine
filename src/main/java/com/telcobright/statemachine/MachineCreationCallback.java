package com.telcobright.statemachine;

/**
 * Callback interface for handling machine creation responses
 * This allows clients to receive notifications when machine creation succeeds or fails
 * Perfect for ESL/FreeSWITCH responses where we need to notify the client about call processing
 */
public interface MachineCreationCallback {
    
    /**
     * Called when machine creation succeeds
     * 
     * @param machineId The ID of the successfully created machine
     * @param machine The created state machine instance
     */
    void onMachineCreated(String machineId, GenericStateMachine<?, ?> machine);
    
    /**
     * Called when machine creation fails
     * 
     * @param machineId The ID of the machine that failed to be created
     * @param reason The reason for the failure (e.g., "CAPACITY_LIMIT_REACHED", "INVALID_CONFIGURATION")
     * @param exception The exception that caused the failure, if any
     */
    void onMachineCreationFailed(String machineId, String reason, Throwable exception);
    
    /**
     * Default implementation for successful creation - does nothing
     * Subclasses can override this to provide custom success handling
     */
    default void onSuccess(String machineId, GenericStateMachine<?, ?> machine) {
        onMachineCreated(machineId, machine);
    }
    
    /**
     * Default implementation for failed creation - does nothing
     * Subclasses can override this to provide custom failure handling
     */
    default void onFailure(String machineId, String reason, Throwable exception) {
        onMachineCreationFailed(machineId, reason, exception);
    }
}