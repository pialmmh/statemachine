package com.telcobright.statemachine.test;

import java.sql.*;
import java.util.Properties;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Test Database Manager for automated testing with MySQL
 * Handles database setup, teardown, and test data management
 */
public class TestDatabaseManager {
    
    private static final String PROPERTIES_FILE = "/database.properties";
    private static final String SCHEMA_FILE = "src/test/resources/test-schema.sql";
    
    private Connection connection;
    private Properties dbProperties;
    
    public TestDatabaseManager() throws Exception {
        loadDatabaseProperties();
        initializeDatabase();
    }
    
    /**
     * Load database configuration from properties file
     */
    private void loadDatabaseProperties() throws Exception {
        dbProperties = new Properties();
        try (InputStream input = getClass().getResourceAsStream(PROPERTIES_FILE)) {
            if (input == null) {
                throw new RuntimeException("Unable to find " + PROPERTIES_FILE);
            }
            dbProperties.load(input);
        }
    }
    
    /**
     * Initialize database connection and schema
     */
    public void initializeDatabase() throws Exception {
        String url = dbProperties.getProperty("db.url");
        String username = dbProperties.getProperty("db.username");
        String password = dbProperties.getProperty("db.password");
        
        System.out.println("🔗 Connecting to MySQL: " + url);
        
        // Load MySQL driver
        Class.forName(dbProperties.getProperty("db.driver"));
        
        // Create connection
        connection = DriverManager.getConnection(url, username, password);
        connection.setAutoCommit(true);
        
        System.out.println("✅ Connected to MySQL successfully");
        
        // Initialize schema if requested
        if ("true".equals(dbProperties.getProperty("db.test.recreateOnStartup"))) {
            initializeSchema();
        }
    }
    
    /**
     * Initialize database schema from SQL file
     */
    private void initializeSchema() throws Exception {
        System.out.println("🔧 Initializing database schema...");
        
        String schemaSQL = new String(Files.readAllBytes(Paths.get(SCHEMA_FILE)));
        String[] statements = schemaSQL.split(";");
        
        try (Statement stmt = connection.createStatement()) {
            for (String sql : statements) {
                sql = sql.trim();
                if (!sql.isEmpty() && !sql.startsWith("--")) {
                    try {
                        stmt.execute(sql);
                    } catch (SQLException e) {
                        // Log but continue - some statements might fail harmlessly
                        if (dbProperties.getProperty("db.test.enableLogging", "false").equals("true")) {
                            System.out.println("⚠️ SQL Warning: " + e.getMessage());
                        }
                    }
                }
            }
        }
        
        System.out.println("✅ Database schema initialized");
    }
    
