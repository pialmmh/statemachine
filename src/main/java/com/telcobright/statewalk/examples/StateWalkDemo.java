package com.telcobright.statewalk.examples;

import com.telcobright.splitverse.config.ShardConfig;
import com.telcobright.statemachine.*;
import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statewalk.core.*;
import com.telcobright.statewalk.playback.TransitionRecord;

import java.math.BigDecimal;
import java.util.List;

/**
 * Demonstration of State-Walk capabilities with multi-entity persistence and playback
 */
public class StateWalkDemo {

    // Define states for call state machine
    private static final String IDLE = "IDLE";
    private static final String RINGING = "RINGING";
    private static final String CONNECTED = "CONNECTED";
    private static final String HUNGUP = "HUNGUP";

    // Define events
    static class IncomingCallEvent implements StateMachineEvent {}
    static class AnswerEvent implements StateMachineEvent {}
    static class HangupEvent implements StateMachineEvent {}

    public static void main(String[] args) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   STATE-WALK DEMONSTRATION");
        System.out.println("   Multi-Entity Persistence & Event Playback");
        System.out.println("=".repeat(80) + "\n");

        // Create shard configuration (database name will be overridden by registry name)
        ShardConfig shardConfig = ShardConfig.builder()
            .shardId("primary")
            .host("127.0.0.1")
            .port(3306)
            .username("root")
            .password("123456")
            .connectionPoolSize(10)
            .enabled(true)
            .build();

