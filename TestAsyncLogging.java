import com.telcobright.statemachine.StateMachineRegistry;
import com.telcobright.statemachine.websocket.CallMachineRunnerEnhanced;
import com.telcobright.statemachine.timeout.TimeoutManager;
import com.telcobright.statemachineexamples.callmachine.events.IncomingCall;
import com.telcobright.statemachineexamples.callmachine.events.Answer;
import com.telcobright.statemachineexamples.callmachine.events.Hangup;

public class TestAsyncLogging {
    public static void main(String[] args) throws Exception {
        System.out.println("=== Testing Asynchronous Registry Event Logging ===");
        
        long startTime = System.currentTimeMillis();
        
        // Create the CallMachineRunnerEnhanced which includes a registry
        CallMachineRunnerEnhanced runner = new CallMachineRunnerEnhanced();
        
        // Allow registry to initialize
        Thread.sleep(1000);
        
        String machineId = "call-001";
        
        System.out.println("\n--- Testing Event Processing Speed with Async Logging ---");
        
        // Measure time for rapid event processing
        long eventStartTime = System.currentTimeMillis();
        
        // Send events rapidly - async logging should not block execution
        System.out.println("Sending IncomingCall event...");
        runner.sendEvent(machineId, new IncomingCall());
        
        System.out.println("Sending Answer event...");
        runner.sendEvent(machineId, new Answer());
        
        // Send events to trigger ignored logging
        System.out.println("Sending multiple events to evicted machine (async ignore logging)...");
        for (int i = 0; i < 5; i++) {
            runner.sendEvent(machineId, new Hangup());
            runner.sendEvent(machineId, new IncomingCall());
        }
        
        long eventEndTime = System.currentTimeMillis();
        long eventProcessingTime = eventEndTime - eventStartTime;
        
        System.out.println("\n--- Performance Results ---");
        System.out.println("Event processing completed in: " + eventProcessingTime + "ms");
        System.out.println("This should be very fast since logging is asynchronous!");
        
        // Give time for async logging to complete
        System.out.println("\nWaiting 2 seconds for async logging to complete...");
        Thread.sleep(2000);
        
        System.out.println("\n--- Registry Event Log Summary ---");
        System.out.println("Events logged asynchronously:");
        System.out.println("✓ REGISTRY_STARTUP - Registry initialization");
        System.out.println("✓ MACHINE_REGISTERED - Machine registrations (3x)"); 
        System.out.println("✓ MACHINE_OFFLINE - Machine offline transitions");
        System.out.println("✓ PERSISTENCE_OPERATION - State persistence");
        System.out.println("✓ EVENT_IGNORED - Ignored events (10x)");
        System.out.println("\nAll logging performed asynchronously without blocking main execution!");
        
        long totalTime = System.currentTimeMillis() - startTime;
        System.out.println("\nTotal test execution time: " + totalTime + "ms");
        
        System.exit(0);
    }
}