package com.telcobright.statemachine.examples;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.events.EventTypeRegistry;
import com.telcobright.statemachineexamples.callmachine.CallContext;
import com.telcobright.statemachineexamples.callmachine.CallState;
import com.telcobright.statemachineexamples.callmachine.events.*;

/**
 * Test showing how WebSocket server broadcasts complete status
 * 
 * The WebSocket server sends COMPLETE_STATUS messages:
 * 1. When a client connects
 * 2. After each state machine event
 * 3. After registry create/remove events
 * 4. Periodically every 5 seconds
 */
public class TestCompleteStatusBroadcast {
    
    public static void main(String[] args) throws Exception {
        // Create registry with WebSocket support
        StateMachineRegistry registry = new StateMachineRegistry(null, 9999);
        
        // Enable live debugging with WebSocket server
        registry.enableLiveDebug(9999);
        
        System.out.println("\n=== Complete Status Broadcast Test ===");
        System.out.println("WebSocket server running on ws://localhost:9999");
        System.out.println("\nThe server will broadcast COMPLETE_STATUS containing:");
        System.out.println("- Registry information (debug mode, machine count, etc.)");
        System.out.println("- All machines with their current states");
        System.out.println("- Full context data for each machine");
        System.out.println("- Statistics summary");
        System.out.println("\nConnect a WebSocket client to see the messages...\n");
        
        // Register event types
        registerEventTypes();
        
        // Create first machine
        String machineId1 = "call-001";
        CallContext context1 = new CallContext(machineId1, "+1-555-1111", "+1-555-2222");
        GenericStateMachine<CallContext, Void> machine1 = createCallMachine(machineId1, context1);
        registry.register(machineId1, machine1);
        machine1.start();
        
        System.out.println("Created machine: " + machineId1);
        Thread.sleep(2000);
        
        // Fire event on first machine
        System.out.println("Firing INCOMING_CALL on " + machineId1);
        machine1.fire(new IncomingCall("+1-555-9999"));
        Thread.sleep(2000);
        
        // Create second machine
        String machineId2 = "call-002";
        CallContext context2 = new CallContext(machineId2, "+1-555-3333", "+1-555-4444");
        GenericStateMachine<CallContext, Void> machine2 = createCallMachine(machineId2, context2);
        registry.register(machineId2, machine2);
        machine2.start();
        
        System.out.println("Created machine: " + machineId2);
        Thread.sleep(2000);
        
        // Fire events on both machines
        System.out.println("Firing ANSWER on " + machineId1);
        machine1.fire(new Answer());
        
        System.out.println("Firing INCOMING_CALL on " + machineId2);
        machine2.fire(new IncomingCall("+1-555-8888"));
        Thread.sleep(2000);
        
        // Show current status
        System.out.println("\n=== Current Status ===");
        System.out.println("Registry has " + registry.getActiveMachineCount() + " machines");
        System.out.println(machineId1 + " state: " + machine1.getCurrentState());
        System.out.println(machineId2 + " state: " + machine2.getCurrentState());
        
        System.out.println("\n=== COMPLETE_STATUS is being broadcast ===");
        System.out.println("Check your WebSocket client for messages");
        System.out.println("The status includes all machine and registry information");
        System.out.println("\nPress Enter to shutdown...");
        
        System.in.read();
        
        // Shutdown
        registry.shutdown();
        System.out.println("Test complete.");
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