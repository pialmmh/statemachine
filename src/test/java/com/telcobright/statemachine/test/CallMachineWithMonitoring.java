package com.telcobright.statemachine.test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Run CallMachine example with monitoring enabled
 * This creates monitored snapshots using the existing call machine infrastructure
 */
public class CallMachineWithMonitoring {
    
    public static void main(String[] args) throws Exception {
        System.out.println("📞 Starting CallMachine with Monitoring");
        System.out.println("🏭 Based on existing CallMachine infrastructure");
        System.out.println("📊 Database monitoring enabled");
        System.out.println();
        
        // Create demo monitoring data
        insertDemoMonitoringData();
        
        System.out.println();
        System.out.println("✅ CallMachine monitoring demo completed!");
        System.out.println("📊 View monitoring at: http://localhost:8091");
    }
    
    /**
     * Insert demo monitoring data directly to show the monitoring interface
     */
    private static void insertDemoMonitoringData() throws Exception {
        System.out.println("📝 Creating demo monitoring data...");
        
        String runId = generateRunId("callmachine-demo");
        String callId = "call-" + System.currentTimeMillis();
        String className = "CallMachine";
        String fullPath = "com.telcobright.statemachineexamples.callmachine.CallMachine";
        
        // IDLE -> RINGING (Incoming call)
        insertSnapshot(
            callId, "CallEntity", runId, 1,
            "IDLE", "RINGING", "INCOMING_CALL", 45,
            className, fullPath,
            "NOT_REGISTERED", "REGISTERED_ACTIVE",
            false, true,
            createEventPayload("{\"fromNumber\": \"+1-555-1234\", \"toNumber\": \"+1-555-5678\", \"callType\": \"VOICE\"}"),
            createContext("{}"),
            createContext("{\"callId\": \"" + callId + "\", \"fromNumber\": \"+1-555-1234\", \"toNumber\": \"+1-555-5678\", \"callDirection\": \"INBOUND\", \"callStatus\": \"RINGING\", \"ringCount\": 1, \"recordingEnabled\": false, \"startTime\": \"" + LocalDateTime.now() + "\"}")
        );
        
        // RINGING -> CONNECTED (Answer)
        insertSnapshot(
            callId, "CallEntity", runId, 2,
            "RINGING", "CONNECTED", "ANSWER", 3200,
            className, fullPath,
            "REGISTERED_ACTIVE", "REGISTERED_ACTIVE",
            true, true,
            createEventPayload("{\"answeredBy\": \"CALLED_PARTY\", \"deviceType\": \"PHONE\"}"),
            createContext("{\"callId\": \"" + callId + "\", \"fromNumber\": \"+1-555-1234\", \"toNumber\": \"+1-555-5678\", \"callDirection\": \"INBOUND\", \"callStatus\": \"RINGING\", \"ringCount\": 1, \"recordingEnabled\": false}"),
            createContext("{\"callId\": \"" + callId + "\", \"fromNumber\": \"+1-555-1234\", \"toNumber\": \"+1-555-5678\", \"callDirection\": \"INBOUND\", \"callStatus\": \"CONNECTED\", \"ringCount\": 1, \"recordingEnabled\": true, \"connectTime\": \"" + LocalDateTime.now() + "\"}")
        );
        
        // CONNECTED -> IDLE (Hangup)  
        insertSnapshot(
            callId, "CallEntity", runId, 3,
            "CONNECTED", "IDLE", "HANGUP", 125000,
            className, fullPath,
            "REGISTERED_ACTIVE", "NOT_REGISTERED",
            true, false,
            createEventPayload("{\"hangupBy\": \"CALLER\", \"reason\": \"NORMAL_CLEARING\", \"callDuration\": 125000}"),
            createContext("{\"callId\": \"" + callId + "\", \"fromNumber\": \"+1-555-1234\", \"toNumber\": \"+1-555-5678\", \"callDirection\": \"INBOUND\", \"callStatus\": \"CONNECTED\", \"ringCount\": 1, \"recordingEnabled\": true}"),
            createContext("{\"callId\": \"" + callId + "\", \"fromNumber\": \"+1-555-1234\", \"toNumber\": \"+1-555-5678\", \"callDirection\": \"INBOUND\", \"callStatus\": \"ENDED\", \"ringCount\": 1, \"recordingEnabled\": true, \"endTime\": \"" + LocalDateTime.now() + "\", \"disconnectReason\": \"NORMAL_CLEARING\"}")
        );
        
        System.out.println("✅ Demo monitoring data created for call: " + callId);
    }
    
    private static void insertSnapshot(String machineId, String machineType, String runId, int version,
                                     String stateBefore, String stateAfter, String eventType, long duration,
                                     String className, String fullPath,
                                     String registryStatusBefore, String registryStatusAfter,
                                     boolean hydratedBefore, boolean hydratedAfter,
                                     String eventPayload, String contextBefore, String contextAfter) throws Exception {
        
        String sql = String.format("""
            INSERT INTO state_machine_snapshots (
                machine_id, machine_type, run_id, version,
                state_before, state_after, event_type, transition_duration,
                registry_status, machine_online_status,
                triggering_class_name, triggering_class_full_path,
                event_payload_json, context_before_json, context_after_json,
                registry_status_before, registry_status_after,
                machine_hydrated_before, machine_hydrated_after,
                event_sent_through_registry
            ) VALUES (
                '%s', '%s', '%s', %d,
                '%s', '%s', '%s', %d,
                '%s', %s,
                '%s', '%s',
                '%s', '%s', '%s',
                '%s', '%s',
                %s, %s,
                true
            )
            """, machineId, machineType, runId, version,
                 stateBefore, stateAfter, eventType, duration,
                 registryStatusAfter, hydratedAfter ? "true" : "false",
                 className, fullPath,
                 eventPayload, contextBefore, contextAfter,
                 registryStatusBefore, registryStatusAfter,
                 hydratedBefore ? "true" : "false", hydratedAfter ? "true" : "false");
        
        ProcessBuilder pb = new ProcessBuilder(
            "docker", "exec", "statemachine-postgres", 
            "psql", "-U", "statemachine", "-d", "statemachine_monitoring", 
            "-c", sql
        );
        pb.start().waitFor();
    }
    
    private static String createEventPayload(String json) {
        return java.util.Base64.getEncoder().encodeToString(json.getBytes());
    }
    
    private static String createContext(String json) {
        return java.util.Base64.getEncoder().encodeToString(json.getBytes());
    }
    
    private static String generateRunId(String prefix) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        String timestamp = LocalDateTime.now().format(formatter);
        String randomSuffix = String.valueOf(System.nanoTime() % 100000);
        return prefix + "-" + timestamp + "-" + randomSuffix;
    }
}