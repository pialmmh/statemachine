package com.telcobright.statemachine.test;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.events.GenericStateMachineEvent;
import com.telcobright.statemachine.monitoring.DockerGrafanaIntegration;

import java.time.LocalDateTime;

/**
 * Docker Grafana Demo - Complete integration test
 * 
 * This demo shows how to:
 * 1. Start Docker containers with Grafana + PostgreSQL
 * 2. Create state machines that automatically send data to Grafana
 * 3. View the complete state machine history in Grafana dashboards
 * 
 * Prerequisites:
 * - Docker and docker-compose installed
 * - PostgreSQL JDBC driver in classpath
 * 
 * Usage:
 * 1. cd /home/mustafa/telcobright-projects/statemachine/docker
 * 2. docker-compose up -d
 * 3. Run this demo
 * 4. Open http://localhost:3000 to view Grafana dashboards
 */
public class DockerGrafanaDemo {
    
    public static void main(String[] args) {
        System.out.println("🐳 Docker Grafana State Machine Demo");
        System.out.println("====================================");
        
        try {
            // Step 1: Test Docker integration
            testDockerSetup();
            
            // Step 2: Run call machine demo with Grafana monitoring
            runCallMachineWithGrafana();
            
            // Step 3: Run SMS machine demo with Grafana monitoring
            runSmsMachineWithGrafana();
            
            // Step 4: Show how to access the data
            showGrafanaAccess();
            
        } catch (Exception e) {
            System.err.println("❌ Demo failed: " + e.getMessage());
            e.printStackTrace();
            
            System.err.println("\n💡 Troubleshooting:");
            System.err.println("   1. Ensure Docker containers are running:");
            System.err.println("      cd /home/mustafa/telcobright-projects/statemachine/docker");
            System.err.println("      docker-compose up -d");
            System.err.println("   2. Check container logs:");
            System.err.println("      docker-compose logs -f");
            System.err.println("   3. Verify PostgreSQL JDBC driver is in classpath");
        } finally {
            // Cleanup
            DockerGrafanaIntegration.shutdown();
        }
    }
    
    /**
     * Test Docker setup and connectivity
     */
    private static void testDockerSetup() {
        System.out.println("\n🧪 === STEP 1: Testing Docker Integration ===");
        
        try {
            // Test the connection
            DockerGrafanaIntegration.quickSetupTest();
            
            // Send a test snapshot
            DockerGrafanaIntegration.sendTestSnapshot();
            
            System.out.println("✅ Docker integration test completed successfully!");
            
        } catch (Exception e) {
            System.err.println("❌ Docker integration test failed: " + e.getMessage());
            throw new RuntimeException("Cannot proceed without Docker setup", e);
        }
    }
    
    /**
     * Run call machine demo with automatic Grafana monitoring
     */
    private static void runCallMachineWithGrafana() throws Exception {
        System.out.println("\n📞 === STEP 2: Call Machine with Grafana Monitoring ===");
        
        // Create call entity and context
        CallEntity callEntity = new CallEntity();
        callEntity.setCallId("GRAFANA-CALL-001");
        callEntity.setFromNumber("+1-800-GRAFANA");
        callEntity.setToNumber("+1-555-DOCKER");
        callEntity.setCurrentState("IDLE");
        
        CallContext context = new CallContext("GRAFANA-CALL-001", "+1-800-GRAFANA", "+1-555-DOCKER");
        context.setCallType("DEMO_CALL");
        context.setRecordingEnabled(true);
        
        // Create monitored state machine (automatically sends to Docker Grafana)
        GenericStateMachine<CallEntity, CallContext> machine = DockerGrafanaIntegration
            .<CallEntity, CallContext>createMonitoredMachine("grafana-call-demo", CallEntity.class)
            .initialState("IDLE")
            .finalState("COMPLETED")
            
            .state("IDLE")
                .on("INCOMING_CALL").to("RINGING")
            .done()
            
            .state("RINGING")
                .on("SESSION_PROGRESS").to("RINGING")
                .on("ANSWER").to("CONNECTED")
                .on("REJECT").to("REJECTED")
                .on("TIMEOUT").to("MISSED")
            .done()
            
            .state("CONNECTED")
                .on("DTMF").to("CONNECTED")
                .on("HOLD").to("ON_HOLD") 
                .on("TRANSFER").to("TRANSFERRING")
                .on("HANGUP").to("COMPLETED")
            .done()
            
            .state("ON_HOLD")
                .on("UNHOLD").to("CONNECTED")
                .on("HANGUP").to("COMPLETED")
            .done()
            
            .state("TRANSFERRING")
                .on("TRANSFER_SUCCESS").to("CONNECTED")
                .on("TRANSFER_FAILED").to("CONNECTED")
                .on("HANGUP").to("COMPLETED")
            .done()
            
            .state("REJECTED").finalState().done()
            .state("MISSED").finalState().done()
            .state("COMPLETED").finalState().done()
            
            .build();
        
        machine.setPersistingEntity(callEntity);
        machine.setContext(context);
        
        // Start the monitored call flow
        System.out.println("\n📊 Starting Grafana-monitored call session...");
        System.out.println("🔍 Run ID: " + machine.getRunId());
        machine.start();
        
        // Simulate comprehensive call flow
        simulateCallFlowForGrafana(machine, context);
        
        machine.stop();
        
        System.out.println("✅ Call machine demo completed!");
        System.out.println("📊 Data has been sent to Grafana automatically");
    }
    
