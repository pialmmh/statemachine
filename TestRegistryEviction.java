import com.telcobright.statemachine.StateMachineRegistry;
import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.websocket.CallMachineRunnerEnhanced;
import com.telcobright.statemachine.timeout.TimeoutManager;
import com.telcobright.statemachineexamples.callmachine.events.IncomingCall;
import com.telcobright.statemachineexamples.callmachine.events.Answer;
import com.telcobright.statemachineexamples.callmachine.events.Hangup;

public class TestRegistryEviction {
    public static void main(String[] args) throws Exception {
        System.out.println("=== Testing Registry Eviction for Final States ===");
        
        // Use the existing CallMachineRunnerEnhanced which already creates a registry
        CallMachineRunnerEnhanced runner = new CallMachineRunnerEnhanced();
        
        // Give the system time to initialize
        Thread.sleep(1000);
        
        String machineId = "call-001"; // Use one of the existing machines
        
        // Test 1: Check initial state of existing machine
        System.out.println("\n--- Test 1: Checking existing machine state ---");
        System.out.println("Using existing machine with ID: " + machineId);
        
        // Test 2: Send IncomingCall event to move to RINGING state  
        System.out.println("\n--- Test 2: Sending IncomingCall event ---");
        runner.sendEvent(machineId, new IncomingCall());
        Thread.sleep(100); // Allow time for processing
        
        // Test 3: Send Answer event to move to CONNECTED state
        System.out.println("\n--- Test 3: Sending Answer event ---");
        runner.sendEvent(machineId, new Answer());
        Thread.sleep(100);
        
        // Test 4: Send Hangup event to move to HUNGUP (final state)
        System.out.println("\n--- Test 4: Sending Hangup event (should evict machine) ---");
        runner.sendEvent(machineId, new Hangup());
        Thread.sleep(500); // Allow more time for eviction processing
        
        // Test 5: Try to send another event to the evicted machine
        System.out.println("\n--- Test 5: Sending event to evicted machine (should be ignored) ---");
        runner.sendEvent(machineId, new IncomingCall());
        Thread.sleep(100);
        System.out.println("Event sent to evicted machine - check logs for ignore message");
        
        // Test 6: Try another event type to make sure it's really evicted
        System.out.println("\n--- Test 6: Sending another event type to evicted machine ---");
        runner.sendEvent(machineId, new Answer());
        Thread.sleep(100);
        System.out.println("Second event sent to evicted machine - should also be ignored");
        
        System.out.println("\n=== Test Complete ===");
        System.out.println("Check the console output above to verify:");
        System.out.println("1. Machine transitioned through IDLE -> RINGING -> CONNECTED -> HUNGUP");
        System.out.println("2. Machine was evicted when reaching HUNGUP (final state)");
        System.out.println("3. Subsequent events were ignored and logged to registry table");
        
        System.exit(0);
    }
}