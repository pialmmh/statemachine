package com.telcobright.statemachine.examples;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.events.EventTypeRegistry;
import com.telcobright.statemachineexamples.callmachine.CallContext;
import com.telcobright.statemachineexamples.callmachine.CallState;
import com.telcobright.statemachineexamples.callmachine.events.*;

/**
 * Example showing how to use StateMachineRegistry with WebSocket support
 * When debug mode is enabled with WebSocket, the registry automatically starts
 * a WebSocket server for real-time monitoring
 */
public class RegistryWithWebSocketExample {
    
    public static void main(String[] args) {
        // Create registry with optional custom WebSocket port
        int webSocketPort = args.length > 0 ? Integer.parseInt(args[0]) : 9999;
        StateMachineRegistry registry = new StateMachineRegistry(null, webSocketPort);
        
        // Enable live debugging with WebSocket server
        // This automatically starts WebSocket server on the specified port
        registry.enableDebugMode(webSocketPort);
        
        System.out.println("\n=== Registry with WebSocket Debug Mode ===");
        System.out.println("WebSocket server is running on port: " + registry.getWebSocketPort());
        System.out.println("Connect your Live Mode UI to ws://localhost:" + registry.getWebSocketPort());
        System.out.println();
        
        // Register event types
        registerEventTypes();
        
        // Create a CallMachine
        String machineId = "call-" + System.currentTimeMillis();
        CallContext context = new CallContext(machineId, "+1-555-0001", "+1-555-0002");
        
        GenericStateMachine<CallContext, Void> machine = createCallMachine(machineId, context);
        
        // Register the machine - debug mode is automatically applied
        registry.register(machineId, machine);
        machine.start();
        
        System.out.println("Created CallMachine: " + machineId);
        System.out.println("Current state: " + machine.getCurrentState());
        System.out.println();
        
        // Simulate some events
        System.out.println("Firing INCOMING_CALL event...");
        machine.fire(new IncomingCall("+1-555-9999"));
        System.out.println("State after INCOMING_CALL: " + machine.getCurrentState());
        
        // Wait a bit
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        System.out.println("Firing ANSWER event...");
        machine.fire(new Answer());
        System.out.println("State after ANSWER: " + machine.getCurrentState());
        
        // Keep running for monitoring
        System.out.println("\n=== Machine is running ===");
        System.out.println("Open http://localhost:8091 and switch to Live Mode");
        System.out.println("The WebSocket is already running and broadcasting events");
        System.out.println("Press Enter to shutdown...");
        
        try {
            System.in.read();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Shutdown registry (this also stops the WebSocket server)
        registry.shutdown();
        System.out.println("Registry and WebSocket server shutdown complete.");
    }
    
    private static GenericStateMachine<CallContext, Void> createCallMachine(String machineId, CallContext context) {
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
        return machine;
    }
    
    private static void registerEventTypes() {
        EventTypeRegistry.register(IncomingCall.class, "INCOMING_CALL");
        EventTypeRegistry.register(Answer.class, "ANSWER");
        EventTypeRegistry.register(Hangup.class, "HANGUP");
        EventTypeRegistry.register(SessionProgress.class, "SESSION_PROGRESS");
    }
}