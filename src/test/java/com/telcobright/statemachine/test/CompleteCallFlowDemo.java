package com.telcobright.statemachine.test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Complete CallMachine State Flow Demo
 * 
 * Shows all state transitions based on CallMachine implementation:
 * 1. STARTUP → IDLE (machine created)
 * 2. IDLE → RINGING (IncomingCall event) - machine goes OFFLINE
 * 3. RINGING → RINGING (SessionProgress stay events) - rehydration
 * 4. RINGING → CONNECTED (Answer event)  
 * 5. CONNECTED → IDLE (Hangup event) - final state
 * 
 * Demonstrates:
 * - Offline state behavior (RINGING state marked .offline())
 * - Registry deregistration/rehydration cycles
 * - Stay events (SessionProgress while ringing)
 * - Complete CallContext evolution through states
 */
public class CompleteCallFlowDemo {
    
    public static void main(String[] args) throws Exception {
        System.out.println("📞 Complete CallMachine State Flow Demo");
        System.out.println("🔄 Based on actual CallMachine state definitions");
        System.out.println("💾 Shows offline states and rehydration");
        System.out.println();
        
        // Clear previous data
        clearMonitoringData();
        
        // Generate complete flow
        generateCompleteCallFlow();
        
        System.out.println();
        System.out.println("✅ Complete CallMachine flow generated!");
        System.out.println("📊 View at: http://localhost:8091");
        System.out.println("🔍 Notice the offline/rehydration cycle in RINGING state");
    }
    
