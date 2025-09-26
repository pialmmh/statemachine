package com.telcobright.statemachine.test;

import com.telcobright.splitverse.config.ShardConfig;
import com.telcobright.splitverse.config.RepositoryMode;
import com.telcobright.statemachine.repository.CallRepository;
import com.telcobright.statemachine.repository.SmsRepository;
import com.telcobright.statemachine.entities.CallRecord;
import com.telcobright.statemachine.entities.SmsRecord;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Test demonstrating two lookup patterns:
 * 1. Call: Simple ID-only lookup
 * 2. SMS: ID and date range lookup
 */
public class CallSmsLookupTest {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   CALL & SMS LOOKUP PATTERNS TEST");
        System.out.println("   1. Call: ID-only lookup");
        System.out.println("   2. SMS: ID and date range lookup");
        System.out.println("=".repeat(80));
        System.out.println();

        ShardConfig config = ShardConfig.builder()
            .shardId("primary")
            .host("127.0.0.1")
            .port(3306)
            .database("statemachine_test")
            .username("root")
            .password("123456")
            .connectionPoolSize(10)
            .enabled(true)
            .build();

        try {
            // Test both PARTITIONED and MULTI_TABLE modes
            System.out.println("━".repeat(80));
            System.out.println("TESTING WITH PARTITIONED MODE");
            System.out.println("━".repeat(80));
            testWithMode(config, RepositoryMode.PARTITIONED);

            Thread.sleep(2000);

            System.out.println("\n" + "━".repeat(80));
            System.out.println("TESTING WITH MULTI_TABLE MODE");
            System.out.println("━".repeat(80));
            testWithMode(config, RepositoryMode.MULTI_TABLE);

        } catch (Exception e) {
            System.err.println("❌ Test failed: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println("   TEST COMPLETED");
        System.out.println("=".repeat(80));
    }

    private static void testWithMode(ShardConfig config, RepositoryMode mode) throws SQLException, InterruptedException {
        CallRepository callRepo = null;
        SmsRepository smsRepo = null;

        try {
            // Initialize repositories
            System.out.println("\n📋 Initializing repositories in " + mode + " mode...");
            callRepo = new CallRepository(config, mode, 7);
            smsRepo = new SmsRepository(config, mode, 7);
            System.out.println("✅ Repositories initialized");

            // Test 1: Call ID-only lookup
            System.out.println("\n" + "-".repeat(60));
            System.out.println("TEST 1: CALL - ID-ONLY LOOKUP");
            System.out.println("-".repeat(60));
            testCallLookup(callRepo);

            // Test 2: SMS ID and date range lookup
            System.out.println("\n" + "-".repeat(60));
            System.out.println("TEST 2: SMS - ID AND DATE RANGE LOOKUP");
            System.out.println("-".repeat(60));
            testSmsLookup(smsRepo);

        } finally {
            if (callRepo != null) callRepo.shutdown();
            if (smsRepo != null) smsRepo.shutdown();
            System.out.println("\n✅ Repositories shut down");
        }
    }

    private static void testCallLookup(CallRepository callRepo) throws SQLException {
        System.out.println("\n📝 Inserting test call records...");

        // Create test calls
        List<String> callIds = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < 5; i++) {
            CallRecord call = new CallRecord();
            call.setId("CALL-TEST-" + i + "-" + UUID.randomUUID().toString().substring(0, 8));
            call.setCreatedAt(now.minusMinutes(i * 10));
            call.setCallerNumber("+1-555-" + String.format("%04d", 1000 + i));
            call.setCalleeNumber("+1-555-" + String.format("%04d", 2000 + i));
            call.setCallStatus("INITIATED");
            call.setCallDirection("OUTBOUND");

            callRepo.insert(call);
            callIds.add(call.getId());

            if (i < 2) {
                System.out.println("   Inserted: " + call.getId() + " at " +
                    call.getCreatedAt().format(formatter));
            }
        }
        System.out.println("   ✅ Inserted " + callIds.size() + " call records");

        // Test ID-only lookup
        System.out.println("\n🔍 Testing ID-only lookup for calls...");
        for (int i = 0; i < 3; i++) {
            String callId = callIds.get(i);
            long startTime = System.currentTimeMillis();
            CallRecord retrieved = callRepo.findById(callId);
            long lookupTime = System.currentTimeMillis() - startTime;

            if (retrieved != null) {
                System.out.println("   ✅ Found call: " + retrieved.getId());
                System.out.println("      Caller: " + retrieved.getCallerNumber() +
                                  " -> Callee: " + retrieved.getCalleeNumber());
                System.out.println("      Lookup time: " + lookupTime + "ms");
            } else {
                System.out.println("   ❌ Call not found: " + callId);
            }
        }

        // Update call status
        System.out.println("\n📝 Updating call status...");
        String updateId = callIds.get(0);
        callRepo.updateCall(updateId, "COMPLETED", 180);
        CallRecord updated = callRepo.findById(updateId);
        if (updated != null) {
            System.out.println("   ✅ Updated call " + updateId + " to status: " + updated.getCallStatus());
        }
    }

