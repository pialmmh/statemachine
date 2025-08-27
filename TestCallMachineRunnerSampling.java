import com.telcobright.statemachine.websocket.CallMachineRunnerEnhanced;
import com.telcobright.statemachineexamples.callmachine.events.IncomingCall;
import com.telcobright.statemachineexamples.callmachine.events.Answer;
import com.telcobright.statemachineexamples.callmachine.events.Hangup;

public class TestCallMachineRunnerSampling {
    public static void main(String[] args) throws Exception {
        System.out.println("=== Testing CallMachineRunnerEnhanced with Sample Logging ===");
        
        // Create the enhanced runner
        CallMachineRunnerEnhanced runner = new CallMachineRunnerEnhanced();
        
        System.out.println("✅ CallMachineRunnerEnhanced created successfully");
        System.out.println("🔧 Configuration:");
        System.out.println("   Registry Sample Logging: 1-in-2 (50% rate)");
        System.out.println("   State Machine Sample Logging: 1-in-2 (50% rate)");
        System.out.println("   Debug Mode: ENABLED (so ALL events will be logged despite sampling)");
        
        // Wait for initialization
        Thread.sleep(2000);
        
        System.out.println("\n🚀 Sending test events to call-001...");
        
        // Send events to one of the pre-created machines
        String machineId = "call-001";
        
        for (int i = 1; i <= 10; i++) {
            System.out.println("Cycle " + i + ":");
            
            runner.sendEvent(machineId, new IncomingCall());
            System.out.println("   ✓ Sent IncomingCall");
            
            runner.sendEvent(machineId, new Answer());
            System.out.println("   ✓ Sent Answer");
            
            runner.sendEvent(machineId, new Hangup());
            System.out.println("   ✓ Sent Hangup");
            
            Thread.sleep(100); // Small delay between cycles
        }
        
        System.out.println("\n✅ Sent 30 events total (10 cycles × 3 events each)");
        
        // Wait for async logging
        Thread.sleep(2000);
        
        System.out.println("\n📊 EXPECTED BEHAVIOR:");
        System.out.println("✅ Debug mode is ENABLED, so:");
        System.out.println("   - ALL registry events should be logged (ignoring 1-in-2 sampling)");
        System.out.println("   - ALL state machine history events should be logged (ignoring 1-in-2 sampling)");
        System.out.println("   - Check the console output above - you should see continuous event logging");
        System.out.println("   - No 'No History available' messages (all events logged)");
        
        System.out.println("\n🔧 TO TEST SAMPLING WITHOUT DEBUG MODE:");
        System.out.println("   - Comment out registry.enableDebugMode() in CallMachineRunnerEnhanced");
        System.out.println("   - Then only ~50% of events would be logged to database");
        System.out.println("   - But registry and state machine events would still be processed normally");
        
        System.out.println("\n=== Test Complete ===");
        System.exit(0);
    }
}