    private static void generateCompleteCallFlow() throws Exception {
        String runId = generateRunId("complete-flow");
        String callId = "call-" + System.currentTimeMillis();
        String className = "CallMachine";
        String fullPath = "com.telcobright.statemachineexamples.callmachine.CallMachine";
        
        System.out.println("📞 Generating complete call flow");
        System.out.println("   Call ID: " + callId);
        System.out.println("   Run ID: " + runId);
        System.out.println();
        
        LocalDateTime callStart = LocalDateTime.now();
        
        // Step 1: Machine Creation - STARTUP → IDLE
        System.out.println("1️⃣ Machine Creation: STARTUP → IDLE");
        insertSnapshot(callId, "CallEntity", runId, 1, 
            "STARTUP", "IDLE", "MACHINE_CREATED", 10,
            className, fullPath,
            "NOT_REGISTERED", "REGISTERED_ACTIVE", false, true,
            createEventPayload("{\"action\": \"CREATE_MACHINE\", \"initialState\": \"IDLE\"}"),
            createContext("{}"),
            createContext(buildEntityAndContext(callId, "IDLE", "INITIALIZING", 0, false, 0, callStart, null, null, null, 0)));
        
        // Step 2: IncomingCall Event - IDLE → RINGING (goes offline)
        System.out.println("2️⃣ IncomingCall: IDLE → RINGING (machine goes OFFLINE)");
        insertSnapshot(callId, "CallEntity", runId, 2,
            "IDLE", "RINGING", "INCOMING_CALL", 35,
            className, fullPath,
            "REGISTERED_ACTIVE", "REGISTERED_INACTIVE", true, false, // Machine goes offline!
            createEventPayload("{\"fromNumber\": \"+1-555-1234\", \"toNumber\": \"+1-555-5678\", \"callType\": \"VOICE\"}"),
            createContext(buildEntityAndContext(callId, "IDLE", "INITIALIZING", 0, false, 0, callStart, null, null, null, 0)),
            createContext(buildEntityAndContext(callId, "RINGING", "RINGING", 1, false, 0, callStart, null, null, null, 0)));
        
        Thread.sleep(100);
        
        // Step 3: SessionProgress (stay event) - RINGING → RINGING (rehydration)
        System.out.println("3️⃣ SessionProgress (stay): RINGING → RINGING (rehydration required)");
        insertSnapshot(callId, "CallEntity", runId, 3,
            "RINGING", "RINGING", "SESSION_PROGRESS", 0,
            className, fullPath,
            "REGISTERED_INACTIVE", "REGISTERED_ACTIVE", false, true, // Rehydrated!
            createEventPayload("{\"progressType\": \"180\", \"progressValue\": 75, \"description\": \"Ringing Indication\"}"),
            createContext(buildEntityAndContext(callId, "RINGING", "RINGING", 1, false, 0, callStart, null, null, null, 0)),
            createContext(buildEntityAndContext(callId, "RINGING", "RINGING", 1, false, 0, callStart, null, null, null, 1)));
        
        Thread.sleep(100);
        
        // Step 4: Another SessionProgress (stay event) - RINGING → RINGING  
        System.out.println("4️⃣ SessionProgress (stay): RINGING → RINGING (early media)");
        insertSnapshot(callId, "CallEntity", runId, 4,
            "RINGING", "RINGING", "SESSION_PROGRESS", 0,
            className, fullPath,
            "REGISTERED_ACTIVE", "REGISTERED_ACTIVE", true, true,
            createEventPayload("{\"progressType\": \"183\", \"progressValue\": 100, \"description\": \"Early Media\"}"),
            createContext(buildEntityAndContext(callId, "RINGING", "RINGING", 1, false, 0, callStart, null, null, null, 1)),
            createContext(buildEntityAndContext(callId, "RINGING", "RINGING", 1, false, 0, callStart, null, null, null, 2)));
        
        Thread.sleep(100);
        
        // Step 5: Answer Event - RINGING → CONNECTED
        System.out.println("5️⃣ Answer: RINGING → CONNECTED");
        LocalDateTime connectTime = LocalDateTime.now();
        insertSnapshot(callId, "CallEntity", runId, 5,
            "RINGING", "CONNECTED", "ANSWER", 2800,
            className, fullPath,
            "REGISTERED_ACTIVE", "REGISTERED_ACTIVE", true, true,
            createEventPayload("{\"answeredBy\": \"CALLED_PARTY\", \"deviceType\": \"SMARTPHONE\", \"answerDelay\": 2800}"),
            createContext(buildEntityAndContext(callId, "RINGING", "RINGING", 1, false, 0, callStart, null, null, null, 2)),
            createContext(buildEntityAndContext(callId, "CONNECTED", "CONNECTED", 1, true, 3, callStart, connectTime, null, null, 3)));
        
        Thread.sleep(100);
        
        // Step 6: Hangup Event - CONNECTED → IDLE (final state)
        System.out.println("6️⃣ Hangup: CONNECTED → IDLE (call completed)");
        LocalDateTime endTime = LocalDateTime.now();
        insertSnapshot(callId, "CallEntity", runId, 6,
            "CONNECTED", "IDLE", "HANGUP", 95000,
            className, fullPath,
            "REGISTERED_ACTIVE", "NOT_REGISTERED", true, false, // Deregistered after completion
            createEventPayload("{\"hangupBy\": \"CALLER\", \"reason\": \"NORMAL_CLEARING\", \"callDuration\": 95000}"),
            createContext(buildEntityAndContext(callId, "CONNECTED", "CONNECTED", 1, true, 95, callStart, connectTime, null, null, 3)),
            createContext(buildEntityAndContext(callId, "IDLE", "ENDED", 1, true, 95, callStart, connectTime, endTime, "NORMAL_CLEARING", 4)));
        
        System.out.println("✅ Complete call flow generated: " + callId);
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
    
    private static void clearMonitoringData() throws Exception {
        System.out.println("🧹 Clearing previous monitoring data...");
        ProcessBuilder pb = new ProcessBuilder(
            "docker", "exec", "statemachine-postgres",
            "psql", "-U", "statemachine", "-d", "statemachine_monitoring",
            "-c", "DELETE FROM state_machine_snapshots;"
        );
        pb.start().waitFor();
    }
    
    private static String createEventPayload(String json) {
        return java.util.Base64.getEncoder().encodeToString(json.getBytes());
    }
    
    private static String createContext(String json) {
        return java.util.Base64.getEncoder().encodeToString(json.getBytes());
    }
    
    private static String buildEntityAndContext(String callId, String state, String callStatus, 
                                                int ringCount, boolean recording, long durationSeconds,
                                                LocalDateTime startTime, LocalDateTime connectTime, 
                                                LocalDateTime endTime, String disconnectReason, 
                                                int sessionEventCount) {
        StringBuilder json = new StringBuilder("{");
        
        // CallEntity (persisted data)
        json.append("\"callEntity\": {");
        json.append("\"callId\": \"").append(callId).append("\",");
        json.append("\"currentState\": \"").append(state).append("\",");
        json.append("\"fromNumber\": \"+1-555-1234\",");
        json.append("\"toNumber\": \"+1-555-5678\",");
        json.append("\"callStatus\": \"").append(callStatus).append("\",");
        json.append("\"durationSeconds\": ").append(durationSeconds).append(",");
        json.append("\"recordingEnabled\": ").append(recording).append(",");
        json.append("\"ringCount\": ").append(ringCount).append(",");
        json.append("\"isComplete\": ").append("IDLE".equals(state) && endTime != null).append(",");
        json.append("\"createdAt\": \"").append(startTime).append("\",");
        json.append("\"updatedAt\": \"").append(LocalDateTime.now()).append("\"");
        if (endTime != null) {
            json.append(",\"endedAt\": \"").append(endTime).append("\"");
        }
        json.append("},");
        
        // CallContext (volatile data) 
        json.append("\"callContext\": {");
        json.append("\"callId\": \"").append(callId).append("\",");
        json.append("\"fromNumber\": \"+1-555-1234\",");
        json.append("\"toNumber\": \"+1-555-5678\",");
        json.append("\"callDirection\": \"INBOUND\",");
        json.append("\"callStatus\": \"").append(callStatus).append("\",");
        json.append("\"ringCount\": ").append(ringCount).append(",");
        json.append("\"recordingEnabled\": ").append(recording).append(",");
        json.append("\"startTime\": \"").append(startTime).append("\"");
        if (connectTime != null) {
            json.append(",\"connectTime\": \"").append(connectTime).append("\"");
        }
        if (endTime != null) {
            json.append(",\"endTime\": \"").append(endTime).append("\"");
        }
        if (disconnectReason != null) {
            json.append(",\"disconnectReason\": \"").append(disconnectReason).append("\"");
        }
        if (sessionEventCount > 0) {
            json.append(",\"sessionEventsCount\": ").append(sessionEventCount);
        }
        json.append("}");
        
        json.append("}");
        return json.toString();
    }
    
    private static String generateRunId(String prefix) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        String timestamp = LocalDateTime.now().format(formatter);
        String randomSuffix = String.valueOf(System.nanoTime() % 100000);
        return prefix + "-" + timestamp + "-" + randomSuffix;
    }
}