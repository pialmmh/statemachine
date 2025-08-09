package com.telcobright.statemachine.test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Test runner to generate detailed monitoring data for state machines
 * This simulates what your actual state machines should produce when debug=true
 */
public class MonitoringTestRunner {
    
    public static void main(String[] args) throws Exception {
        System.out.println("🚀 Generating detailed monitoring data for Call and SMS machines...");
        
        // Generate call machine data
        generateCallMachineData();
        
        // Generate SMS machine data  
        generateSmsMachineData();
        
        System.out.println("✅ Complete! Refresh the monitoring dashboard to see detailed data.");
    }
    
    private static void generateCallMachineData() throws Exception {
        String runId = generateRunId("call-machine");
        String className = "CallMachineTestRunner";
        String fullPath = "com.telcobright.statemachineexamples.callmachine.CallMachineTestRunner";
        
        System.out.println("📞 Generating call machine run: " + runId);
        
        // Step 1: IDLE -> RINGING (Incoming Call)
        insertSnapshot(
            "call-demo-001", "CallEntity", runId, 1,
            "IDLE", "RINGING", "INCOMING_CALL", 25,
            className, fullPath,
            createEventPayload("{\"fromNumber\": \"+1-555-0123\", \"toNumber\": \"+1-555-0456\", \"callType\": \"VOICE\", \"priority\": \"NORMAL\"}"),
            createContextBefore("{\"status\": \"IDLE\", \"activeCallCount\": 0, \"lastActivity\": null, \"availableLines\": 10}"),
            createContextAfter("{\"status\": \"RINGING\", \"activeCallCount\": 1, \"currentCall\": {\"id\": \"call-demo-001\", \"from\": \"+1-555-0123\", \"to\": \"+1-555-0456\"}, \"ringStartTime\": \"" + LocalDateTime.now() + "\", \"availableLines\": 9}")
        );
        
        Thread.sleep(100);
        
        // Step 2: RINGING -> CONNECTED (Answer)
        insertSnapshot(
            "call-demo-001", "CallEntity", runId, 2,
            "RINGING", "CONNECTED", "ANSWER", 150,
            className, fullPath,
            createEventPayload("{\"answeredBy\": \"USER\", \"answerTime\": \"" + LocalDateTime.now() + "\", \"deviceType\": \"MOBILE\"}"),
            createContextBefore("{\"status\": \"RINGING\", \"activeCallCount\": 1, \"currentCall\": {\"id\": \"call-demo-001\", \"from\": \"+1-555-0123\", \"to\": \"+1-555-0456\"}, \"ringDuration\": 2500, \"availableLines\": 9}"),
            createContextAfter("{\"status\": \"CONNECTED\", \"activeCallCount\": 1, \"currentCall\": {\"id\": \"call-demo-001\", \"from\": \"+1-555-0123\", \"to\": \"+1-555-0456\", \"connectedAt\": \"" + LocalDateTime.now() + "\", \"quality\": \"HD\"}, \"availableLines\": 9}")
        );
        
        Thread.sleep(100);
        
        // Step 3: CONNECTED -> ON_HOLD (Hold)
        insertSnapshot(
            "call-demo-001", "CallEntity", runId, 3,
            "CONNECTED", "ON_HOLD", "HOLD", 45,
            className, fullPath,
            createEventPayload("{\"holdReason\": \"TRANSFER_PREPARATION\", \"holdMusic\": true, \"initiatedBy\": \"CALLER\"}"),
            createContextBefore("{\"status\": \"CONNECTED\", \"activeCallCount\": 1, \"currentCall\": {\"id\": \"call-demo-001\", \"duration\": 45000, \"quality\": \"HD\"}, \"availableLines\": 9}"),
            createContextAfter("{\"status\": \"ON_HOLD\", \"activeCallCount\": 1, \"currentCall\": {\"id\": \"call-demo-001\", \"duration\": 45000, \"onHoldSince\": \"" + LocalDateTime.now() + "\", \"holdMusic\": true}, \"availableLines\": 9}")
        );
        
        Thread.sleep(100);
        
        // Step 4: ON_HOLD -> CONNECTED (Unhold)
        insertSnapshot(
            "call-demo-001", "CallEntity", runId, 4,
            "ON_HOLD", "CONNECTED", "UNHOLD", 30,
            className, fullPath,
            createEventPayload("{\"resumedBy\": \"CALLER\", \"holdDuration\": 5000}"),
            createContextBefore("{\"status\": \"ON_HOLD\", \"activeCallCount\": 1, \"currentCall\": {\"id\": \"call-demo-001\", \"holdDuration\": 5000, \"holdMusic\": true}, \"availableLines\": 9}"),
            createContextAfter("{\"status\": \"CONNECTED\", \"activeCallCount\": 1, \"currentCall\": {\"id\": \"call-demo-001\", \"duration\": 50000, \"quality\": \"HD\", \"resumed\": true}, \"availableLines\": 9}")
        );
        
        Thread.sleep(100);
        
        // Step 5: CONNECTED -> COMPLETED (Hangup)
        insertSnapshot(
            "call-demo-001", "CallEntity", runId, 5,
            "CONNECTED", "COMPLETED", "HANGUP", 35,
            className, fullPath,
            createEventPayload("{\"hangupReason\": \"NORMAL_CLEARING\", \"initiatedBy\": \"CALLER\", \"totalDuration\": 65000, \"billableMinutes\": 2}"),
            createContextBefore("{\"status\": \"CONNECTED\", \"activeCallCount\": 1, \"currentCall\": {\"id\": \"call-demo-001\", \"duration\": 65000, \"quality\": \"HD\"}, \"availableLines\": 9}"),
            createContextAfter("{\"status\": \"COMPLETED\", \"activeCallCount\": 0, \"completedCall\": {\"id\": \"call-demo-001\", \"totalDuration\": 65000, \"outcome\": \"SUCCESSFUL\", \"cost\": 0.15}, \"availableLines\": 10}")
        );
    }
    
