package com.telcobright.statemachine.websocket;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.events.EventTypeRegistry;
import com.telcobright.statemachineexamples.callmachine.CallContext;
import com.telcobright.statemachineexamples.callmachine.CallState;
import com.telcobright.statemachineexamples.callmachine.events.*;

import org.java_websocket.WebSocket;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * CallMachine runner with WebSocket server - refactored to use generic base class
 * This implementation extends the generic runner for CallContext specific functionality
 */
public class CallMachineRunnerWithWebServerRefactored 
        extends GenericWebSocketStateMachineRunner<CallContext, Void> {
    
    private static final int DEFAULT_PORT = 9999;
    private static final int DEFAULT_UI_PORT = 8091;
    
    public CallMachineRunnerWithWebServerRefactored() {
        this(DEFAULT_PORT, DEFAULT_UI_PORT);
    }
    
    public CallMachineRunnerWithWebServerRefactored(int port) {
        this(port, DEFAULT_UI_PORT);
    }
    
    public CallMachineRunnerWithWebServerRefactored(int wsPort, int uiPort) {
        super(wsPort, uiPort, "websocket-" + System.currentTimeMillis());
    }
    
    @Override
    protected void initializeStateMachine() {
        // Register event types first
        registerEventTypes();
        
        // Create persisting entity and machine
        persistingEntity = createPersistingEntity();
        machine = createStateMachine();
        
        // Setup state transition callback
        setupStateTransitionCallback();
        
        // Register and start machine
        registry.register(machineId, machine);
        machine.start();
        
        System.out.println("[CallMachine] Initialized with ID: " + machineId);
        System.out.println("[CallMachine] Machine ready with events: INCOMING_CALL, ANSWER, HANGUP, SESSION_PROGRESS, REJECT, BUSY, TIMEOUT");
        System.out.println("[CallMachine] Debug mode ENABLED - countdown timer will show for states with timeout");
    }
    
    @Override
    protected CallContext createPersistingEntity() {
        CallContext context = new CallContext(machineId, "+1-555-0001", "+1-555-0002");
        context.setStartTime(LocalDateTime.now());
        context.setCurrentState(CallState.IDLE.name());
        return context;
    }
    
    @Override
    protected GenericStateMachine<CallContext, Void> createStateMachine() {
        // Temporarily suppress System.out during initialization
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(new ByteArrayOutputStream()));
        
        try {
            GenericStateMachine<CallContext, Void> stateMachine = FluentStateMachineBuilder.<CallContext, Void>create(machineId)
                .initialState(CallState.IDLE)
                
                .state(CallState.IDLE)
                    .on(IncomingCall.class).to(CallState.RINGING)
                    .done()
                    
                .state(CallState.RINGING)
                    .timeout(30, com.telcobright.statemachine.timeout.TimeUnit.SECONDS, CallState.IDLE.name())
                    .on(Answer.class).to(CallState.CONNECTED)
                    .on(Hangup.class).to(CallState.IDLE)
                    .on(Reject.class).to(CallState.IDLE)
                    .stay(SessionProgress.class, (m, e) -> {
                        persistingEntity.setRingCount(persistingEntity.getRingCount() + 1);
                        System.out.println("[Stay] SESSION_PROGRESS in RINGING - ring #" + persistingEntity.getRingCount());
                    })
                    .done()
                    
                .state(CallState.CONNECTED)
                    .on(Hangup.class).to(CallState.IDLE)
                    .done()
                    
                .build();
            
            stateMachine.setPersistingEntity(persistingEntity);
            return stateMachine;
            
        } finally {
            // Restore original System.out
            System.setOut(originalOut);
        }
    }
    
    @Override
    protected void registerEventTypes() {
        EventTypeRegistry.register(IncomingCall.class, "INCOMING_CALL");
        EventTypeRegistry.register(Answer.class, "ANSWER");
        EventTypeRegistry.register(Hangup.class, "HANGUP");
        EventTypeRegistry.register(SessionProgress.class, "SESSION_PROGRESS");
        EventTypeRegistry.register(Reject.class, "REJECT");
        System.out.println("[Init] Registered event types in EventTypeRegistry");
    }
    
    @Override
    protected void handleMessage(WebSocket conn, JsonObject request) {
        // Handle EVENT messages from Live Mode UI
        if (request.has("type") && "EVENT".equals(request.get("type").getAsString())) {
            String eventType = request.get("eventType").getAsString();
            JsonObject payload = request.has("payload") ? request.getAsJsonObject("payload") : null;
            
            System.out.println("[WS] Received event: " + eventType);
            
            switch (eventType) {
                case "DIAL":
                case "INCOMING_CALL":
                    String callerNumber = payload != null && payload.has("phoneNumber") 
                        ? payload.get("phoneNumber").getAsString() 
                        : "+1-555-9999";
                    sendIncomingCall(callerNumber);
                    break;
                case "ANSWER":
                    sendAnswer();
                    break;
                case "HANGUP":
                    sendHangup();
                    break;
                case "BUSY":
                case "REJECT":
                case "TIMEOUT":
                    sendHangup(); // Treat as hangup for now
                    break;
                case "SESSION_PROGRESS":
                    int ringNumber = payload != null && payload.has("ringNumber") 
                        ? payload.get("ringNumber").getAsInt() 
                        : persistingEntity.getRingCount() + 1;
                    sendSessionProgress("v=0", ringNumber);
                    break;
                default:
                    System.out.println("[WS] Unhandled event type: " + eventType);
            }
            return;
        }
        
        // Handle legacy format (backward compatibility)
        if (request.has("action")) {
            String action = request.get("action").getAsString();
            switch (action) {
                case "INCOMING_CALL":
                    sendIncomingCall();
                    break;
                case "ANSWER":
                    sendAnswer();
                    break;
                case "HANGUP":
                    sendHangup();
                    break;
                case "SESSION_PROGRESS":
                    sendSessionProgress();
                    break;
                default:
                    System.out.println("[WS] Unknown action: " + action);
            }
        }
    }
    
    @Override
    protected JsonObject getEventMetadata() {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("machineId", machineId);
        metadata.addProperty("machineType", "CallMachine");
        
        // Add supported events
        JsonObject events = new JsonObject();
        events.addProperty("INCOMING_CALL", "Incoming call event");
        events.addProperty("ANSWER", "Answer the call");
        events.addProperty("HANGUP", "Hang up the call");
        events.addProperty("SESSION_PROGRESS", "Session progress (ringing)");
        events.addProperty("REJECT", "Reject the call");
        events.addProperty("BUSY", "Line busy");
        events.addProperty("TIMEOUT", "Call timeout");
        metadata.add("supportedEvents", events);
        
        return metadata;
    }
    
    @Override
    protected int getTimeoutForState(String state) {
        if (CallState.RINGING.name().equals(state)) {
            return 30; // 30 seconds timeout for RINGING state
        }
        return 0; // No timeout for other states
    }
    
    /**
     * Setup state transition callback for tracking
     */
    private void setupStateTransitionCallback() {
        final String[] previousState = {machine.getCurrentState()};
        
        machine.setOnStateTransition(newState -> {
            String oldState = previousState[0];
            
            // Check if this is a timeout transition
            if ("IDLE".equals(newState) && "RINGING".equals(oldState) && lastEventName == null) {
                lastEventName = "Timeout";
            }
            
            // Notify registry about transitions
            registry.notifyStateMachineEvent(machineId, oldState, newState, persistingEntity, null);
            
            // Only update previous state and restart timer if state actually changed
            if (!oldState.equals(newState)) {
                previousState[0] = newState;
                
                // Start countdown timer if debug mode is enabled
                if (debugMode) {
                    startCountdownTimer(newState);
                }
            }
            
            // Reset event name after notification
            lastEventName = null;
        });
    }
    
    // Call-specific event methods
    private void sendIncomingCall() {
        sendIncomingCall("+1-555-9999");
    }
    
    private void sendIncomingCall(String callerNumber) {
        if (machine != null) {
            lastEventName = "IncomingCall";
            machine.fire(new IncomingCall(callerNumber));
            System.out.println("[Event] INCOMING_CALL -> caller: " + callerNumber);
        }
    }
    
    private void sendAnswer() {
        if (machine != null) {
            lastEventName = "Answer";
            machine.fire(new Answer());
            persistingEntity.setConnectTime(LocalDateTime.now());
            System.out.println("[Event] ANSWER");
        }
    }
    
    private void sendHangup() {
        if (machine != null) {
            lastEventName = "Hangup";
            machine.fire(new Hangup());
            System.out.println("[Event] HANGUP");
        }
    }
    
    private void sendSessionProgress() {
        sendSessionProgress("v=0", persistingEntity.getRingCount() + 1);
    }
    
    private void sendSessionProgress(String sdp, int ringNumber) {
        if (machine != null) {
            lastEventName = "SessionProgress";
            String currentState = machine.getCurrentState();
            machine.fire(new SessionProgress(sdp, ringNumber));
            
            // For "stay" transitions, manually notify registry
            if (currentState.equals(machine.getCurrentState())) {
                registry.notifyStateMachineEvent(machineId, currentState, currentState, persistingEntity, null);
            }
            
            System.out.println("[Event] SESSION_PROGRESS -> SDP: " + sdp.substring(0, Math.min(20, sdp.length())) + 
                             "..., ring #" + ringNumber);
        }
    }
    
    /**
     * Main method for testing
     */
    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        
        CallMachineRunnerWithWebServerRefactored runner = new CallMachineRunnerWithWebServerRefactored(port);
        runner.start();
        
        System.out.println("\n=== CallMachine WebSocket Server (Refactored) ===");
        System.out.println("WebSocket: ws://localhost:" + port);
        System.out.println("UI: http://localhost:" + DEFAULT_UI_PORT);
        System.out.println("\nPress Ctrl+C to stop...\n");
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down...");
            runner.shutdown();
        }));
    }
}