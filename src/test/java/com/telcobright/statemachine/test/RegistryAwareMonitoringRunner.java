package com.telcobright.statemachine.test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Registry-Aware Monitoring Test Runner
 * 
 * Simulates proper state machine execution where:
 * 1. All events are sent through the registry
 * 2. Registry decides whether to hydrate/dehydrate machines
 * 3. Context tracks registry status before/after each event
 * 4. Shows realistic machine lifecycle management
 */
public class RegistryAwareMonitoringRunner {
    
    public static void main(String[] args) throws Exception {
        System.out.println("🏭 Generating registry-aware monitoring data...");
        System.out.println("📋 All events flow through StateMachineRegistry");
        System.out.println("🔄 Tracking machine hydration/dehydration states");
        
        // Clean old data first
        cleanOldData();
        
        // Generate registry-aware call flow
        generateRegistryAwareCallFlow();
        
        // Generate registry-aware SMS flow with completion scenario
        generateRegistryAwareSmsFlow();
        
        // Generate machine lifecycle scenario (hydration/dehydration)
        generateMachineLifecycleFlow();
        
        System.out.println("✅ Registry-aware monitoring data generated!");
        System.out.println("🔄 Refresh dashboard to see registry-mediated state transitions");
    }
    
    private static void cleanOldData() throws Exception {
        System.out.println("🧹 Cleaning old monitoring data...");
        
        ProcessBuilder pb = new ProcessBuilder(
            "docker", "exec", "statemachine-postgres",
            "psql", "-U", "statemachine", "-d", "statemachine_monitoring",
            "-c", "DELETE FROM state_machine_snapshots;"
        );
        pb.start().waitFor();
    }
    
    /**
     * Generate call flow showing registry decision-making
     */
    private static void generateRegistryAwareCallFlow() throws Exception {
        String runId = generateRunId("registry-call");
        String className = "RegistryCallHandler";
        String fullPath = "com.telcobright.statemachine.registry.handlers.RegistryCallHandler";
        
        System.out.println("📞 Generating registry-aware call flow: " + runId);
        
        // Step 1: IDLE -> RINGING (Registry hydrates machine for incoming call)
        insertRegistrySnapshot(
            "call-registry-001", "CallEntity", runId, 1,
            "IDLE", "RINGING", "INCOMING_CALL", 35,
            className, fullPath,
            "REGISTERED_INACTIVE", "REGISTERED_ACTIVE",  // Registry status before/after
            false, true,  // Machine hydration before/after
            createEventPayload("{\"eventSource\": \"REGISTRY\", \"fromNumber\": \"+1-555-CALL\", \"toNumber\": \"+1-555-DEST\", \"callType\": \"VOICE\", \"registryDecision\": \"HYDRATE_AND_PROCESS\"}"),
            createContextBefore("{\"machineStatus\": \"DEHYDRATED\", \"registryStatus\": \"REGISTERED_INACTIVE\", \"lastActivity\": null, \"queuedEvents\": 0}"),
            createContextAfter("{\"machineStatus\": \"HYDRATED_ACTIVE\", \"registryStatus\": \"REGISTERED_ACTIVE\", \"currentCall\": {\"id\": \"call-registry-001\", \"from\": \"+1-555-CALL\"}, \"lastActivity\": \"" + LocalDateTime.now() + "\", \"queuedEvents\": 0}")
        );
        
        Thread.sleep(100);
        
        // Step 2: RINGING -> CONNECTED (Registry processes answer event)
        insertRegistrySnapshot(
            "call-registry-001", "CallEntity", runId, 2,
            "RINGING", "CONNECTED", "ANSWER", 120,
            className, fullPath,
            "REGISTERED_ACTIVE", "REGISTERED_ACTIVE",  // Registry keeps machine active
            true, true,  // Machine stays hydrated
            createEventPayload("{\"eventSource\": \"REGISTRY\", \"answeredBy\": \"USER\", \"deviceType\": \"MOBILE\", \"registryDecision\": \"PROCESS_ON_ACTIVE_MACHINE\"}"),
            createContextBefore("{\"machineStatus\": \"HYDRATED_ACTIVE\", \"registryStatus\": \"REGISTERED_ACTIVE\", \"ringDuration\": 3500, \"queuedEvents\": 0}"),
            createContextAfter("{\"machineStatus\": \"HYDRATED_ACTIVE\", \"registryStatus\": \"REGISTERED_ACTIVE\", \"connectedAt\": \"" + LocalDateTime.now() + "\", \"callQuality\": \"HD\", \"queuedEvents\": 0}")
        );
        
        Thread.sleep(100);
        
        // Step 3: CONNECTED -> COMPLETED (Registry processes hangup and decides to dehydrate)
        insertRegistrySnapshot(
            "call-registry-001", "CallEntity", runId, 3,
            "CONNECTED", "COMPLETED", "HANGUP", 40,
            className, fullPath,
            "REGISTERED_ACTIVE", "REGISTERED_INACTIVE",  // Registry deactivates machine
            true, false,  // Machine gets dehydrated after completion
            createEventPayload("{\"eventSource\": \"REGISTRY\", \"hangupReason\": \"NORMAL_CLEARING\", \"duration\": 45000, \"registryDecision\": \"COMPLETE_AND_DEHYDRATE\"}"),
            createContextBefore("{\"machineStatus\": \"HYDRATED_ACTIVE\", \"registryStatus\": \"REGISTERED_ACTIVE\", \"callDuration\": 45000, \"queuedEvents\": 0}"),
            createContextAfter("{\"machineStatus\": \"DEHYDRATED_COMPLETED\", \"registryStatus\": \"REGISTERED_INACTIVE\", \"finalOutcome\": \"SUCCESS\", \"totalDuration\": 45000, \"cost\": 0.20, \"queuedEvents\": 0}")
        );
    }
    
