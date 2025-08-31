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
 * Simplified ESL Event Simulator
 * Target: 1000 TPS = 1000 full cycles/sec = 4000 events/sec
 */
public class SimplifiedESLTest {
    public static void main(String[] args) throws Exception {
        final int TARGET_TPS = 1000;  // Full cycles per second
        final int TEST_DURATION = 5;
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   ESL EVENT SIMULATOR - 1000 TPS (4000 Events/sec)");
        System.out.println("=".repeat(80));
        System.out.println("Target: " + TARGET_TPS + " full cycles/sec = " + (TARGET_TPS * 4) + " events/sec");
        System.out.println("Duration: " + TEST_DURATION + " seconds");
        System.out.println("Expected: " + (TARGET_TPS * TEST_DURATION) + " complete cycles");
        System.out.println("=".repeat(80) + "\n");
        
        // Create registry
        TimeoutManager tm = new TimeoutManager();
        StateMachineRegistry registry = new StateMachineRegistry("esl", tm, 9995);
        
        // Statistics
        AtomicLong totalCycles = new AtomicLong();
        AtomicLong totalEvents = new AtomicLong();
        AtomicLong activeMachines = new AtomicLong();
        AtomicLong peakMachines = new AtomicLong();
        AtomicLong incomingCalls = new AtomicLong();
        AtomicLong rings = new AtomicLong();
        AtomicLong answers = new AtomicLong();
        AtomicLong hangups = new AtomicLong();
        
        Map<String, AtomicLong> stateCounts = new ConcurrentHashMap<>();
        stateCounts.put("ADMISSION", new AtomicLong());
        stateCounts.put("TRYING", new AtomicLong());
        stateCounts.put("RINGING", new AtomicLong());
        stateCounts.put("CONNECTED", new AtomicLong());
        stateCounts.put("HUNGUP", new AtomicLong());
        
        System.out.println("Starting test...\n");
        
        ScheduledExecutorService exec = Executors.newScheduledThreadPool(8);
        AtomicBoolean running = new AtomicBoolean(true);
        long startTime = System.currentTimeMillis();
        
        // Generate full cycles at TARGET_TPS rate
        exec.scheduleAtFixedRate(() -> {
            if (!running.get()) return;
            
            try {
                String callId = "c" + System.nanoTime();
                
                // Create machine with proper state flow
                GenericStateMachine m = EnhancedFluentBuilder.create(callId)
                    .initialState("ADMISSION")
                    .state("ADMISSION")
                        .on(IncomingCall.class).to("TRYING")
                        .done()
                    .state("TRYING")
                        .on(SessionProgress.class).to("RINGING")
                        .done()
                    .state("RINGING")
                        .on(Answer.class).to("CONNECTED")
                        .done()
                    .state("CONNECTED")
                        .on(Hangup.class).to("HUNGUP")
                        .done()
                    .state("HUNGUP")
                        .done()
                    .build();
                
                // Register and start
                registry.register(callId, m);
                m.start();
                activeMachines.incrementAndGet();
                peakMachines.updateAndGet(v -> Math.max(v, activeMachines.get()));
                stateCounts.get("ADMISSION").incrementAndGet();
                
                // Fire all 4 events in sequence (simulating full cycle)
                // Event 1: INCOMING_CALL
                m.fire(new IncomingCall("+1", "+2"));
                incomingCalls.incrementAndGet();
                totalEvents.incrementAndGet();
                stateCounts.get("ADMISSION").decrementAndGet();
                stateCounts.get("TRYING").incrementAndGet();
                
                // Event 2: RING (using SessionProgress as Ring)
                m.fire(new SessionProgress("ringing"));
                rings.incrementAndGet();
                totalEvents.incrementAndGet();
                stateCounts.get("TRYING").decrementAndGet();
                stateCounts.get("RINGING").incrementAndGet();
                
                // Event 3: ANSWER
                m.fire(new Answer());
                answers.incrementAndGet();
                totalEvents.incrementAndGet();
                stateCounts.get("RINGING").decrementAndGet();
                stateCounts.get("CONNECTED").incrementAndGet();
                
                // Event 4: HANGUP
                m.fire(new Hangup());
                hangups.incrementAndGet();
                totalEvents.incrementAndGet();
                stateCounts.get("CONNECTED").decrementAndGet();
                stateCounts.get("HUNGUP").incrementAndGet();
                
                // Remove from registry
                registry.removeMachine(callId);
                activeMachines.decrementAndGet();
                stateCounts.get("HUNGUP").decrementAndGet();
                totalCycles.incrementAndGet();
                
            } catch (Exception e) {
                // Ignore
            }
        }, 0, 1000000/TARGET_TPS, TimeUnit.MICROSECONDS);
        
        // Progress reporter
        exec.scheduleAtFixedRate(() -> {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            if (elapsed > 0) {
                System.out.printf("T=%ds | Cycles: %d | Events: %d | Active: %d | TPS: %d | EPS: %d\n",
                    elapsed, totalCycles.get(), totalEvents.get(), activeMachines.get(),
                    totalCycles.get()/elapsed, totalEvents.get()/elapsed);
            }
        }, 1, 1, TimeUnit.SECONDS);
        
        // Run test
        Thread.sleep(TEST_DURATION * 1000);
        running.set(false);
        exec.shutdown();
        exec.awaitTermination(1, TimeUnit.SECONDS);
        
        // Calculate results
        long duration = TEST_DURATION;
        double actualTPS = totalCycles.get() / (double) duration;
        double actualEPS = totalEvents.get() / (double) duration;
        
        // Print report
        System.out.println("\n" + "=".repeat(80));
        System.out.println("                    FINAL TEST REPORT");
        System.out.println("=".repeat(80));
        
        System.out.println("\n📊 PERFORMANCE METRICS:");
        System.out.printf("  Target: %d cycles/sec (%d events/sec)\n", TARGET_TPS, TARGET_TPS * 4);
        System.out.printf("  Actual TPS: %.0f cycles/sec (%.1f%% efficiency)\n", 
            actualTPS, (actualTPS/TARGET_TPS)*100);
        System.out.printf("  Actual EPS: %.0f events/sec (%.1f%% efficiency)\n", 
            actualEPS, (actualEPS/(TARGET_TPS*4))*100);
        
        System.out.println("\n📈 TOTALS:");
        System.out.printf("  Complete Cycles: %,d\n", totalCycles.get());
        System.out.printf("  Total Events: %,d\n", totalEvents.get());
        System.out.printf("  Peak Concurrent: %,d\n", peakMachines.get());
        System.out.printf("  Final Active: %,d\n", activeMachines.get());
        
        System.out.println("\n📨 EVENT COUNTS (should be equal):");
        System.out.printf("  INCOMING_CALL: %,d\n", incomingCalls.get());
        System.out.printf("  RING:          %,d\n", rings.get());
        System.out.printf("  ANSWER:        %,d\n", answers.get());
        System.out.printf("  HANGUP:        %,d\n", hangups.get());
        
        System.out.println("\n🎯 FINAL STATE DISTRIBUTION:");
        System.out.println("  (Should be ~0 since all cycles complete)");
        System.out.println("  " + "-".repeat(40));
        stateCounts.forEach((state, count) -> 
            System.out.printf("  | %-10s | %5d |\n", state, count.get()));
        System.out.println("  " + "-".repeat(40));
        
        System.out.println("\n✅ TEST VALIDATION:");
        boolean cyclesMatch = Math.abs(totalCycles.get() - (TARGET_TPS * duration)) < 100;
        boolean eventsMatch = totalEvents.get() == totalCycles.get() * 4;
        boolean statesClean = stateCounts.values().stream().allMatch(c -> c.get() == 0);
        
        System.out.println("  Cycles target met: " + (cyclesMatch ? "✅ YES" : "❌ NO"));
        System.out.println("  Events = Cycles×4: " + (eventsMatch ? "✅ YES" : "❌ NO"));
        System.out.println("  All states clean: " + (statesClean ? "✅ YES" : "❌ NO"));
        
        // Write report to file
        try (PrintWriter w = new PrintWriter("last_tps_test_report.txt")) {
            w.println("ESL EVENT SIMULATOR TEST REPORT");
            w.println("=".repeat(80));
            w.printf("Timestamp: %s\n", LocalDateTime.now());
            w.printf("Test Duration: %d seconds\n", duration);
            w.printf("Target TPS: %d cycles/sec\n", TARGET_TPS);
            w.printf("Actual TPS: %.0f cycles/sec\n", actualTPS);
            w.printf("Target EPS: %d events/sec\n", TARGET_TPS * 4);
            w.printf("Actual EPS: %.0f events/sec\n", actualEPS);
            w.printf("Efficiency: %.1f%%\n", (actualTPS/TARGET_TPS)*100);
            w.printf("Total Cycles: %d\n", totalCycles.get());
            w.printf("Total Events: %d\n", totalEvents.get());
            w.printf("Peak Concurrent: %d\n", peakMachines.get());
            w.println("\nEvent Counts:");
            w.printf("  INCOMING_CALL: %d\n", incomingCalls.get());
            w.printf("  RING: %d\n", rings.get());
            w.printf("  ANSWER: %d\n", answers.get());
            w.printf("  HANGUP: %d\n", hangups.get());
            w.println("\nValidation:");
            w.printf("  Cycles Match Target: %s\n", cyclesMatch);
            w.printf("  Events = Cycles×4: %s\n", eventsMatch);
            w.printf("  All States Clean: %s\n", statesClean);
            
            System.out.println("\n📄 Report saved to: last_tps_test_report.txt");
        }
        
        System.out.println("=".repeat(80));
        
        // Cleanup
        registry.shutdown();
        System.exit(0);
    }
}