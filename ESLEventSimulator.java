import com.telcobright.core.*;
import com.telcobright.core.timeout.TimeoutManager;
import com.telcobright.examples.callmachine.events.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.io.*;

/**
 * ESL Event Simulator/Generator
 * Generates 1000 full lifecycle events/sec (4000 events total)
 * Each cycle: INCOMING_CALL → RING → ANSWER → HANGUP
 */
public class ESLEventSimulator {
    private static final int TARGET_TPS = 1000;  // Full cycles per second
    private static final int EVENTS_PER_CYCLE = 4;  // incoming_call, ring, answer, hangup
    private static final int TOTAL_EVENT_RATE = TARGET_TPS * EVENTS_PER_CYCLE;  // 4000 events/sec
    private static final int TEST_DURATION_SECONDS = 10;
    
    // Statistics
    private static final AtomicLong totalCycles = new AtomicLong(0);
    private static final AtomicLong totalEvents = new AtomicLong(0);
    private static final AtomicLong activeMachines = new AtomicLong(0);
    private static final AtomicLong peakMachines = new AtomicLong(0);
    
    // Event counters
    private static final Map<String, AtomicLong> eventCounts = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> stateCounts = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> transitionCounts = new ConcurrentHashMap<>();
    
    static {
        eventCounts.put("INCOMING_CALL", new AtomicLong());
        eventCounts.put("ADMISSION_SUCCESS", new AtomicLong());
        eventCounts.put("RING", new AtomicLong());
        eventCounts.put("ANSWER", new AtomicLong());
        eventCounts.put("HANGUP", new AtomicLong());
        
        stateCounts.put("ADMISSION", new AtomicLong());
        stateCounts.put("TRYING", new AtomicLong());
        stateCounts.put("RINGING", new AtomicLong());
        stateCounts.put("CONNECTED", new AtomicLong());
        stateCounts.put("HUNGUP", new AtomicLong());
        
        transitionCounts.put("CREATE->ADMISSION", new AtomicLong());
        transitionCounts.put("ADMISSION->TRYING", new AtomicLong());
        transitionCounts.put("TRYING->RINGING", new AtomicLong());
        transitionCounts.put("RINGING->CONNECTED", new AtomicLong());
        transitionCounts.put("CONNECTED->HUNGUP", new AtomicLong());
        transitionCounts.put("HUNGUP->REMOVED", new AtomicLong());
    }
    
    // Track active calls
    private static final Map<String, CallLifecycle> activeCalls = new ConcurrentHashMap<>();
    
    static class CallLifecycle {
        final String callId;
        String currentState = "CREATED";
        long startTime = System.currentTimeMillis();
        int eventsProcessed = 0;
        
        CallLifecycle(String callId) {
            this.callId = callId;
        }
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("         ESL EVENT SIMULATOR - 1000 TPS (4000 Events/sec)");
        System.out.println("=".repeat(80));
        System.out.println("Target: " + TARGET_TPS + " full call cycles/second");
        System.out.println("Events per cycle: " + EVENTS_PER_CYCLE);
        System.out.println("Total event rate: " + TOTAL_EVENT_RATE + " events/second");
        System.out.println("Test duration: " + TEST_DURATION_SECONDS + " seconds");
        System.out.println("Expected total events: " + (TOTAL_EVENT_RATE * TEST_DURATION_SECONDS));
        System.out.println("=".repeat(80) + "\n");
        
        // Create Registry with proper configuration
        TimeoutManager timeoutManager = new TimeoutManager();
        StateMachineRegistry registry = new StateMachineRegistry("esl", timeoutManager, 9996);
        
        System.out.println("Registry initialized. Starting simulation...\n");
        
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);
        AtomicBoolean running = new AtomicBoolean(true);
        long testStartTime = System.currentTimeMillis();
        
