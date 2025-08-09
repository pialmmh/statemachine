package com.telcobright.statemachine.test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Simple Call Machine Demo with Registry-based Creation
 * 
 * Shows realistic call scenarios:
 * 1. Registry creates machine for incoming call
 * 2. Normal call flow with registry management
 * 3. Call going offline during conversation
 * 4. Call timeout scenario with registry cleanup
 */
public class SimpleCallMachineDemo {
    
    public static void main(String[] args) throws Exception {
        System.out.println("📞 Starting Simple Call Machine Demo");
        System.out.println("🏭 All machines created through StateMachineRegistry");
        System.out.println("🎯 Includes offline states and timeout scenarios");
        System.out.println();
        
        // Create scenarios
        createNormalCallScenario();
        Thread.sleep(200);
        
        createOfflineCallScenario(); 
        Thread.sleep(200);
        
        createTimeoutScenario();
        
        System.out.println();
        System.out.println("✅ All scenarios generated!");
        System.out.println("🔄 Refresh monitoring dashboard to see registry-based call flows");
        System.out.println("📊 View at: http://localhost:8091");
    }
    
    /**
     * Normal call flow - registry creates, manages, and completes machine
     */
    private static void createNormalCallScenario() throws Exception {
        String runId = generateRunId("normal-call");
        String className = "SimpleCallMachineDemo";
        String fullPath = "com.telcobright.statemachine.test.SimpleCallMachineDemo";
        
        System.out.println("📞 Scenario 1: Normal Call Flow");
        System.out.println("   Run ID: " + runId);
        
        // 1. IDLE -> RINGING (Registry creates machine for incoming call)
        insertSnapshot(
            "call-001", "CallEntity", runId, 1,
            "IDLE", "RINGING", "INCOMING_CALL", 35,
            className, fullPath,
            "NOT_REGISTERED", "REGISTERED_ACTIVE",
            false, true,
            createEventPayload("{\"callId\": \"call-001\", \"fromNumber\": \"+1-555-1234\", \"toNumber\": \"+1-555-5678\", \"callType\": \"VOICE\", \"registryAction\": \"CREATE_MACHINE\"}"),
            createContext("{\"machineExists\": false, \"registryStatus\": \"NOT_REGISTERED\", \"incomingCallQueue\": 1}"),
            createContext("{\"machineStatus\": \"HYDRATED_ACTIVE\", \"registryStatus\": \"REGISTERED_ACTIVE\", \"callId\": \"call-001\", \"fromNumber\": \"+1-555-1234\", \"ringStartTime\": \"" + LocalDateTime.now() + "\", \"callQuality\": \"GOOD\"}")
        );
        
        // 2. RINGING -> CONNECTED (User answers, registry keeps machine active)
        insertSnapshot(
            "call-001", "CallEntity", runId, 2,
            "RINGING", "CONNECTED", "ANSWER", 120,
            className, fullPath,
            "REGISTERED_ACTIVE", "REGISTERED_ACTIVE",
            true, true,
            createEventPayload("{\"callId\": \"call-001\", \"answeredBy\": \"CALLED_PARTY\", \"answerTime\": \"" + LocalDateTime.now() + "\", \"deviceType\": \"SMARTPHONE\", \"registryAction\": \"KEEP_ACTIVE\"}"),
            createContext("{\"machineStatus\": \"HYDRATED_ACTIVE\", \"registryStatus\": \"REGISTERED_ACTIVE\", \"callState\": \"RINGING\", \"ringDuration\": 3500, \"callQuality\": \"GOOD\"}"),
            createContext("{\"machineStatus\": \"HYDRATED_ACTIVE\", \"registryStatus\": \"REGISTERED_ACTIVE\", \"callState\": \"CONNECTED\", \"connectedTime\": \"" + LocalDateTime.now() + "\", \"callQuality\": \"HD\", \"billingStarted\": true}")
        );
        
        // 3. CONNECTED -> COMPLETED (Normal hangup, registry cleans up)
        insertSnapshot(
            "call-001", "CallEntity", runId, 3,
            "CONNECTED", "COMPLETED", "HANGUP", 50,
            className, fullPath,
            "REGISTERED_ACTIVE", "NOT_REGISTERED",
            true, false,
            createEventPayload("{\"callId\": \"call-001\", \"hangupBy\": \"CALLER\", \"reason\": \"NORMAL_CLEARING\", \"callDuration\": 120000, \"registryAction\": \"DEREGISTER_AND_CLEANUP\"}"),
            createContext("{\"machineStatus\": \"HYDRATED_ACTIVE\", \"registryStatus\": \"REGISTERED_ACTIVE\", \"callState\": \"CONNECTED\", \"callDuration\": 120000, \"callQuality\": \"HD\"}"),
            createContext("{\"machineStatus\": \"DEHYDRATED_COMPLETED\", \"registryStatus\": \"NOT_REGISTERED\", \"callState\": \"COMPLETED\", \"finalDuration\": 120000, \"callCost\": 2.45, \"outcome\": \"SUCCESSFUL\"}")
        );
    }
    
