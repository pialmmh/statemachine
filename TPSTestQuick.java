import com.telcobright.core.*;
import com.telcobright.core.timeout.TimeoutManager;
import com.telcobright.examples.callmachine.CallState;
import com.telcobright.examples.callmachine.events.*;
import com.telcobright.debugger.CallMachineRunnerEnhanced;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class TPSTestQuick {
    private static final int TARGET_TPS = 1000;
    private static final int TEST_DURATION_SECONDS = 5;
    private static final int NUM_MACHINES = 50;
    
    // Statistics
    private static final AtomicLong totalTransitions = new AtomicLong(0);
    private static final AtomicLong successfulTransitions = new AtomicLong(0);
    private static final Map<String, AtomicLong> stateCount = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> eventCount = new ConcurrentHashMap<>();
    
    static {
        for (CallState state : CallState.values()) {
            stateCount.put(state.name(), new AtomicLong(0));
        }
        eventCount.put("INCOMING_CALL", new AtomicLong(0));
        eventCount.put("ANSWER", new AtomicLong(0));
        eventCount.put("HANGUP", new AtomicLong(0));
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("         1000 TPS PERFORMANCE TEST - QUICK");
        System.out.println("=".repeat(80));
        System.out.println("Target TPS: " + TARGET_TPS);
        System.out.println("Duration: " + TEST_DURATION_SECONDS + " seconds");
        System.out.println("Machines: " + NUM_MACHINES);
        System.out.println("=".repeat(80) + "\n");
        
        // Create registry
        TimeoutManager timeoutManager = new TimeoutManager();
        StateMachineRegistry registry = new StateMachineRegistry("test", timeoutManager, 9998);
        
        // Create machines
        System.out.println("Creating " + NUM_MACHINES + " machines...");
        List<GenericStateMachine> machines = new ArrayList<>();
        
        for (int i = 1; i <= NUM_MACHINES; i++) {
            String machineId = "quick-" + i;
            GenericStateMachine machine = createMachine(machineId);
            
            CallMachineRunnerEnhanced.CallPersistentContext context = 
                new CallMachineRunnerEnhanced.CallPersistentContext(machineId, "+1-555-" + i, "+1-555-9999");
            machine.setPersistingEntity(context);
            
            registry.register(machineId, machine);
            machine.start();
            machines.add(machine);
            stateCount.get("ADMISSION").incrementAndGet();
        }
        
        System.out.println("Starting test...\n");
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);
        AtomicBoolean running = new AtomicBoolean(true);
        long startTime = System.currentTimeMillis();
        
        // Event generator
        executor.scheduleAtFixedRate(() -> {
            if (!running.get()) return;
            
            try {
                GenericStateMachine machine = machines.get(ThreadLocalRandom.current().nextInt(NUM_MACHINES));
                String currentState = machine.getCurrentState();
                String previousState = currentState;
                
                switch (currentState) {
                    case "ADMISSION":
                        machine.fire(new IncomingCall("+1-555-0000", "+1-555-9999"));
                        eventCount.get("INCOMING_CALL").incrementAndGet();
                        break;
                    case "RINGING":
                        if (ThreadLocalRandom.current().nextBoolean()) {
                            machine.fire(new Answer());
                            eventCount.get("ANSWER").incrementAndGet();
                        } else {
                            machine.fire(new Hangup());
                            eventCount.get("HANGUP").incrementAndGet();
                        }
                        break;
                    case "CONNECTED":
                        machine.fire(new Hangup());
                        eventCount.get("HANGUP").incrementAndGet();
                        break;
                    case "HUNGUP":
                        machine.fire(new IncomingCall("+1-555-0000", "+1-555-9999"));
                        eventCount.get("INCOMING_CALL").incrementAndGet();
                        break;
                }
                
                totalTransitions.incrementAndGet();
                
                String newState = machine.getCurrentState();
                if (!previousState.equals(newState)) {
                    successfulTransitions.incrementAndGet();
                    stateCount.get(previousState).decrementAndGet();
                    stateCount.get(newState).incrementAndGet();
                }
                
            } catch (Exception e) {
                // Ignore
            }
        }, 0, 1000000 / TARGET_TPS, TimeUnit.MICROSECONDS);
        
        // Wait for test duration
        Thread.sleep(TEST_DURATION_SECONDS * 1000);
        running.set(false);
        
        // Shutdown
        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS);
        
        // Print results
        long duration = (System.currentTimeMillis() - startTime) / 1000;
        double actualTPS = totalTransitions.get() / (double) duration;
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("                    TEST RESULTS");
        System.out.println("=".repeat(80));
        
        System.out.println("\n📊 PERFORMANCE:");
        System.out.println("  Target TPS: " + TARGET_TPS);
        System.out.println("  Actual TPS: " + String.format("%.0f", actualTPS));
        System.out.println("  Efficiency: " + String.format("%.1f%%", (actualTPS / TARGET_TPS) * 100));
        
        System.out.println("\n📈 TRANSITIONS:");
        System.out.println("  Total: " + totalTransitions.get());
        System.out.println("  Successful: " + successfulTransitions.get());
        System.out.println("  Success Rate: " + String.format("%.1f%%", 
            (successfulTransitions.get() * 100.0) / totalTransitions.get()));
        
        System.out.println("\n🗂️ STATE DISTRIBUTION (FINAL COUNT):");
        stateCount.forEach((state, count) -> {
            System.out.println("  " + state + ": " + count.get());
        });
        
        System.out.println("\n📨 EVENT COUNTS:");
        eventCount.forEach((event, count) -> {
            System.out.println("  " + event + ": " + count.get());
        });
        
        System.out.println("\n✅ Test completed!");
        System.out.println("=".repeat(80));
        
        registry.shutdown();
        System.exit(0);
    }
    
    private static GenericStateMachine createMachine(String machineId) {
        return EnhancedFluentBuilder.create(machineId)
            .initialState(CallState.ADMISSION.name())
            
            .state(CallState.ADMISSION.name())
                .on(IncomingCall.class).to(CallState.RINGING.name())
                .on(Hangup.class).to(CallState.HUNGUP.name())
                .done()
                
            .state(CallState.RINGING.name())
                .on(Answer.class).to(CallState.CONNECTED.name())
                .on(Hangup.class).to(CallState.HUNGUP.name())
                .done()
                
            .state(CallState.CONNECTED.name())
                .on(Hangup.class).to(CallState.HUNGUP.name())
                .done()
                
            .state(CallState.HUNGUP.name())
                .on(IncomingCall.class).to(CallState.ADMISSION.name())
                .done()
                
            .build();
    }
}