        // Event Generator - Creates new calls at TARGET_TPS rate
        scheduler.scheduleAtFixedRate(() -> {
            if (!running.get()) return;
            
            try {
                String callId = "call-" + UUID.randomUUID().toString().substring(0, 8);
                CallLifecycle lifecycle = new CallLifecycle(callId);
                activeCalls.put(callId, lifecycle);
                
                // Create new machine on INCOMING_CALL
                GenericStateMachine machine = createCallMachine(callId);
                registry.register(callId, machine);
                machine.start();
                
                // Fire INCOMING_CALL event
                machine.fire(new IncomingCall("+1-555-" + callId.substring(5), "+1-555-9999"));
                eventCounts.get("INCOMING_CALL").incrementAndGet();
                lifecycle.currentState = "ADMISSION";
                lifecycle.eventsProcessed++;
                
                activeMachines.incrementAndGet();
                peakMachines.updateAndGet(current -> Math.max(current, activeMachines.get()));
                
                transitionCounts.get("CREATE->ADMISSION").incrementAndGet();
                stateCounts.get("ADMISSION").incrementAndGet();
                totalEvents.incrementAndGet();
                
            } catch (Exception e) {
                System.err.println("Error creating call: " + e.getMessage());
            }
        }, 0, 1000000/TARGET_TPS, TimeUnit.MICROSECONDS);
        
        // Event Processor - Advances existing calls through their lifecycle
        scheduler.scheduleAtFixedRate(() -> {
            if (!running.get()) return;
            
            for (CallLifecycle lifecycle : activeCalls.values()) {
                try {
                    GenericStateMachine machine = registry.getMachine(lifecycle.callId);
                    if (machine == null) continue;
                    
                    String currentState = machine.getCurrentState();
                    
                    switch (currentState) {
                        case "ADMISSION":
                            // Fire ADMISSION_SUCCESS to move to TRYING
                            machine.fire(new AdmissionSuccess());
                            eventCounts.get("ADMISSION_SUCCESS").incrementAndGet();
                            lifecycle.currentState = "TRYING";
                            transitionCounts.get("ADMISSION->TRYING").incrementAndGet();
                            stateCounts.get("ADMISSION").decrementAndGet();
                            stateCounts.get("TRYING").incrementAndGet();
                            break;
                            
                        case "TRYING":
                            // Fire RING to move to RINGING
                            machine.fire(new Ring());
                            eventCounts.get("RING").incrementAndGet();
                            lifecycle.currentState = "RINGING";
                            transitionCounts.get("TRYING->RINGING").incrementAndGet();
                            stateCounts.get("TRYING").decrementAndGet();
                            stateCounts.get("RINGING").incrementAndGet();
                            break;
                            
                        case "RINGING":
                            // Fire ANSWER to move to CONNECTED
                            machine.fire(new Answer());
                            eventCounts.get("ANSWER").incrementAndGet();
                            lifecycle.currentState = "CONNECTED";
                            transitionCounts.get("RINGING->CONNECTED").incrementAndGet();
                            stateCounts.get("RINGING").decrementAndGet();
                            stateCounts.get("CONNECTED").incrementAndGet();
                            break;
                            
                        case "CONNECTED":
                            // Fire HANGUP to end the call
                            machine.fire(new Hangup());
                            eventCounts.get("HANGUP").incrementAndGet();
                            lifecycle.currentState = "HUNGUP";
                            transitionCounts.get("CONNECTED->HUNGUP").incrementAndGet();
                            stateCounts.get("CONNECTED").decrementAndGet();
                            stateCounts.get("HUNGUP").incrementAndGet();
                            break;
                            
                        case "HUNGUP":
                            // Remove from registry
                            registry.removeMachine(lifecycle.callId);
                            activeCalls.remove(lifecycle.callId);
                            activeMachines.decrementAndGet();
                            totalCycles.incrementAndGet();
                            transitionCounts.get("HUNGUP->REMOVED").incrementAndGet();
                            stateCounts.get("HUNGUP").decrementAndGet();
                            break;
                    }
                    
                    lifecycle.eventsProcessed++;
                    totalEvents.incrementAndGet();
                    
                } catch (Exception e) {
                    // Ignore individual errors
                }
            }
        }, 100, 250, TimeUnit.MICROSECONDS);  // Process events 4000 times/second
        