    /**
     * Run SMS machine demo with Grafana monitoring
     */
    private static void runSmsMachineWithGrafana() throws Exception {
        System.out.println("\n📱 === STEP 3: SMS Machine with Grafana Monitoring ===");
        
        // Create SMS entity and context
        SmsEntity smsEntity = new SmsEntity();
        smsEntity.setSmsId("GRAFANA-SMS-001");
        smsEntity.setFromNumber("+1-800-SMS-API");
        smsEntity.setToNumber("+1-555-MOBILE");
        smsEntity.setCurrentState("QUEUED");
        
        SmsContext context = new SmsContext("GRAFANA-SMS-001", "+1-800-SMS-API", "+1-555-MOBILE", "Hello from Grafana demo!");
        
        // Create monitored SMS machine
        GenericStateMachine<SmsEntity, SmsContext> machine = DockerGrafanaIntegration
            .<SmsEntity, SmsContext>createMonitoredMachine("grafana-sms-demo", SmsEntity.class)
            .initialState("QUEUED")
            .finalState("DELIVERED")
            
            .state("QUEUED")
                .on("SEND_REQUEST").to("SENDING")
                .on("CANCEL").to("CANCELLED")
            .done()
            
            .state("SENDING")
                .on("DELIVERY_REPORT").to("DELIVERED")
                .on("FAILURE_REPORT").to("FAILED")
                .on("RETRY").to("SENDING")
            .done()
            
            .state("DELIVERED").finalState().done()
            .state("FAILED").finalState().done()
            .state("CANCELLED").finalState().done()
            
            .build();
        
        machine.setPersistingEntity(smsEntity);
        machine.setContext(context);
        
        // Start monitored SMS flow
        System.out.println("\n📊 Starting Grafana-monitored SMS session...");
        System.out.println("🔍 Run ID: " + machine.getRunId());
        machine.start();
        
        // Simulate SMS flow
        simulateSmsFlowForGrafana(machine, context);
        
        machine.stop();
        
        System.out.println("✅ SMS machine demo completed!");
        System.out.println("📊 Data has been sent to Grafana automatically");
    }
    
    /**
     * Show how to access Grafana dashboards
     */
    private static void showGrafanaAccess() {
        System.out.println("\n🎯 === STEP 4: Access Your Data in Grafana ===");
        
        DockerGrafanaIntegration.printConnectionInfo();
        
        System.out.println("\n📊 Grafana Dashboards Available:");
        System.out.println("   • State Machine Overview: http://localhost:3000/d/statemachine-overview");
        System.out.println("   • Explore Data: http://localhost:3000/explore");
        System.out.println("   • Create Custom Dashboards: http://localhost:3000/dashboard/new");
        
        System.out.println("\n🔍 Sample Grafana Queries to Try:");
        System.out.println("   • SELECT * FROM v_machine_history LIMIT 20");
        System.out.println("   • SELECT * FROM v_call_history WHERE call_type = 'DEMO_CALL'");
        System.out.println("   • SELECT machine_id, COUNT(*) FROM state_machine_snapshots GROUP BY machine_id");
        System.out.println("   • SELECT * FROM mv_machine_metrics ORDER BY hour DESC");
        
        System.out.println("\n🎊 Demo Complete! Your state machine history is now visible in Grafana.");
    }
    