    /**
     * Call goes offline during conversation
     */
    private static void createOfflineCallScenario() throws Exception {
        String runId = generateRunId("offline-call");
        String className = "SimpleCallMachineDemo";
        String fullPath = "com.telcobright.statemachine.test.SimpleCallMachineDemo";
        
        System.out.println("📞 Scenario 2: Call Goes Offline");
        System.out.println("   Run ID: " + runId);
        
        // 1. IDLE -> RINGING (Registry creates machine)
        insertSnapshot(
            "call-002", "CallEntity", runId, 1,
            "IDLE", "RINGING", "INCOMING_CALL", 28,
            className, fullPath,
            "NOT_REGISTERED", "REGISTERED_ACTIVE",
            false, true,
            createEventPayload("{\"callId\": \"call-002\", \"fromNumber\": \"+1-555-9876\", \"toNumber\": \"+1-555-1111\", \"callType\": \"VOICE\", \"registryAction\": \"CREATE_MACHINE\"}"),
            createContext("{\"machineExists\": false, \"registryStatus\": \"NOT_REGISTERED\", \"incomingCallQueue\": 1}"),
            createContext("{\"machineStatus\": \"HYDRATED_ACTIVE\", \"registryStatus\": \"REGISTERED_ACTIVE\", \"callId\": \"call-002\", \"fromNumber\": \"+1-555-9876\", \"signalStrength\": \"WEAK\", \"callQuality\": \"POOR\"}")
        );
        
        // 2. RINGING -> CONNECTED (Quick answer)
        insertSnapshot(
            "call-002", "CallEntity", runId, 2,
            "RINGING", "CONNECTED", "ANSWER", 80,
            className, fullPath,
            "REGISTERED_ACTIVE", "REGISTERED_ACTIVE",
            true, true,
            createEventPayload("{\"callId\": \"call-002\", \"answeredBy\": \"CALLED_PARTY\", \"answerTime\": \"" + LocalDateTime.now() + "\", \"signalStrength\": \"WEAK\", \"registryAction\": \"KEEP_ACTIVE\"}"),
            createContext("{\"machineStatus\": \"HYDRATED_ACTIVE\", \"registryStatus\": \"REGISTERED_ACTIVE\", \"callState\": \"RINGING\", \"ringDuration\": 2100, \"signalStrength\": \"WEAK\"}"),
            createContext("{\"machineStatus\": \"HYDRATED_ACTIVE\", \"registryStatus\": \"REGISTERED_ACTIVE\", \"callState\": \"CONNECTED\", \"connectedTime\": \"" + LocalDateTime.now() + "\", \"callQuality\": \"POOR\", \"dropoutRisk\": \"HIGH\"}")
        );
        
        // 3. CONNECTED -> OFFLINE (Network issues, registry marks offline)
        insertSnapshot(
            "call-002", "CallEntity", runId, 3,
            "CONNECTED", "OFFLINE", "NETWORK_DROPOUT", 0,  // No duration - immediate dropout
            className, fullPath,
            "REGISTERED_ACTIVE", "REGISTERED_INACTIVE",
            true, false,  // Machine goes offline
            createEventPayload("{\"callId\": \"call-002\", \"reason\": \"NETWORK_FAILURE\", \"lastSignalStrength\": \"NONE\", \"registryAction\": \"MARK_OFFLINE_RETRY_LATER\"}"),
            createContext("{\"machineStatus\": \"HYDRATED_ACTIVE\", \"registryStatus\": \"REGISTERED_ACTIVE\", \"callState\": \"CONNECTED\", \"callDuration\": 45000, \"signalStrength\": \"WEAK\"}"),
            createContext("{\"machineStatus\": \"OFFLINE\", \"registryStatus\": \"REGISTERED_INACTIVE\", \"callState\": \"OFFLINE\", \"dropoutTime\": \"" + LocalDateTime.now() + "\", \"retryScheduled\": true, \"nextRetryIn\": \"30s\"}")
        );
        
        // 4. OFFLINE -> COMPLETED (Retry fails, registry gives up)
        insertSnapshot(
            "call-002", "CallEntity", runId, 4,
            "OFFLINE", "COMPLETED", "RETRY_FAILED", 30000,  // 30 second retry attempt
            className, fullPath,
            "REGISTERED_INACTIVE", "NOT_REGISTERED",
            false, false,
            createEventPayload("{\"callId\": \"call-002\", \"retryAttempts\": 3, \"allRetriesFailed\": true, \"registryAction\": \"DEREGISTER_FAILED_CALL\"}"),
            createContext("{\"machineStatus\": \"OFFLINE\", \"registryStatus\": \"REGISTERED_INACTIVE\", \"retryAttempts\": 2, \"retryScheduled\": true}"),
            createContext("{\"machineStatus\": \"DEHYDRATED_COMPLETED\", \"registryStatus\": \"NOT_REGISTERED\", \"callState\": \"FAILED\", \"failureReason\": \"NETWORK_UNREACHABLE\", \"partialDuration\": 45000, \"callCost\": 0.00}")
        );
    }
    
