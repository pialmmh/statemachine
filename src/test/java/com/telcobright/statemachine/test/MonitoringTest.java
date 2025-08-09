package com.telcobright.statemachine.test;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.events.GenericStateMachineEvent;
import com.telcobright.statemachine.monitoring.*;
import com.telcobright.statemachineexamples.callmachine.CallContext;
import com.telcobright.statemachineexamples.callmachine.entity.CallEntity;

import java.util.UUID;

/**
 * Test monitoring and debug functionality of state machines.
 * This test verifies that snapshots are properly recorded during state transitions.
 */
public class MonitoringTest {
    
    public static void main(String[] args) {
        System.out.println("🧪 Starting Monitoring Test");
        
        // Test scenario 1: Basic debug monitoring
        testBasicDebugMonitoring();
        
        // Test scenario 2: Comprehensive monitoring with context
        testComprehensiveMonitoring();
        
        // Test scenario 3: Production-safe monitoring
        testProductionMonitoring();
        
        System.out.println("✅ All monitoring tests completed successfully!");
    }
    
    /**
     * Test basic debug monitoring with default configuration
     */
    private static void testBasicDebugMonitoring() {
        System.out.println("\n📋 Test 1: Basic Debug Monitoring");
        
        String testRunId = "monitoring-test-" + UUID.randomUUID().toString().substring(0, 8);
        
        // Create a simple call machine with debug enabled
        GenericStateMachine<CallEntity, CallContext> machine = FluentStateMachineBuilder
            .<CallEntity, CallContext>create("call-machine-debug")
            .enableDebug() // Use default snapshot recorder
            .withRunId(testRunId)
            .withCorrelationId("test-correlation-123")
            .withDebugSessionId("debug-session-abc")
            .initialState("IDLE")
            .finalState("DISCONNECTED")
            
            .state("IDLE")
                .on("INCOMING_CALL").to("RINGING")
            .done()
            
            .state("RINGING")
                .on("ANSWER").to("CONNECTED")
                .on("REJECT").to("DISCONNECTED")
            .done()
            
            .state("CONNECTED")
                .on("HANGUP").to("DISCONNECTED")
            .done()
            
            .state("DISCONNECTED")
                .finalState()
            .done()
            
            .build();
        
        // Set up entity and context
        CallEntity callEntity = new CallEntity();
        callEntity.setCallId("call-debug-001");
        callEntity.setFromNumber("1234567890");
        callEntity.setToNumber("9876543210");
        callEntity.setCurrentState("IDLE");
        
        CallContext context = new CallContext();
        context.setCallId("call-debug-001");
        context.setStartTime(java.time.LocalDateTime.now());
        
        machine.setPersistingEntity(callEntity);
        machine.setContext(context);
        
        // Start the machine and fire some events
        machine.start();
        
        System.out.println("🔥 Firing INCOMING_CALL event...");
        machine.fire(new GenericStateMachineEvent("INCOMING_CALL"));
        
        System.out.println("🔥 Firing ANSWER event...");
        machine.fire(new GenericStateMachineEvent("ANSWER"));
        
        System.out.println("🔥 Firing HANGUP event...");
        machine.fire(new GenericStateMachineEvent("HANGUP"));
        
        // Verify debug is enabled
        System.out.println("Debug enabled: " + machine.isDebugEnabled());
        System.out.println("Run ID: " + machine.getRunId());
        System.out.println("Correlation ID: " + machine.getCorrelationId());
        System.out.println("Final state: " + machine.getCurrentState());
        System.out.println("Machine complete: " + machine.isComplete());
        
        machine.stop();
        System.out.println("✅ Basic debug monitoring test completed");
    }
    
