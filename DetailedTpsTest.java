import com.telcobright.core.*;
import com.telcobright.examples.callmachine.events.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class DetailedTpsTest {
    public static void main(String[] args) throws Exception {
        final int NUM_MACHINES = 50;
        final int DURATION = 5;
        final int TARGET_TPS = 1000;
        
        System.out.println("\n" + "=".repeat(70));
        System.out.println("   1000 TPS TEST - DETAILED STATE ANALYSIS");
        System.out.println("=".repeat(70));
        System.out.println("Machines: " + NUM_MACHINES);
        System.out.println("Duration: " + DURATION + " seconds");
        System.out.println("Target TPS: " + TARGET_TPS + " (20 events/machine/second)");
        System.out.println("Expected cycles per machine: ~5-6 full cycles/second");
        System.out.println("=".repeat(70) + "\n");
        
        // Create machines
        List<GenericStateMachine> machines = new ArrayList<>();
        for (int i = 0; i < NUM_MACHINES; i++) {
            GenericStateMachine m = new GenericStateMachine("m" + i, null, null);
            // Set up transitions
            m.transition("ADMISSION", IncomingCall.class, "RINGING");
            m.transition("RINGING", Answer.class, "CONNECTED");
            m.transition("RINGING", Hangup.class, "HUNGUP");
            m.transition("CONNECTED", Hangup.class, "HUNGUP");
            m.transition("HUNGUP", IncomingCall.class, "ADMISSION");
            m.initialState("ADMISSION");
            m.start();
            machines.add(m);
        }
        
        // Track statistics
        AtomicLong totalEvents = new AtomicLong();
        AtomicLong[] eventCounts = new AtomicLong[4];
        for (int i = 0; i < 4; i++) eventCounts[i] = new AtomicLong();
        
        // Track transitions
        Map<String, AtomicLong> transitionCounts = new ConcurrentHashMap<>();
        transitionCounts.put("ADMISSION->RINGING", new AtomicLong());
        transitionCounts.put("RINGING->CONNECTED", new AtomicLong());
        transitionCounts.put("RINGING->HUNGUP", new AtomicLong());
        transitionCounts.put("CONNECTED->HUNGUP", new AtomicLong());
        transitionCounts.put("HUNGUP->ADMISSION", new AtomicLong());
        
        System.out.println("Starting test...\n");
        
        ScheduledExecutorService exec = Executors.newScheduledThreadPool(4);
        AtomicBoolean running = new AtomicBoolean(true);
        
        // Event firing logic - ALWAYS progress forward
        exec.scheduleAtFixedRate(() -> {
            if (!running.get()) return;
            try {
                GenericStateMachine m = machines.get(ThreadLocalRandom.current().nextInt(NUM_MACHINES));
                String before = m.getCurrentState();
                
                switch (before) {
                    case "ADMISSION":
                        m.fire(new IncomingCall("+1", "+2"));
                        eventCounts[0].incrementAndGet();
                        break;
                    case "RINGING":
                        // 50% answer, 50% hangup for balanced flow
                        if (ThreadLocalRandom.current().nextBoolean()) {
                            m.fire(new Answer());
                            eventCounts[1].incrementAndGet();
                        } else {
                            m.fire(new Hangup());
                            eventCounts[2].incrementAndGet();
                        }
                        break;
                    case "CONNECTED":
                        m.fire(new Hangup());
                        eventCounts[2].incrementAndGet();
                        break;
                    case "HUNGUP":
                        // Immediately restart cycle
                        m.fire(new IncomingCall("+1", "+2"));
                        eventCounts[3].incrementAndGet();
                        break;
                }
                
                String after = m.getCurrentState();
                if (!before.equals(after)) {
                    transitionCounts.get(before + "->" + after).incrementAndGet();
                }
                
                totalEvents.incrementAndGet();
            } catch (Exception e) {}
        }, 0, 1000000/TARGET_TPS, TimeUnit.MICROSECONDS);
        
        // Run test
        Thread.sleep(DURATION * 1000);
        running.set(false);
        exec.shutdown();
        exec.awaitTermination(1, TimeUnit.SECONDS);
        
        // Count final states
        Map<String, Integer> stateCounts = new TreeMap<>();
        stateCounts.put("ADMISSION", 0);
        stateCounts.put("RINGING", 0);
        stateCounts.put("CONNECTED", 0);
        stateCounts.put("HUNGUP", 0);
        
        for (GenericStateMachine m : machines) {
            String state = m.getCurrentState();
            stateCounts.put(state, stateCounts.getOrDefault(state, 0) + 1);
        }
        
        // Print detailed results
        System.out.println("=".repeat(70));
        System.out.println("                    TEST RESULTS");
        System.out.println("=".repeat(70));
        
        System.out.println("\n📊 PERFORMANCE METRICS:");
        System.out.println("  Total Events Fired: " + totalEvents.get());
        System.out.println("  Actual TPS: " + (totalEvents.get() / DURATION));
        System.out.println("  Events per Machine: " + (totalEvents.get() / NUM_MACHINES));
        System.out.println("  Avg Events/Machine/Sec: " + (totalEvents.get() / NUM_MACHINES / DURATION));
        
        System.out.println("\n📈 EVENT DISTRIBUTION:");
        System.out.println("  IncomingCall (ADMISSION): " + eventCounts[0].get());
        System.out.println("  Answer (RINGING): " + eventCounts[1].get());
        System.out.println("  Hangup (RINGING/CONNECTED): " + eventCounts[2].get());
        System.out.println("  IncomingCall (HUNGUP): " + eventCounts[3].get());
        
        System.out.println("\n🔄 TRANSITION COUNTS:");
        transitionCounts.forEach((k, v) -> 
            System.out.println("  " + k + ": " + v.get()));
        
        System.out.println("\n🎯 FINAL STATE DISTRIBUTION:");
        System.out.println("  " + "-".repeat(40));
        System.out.println("  | State      | Count | Percentage |");
        System.out.println("  " + "-".repeat(40));
        
        for (Map.Entry<String, Integer> entry : stateCounts.entrySet()) {
            double percent = (entry.getValue() * 100.0) / NUM_MACHINES;
            System.out.printf("  | %-10s | %5d | %9.1f%% |\n", 
                entry.getKey(), entry.getValue(), percent);
        }
        System.out.println("  " + "-".repeat(40));
        System.out.println("  | TOTAL      |    " + NUM_MACHINES + " |     100.0% |");
        System.out.println("  " + "-".repeat(40));
        
        // Analysis
        System.out.println("\n📝 ANALYSIS:");
        long cycles = transitionCounts.get("HUNGUP->ADMISSION").get();
        System.out.println("  Complete cycles: " + cycles);
        System.out.println("  Cycles per machine: " + (cycles / NUM_MACHINES));
        System.out.println("  Cycles per second: " + (cycles / DURATION));
        
        System.out.println("\n✅ Test completed!");
        System.out.println("=".repeat(70));
    }
}