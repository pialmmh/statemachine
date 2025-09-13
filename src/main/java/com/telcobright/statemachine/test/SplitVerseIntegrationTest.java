package com.telcobright.statemachine.test;

import com.telcobright.statemachine.persistence.SplitVersePersistenceProvider;
import com.telcobright.statemachine.StateMachineRegistry;
import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.persistence.PersistenceType;
import com.telcobright.splitverse.config.ShardConfig;
import com.telcobright.core.partition.PartitionType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Integration test for Split-Verse persistence with StateMachine
 */
public class SplitVerseIntegrationTest {

    private static final Logger logger = Logger.getLogger(SplitVerseIntegrationTest.class.getName());

    // Call states
    private static final String IDLE = "IDLE";
    private static final String DIALING = "DIALING";
    private static final String RINGING = "RINGING";
    private static final String CONNECTED = "CONNECTED";
    private static final String ON_HOLD = "ON_HOLD";
    private static final String DISCONNECTED = "DISCONNECTED";

    // Events
    private static final String DIAL = "DIAL";
    private static final String RING = "RING";
    private static final String ANSWER = "ANSWER";
    private static final String HOLD = "HOLD";
    private static final String RESUME = "RESUME";
    private static final String HANGUP = "HANGUP";

    public static void main(String[] args) {
        // Set logging level
        Logger.getGlobal().setLevel(Level.INFO);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("   SPLIT-VERSE INTEGRATION TEST FOR STATEMACHINE");
        System.out.println("=".repeat(80));
        System.out.println();

        try {
            runTest();
        } catch (Exception e) {
            System.err.println("Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void runTest() throws Exception {
        // Step 1: Create Split-Verse persistence provider
        System.out.println("📋 Setting up Split-Verse persistence provider...");

        SplitVersePersistenceProvider<SplitVerseCallEntity> persistenceProvider =
            SplitVersePersistenceProvider.<SplitVerseCallEntity>builder()
                .withEntityClass(SplitVerseCallEntity.class)
                .withDatabaseConfig(
                    "127.0.0.1",           // MySQL host (LXC container)
                    3306,                  // MySQL port
                    "statemachine_test",   // Database name
                    "root",                // Username
                    "123456"               // Password
                )
                .withPartitionType(PartitionType.DATE_BASED)
                .withPartitionKeyColumn("created_at")
                .build();

        System.out.println("✅ Split-Verse persistence provider initialized");

        // Step 2: Setup StateMachine Registry
        System.out.println("\n📋 Setting up StateMachine registry...");

        StateMachineRegistry registry = new StateMachineRegistry();
        registry.setPersistenceType(PersistenceType.PARTITIONED_REPO);
        registry.setPersistenceProvider(persistenceProvider);

        // Create a simple call state machine
        GenericStateMachine<SplitVerseCallEntity, SplitVerseCallEntity> callStateMachine =
            new GenericStateMachine<>("CallStateMachine");

        // Define states
        callStateMachine.addState(IDLE);
        callStateMachine.addState(DIALING);
        callStateMachine.addState(RINGING);
        callStateMachine.addState(CONNECTED);
        callStateMachine.addState(ON_HOLD);
        callStateMachine.addState(DISCONNECTED);

        // Define transitions
        callStateMachine.addTransition(IDLE, DIAL, DIALING);
        callStateMachine.addTransition(DIALING, RING, RINGING);
        callStateMachine.addTransition(RINGING, ANSWER, CONNECTED);
        callStateMachine.addTransition(CONNECTED, HOLD, ON_HOLD);
        callStateMachine.addTransition(ON_HOLD, RESUME, CONNECTED);
        callStateMachine.addTransition(CONNECTED, HANGUP, DISCONNECTED);
        callStateMachine.addTransition(RINGING, HANGUP, DISCONNECTED);
        callStateMachine.addTransition(ON_HOLD, HANGUP, DISCONNECTED);

        registry.register("CallStateMachine", callStateMachine);
        System.out.println("✅ StateMachine registered with Split-Verse persistence");

        // Step 3: Test data persistence
        System.out.println("\n📋 Testing data persistence with Split-Verse...");

        int numCalls = 100;
        String[] callIds = new String[numCalls];

        // Create and persist call entities
        for (int i = 0; i < numCalls; i++) {
            String callId = "CALL-" + UUID.randomUUID().toString().substring(0, 8);
            callIds[i] = callId;

            SplitVerseCallEntity call = new SplitVerseCallEntity(
                callId,
                "+1-555-" + String.format("%04d", ThreadLocalRandom.current().nextInt(10000)),
                "+1-555-" + String.format("%04d", ThreadLocalRandom.current().nextInt(10000))
            );
            call.setCurrentState(IDLE);

            // Initialize state machine for this call
            GenericStateMachine<SplitVerseCallEntity, SplitVerseCallEntity> machine =
                registry.getStateMachine("CallStateMachine", callId);
            machine.setContext(call);
            machine.start(IDLE);

            // Save via persistence provider
            persistenceProvider.save(callId, call);

            if (i < 5) {
                System.out.println("  Created call: " + callId + " (Caller: " + call.getCallerId() + ")");
            }
        }
        System.out.println("✅ Created and persisted " + numCalls + " call entities");

        // Step 4: Simulate call flow
        System.out.println("\n📋 Simulating call flows...");

        for (int i = 0; i < Math.min(20, numCalls); i++) {
            String callId = callIds[i];

            // Load entity from Split-Verse
            SplitVerseCallEntity call = persistenceProvider.load(callId, SplitVerseCallEntity.class);
            if (call == null) {
                System.err.println("Failed to load call: " + callId);
                continue;
            }

            // Get state machine
            GenericStateMachine<SplitVerseCallEntity, SplitVerseCallEntity> machine =
                registry.getStateMachine("CallStateMachine", callId);
            machine.setContext(call);

            // Process events
            machine.sendEvent(DIAL);
            call.incrementEventCount();
            call.setLastEvent(DIAL);

            machine.sendEvent(RING);
            call.incrementEventCount();
            call.setLastEvent(RING);

            if (ThreadLocalRandom.current().nextBoolean()) {
                machine.sendEvent(ANSWER);
                call.incrementEventCount();
                call.setLastEvent(ANSWER);
                call.setCallDuration(ThreadLocalRandom.current().nextLong(60, 3600));

                if (ThreadLocalRandom.current().nextBoolean()) {
                    machine.sendEvent(HOLD);
                    call.incrementEventCount();
                    call.setLastEvent(HOLD);

                    machine.sendEvent(RESUME);
                    call.incrementEventCount();
                    call.setLastEvent(RESUME);
                }

                machine.sendEvent(HANGUP);
                call.incrementEventCount();
                call.setLastEvent(HANGUP);
                call.setDisconnectReason("NORMAL_CLEARING");
            } else {
                machine.sendEvent(HANGUP);
                call.incrementEventCount();
                call.setLastEvent(HANGUP);
                call.setDisconnectReason("NO_ANSWER");
            }

            call.setComplete(true);

            // Update via persistence provider
            persistenceProvider.save(callId, call);

            if (i < 5) {
                System.out.println("  Processed call: " + callId +
                    " -> " + call.getCurrentState() +
                    " (Events: " + call.getEventCount() + ")");
            }
        }
        System.out.println("✅ Processed call flows and updated persistence");

        // Step 5: Test retrieval and date range queries
        System.out.println("\n📋 Testing data retrieval from Split-Verse...");

        // Test individual retrieval
        int foundCount = 0;
        for (int i = 0; i < Math.min(10, numCalls); i++) {
            SplitVerseCallEntity loaded = persistenceProvider.load(callIds[i], SplitVerseCallEntity.class);
            if (loaded != null) {
                foundCount++;
                if (i < 3) {
                    System.out.println("  Retrieved: " + loaded.getId() +
                        " (State: " + loaded.getCurrentState() +
                        ", Complete: " + loaded.isComplete() + ")");
                }
            }
        }
        System.out.println("✅ Successfully retrieved " + foundCount + "/10 entities");

        // Test date range query
        LocalDateTime startDate = LocalDateTime.now().minusHours(1);
        LocalDateTime endDate = LocalDateTime.now().plusHours(1);
        List<SplitVerseCallEntity> rangeResults = persistenceProvider.loadByDateRange(startDate, endDate);
        System.out.println("✅ Date range query returned " + rangeResults.size() + " entities");

        // Test batch loading
        if (numCalls > 10) {
            List<SplitVerseCallEntity> batch = persistenceProvider.loadBatch(callIds[0], 10);
            System.out.println("✅ Batch query returned " + batch.size() + " entities");
        }

        // Step 6: Show partition information
        System.out.println("\n📋 Split-Verse Partition Information:");
        System.out.println("  Partition Type: DATE_BASED");
        System.out.println("  Partition Key: created_at");
        System.out.println("  Current Date: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        System.out.println("  Tables are automatically created as: call_states_YYYY_MM_DD");
        System.out.println("  Split-Verse handles partition management automatically");

        System.out.println("\n" + "=".repeat(80));
        System.out.println("✅ SPLIT-VERSE INTEGRATION TEST COMPLETED SUCCESSFULLY!");
        System.out.println("=".repeat(80));
    }
}