    /**
     * Simulate comprehensive call flow for Grafana visualization
     */
    private static void simulateCallFlowForGrafana(GenericStateMachine<CallEntity, CallContext> machine, CallContext context) 
            throws InterruptedException {
        
        // 1. Incoming call
        System.out.println("📞 1. Processing incoming call...");
        machine.fire(new IncomingCallEvent("+1-800-GRAFANA"));
        context.setCallStartTime(System.currentTimeMillis());
        context.addSessionEvent("Enhanced incoming call for Grafana demo");
        Thread.sleep(400);
        
        // 2. Session progress updates
        System.out.println("📡 2. Session progress updates...");
        machine.fire(new SessionProgressEvent("100", "Trying"));
        context.addSessionEvent("Session progress: Trying");
        Thread.sleep(250);
        
        machine.fire(new SessionProgressEvent("180", "Ringing"));
        context.incrementRingCount();
        context.addSessionEvent("Session progress: Ringing");
        Thread.sleep(600);
        
        machine.fire(new SessionProgressEvent("183", "Session Progress with Media"));
        context.incrementRingCount();
        context.addSessionEvent("Session progress: Media negotiation");
        Thread.sleep(350);
        
        // 3. Answer call
        System.out.println("✅ 3. Call answered...");
        machine.fire(new AnswerEvent());
        context.setCallStatus("CONNECTED");
        context.setConnectedTime(System.currentTimeMillis());
        context.addSessionEvent("Call answered - ready for interaction");
        Thread.sleep(800);
        
        // 4. DTMF interactions
        System.out.println("🔢 4. DTMF interactions...");
        machine.fire(new DTMFEvent("1"));
        context.addSessionEvent("DTMF: Menu option 1 selected");
        Thread.sleep(400);
        
        machine.fire(new DTMFEvent("*"));
        context.addSessionEvent("DTMF: Return to main menu");
        Thread.sleep(300);
        
        machine.fire(new DTMFEvent("9"));
        context.addSessionEvent("DTMF: Transfer request");
        Thread.sleep(200);
        
        // 5. Call hold sequence
        System.out.println("⏸️  5. Call hold sequence...");
        machine.fire("HOLD");
        context.addSessionEvent("Call placed on hold for transfer");
        Thread.sleep(1000);
        
        machine.fire("UNHOLD");
        context.addSessionEvent("Call resumed from hold");
        Thread.sleep(500);
        
        // 6. Transfer attempt
        System.out.println("📞 6. Transfer attempt...");
        machine.fire("TRANSFER");
        context.addSessionEvent("Transfer initiated to specialist");
        Thread.sleep(800);
        
        machine.fire("TRANSFER_SUCCESS");
        context.addSessionEvent("Transfer completed successfully");
        Thread.sleep(400);
        
        // 7. Final hangup
        System.out.println("📴 7. Call completion...");
        machine.fire(new HangupEvent("NORMAL_CLEARING", "CALLER"));
        context.setCallStatus("COMPLETED");
        context.setDisconnectReason("NORMAL_CLEARING");
        context.addSessionEvent("Call completed normally - customer satisfied");
        
        // Show call summary
        showCallSummary(context, "Grafana Call Demo");
    }
    
    /**
     * Simulate SMS flow for Grafana visualization
     */
    private static void simulateSmsFlowForGrafana(GenericStateMachine<SmsEntity, SmsContext> machine, SmsContext context) 
            throws InterruptedException {
        
        // 1. Send request
        System.out.println("📱 1. Processing SMS send request...");
        machine.fire(new SendSmsEvent(context.getMessage()));
        context.setSentTime(System.currentTimeMillis());
        context.addEvent("SMS send request processed");
        Thread.sleep(300);
        
        // 2. Delivery report
        System.out.println("📩 2. Delivery confirmation...");
        machine.fire(new DeliveryReportEvent("DELIVERED"));
        context.setDeliveredTime(System.currentTimeMillis());
        context.addEvent("SMS delivered successfully");
        
        // Show SMS summary
        showSmsSummary(context, "Grafana SMS Demo");
    }
    
    /**
     * Display call summary
     */
    private static void showCallSummary(CallContext context, String title) {
        System.out.println("\n📊 === " + title + " Summary ===");
        System.out.println("📞 Call ID: " + context.getCallId());
        System.out.println("📋 " + context.getFromNumber() + " → " + context.getToNumber());
        System.out.println("⏱️  Total Duration: " + context.getCallDuration().toSeconds() + "s");
        System.out.println("🔔 Ring Count: " + context.getRingCount());
        System.out.println("📞 Call Type: " + context.getCallType());
        System.out.println("🎙️  Recording: " + (context.isRecordingEnabled() ? "Enabled" : "Disabled"));
        System.out.println("💡 Final Status: " + context.getCallStatus());
        
        if (context.getDisconnectReason() != null) {
            System.out.println("🔚 Disconnect Reason: " + context.getDisconnectReason());
        }
        
        System.out.println("\n📝 Session Events (" + context.getSessionEvents().size() + " total):");
        for (int i = 0; i < context.getSessionEvents().size(); i++) {
            System.out.printf("   %d. %s%n", i + 1, context.getSessionEvents().get(i));
        }
        
        System.out.println("=" + "=".repeat(50));
    }
    
