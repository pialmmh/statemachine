import com.telcobright.statemachine.StateMachineRegistry;
import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.timeout.TimeoutManager;
import com.telcobright.statemachine.SampleLoggingConfig;
import com.telcobright.statemachine.EnhancedFluentBuilder;
import com.telcobright.statemachine.websocket.CallMachineRunnerEnhanced;
import com.telcobright.statemachineexamples.callmachine.events.IncomingCall;
import com.telcobright.statemachineexamples.callmachine.events.Answer;
import com.telcobright.statemachineexamples.callmachine.events.Hangup;
import com.telcobright.statemachine.persistence.MysqlConnectionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class TestNewSamplingBehavior {
    
    private static void clearDatabase() throws Exception {
        System.out.println("🗑️ Clearing database tables...");
        
        MysqlConnectionProvider connectionProvider = new MysqlConnectionProvider();
        try (Connection conn = connectionProvider.getConnection()) {
            // Clear registry event logs
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM registry_sampling_test")) {
                stmt.executeUpdate();
                System.out.println("✅ Cleared registry_sampling_test table");
            } catch (Exception e) {
                System.out.println("⚠️ Could not clear registry_sampling_test table: " + e.getMessage());
            }
            
            // Clear history table
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM history_sample_test_machine")) {
                stmt.executeUpdate();
                System.out.println("✅ Cleared history_sample_test_machine table");
            } catch (Exception e) {
                System.out.println("⚠️ Could not clear history_sample_test_machine table: " + e.getMessage());
            }
        }
        connectionProvider.close();
        System.out.println("🧹 Database cleanup complete\n");
    }
    
    private static int countRegistryEvents() throws Exception {
        MysqlConnectionProvider connectionProvider = new MysqlConnectionProvider();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM registry_sampling_test");
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                int count = rs.getInt(1);
                connectionProvider.close();
                return count;
            }
        }
        connectionProvider.close();
        return 0;
    }
    
    private static int countHistoryEvents() throws Exception {
        MysqlConnectionProvider connectionProvider = new MysqlConnectionProvider();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM history_sample_test_machine");
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                int count = rs.getInt(1);
                connectionProvider.close();
                return count;
            }
        } catch (Exception e) {
            System.out.println("⚠️ Could not count history events: " + e.getMessage());
        }
        connectionProvider.close();
        return 0;
    }
    
    private static void testScenario(String scenarioName, boolean debugMode, SampleLoggingConfig registrySampling, 
                                   SampleLoggingConfig machineSampling, int eventsToSend) throws Exception {
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🧪 SCENARIO: " + scenarioName);
        System.out.println("   Debug Mode: " + debugMode);
        System.out.println("   Registry Sampling: " + registrySampling);
        System.out.println("   Machine Sampling: " + machineSampling);
        System.out.println("   Events to Send: " + eventsToSend);
        System.out.println("=".repeat(60));
        
        clearDatabase();
        
        // Create registry
        TimeoutManager timeoutManager = new TimeoutManager();
        StateMachineRegistry registry = new StateMachineRegistry("sampling_test", timeoutManager, 9996);
        
        // Configure registry sample logging
        registry.setRegistrySampleLogging(registrySampling);
        
        // Enable debug mode if required
        if (debugMode) {
            registry.enableDebugMode(9996);
        }
        
        // Create test machine with sampling configuration
        String machineId = "sample-test-machine";
        CallMachineRunnerEnhanced.CallPersistentContext persistentContext = 
            new CallMachineRunnerEnhanced.CallPersistentContext(machineId, "+1-555-TEST", "+1-555-DEST");
        CallMachineRunnerEnhanced.CallVolatileContext volatileContext = 
            new CallMachineRunnerEnhanced.CallVolatileContext();
        
        GenericStateMachine<CallMachineRunnerEnhanced.CallPersistentContext, CallMachineRunnerEnhanced.CallVolatileContext> machine = 
            EnhancedFluentBuilder.<CallMachineRunnerEnhanced.CallPersistentContext, CallMachineRunnerEnhanced.CallVolatileContext>create(machineId)
                .withPersistentContext(persistentContext)
                .withVolatileContext(volatileContext)
                .withSampleLogging(machineSampling)
                .initialState("IDLE")
                .state("IDLE")
                    .on(IncomingCall.class).to("RINGING")
                    .done()
                .state("RINGING") 
                    .on(Answer.class).to("CONNECTED")
                    .on(Hangup.class).to("IDLE")
                    .done()
                .state("CONNECTED")
                    .on(Hangup.class).to("IDLE")
                    .done()
                .build();
        
        registry.register(machineId, machine);
        machine.start();
        
        // Send events
        System.out.println("🚀 Sending " + eventsToSend + " events...");
        int totalEventsSent = 0;
        
        for (int i = 0; i < eventsToSend; i++) {
            registry.sendEvent(machineId, new IncomingCall());
            totalEventsSent++;
            registry.sendEvent(machineId, new Answer()); 
            totalEventsSent++;
            registry.sendEvent(machineId, new Hangup());
            totalEventsSent++;
        }
        
        System.out.println("✅ Sent " + totalEventsSent + " events total");
        
        // Wait for async logging
        Thread.sleep(2000);
        
        // Count results
        int registryEventsLogged = countRegistryEvents();
        int historyEventsLogged = countHistoryEvents();
        
        System.out.println("\n📊 RESULTS:");
        System.out.println("   Registry events logged: " + registryEventsLogged);
        System.out.println("   History events logged: " + historyEventsLogged);
        
        // Analyze results
        if (debugMode) {
            System.out.println("🔍 DEBUG MODE ANALYSIS:");
            System.out.println("   Expected: ALL events logged (sampling ignored)");
            System.out.println("   Registry rate: " + String.format("%.1f%%", (registryEventsLogged * 100.0 / totalEventsSent)));
            System.out.println("   History rate: " + String.format("%.1f%%", (historyEventsLogged * 100.0 / totalEventsSent)));
            
            if (registryEventsLogged >= totalEventsSent * 0.8 && historyEventsLogged >= totalEventsSent * 0.8) {
                System.out.println("   ✅ SUCCESS: Debug mode logging all events as expected");
            } else {
                System.out.println("   ❌ FAIL: Debug mode not logging all events");
            }
        } else {
            System.out.println("📊 SAMPLING MODE ANALYSIS:");
            double expectedRegistryRate = registrySampling.getExpectedLogRate();
            double expectedHistoryRate = machineSampling.getExpectedLogRate();
            double actualRegistryRate = (registryEventsLogged * 100.0 / totalEventsSent);
            double actualHistoryRate = (historyEventsLogged * 100.0 / totalEventsSent);
            
            System.out.println("   Registry: " + String.format("%.1f%%", actualRegistryRate) + " (expected " + String.format("%.1f%%", expectedRegistryRate) + ")");
            System.out.println("   History: " + String.format("%.1f%%", actualHistoryRate) + " (expected " + String.format("%.1f%%", expectedHistoryRate) + ")");
            
            boolean registryGood = Math.abs(actualRegistryRate - expectedRegistryRate) < 15.0;
            boolean historyGood = Math.abs(actualHistoryRate - expectedHistoryRate) < 15.0;
            
            System.out.println("   Registry sampling: " + (registryGood ? "✅ PASS" : "❌ FAIL"));
            System.out.println("   History sampling: " + (historyGood ? "✅ PASS" : "❌ FAIL"));
        }
        
        registry.shutdownAsyncLogging();
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== Testing New Sampling Behavior ===");
        System.out.println("New Rules:");
        System.out.println("1. If sampling configured: applies regardless of debug mode");
        System.out.println("2. In debug mode: ALL events logged (sampling ignored)");
        System.out.println("3. Non-debug mode: sampling applies normally\n");
        
        // Scenario 1: Debug mode with sampling configured (should log ALL)
        testScenario("Debug Mode + Sampling (Should Log ALL)", 
                    true, 
                    SampleLoggingConfig.oneIn2(), 
                    SampleLoggingConfig.oneIn2(), 
                    20);
        
        // Scenario 2: No debug mode with 1-in-2 sampling (should log ~50%)
        testScenario("No Debug + 1-in-2 Sampling (Should Log ~50%)", 
                    false, 
                    SampleLoggingConfig.oneIn2(), 
                    SampleLoggingConfig.oneIn2(), 
                    20);
        
        // Scenario 3: No debug mode with 1-in-10 sampling (should log ~10%)
        testScenario("No Debug + 1-in-10 Sampling (Should Log ~10%)", 
                    false, 
                    SampleLoggingConfig.oneIn10(), 
                    SampleLoggingConfig.oneIn10(), 
                    20);
        
        // Scenario 4: No debug mode with no sampling (should log nothing)
        testScenario("No Debug + No Sampling (Should Log Nothing)", 
                    false, 
                    SampleLoggingConfig.DISABLED, 
                    SampleLoggingConfig.DISABLED, 
                    10);
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🎉 ALL SCENARIOS COMPLETE");
        System.out.println("=".repeat(60));
        
        System.exit(0);
    }
}