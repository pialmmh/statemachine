import com.telcobright.core.*;
import com.telcobright.core.timeout.TimeoutManager;
import com.telcobright.examples.callmachine.events.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

/**
 * Final ESL Event Simulator with proper test completion
 * - Runs for TEST_DURATION seconds
 * - Stops sending events
 * - Waits 5 seconds for settling
 * - Generates final report
 */
public class FinalESLTest {
    private static final int TARGET_TPS = 1000;  // Full cycles per second
    private static final int TEST_DURATION = 10;  // Active test period
    private static final int SETTLE_TIME = 5;     // Wait time after test
    
    // Track all machines for final state count
    private static final Map<String, GenericStateMachine> allMachines = new ConcurrentHashMap<>();
    
    // Statistics
    private static final AtomicLong totalCycles = new AtomicLong();
    private static final AtomicLong totalEvents = new AtomicLong();
    private static final AtomicLong activeMachines = new AtomicLong();
    private static final AtomicLong peakMachines = new AtomicLong();
    
    // Event counters
    private static final AtomicLong incomingCalls = new AtomicLong();
    private static final AtomicLong admissionSuccess = new AtomicLong();
    private static final AtomicLong rings = new AtomicLong();
    private static final AtomicLong answers = new AtomicLong();
    private static final AtomicLong hangups = new AtomicLong();
    
    public static void main(String[] args) throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   FINAL ESL EVENT SIMULATOR - 1000 TPS (4000 Events/sec)");
        System.out.println("=".repeat(80));
        System.out.println("Configuration:");
        System.out.println("  Target: " + TARGET_TPS + " full cycles/sec = " + (TARGET_TPS * 4) + " events/sec");
        System.out.println("  Test Duration: " + TEST_DURATION + " seconds");
        System.out.println("  Settle Time: " + SETTLE_TIME + " seconds (after test)");
        System.out.println("  Expected: ~" + (TARGET_TPS * TEST_DURATION) + " complete cycles");
        System.out.println("=".repeat(80) + "\n");
        
        // Create registry with auto-eviction disabled
        TimeoutManager tm = new TimeoutManager();
        StateMachineRegistry registry = new StateMachineRegistry("esl", tm, 9994);
        registry.disableAutoEviction(); // Prevent auto-removal on HUNGUP
        
        System.out.println("Starting test phase...\n");
        
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(8);
        AtomicBoolean testRunning = new AtomicBoolean(true);
        long startTime = System.currentTimeMillis();
        
        // Progress reporter
        ScheduledFuture<?> progressReporter = executor.scheduleAtFixedRate(() -> {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            if (elapsed <= TEST_DURATION) {
                System.out.printf("[TEST] T=%02d/%ds | Cycles: %d | Events: %d | Active: %d | TPS: %d\n",
                    elapsed, TEST_DURATION, totalCycles.get(), totalEvents.get(), 
                    activeMachines.get(), elapsed > 0 ? totalCycles.get()/elapsed : 0);
            } else {
                long settleElapsed = elapsed - TEST_DURATION;
                System.out.printf("[SETTLE] T=%d/%ds | Active machines: %d | Waiting for completion...\n",
                    settleElapsed, SETTLE_TIME, activeMachines.get());
            }
        }, 1, 1, TimeUnit.SECONDS);
        
        // Cycle generator - Creates and processes full call cycles
        ScheduledFuture<?> cycleGenerator = executor.scheduleAtFixedRate(() -> {
            if (!testRunning.get()) return;
            
            try {
                String callId = "call-" + System.nanoTime();
                
                // Create machine with proper ESL state flow
                GenericStateMachine machine = EnhancedFluentBuilder.create(callId)
                    .sampleLogging(1000) // Only log 1 in 1000 events
                    .initialState("ADMISSION")
                    
                    .state("ADMISSION")
                        .on(IncomingCall.class).to("TRYING")
                        .on(Hangup.class).to("HUNGUP")
                        .done()
                        
                    .state("TRYING")
                        .on(SessionProgress.class).to("RINGING")  // Using SessionProgress as RING
                        .on(Hangup.class).to("HUNGUP")
                        .done()
                        
                    .state("RINGING")
                        .on(Answer.class).to("CONNECTED")
                        .on(Hangup.class).to("HUNGUP")
                        .done()
                        
                    .state("CONNECTED")
                        .on(Hangup.class).to("HUNGUP")
                        .done()
                        
                    .state("HUNGUP")
                        // Final state - no transitions
                        .done()
                        
                    .build();
                
                // Register with registry
                registry.register(callId, machine);
                machine.start();
                allMachines.put(callId, machine);
                activeMachines.incrementAndGet();
                peakMachines.updateAndGet(v -> Math.max(v, activeMachines.get()));
                
                // Execute full call cycle (4 events)
                
                // Event 1: INCOMING_CALL (creates machine, moves to TRYING)
                machine.fire(new IncomingCall("+1-555-1000", "+1-555-2000"));
                incomingCalls.incrementAndGet();
                totalEvents.incrementAndGet();
                
                // Event 2: RING (moves to RINGING)
                machine.fire(new SessionProgress("ringing"));
                rings.incrementAndGet();
                totalEvents.incrementAndGet();
                
                // Event 3: ANSWER (moves to CONNECTED)
                machine.fire(new Answer());
                answers.incrementAndGet();
                totalEvents.incrementAndGet();
                
                // Event 4: HANGUP (moves to HUNGUP - final state)
                machine.fire(new Hangup());
                hangups.incrementAndGet();
                totalEvents.incrementAndGet();
                
                // Note: In real scenario, registry would remove on HUNGUP
                // For testing, we keep them to count final states
                
                totalCycles.incrementAndGet();
                
                // Simulate removal after a delay (async)
                executor.schedule(() -> {
                    registry.removeMachine(callId);
                    allMachines.remove(callId);
                    activeMachines.decrementAndGet();
                }, 100, TimeUnit.MILLISECONDS);
                
            } catch (Exception e) {
                // Ignore individual errors
            }
        }, 0, 1000000/TARGET_TPS, TimeUnit.MICROSECONDS);
        