    private static void testSmsLookup(SmsRepository smsRepo) throws SQLException {
        System.out.println("\n📝 Inserting test SMS records across different time ranges...");

        // Create SMS records with specific timestamps
        List<TestSmsData> testData = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now();

        // SMS in different time windows
        for (int day = 0; day < 3; day++) {
            for (int hour = 0; hour < 24; hour += 6) {
                String smsId = String.format("SMS-D%d-H%02d-%s", day, hour,
                    UUID.randomUUID().toString().substring(0, 8));
                LocalDateTime smsTime = baseTime.minusDays(day).minusHours(hour);

                SmsRecord sms = new SmsRecord(smsId, smsTime);
                sms.setSenderNumber("+1-555-" + String.format("%04d", 3000 + day * 100 + hour));
                sms.setReceiverNumber("+1-555-" + String.format("%04d", 4000 + day * 100 + hour));
                sms.setMessageContent("Test message at day " + day + " hour " + hour);
                sms.setMessageType("PROMOTIONAL");

                smsRepo.insert(sms);
                testData.add(new TestSmsData(smsId, smsTime));

                if (testData.size() <= 3) {
                    System.out.println("   Inserted: " + smsId + " at " + smsTime.format(formatter));
                }
            }
        }
        System.out.println("   ✅ Inserted " + testData.size() + " SMS records");

        // Test case 1: Find SMS within exact time window
        System.out.println("\n🔍 Test Case 1: Find SMS within exact time window");
        TestSmsData target = testData.get(5); // Get a middle record
        LocalDateTime rangeStart = target.timestamp.minusMinutes(5);
        LocalDateTime rangeEnd = target.timestamp.plusMinutes(5);

        System.out.println("   Looking for: " + target.id);
        System.out.println("   Time range: " + rangeStart.format(formatter) + " to " + rangeEnd.format(formatter));

        long startTime = System.currentTimeMillis();
        SmsRecord found = smsRepo.findByIdAndPartitionedColRange(target.id, rangeStart, rangeEnd);
        long lookupTime = System.currentTimeMillis() - startTime;

        if (found != null) {
            System.out.println("   ✅ Found SMS: " + found.getId());
            System.out.println("      Created at: " + found.getCreatedAt().format(formatter));
            System.out.println("      Content: " + found.getMessageContent());
            System.out.println("      Lookup time: " + lookupTime + "ms");
        } else {
            System.out.println("   ❌ SMS not found in time range");
        }

        // Test case 2: Find SMS with string date format
        System.out.println("\n🔍 Test Case 2: Find SMS using string date format");
        TestSmsData target2 = testData.get(10);
        String startStr = target2.timestamp.minusHours(1).format(formatter);
        String endStr = target2.timestamp.plusHours(1).format(formatter);

        System.out.println("   Looking for: " + target2.id);
        System.out.println("   Time range: " + startStr + " to " + endStr);

        startTime = System.currentTimeMillis();
        SmsRecord found2 = smsRepo.findByIdAndPartitionedColRange(target2.id, startStr, endStr);
        lookupTime = System.currentTimeMillis() - startTime;

        if (found2 != null) {
            System.out.println("   ✅ Found SMS: " + found2.getId());
            System.out.println("      Sender: " + found2.getSenderNumber() +
                              " -> Receiver: " + found2.getReceiverNumber());
            System.out.println("      Lookup time: " + lookupTime + "ms");
        } else {
            System.out.println("   ❌ SMS not found in time range");
        }

        // Test case 3: SMS outside time range (should not find)
        System.out.println("\n🔍 Test Case 3: SMS outside time range (should not find)");
        TestSmsData target3 = testData.get(0);
        LocalDateTime wrongStart = target3.timestamp.plusDays(1); // Future date
        LocalDateTime wrongEnd = target3.timestamp.plusDays(2);

        System.out.println("   Looking for: " + target3.id);
        System.out.println("   Wrong time range: " + wrongStart.format(formatter) + " to " + wrongEnd.format(formatter));

        SmsRecord notFound = smsRepo.findByIdAndPartitionedColRange(target3.id, wrongStart, wrongEnd);
        if (notFound == null) {
            System.out.println("   ✅ Correctly returned null for SMS outside time range");
        } else {
            System.out.println("   ❌ Unexpectedly found SMS outside time range");
        }

        // Test case 4: Multiple SMS lookup
        System.out.println("\n🔍 Test Case 4: Multiple SMS lookup within date range");
        List<String> smsIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            smsIds.add(testData.get(i).id);
        }
        LocalDateTime multiRangeStart = baseTime.minusDays(3);
        LocalDateTime multiRangeEnd = baseTime.plusDays(1);

        List<SmsRecord> multiResults = smsRepo.findByIdsAndPartitionedColRange(smsIds,
            multiRangeStart, multiRangeEnd);
        System.out.println("   ✅ Found " + multiResults.size() + "/" + smsIds.size() +
            " SMS records in date range");
    }

    // Helper class for test data
    private static class TestSmsData {
        String id;
        LocalDateTime timestamp;

        TestSmsData(String id, LocalDateTime timestamp) {
            this.id = id;
            this.timestamp = timestamp;
        }
    }
}