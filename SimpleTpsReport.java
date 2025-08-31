import com.telcobright.core.*;
import com.telcobright.core.timeout.TimeoutManager;
import com.telcobright.examples.callmachine.CallState;
import com.telcobright.examples.callmachine.events.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;

public class SimpleTpsReport {
    public static void main(String[] args) throws Exception {
        final int TARGET_TPS = 1000;
        final int DURATION_SECONDS = 5;
        final int NUM_MACHINES = 100;
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("         1000 TPS TEST WITH STATE COUNT REPORT");
        System.out.println("=".repeat(80));
        
        // Redirect verbose output
        System.setProperty("java.util.logging.level", "SEVERE");
        
        // Create registry
        TimeoutManager timeoutManager = new TimeoutManager();
        StateMachineRegistry registry = new StateMachineRegistry("tps", timeoutManager, 9997);
        
        // Create machines WITHOUT verbose logging
        List<GenericStateMachine> machines = new ArrayList<>();
        for (int i = 1; i <= NUM_MACHINES; i++) {
            String id = "m" + i;
            GenericStateMachine machine = EnhancedFluentBuilder.create(id)
                .initialState("ADMISSION")
                .state("ADMISSION")
                    .on(IncomingCall.class).to("RINGING")
                    .done()
                .state("RINGING")
                    .on(Answer.class).to("CONNECTED")
                    .on(Hangup.class).to("HUNGUP")
                    .done()
                .state("CONNECTED")
                    .on(Hangup.class).to("HUNGUP")
                    .done()
                .state("HUNGUP")
                    .on(IncomingCall.class).to("ADMISSION")
                    .done()
                .build();
            
            machine.start();
            machines.add(machine);
        }
        
        System.out.println("Created " + NUM_MACHINES + " machines");
        System.out.println("Running test for " + DURATION_SECONDS + " seconds...\n");
        
        // Statistics
        AtomicLong totalEvents = new AtomicLong(0);
        AtomicLong successfulTransitions = new AtomicLong(0);
        
        // Run test
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);
        AtomicBoolean running = new AtomicBoolean(true);
        
        executor.scheduleAtFixedRate(() -> {
            if (!running.get()) return;
            
            try {
                GenericStateMachine machine = machines.get(ThreadLocalRandom.current().nextInt(NUM_MACHINES));
                String state = machine.getCurrentState();
                
                switch (state) {
                    case "ADMISSION":
                        machine.fire(new IncomingCall("+1", "+2"));
                        break;
                    case "RINGING":
                        if (ThreadLocalRandom.current().nextBoolean()) {
                            machine.fire(new Answer());
                        } else {
                            machine.fire(new Hangup());
                        }
                        break;
                    case "CONNECTED":
                        machine.fire(new Hangup());
                        break;
                    case "HUNGUP":
                        machine.fire(new IncomingCall("+1", "+2"));
                        break;
                }
                
                totalEvents.incrementAndGet();
                String newState = machine.getCurrentState();
                if (!state.equals(newState)) {
                    successfulTransitions.incrementAndGet();
                }
            } catch (Exception e) {
                // Ignore
            }
        }, 0, 1000000 / TARGET_TPS, TimeUnit.MICROSECONDS);
        
        // Wait
        Thread.sleep(DURATION_SECONDS * 1000);
        running.set(false);
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);
        
        // COUNT FINAL STATES
        Map<String, Integer> stateCounts = new HashMap<>();
        stateCounts.put("ADMISSION", 0);
        stateCounts.put("RINGING", 0);
        stateCounts.put("CONNECTED", 0);
        stateCounts.put("HUNGUP", 0);
        
        for (GenericStateMachine machine : machines) {
            String state = machine.getCurrentState();
            stateCounts.put(state, stateCounts.get(state) + 1);
        }
        
        // Print results
        System.out.println("=".repeat(80));
        System.out.println("                    FINAL RESULTS");
        System.out.println("=".repeat(80));
        
        System.out.println("\n📊 PERFORMANCE:");
        System.out.println("  Total Events Fired: " + totalEvents.get());
        System.out.println("  Successful Transitions: " + successfulTransitions.get());
        System.out.println("  Actual TPS: " + (totalEvents.get() / DURATION_SECONDS));
        System.out.println("  Success Rate: " + String.format("%.1f%%", 
            (successfulTransitions.get() * 100.0) / totalEvents.get()));
        
        System.out.println("\n🗂️ FINAL STATE DISTRIBUTION (COUNT OF MACHINES):");
        System.out.println("  " + "-".repeat(30));
        System.out.println("  | State      | Count | Percent |");
        System.out.println("  " + "-".repeat(30));
        
        int total = 0;
        for (Map.Entry<String, Integer> entry : stateCounts.entrySet()) {
            total += entry.getValue();
        }
        
        for (Map.Entry<String, Integer> entry : stateCounts.entrySet()) {
            double percent = (entry.getValue() * 100.0) / total;
            System.out.printf("  | %-10s | %5d | %6.1f%% |\n", 
                entry.getKey(), entry.getValue(), percent);
        }
        System.out.println("  " + "-".repeat(30));
        System.out.println("  | TOTAL      | " + String.format("%5d", total) + " | 100.0% |");
        System.out.println("  " + "-".repeat(30));
        
        System.out.println("\n✅ Test completed!");
        System.out.println("=".repeat(80));
        
        // Cleanup
        registry.shutdown();
        System.exit(0);
    }
}