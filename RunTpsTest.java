import com.telcobright.core.*;
import com.telcobright.examples.callmachine.events.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class RunTpsTest {
    public static void main(String[] args) throws Exception {
        final int NUM_MACHINES = 50;
        final int DURATION = 5;
        final int TARGET_TPS = 1000;
        
        System.out.println("\n====== 1000 TPS TEST - STATE COUNT REPORT ======\n");
        
        // Create simple machines
        List<GenericStateMachine> machines = new ArrayList<>();
        for (int i = 0; i < NUM_MACHINES; i++) {
            GenericStateMachine m = new GenericStateMachine("m" + i, null, null);
            m.transition("ADMISSION", IncomingCall.class, "RINGING");
            m.transition("RINGING", Answer.class, "CONNECTED");
            m.transition("RINGING", Hangup.class, "HUNGUP");
            m.transition("CONNECTED", Hangup.class, "HUNGUP");
            m.transition("HUNGUP", IncomingCall.class, "ADMISSION");
            m.initialState("ADMISSION");
            m.start();
            machines.add(m);
        }
        
        System.out.println("Running " + NUM_MACHINES + " machines for " + DURATION + " seconds...\n");
        
        AtomicLong events = new AtomicLong();
        ScheduledExecutorService exec = Executors.newScheduledThreadPool(2);
        
        // Fire events
        exec.scheduleAtFixedRate(() -> {
            try {
                GenericStateMachine m = machines.get(ThreadLocalRandom.current().nextInt(NUM_MACHINES));
                String s = m.getCurrentState();
                
                if ("ADMISSION".equals(s)) m.fire(new IncomingCall("+1", "+2"));
                else if ("RINGING".equals(s)) {
                    if (ThreadLocalRandom.current().nextBoolean()) m.fire(new Answer());
                    else m.fire(new Hangup());
                }
                else if ("CONNECTED".equals(s)) m.fire(new Hangup());
                else if ("HUNGUP".equals(s)) m.fire(new IncomingCall("+1", "+2"));
                
                events.incrementAndGet();
            } catch (Exception e) {}
        }, 0, 1000000/TARGET_TPS, TimeUnit.MICROSECONDS);
        
        Thread.sleep(DURATION * 1000);
        exec.shutdown();
        
        // Count states
        Map<String, Integer> counts = new TreeMap<>();
        for (GenericStateMachine m : machines) {
            String state = m.getCurrentState();
            counts.put(state, counts.getOrDefault(state, 0) + 1);
        }
        
        // Print results
        System.out.println("RESULTS:");
        System.out.println("Total Events: " + events.get());
        System.out.println("TPS: " + (events.get() / DURATION));
        
        System.out.println("\nFINAL STATE COUNTS:");
        System.out.println("State        | Count | Percent");
        System.out.println("-------------|-------|--------");
        counts.forEach((s, c) -> 
            System.out.printf("%-12s | %5d | %5.1f%%\n", s, c, (c*100.0/NUM_MACHINES)));
        System.out.println("-------------|-------|--------");
        System.out.println("TOTAL        |    " + NUM_MACHINES + " | 100.0%");
    }
}