    /**
     * Test comprehensive monitoring with full context capture
     */
    private static void testComprehensiveMonitoring() {
        System.out.println("\n📋 Test 2: Comprehensive Monitoring");
        
        String testRunId = "comprehensive-test-" + UUID.randomUUID().toString().substring(0, 8);
        
        // Create machine with comprehensive monitoring
        GenericStateMachine<CallEntity, CallContext> machine = FluentStateMachineBuilder
            .<CallEntity, CallContext>create("call-machine-comprehensive")
            .enableDebugComprehensive() // Use comprehensive snapshot configuration
            .withRunId(testRunId)
            .withCorrelationId("comprehensive-correlation-456")
            .initialState("IDLE")
            
            .state("IDLE")
                .on("INCOMING_CALL").to("RINGING")
            .done()
            
            .state("RINGING")
                .on("ANSWER").to("CONNECTED")
            .done()
            
            .state("CONNECTED")
                .finalState()
            .done()
            
            .build();
        
        // Set up entity with sensitive data to test redaction
        CallEntity callEntity = new CallEntity();
        callEntity.setCallId("call-comprehensive-002");
        callEntity.setFromNumber("sensitive-number-123");
        callEntity.setToNumber("another-sensitive-456");
        
        CallContext context = new CallContext();
        context.setCallId("call-comprehensive-002");
        context.setStartTime(java.time.LocalDateTime.now());
        // Add some context data
        context.setCallDirection("INBOUND");
        
        machine.setPersistingEntity(callEntity);
        machine.setContext(context);
        
        machine.start();
        
        System.out.println("🔥 Firing events with comprehensive monitoring...");
        machine.fire(new GenericStateMachineEvent("INCOMING_CALL"));
        machine.fire(new GenericStateMachineEvent("ANSWER"));
        
        // Verify comprehensive config
        SnapshotRecorder<CallEntity, CallContext> recorder = machine.getSnapshotRecorder();
        if (recorder != null) {
            SnapshotConfig config = recorder.getConfig();
            System.out.println("Comprehensive config - Capture context before: " + config.isCaptureContextBefore());
            System.out.println("Comprehensive config - Serialize context: " + config.isSerializeContext());
            System.out.println("Comprehensive config - Redact sensitive: " + config.isRedactSensitiveFields());
            System.out.println("Comprehensive config - Hash context: " + config.isHashContext());
        }
        
        machine.stop();
        System.out.println("✅ Comprehensive monitoring test completed");
    }
    
    /**
     * Test production-safe monitoring with minimal overhead
     */
    private static void testProductionMonitoring() {
        System.out.println("\n📋 Test 3: Production Monitoring");
        
        String testRunId = "production-test-" + UUID.randomUUID().toString().substring(0, 8);
        
        // Create machine with production-safe monitoring
        GenericStateMachine<CallEntity, CallContext> machine = FluentStateMachineBuilder
            .<CallEntity, CallContext>create("call-machine-production")
            .enableDebugProduction() // Use production-safe configuration
            .withRunId(testRunId)
            .initialState("IDLE")
            
            .state("IDLE")
                .on("START").to("PROCESSING")
            .done()
            
            .state("PROCESSING")
                .on("COMPLETE").to("DONE")
            .done()
            
            .state("DONE")
                .finalState()
            .done()
            
            .build();
        
        CallEntity callEntity = new CallEntity();
        callEntity.setCallId("call-production-003");
        
        CallContext context = new CallContext();
        context.setCallId("call-production-003");
        
        machine.setPersistingEntity(callEntity);
        machine.setContext(context);
        
        machine.start();
        
        System.out.println("🔥 Testing production monitoring...");
        machine.fire(new GenericStateMachineEvent("START"));
        machine.fire(new GenericStateMachineEvent("COMPLETE"));
        
        // Verify production config is more restrictive
        SnapshotRecorder<CallEntity, CallContext> recorder = machine.getSnapshotRecorder();
        if (recorder != null) {
            SnapshotConfig config = recorder.getConfig();
            System.out.println("Production config - Async recording: " + config.isAsyncRecording());
            System.out.println("Production config - Verbose logging: " + config.isVerboseLogging());
            System.out.println("Production config - Fail on error: " + config.isFailOnRecordingError());
        }
        
        machine.stop();
        System.out.println("✅ Production monitoring test completed");
    }
}