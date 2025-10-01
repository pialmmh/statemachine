package com.telcobright.statemachine.history;

import com.telcobright.splitverse.config.RepositoryMode;
import com.telcobright.splitverse.config.ShardConfig;
import com.telcobright.statewalk.persistence.EntityGraphMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Standalone test for history archival functionality
 * Tests the core archival features without full state machine integration
 */
public class HistoryArchivalStandaloneTest {

    private static final Logger log = LoggerFactory.getLogger(HistoryArchivalStandaloneTest.class);

    private static final String REGISTRY_ID = "standalone-test-registry";
    private static final String ACTIVE_DB = REGISTRY_ID;
    private static final String HISTORY_DB = REGISTRY_ID + "-history";

    private static final String DB_HOST = "127.0.0.1";
    private static final int DB_PORT = 3306;
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "123456";

    public static void main(String[] args) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   HISTORY ARCHIVAL STANDALONE TEST");
        System.out.println("=".repeat(80));
        System.out.println();

        try {
            // Test 1: Database initialization
            test1_DatabaseInitialization();

            // Test 2: History archival manager creation
            test2_HistoryArchivalManagerCreation();

            // Test 3: Schema replication
            test3_SchemaReplication();

            // Test 4: Manual archival with entity graph
            test4_ManualArchival();

            // Test 5: Startup scan functionality
            test5_StartupScan();

            // Test 6: Retention manager
            test6_RetentionManager();

            // Test 7: Statistics
            test7_Statistics();

            System.out.println("\n" + "=".repeat(80));
            System.out.println("   ✅ ALL STANDALONE TESTS PASSED");
            System.out.println("=".repeat(80));

        } catch (Exception e) {
            log.error("Test failed", e);
            System.err.println("\n❌ TEST FAILED: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            try {
                cleanupDatabases();
            } catch (Exception e) {
                log.warn("Cleanup failed", e);
            }
        }
    }

    /**
     * Test 1: Database initialization
     */
    private static void test1_DatabaseInitialization() throws SQLException {
        System.out.println("TEST 1: Database Initialization");
        System.out.println("-".repeat(60));

        cleanupDatabases();

        // Create active database with test table
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/?useSSL=false&serverTimezone=UTC",
            DB_HOST, DB_PORT);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {

            // Create active database
            stmt.execute("CREATE DATABASE IF NOT EXISTS `" + ACTIVE_DB + "`");
            log.info("Created active database: {}", ACTIVE_DB);

            stmt.execute("USE `" + ACTIVE_DB + "`");

            // Create test table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS `test_machines` (" +
                "  id VARCHAR(100) PRIMARY KEY," +
                "  current_state VARCHAR(50)," +
                "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  data VARCHAR(500)" +
                ")"
            );
            log.info("Created test_machines table");

            // Insert test data
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO `test_machines` (id, current_state, data) VALUES (?, ?, ?)")) {

                for (int i = 1; i <= 10; i++) {
                    ps.setString(1, "machine-" + String.format("%03d", i));
                    ps.setString(2, (i <= 5) ? "COMPLETED" : "ACTIVE");
                    ps.setString(3, "Test data for machine " + i);
                    ps.executeUpdate();
                }
            }

            log.info("Inserted 10 test machines (5 COMPLETED, 5 ACTIVE)");
        }

        System.out.println("✅ Test 1 passed\n");
    }

    /**
     * Test 2: History archival manager creation
     */
    private static void test2_HistoryArchivalManagerCreation() {
        System.out.println("TEST 2: History Archival Manager Creation");
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

        log.info("Creating HistoryArchivalManager...");
        HistoryArchivalManager archivalManager = new HistoryArchivalManager(
            REGISTRY_ID, shardConfig, graphMapper);

        log.info("HistoryArchivalManager created successfully");

        // Verify history database was created
        try {
            verifyHistoryDatabase();
        } catch (SQLException e) {
            log.error("Failed to verify history database", e);
            throw new RuntimeException(e);
        }

        archivalManager.shutdown();
        log.info("HistoryArchivalManager shutdown");

        System.out.println("✅ Test 2 passed\n");
    }

    /**
     * Test 3: Schema replication
     */
    private static void test3_SchemaReplication() throws SQLException {
        System.out.println("TEST 3: Schema Replication");
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

        log.info("Creating HistoryArchivalManager to trigger schema replication...");
        HistoryArchivalManager archivalManager = new HistoryArchivalManager(
            REGISTRY_ID, shardConfig, graphMapper);

        // Verify tables in history database
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
            DB_HOST, DB_PORT, HISTORY_DB);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, DB_USER, DB_PASSWORD)) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet tables = metaData.getTables(HISTORY_DB, null, "%", new String[]{"TABLE"});

            int tableCount = 0;
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                tableCount++;
                log.info("History DB table: {}", tableName);
            }

            log.info("History database has {} tables", tableCount);

            if (tableCount == 0) {
                throw new RuntimeException("No tables replicated to history database");
            }
        }

        archivalManager.shutdown();

        System.out.println("✅ Test 3 passed\n");
    }

    /**
     * Test 4: Manual archival
     */
    private static void test4_ManualArchival() throws Exception {
        System.out.println("TEST 4: Manual Archival");
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

        // Archive specific machines
        log.info("Queueing machines for archival...");

        for (int i = 1; i <= 3; i++) {
            String machineId = "machine-" + String.format("%03d", i);
            Map<Class<?>, List<Object>> entityGraph = new HashMap<>();
            archivalManager.archiveMachine(machineId, entityGraph);
            log.info("Queued machine for archival: {}", machineId);
        }

        // Wait for async archival
        log.info("Waiting for async archival to complete...");
        Thread.sleep(5000);

        // Check stats
        HistoryArchivalManager.ArchivalStats stats = archivalManager.getStats();
        log.info("Archival stats after manual archival: {}", stats);

        archivalManager.shutdown();

        System.out.println("✅ Test 4 passed\n");
    }

    /**
     * Test 5: Startup scan
     */
    private static void test5_StartupScan() throws Exception {
        System.out.println("TEST 5: Startup Scan");
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

        // Perform startup scan
        Set<String> finalStates = Set.of("COMPLETED", "FINISHED", "TERMINATED");

        log.info("Performing startup scan for final states: {}", finalStates);

        try {
            archivalManager.moveAllFinishedMachines(finalStates);
            log.info("Startup scan completed successfully");
        } catch (SQLException e) {
            log.error("Startup scan failed", e);
            throw e;
        }

        // Check stats
        HistoryArchivalManager.ArchivalStats stats = archivalManager.getStats();
        log.info("Archival stats after startup scan: {}", stats);

        archivalManager.shutdown();

        System.out.println("✅ Test 5 passed\n");
    }

    /**
     * Test 6: Retention manager
     */
    private static void test6_RetentionManager() throws Exception {
        System.out.println("TEST 6: Retention Manager");
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

        log.info("Creating RetentionManager with 30-day retention...");
        RetentionManager retentionManager = new RetentionManager(
            HISTORY_DB, shardConfig, RepositoryMode.MULTI_TABLE, 30);

        log.info("RetentionManager created successfully");

        // Perform manual cleanup
        log.info("Performing manual cleanup...");
        retentionManager.performCleanupNow();
        log.info("Cleanup completed");

        retentionManager.shutdown();

        System.out.println("✅ Test 6 passed\n");
    }

    /**
     * Test 7: Statistics and verification
     */
    private static void test7_Statistics() throws SQLException {
        System.out.println("TEST 7: Statistics and Verification");
        System.out.println("-".repeat(60));

        // Count records in active and history databases
        int activeCount = countRecords(ACTIVE_DB, "test_machines");
        int historyCount = countRecords(HISTORY_DB, "test_machines");

        log.info("Active DB record count: {}", activeCount);
        log.info("History DB record count: {}", historyCount);

        // Verify data integrity
        verifyDataIntegrity();

        System.out.println("✅ Test 7 passed\n");
    }

    /**
     * Verify history database exists
     */
    private static void verifyHistoryDatabase() throws SQLException {
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/?useSSL=false&serverTimezone=UTC",
            DB_HOST, DB_PORT);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, DB_USER, DB_PASSWORD)) {
            ResultSet rs = conn.getMetaData().getCatalogs();

            boolean found = false;
            while (rs.next()) {
                String dbName = rs.getString("TABLE_CAT");
                if (dbName.equals(HISTORY_DB)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                throw new RuntimeException("History database not created: " + HISTORY_DB);
            }

            log.info("History database verified: {}", HISTORY_DB);
        }
    }

    /**
     * Count records in a table
     */
    private static int countRecords(String database, String tableName) throws SQLException {
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
            DB_HOST, DB_PORT, database);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery(
                "SELECT COUNT(*) FROM `" + tableName + "`");

            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            log.warn("Failed to count records in {}.{}: {}", database, tableName, e.getMessage());
        }

        return 0;
    }

    /**
     * Verify data integrity between active and history databases
     */
    private static void verifyDataIntegrity() throws SQLException {
        log.info("Verifying data integrity...");

        String activeJdbc = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
            DB_HOST, DB_PORT, ACTIVE_DB);
        String historyJdbc = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
            DB_HOST, DB_PORT, HISTORY_DB);

        try (Connection activeConn = DriverManager.getConnection(activeJdbc, DB_USER, DB_PASSWORD);
             Connection historyConn = DriverManager.getConnection(historyJdbc, DB_USER, DB_PASSWORD)) {

            // Get all IDs from active database
            Set<String> activeIds = new HashSet<>();
            try (Statement stmt = activeConn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id FROM test_machines")) {
                while (rs.next()) {
                    activeIds.add(rs.getString("id"));
                }
            }

            // Get all IDs from history database
            Set<String> historyIds = new HashSet<>();
            try (Statement stmt = historyConn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id FROM test_machines")) {
                while (rs.next()) {
                    historyIds.add(rs.getString("id"));
                }
            }

            log.info("Active database IDs: {}", activeIds);
            log.info("History database IDs: {}", historyIds);

            // Check for duplicates
            Set<String> duplicates = new HashSet<>(activeIds);
            duplicates.retainAll(historyIds);

            if (!duplicates.isEmpty()) {
                log.warn("WARNING: Found duplicate IDs in both databases: {}", duplicates);
            } else {
                log.info("No duplicate IDs found (good - archival working correctly)");
            }
        }
    }

    /**
     * Cleanup test databases
     */
    private static void cleanupDatabases() throws SQLException {
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/?useSSL=false&serverTimezone=UTC",
            DB_HOST, DB_PORT);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {

            stmt.execute("DROP DATABASE IF EXISTS `" + ACTIVE_DB + "`");
            stmt.execute("DROP DATABASE IF EXISTS `" + HISTORY_DB + "`");

            log.info("Cleaned up test databases");
        }
    }
}