        try {
            // Test 1: Multi-Entity Mode with Graph Persistence
            System.out.println("━".repeat(80));
            System.out.println("TEST 1: MULTI-ENTITY MODE WITH GRAPH PERSISTENCE");
            System.out.println("━".repeat(80));
            testMultiEntityMode(shardConfig);

            Thread.sleep(2000);

            // Test 2: Event Playback
            System.out.println("\n" + "━".repeat(80));
            System.out.println("TEST 2: EVENT PLAYBACK (FORWARD & BACKWARD)");
            System.out.println("━".repeat(80));
            testEventPlayback(shardConfig);

            Thread.sleep(2000);

            // Test 3: Legacy Mode for Backward Compatibility
            System.out.println("\n" + "━".repeat(80));
            System.out.println("TEST 3: LEGACY MODE (BACKWARD COMPATIBILITY)");
            System.out.println("━".repeat(80));
            testLegacyMode();

        } catch (Exception e) {
            System.err.println("❌ Demo failed: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println("   DEMO COMPLETED");
        System.out.println("=".repeat(80));
    }

    /**
     * Test multi-entity mode with complex object graph
     */
    private static void testMultiEntityMode(ShardConfig shardConfig) {
        System.out.println("\n📋 Creating State-Walk registry with multi-entity support...");

        // Build registry with multi-entity configuration
        StateWalkRegistry<CallContext> registry = StateWalkBuilder.<CallContext>create("telecom_calls")
            .withContextClass(CallContext.class)
            .withShardConfig(shardConfig)
            .withPlayback(true)
            .withPerformanceConfig(RegistryPerformanceConfig.forDevelopment())
            .build();

        System.out.println("✅ Registry 'telecom_calls' created with database auto-creation");

        // Create a call state machine
        String callId = "CALL-" + System.currentTimeMillis();
        System.out.println("\n📞 Creating call state machine: " + callId);

        GenericStateMachine<CallContext, ?> callMachine = registry.createOrGetWithGraph(callId, () -> {
            // Create state machine with complex context
            CallContext context = new CallContext(callId);
            context.initialize();

            // Set up initial data
            Call call = context.getCall();
            call.setCallerNumber("+1-555-0100");
            call.setCalleeNumber("+1-555-0200");
            call.addEvent(new CallEvent("INITIATED", "Call started"));

            Cdr cdr = context.getCdr();
            cdr.setSourceNetwork("NetworkA");
            cdr.setDestinationNetwork("NetworkB");

            BillInfo billInfo = context.getBillInfo();
            billInfo.setAccountNumber("ACC-12345");
            billInfo.setTotalAmount(new BigDecimal("10.50"));
            billInfo.setParty(new Party("John Doe", "+1-555-0100"));

            DeviceInfo deviceInfo = new DeviceInfo("MOBILE", "iPhone 14");
            deviceInfo.setManufacturer("Apple");
            deviceInfo.setNetworkOperator("Verizon");
            context.setDeviceInfo(deviceInfo); // Singleton shared across graph

            // Create state machine
            return createCallStateMachine(context);
        });

        System.out.println("✅ Call machine created with multi-entity context");

        // Simulate call flow
        System.out.println("\n🔄 Simulating call flow...");

        // Incoming call
        callMachine.fire(new IncomingCallEvent());
        System.out.println("  → State: " + callMachine.getCurrentState());

        // Answer call
        callMachine.fire(new AnswerEvent());
        System.out.println("  → State: " + callMachine.getCurrentState());

        // Update call data
        CallContext context = callMachine.getPersistingEntity();
        context.getCall().addEvent(new CallEvent("CONNECTED", "Call connected"));
        context.getCdr().setDuration(120);
        context.getCdr().setChargeAmount(new BigDecimal("5.00"));

        // Hangup
        callMachine.fire(new HangupEvent());
        System.out.println("  → State: " + callMachine.getCurrentState());

        // Persist the graph
        System.out.println("\n💾 Persisting entity graph...");
        registry.persistGraph(callId);
        System.out.println("✅ Graph persisted with all relationships");

        // Display context
        System.out.println("\n📊 Final Call Context:");
        System.out.println("  " + context.toString());

        // Cleanup
        registry.shutdown();
    }

    /**
     * Test event playback functionality
     */
    private static void testEventPlayback(ShardConfig shardConfig) {
        System.out.println("\n📋 Creating State-Walk registry with playback enabled...");

        StateWalkRegistry<CallContext> registry = StateWalkBuilder.<CallContext>create("playback_demo")
            .withContextClass(CallContext.class)
            .withShardConfig(shardConfig)
            .withPlayback(true)
            .build();

        String callId = "PLAYBACK-" + System.currentTimeMillis();
        System.out.println("\n🎬 Creating machine for playback: " + callId);

        // Create machine and simulate transitions
        GenericStateMachine<CallContext, ?> machine = registry.createOrGetWithGraph(callId, () -> {
            CallContext context = new CallContext(callId);
            context.initialize();
            return createCallStateMachine(context);
        });

        // Record transitions
        System.out.println("\n📹 Recording transitions...");
        machine.fire(new IncomingCallEvent());
        System.out.println("  1. " + IDLE + " -> " + machine.getCurrentState());

        machine.fire(new AnswerEvent());
        System.out.println("  2. " + RINGING + " -> " + machine.getCurrentState());

        machine.fire(new HangupEvent());
        System.out.println("  3. " + CONNECTED + " -> " + machine.getCurrentState());

        // Get playback history
        List<TransitionRecord> history = registry.getPlaybackHistory(callId);
        System.out.println("\n📜 Recorded " + history.size() + " transitions");

        // Play backward
        System.out.println("\n⏪ Playing backward...");
        registry.playBackward(callId);
        System.out.println("  Current state: " + machine.getCurrentState());

        registry.playBackward(callId);
        System.out.println("  Current state: " + machine.getCurrentState());

        // Play forward
        System.out.println("\n⏩ Playing forward...");
        registry.playForward(callId);
        System.out.println("  Current state: " + machine.getCurrentState());

        // Jump to specific position
        System.out.println("\n⏭️ Jumping to transition 2...");
        registry.jumpToTransition(callId, 2);
        System.out.println("  Current state: " + machine.getCurrentState());

        System.out.println("\n✅ Playback demonstration completed");

        // Cleanup
        registry.shutdown();
    }

    /**
     * Test legacy mode for backward compatibility
     */
    private static void testLegacyMode() {
        System.out.println("\n📋 Creating State-Walk registry in LEGACY mode...");

        StateWalkRegistry<CallContext> registry = StateWalkBuilder.<CallContext>create("legacy_demo")
            .withContextClass(CallContext.class)
            .enableLegacyMode()
            .build();

        System.out.println("✅ Registry created in legacy single-entity mode");

        String callId = "LEGACY-" + System.currentTimeMillis();
        System.out.println("\n📞 Creating legacy machine: " + callId);

        GenericStateMachine<CallContext, ?> machine = registry.createOrGet(callId, () -> {
            CallContext context = new CallContext(callId);
            context.setCurrentState(IDLE);
            return createCallStateMachine(context);
        });

        // Simple transitions
        machine.fire(new IncomingCallEvent());
        System.out.println("  State: " + machine.getCurrentState());

        machine.fire(new HangupEvent());
        System.out.println("  State: " + machine.getCurrentState());

        System.out.println("\n✅ Legacy mode working with backward compatibility");

        // Cleanup
        registry.shutdown();
    }

    /**
     * Create a call state machine
     */
    private static GenericStateMachine<CallContext, Object> createCallStateMachine(CallContext context) {
        GenericStateMachine<CallContext, Object> machine = new GenericStateMachine<>(context, null);

        // Configure states and transitions
        machine.configure(IDLE)
            .permit(IncomingCallEvent.class, RINGING);

        machine.configure(RINGING)
            .permit(AnswerEvent.class, CONNECTED)
            .permit(HangupEvent.class, HUNGUP);

        machine.configure(CONNECTED)
            .permit(HangupEvent.class, HUNGUP);

        machine.configure(HUNGUP)
            .setAsFinalState();

        // Set initial state
        machine.restoreState(IDLE);

        return machine;
    }
}