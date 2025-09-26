package com.telcobright.statewalk.test_multientity.tests;

import com.telcobright.splitverse.config.ShardConfig;
import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statewalk.core.*;
import com.telcobright.statewalk.test_multientity.entities.*;

/**
 * Test Requirement #3: Singleton Management Test
 * Verifies singleton entities work correctly with machine IDs:
 * - Shared within each machine's graph
 * - Isolated between different machines
 * - Correct machine ID for each machine
 */
public class SingletonManagementTest {

    private static final String TEST_DB_NAME = "test_singleton_db";

    public static void main(String[] args) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   SINGLETON MANAGEMENT TEST");
        System.out.println("   Requirement: Singleton entities shared within machine, isolated between machines");
        System.out.println("=".repeat(80) + "\n");

        ShardConfig shardConfig = createShardConfig();

        try {
            StateWalkRegistry<CallMachineContext> registry = StateWalkBuilder.<CallMachineContext>create(TEST_DB_NAME)
                .withContextClass(CallMachineContext.class)
                .withShardConfig(shardConfig)
                .withPlayback(false)
                .build();

            System.out.println("✅ Registry created with database: " + TEST_DB_NAME);

            // Test 1: Singleton sharing within machine
            testSingletonWithinMachine(registry);

            // Test 2: Singleton isolation between machines
            testSingletonIsolation(registry);

            // Test 3: Singleton ID consistency
            testSingletonIdConsistency(registry);

            // Test 4: Singleton updates propagation
            testSingletonUpdates(registry);

            // Cleanup
            registry.shutdown();

            System.out.println("\n" + "=".repeat(80));
            System.out.println("   ✅ SINGLETON MANAGEMENT TEST PASSED");
            System.out.println("=".repeat(80));

        } catch (Exception e) {
            System.err.println("\n❌ TEST FAILED: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Test 1: Singleton sharing within a machine's graph
     */
    private static void testSingletonWithinMachine(StateWalkRegistry<CallMachineContext> registry) {
        System.out.println("\n" + "-".repeat(60));
        System.out.println("TEST 1: Singleton Sharing Within Machine");
        System.out.println("-".repeat(60));

        String machineId = "CALL-SINGLETON-001";

        GenericStateMachine<CallMachineContext, ?> machine = registry.createOrGetWithGraph(machineId, () -> {
            CallMachineContext context = new CallMachineContext(machineId);
            return createStateMachine(context);
        });

        CallMachineContext context = machine.getPersistingEntity();

        // Get DeviceInfo references from different entities
        DeviceInfoEntity deviceFromCall = context.getCall().getDeviceInfo();
        DeviceInfoEntity deviceFromBillInfo = context.getBillInfo().getDeviceInfo();
        DeviceInfoEntity deviceFromContext = context.getDeviceInfo();

        System.out.println("Checking singleton instance sharing...");

        // Verify all references point to same instance
        if (deviceFromCall != deviceFromContext) {
            throw new AssertionError("Call's DeviceInfo is not the same instance as Context's");
        }
        if (deviceFromBillInfo != deviceFromContext) {
            throw new AssertionError("BillInfo's DeviceInfo is not the same instance as Context's");
        }
        if (deviceFromCall != deviceFromBillInfo) {
            throw new AssertionError("Call's DeviceInfo is not the same instance as BillInfo's");
        }

        System.out.println("✅ DeviceInfo singleton shared across graph");
        System.out.println("  - Same instance in Call: " + (deviceFromCall == deviceFromContext));
        System.out.println("  - Same instance in BillInfo: " + (deviceFromBillInfo == deviceFromContext));
        System.out.println("  - All have machine ID: " + deviceFromContext.getId());
    }

    /**
     * Test 2: Singleton isolation between different machines
     */
    private static void testSingletonIsolation(StateWalkRegistry<CallMachineContext> registry) {
        System.out.println("\n" + "-".repeat(60));
        System.out.println("TEST 2: Singleton Isolation Between Machines");
        System.out.println("-".repeat(60));

        // Create first machine
        String machineId1 = "CALL-ISO-001";
        GenericStateMachine<CallMachineContext, ?> machine1 = registry.createOrGetWithGraph(machineId1, () -> {
            CallMachineContext context = new CallMachineContext(machineId1);
            context.getDeviceInfo().setModel("iPhone 14");
            context.getDeviceInfo().setNetworkOperator("AT&T");
            return createStateMachine(context);
        });

        // Create second machine
        String machineId2 = "CALL-ISO-002";
        GenericStateMachine<CallMachineContext, ?> machine2 = registry.createOrGetWithGraph(machineId2, () -> {
            CallMachineContext context = new CallMachineContext(machineId2);
            context.getDeviceInfo().setModel("Samsung S23");
            context.getDeviceInfo().setNetworkOperator("Verizon");
            return createStateMachine(context);
        });

        CallMachineContext context1 = machine1.getPersistingEntity();
        CallMachineContext context2 = machine2.getPersistingEntity();

        DeviceInfoEntity device1 = context1.getDeviceInfo();
        DeviceInfoEntity device2 = context2.getDeviceInfo();

        System.out.println("Checking singleton isolation...");

        // Verify different instances
        if (device1 == device2) {
            throw new AssertionError("DeviceInfo should be different instances for different machines");
        }

        // Verify different IDs
        if (device1.getId().equals(device2.getId())) {
            throw new AssertionError("DeviceInfo instances should have different machine IDs");
        }

        // Verify data isolation
        if (device1.getModel().equals(device2.getModel())) {
            throw new AssertionError("DeviceInfo data should be isolated between machines");
        }

        System.out.println("✅ Singletons isolated between machines");
        System.out.println("  Machine 1:");
        System.out.println("    - Device ID: " + device1.getId());
        System.out.println("    - Model: " + device1.getModel());
        System.out.println("    - Operator: " + device1.getNetworkOperator());
        System.out.println("  Machine 2:");
        System.out.println("    - Device ID: " + device2.getId());
        System.out.println("    - Model: " + device2.getModel());
        System.out.println("    - Operator: " + device2.getNetworkOperator());
    }

    /**
     * Test 3: Singleton ID consistency with machine ID
     */
    private static void testSingletonIdConsistency(StateWalkRegistry<CallMachineContext> registry) {
        System.out.println("\n" + "-".repeat(60));
        System.out.println("TEST 3: Singleton ID Consistency");
        System.out.println("-".repeat(60));

        String machineId = "CALL-ID-001";

        GenericStateMachine<CallMachineContext, ?> machine = registry.createOrGetWithGraph(machineId, () -> {
            CallMachineContext context = new CallMachineContext(machineId);
            return createStateMachine(context);
        });

        CallMachineContext context = machine.getPersistingEntity();

        System.out.println("Checking singleton ID matches machine ID...");

        // Verify DeviceInfo has machine ID
        if (!machineId.equals(context.getDeviceInfo().getId())) {
            throw new AssertionError("DeviceInfo ID doesn't match machine ID");
        }

        // Verify ID consistency across references
        if (!machineId.equals(context.getCall().getDeviceInfo().getId())) {
            throw new AssertionError("Call's DeviceInfo ID doesn't match machine ID");
        }

        if (!machineId.equals(context.getBillInfo().getDeviceInfo().getId())) {
            throw new AssertionError("BillInfo's DeviceInfo ID doesn't match machine ID");
        }

        System.out.println("✅ Singleton maintains machine ID consistency");
        System.out.println("  - Machine ID: " + machineId);
        System.out.println("  - DeviceInfo ID: " + context.getDeviceInfo().getId());
        System.out.println("  - Same ID in Call: " + context.getCall().getDeviceInfo().getId());
        System.out.println("  - Same ID in BillInfo: " + context.getBillInfo().getDeviceInfo().getId());
    }

    /**
     * Test 4: Singleton updates propagate within machine
     */
    private static void testSingletonUpdates(StateWalkRegistry<CallMachineContext> registry) {
        System.out.println("\n" + "-".repeat(60));
        System.out.println("TEST 4: Singleton Update Propagation");
        System.out.println("-".repeat(60));

        String machineId = "CALL-UPDATE-001";

        GenericStateMachine<CallMachineContext, ?> machine = registry.createOrGetWithGraph(machineId, () -> {
            CallMachineContext context = new CallMachineContext(machineId);
            context.getDeviceInfo().setModel("iPhone 13");
            context.getDeviceInfo().setNetworkType("4G");
            return createStateMachine(context);
        });

        CallMachineContext context = machine.getPersistingEntity();

        // Update device info through context
        System.out.println("Updating device info through context...");
        context.getDeviceInfo().setModel("iPhone 15 Pro");
        context.getDeviceInfo().setNetworkType("5G");
        context.getDeviceInfo().updateLastSeen();

        // Verify updates visible through all references
        String expectedModel = "iPhone 15 Pro";
        String expectedNetwork = "5G";

        System.out.println("Verifying updates propagated...");

        // Check through Call entity
        if (!expectedModel.equals(context.getCall().getDeviceInfo().getModel())) {
            throw new AssertionError("Update not visible through Call entity");
        }
        if (!expectedNetwork.equals(context.getCall().getDeviceInfo().getNetworkType())) {
            throw new AssertionError("Network update not visible through Call entity");
        }

        // Check through BillInfo entity
        if (!expectedModel.equals(context.getBillInfo().getDeviceInfo().getModel())) {
            throw new AssertionError("Update not visible through BillInfo entity");
        }
        if (!expectedNetwork.equals(context.getBillInfo().getDeviceInfo().getNetworkType())) {
            throw new AssertionError("Network update not visible through BillInfo entity");
        }

        System.out.println("✅ Singleton updates propagate to all references");
        System.out.println("  - Updated model: " + context.getDeviceInfo().getModel());
        System.out.println("  - Updated network: " + context.getDeviceInfo().getNetworkType());
        System.out.println("  - Visible in Call: " + context.getCall().getDeviceInfo().getModel());
        System.out.println("  - Visible in BillInfo: " + context.getBillInfo().getDeviceInfo().getModel());

        // Test update through different reference
        System.out.println("\nUpdating through Call entity reference...");
        context.getCall().getDeviceInfo().setManufacturer("Apple Inc.");

        // Verify visible everywhere
        if (!"Apple Inc.".equals(context.getDeviceInfo().getManufacturer())) {
            throw new AssertionError("Update through Call not visible in main context");
        }
        if (!"Apple Inc.".equals(context.getBillInfo().getDeviceInfo().getManufacturer())) {
            throw new AssertionError("Update through Call not visible in BillInfo");
        }

        System.out.println("✅ Updates through any reference affect singleton");
        System.out.println("  - Manufacturer: " + context.getDeviceInfo().getManufacturer());
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
}