    /**
     * Generate SMS flow with registry completion scenario
     */
    private static void generateRegistryAwareSmsFlow() throws Exception {
        String runId = generateRunId("registry-sms");
        String className = "RegistrySmsHandler";
        String fullPath = "com.telcobright.statemachine.registry.handlers.RegistrySmsHandler";
        
        System.out.println("📱 Generating registry-aware SMS flow: " + runId);
        
        // Step 1: QUEUED -> SENDING (Registry hydrates machine for SMS send)
        insertRegistrySnapshot(
            "sms-registry-001", "SmsEntity", runId, 1,
            "QUEUED", "SENDING", "SEND_REQUEST", 25,
            className, fullPath,
            "REGISTERED_INACTIVE", "REGISTERED_ACTIVE",
            false, true,
            createEventPayload("{\"eventSource\": \"REGISTRY\", \"messageId\": \"sms-reg-001\", \"fromNumber\": \"+1-555-SMS\", \"toNumber\": \"+1-555-MOBILE\", \"message\": \"Registry-mediated SMS\", \"registryDecision\": \"HYDRATE_AND_SEND\"}"),
            createContextBefore("{\"machineStatus\": \"DEHYDRATED\", \"registryStatus\": \"REGISTERED_INACTIVE\", \"queuePosition\": 5, \"retryCount\": 0}"),
            createContextAfter("{\"machineStatus\": \"HYDRATED_ACTIVE\", \"registryStatus\": \"REGISTERED_ACTIVE\", \"sendStartTime\": \"" + LocalDateTime.now() + "\", \"carrier\": \"T-Mobile\", \"retryCount\": 0}")
        );
        
        Thread.sleep(100);
        
        // Step 2: SENDING -> DELIVERED (Registry processes delivery report and completes)
        insertRegistrySnapshot(
            "sms-registry-001", "SmsEntity", runId, 2,
            "SENDING", "DELIVERED", "DELIVERY_REPORT", 180,
            className, fullPath,
            "REGISTERED_ACTIVE", "REGISTERED_INACTIVE",  // Registry completes and deregisters
            true, false,  // Machine gets dehydrated after delivery
            createEventPayload("{\"eventSource\": \"REGISTRY\", \"deliveryStatus\": \"DELIVERED\", \"carrierId\": \"TMO-98765\", \"deliveryLatency\": 1800, \"registryDecision\": \"COMPLETE_AND_DEREGISTER\"}"),
            createContextBefore("{\"machineStatus\": \"HYDRATED_ACTIVE\", \"registryStatus\": \"REGISTERED_ACTIVE\", \"awaitingDelivery\": true, \"sendTime\": \"" + LocalDateTime.now().minusSeconds(2) + "\"}"),
            createContextAfter("{\"machineStatus\": \"DEHYDRATED_COMPLETED\", \"registryStatus\": \"REGISTERED_INACTIVE\", \"deliveredTime\": \"" + LocalDateTime.now() + "\", \"totalDeliveryTime\": 1800, \"finalStatus\": \"DELIVERED\", \"cost\": 0.03}")
        );
    }
    
