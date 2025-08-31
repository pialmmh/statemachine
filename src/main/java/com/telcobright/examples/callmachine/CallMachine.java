package com.telcobright.examples.callmachine;

import com.telcobright.statemachine.*;
import com.telcobright.examples.callmachine.events.*;
import com.telcobright.examples.callmachine.entity.CallEntity;
import com.telcobright.statemachine.events.GenericStateMachineEvent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Production-ready Call State Machine Implementation
 * 
 * Implements the ESL (Event Socket Library) call flow:
 * ADMISSION → TRYING → RINGING → CONNECTED → HUNGUP
 * 
 * Features:
 * - High-performance: Supports 2000+ calls per second
 * - Auto-creation: Machines created automatically on INCOMING_CALL
 * - Complete lifecycle management with proper state transitions
 * - Production-grade error handling and logging
 */
public class CallMachine {
    
    private final StateMachineLibrary<CallEntity, CallContext> library;
    
    /**
     * Create a production-ready call machine system
     * 
     * @param systemId Unique identifier for this call system
     * @param targetCPS Target calls per second (performance tuning)
     * @param maxConcurrentCalls Maximum concurrent calls to support
     */
    public CallMachine(String systemId, int targetCPS, int maxConcurrentCalls) {
        this(systemId, targetCPS, maxConcurrentCalls, false, 0);
    }
    
    /**
     * Create a call machine system with optional debug mode
     * 
     * @param systemId Unique identifier for this call system
     * @param targetCPS Target calls per second (performance tuning)
     * @param maxConcurrentCalls Maximum concurrent calls to support
     * @param debugMode Enable debug mode with WebSocket monitoring
     * @param webSocketPort WebSocket server port (used if debugMode is true)
     */
    public CallMachine(String systemId, int targetCPS, int maxConcurrentCalls, 
                       boolean debugMode, int webSocketPort) {
        this.library = createLibrary(systemId, targetCPS, maxConcurrentCalls, debugMode, webSocketPort);
    }
    
    /**
     * Create the state machine library with full configuration
     */
    private StateMachineLibrary<CallEntity, CallContext> createLibrary(
            String systemId, int targetCPS, int maxConcurrentCalls) {
        return createLibrary(systemId, targetCPS, maxConcurrentCalls, false, 0);
    }
    
    /**
     * Create the state machine library with full configuration and optional debug
     */
    private StateMachineLibrary<CallEntity, CallContext> createLibrary(
            String systemId, int targetCPS, int maxConcurrentCalls,
            boolean debugMode, int webSocketPort) {
        
        var builder = StateMachineLibraryBuilder.<CallEntity, CallContext>create(systemId);
        
        // Configure registry with performance and optional debug
        var registryConfig = builder.registryConfig()
            .targetTps(targetCPS * 5)  // 5 events per call average
            .maxConcurrentMachines(maxConcurrentCalls)
            .enablePerformanceMetrics(true)
            .timeoutThreads(8);
        
        // Add debug mode if requested
        if (debugMode) {
            registryConfig.webSocketPort(webSocketPort);
        }
        
        // State machine template
        return registryConfig.done()
            .stateMachineTemplate()
                .initialState(CallState.ADMISSION.name())
                
                // ADMISSION - Initial state for business logic validation
                .state(CallState.ADMISSION.name())
                    .on("INCOMING_CALL").to(CallState.ADMISSION.name())  // Stay in admission
                    .on("ADMISSION_SUCCESS").to(CallState.TRYING.name())
                    .on("ADMISSION_FAILURE").to(CallState.HUNGUP.name())
                    .on("HANGUP").to(CallState.HUNGUP.name())
                    .done()
                
                // TRYING - Attempting to reach destination
                .state(CallState.TRYING.name())
                    .on("RING").to(CallState.RINGING.name())
                    .on("BUSY").to(CallState.HUNGUP.name())
                    .on("HANGUP").to(CallState.HUNGUP.name())
                    .done()
                
                // RINGING - Call is ringing at destination
                .state(CallState.RINGING.name())
                    .on("ANSWER").to(CallState.CONNECTED.name())
                    .on("NO_ANSWER").to(CallState.HUNGUP.name())
                    .on("HANGUP").to(CallState.HUNGUP.name())
                    .done()
                
                // CONNECTED - Active call
                .state(CallState.CONNECTED.name())
                    .on("HANGUP").to(CallState.HUNGUP.name())
                    .on("TRANSFER").to(CallState.TRYING.name())
                    .done()
                
                // HUNGUP - Final state
                .state(CallState.HUNGUP.name())
                    .finalState()
                    .onEntry(machine -> {
                        // Auto-cleanup after reaching final state
                        String machineId = machine.getId();
                        CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS)
                            .execute(() -> removeMachine(machineId));
                    })
                    .done()
                .done()
            
            // Auto-creation on INCOMING_CALL
            .newMachineCreationEvent("INCOMING_CALL",
                () -> new CallEntity(),
                () -> new CallContext())
            
            // Callbacks
            .onMachineCreated(id -> {
                System.out.printf("[CallMachine] Call %s created%n", id);
            })
            .onMachineCreationFailed((id, reason) -> {
                System.err.printf("[CallMachine] Failed to create call %s: %s%n", id, reason);
            })
            
            .build();
    }
    
    /**
     * Process an incoming call
     * 
     * @param callId Unique call identifier
     * @param fromNumber Caller number
     * @param toNumber Called number
     * @return true if call was accepted
     */
    public boolean processIncomingCall(String callId, String fromNumber, String toNumber) {
        // Send INCOMING_CALL event - will auto-create machine if needed
        IncomingCall event = new IncomingCall(fromNumber, toNumber);
        return library.sendEvent(callId, event);
    }
    
    /**
     * Accept a call after admission
     */
    public boolean acceptCall(String callId) {
        return library.sendEvent(callId, new GenericStateMachineEvent("ADMISSION_SUCCESS"));
    }
    
    /**
     * Reject a call during admission
     */
    public boolean rejectCall(String callId, String reason) {
        return library.sendEvent(callId, new GenericStateMachineEvent("ADMISSION_FAILURE", reason));
    }
    
    /**
     * Signal that call is ringing
     */
    public boolean ringCall(String callId) {
        return library.sendEvent(callId, new GenericStateMachineEvent("RING"));
    }
    
    /**
     * Answer a ringing call
     */
    public boolean answerCall(String callId) {
        return library.sendEvent(callId, new GenericStateMachineEvent("ANSWER"));
    }
    
    /**
     * Hangup a call
     */
    public boolean hangupCall(String callId) {
        return library.sendEvent(callId, new GenericStateMachineEvent("HANGUP"));
    }
    
    /**
     * Get current state of a call
     */
    public String getCallState(String callId) {
        return library.getMachineState(callId);
    }
    
    /**
     * Check if a call exists
     */
    public boolean callExists(String callId) {
        return library.machineExists(callId);
    }
    
    /**
     * Get number of active calls
     */
    public int getActiveCallCount() {
        return library.getActiveMachineCount();
    }
    
    /**
     * Get performance statistics
     */
    public StateMachineLibrary.LibraryPerformanceStats getPerformanceStats() {
        return library.getPerformanceStats();
    }
    
    /**
     * Remove a machine from the registry
     */
    private void removeMachine(String machineId) {
        library.removeMachine(machineId);
    }
    
    /**
     * Shutdown the call machine system
     */
    public void shutdown() {
        System.out.println("[CallMachine] Shutting down...");
        library.shutdown();
    }
}