        // Statistics Reporter
        scheduler.scheduleAtFixedRate(() -> {
            long elapsed = (System.currentTimeMillis() - testStartTime) / 1000;
            if (elapsed > 0) {
                System.out.println("Time: " + elapsed + "s | Active: " + activeMachines.get() + 
                    " | Cycles: " + totalCycles.get() + 
                    " | Events: " + totalEvents.get() + 
                    " | EPS: " + (totalEvents.get() / elapsed));
            }
        }, 1, 1, TimeUnit.SECONDS);
        
        // Run test
        Thread.sleep(TEST_DURATION_SECONDS * 1000);
        running.set(false);
        scheduler.shutdown();
        scheduler.awaitTermination(2, TimeUnit.SECONDS);
        
        // Final state count from registry
        Map<String, Integer> finalStates = new TreeMap<>();
        finalStates.put("ADMISSION", 0);
        finalStates.put("TRYING", 0);
        finalStates.put("RINGING", 0);
        finalStates.put("CONNECTED", 0);
        finalStates.put("HUNGUP", 0);
        
        // Count remaining active machines
        for (CallLifecycle lifecycle : activeCalls.values()) {
            String state = lifecycle.currentState;
            if (!state.equals("HUNGUP") && !state.equals("REMOVED")) {
                finalStates.put(state, finalStates.getOrDefault(state, 0) + 1);
            }
        }
        
        // Generate Report
        long testDuration = TEST_DURATION_SECONDS;
        double actualTPS = totalCycles.get() / (double) testDuration;
        double actualEPS = totalEvents.get() / (double) testDuration;
        
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        report.put("test_duration_seconds", testDuration);
        report.put("target_tps", TARGET_TPS);
        report.put("actual_tps", actualTPS);
        report.put("target_eps", TOTAL_EVENT_RATE);
        report.put("actual_eps", actualEPS);
        report.put("efficiency_percent", (actualTPS / TARGET_TPS) * 100);
        report.put("total_cycles_completed", totalCycles.get());
        report.put("total_events_processed", totalEvents.get());
        report.put("peak_concurrent_machines", peakMachines.get());
        report.put("final_active_machines", activeMachines.get());
        
        Map<String, Long> eventMap = new LinkedHashMap<>();
        eventCounts.forEach((k, v) -> eventMap.put(k, v.get()));
        report.put("event_counts", eventMap);
        
        report.put("final_state_distribution", finalStates);
        
        Map<String, Long> transitionMap = new LinkedHashMap<>();
        transitionCounts.forEach((k, v) -> transitionMap.put(k, v.get()));
        report.put("transition_counts", transitionMap);
        
        // Print to console
        System.out.println("\n" + "=".repeat(80));
        System.out.println("                    FINAL TEST REPORT");
        System.out.println("=".repeat(80));
        System.out.println("\n📊 PERFORMANCE METRICS:");
        System.out.printf("  Target TPS (cycles/sec): %,d\n", TARGET_TPS);
        System.out.printf("  Actual TPS: %,.0f (%.1f%%)\n", actualTPS, (actualTPS/TARGET_TPS)*100);
        System.out.printf("  Target EPS (events/sec): %,d\n", TOTAL_EVENT_RATE);
        System.out.printf("  Actual EPS: %,.0f (%.1f%%)\n", actualEPS, (actualEPS/TOTAL_EVENT_RATE)*100);
        
        System.out.println("\n📈 TOTALS:");
        System.out.printf("  Complete Cycles: %,d\n", totalCycles.get());
        System.out.printf("  Total Events: %,d\n", totalEvents.get());
        System.out.printf("  Peak Concurrent: %,d\n", peakMachines.get());
        
        System.out.println("\n📨 EVENT DISTRIBUTION:");
        eventCounts.forEach((event, count) -> 
            System.out.printf("  %-20s: %,8d\n", event, count.get()));
        