    /**
     * Generate machine lifecycle showing registry refusing to hydrate completed machine
     */
    private static void generateMachineLifecycleFlow() throws Exception {
        String runId = generateRunId("lifecycle-demo");
        String className = "MachineLifecycleDemo";
        String fullPath = "com.telcobright.statemachine.registry.lifecycle.MachineLifecycleDemo";
        
        System.out.println("🔄 Generating machine lifecycle demo: " + runId);
        
        // Step 1: IDLE -> PROCESSING (Registry hydrates for processing)
        insertRegistrySnapshot(
            "lifecycle-001", "ProcessEntity", runId, 1,
            "IDLE", "PROCESSING", "START_PROCESS", 30,
            className, fullPath,
            "REGISTERED_INACTIVE", "REGISTERED_ACTIVE",
            false, true,
            createEventPayload("{\"eventSource\": \"REGISTRY\", \"processId\": \"proc-001\", \"taskType\": \"DATA_PROCESSING\", \"registryDecision\": \"HYDRATE_FOR_PROCESSING\"}"),
            createContextBefore("{\"machineStatus\": \"DEHYDRATED\", \"registryStatus\": \"REGISTERED_INACTIVE\", \"pendingTasks\": 1, \"lastProcessed\": null}"),
            createContextAfter("{\"machineStatus\": \"HYDRATED_ACTIVE\", \"registryStatus\": \"REGISTERED_ACTIVE\", \"currentTask\": \"proc-001\", \"startTime\": \"" + LocalDateTime.now() + "\", \"pendingTasks\": 0}")
        );
        
        Thread.sleep(100);
        
        // Step 2: PROCESSING -> COMPLETED (Registry completes and deregisters)
        insertRegistrySnapshot(
            "lifecycle-001", "ProcessEntity", runId, 2,
            "PROCESSING", "COMPLETED", "PROCESS_FINISHED", 2500,
            className, fullPath,
            "REGISTERED_ACTIVE", "REGISTERED_INACTIVE",
            true, false,
            createEventPayload("{\"eventSource\": \"REGISTRY\", \"processId\": \"proc-001\", \"result\": \"SUCCESS\", \"recordsProcessed\": 10000, \"registryDecision\": \"COMPLETE_AND_DEREGISTER\"}"),
            createContextBefore("{\"machineStatus\": \"HYDRATED_ACTIVE\", \"registryStatus\": \"REGISTERED_ACTIVE\", \"processingTime\": 2500, \"recordsProcessed\": 10000}"),
            createContextAfter("{\"machineStatus\": \"DEHYDRATED_COMPLETED\", \"registryStatus\": \"REGISTERED_INACTIVE\", \"completedAt\": \"" + LocalDateTime.now() + "\", \"finalResult\": \"SUCCESS\", \"totalRecords\": 10000}")
        );
        
        Thread.sleep(100);
        
        // Step 3: Attempt to send event to completed machine (Registry refuses)
        insertRegistrySnapshot(
            "lifecycle-001", "ProcessEntity", runId, 3,
            "COMPLETED", "COMPLETED", "RETRY_PROCESS",  0,  // No duration - event rejected
            className, fullPath,
            "NOT_REGISTERED", "NOT_REGISTERED",  // Machine not in registry
            false, false,  // Machine stays dehydrated
            createEventPayload("{\"eventSource\": \"REGISTRY\", \"processId\": \"proc-002\", \"registryDecision\": \"REJECT_MACHINE_COMPLETED\", \"errorCode\": \"MACHINE_ALREADY_COMPLETED\"}"),
            createContextBefore("{\"machineStatus\": \"DEHYDRATED_COMPLETED\", \"registryStatus\": \"NOT_REGISTERED\", \"eventQueue\": [\"RETRY_PROCESS\"], \"lastError\": null}"),
            createContextAfter("{\"machineStatus\": \"DEHYDRATED_COMPLETED\", \"registryStatus\": \"NOT_REGISTERED\", \"eventQueue\": [], \"lastError\": \"Machine already completed, cannot process new events\"}")
        );
    }
    
    private static void insertRegistrySnapshot(String machineId, String machineType, String runId, int version,
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
                 registryStatusAfter, hydratedAfter ? "true" : "false",  // Current status
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
    
    private static String createContextBefore(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes());
    }
    
    private static String createContextAfter(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes());
    }
    
    private static String generateRunId(String prefix) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        String timestamp = LocalDateTime.now().format(formatter);
        String randomSuffix = String.valueOf(System.nanoTime() % 100000);
        return prefix + "-" + timestamp + "-" + randomSuffix;
    }
}