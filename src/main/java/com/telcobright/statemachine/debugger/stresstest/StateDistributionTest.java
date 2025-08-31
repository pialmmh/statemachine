package com.telcobright.statemachine.debugger.stresstest;

import com.telcobright.statemachine.*;
import com.telcobright.examples.callmachine.events.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

/**
 * State Distribution Test - Focus on Final State Counts
 * Target: 1000 TPS with detailed state distribution tracking
 */
public class StateDistributionTest {
    private static final int TARGET_TPS = 1000;
    private static final int TEST_DURATION = 10;
    private static final int SETTLE_TIME = 5;
    
    // Track all machines for final state analysis
    private static final Map<String, GenericStateMachine> allMachines = new ConcurrentHashMap<>();
    
    // Statistics
    private static final AtomicLong totalCycles = new AtomicLong();
    private static final AtomicLong totalEvents = new AtomicLong();
    private static final AtomicLong activeMachines = new AtomicLong();
    private static final AtomicLong peakMachines = new AtomicLong();
    
    public static void main(String[] args) throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   STATE DISTRIBUTION TEST - FOCUS ON FINAL STATES");
        System.out.println("=".repeat(80));
        System.out.println("Configuration:");
        System.out.println("  Target: " + TARGET_TPS + " full cycles/sec");
        System.out.println("  Test Duration: " + TEST_DURATION + " seconds");
        System.out.println("  Settle Time: " + SETTLE_TIME + " seconds");
        System.out.println("  Focus: Final state distribution after settlement");
        System.out.println("=".repeat(80) + "\n");
        
        System.out.println("Starting test phase...\n");
        
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(8);
        AtomicBoolean testRunning = new AtomicBoolean(true);
        long startTime = System.currentTimeMillis();
        
        // Progress reporter with state counts
        executor.scheduleAtFixedRate(() -> {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            
            // Quick state count
            Map<String, Integer> currentStates = new HashMap<>();
            for (GenericStateMachine m : allMachines.values()) {
                String state = m.getCurrentState();
                currentStates.put(state, currentStates.getOrDefault(state, 0) + 1);
            }
            
            if (elapsed <= TEST_DURATION) {
                System.out.printf("[TEST] T=%02d/%ds | Cycles: %d | Active: %d | States: ",
                    elapsed, TEST_DURATION, totalCycles.get(), activeMachines.get());
                currentStates.forEach((k, v) -> System.out.printf("%s:%d ", k.substring(0, Math.min(3, k.length())), v));
                System.out.println();
            } else {
                long settleElapsed = elapsed - TEST_DURATION;
                System.out.printf("[SETTLE] T=%d/%ds | Active: %d | States: ",
                    settleElapsed, SETTLE_TIME, activeMachines.get());
                currentStates.forEach((k, v) -> System.out.printf("%s:%d ", k.substring(0, Math.min(3, k.length())), v));
                System.out.println();
            }
        }, 1, 1, TimeUnit.SECONDS);
        
        // Cycle generator - simulates different completion rates
        executor.scheduleAtFixedRate(() -> {
            if (!testRunning.get()) return;
            
            try {
                String id = "call-" + System.nanoTime();
                
                // Create machine
                GenericStateMachine m = new GenericStateMachine(id, null, null);
                
                // Setup ESL state transitions
                m.transition("ADMISSION", IncomingCall.class, "TRYING");
                m.transition("TRYING", SessionProgress.class, "RINGING");
                m.transition("RINGING", Answer.class, "CONNECTED");
                m.transition("RINGING", Hangup.class, "HUNGUP");  // Can hangup during ring
                m.transition("CONNECTED", Hangup.class, "HUNGUP");
                
                m.initialState("ADMISSION");
                m.start();
                
                allMachines.put(id, m);
                activeMachines.incrementAndGet();
                peakMachines.updateAndGet(v -> Math.max(v, activeMachines.get()));
                
                // Simulate realistic call flow with varying completion
                m.fire(new IncomingCall("+1-555-1000", "+1-555-2000"));
                totalEvents.incrementAndGet();
                
                // Randomly simulate different call scenarios
                double rand = ThreadLocalRandom.current().nextDouble();
                
                if (rand < 0.05) {
                    // 5% fail immediately (busy, rejected, etc)
                    // Stay in TRYING
                } else if (rand < 0.15) {
                    // 10% ring but no answer
                    m.fire(new SessionProgress("ringing"));
                    totalEvents.incrementAndGet();
                    
                    executor.schedule(() -> {
                        if (m.getCurrentState().equals("RINGING")) {
                            m.fire(new Hangup());
                            totalEvents.incrementAndGet();
                        }
                    }, 3, TimeUnit.SECONDS);
                } else if (rand < 0.95) {
                    // 80% normal flow - ring, answer, hangup
                    m.fire(new SessionProgress("ringing"));
                    totalEvents.incrementAndGet();
                    
                    m.fire(new Answer());
                    totalEvents.incrementAndGet();
                    
                    // Hangup after random duration
                    executor.schedule(() -> {
                        if (!m.getCurrentState().equals("HUNGUP")) {
                            m.fire(new Hangup());
                            totalEvents.incrementAndGet();
                        }
                    }, ThreadLocalRandom.current().nextInt(100, 500), TimeUnit.MILLISECONDS);
                } else {
                    // 5% long calls - still connected at test end
                    m.fire(new SessionProgress("ringing"));
                    totalEvents.incrementAndGet();
                    
                    m.fire(new Answer());
                    totalEvents.incrementAndGet();
                    // Don't hangup - leave connected
                }
                
                totalCycles.incrementAndGet();
                
            } catch (Exception e) {
                // Ignore
            }
        }, 0, 1000000/TARGET_TPS, TimeUnit.MICROSECONDS);
        
