package com.telcobright.statemachine.test;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.events.GenericStateMachineEvent;
import com.telcobright.statemachine.monitoring.*;
import com.telcobright.statemachine.persistence.*;
import com.telcobright.statemachine.test.entity.CallEntitySnapshot;

/**
 * Comprehensive State Machine Monitoring Demo
 * 
 * Demonstrates:
 * 1. XState-like complete runtime history tracking
 * 2. Database persistence with partitioned repository
 * 3. Auto-generated timestamp-based run IDs
 * 4. JSON + Base64 encoding for snapshot storage
 * 5. Entity-specific snapshot classes (CallEntitySnapshot)
 * 6. Multiple viewing options: HTML viewer + recommendation for open source tools
 */
public class ComprehensiveMonitoringDemo {
    
    public static void main(String[] args) {
        System.out.println("🎯 Comprehensive State Machine Monitoring Demo");
        System.out.println("==============================================");
        System.out.println("Features demonstrated:");
        System.out.println("• XState-like complete runtime history");
        System.out.println("• Auto-generated timestamp run IDs");
        System.out.println("• JSON + Base64 snapshot encoding");
        System.out.println("• Entity-specific snapshots (CallEntitySnapshot)");
        System.out.println("• Database persistence simulation");
        System.out.println("• Multiple viewing options");
        System.out.println();
        
        // Demo 1: In-memory monitoring with auto run ID
        runInMemoryMonitoringDemo();
        
        // Demo 2: Database persistence simulation
        runDatabasePersistenceDemo();
        
        // Demo 3: Viewing recommendations
        showViewingRecommendations();
        
        System.out.println("\n✅ Comprehensive monitoring demo completed!");
    }
    
