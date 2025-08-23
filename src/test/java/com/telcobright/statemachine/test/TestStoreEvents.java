package com.telcobright.statemachine.test;

import com.telcobright.statemachine.StateMachineRegistry;
import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.FluentStateMachineBuilder;
import com.telcobright.statemachine.StateMachineContextEntity;
import com.telcobright.statemachine.timeout.TimeUnit;
import com.telcobright.statemachineexamples.callmachine.CallState;
import com.telcobright.statemachineexamples.callmachine.events.*;
import java.time.LocalDateTime;

public class TestStoreEvents {
    
    static class TestContext implements StateMachineContextEntity<String> {
        private String currentState = CallState.IDLE.name();
        private LocalDateTime lastStateChange = LocalDateTime.now();
        private boolean complete = false;
        
        @Override
        public String getCurrentState() { return currentState; }
        
        @Override
        public void setCurrentState(String state) {
            this.currentState = state;
            this.lastStateChange = LocalDateTime.now();
        }
        
        @Override
        public LocalDateTime getLastStateChange() { return lastStateChange; }
        
        @Override
        public void setLastStateChange(LocalDateTime time) { this.lastStateChange = time; }
        
        @Override
        public boolean isComplete() { return complete; }
        
        @Override
        public void setComplete(boolean complete) { this.complete = complete; }
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println("Starting Test Store Events...");
        
        // Register event types properly
        com.telcobright.statemachine.events.EventTypeRegistry.register(IncomingCall.class, "INCOMING_CALL");
        com.telcobright.statemachine.events.EventTypeRegistry.register(Answer.class, "ANSWER");
        com.telcobright.statemachine.events.EventTypeRegistry.register(Hangup.class, "HANGUP");
        com.telcobright.statemachine.events.EventTypeRegistry.register(Reject.class, "REJECT");
        com.telcobright.statemachine.events.EventTypeRegistry.register(SessionProgress.class, "SESSION_PROGRESS");
        
        // Create registry with debug mode
        StateMachineRegistry registry = new StateMachineRegistry();
        registry.enableDebugMode(9999);
        System.out.println("WebSocket server started on port 9999");
        
        // Create a test call machine with strongly typed events
        GenericStateMachine<TestContext, Void> machine = FluentStateMachineBuilder.<TestContext, Void>create("test-call-001")
            .initialState(CallState.IDLE)
            
            .state(CallState.IDLE)
                .on(IncomingCall.class)
                    .to(CallState.RINGING)
                .done()
                
            .state(CallState.RINGING)
                .timeout(30, TimeUnit.SECONDS, CallState.IDLE.name())
                .on(Answer.class)
                    .to(CallState.CONNECTED)
                .on(Hangup.class)
                    .to(CallState.IDLE)
                .on(Reject.class)
                    .to(CallState.IDLE)
                .stay(SessionProgress.class, (m, e) -> {
                    System.out.println("Session progress received");
                })
                .done()
                
            .state(CallState.CONNECTED)
                .offline()
                .timeout(120, TimeUnit.SECONDS, CallState.IDLE.name())
                .on(Hangup.class)
                    .to(CallState.IDLE)
                .done()
                
            .build();
        
        registry.register("test-call-001", machine);
        System.out.println("Machine test-call-001 registered");
        
        // Wait a moment for React app to connect
        Thread.sleep(2000);
        
        // Send some events to generate state changes
        System.out.println("\nSending INCOMING_CALL event...");
        machine.fire(new IncomingCall("+1-555-0123"));
        Thread.sleep(1000);
        
        System.out.println("Sending ANSWER event...");
        machine.fire(new Answer());
        Thread.sleep(1000);
        
        System.out.println("Sending HANGUP event...");
        machine.fire(new Hangup());
        Thread.sleep(1000);
        
        System.out.println("\nKeeping server running. Press Ctrl+C to stop.");
        
        // Keep running
        while (true) {
            Thread.sleep(5000);
            System.out.println("Server still running on port 9999...");
        }
    }
}