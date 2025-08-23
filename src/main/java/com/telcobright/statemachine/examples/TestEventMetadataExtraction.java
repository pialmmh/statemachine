package com.telcobright.statemachine.examples;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.events.EventTypeRegistry;
import com.telcobright.statemachineexamples.callmachine.CallContext;
import com.telcobright.statemachineexamples.callmachine.CallState;
import com.telcobright.statemachineexamples.callmachine.events.*;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Test showing how event metadata is extracted from a single state machine
 * 
 * Demonstrates:
 * 1. Extracting supported events using reflection
 * 2. Determining event schemas and transitions
 * 3. Generating metadata for UI consumption
 */
public class TestEventMetadataExtraction {
    
    public static void main(String[] args) throws Exception {
        // Create registry with WebSocket support
        StateMachineRegistry registry = new StateMachineRegistry(null, 9999);
        
        // Enable live debugging with WebSocket server
        registry.enableDebugMode(9999);
        
        System.out.println("\n=== Event Metadata Extraction Test ===");
        System.out.println("WebSocket server running on ws://localhost:9999");
        System.out.println("\nThe server will send EVENT_METADATA_UPDATE containing:");
        System.out.println("- Supported events for each machine");
        System.out.println("- Event display names and types");
        System.out.println("- Event schemas (field definitions)");
        System.out.println("- Possible state transitions for each event");
        System.out.println("\nConnect a WebSocket client to see the metadata...\n");
        
        // Register event types
        registerEventTypes();
        
        // Create a call machine with various events
        String machineId = "call-metadata-test";
        CallContext context = new CallContext(machineId, "+1-555-1111", "+1-555-2222");
        GenericStateMachine<CallContext, Void> machine = createCallMachine(machineId, context);
        
        System.out.println("Creating single machine with events: INCOMING_CALL, ANSWER, HANGUP, SESSION_PROGRESS");
        registry.register(machineId, machine);
        machine.start();
        
        // Extract and display metadata locally
        JsonObject metadata = registry.extractMachineEventMetadata(machine);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        System.out.println("\n=== Extracted Event Metadata ===");
        System.out.println(gson.toJson(metadata));
        
        System.out.println("\n=== How the UI uses this metadata ===");
        System.out.println("1. Populates event dropdown with available events");
        System.out.println("2. Shows event parameter hints in payload field");
        System.out.println("3. Validates events before sending");
        System.out.println("4. Shows possible state transitions");
        
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