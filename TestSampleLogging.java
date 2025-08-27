import com.telcobright.statemachine.StateMachineRegistry;
import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.websocket.CallMachineRunnerEnhanced;
import com.telcobright.statemachine.timeout.TimeoutManager;
import com.telcobright.statemachine.SampleLoggingConfig;
import com.telcobright.statemachine.EnhancedFluentBuilder;
import com.telcobright.statemachineexamples.callmachine.events.IncomingCall;
import com.telcobright.statemachineexamples.callmachine.events.Answer;
import com.telcobright.statemachineexamples.callmachine.events.Hangup;
import com.telcobright.statemachine.persistence.MysqlConnectionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class TestSampleLogging {
    
    private static void clearDatabase() throws Exception {
        System.out.println("🗑️ Clearing database tables...");
        
        MysqlConnectionProvider connectionProvider = new MysqlConnectionProvider();
        try (Connection conn = connectionProvider.getConnection()) {
            // Clear registry event logs
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM registry_call")) {
                stmt.executeUpdate();
                System.out.println("✅ Cleared registry_call table");
            } catch (Exception e) {
                System.out.println("⚠️ Could not clear registry_call table: " + e.getMessage());
            }
            
            // Clear history tables
            String[] historyTables = {"history_call_001", "history_call_002", "history_call_003"};
            for (String table : historyTables) {
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM " + table)) {
                    stmt.executeUpdate();
                    System.out.println("✅ Cleared " + table + " table");
                } catch (Exception e) {
                    System.out.println("⚠️ Could not clear " + table + " table: " + e.getMessage());
                }
            }
        }
        connectionProvider.close();
        System.out.println("🧹 Database cleanup complete\n");
    }
    
    private static int countRegistryEvents() throws Exception {
        MysqlConnectionProvider connectionProvider = new MysqlConnectionProvider();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM registry_call");
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
        int totalCount = 0;
        
        try (Connection conn = connectionProvider.getConnection()) {
            String[] historyTables = {"history_call_001", "history_call_002", "history_call_003"};
            for (String table : historyTables) {
                try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM " + table);
                     ResultSet rs = stmt.executeQuery()) {
                    
                    if (rs.next()) {
                        totalCount += rs.getInt(1);
                    }
                } catch (Exception e) {
                    System.out.println("⚠️ Could not count " + table + ": " + e.getMessage());
                }
            }
        }
        connectionProvider.close();
        return totalCount;
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== Testing Sample Logging (1 in 2 events) ===");
        
        // Step 1: Clear database
        clearDatabase();
        
        // Step 2: Create registry with custom machine that has 1-in-2 sampling
        TimeoutManager timeoutManager = new TimeoutManager();
        StateMachineRegistry registry = new StateMachineRegistry("call", timeoutManager, 9997);
        
        // Configure 1-in-2 sample logging for registry events
        registry.setRegistrySampleLogging(SampleLoggingConfig.oneIn2());
        
        // Enable debug mode to activate logging
        registry.enableDebugMode(9997);
        
        System.out.println("📊 Sample logging configuration:");
        System.out.println("   Registry: " + SampleLoggingConfig.oneIn2());
        System.out.println("   Expected logging rate: 50%\n");
        
        // Step 3: Create a test machine with 1-in-2 sampling configured
        String machineId = "test-sample-machine";
        CallMachineRunnerEnhanced.CallPersistentContext persistentContext = 
            new CallMachineRunnerEnhanced.CallPersistentContext(machineId, "+1-555-TEST", "+1-555-DEST");
        CallMachineRunnerEnhanced.CallVolatileContext volatileContext = 
            new CallMachineRunnerEnhanced.CallVolatileContext();
        
        // Build machine with 1-in-2 sample logging
        GenericStateMachine<CallMachineRunnerEnhanced.CallPersistentContext, CallMachineRunnerEnhanced.CallVolatileContext> machine = 
            EnhancedFluentBuilder.<CallMachineRunnerEnhanced.CallPersistentContext, CallMachineRunnerEnhanced.CallVolatileContext>create(machineId)
                .withPersistentContext(persistentContext)
                .withVolatileContext(volatileContext)
                .withSampleLogging(2) // 1 in 2 events
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
        
        // Register the machine
        registry.register(machineId, machine);
        machine.start();
        
        // Wait for initialization
        Thread.sleep(1000);
        
        // Step 4: Send many events and track what we send
        System.out.println("🚀 Sending test events...");
        int totalEventsSent = 0;
        int registryEventsSent = 0;
        
        // Send 100 events to generate both registry events and state machine history events
        for (int i = 0; i < 50; i++) {
            // Each cycle sends 3 events: IncomingCall -> Answer -> Hangup
            registry.sendEvent(machineId, new IncomingCall());
            totalEventsSent++;
            registryEventsSent++; // Registry processes this event
            
            registry.sendEvent(machineId, new Answer()); 
            totalEventsSent++;
            registryEventsSent++;
            
            registry.sendEvent(machineId, new Hangup());
            totalEventsSent++;
            registryEventsSent++;
            
            if (i % 10 == 0) {
                System.out.println("   Sent " + (i * 3) + " events...");
            }
        }
        
        System.out.println("✅ Completed sending " + totalEventsSent + " events");
        System.out.println("📈 Total registry operations: " + registryEventsSent);
        
        // Wait for async logging to complete
        System.out.println("⏱️ Waiting for async logging to complete...");
        Thread.sleep(3000);
        
        // Step 5: Count events in database
        int registryEventsLogged = countRegistryEvents();
        int historyEventsLogged = countHistoryEvents();
        
        System.out.println("\n📊 === SAMPLE LOGGING RESULTS ===");
        System.out.println("Registry Events:");
        System.out.println("   Expected to log: ~" + (registryEventsSent / 2) + " events (50%)");
        System.out.println("   Actually logged: " + registryEventsLogged + " events");
        System.out.println("   Actual rate: " + String.format("%.1f%%", (registryEventsLogged * 100.0 / registryEventsSent)));
        
        System.out.println("\nHistory Events:");
        System.out.println("   Expected to log: ~" + (totalEventsSent / 2) + " events (50%)");
        System.out.println("   Actually logged: " + historyEventsLogged + " events");
        System.out.println("   Actual rate: " + String.format("%.1f%%", (historyEventsLogged * 100.0 / totalEventsSent)));
        
        // Step 6: Validate results
        System.out.println("\n🔍 === VALIDATION ===");
        
        double registryRate = (registryEventsLogged * 100.0 / registryEventsSent);
        double historyRate = (historyEventsLogged * 100.0 / totalEventsSent);
        
        boolean registrySuccess = Math.abs(registryRate - 50.0) < 10.0; // Within 10% of expected
        boolean historySuccess = Math.abs(historyRate - 50.0) < 10.0;
        
        System.out.println("Registry sampling: " + (registrySuccess ? "✅ PASS" : "❌ FAIL") + 
                          " (" + String.format("%.1f%%", registryRate) + " vs expected 50%)");
        System.out.println("History sampling: " + (historySuccess ? "✅ PASS" : "❌ FAIL") + 
                          " (" + String.format("%.1f%%", historyRate) + " vs expected 50%)");
        
        if (registrySuccess && historySuccess) {
            System.out.println("\n🎉 SUCCESS: Sample logging is working correctly!");
            System.out.println("   Both registry and history events are being logged at ~50% rate");
        } else {
            System.out.println("\n⚠️ WARNING: Sample logging may not be working as expected");
            System.out.println("   Check the sampling logic implementation");
        }
        
        System.out.println("\n=== Test Complete ===");
        registry.shutdownAsyncLogging();
        System.exit(0);
    }
}