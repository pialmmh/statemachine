package com.telcobright.statemachine.extendedtest;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TestReporter {
    
    private final ConcurrentTestConfig config;
    private final long testStartTime;
    
    public TestReporter(ConcurrentTestConfig config) {
        this.config = config;
        this.testStartTime = System.currentTimeMillis();
    }
    
    public void generateReport(int machinesCreated, long totalEventsSent, int failedMachines, 
                             long totalTestTime, ConcurrentHashMap<String, AtomicInteger> machineEventCounts) {
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("📊 CONCURRENT REGISTRY TEST REPORT");
        System.out.println("=".repeat(80));
        System.out.println("Test Date: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println("Test Duration: " + formatDuration(totalTestTime));
        System.out.println();
        
        // Test Configuration
        printSectionHeader("🔧 Test Configuration");
        System.out.println("Machine Count: " + config.getMachineCount());
        System.out.println("Thread Pool Size: " + config.getThreadPoolSize());
        System.out.println("Event Scheduler Threads: " + config.getEventSchedulerThreads());
        System.out.println("Debug Mode: " + (config.isDebugMode() ? "ENABLED" : "DISABLED"));
        System.out.println("Database Validation: " + (config.isValidateDatabase() ? "ENABLED" : "DISABLED"));
        System.out.println("Registry ID: " + config.getRegistryId());
        System.out.println("WebSocket Port: " + config.getWebSocketPort());
        System.out.println();
        
        // Machine Creation Results
        printSectionHeader("🏗️ Machine Creation Results");
        System.out.println("Target Machines: " + config.getMachineCount());
        System.out.println("Successfully Created: " + machinesCreated);
        System.out.println("Failed to Create: " + failedMachines);
        double creationSuccessRate = (machinesCreated * 100.0) / config.getMachineCount();
        System.out.println("Creation Success Rate: " + String.format("%.2f%%", creationSuccessRate));
        System.out.println();
        
        // Event Processing Results
        printSectionHeader("🚀 Event Processing Results");
        System.out.println("Total Events Sent: " + totalEventsSent);
        if (totalTestTime > 0) {
            double eventsPerSecond = (totalEventsSent * 1000.0) / totalTestTime;
            System.out.println("Events Per Second: " + String.format("%.2f", eventsPerSecond));
        }
        
        // Calculate event distribution statistics
        if (!machineEventCounts.isEmpty()) {
            int minEvents = machineEventCounts.values().stream().mapToInt(AtomicInteger::get).min().orElse(0);
            int maxEvents = machineEventCounts.values().stream().mapToInt(AtomicInteger::get).max().orElse(0);
            double avgEvents = machineEventCounts.values().stream().mapToDouble(AtomicInteger::get).average().orElse(0.0);
            
            System.out.println("Events per Machine (Min/Avg/Max): " + minEvents + " / " + 
                             String.format("%.1f", avgEvents) + " / " + maxEvents);
        }
        System.out.println();
        
        // Performance Analysis
        printSectionHeader("⚡ Performance Analysis");
        if (totalTestTime > 0) {
            double machinesPerSecond = (machinesCreated * 1000.0) / totalTestTime;
            System.out.println("Machine Creation Rate: " + String.format("%.2f machines/second", machinesPerSecond));
        }
        
        // Memory usage approximation
        Runtime runtime = Runtime.getRuntime();
        long usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemoryMB = runtime.maxMemory() / (1024 * 1024);
        System.out.println("Memory Usage: " + usedMemoryMB + " MB / " + maxMemoryMB + " MB");
        
        boolean memoryWithinLimits = usedMemoryMB <= ConcurrentTestConfig.MAX_MEMORY_USAGE_MB;
        System.out.println("Memory Within Limits: " + (memoryWithinLimits ? "✅ YES" : "❌ NO"));
        System.out.println();
        
        // Test Success Criteria
        printSectionHeader("✅ Test Success Criteria");
        boolean machineCreationSuccess = creationSuccessRate >= 95.0;
        boolean memorySuccess = memoryWithinLimits;
        boolean eventSuccess = totalEventsSent > 0;
        
        System.out.println("Machine Creation Success (≥95%): " + formatResult(machineCreationSuccess));
        System.out.println("Memory Usage Within Limits: " + formatResult(memorySuccess));
        System.out.println("Events Successfully Sent: " + formatResult(eventSuccess));
        
        boolean overallSuccess = machineCreationSuccess && memorySuccess && eventSuccess;
        System.out.println();
        System.out.println("OVERALL TEST RESULT: " + formatResult(overallSuccess));
        
        if (overallSuccess) {
            System.out.println("🎉 CONCURRENT TEST PASSED!");
        } else {
            System.out.println("❌ CONCURRENT TEST FAILED!");
            printFailureAnalysis(machineCreationSuccess, memorySuccess, eventSuccess);
        }
        
        System.out.println("=".repeat(80));
    }
    
    public void generateDatabaseValidationReport(DatabaseValidator.ValidationResult result) {
        System.out.println();
        printSectionHeader("🗄️ Database Validation Report");
        System.out.println("Total Events Sent: " + result.totalEventsSent);
        System.out.println("Registry Events Logged: " + result.registryEventsLogged + " (" + 
                         String.format("%.2f%%", result.registryLogRate) + ")");
        System.out.println("History Events Logged: " + result.historyEventsLogged + " (" + 
                         String.format("%.2f%%", result.historyLogRate) + ")");
        System.out.println("Expected Log Rate: " + String.format("%.1f%%", result.expectedRate));
        System.out.println();
        System.out.println("Registry Validation: " + formatResult(result.registryValid));
        System.out.println("History Validation: " + formatResult(result.historyValid));
        System.out.println("Overall Database Validation: " + formatResult(result.overallValid));
        
        if (!result.overallValid) {
            System.out.println();
            System.out.println("⚠️ Database validation failed:");
            if (!result.registryValid) {
                System.out.println("   - Registry log rate (" + String.format("%.2f%%", result.registryLogRate) + 
                                 ") not within expected range");
            }
            if (!result.historyValid) {
                System.out.println("   - History log rate (" + String.format("%.2f%%", result.historyLogRate) + 
                                 ") not within expected range");
            }
        }
        System.out.println();
    }
    
    public void generateQuickStatusReport(int machinesCreated, long eventsSent, String currentPhase) {
        System.out.println("\n📊 Status Update - " + currentPhase);
        System.out.println("Machines Created: " + machinesCreated + "/" + config.getMachineCount());
        System.out.println("Events Sent: " + eventsSent);
        System.out.println("Elapsed Time: " + formatDuration(System.currentTimeMillis() - testStartTime));
    }
    
    private void printSectionHeader(String title) {
        System.out.println(title);
        System.out.println("-".repeat(Math.max(30, title.length())));
    }
    
    private String formatResult(boolean success) {
        return success ? "✅ PASS" : "❌ FAIL";
    }
    
    private String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }
    
    private void printFailureAnalysis(boolean machineSuccess, boolean memorySuccess, boolean eventSuccess) {
        System.out.println();
        System.out.println("🔍 Failure Analysis:");
        
        if (!machineSuccess) {
            System.out.println("❌ Machine creation success rate below threshold (95%)");
            System.out.println("   Recommendation: Increase timeout, check database connectivity, review thread pool size");
        }
        
        if (!memorySuccess) {
            System.out.println("❌ Memory usage exceeded limits (" + ConcurrentTestConfig.MAX_MEMORY_USAGE_MB + " MB)");
            System.out.println("   Recommendation: Reduce machine count, optimize memory usage, increase heap size");
        }
        
        if (!eventSuccess) {
            System.out.println("❌ No events were successfully sent");
            System.out.println("   Recommendation: Check machine creation, event scheduler configuration");
        }
    }
}