    /**
     * Call timeout scenario
     */
    private static void createTimeoutScenario() throws Exception {
        String runId = generateRunId("timeout-call");
        String className = "SimpleCallMachineDemo";
        String fullPath = "com.telcobright.statemachine.test.SimpleCallMachineDemo";
        
        System.out.println("📞 Scenario 3: Call Timeout");
        System.out.println("   Run ID: " + runId);
        
        // 1. IDLE -> RINGING (Registry creates machine)
        insertSnapshot(
            "call-003", "CallEntity", runId, 1,
            "IDLE", "RINGING", "INCOMING_CALL", 32,
            className, fullPath,
            "NOT_REGISTERED", "REGISTERED_ACTIVE",
            false, true,
            createEventPayload("{\"callId\": \"call-003\", \"fromNumber\": \"+1-555-BUSY\", \"toNumber\": \"+1-555-2222\", \"callType\": \"VOICE\", \"registryAction\": \"CREATE_MACHINE\", \"timeoutSet\": \"60s\"}"),
            createContext("{\"machineExists\": false, \"registryStatus\": \"NOT_REGISTERED\", \"incomingCallQueue\": 1}"),
            createContext("{\"machineStatus\": \"HYDRATED_ACTIVE\", \"registryStatus\": \"REGISTERED_ACTIVE\", \"callId\": \"call-003\", \"timeoutScheduled\": \"" + LocalDateTime.now().plusSeconds(60) + "\", \"maxRingTime\": 60000}")
        );
        
        // 2. RINGING -> TIMEOUT (No answer, registry handles timeout)
        insertSnapshot(
            "call-003", "CallEntity", runId, 2,
            "RINGING", "TIMEOUT", "RING_TIMEOUT", 60000,  // Full 60 second timeout
            className, fullPath,
            "REGISTERED_ACTIVE", "REGISTERED_INACTIVE",
            true, false,
            createEventPayload("{\"callId\": \"call-003\", \"timeoutReason\": \"NO_ANSWER\", \"ringDuration\": 60000, \"registryAction\": \"TIMEOUT_AND_DEACTIVATE\"}"),
            createContext("{\"machineStatus\": \"HYDRATED_ACTIVE\", \"registryStatus\": \"REGISTERED_ACTIVE\", \"callState\": \"RINGING\", \"ringDuration\": 59500, \"timeoutPending\": true}"),
            createContext("{\"machineStatus\": \"HYDRATED_INACTIVE\", \"registryStatus\": \"REGISTERED_INACTIVE\", \"callState\": \"TIMEOUT\", \"timeoutTime\": \"" + LocalDateTime.now() + "\", \"cleanupScheduled\": true}")
        );
        
        // 3. TIMEOUT -> COMPLETED (Registry cleanup)
        insertSnapshot(
            "call-003", "CallEntity", runId, 3,
            "TIMEOUT", "COMPLETED", "CLEANUP_TIMEOUT", 100,
            className, fullPath,
            "REGISTERED_INACTIVE", "NOT_REGISTERED",
            false, false,
            createEventPayload("{\"callId\": \"call-003\", \"cleanupReason\": \"TIMEOUT_CLEANUP\", \"registryAction\": \"DEREGISTER_TIMEOUT\"}"),
            createContext("{\"machineStatus\": \"HYDRATED_INACTIVE\", \"registryStatus\": \"REGISTERED_INACTIVE\", \"cleanupScheduled\": true, \"timeoutDuration\": 60000}"),
            createContext("{\"machineStatus\": \"DEHYDRATED_COMPLETED\", \"registryStatus\": \"NOT_REGISTERED\", \"callState\": \"TIMEOUT_COMPLETED\", \"outcome\": \"NO_ANSWER\", \"totalRingTime\": 60000, \"callCost\": 0.00}")
        );
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
        return Base64.getEncoder().encodeToString(json.getBytes());
    }
    
    private static String createContext(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes());
    }
    
    private static String generateRunId(String prefix) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        String timestamp = LocalDateTime.now().format(formatter);
        String randomSuffix = String.valueOf(System.nanoTime() % 100000);
        return prefix + "-" + timestamp + "-" + randomSuffix;
    }
}