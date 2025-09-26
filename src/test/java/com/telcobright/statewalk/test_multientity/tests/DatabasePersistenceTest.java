package com.telcobright.statewalk.test_multientity.tests;

import com.telcobright.splitverse.config.ShardConfig;
import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statewalk.core.*;
import com.telcobright.statewalk.test_multientity.entities.*;

import java.sql.*;
import java.math.BigDecimal;

/**
 * Test Requirement #2: Database Persistence Test
 * Verifies that database survives registry restarts and data is preserved
 */
public class DatabasePersistenceTest {

    private static final String TEST_DB_NAME = "test_persistence_db";
    private static final String TEST_MACHINE_ID = "PERSIST-001";

    public static void main(String[] args) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   DATABASE PERSISTENCE TEST");
        System.out.println("   Requirement: Database must persist across registry restarts");
        System.out.println("=".repeat(80) + "\n");

        ShardConfig shardConfig = createShardConfig();

        try {
            // Test 1: Create registry and insert data
            System.out.println("\n" + "-".repeat(60));
            System.out.println("PHASE 1: Initial Registry Creation");
            System.out.println("-".repeat(60));

            StateWalkRegistry<CallMachineContext> registry1 = createRegistry(shardConfig);
            String initialData = insertTestData(registry1);
            verifyDatabaseExists(shardConfig);

            // Shutdown first registry
            registry1.shutdown();
            System.out.println("\n✅ First registry shut down");

            Thread.sleep(2000); // Wait for cleanup

            // Test 2: Create new registry with same name
            System.out.println("\n" + "-".repeat(60));
            System.out.println("PHASE 2: Registry Restart");
            System.out.println("-".repeat(60));

            StateWalkRegistry<CallMachineContext> registry2 = createRegistry(shardConfig);
            System.out.println("✅ Second registry created with same database name");

            // Test 3: Verify database still exists
            verifyDatabaseExists(shardConfig);

            // Test 4: Verify data can be retrieved
            verifyDataPersistence(registry2, initialData);

            // Test 5: Add more data in second session
            System.out.println("\n" + "-".repeat(60));
            System.out.println("PHASE 3: Adding Data in Second Session");
            System.out.println("-".repeat(60));

            String secondData = insertAdditionalData(registry2);

            // Shutdown second registry
            registry2.shutdown();
            System.out.println("\n✅ Second registry shut down");

            Thread.sleep(2000);

            // Test 6: Third registry to verify all data
            System.out.println("\n" + "-".repeat(60));
            System.out.println("PHASE 4: Third Registry Verification");
            System.out.println("-".repeat(60));

            StateWalkRegistry<CallMachineContext> registry3 = createRegistry(shardConfig);
            verifyAllData(registry3, initialData, secondData);

            // Cleanup
            registry3.shutdown();

            System.out.println("\n" + "=".repeat(80));
            System.out.println("   ✅ DATABASE PERSISTENCE TEST PASSED");
            System.out.println("=".repeat(80));

        } catch (Exception e) {
            System.err.println("\n❌ TEST FAILED: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Create registry with test database
     */
    private static StateWalkRegistry<CallMachineContext> createRegistry(ShardConfig shardConfig) {
        System.out.println("\n📋 Creating registry with database: " + TEST_DB_NAME);

        StateWalkRegistry<CallMachineContext> registry = StateWalkBuilder.<CallMachineContext>create(TEST_DB_NAME)
            .withContextClass(CallMachineContext.class)
            .withShardConfig(shardConfig)
            .withPlayback(false)
            .build();

        System.out.println("✅ Registry created successfully");
        return registry;
    }

    /**
     * Insert test data
     */
    private static String insertTestData(StateWalkRegistry<CallMachineContext> registry) {
        System.out.println("\n📝 Inserting test data...");

        GenericStateMachine<CallMachineContext, ?> machine = registry.createOrGetWithGraph(TEST_MACHINE_ID, () -> {
            CallMachineContext context = new CallMachineContext(TEST_MACHINE_ID);

            // Populate with test data
            context.getCall().setCallerNumber("+1-555-1001");
            context.getCall().setCalleeNumber("+1-555-2001");
            context.getCall().setCallStatus("CONNECTED");
            context.getCall().addEvent(new CallEventEntity("INITIATED", "First call"));

            context.getCdr().setDuration(120);
            context.getCdr().setSourceNetwork("NetworkA");
            context.getCdr().setChargeAmount(new BigDecimal("10.50"));

            context.getBillInfo().setAccountNumber("ACC-1001");
            context.getBillInfo().setTotalAmount(new BigDecimal("15.75"));
            context.getBillInfo().setParty(new PartyEntity("Alice Smith", "+1-555-1001"));

            context.getDeviceInfo().setDeviceType("MOBILE");
            context.getDeviceInfo().setModel("iPhone 15");
            context.getDeviceInfo().setNetworkOperator("Verizon");

            return createStateMachine(context);
        });

        // Persist the graph
        registry.persistGraph(TEST_MACHINE_ID);

        String dataSignature = machine.getPersistingEntity().getCall().getCallerNumber();
        System.out.println("✅ Test data inserted with signature: " + dataSignature);
        return dataSignature;
    }

    /**
     * Insert additional data in second session
     */
    private static String insertAdditionalData(StateWalkRegistry<CallMachineContext> registry) {
        String machineId2 = "PERSIST-002";

        GenericStateMachine<CallMachineContext, ?> machine = registry.createOrGetWithGraph(machineId2, () -> {
            CallMachineContext context = new CallMachineContext(machineId2);

            context.getCall().setCallerNumber("+1-555-3001");
            context.getCall().setCalleeNumber("+1-555-4001");
            context.getCdr().setDuration(300);
            context.getBillInfo().setAccountNumber("ACC-2001");

            return createStateMachine(context);
        });

        registry.persistGraph(machineId2);

        String dataSignature = machine.getPersistingEntity().getCall().getCallerNumber();
        System.out.println("✅ Additional data inserted: " + machineId2 + " with signature: " + dataSignature);
        return dataSignature;
    }

    /**
     * Verify database exists
     */
    private static void verifyDatabaseExists(ShardConfig shardConfig) throws SQLException {
        System.out.println("\n🔍 Verifying database existence...");

        String jdbcUrl = String.format("jdbc:mysql://%s:%d/?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
            shardConfig.getHost(), shardConfig.getPort());

        try (Connection conn = DriverManager.getConnection(jdbcUrl,
                shardConfig.getUsername(), shardConfig.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW DATABASES LIKE '" + TEST_DB_NAME + "'")) {

            if (!rs.next()) {
                throw new AssertionError("Database " + TEST_DB_NAME + " does not exist!");
            }

            System.out.println("✅ Database exists: " + TEST_DB_NAME);
        }

        // Also verify tables exist
        verifyTablesExist(shardConfig);
    }

    /**
     * Verify tables exist in database
     */
    private static void verifyTablesExist(ShardConfig shardConfig) throws SQLException {
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
            shardConfig.getHost(), shardConfig.getPort(), TEST_DB_NAME);

        try (Connection conn = DriverManager.getConnection(jdbcUrl,
                shardConfig.getUsername(), shardConfig.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW TABLES")) {

            System.out.println("\n📋 Tables in database:");
            int tableCount = 0;
            while (rs.next()) {
                String tableName = rs.getString(1);
                System.out.println("  - " + tableName);
                tableCount++;
            }

            if (tableCount == 0) {
                throw new AssertionError("No tables found in database!");
            }

            System.out.println("✅ Found " + tableCount + " tables");
        }
    }

    /**
     * Verify data persistence after restart
     */
    private static void verifyDataPersistence(StateWalkRegistry<CallMachineContext> registry, String expectedSignature) {
        System.out.println("\n🔍 Verifying data persistence...");

        // Try to load the machine
        GenericStateMachine<CallMachineContext, ?> machine = registry.createOrGetWithGraph(TEST_MACHINE_ID, () -> {
            // This factory should not be called if data exists
            throw new AssertionError("Machine factory called - data was not persisted!");
        });

        if (machine == null) {
            throw new AssertionError("Machine not found after restart!");
        }

        CallMachineContext context = machine.getPersistingEntity();
        if (context == null) {
            throw new AssertionError("Context is null after restart!");
        }

        // Verify data integrity
        String actualSignature = context.getCall().getCallerNumber();
        if (!expectedSignature.equals(actualSignature)) {
            throw new AssertionError("Data mismatch! Expected: " + expectedSignature + ", Got: " + actualSignature);
        }

        // Verify all entities present
        if (context.getCall() == null || context.getCdr() == null ||
            context.getBillInfo() == null || context.getDeviceInfo() == null) {
            throw new AssertionError("Some entities missing after restart!");
        }

        // Verify ID consistency maintained
        if (!context.validateIdConsistency()) {
            throw new AssertionError("ID consistency lost after restart!");
        }

        System.out.println("✅ Data persisted correctly");
        System.out.println("  - Machine ID: " + context.getId());
        System.out.println("  - Caller: " + context.getCall().getCallerNumber());
        System.out.println("  - Duration: " + context.getCdr().getDuration());
        System.out.println("  - Account: " + context.getBillInfo().getAccountNumber());
        System.out.println("  - Device: " + context.getDeviceInfo().getModel());
        System.out.println("  - ID Consistency: " + context.validateIdConsistency());
    }

    /**
     * Verify all data from multiple sessions
     */
    private static void verifyAllData(StateWalkRegistry<CallMachineContext> registry,
                                     String data1, String data2) {
        System.out.println("\n🔍 Verifying all data from multiple sessions...");

        // Verify first machine
        GenericStateMachine<CallMachineContext, ?> machine1 = registry.getActiveMachine(TEST_MACHINE_ID);
        if (machine1 == null) {
            // Try to rehydrate
            machine1 = registry.createOrGetWithGraph(TEST_MACHINE_ID, () -> {
                throw new AssertionError("Machine 1 data lost!");
            });
        }

        // Verify second machine
        GenericStateMachine<CallMachineContext, ?> machine2 = registry.getActiveMachine("PERSIST-002");
        if (machine2 == null) {
            // Try to rehydrate
            machine2 = registry.createOrGetWithGraph("PERSIST-002", () -> {
                throw new AssertionError("Machine 2 data lost!");
            });
        }

        System.out.println("✅ All data verified from multiple sessions");
        System.out.println("  - Machine 1: " + machine1.getPersistingEntity().getCall().getCallerNumber());
        System.out.println("  - Machine 2: " + machine2.getPersistingEntity().getCall().getCallerNumber());
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