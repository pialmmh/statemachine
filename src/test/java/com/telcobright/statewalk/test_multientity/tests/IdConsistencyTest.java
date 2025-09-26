package com.telcobright.statewalk.test_multientity.tests;

import com.telcobright.splitverse.config.ShardConfig;
import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statewalk.core.*;
import com.telcobright.statewalk.test_multientity.entities.*;
import java.math.BigDecimal;

/**
 * Test Requirement #1: ID Consistency Test
 * Verifies that all entities in a machine's context share the same machine ID
 */
public class IdConsistencyTest {

    private static final String TEST_MACHINE_ID = "CALL-001";
    private static final String TEST_DB_NAME = "test_id_consistency";

    public static void main(String[] args) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   ID CONSISTENCY TEST");
        System.out.println("   Requirement: All entities must share the same machine ID");
        System.out.println("=".repeat(80) + "\n");

        ShardConfig shardConfig = createShardConfig();

        try {
            // Create registry with test database
            StateWalkRegistry<CallMachineContext> registry = StateWalkBuilder.<CallMachineContext>create(TEST_DB_NAME)
                .withContextClass(CallMachineContext.class)
                .withShardConfig(shardConfig)
                .withPlayback(false)
                .build();

            System.out.println("✅ Registry created with database: " + TEST_DB_NAME);

            // Test 1: Create machine with ID consistency
            testIdConsistency(registry);

            // Test 2: Verify child entity references
            testChildEntityReferences(registry);

            // Test 3: Test ID propagation on updates
            testIdPropagation(registry);

            // Test 4: Validate consistency method
            testValidationMethod(registry);

            // Cleanup
            registry.shutdown();

            System.out.println("\n" + "=".repeat(80));
            System.out.println("   ✅ ID CONSISTENCY TEST PASSED");
            System.out.println("=".repeat(80));

        } catch (Exception e) {
            System.err.println("\n❌ TEST FAILED: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Test 1: Basic ID consistency across all entities
     */
    private static void testIdConsistency(StateWalkRegistry<CallMachineContext> registry) {
        System.out.println("\n" + "-".repeat(60));
        System.out.println("TEST 1: Basic ID Consistency");
        System.out.println("-".repeat(60));

        // Create machine with context
        GenericStateMachine<CallMachineContext, ?> machine = registry.createOrGetWithGraph(TEST_MACHINE_ID, () -> {
            CallMachineContext context = new CallMachineContext(TEST_MACHINE_ID);
            return createStateMachine(context);
        });

        CallMachineContext context = machine.getPersistingEntity();

        // Verify all entities have the same ID
        System.out.println("Checking entity IDs...");

        assertIdEquals("Context", TEST_MACHINE_ID, context.getId());
        assertIdEquals("Call", TEST_MACHINE_ID, context.getCall().getId());
        assertIdEquals("CDR", TEST_MACHINE_ID, context.getCdr().getId());
        assertIdEquals("BillInfo", TEST_MACHINE_ID, context.getBillInfo().getId());
        assertIdEquals("DeviceInfo", TEST_MACHINE_ID, context.getDeviceInfo().getId());

        System.out.println("✅ All entities share machine ID: " + TEST_MACHINE_ID);
    }

    /**
     * Test 2: Verify child entity references
     */
    private static void testChildEntityReferences(StateWalkRegistry<CallMachineContext> registry) {
        System.out.println("\n" + "-".repeat(60));
        System.out.println("TEST 2: Child Entity References");
        System.out.println("-".repeat(60));

        String machineId = "CALL-002";

        GenericStateMachine<CallMachineContext, ?> machine = registry.createOrGetWithGraph(machineId, () -> {
            CallMachineContext context = new CallMachineContext(machineId);

            // Add child entities
            context.getCall().addEvent(new CallEventEntity("INITIATED", "Call started"));
            context.getCall().addEvent(new CallEventEntity("RINGING", "Phone ringing"));
            context.getCall().addEvent(new CallEventEntity("CONNECTED", "Call connected"));

            PartyEntity party = new PartyEntity("John Doe", "+1-555-0100");
            context.getBillInfo().setParty(party);

            return createStateMachine(context);
        });

        CallMachineContext context = machine.getPersistingEntity();

        // Verify call events reference machine ID
        System.out.println("Checking CallEvent references...");
        for (CallEventEntity event : context.getCall().getEvents()) {
            assertIdEquals("CallEvent.callId", machineId, event.getCallId());
        }
        System.out.println("✅ All CallEvents reference machine ID: " + machineId);

        // Verify party references machine ID through billId
        System.out.println("Checking Party reference...");
        assertIdEquals("Party.billId", machineId, context.getBillInfo().getParty().getBillId());
        System.out.println("✅ Party references machine ID through billId: " + machineId);
    }

    /**
     * Test 3: Test ID propagation when setting entities
     */
    private static void testIdPropagation(StateWalkRegistry<CallMachineContext> registry) {
        System.out.println("\n" + "-".repeat(60));
        System.out.println("TEST 3: ID Propagation on Updates");
        System.out.println("-".repeat(60));

        String machineId = "CALL-003";

        // Create context first
        CallMachineContext context = new CallMachineContext(machineId);

        // Create entities separately and set them
        CallEntity newCall = new CallEntity();
        newCall.setCallerNumber("+1-555-1111");
        context.setCall(newCall);

        CdrEntity newCdr = new CdrEntity();
        newCdr.setDuration(180);
        context.setCdr(newCdr);

        BillInfoEntity newBillInfo = new BillInfoEntity();
        newBillInfo.setTotalAmount(new BigDecimal("25.50"));
        context.setBillInfo(newBillInfo);

        // Verify IDs were propagated
        System.out.println("Checking ID propagation after setting entities...");
        assertIdEquals("Call after set", machineId, context.getCall().getId());
        assertIdEquals("CDR after set", machineId, context.getCdr().getId());
        assertIdEquals("BillInfo after set", machineId, context.getBillInfo().getId());

        System.out.println("✅ IDs propagated correctly when entities are set");
    }

    /**
     * Test 4: Validate consistency method
     */
    private static void testValidationMethod(StateWalkRegistry<CallMachineContext> registry) {
        System.out.println("\n" + "-".repeat(60));
        System.out.println("TEST 4: Validation Method");
        System.out.println("-".repeat(60));

        // Test valid consistency
        String validId = "CALL-004";
        CallMachineContext validContext = new CallMachineContext(validId);

        System.out.println("Testing valid consistency...");
        if (!validContext.validateIdConsistency()) {
            throw new AssertionError("Valid context failed consistency check");
        }
        System.out.println("✅ Valid context passes consistency check");

        // Test invalid consistency
        CallMachineContext invalidContext = new CallMachineContext("CALL-005");
        invalidContext.getCall().setId("WRONG-ID"); // Intentionally break consistency

        System.out.println("Testing invalid consistency...");
        if (invalidContext.validateIdConsistency()) {
            throw new AssertionError("Invalid context passed consistency check");
        }
        System.out.println("✅ Invalid context correctly fails consistency check");

        // Test child entity inconsistency
        CallMachineContext childInconsistent = new CallMachineContext("CALL-006");
        CallEventEntity badEvent = new CallEventEntity("TEST", "data");
        badEvent.setCallId("WRONG-CALL-ID");
        childInconsistent.getCall().getEvents().add(badEvent);

        System.out.println("Testing child entity inconsistency...");
        if (childInconsistent.validateIdConsistency()) {
            throw new AssertionError("Context with inconsistent child passed check");
        }
        System.out.println("✅ Context with inconsistent child correctly fails check");
    }

    /**
     * Helper: Create shard configuration
     */
    private static ShardConfig createShardConfig() {
        return ShardConfig.builder()
            .shardId("primary")
            .host("127.0.0.1")
            .port(3306)
            .username("root")
            .password("123456")
            .connectionPoolSize(10)
            .enabled(true)
            .build();
    }

    /**
     * Helper: Create state machine
     */
    private static GenericStateMachine<CallMachineContext, Object> createStateMachine(CallMachineContext context) {
        GenericStateMachine<CallMachineContext, Object> machine = new GenericStateMachine<>(context, null);
        machine.configure("IDLE");
        machine.restoreState("IDLE");
        return machine;
    }

    /**
     * Helper: Assert ID equals
     */
    private static void assertIdEquals(String entityName, String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(
                String.format("%s ID mismatch: expected=%s, actual=%s", entityName, expected, actual)
            );
        }
        System.out.println("  ✓ " + entityName + " ID: " + actual);
    }
}