    private static void generateSmsMachineData() throws Exception {
        String runId = generateRunId("sms-machine");
        String className = "SmsMachineTestRunner";
        String fullPath = "com.telcobright.statemachineexamples.smsmachine.SmsMachineTestRunner";
        
        System.out.println("📱 Generating SMS machine run: " + runId);
        
        // Step 1: QUEUED -> SENDING (Send Request)
        insertSnapshot(
            "sms-demo-001", "SmsEntity", runId, 1,
            "QUEUED", "SENDING", "SEND_REQUEST", 20,
            className, fullPath,
            createEventPayload("{\"messageId\": \"msg-789\", \"fromNumber\": \"+1-555-SMS1\", \"toNumber\": \"+1-555-SMS2\", \"message\": \"Hello from monitoring system!\", \"messageType\": \"TEXT\", \"priority\": \"NORMAL\"}"),
            createContextBefore("{\"status\": \"QUEUED\", \"queuePosition\": 3, \"estimatedSendTime\": \"" + LocalDateTime.now().plusSeconds(10) + "\", \"retryCount\": 0}"),
            createContextAfter("{\"status\": \"SENDING\", \"sendStartTime\": \"" + LocalDateTime.now() + "\", \"carrier\": \"Verizon\", \"messageLength\": 35, \"retryCount\": 0}")
        );
        
        Thread.sleep(100);
        
        // Step 2: SENDING -> SENT (Carrier Accepted)
        insertSnapshot(
            "sms-demo-001", "SmsEntity", runId, 2,
            "SENDING", "SENT", "CARRIER_ACCEPTED", 180,
            className, fullPath,
            createEventPayload("{\"carrierId\": \"VZW-12345\", \"messageId\": \"msg-789\", \"acceptedAt\": \"" + LocalDateTime.now() + "\", \"networkType\": \"4G\"}"),
            createContextBefore("{\"status\": \"SENDING\", \"sendStartTime\": \"" + LocalDateTime.now().minusSeconds(1) + "\", \"carrier\": \"Verizon\", \"messageLength\": 35, \"retryCount\": 0}"),
            createContextAfter("{\"status\": \"SENT\", \"sentTime\": \"" + LocalDateTime.now() + "\", \"carrier\": \"Verizon\", \"carrierId\": \"VZW-12345\", \"deliveryExpected\": \"" + LocalDateTime.now().plusSeconds(30) + "\"}")
        );
        
        Thread.sleep(100);
        
        // Step 3: SENT -> DELIVERED (Delivery Confirmation)
        insertSnapshot(
            "sms-demo-001", "SmsEntity", runId, 3,
            "SENT", "DELIVERED", "DELIVERY_REPORT", 220,
            className, fullPath,
            createEventPayload("{\"deliveryStatus\": \"DELIVERED\", \"deliveredAt\": \"" + LocalDateTime.now() + "\", \"recipientDevice\": \"iPhone 14\", \"deliveryLatency\": 2200}"),
            createContextBefore("{\"status\": \"SENT\", \"sentTime\": \"" + LocalDateTime.now().minusSeconds(2) + "\", \"carrier\": \"Verizon\", \"carrierId\": \"VZW-12345\", \"awaitingDelivery\": true}"),
            createContextAfter("{\"status\": \"DELIVERED\", \"deliveredTime\": \"" + LocalDateTime.now() + "\", \"totalDeliveryTime\": 2200, \"finalStatus\": \"SUCCESS\", \"cost\": 0.02}")
        );
    }
    
    private static void insertSnapshot(String machineId, String machineType, String runId, int version,
                                     String stateBefore, String stateAfter, String eventType, long duration,
                                     String className, String fullPath,
                                     String eventPayload, String contextBefore, String contextAfter) throws Exception {
        
        String sql = String.format("""
            INSERT INTO state_machine_snapshots (
                machine_id, machine_type, run_id, version,
                state_before, state_after, event_type, transition_duration,
                registry_status, machine_online_status,
                triggering_class_name, triggering_class_full_path,
                event_payload_json, context_before_json, context_after_json
            ) VALUES (
                '%s', '%s', '%s', %d,
                '%s', '%s', '%s', %d,
                'ACTIVE', true,
                '%s', '%s',
                '%s', '%s', '%s'
            )
            """, machineId, machineType, runId, version,
                 stateBefore, stateAfter, eventType, duration,
                 className, fullPath,
                 eventPayload, contextBefore, contextAfter);
        
        // Execute via docker command
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