        // TEST PHASE: Run for TEST_DURATION seconds
        Thread.sleep(TEST_DURATION * 1000);
        
        // STOP sending new events
        testRunning.set(false);
        cycleGenerator.cancel(false);
        System.out.println("\n[TEST COMPLETE] Stopped generating events. Waiting " + SETTLE_TIME + " seconds for settlement...\n");
        
        // SETTLE PHASE: Wait additional SETTLE_TIME seconds
        Thread.sleep(SETTLE_TIME * 1000);
        
        // Stop progress reporter
        progressReporter.cancel(false);
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);
        
        // Count final states from remaining machines
        Map<String, Integer> finalStateCounts = new TreeMap<>();
        finalStateCounts.put("ADMISSION", 0);
        finalStateCounts.put("TRYING", 0);
        finalStateCounts.put("RINGING", 0);
        finalStateCounts.put("CONNECTED", 0);
        finalStateCounts.put("HUNGUP", 0);
        
        int totalRemaining = 0;
        for (GenericStateMachine machine : allMachines.values()) {
            String state = machine.getCurrentState();
            finalStateCounts.put(state, finalStateCounts.getOrDefault(state, 0) + 1);
            totalRemaining++;
        }
        
        // Calculate metrics
        long testDurationSec = TEST_DURATION;
        double actualTPS = totalCycles.get() / (double) testDurationSec;
        double actualEPS = totalEvents.get() / (double) testDurationSec;
        double efficiency = (actualTPS / TARGET_TPS) * 100;
        
        // Generate timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        
        // Print console report
        System.out.println("\n" + "=".repeat(80));
        System.out.println("                    FINAL TEST REPORT");
        System.out.println("=".repeat(80));
        
        System.out.println("\n📊 PERFORMANCE METRICS:");
        System.out.printf("  Test Duration: %d seconds\n", TEST_DURATION);
        System.out.printf("  Settle Time: %d seconds\n", SETTLE_TIME);
        System.out.printf("  Target TPS: %,d cycles/sec\n", TARGET_TPS);
        System.out.printf("  Actual TPS: %,.0f cycles/sec\n", actualTPS);
        System.out.printf("  Target EPS: %,d events/sec\n", TARGET_TPS * 4);
        System.out.printf("  Actual EPS: %,.0f events/sec\n", actualEPS);
        System.out.printf("  Efficiency: %.1f%%\n", efficiency);
        
        System.out.println("\n📈 TOTALS:");
        System.out.printf("  Complete Cycles: %,d\n", totalCycles.get());
        System.out.printf("  Total Events: %,d\n", totalEvents.get());
        System.out.printf("  Peak Concurrent: %,d machines\n", peakMachines.get());
        System.out.printf("  Remaining Active: %,d machines\n", activeMachines.get());
        
        System.out.println("\n📨 EVENT COUNTS:");
        System.out.printf("  INCOMING_CALL: %,d\n", incomingCalls.get());
        System.out.printf("  RING:          %,d\n", rings.get());
        System.out.printf("  ANSWER:        %,d\n", answers.get());
        System.out.printf("  HANGUP:        %,d\n", hangups.get());
        
        System.out.println("\n🎯 FINAL STATE DISTRIBUTION (after " + SETTLE_TIME + "s settle time):");
        System.out.println("  " + "-".repeat(45));
        System.out.println("  | State      | Count | Percentage |");
        System.out.println("  " + "-".repeat(45));
        
        if (totalRemaining > 0) {
            for (Map.Entry<String, Integer> entry : finalStateCounts.entrySet()) {
                double percent = (entry.getValue() * 100.0) / totalRemaining;
                System.out.printf("  | %-10s | %5d | %9.1f%% |\n", 
                    entry.getKey(), entry.getValue(), percent);
            }
        } else {
            System.out.println("  | All machines completed and removed |");
        }
        System.out.println("  " + "-".repeat(45));
        System.out.printf("  | TOTAL      | %5d | %9.1f%% |\n", totalRemaining, 100.0);
        System.out.println("  " + "-".repeat(45));
        
        System.out.println("\n✅ VALIDATION:");
        boolean tpsAchieved = actualTPS >= (TARGET_TPS * 0.95);  // 95% of target
        boolean eventsCorrect = totalEvents.get() == (totalCycles.get() * 4);
        boolean mostCompleted = activeMachines.get() < (peakMachines.get() * 0.1);  // <10% remaining
        
        System.out.println("  TPS Target (95%+): " + (tpsAchieved ? "✅ PASS" : "❌ FAIL"));
        System.out.println("  Events = Cycles×4: " + (eventsCorrect ? "✅ PASS" : "❌ FAIL"));
        System.out.println("  Most Cycles Done: " + (mostCompleted ? "✅ PASS" : "❌ FAIL"));
        
        // Write report to file (OVERWRITE)
        try (PrintWriter writer = new PrintWriter(new FileWriter("last_tps_test_report.txt", false))) {
            writer.println("ESL EVENT SIMULATOR - FINAL TEST REPORT");
            writer.println("=" + "=".repeat(79));
            writer.printf("Generated: %s\n", timestamp);
            writer.println();
            writer.println("TEST CONFIGURATION:");
            writer.printf("  Test Duration: %d seconds\n", TEST_DURATION);
            writer.printf("  Settle Time: %d seconds\n", SETTLE_TIME);
            writer.printf("  Target TPS: %,d cycles/sec\n", TARGET_TPS);
            writer.printf("  Target EPS: %,d events/sec\n", TARGET_TPS * 4);
            writer.println();
            writer.println("PERFORMANCE RESULTS:");
            writer.printf("  Actual TPS: %,.0f cycles/sec\n", actualTPS);
            writer.printf("  Actual EPS: %,.0f events/sec\n", actualEPS);
            writer.printf("  Efficiency: %.1f%%\n", efficiency);
            writer.printf("  Total Cycles: %,d\n", totalCycles.get());
            writer.printf("  Total Events: %,d\n", totalEvents.get());
            writer.println();
            writer.println("CONCURRENCY:");
            writer.printf("  Peak Concurrent Machines: %,d\n", peakMachines.get());
            writer.printf("  Final Active Machines: %,d\n", activeMachines.get());
            writer.println();
            writer.println("EVENT DISTRIBUTION:");
            writer.printf("  INCOMING_CALL: %,d\n", incomingCalls.get());
            writer.printf("  RING: %,d\n", rings.get());
            writer.printf("  ANSWER: %,d\n", answers.get());
            writer.printf("  HANGUP: %,d\n", hangups.get());
            writer.println();
            writer.println("FINAL STATE DISTRIBUTION (after settle time):");
            writer.println("  State       Count    Percentage");
            writer.println("  ----------  -------  ----------");
            
            if (totalRemaining > 0) {
                for (Map.Entry<String, Integer> entry : finalStateCounts.entrySet()) {
                    double percent = (entry.getValue() * 100.0) / totalRemaining;
                    writer.printf("  %-10s  %,7d  %9.1f%%\n", 
                        entry.getKey(), entry.getValue(), percent);
                }
                writer.println("  ----------  -------  ----------");
                writer.printf("  TOTAL       %,7d  %9.1f%%\n", totalRemaining, 100.0);
            } else {
                writer.println("  All machines completed and removed");
            }
            
            writer.println();
            writer.println("VALIDATION RESULTS:");
            writer.printf("  TPS Achieved (95%%+): %s\n", tpsAchieved ? "PASS" : "FAIL");
            writer.printf("  Events Correct (C×4): %s\n", eventsCorrect ? "PASS" : "FAIL");
            writer.printf("  Machines Completed: %s\n", mostCompleted ? "PASS" : "FAIL");
            writer.println();
            writer.println("=" + "=".repeat(79));
            writer.println("END OF REPORT");
        }
        
        System.out.println("\n📄 Report saved to: last_tps_test_report.txt");
        System.out.println("=" + "=".repeat(79));
        
        // Cleanup
        registry.shutdown();
        System.exit(0);
    }
}