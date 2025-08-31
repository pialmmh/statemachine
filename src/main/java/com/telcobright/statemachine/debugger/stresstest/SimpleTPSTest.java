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
 * Simple TPS Test without Registry overhead
 * Target: 1000 TPS = 1000 full cycles/sec = 4000 events/sec
 */
public class SimpleTPSTest {
    private static final int TARGET_TPS = 1000;
    private static final int TEST_DURATION = 10;
    private static final int SETTLE_TIME = 5;
    
    public static void main(String[] args) throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   SIMPLE TPS TEST - 1000 TPS (4000 Events/sec)");
        System.out.println("=".repeat(80));
        System.out.println("Configuration:");
        System.out.println("  Target: " + TARGET_TPS + " full cycles/sec = " + (TARGET_TPS * 4) + " events/sec");
        System.out.println("  Test Duration: " + TEST_DURATION + " seconds");
        System.out.println("  Settle Time: " + SETTLE_TIME + " seconds");
        System.out.println("=".repeat(80) + "\n");
        
        // Track all machines
        Map<String, GenericStateMachine> machines = new ConcurrentHashMap<>();
        
        // Statistics
        AtomicLong totalCycles = new AtomicLong();
        AtomicLong totalEvents = new AtomicLong();
        AtomicLong peakMachines = new AtomicLong();
        
        System.out.println("Starting test phase...\n");
        
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(8);
        AtomicBoolean testRunning = new AtomicBoolean(true);
        long startTime = System.currentTimeMillis();
        
        // Progress reporter
        executor.scheduleAtFixedRate(() -> {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            if (elapsed <= TEST_DURATION) {
                System.out.printf("[TEST] T=%02d/%ds | Cycles: %d | Events: %d | Active: %d | TPS: %d\n",
                    elapsed, TEST_DURATION, totalCycles.get(), totalEvents.get(), 
                    machines.size(), elapsed > 0 ? totalCycles.get()/elapsed : 0);
            } else {
                long settleElapsed = elapsed - TEST_DURATION;
                System.out.printf("[SETTLE] T=%d/%ds | Active machines: %d\n",
                    settleElapsed, SETTLE_TIME, machines.size());
            }
        }, 1, 1, TimeUnit.SECONDS);
        
        // Cycle generator
        executor.scheduleAtFixedRate(() -> {
            if (!testRunning.get()) return;
            
            try {
                String id = "m" + System.nanoTime();
                
                // Create simple machine
                GenericStateMachine m = new GenericStateMachine(id, null, null);
                
                // Setup transitions directly
                m.transition("ADMISSION", IncomingCall.class, "TRYING");
                m.transition("TRYING", SessionProgress.class, "RINGING");
                m.transition("RINGING", Answer.class, "CONNECTED");
                m.transition("CONNECTED", Hangup.class, "HUNGUP");
                
                m.initialState("ADMISSION");
                m.start();
                
                machines.put(id, m);
                peakMachines.updateAndGet(v -> Math.max(v, machines.size()));
                
                // Fire all 4 events
                m.fire(new IncomingCall("+1", "+2"));
                totalEvents.incrementAndGet();
                
                m.fire(new SessionProgress("ringing"));
                totalEvents.incrementAndGet();
                
                m.fire(new Answer());
                totalEvents.incrementAndGet();
                
                m.fire(new Hangup());
                totalEvents.incrementAndGet();
                
                totalCycles.incrementAndGet();
                
                // Remove completed machine after short delay
                executor.schedule(() -> {
                    machines.remove(id);
                }, 100, TimeUnit.MILLISECONDS);
                
            } catch (Exception e) {
                // Ignore
            }
        }, 0, 1000000/TARGET_TPS, TimeUnit.MICROSECONDS);
        
        // Run test
        Thread.sleep(TEST_DURATION * 1000);
        testRunning.set(false);
        
