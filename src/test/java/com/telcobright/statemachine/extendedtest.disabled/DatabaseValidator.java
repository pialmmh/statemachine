package com.telcobright.statemachine.extendedtest;

import com.telcobright.core.persistence.MysqlConnectionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class DatabaseValidator {
    
    private final MysqlConnectionProvider connectionProvider;
    
    public DatabaseValidator() {
        this.connectionProvider = new MysqlConnectionProvider();
    }
    
    public void clearTestTables() {
        System.out.println("🗑️ Clearing test database tables...");
        
        try (Connection conn = connectionProvider.getConnection()) {
            // Clear registry events table
            clearTable(conn, "registry_concurrent_test_registry", "registry events");
            
            // Clear history tables for test machines
            clearHistoryTables(conn);
            
        } catch (Exception e) {
            System.err.println("❌ Error clearing database tables: " + e.getMessage());
        }
        
        System.out.println("✅ Database cleanup completed\n");
    }
    
    private void clearTable(Connection conn, String tableName, String description) {
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM " + tableName)) {
            int deleted = stmt.executeUpdate();
            System.out.println("✅ Cleared " + deleted + " " + description + " from " + tableName);
        } catch (SQLException e) {
            System.out.println("⚠️ Could not clear " + tableName + " table: " + e.getMessage());
        }
    }
    
    private void clearHistoryTables(Connection conn) {
        try (PreparedStatement stmt = conn.prepareStatement(
            "SELECT TABLE_NAME FROM information_schema.TABLES " +
            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 'history_concurrent_test_%'")) {
            
            ResultSet rs = stmt.executeQuery();
            int tablesCleared = 0;
            
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                clearTable(conn, tableName, "history events");
                tablesCleared++;
            }
            
            System.out.println("✅ Cleared " + tablesCleared + " history tables");
            
        } catch (SQLException e) {
            System.out.println("⚠️ Could not clear history tables: " + e.getMessage());
        }
    }
    
    public int countRegistryEvents() {
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT COUNT(*) FROM registry_concurrent_test_registry");
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getInt(1);
            }
            
        } catch (SQLException e) {
            System.err.println("❌ Error counting registry events: " + e.getMessage());
        }
        
        return 0;
    }
    
    public int countHistoryEvents() {
        int totalCount = 0;
        
        try (Connection conn = connectionProvider.getConnection()) {
            // Get all history tables for concurrent test machines
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT TABLE_NAME FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 'history_concurrent_test_%'")) {
                
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    totalCount += countEventsInTable(conn, tableName);
                }
            }
            
        } catch (SQLException e) {
            System.err.println("❌ Error counting history events: " + e.getMessage());
        }
        
        return totalCount;
    }
    
    private int countEventsInTable(Connection conn, String tableName) {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM " + tableName);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getInt(1);
            }
            
        } catch (SQLException e) {
            System.err.println("❌ Error counting events in " + tableName + ": " + e.getMessage());
        }
        
        return 0;
    }
    
    public Map<String, Integer> getDetailedEventCounts() {
        Map<String, Integer> counts = new HashMap<>();
        
        try (Connection conn = connectionProvider.getConnection()) {
            // Count registry events
            counts.put("registry_events", countRegistryEvents());
            
            // Count events by machine
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT TABLE_NAME FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 'history_concurrent_test_%'")) {
                
                ResultSet rs = stmt.executeQuery();
                int totalHistoryEvents = 0;
                int machineCount = 0;
                
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    int eventsInMachine = countEventsInTable(conn, tableName);
                    totalHistoryEvents += eventsInMachine;
                    machineCount++;
                    
                    // Extract machine ID from table name
                    String machineId = tableName.replace("history_", "").replace("_", "-");
                    counts.put(machineId, eventsInMachine);
                }
                
                counts.put("total_history_events", totalHistoryEvents);
                counts.put("total_machines", machineCount);
            }
            
        } catch (SQLException e) {
            System.err.println("❌ Error getting detailed event counts: " + e.getMessage());
        }
        
        return counts;
    }
    
    public ValidationResult validateLogRates(long totalEventsSent, boolean debugMode) {
        int registryEvents = countRegistryEvents();
        int historyEvents = countHistoryEvents();
        
        double registryRate = (registryEvents * 100.0) / totalEventsSent;
        double historyRate = (historyEvents * 100.0) / totalEventsSent;
        
        ValidationResult result = new ValidationResult();
        result.totalEventsSent = totalEventsSent;
        result.registryEventsLogged = registryEvents;
        result.historyEventsLogged = historyEvents;
        result.registryLogRate = registryRate;
        result.historyLogRate = historyRate;
        
        if (debugMode) {
            // In debug mode, expect 100% logging (allowing for some async delay tolerance)
            result.registryValid = registryRate >= 80.0;
            result.historyValid = historyRate >= 80.0;
            result.expectedRate = 100.0;
        } else {
            // In sampling mode, expect ~50% logging (1-in-2 sampling)
            result.registryValid = Math.abs(registryRate - 50.0) <= 15.0;
            result.historyValid = Math.abs(historyRate - 50.0) <= 15.0;
            result.expectedRate = 50.0;
        }
        
        result.overallValid = result.registryValid && result.historyValid;
        
        return result;
    }
    
    public void close() {
        try {
            connectionProvider.close();
        } catch (Exception e) {
            System.err.println("❌ Error closing database connection: " + e.getMessage());
        }
    }
    
    public static class ValidationResult {
        public long totalEventsSent;
        public int registryEventsLogged;
        public int historyEventsLogged;
        public double registryLogRate;
        public double historyLogRate;
        public double expectedRate;
        public boolean registryValid;
        public boolean historyValid;
        public boolean overallValid;
        
        @Override
        public String toString() {
            return String.format(
                "ValidationResult{events_sent=%d, registry_logged=%d (%.2f%%), history_logged=%d (%.2f%%), " +
                "expected=%.1f%%, registry_valid=%s, history_valid=%s, overall_valid=%s}",
                totalEventsSent, registryEventsLogged, registryLogRate, historyEventsLogged, historyLogRate,
                expectedRate, registryValid, historyValid, overallValid
            );
        }
    }
}