    /**
     * Clean test data for a specific test run
     */
    public void cleanTestData(String testRunId) throws SQLException {
        String sql = "CALL CleanTestData(?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, testRunId);
            stmt.execute();
            System.out.println("🧹 Cleaned test data for run: " + testRunId);
        }
    }
    
    /**
     * Get test summary for a test run
     */
    public void printTestSummary(String testRunId) throws SQLException {
        String sql = "CALL GetTestSummary(?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, testRunId);
            try (ResultSet rs = stmt.executeQuery()) {
                System.out.println("\n📊 === TEST EXECUTION SUMMARY ===");
                System.out.println("Test Run ID: " + testRunId);
                System.out.println("─".repeat(60));
                
                while (rs.next()) {
                    String testType = rs.getString("test_type");
                    int totalTests = rs.getInt("total_tests");
                    int passedTests = rs.getInt("passed_tests");
                    int failedTests = rs.getInt("failed_tests");
                    int totalAssertions = rs.getInt("total_assertions");
                    int passedAssertions = rs.getInt("passed_assertions");
                    double avgExecutionMs = rs.getDouble("avg_execution_ms");
                    
                    System.out.printf("📋 %s: %d/%d tests passed (%.1f%%) | %d/%d assertions | %.2fms avg%n",
                        testType, passedTests, totalTests, 
                        (totalTests > 0 ? (passedTests * 100.0 / totalTests) : 0),
                        passedAssertions, totalAssertions, avgExecutionMs);
                }
                System.out.println("─".repeat(60));
            }
        }
    }
    
    /**
     * Validate state machine integrity for a test run
     */
    public boolean validateStateMachineIntegrity(String testRunId, String machineType) throws SQLException {
        String sql = "CALL ValidateStateMachineIntegrity(?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, testRunId);
            stmt.setString(2, machineType);
            
            try (ResultSet rs = stmt.executeQuery()) {
                boolean allValid = true;
                while (rs.next()) {
                    String validity = rs.getString("transition_validity");
                    if ("INVALID".equals(validity)) {
                        allValid = false;
                        System.out.printf("❌ Invalid transition: %s -> %s (Event: %s)%n",
                            rs.getString("previous_state"),
                            rs.getString("state_name"),
                            rs.getString("transition_event"));
                    }
                }
                return allValid;
            }
        }
    }
    
    /**
     * Log test execution
     */
    public void logTestExecution(String testRunId, String testClass, String testMethod, 
                                String testType, String machineId, String status, 
                                String errorMessage, int assertionsCount, int assertionsPassed) throws SQLException {
        String sql = "INSERT INTO test_execution_log (test_run_id, test_class, test_method, test_type, " +
                    "machine_id, end_time, status, error_message, assertions_count, assertions_passed) " +
                    "VALUES (?, ?, ?, ?, ?, NOW(), ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, testRunId);
            stmt.setString(2, testClass);
            stmt.setString(3, testMethod);
            stmt.setString(4, testType);
            stmt.setString(5, machineId);
            stmt.setString(6, status);
            stmt.setString(7, errorMessage);
            stmt.setInt(8, assertionsCount);
            stmt.setInt(9, assertionsPassed);
            stmt.executeUpdate();
        }
    }
    
    /**
     * Save state snapshot during testing
     */
    public void saveStateSnapshot(String testRunId, String machineId, String machineType,
                                 String stateName, String persistentData, String volatileContext,
                                 String transitionEvent, String testNotes) throws SQLException {
        String tableName = "SMS".equals(machineType) ? "sms_state_snapshots" : "call_state_snapshots";
        String idColumn = "SMS".equals(machineType) ? "sms_id" : "call_id";
        
        String sql = String.format(
            "INSERT INTO %s (test_run_id, %s, state_name, persistent_data, volatile_context, " +
            "transition_event, test_notes) VALUES (?, ?, ?, ?, ?, ?, ?)",
            tableName, idColumn);
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, testRunId);
            stmt.setString(2, machineId);
            stmt.setString(3, stateName);
            stmt.setString(4, persistentData);
            stmt.setString(5, volatileContext);
            stmt.setString(6, transitionEvent);
            stmt.setString(7, testNotes);
            stmt.executeUpdate();
        }
    }
    
    /**
     * Get snapshots for a specific machine
     */
    public ResultSet getSnapshots(String testRunId, String machineId, String machineType) throws SQLException {
        String tableName = "SMS".equals(machineType) ? "sms_state_snapshots" : "call_state_snapshots";
        String idColumn = "SMS".equals(machineType) ? "sms_id" : "call_id";
        
        String sql = String.format(
            "SELECT * FROM %s WHERE test_run_id = ? AND %s = ? ORDER BY transition_timestamp",
            tableName, idColumn);
        
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setString(1, testRunId);
        stmt.setString(2, machineId);
        return stmt.executeQuery();
    }
    
    /**
     * Get database connection for custom queries
     */
    public Connection getConnection() {
        return connection;
    }
    
    /**
     * Close database connection
     */
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
            System.out.println("🔌 Database connection closed");
        }
    }
    
    /**
     * Execute custom SQL for testing
     */
    public ResultSet executeQuery(String sql, Object... params) throws SQLException {
        PreparedStatement stmt = connection.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }
        return stmt.executeQuery();
    }
    
    /**
     * Execute update SQL for testing
     */
    public int executeUpdate(String sql, Object... params) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            return stmt.executeUpdate();
        }
    }
    
    /**
     * Check if database is available and responsive
     */
    public boolean isDatabaseAvailable() {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(5);
        } catch (SQLException e) {
            return false;
        }
    }
    
    /**
     * Get database metadata for reporting
     */
    public String getDatabaseInfo() throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        return String.format("MySQL %s | Driver: %s | URL: %s",
            metaData.getDatabaseProductVersion(),
            metaData.getDriverVersion(),
            connection.getMetaData().getURL().replaceAll("password=[^&]*", "password=***"));
    }
}