    /**
     * Display SMS summary
     */
    private static void showSmsSummary(SmsContext context, String title) {
        System.out.println("\n📊 === " + title + " Summary ===");
        System.out.println("📱 SMS ID: " + context.getSmsId());
        System.out.println("📋 " + context.getFromNumber() + " → " + context.getToNumber());
        System.out.println("💬 Message: " + context.getMessage());
        System.out.println("📊 Status: " + context.getStatus());
        
        if (context.getSentTime() > 0 && context.getDeliveredTime() > 0) {
            long deliveryTime = context.getDeliveredTime() - context.getSentTime();
            System.out.println("⚡ Delivery Time: " + deliveryTime + "ms");
        }
        
        System.out.println("\n📝 SMS Events:");
        for (int i = 0; i < context.getEvents().size(); i++) {
            System.out.printf("   %d. %s%n", i + 1, context.getEvents().get(i));
        }
        
        System.out.println("=" + "=".repeat(50));
    }
    
    // Event classes for the demo
    public static class IncomingCallEvent extends GenericStateMachineEvent {
        private final String fromNumber;
        public IncomingCallEvent(String fromNumber) {
            super("INCOMING_CALL");
            this.fromNumber = fromNumber;
        }
        public String getFromNumber() { return fromNumber; }
    }
    
    public static class SessionProgressEvent extends GenericStateMachineEvent {
        private final String responseCode;
        private final String description;
        public SessionProgressEvent(String responseCode, String description) {
            super("SESSION_PROGRESS");
            this.responseCode = responseCode;
            this.description = description;
        }
        public String getResponseCode() { return responseCode; }
        public String getDescription() { return description; }
    }
    
    public static class AnswerEvent extends GenericStateMachineEvent {
        public AnswerEvent() { super("ANSWER"); }
    }
    
    public static class DTMFEvent extends GenericStateMachineEvent {
        private final String digit;
        public DTMFEvent(String digit) {
            super("DTMF");
            this.digit = digit;
        }
        public String getDigit() { return digit; }
    }
    
    public static class HangupEvent extends GenericStateMachineEvent {
        private final String reason;
        private final String initiatedBy;
        public HangupEvent(String reason, String initiatedBy) {
            super("HANGUP");
            this.reason = reason;
            this.initiatedBy = initiatedBy;
        }
        public String getReason() { return reason; }
        public String getInitiatedBy() { return initiatedBy; }
    }
    
    public static class SendSmsEvent extends GenericStateMachineEvent {
        private final String message;
        public SendSmsEvent(String message) {
            super("SEND_REQUEST");
            this.message = message;
        }
        public String getMessage() { return message; }
    }
    
    public static class DeliveryReportEvent extends GenericStateMachineEvent {
        private final String status;
        public DeliveryReportEvent(String status) {
            super("DELIVERY_REPORT");
            this.status = status;
        }
        public String getStatus() { return status; }
    }
    
    // Entity classes
    public static class CallEntity implements StateMachineContextEntity<String> {
        private String callId;
        private String fromNumber;
        private String toNumber;
        private String currentState;
        private boolean complete = false;
        private LocalDateTime lastStateChange = LocalDateTime.now();
        
        @Override public boolean isComplete() { return complete; }
        @Override public void setComplete(boolean complete) { this.complete = complete; }
        public String getShardingKey() { return callId; }
        
        // Getters and setters
        public String getCallId() { return callId; }
        public void setCallId(String callId) { this.callId = callId; }
        public String getFromNumber() { return fromNumber; }
        public void setFromNumber(String fromNumber) { this.fromNumber = fromNumber; }
        public String getToNumber() { return toNumber; }
        public void setToNumber(String toNumber) { this.toNumber = toNumber; }
        
        @Override
        public String getCurrentState() { return currentState; }
        
        @Override
        public void setCurrentState(String currentState) { 
            this.currentState = currentState;
            this.lastStateChange = LocalDateTime.now();
        }
        
        @Override
        public LocalDateTime getLastStateChange() {
            return lastStateChange;
        }
        