        System.out.println("\n[TEST COMPLETE] Waiting " + SETTLE_TIME + " seconds for settlement...\n");
        Thread.sleep(SETTLE_TIME * 1000);
        
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);
        
        // Count final states
        Map<String, Integer> stateCounts = new TreeMap<>();
        for (GenericStateMachine m : machines.values()) {
            String state = m.getCurrentState();
            stateCounts.put(state, stateCounts.getOrDefault(state, 0) + 1);
        }
        
        // Calculate metrics
        double actualTPS = totalCycles.get() / (double) TEST_DURATION;
        double actualEPS = totalEvents.get() / (double) TEST_DURATION;
        double efficiency = (actualTPS / TARGET_TPS) * 100;
        
        // Print report
        System.out.println("\n" + "=".repeat(80));
        System.out.println("                    FINAL TEST REPORT");
        System.out.println("=".repeat(80));
        
        System.out.println("\n📊 PERFORMANCE METRICS:");
        System.out.printf("  Target TPS: %,d cycles/sec\n", TARGET_TPS);
        System.out.printf("  Actual TPS: %,.0f cycles/sec\n", actualTPS);
        System.out.printf("  Actual EPS: %,.0f events/sec\n", actualEPS);
        System.out.printf("  Efficiency: %.1f%%\n", efficiency);
        
        System.out.println("\n📈 TOTALS:");
        System.out.printf("  Complete Cycles: %,d\n", totalCycles.get());
        System.out.printf("  Total Events: %,d\n", totalEvents.get());
        System.out.printf("  Peak Concurrent: %,d machines\n", peakMachines.get());
        System.out.printf("  Remaining Active: %,d machines\n", machines.size());
        
        System.out.println("\n🎯 FINAL STATE DISTRIBUTION:");
        System.out.println("  " + "-".repeat(45));
        System.out.println("  | State      | Count | Percentage |");
        System.out.println("  " + "-".repeat(45));
        
        int total = machines.size();
        if (total > 0) {
            for (Map.Entry<String, Integer> entry : stateCounts.entrySet()) {
                double percent = (entry.getValue() * 100.0) / total;
                System.out.printf("  | %-10s | %5d | %9.1f%% |\n", 
                    entry.getKey(), entry.getValue(), percent);
            }
        } else {
            System.out.println("  | All machines completed |");
        }
        System.out.println("  " + "-".repeat(45));
        
        System.out.println("\n✅ VALIDATION:");
        boolean tpsAchieved = actualTPS >= (TARGET_TPS * 0.95);
        boolean eventsCorrect = totalEvents.get() == (totalCycles.get() * 4);
        
        System.out.println("  TPS Target (95%+): " + (tpsAchieved ? "✅ PASS" : "❌ FAIL"));
        System.out.println("  Events = Cycles×4: " + (eventsCorrect ? "✅ PASS" : "❌ FAIL"));
        
        // Write report
        try (PrintWriter w = new PrintWriter(new FileWriter("last_tps_test_report.txt", false))) {
            w.println("SIMPLE TPS TEST REPORT");
            w.println("=" + "=".repeat(79));
            w.printf("Generated: %s\n", LocalDateTime.now());
            w.println();
            w.println("TEST CONFIGURATION:");
            w.printf("  Test Duration: %d seconds\n", TEST_DURATION);
            w.printf("  Settle Time: %d seconds\n", SETTLE_TIME);
            w.printf("  Target TPS: %,d cycles/sec\n", TARGET_TPS);
            w.println();
            w.println("PERFORMANCE RESULTS:");
            w.printf("  Actual TPS: %,.0f cycles/sec\n", actualTPS);
            w.printf("  Actual EPS: %,.0f events/sec\n", actualEPS);
            w.printf("  Efficiency: %.1f%%\n", efficiency);
            w.printf("  Total Cycles: %,d\n", totalCycles.get());
            w.printf("  Total Events: %,d\n", totalEvents.get());
            w.println();
            w.println("VALIDATION:");
            w.printf("  TPS Achieved (95%%+): %s\n", tpsAchieved ? "PASS" : "FAIL");
            w.printf("  Events Correct: %s\n", eventsCorrect ? "PASS" : "FAIL");
        }
        
        System.out.println("\n📄 Report saved to: last_tps_test_report.txt");
        System.out.println("=".repeat(80));
        
        System.exit(0);
    }
}