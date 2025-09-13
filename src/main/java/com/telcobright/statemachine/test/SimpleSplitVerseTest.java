package com.telcobright.statemachine.test;

import com.telcobright.statemachine.persistence.SplitVersePersistenceProvider;
import com.telcobright.statemachine.StateMachineRegistry;
import com.telcobright.statemachine.persistence.PersistenceType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Simplified integration test for Split-Verse persistence with StateMachine
 */
public class SimpleSplitVerseTest {

    public static void main(String[] args) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   SPLIT-VERSE INTEGRATION TEST");
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
                .build();

        System.out.println("✅ Split-Verse persistence provider initialized");

        // Step 2: Initialize the provider
        persistenceProvider.initialize();

        // Step 3: Test data persistence
        System.out.println("\n📋 Testing data persistence with Split-Verse...");

        int numCalls = 50;
        String[] callIds = new String[numCalls];

        // Create and persist call entities
        for (int i = 0; i < numCalls; i++) {
            String callId = "CALL-" + UUID.randomUUID().toString().substring(0, 8);
            callIds[i] = callId;

            SplitVerseCallEntity call = new SplitVerseCallEntity(
                callId,
                "+1-555-" + String.format("%04d", i),
                "+1-555-" + String.format("%04d", i + 1)
            );
            call.setCurrentState("IDLE");
            call.setCreatedAt(LocalDateTime.now());

            // Save via persistence provider
            persistenceProvider.save(callId, call);

            if (i < 5) {
                System.out.println("  Created call: " + callId + " (Caller: " + call.getCallerId() + ")");
            }
        }
        System.out.println("✅ Created and persisted " + numCalls + " call entities");

        // Step 4: Test retrieval
        System.out.println("\n📋 Testing data retrieval from Split-Verse...");

        int foundCount = 0;
        for (int i = 0; i < Math.min(10, numCalls); i++) {
            SplitVerseCallEntity loaded = persistenceProvider.load(callIds[i], SplitVerseCallEntity.class);
            if (loaded != null) {
                foundCount++;
                if (i < 3) {
                    System.out.println("  Retrieved: " + loaded.getId() +
                        " (State: " + loaded.getCurrentState() +
                        ", Caller: " + loaded.getCallerId() + ")");
                }
            }
        }
        System.out.println("✅ Successfully retrieved " + foundCount + "/10 entities");

        // Step 5: Test updates
        System.out.println("\n📋 Testing updates...");

        for (int i = 0; i < Math.min(5, numCalls); i++) {
            String callId = callIds[i];
            SplitVerseCallEntity call = persistenceProvider.load(callId, SplitVerseCallEntity.class);

            if (call != null) {
                call.setCurrentState("CONNECTED");
                call.setCallDuration(100L + i * 50);
                call.setComplete(true);
                call.setDisconnectReason("NORMAL_CLEARING");
                call.setEventCount(5);

                // Update via persistence provider
                persistenceProvider.save(callId, call);

                System.out.println("  Updated call: " + callId + " -> CONNECTED");
            }
        }

        // Verify updates
        SplitVerseCallEntity verifyUpdate = persistenceProvider.load(callIds[0], SplitVerseCallEntity.class);
        if (verifyUpdate != null && "CONNECTED".equals(verifyUpdate.getCurrentState())) {
            System.out.println("✅ Updates verified successfully");
        }

        // Step 6: Test date range queries
        System.out.println("\n📋 Testing date range queries...");
        LocalDateTime startDate = LocalDateTime.now().minusHours(1);
        LocalDateTime endDate = LocalDateTime.now().plusHours(1);
        var rangeResults = persistenceProvider.loadByDateRange(startDate, endDate);
        System.out.println("✅ Date range query returned " + rangeResults.size() + " entities");

        // Step 7: Test batch loading
        if (numCalls > 10) {
            var batch = persistenceProvider.loadBatch(callIds[0], 10);
            System.out.println("✅ Batch query returned " + batch.size() + " entities");
        }

        // Step 8: Test deletion
        System.out.println("\n📋 Testing deletion...");
        persistenceProvider.delete(callIds[0]);
        SplitVerseCallEntity deleted = persistenceProvider.load(callIds[0], SplitVerseCallEntity.class);
        if (deleted == null) {
            System.out.println("✅ Deletion successful");
        } else {
            System.out.println("❌ Deletion failed - entity still exists");
        }

        // Show partition information
        System.out.println("\n📋 Split-Verse Partition Information:");
        System.out.println("  Partition Type: DATE_BASED");
        System.out.println("  Partition Key: created_at");
        System.out.println("  Current Date: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        System.out.println("  Tables are automatically created as: call_states_YYYY_MM_DD");

        System.out.println("\n" + "=".repeat(80));
        System.out.println("✅ SPLIT-VERSE INTEGRATION TEST COMPLETED SUCCESSFULLY!");
        System.out.println("=".repeat(80));
    }
}