package com.telcobright.statemachine.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.io.InputStream;
import java.io.IOException;

/**
 * Test helper for MySQL database operations
 */
public class TestDatabaseHelper {
    
    private static final String CONFIG_FILE = "test-mysql.properties";
    private static Properties config;
    
    static {
        loadConfig();
    }
    
    private static void loadConfig() {
        config = new Properties();
        try (InputStream input = TestDatabaseHelper.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input != null) {
                config.load(input);
            } else {
                throw new RuntimeException("Could not load " + CONFIG_FILE);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load test configuration", e);
        }
    }
    
    public static Connection getConnection() throws SQLException {
        String url = config.getProperty("statemachine.db.url");
        String username = config.getProperty("statemachine.db.username");
        String password = config.getProperty("statemachine.db.password");
        
        return DriverManager.getConnection(url, username, password);
    }
    
    public static void createTestDatabase() throws SQLException {
        String baseUrl = "jdbc:mysql://localhost:3306/";
        String username = config.getProperty("statemachine.db.username");
        String password = config.getProperty("statemachine.db.password");
        
        try (Connection conn = DriverManager.getConnection(baseUrl, username, password);
             Statement stmt = conn.createStatement()) {
            
            // Create test database
            stmt.execute("CREATE DATABASE IF NOT EXISTS statemachine_test");
            stmt.execute("USE statemachine_test");
            
            // Create call_snapshots table
            String createCallTable = """
                CREATE TABLE IF NOT EXISTS call_snapshots (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    machine_id VARCHAR(255) NOT NULL,
                    state VARCHAR(100) NOT NULL,
                    context_data TEXT,
                    timestamp TIMESTAMP NOT NULL,
                    is_offline BOOLEAN DEFAULT FALSE,
                    INDEX idx_machine_id (machine_id),
                    INDEX idx_timestamp (timestamp)
                )
                """;
            stmt.execute(createCallTable);
            
            // Create sms_snapshots table
            String createSmsTable = """
                CREATE TABLE IF NOT EXISTS smsmachine_snapshots (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    machine_id VARCHAR(255) NOT NULL,
                    state VARCHAR(100) NOT NULL,
                    context_data TEXT,
                    timestamp TIMESTAMP NOT NULL,
                    is_offline BOOLEAN DEFAULT FALSE,
                    INDEX idx_machine_id (machine_id),
                    INDEX idx_timestamp (timestamp)
                )
                """;
            stmt.execute(createSmsTable);
        }
    }
    
    public static void cleanupTestData() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.execute("DELETE FROM call_snapshots");
            stmt.execute("DELETE FROM smsmachine_snapshots");
        }
    }
    
    public static void dropTestDatabase() throws SQLException {
        String baseUrl = "jdbc:mysql://localhost:3306/";
        String username = config.getProperty("statemachine.db.username");
        String password = config.getProperty("statemachine.db.password");
        
        try (Connection conn = DriverManager.getConnection(baseUrl, username, password);
             Statement stmt = conn.createStatement()) {
            
            stmt.execute("DROP DATABASE IF EXISTS statemachine_test");
        }
    }
    
    public static CallSnapshotData getCallSnapshot(String machineId) throws SQLException {
        String sql = "SELECT machine_id, state, context_data, timestamp, is_offline " +
                    "FROM call_snapshots WHERE machine_id = ? ORDER BY timestamp DESC LIMIT 1";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, machineId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new CallSnapshotData(
                        rs.getString("machine_id"),
                        rs.getString("state"),
                        rs.getString("context_data"),
                        rs.getTimestamp("timestamp"),
                        rs.getBoolean("is_offline")
                    );
                }
            }
        }
        return null;
    }
    
    public static SmsSnapshotData getSmsSnapshot(String machineId) throws SQLException {
        String sql = "SELECT machine_id, state, context_data, timestamp, is_offline " +
                    "FROM smsmachine_snapshots WHERE machine_id = ? ORDER BY timestamp DESC LIMIT 1";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, machineId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new SmsSnapshotData(
                        rs.getString("machine_id"),
                        rs.getString("state"),
                        rs.getString("context_data"),
                        rs.getTimestamp("timestamp"),
                        rs.getBoolean("is_offline")
                    );
                }
            }
        }
        return null;
    }
    
    // Data classes for test assertions
    public static class CallSnapshotData {
        public final String machineId;
        public final String state;
        public final String contextData;
        public final java.sql.Timestamp timestamp;
        public final boolean isOffline;
        
        public CallSnapshotData(String machineId, String state, String contextData, 
                               java.sql.Timestamp timestamp, boolean isOffline) {
            this.machineId = machineId;
            this.state = state;
            this.contextData = contextData;
            this.timestamp = timestamp;
            this.isOffline = isOffline;
        }
    }
    
    public static class SmsSnapshotData {
        public final String machineId;
        public final String state;
        public final String contextData;
        public final java.sql.Timestamp timestamp;
        public final boolean isOffline;
        
        public SmsSnapshotData(String machineId, String state, String contextData, 
                              java.sql.Timestamp timestamp, boolean isOffline) {
            this.machineId = machineId;
            this.state = state;
            this.contextData = contextData;
            this.timestamp = timestamp;
            this.isOffline = isOffline;
        }
    }
}