        @Override
        public void setLastStateChange(LocalDateTime lastStateChange) {
            this.lastStateChange = lastStateChange;
        }
    }
    
    public static class SmsEntity implements StateMachineContextEntity<String> {
        private String smsId;
        private String fromNumber;
        private String toNumber;
        private String currentState;
        private boolean complete = false;
        private LocalDateTime lastStateChange = LocalDateTime.now();
        
        @Override public boolean isComplete() { return complete; }
        @Override public void setComplete(boolean complete) { this.complete = complete; }
        public String getShardingKey() { return smsId; }
        
        // Getters and setters
        public String getSmsId() { return smsId; }
        public void setSmsId(String smsId) { this.smsId = smsId; }
        public String getFromNumber() { return fromNumber; }
        public void setFromNumber(String fromNumber) { this.fromNumber = fromNumber; }
        public String getToNumber() { return toNumber; }
        public void setToNumber(String toNumber) { this.toNumber = toNumber; }
        
        @Override
        public String getCurrentState() { return currentState; }
        
        @Override
        public void setCurrentState(String currentState) { 
            this.currentState = currentState;
            this.lastStateChange = LocalDateTime.now();
        }
        
        @Override
        public LocalDateTime getLastStateChange() {
            return lastStateChange;
        }
        
        @Override
        public void setLastStateChange(LocalDateTime lastStateChange) {
            this.lastStateChange = lastStateChange;
        }
    }
    
    // Context classes  
    public static class CallContext {
        private String callId;
        private String fromNumber;
        private String toNumber;
        private String callType = "REGULAR";
        private String callStatus = "IDLE";
        private long callStartTime;
        private Long connectedTime;
        private String disconnectReason;
        private int ringCount = 0;
        private boolean recordingEnabled = false;
        private java.util.List<String> sessionEvents = new java.util.ArrayList<>();
        
        public CallContext(String callId, String fromNumber, String toNumber) {
            this.callId = callId;
            this.fromNumber = fromNumber;
            this.toNumber = toNumber;
        }
        
        public void addSessionEvent(String event) { sessionEvents.add(event); }
        public void incrementRingCount() { ringCount++; }
        
        public java.time.Duration getCallDuration() {
            if (callStartTime == 0) return java.time.Duration.ZERO;
            return java.time.Duration.ofMillis(System.currentTimeMillis() - callStartTime);
        }
        
        // Getters and setters
        public String getCallId() { return callId; }
        public String getFromNumber() { return fromNumber; }
        public String getToNumber() { return toNumber; }
        public String getCallType() { return callType; }
        public void setCallType(String callType) { this.callType = callType; }
        public String getCallStatus() { return callStatus; }
        public void setCallStatus(String callStatus) { this.callStatus = callStatus; }
        public long getCallStartTime() { return callStartTime; }
        public void setCallStartTime(long callStartTime) { this.callStartTime = callStartTime; }
        public Long getConnectedTime() { return connectedTime; }
        public void setConnectedTime(Long connectedTime) { this.connectedTime = connectedTime; }
        public String getDisconnectReason() { return disconnectReason; }
        public void setDisconnectReason(String disconnectReason) { this.disconnectReason = disconnectReason; }
        public int getRingCount() { return ringCount; }
        public boolean isRecordingEnabled() { return recordingEnabled; }
        public void setRecordingEnabled(boolean recordingEnabled) { this.recordingEnabled = recordingEnabled; }
        public java.util.List<String> getSessionEvents() { return sessionEvents; }
    }
    
    public static class SmsContext {
        private String smsId;
        private String fromNumber;
        private String toNumber;
        private String message;
        private String status = "QUEUED";
        private long sentTime;
        private long deliveredTime;
        private java.util.List<String> events = new java.util.ArrayList<>();
        
        public SmsContext(String smsId, String fromNumber, String toNumber, String message) {
            this.smsId = smsId;
            this.fromNumber = fromNumber;
            this.toNumber = toNumber;
            this.message = message;
        }
        
        public void addEvent(String event) { events.add(event); }
        
        // Getters and setters
        public String getSmsId() { return smsId; }
        public String getFromNumber() { return fromNumber; }
        public String getToNumber() { return toNumber; }
        public String getMessage() { return message; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getSentTime() { return sentTime; }
        public void setSentTime(long sentTime) { this.sentTime = sentTime; }
        public long getDeliveredTime() { return deliveredTime; }
        public void setDeliveredTime(long deliveredTime) { this.deliveredTime = deliveredTime; }
        public java.util.List<String> getEvents() { return events; }
    }
}