    /**
     * Demo 1: In-memory monitoring with automatic run ID generation
     */
    private static void runInMemoryMonitoringDemo() {
        System.out.println("\n🚀 === DEMO 1: In-Memory Monitoring with Auto Run ID ===");
        
        try {
            // Create call entity and context
            CallEntity callEntity = new CallEntity();
            callEntity.setCallId("DEMO-CALL-001");
            callEntity.setFromNumber("+1-800-DEMO-01");
            callEntity.setToNumber("+1-555-TEST-01");
            callEntity.setCurrentState("IDLE");
            
            CallContext context = new CallContext("DEMO-CALL-001", "+1-800-DEMO-01", "+1-555-TEST-01");
            
            // Create state machine with auto debug mode (like XState history tracking)
            GenericStateMachine<CallEntity, CallContext> machine = FluentStateMachineBuilder
                .<CallEntity, CallContext>create("call-demo-inmemory")
                .enableDebugWithAutoRunId() // 🎯 Auto timestamp run ID + comprehensive monitoring
                .withCorrelationId("demo-correlation-001")
                .withDebugSessionId("demo-session-001")
                .initialState("IDLE")
                .finalState("COMPLETED")
                
                .state("IDLE")
                    .on("INCOMING_CALL").to("RINGING")
                .done()
                
                .state("RINGING")
                    .on("ANSWER").to("CONNECTED")
                    .on("REJECT").to("REJECTED")
                    .on("TIMEOUT").to("MISSED")
                .done()
                
                .state("CONNECTED")
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
            
            // Start the monitoring session
            System.out.println("\n📊 Starting monitored call session...");
            System.out.println("🔍 Debug Run ID: " + machine.getRunId());
            machine.start();
            
            // Simulate complete call flow with comprehensive monitoring
            simulateCallFlow(machine, context);
            
            machine.stop();
            
            // Generate HTML report
            SnapshotRecorder<CallEntity, CallContext> recorder = machine.getSnapshotRecorder();
            if (recorder != null) {
                System.out.println("\n📊 Generating comprehensive monitoring report...");
                String fileName = "monitoring_demo_inmemory_" + System.currentTimeMillis() + ".html";
                recorder.generateHtmlViewer(machine.getId(), fileName);
                
                System.out.println("✅ In-memory demo completed!");
                System.out.println("📄 Report: " + fileName);
                System.out.println("📈 Total snapshots captured: " + recorder.getAllSnapshots().size());
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error in in-memory monitoring demo: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Demo 2: Database persistence simulation with entity-specific snapshots
     */
    private static void runDatabasePersistenceDemo() {
        System.out.println("\n💾 === DEMO 2: Database Persistence with CallEntitySnapshot ===");
        
        try {
            // Create mock repository for demonstration
            StateMachineSnapshotRepository mockRepository = createMockRepository();
            
            // Create call entity and context
            CallEntity callEntity = new CallEntity();
            callEntity.setCallId("DEMO-CALL-002");
            callEntity.setFromNumber("+1-888-DB-TEST");
            callEntity.setToNumber("+1-999-PERSIST");
            callEntity.setCurrentState("IDLE");
            
            CallContext context = new CallContext("DEMO-CALL-002", "+1-888-DB-TEST", "+1-999-PERSIST");
            context.setCallType("ENTERPRISE");
            context.setRecordingEnabled(true);
            
            // Create state machine with database persistence
            GenericStateMachine<CallEntity, CallContext> machine = FluentStateMachineBuilder
                .<CallEntity, CallContext>create("call-demo-database")
                .enableDebugWithDatabase(mockRepository, CallEntity.class) // 🎯 Database + entity snapshots
                .withCorrelationId("demo-correlation-db-002")
                .withDebugSessionId("demo-session-db-002")
                .initialState("IDLE")
                .finalState("COMPLETED")
                
                .state("IDLE")
                    .on("INCOMING_CALL").to("RINGING")
                .done()
                
                .state("RINGING")
                    .on("SESSION_PROGRESS").to("RINGING") // Stay in same state
                    .on("ANSWER").to("CONNECTED")
                    .on("REJECT").to("REJECTED")
                .done()
                
                .state("CONNECTED")
                    .on("DTMF").to("CONNECTED") // Handle DTMF while connected
                    .on("MUTE").to("CONNECTED") // Handle mute while connected
                    .on("HANGUP").to("COMPLETED")
                .done()
                
                .state("REJECTED").finalState().done()
                .state("COMPLETED").finalState().done()
                
                .build();
            
            machine.setPersistingEntity(callEntity);
            machine.setContext(context);
            
            // Start the monitored database session
            System.out.println("\n📊 Starting database-monitored call session...");
            System.out.println("🔍 Debug Run ID: " + machine.getRunId());
            System.out.println("💾 Using CallEntitySnapshot for entity-specific data");
            machine.start();
            
            // Simulate rich call flow with database persistence
            simulateRichCallFlow(machine, context);
            
            machine.stop();
            
            // Show database persistence results
            showDatabasePersistenceResults(machine);
            
        } catch (Exception e) {
            System.err.println("❌ Error in database persistence demo: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Simulate a comprehensive call flow with state transitions
     */
    private static void simulateCallFlow(GenericStateMachine<CallEntity, CallContext> machine, CallContext context) 
            throws InterruptedException {
        
        // Incoming call
        System.out.println("\n📞 1. Incoming call...");
        machine.fire(new IncomingCallEvent("+1-800-DEMO-01"));
        context.addSessionEvent("Incoming call received");
        Thread.sleep(500);
        
        // Answer call
        System.out.println("✅ 2. Answering call...");
        machine.fire("ANSWER");
        context.setCallStatus("CONNECTED");
        context.setConnectedTime(System.currentTimeMillis());
        context.addSessionEvent("Call answered");
        Thread.sleep(1000);
        
        // Put on hold
        System.out.println("⏸️  3. Putting call on hold...");
        machine.fire("HOLD");
        context.addSessionEvent("Call put on hold");
        Thread.sleep(800);
        
        // Resume from hold
        System.out.println("▶️  4. Resuming call from hold...");
        machine.fire("UNHOLD");
        context.addSessionEvent("Call resumed from hold");
        Thread.sleep(600);
        
        // Attempt transfer
        System.out.println("📞 5. Attempting call transfer...");
        machine.fire("TRANSFER");
        context.addSessionEvent("Transfer initiated");
        Thread.sleep(1200);
        
        // Transfer successful
        System.out.println("✅ 6. Transfer successful...");
        machine.fire("TRANSFER_SUCCESS");
        context.addSessionEvent("Transfer completed successfully");
        Thread.sleep(500);
        
        // End call
        System.out.println("📴 7. Ending call...");
        machine.fire("HANGUP");
        context.setCallStatus("COMPLETED");
        context.setDisconnectReason("NORMAL_CLEARING");
        context.addSessionEvent("Call ended normally");
    }
    
    /**
     * Simulate rich call flow with advanced features
     */
    private static void simulateRichCallFlow(GenericStateMachine<CallEntity, CallContext> machine, CallContext context) 
            throws InterruptedException {
        
        // Enhanced incoming call
        System.out.println("\n📞 1. Enhanced incoming call...");
        EnhancedIncomingCallEvent enhancedCall = new EnhancedIncomingCallEvent("+1-888-DB-TEST");
        enhancedCall.setCallType("ENTERPRISE");
        enhancedCall.setPriority("HIGH");
        enhancedCall.setRecordingRequired(true);
        machine.fire(enhancedCall);
        context.addSessionEvent("Enhanced incoming call - " + enhancedCall.getCallType());
        Thread.sleep(300);
        
        // Session progress events
        System.out.println("📡 2. Session progress updates...");
        SessionProgressEvent progress1 = new SessionProgressEvent("100", "Trying");
        machine.fire(progress1);
        context.addSessionEvent("Session progress: Trying");
        Thread.sleep(200);
        
        SessionProgressEvent progress2 = new SessionProgressEvent("180", "Ringing");
        machine.fire(progress2);
        context.incrementRingCount();
        context.addSessionEvent("Session progress: Ringing");
        Thread.sleep(400);
        
        // Answer call
        System.out.println("✅ 3. Answering enterprise call...");
        machine.fire("ANSWER");
        context.setCallStatus("CONNECTED");
        context.setConnectedTime(System.currentTimeMillis());
        context.addSessionEvent("Enterprise call answered");
        Thread.sleep(600);
        
        // DTMF input
        System.out.println("🔢 4. DTMF input received...");
        DTMFEvent dtmf1 = new DTMFEvent("1");
        dtmf1.setDuration(200);
        dtmf1.setVolume(-10);
        machine.fire(dtmf1);
        context.addSessionEvent("DTMF: " + dtmf1.getDigit());
        Thread.sleep(300);
        
        DTMFEvent dtmf2 = new DTMFEvent("#");
        dtmf2.setDuration(150);
        dtmf2.setVolume(-8);
        machine.fire(dtmf2);
        context.addSessionEvent("DTMF: " + dtmf2.getDigit());
        Thread.sleep(400);
        
        // Mute/unmute
        System.out.println("🔇 5. Mute operation...");
        MuteEvent mute = new MuteEvent(true);
        machine.fire(mute);
        context.addSessionEvent("Call muted");
        Thread.sleep(800);
        
        // End call
        System.out.println("📴 6. Ending enterprise call...");
        EnhancedHangupEvent hangup = new EnhancedHangupEvent();
        hangup.setReason("NORMAL_CLEARING");
        hangup.setInitiatedBy("CALLER");
        hangup.setCause("16");
        hangup.setBillableSeconds(65);
        machine.fire(hangup);
        context.setCallStatus("COMPLETED");
        context.setDisconnectReason("NORMAL_CLEARING");
        context.addSessionEvent("Enterprise call completed - billed " + hangup.getBillableSeconds() + "s");
    }
    
    /**
     * Show database persistence results
     */
    private static void showDatabasePersistenceResults(GenericStateMachine<CallEntity, CallContext> machine) {
        SnapshotRecorder<CallEntity, CallContext> recorder = machine.getSnapshotRecorder();
        
        if (recorder instanceof DatabaseSnapshotRecorder) {
            DatabaseSnapshotRecorder<CallEntity, CallContext> dbRecorder = 
                (DatabaseSnapshotRecorder<CallEntity, CallContext>) recorder;
            
            System.out.println("\n📊 Database Persistence Results:");
            dbRecorder.printPerformanceMetrics();
            
            // Generate HTML report
            System.out.println("\n📊 Generating database-backed monitoring report...");
            String fileName = "monitoring_demo_database_" + System.currentTimeMillis() + ".html";
            dbRecorder.generateHtmlViewer(machine.getId(), fileName);
            
            System.out.println("✅ Database demo completed!");
            System.out.println("📄 Report: " + fileName);
            System.out.println("💾 Snapshots persisted to database (simulated)");
        }
    }
    
    /**
     * Show recommendations for open source viewing tools
     */
    private static void showViewingRecommendations() {
        System.out.println("\n🛠️  === OPEN SOURCE MONITORING TOOLS RECOMMENDATIONS ===");
        System.out.println();
        
        System.out.println("🎯 **RECOMMENDED**: Grafana + PostgreSQL");
        System.out.println("   • Best for: Time-series visualization, dashboards, alerting");
        System.out.println("   • Setup: Connect Grafana to your PostgreSQL snapshot tables");
        System.out.println("   • Benefits: Rich visualizations, real-time monitoring, historical analysis");
        System.out.println("   • Use case: Production monitoring, trend analysis, SLA tracking");
        System.out.println();
        
        System.out.println("📈 Apache Superset");
        System.out.println("   • Best for: Business intelligence style reports");
        System.out.println("   • Setup: SQL-based exploration of snapshot data");
        System.out.println("   • Benefits: Interactive dashboards, drill-down capabilities");
        System.out.println("   • Use case: Business reporting, state machine analytics");
        System.out.println();
        
        System.out.println("🔍 Jaeger (Distributed Tracing)");
        System.out.println("   • Best for: Distributed state machine orchestration");
        System.out.println("   • Setup: Export snapshots as tracing spans");
        System.out.println("   • Benefits: Cross-service state machine flows");
        System.out.println("   • Use case: Microservices with state machine coordination");
        System.out.println();
        
        System.out.println("🎨 Custom React/Vue.js Dashboard");
        System.out.println("   • Best for: XState-like debugging interface");
        System.out.println("   • Setup: REST API to query snapshot database");
        System.out.println("   • Benefits: Full control, state machine specific UI");
        System.out.println("   • Use case: Developer debugging, custom visualizations");
        System.out.println();
        
        System.out.println("📊 Integration Examples:");
        System.out.println("   • Grafana Query: SELECT state_before, state_after, event_type, transition_duration");
        System.out.println("                    FROM call_entity_snapshots WHERE run_id = 'your-run-id'");
        System.out.println("   • REST API: GET /api/snapshots/{runId} -> JSON for custom dashboard");
        System.out.println("   • Elasticsearch: Index snapshots for full-text search and Kibana visualization");
    }
    
    /**
     * Create a mock repository for demonstration
     */
    private static StateMachineSnapshotRepository createMockRepository() {
        return new StateMachineSnapshotRepository() {
            @Override
            public void saveAsync(StateMachineSnapshotEntity snapshot) {
                // Simulate database save
                System.out.println("💾 [MOCK DB] Saving snapshot: " + snapshot.getMachineId() + 
                                 " id=" + snapshot.getId() + " (JSON+Base64 encoded)");
            }
            
            @Override
            public java.util.Optional<StateMachineSnapshotEntity> findLatestByMachineId(String machineId) {
                System.out.println("🔍 [MOCK DB] Finding latest snapshot for: " + machineId);
                return java.util.Optional.empty();
            }
            
            @Override
            public java.util.List<StateMachineSnapshotEntity> findAllByMachineId(String machineId) {
                System.out.println("🔍 [MOCK DB] Finding all snapshots for: " + machineId);
                return java.util.List.of();
            }
            
            @Override
            public void deleteByMachineId(String machineId) {
                System.out.println("🗑️  [MOCK DB] Deleting snapshots for: " + machineId);
            }
            
            @Override
            public java.util.List<StateMachineSnapshotEntity> findAllOfflineSnapshots() {
                System.out.println("🔍 [MOCK DB] Finding offline snapshots");
                return java.util.List.of();
            }
        };
    }
    
    // Event classes for demonstration
    public static class IncomingCallEvent extends GenericStateMachineEvent {
        private final String fromNumber;
        public IncomingCallEvent(String fromNumber) {
            super("INCOMING_CALL");
            this.fromNumber = fromNumber;
        }
        public String getFromNumber() { return fromNumber; }
    }
    
    public static class EnhancedIncomingCallEvent extends IncomingCallEvent {
        private String callType;
        private String priority;
        private boolean recordingRequired;
        
        public EnhancedIncomingCallEvent(String fromNumber) {
            super(fromNumber);
        }
        
        public String getCallType() { return callType; }
        public void setCallType(String callType) { this.callType = callType; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        public boolean isRecordingRequired() { return recordingRequired; }
        public void setRecordingRequired(boolean recordingRequired) { this.recordingRequired = recordingRequired; }
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
    
    public static class DTMFEvent extends GenericStateMachineEvent {
        private final String digit;
        private int duration;
        private int volume;
        public DTMFEvent(String digit) {
            super("DTMF");
            this.digit = digit;
        }
        public String getDigit() { return digit; }
        public int getDuration() { return duration; }
        public void setDuration(int duration) { this.duration = duration; }
        public int getVolume() { return volume; }
        public void setVolume(int volume) { this.volume = volume; }
    }
    
    public static class MuteEvent extends GenericStateMachineEvent {
        private final boolean muted;
        public MuteEvent(boolean muted) {
            super("MUTE");
            this.muted = muted;
        }
        public boolean isMuted() { return muted; }
    }
    
    public static class EnhancedHangupEvent extends GenericStateMachineEvent {
        private String reason;
        private String initiatedBy;
        private String cause;
        private int billableSeconds;
        
        public EnhancedHangupEvent() {
            super("HANGUP");
        }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getInitiatedBy() { return initiatedBy; }
        public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }
        public String getCause() { return cause; }
        public void setCause(String cause) { this.cause = cause; }
        public int getBillableSeconds() { return billableSeconds; }
        public void setBillableSeconds(int billableSeconds) { this.billableSeconds = billableSeconds; }
    }
    
    // Reuse entities from previous demo
    public static class CallEntity implements StateMachineContextEntity<String> {
        private String callId;
        private String fromNumber;
        private String toNumber;
        private String currentState;
        private boolean complete = false;
        
        @Override public boolean isComplete() { return complete; }
        @Override public void setComplete(boolean complete) { this.complete = complete; }
        public String getShardingKey() { return callId; }
        
        public String getCallId() { return callId; }
        public void setCallId(String callId) { this.callId = callId; }
        public String getFromNumber() { return fromNumber; }
        public void setFromNumber(String fromNumber) { this.fromNumber = fromNumber; }
        public String getToNumber() { return toNumber; }
        public void setToNumber(String toNumber) { this.toNumber = toNumber; }
        public String getCurrentState() { return currentState; }
        public void setCurrentState(String currentState) { this.currentState = currentState; }
    }
    
    public static class CallContext {
        private String callId;
        private String fromNumber;
        private String toNumber;
        private String callType;
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
            this.callStartTime = System.currentTimeMillis();
        }
        
        public void addSessionEvent(String event) {
            sessionEvents.add(event);
        }
        
        public void incrementRingCount() {
            ringCount++;
        }
        
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
        
        @Override
        public String toString() {
            return String.format("CallContext{id=%s, %s->%s, status=%s, duration=%ds}", 
                               callId, fromNumber, toNumber, callStatus, getCallDuration().toSeconds());
        }
    }
}