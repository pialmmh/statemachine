package com.telcobright.statemachine.websocket;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.events.EventTypeRegistry;
import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachineexamples.callmachine.CallContext;
import com.telcobright.statemachineexamples.callmachine.CallState;
import com.telcobright.statemachineexamples.callmachine.events.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * Example implementation of CallMachine runner that properly uses registry's debug modes
 * Demonstrates best practice: Debug modes are set programmatically in Java code
 * This example has live debug hardcoded to show WebSocket monitoring
 */
public class CallMachineRunnerProper {
    
    // Hardcoded port numbers
    private static final int WS_PORT = 9999;
    
    private final StateMachineRegistry registry;
    private final Map<String, GenericStateMachine<CallContext, Void>> machines = new ConcurrentHashMap<>();
    private final Map<String, CallContext> contexts = new ConcurrentHashMap<>();
    
    /**
     * Create runner with hardcoded settings
     */
    public CallMachineRunnerProper() {
        // Create registry with hardcoded WebSocket port
        this.registry = new StateMachineRegistry(null, WS_PORT);
        
        // Enable live debug mode (hardcoded)
        registry.enableLiveDebug(WS_PORT);
        System.out.println("🔴 Live debugging ENABLED - WebSocket server started on port " + WS_PORT);
        
        // Initialize state machines
        initializeStateMachines();
    }
    
    private void initializeStateMachines() {
        System.out.println("\n[Init] Initializing state machines...");
        registerEventTypes();
        
        // Create 3 CallMachine instances
        createAndRegisterMachine("call-001", "+1-555-0001", "+1-555-1001");
        createAndRegisterMachine("call-002", "+1-555-0002", "+1-555-1002");
        createAndRegisterMachine("call-003", "+1-555-0003", "+1-555-1003");
        
        System.out.println("[Init] Created 3 CallMachine instances: call-001, call-002, call-003");
    }
    
    private void createAndRegisterMachine(String machineId, String caller, String callee) {
        CallContext context = new CallContext(machineId, caller, callee);
        
        GenericStateMachine<CallContext, Void> machine = FluentStateMachineBuilder.<CallContext, Void>create(machineId)
            .initialState(CallState.IDLE)
            
            .state(CallState.IDLE)
                .on(IncomingCall.class).to(CallState.RINGING)
                .done()
                
            .state(CallState.RINGING)
                .timeout(30, com.telcobright.statemachine.timeout.TimeUnit.SECONDS, CallState.IDLE.name())
                .on(Answer.class).to(CallState.CONNECTED)
                .on(Hangup.class).to(CallState.IDLE)
                .stay(SessionProgress.class, (m, e) -> {
                    context.setRingCount(context.getRingCount() + 1);
                })
                .done()
                
            .state(CallState.CONNECTED)
                .on(Hangup.class).to(CallState.IDLE)
                .done()
            .build();
        
        machine.setPersistingEntity(context);
        
        // Register with the registry - debug modes are automatically applied if enabled
        registry.register(machineId, machine);
        machine.start();
        
        // Store references
        machines.put(machineId, machine);
        contexts.put(machineId, context);
        
        System.out.println("[Init] Registered CallMachine: " + machineId);
    }
    
    private void registerEventTypes() {
        EventTypeRegistry.register(IncomingCall.class, "INCOMING_CALL");
        EventTypeRegistry.register(Answer.class, "ANSWER");
        EventTypeRegistry.register(Hangup.class, "HANGUP");
        EventTypeRegistry.register(SessionProgress.class, "SESSION_PROGRESS");
        EventTypeRegistry.register(Reject.class, "REJECT");
        System.out.println("[Init] Registered event types");
    }
    
    /**
     * Send event to a specific machine
     */
    public void sendEvent(String machineId, StateMachineEvent event) {
        GenericStateMachine<CallContext, Void> machine = machines.get(machineId);
        if (machine != null) {
            machine.fire(event);
            System.out.println("[Event] Sent " + event.getClass().getSimpleName() + " to " + machineId);
        } else {
            System.err.println("[Event] Machine not found: " + machineId);
        }
    }
    
    /**
     * Get list of machine IDs
     */
    public Set<String> getMachineIds() {
        return new HashSet<>(machines.keySet());
    }
    
    /**
     * Get machine state
     */
    public String getMachineState(String machineId) {
        GenericStateMachine<CallContext, Void> machine = machines.get(machineId);
        return machine != null ? machine.getCurrentState() : null;
    }
    
    /**
     * Shutdown the runner
     */
    public void shutdown() {
        System.out.println("\n[Shutdown] Shutting down...");
        
        // Shutdown registry (this stops WebSocket if it was running)
        registry.shutdown();
        
        System.out.println("[Shutdown] Complete");
    }
    
    public static void main(String[] args) {
        // Create and run with all settings hardcoded
        CallMachineRunnerProper runner = new CallMachineRunnerProper();
        
        System.out.println("\n==========================================");
        System.out.println("   CallMachine Runner with Live Debug");
        System.out.println("==========================================");
        System.out.println("🔌 WebSocket API: ws://localhost:" + WS_PORT);
        System.out.println("==========================================");
        System.out.println("");
        System.out.println("To monitor the state machines:");
        System.out.println("1. Start the React UI: cd statemachine-ui-react && npm start");
        System.out.println("2. Open http://localhost:4001 in your browser");
        System.out.println("3. Click 'Live Viewer' to connect to this WebSocket");
        System.out.println("");
        System.out.println("Press Ctrl+C to stop\n");
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(runner::shutdown));
        
        // Keep running
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            runner.shutdown();
        }
    }
}