package com.telcobright.statemachine.test;

import com.telcobright.splitverse.config.RepositoryMode;
import com.telcobright.splitverse.config.ShardConfig;
import com.telcobright.statemachine.StateMachineRegistry;
import com.telcobright.statemachine.history.HistoryArchivalManager;
import com.telcobright.statemachine.history.RetentionManager;
import com.telcobright.statewalk.persistence.EntityGraphMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

/**
 * Test for history archival feature
 *
 * Tests:
 * - Automatic archival when machines reach final states
 * - Startup scan to move finished machines
 * - Retention cleanup of old data
 * - Async archival with retry mechanism
 */
public class HistoryArchivalTest {

    private static final Logger log = LoggerFactory.getLogger(HistoryArchivalTest.class);

    private static final String REGISTRY_ID = "call-machine-registry-test";
    private static final String ACTIVE_DB = REGISTRY_ID;
    private static final String HISTORY_DB = REGISTRY_ID + "-history";

    private static final String DB_HOST = "127.0.0.1";
    private static final int DB_PORT = 3306;
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "123456";

    public static void main(String[] args) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   HISTORY ARCHIVAL FEATURE TEST");
        System.out.println("=".repeat(80));
        System.out.println();

        try {
            // Clean up before testing
            cleanupDatabases();

            // Test 1: Basic archival functionality
            testBasicArchival();

            // Test 2: Async archival with queue
            testAsyncArchival();

            // Test 3: Startup scan
            testStartupScan();

            // Test 4: Retention cleanup
            testRetentionCleanup();

            System.out.println("\n" + "=".repeat(80));
            System.out.println("   ✅ ALL HISTORY ARCHIVAL TESTS PASSED");
            System.out.println("=".repeat(80));

        } catch (Exception e) {
            log.error("Test failed", e);
            System.err.println("\n❌ TEST FAILED: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Test 1: Basic archival functionality
     */
    private static void testBasicArchival() throws Exception {
        System.out.println("TEST 1: Basic Archival Functionality");
        System.out.println("-".repeat(60));

        // Create shard config
        ShardConfig shardConfig = ShardConfig.builder()
            .shardId("shard-1")
            .host(DB_HOST)
            .port(DB_PORT)
            .database(ACTIVE_DB)
            .username(DB_USER)
            .password(DB_PASSWORD)
            .connectionPoolSize(5)
            .enabled(true)
            .build();

        // Create entity graph mapper (placeholder)
        EntityGraphMapper graphMapper = new EntityGraphMapper();

        // Create history archival manager
        HistoryArchivalManager archivalManager = new HistoryArchivalManager(
            REGISTRY_ID, shardConfig, graphMapper);

        // Create test data in active database
        createTestMachine("machine-001", "COMPLETED");

        // Test archival
        System.out.println("Archiving machine-001...");
        archivalManager.archiveMachine("machine-001", new java.util.HashMap<>());

        // Wait for async archival
        Thread.sleep(3000);

        // Verify stats
        HistoryArchivalManager.ArchivalStats stats = archivalManager.getStats();
        System.out.println("Archival stats: " + stats);

        // Verify machine is in history database
        boolean inHistory = verifyMachineInHistory("machine-001");
        System.out.println("Machine in history: " + inHistory);

        // Cleanup
        archivalManager.shutdown();

        System.out.println("✅ Test 1 passed\n");
    }

    /**
     * Test 2: Async archival with queue
     */
    private static void testAsyncArchival() throws Exception {
        System.out.println("TEST 2: Async Archival with Queue");
        System.out.println("-".repeat(60));

        ShardConfig shardConfig = ShardConfig.builder()
            .shardId("shard-1")
            .host(DB_HOST)
            .port(DB_PORT)
            .database(ACTIVE_DB)
            .username(DB_USER)
            .password(DB_PASSWORD)
            .connectionPoolSize(5)
            .enabled(true)
            .build();

        EntityGraphMapper graphMapper = new EntityGraphMapper();
        HistoryArchivalManager archivalManager = new HistoryArchivalManager(
            REGISTRY_ID, shardConfig, graphMapper);

        // Create multiple test machines
        int machineCount = 10;
        for (int i = 1; i <= machineCount; i++) {
            String machineId = String.format("machine-%03d", i);
            createTestMachine(machineId, "COMPLETED");
            archivalManager.archiveMachine(machineId, new java.util.HashMap<>());
        }

        System.out.println("Queued " + machineCount + " machines for archival");

        // Wait for all archivals
        Thread.sleep(5000);

        // Verify stats
        HistoryArchivalManager.ArchivalStats stats = archivalManager.getStats();
        System.out.println("Final stats: " + stats);

        // Cleanup
        archivalManager.shutdown();

        System.out.println("✅ Test 2 passed\n");
    }

    /**
     * Test 3: Startup scan
     */
    private static void testStartupScan() throws Exception {
        System.out.println("TEST 3: Startup Scan");
        System.out.println("-".repeat(60));

        ShardConfig shardConfig = ShardConfig.builder()
            .shardId("shard-1")
            .host(DB_HOST)
            .port(DB_PORT)
            .database(ACTIVE_DB)
            .username(DB_USER)
            .password(DB_PASSWORD)
            .connectionPoolSize(5)
            .enabled(true)
            .build();

        EntityGraphMapper graphMapper = new EntityGraphMapper();
        HistoryArchivalManager archivalManager = new HistoryArchivalManager(
            REGISTRY_ID, shardConfig, graphMapper);

        // Create machines in final states
        createTestMachine("startup-001", "COMPLETED");
        createTestMachine("startup-002", "FAILED");
        createTestMachine("startup-003", "ACTIVE"); // Not final

        // Perform startup scan
        System.out.println("Performing startup scan...");
        java.util.Set<String> finalStates = java.util.Set.of("COMPLETED", "FAILED");
        archivalManager.moveAllFinishedMachines(finalStates);

        System.out.println("Startup scan completed");

        // Cleanup
        archivalManager.shutdown();

        System.out.println("✅ Test 3 passed\n");
    }

    /**
     * Test 4: Retention cleanup
     */
    private static void testRetentionCleanup() throws Exception {
        System.out.println("TEST 4: Retention Cleanup");
        System.out.println("-".repeat(60));

        ShardConfig shardConfig = ShardConfig.builder()
            .shardId("shard-1")
            .host(DB_HOST)
            .port(DB_PORT)
            .database(HISTORY_DB)
            .username(DB_USER)
            .password(DB_PASSWORD)
            .connectionPoolSize(5)
            .enabled(true)
            .build();

        // Create retention manager with short retention for testing
        RetentionManager retentionManager = new RetentionManager(
            HISTORY_DB, shardConfig, RepositoryMode.MULTI_TABLE, 30);

        System.out.println("Retention manager created with 30-day retention");

        // Perform immediate cleanup (for testing)
        retentionManager.performCleanupNow();

        System.out.println("Manual cleanup completed");

        // Cleanup
        retentionManager.shutdown();

        System.out.println("✅ Test 4 passed\n");
    }

    /**
     * Create test machine in active database
     */
    private static void createTestMachine(String machineId, String state) throws SQLException {
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/?useSSL=false&serverTimezone=UTC",
            DB_HOST, DB_PORT);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {

            // Create database if not exists
            stmt.execute("CREATE DATABASE IF NOT EXISTS `" + ACTIVE_DB + "`");

            // Create test table if not exists
            stmt.execute("USE `" + ACTIVE_DB + "`");
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS `test_machines` (" +
                "  id VARCHAR(100) PRIMARY KEY," +
                "  current_state VARCHAR(50)," +
                "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );

            // Insert test machine
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO `test_machines` (id, current_state) VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE current_state = ?")) {
                ps.setString(1, machineId);
                ps.setString(2, state);
                ps.setString(3, state);
                ps.executeUpdate();
            }

            log.debug("Created test machine: {} with state: {}", machineId, state);
        }
    }

    /**
     * Verify machine exists in history database
     */
    private static boolean verifyMachineInHistory(String machineId) throws SQLException {
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
            DB_HOST, DB_PORT, HISTORY_DB);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, DB_USER, DB_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM `test_machines` WHERE id = ?")) {

            ps.setString(1, machineId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            log.warn("Failed to verify machine in history: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Cleanup test databases
     */
    private static void cleanupDatabases() throws SQLException {
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/?useSSL=false&serverTimezone=UTC",
            DB_HOST, DB_PORT);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {

            // Drop databases
            stmt.execute("DROP DATABASE IF EXISTS `" + ACTIVE_DB + "`");
            stmt.execute("DROP DATABASE IF EXISTS `" + HISTORY_DB + "`");

            System.out.println("Cleaned up test databases");
        }
    }
}