        // Run test
        Thread.sleep(TEST_DURATION * 1000);
        testRunning.set(false);
        
        System.out.println("\n[TEST COMPLETE] Stopped generating events. Waiting " + SETTLE_TIME + " seconds for settlement...\n");
        Thread.sleep(SETTLE_TIME * 1000);
        
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);
        
        // DETAILED FINAL STATE ANALYSIS
        Map<String, Integer> finalStateCounts = new TreeMap<>();
        finalStateCounts.put("ADMISSION", 0);
        finalStateCounts.put("TRYING", 0);
        finalStateCounts.put("RINGING", 0);
        finalStateCounts.put("CONNECTED", 0);
        finalStateCounts.put("HUNGUP", 0);
        
        int totalMachines = 0;
        for (GenericStateMachine m : allMachines.values()) {
            String state = m.getCurrentState();
            finalStateCounts.put(state, finalStateCounts.getOrDefault(state, 0) + 1);
            totalMachines++;
        }
        
        // Calculate metrics
        double actualTPS = totalCycles.get() / (double) TEST_DURATION;
        double actualEPS = totalEvents.get() / (double) TEST_DURATION;
        
        // Generate timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        
        // Print and write comprehensive state report
        printStateReport(timestamp, actualTPS, actualEPS, totalMachines, finalStateCounts);
        writeStateReport(timestamp, actualTPS, actualEPS, totalMachines, finalStateCounts);
        
        System.out.println("\n📄 Report saved to: last_tps_test_report.txt");
        System.out.println("=".repeat(80));
        
        System.exit(0);
    }
    
    private static void printStateReport(
        String timestamp, double actualTPS, double actualEPS,
        int totalMachines, Map<String, Integer> finalStateCounts
    ) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   FINAL STATE DISTRIBUTION REPORT");
        System.out.println("=".repeat(80));
        System.out.println("Generated: " + timestamp);
        System.out.println();
        
        // Performance Summary
        System.out.println("📊 Performance Summary");
        System.out.println();
        System.out.printf("  Actual TPS: %,.0f cycles/sec\n", actualTPS);
        System.out.printf("  Actual EPS: %,.0f events/sec\n", actualEPS);
        System.out.printf("  Total Cycles: %,d\n", totalCycles.get());
        System.out.printf("  Total Events: %,d\n", totalEvents.get());
        System.out.printf("  Total Machines: %,d\n", totalMachines);
        System.out.printf("  Peak Concurrent: %,d\n", peakMachines.get());
        System.out.println();
        
        // THE MOST IMPORTANT TABLE - Final State Distribution
        System.out.println("🎯 FINAL STATE DISTRIBUTION (After " + SETTLE_TIME + "s Settlement)");
        System.out.println("=" + "=".repeat(79));
        System.out.println();
        System.out.println("  ┌────────────┬──────────┬────────────┬──────────────────────────────────────┐");
        System.out.println("  │ State      │   Count  │ Percentage │ Bar Graph                            │");
        System.out.println("  ├────────────┼──────────┼────────────┼──────────────────────────────────────┤");
        
        // Sort by count descending
        List<Map.Entry<String, Integer>> sortedStates = new ArrayList<>(finalStateCounts.entrySet());
        sortedStates.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        for (Map.Entry<String, Integer> entry : sortedStates) {
            double percent = totalMachines > 0 ? (entry.getValue() * 100.0) / totalMachines : 0;
            String bar = generateBar(percent, 38);
            System.out.printf("  │ %-10s │ %,8d │ %9.2f%% │ %-36s │\n",
                entry.getKey(), entry.getValue(), percent, bar);
        }
        
        System.out.println("  ├────────────┼──────────┼────────────┼──────────────────────────────────────┤");
        System.out.printf("  │ TOTAL      │ %,8d │ %9.2f%% │ %-36s │\n",
            totalMachines, 100.0, "████████████████████████████████████");
        System.out.println("  └────────────┴──────────┴────────────┴──────────────────────────────────────┘");
        System.out.println();
        
        // Detailed State Analysis
        System.out.println("📈 State Distribution Analysis");
        System.out.println();
        System.out.println("  | State      | Count    | % of Total | Expected Range | Status | Notes              |");
        System.out.println("  |------------|----------|------------|----------------|--------|--------------------|");
        
        for (Map.Entry<String, Integer> entry : sortedStates) {
            double percent = totalMachines > 0 ? (entry.getValue() * 100.0) / totalMachines : 0;
            String expected = getExpectedRange(entry.getKey());
            String status = isInExpectedRange(entry.getKey(), percent) ? "✅" : "⚠️";
            String notes = getStateNotes(entry.getKey(), percent);
            
            System.out.printf("  | %-10s | %,8d | %9.2f%% | %-14s | %s    | %-18s |\n",
                entry.getKey(), entry.getValue(), percent, expected, status, notes);
        }
        System.out.println();
        
        // Summary Statistics
        System.out.println("📊 Summary Statistics");
        System.out.println();
        System.out.println("  | Metric                        | Value      | Interpretation                  |");
        System.out.println("  |-------------------------------|------------|---------------------------------|");
        
        int completed = finalStateCounts.getOrDefault("HUNGUP", 0);
        int inProgress = totalMachines - completed;
        double completionRate = totalMachines > 0 ? (completed * 100.0) / totalMachines : 0;
        
        System.out.printf("  | Completed Calls (HUNGUP)      | %,10d | Fully processed calls           |\n", completed);
        System.out.printf("  | In-Progress Calls             | %,10d | Still active in pipeline        |\n", inProgress);
        System.out.printf("  | Completion Rate               | %9.2f%% | Percentage fully processed      |\n", completionRate);
        System.out.printf("  | Admission Queue (ADMISSION)   | %,10d | Waiting for processing          |\n",
            finalStateCounts.getOrDefault("ADMISSION", 0));
        System.out.printf("  | Setup Phase (TRYING)          | %,10d | In call setup                   |\n",
            finalStateCounts.getOrDefault("TRYING", 0));
        System.out.printf("  | Ringing (RINGING)             | %,10d | Alerting phase                  |\n",
            finalStateCounts.getOrDefault("RINGING", 0));
        System.out.printf("  | Active Calls (CONNECTED)      | %,10d | Currently connected             |\n",
            finalStateCounts.getOrDefault("CONNECTED", 0));
        
        System.out.println();
        System.out.println("=".repeat(80));
    }
    
    private static void writeStateReport(
        String timestamp, double actualTPS, double actualEPS,
        int totalMachines, Map<String, Integer> finalStateCounts
    ) throws IOException {
        try (PrintWriter w = new PrintWriter(new FileWriter("last_tps_test_report.txt", false))) {
            w.println("FINAL STATE DISTRIBUTION REPORT");
            w.println("=".repeat(80));
            w.println("Generated: " + timestamp);
            w.println();
            
            w.println("Performance Summary");
            w.println("-".repeat(40));
            w.printf("Actual TPS: %,.0f cycles/sec\n", actualTPS);
            w.printf("Actual EPS: %,.0f events/sec\n", actualEPS);
            w.printf("Total Cycles: %,d\n", totalCycles.get());
            w.printf("Total Events: %,d\n", totalEvents.get());
            w.printf("Total Machines: %,d\n", totalMachines);
            w.printf("Peak Concurrent: %,d\n", peakMachines.get());
            w.println();
            
            w.println("FINAL STATE DISTRIBUTION (After " + SETTLE_TIME + "s Settlement)");
            w.println("=".repeat(80));
            w.println();
            w.println("| State      |   Count  | Percentage | Visual                               |");
            w.println("|------------|----------|------------|--------------------------------------|");
            
            // Sort by count descending
            List<Map.Entry<String, Integer>> sortedStates = new ArrayList<>(finalStateCounts.entrySet());
            sortedStates.sort((a, b) -> b.getValue().compareTo(a.getValue()));
            
            for (Map.Entry<String, Integer> entry : sortedStates) {
                double percent = totalMachines > 0 ? (entry.getValue() * 100.0) / totalMachines : 0;
                String bar = generateBar(percent, 38);
                w.printf("| %-10s | %,8d | %9.2f%% | %-36s |\n",
                    entry.getKey(), entry.getValue(), percent, bar);
            }
            
            w.println("|------------|----------|------------|--------------------------------------|");
            w.printf("| TOTAL      | %,8d | %9.2f%% | %-36s |\n",
                totalMachines, 100.0, "████████████████████████████████████");
            w.println();
            
            w.println("State Distribution Analysis");
            w.println("-".repeat(80));
            w.println();
            w.println("| State      | Count    | % of Total | Expected Range | Status | Notes              |");
            w.println("|------------|----------|------------|----------------|--------|--------------------|");
            
            for (Map.Entry<String, Integer> entry : sortedStates) {
                double percent = totalMachines > 0 ? (entry.getValue() * 100.0) / totalMachines : 0;
                String expected = getExpectedRange(entry.getKey());
                String status = isInExpectedRange(entry.getKey(), percent) ? "✅" : "⚠️";
                String notes = getStateNotes(entry.getKey(), percent);
                
                w.printf("| %-10s | %,8d | %9.2f%% | %-14s | %s    | %-18s |\n",
                    entry.getKey(), entry.getValue(), percent, expected, status, notes);
            }
            w.println();
            
            w.println("Summary Statistics");
            w.println("-".repeat(80));
            w.println();
            
            int completed = finalStateCounts.getOrDefault("HUNGUP", 0);
            int inProgress = totalMachines - completed;
            double completionRate = totalMachines > 0 ? (completed * 100.0) / totalMachines : 0;
            
            w.printf("Completed Calls (HUNGUP): %,d (%.2f%%)\n", completed, completionRate);
            w.printf("In-Progress Calls: %,d (%.2f%%)\n", inProgress, 100 - completionRate);
            w.printf("Admission Queue: %,d\n", finalStateCounts.getOrDefault("ADMISSION", 0));
            w.printf("Setup Phase (TRYING): %,d\n", finalStateCounts.getOrDefault("TRYING", 0));
            w.printf("Ringing: %,d\n", finalStateCounts.getOrDefault("RINGING", 0));
            w.printf("Active Calls (CONNECTED): %,d\n", finalStateCounts.getOrDefault("CONNECTED", 0));
            
            w.println();
            w.println("=".repeat(80));
            w.println("END OF REPORT");
        }
    }
    
    private static String generateBar(double percent, int maxWidth) {
        int filled = (int) ((percent / 100.0) * maxWidth);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < filled; i++) {
            bar.append("█");
        }
        for (int i = filled; i < maxWidth; i++) {
            bar.append("░");
        }
        return bar.toString();
    }
    
    private static String getExpectedRange(String state) {
        switch (state) {
            case "ADMISSION": return "0-5%";
            case "TRYING": return "0-10%";
            case "RINGING": return "5-20%";
            case "CONNECTED": return "10-30%";
            case "HUNGUP": return "50-80%";
            default: return "N/A";
        }
    }
    
    private static boolean isInExpectedRange(String state, double percent) {
        switch (state) {
            case "ADMISSION": return percent <= 5;
            case "TRYING": return percent <= 10;
            case "RINGING": return percent >= 5 && percent <= 20;
            case "CONNECTED": return percent >= 10 && percent <= 30;
            case "HUNGUP": return percent >= 50 && percent <= 80;
            default: return true;
        }
    }
    
    private static String getStateNotes(String state, double percent) {
        switch (state) {
            case "ADMISSION":
                return percent > 5 ? "High queue" : "Normal";
            case "TRYING":
                return percent > 10 ? "Setup delays" : "Normal";
            case "RINGING":
                return "Alerting phase";
            case "CONNECTED":
                return percent > 30 ? "Many active" : "Normal";
            case "HUNGUP":
                return percent < 50 ? "Low completion" : "Good completion";
            default:
                return "-";
        }
    }
}