        System.out.println("\n🎯 FINAL STATE DISTRIBUTION:");
        System.out.println("  " + "-".repeat(40));
        System.out.println("  | State      | Count | Active % |");
        System.out.println("  " + "-".repeat(40));
        int totalActive = finalStates.values().stream().mapToInt(Integer::intValue).sum();
        finalStates.forEach((state, count) -> {
            double percent = totalActive > 0 ? (count * 100.0 / totalActive) : 0;
            System.out.printf("  | %-10s | %5d | %7.1f%% |\n", state, count, percent);
        });
        System.out.println("  " + "-".repeat(40));
        System.out.printf("  | TOTAL      | %5d | %7.1f%% |\n", totalActive, 100.0);
        System.out.println("  " + "-".repeat(40));
        
        System.out.println("\n🔄 TRANSITION COUNTS:");
        transitionCounts.forEach((transition, count) -> 
            System.out.printf("  %-25s: %,8d\n", transition, count.get()));
        
        // Write report to file
        try (PrintWriter writer = new PrintWriter(new FileWriter("last_tps_test_report.txt"))) {
            writer.println("ESL EVENT SIMULATOR TEST REPORT");
            writer.println("=".repeat(80));
            writer.printf("Timestamp: %s\n", report.get("timestamp"));
            writer.printf("Test Duration: %d seconds\n", report.get("test_duration_seconds"));
            writer.printf("Target TPS: %d cycles/sec\n", report.get("target_tps"));
            writer.printf("Actual TPS: %.0f cycles/sec\n", report.get("actual_tps"));
            writer.printf("Target EPS: %d events/sec\n", report.get("target_eps"));
            writer.printf("Actual EPS: %.0f events/sec\n", report.get("actual_eps"));
            writer.printf("Efficiency: %.1f%%\n", report.get("efficiency_percent"));
            writer.printf("Total Cycles: %d\n", report.get("total_cycles_completed"));
            writer.printf("Total Events: %d\n", report.get("total_events_processed"));
            writer.printf("Peak Concurrent: %d\n", report.get("peak_concurrent_machines"));
            writer.println("\nEvent Counts:");
            eventCounts.forEach((k,v) -> writer.printf("  %s: %d\n", k, v.get()));
            writer.println("\nFinal State Distribution:");
            finalStates.forEach((k,v) -> writer.printf("  %s: %d\n", k, v));
            writer.println("\nTransition Counts:");
            transitionCounts.forEach((k,v) -> writer.printf("  %s: %d\n", k, v.get()));
            System.out.println("\n✅ Report saved to: last_tps_test_report.txt");
        }
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("Test completed!");
        System.out.println("=".repeat(80));
        
        // Cleanup
        registry.shutdown();
        System.exit(0);
    }
    
    private static GenericStateMachine createCallMachine(String callId) {
        return EnhancedFluentBuilder.create(callId)
            .initialState("ADMISSION")
            
            .state("ADMISSION")
                .on(AdmissionSuccess.class).to("TRYING")
                .on(AdmissionFailure.class).to("HUNGUP")
                .on(Hangup.class).to("HUNGUP")
                .done()
                
            .state("TRYING")
                .on(Ring.class).to("RINGING")
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
    }
}

// Additional event classes needed
class AdmissionSuccess implements com.telcobright.core.events.StateMachineEvent {
    private final long timestamp = System.currentTimeMillis();
    public String getEventType() { return "ADMISSION_SUCCESS"; }
    public String getDescription() { return "Admission successful"; }
    public Object getPayload() { return null; }
    public long getTimestamp() { return timestamp; }
}

class AdmissionFailure implements com.telcobright.core.events.StateMachineEvent {
    private final long timestamp = System.currentTimeMillis();
    public String getEventType() { return "ADMISSION_FAILURE"; }
    public String getDescription() { return "Admission failed"; }
    public Object getPayload() { return null; }
    public long getTimestamp() { return timestamp; }
}

class Ring implements com.telcobright.core.events.StateMachineEvent {
    private final long timestamp = System.currentTimeMillis();
    public String getEventType() { return "RING"; }
    public String getDescription() { return "Phone ringing"; }
    public Object getPayload() { return null; }
    public long getTimestamp() { return timestamp; }
}