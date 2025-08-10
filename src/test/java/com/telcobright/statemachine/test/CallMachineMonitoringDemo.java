package com.telcobright.statemachine.test;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.events.GenericStateMachineEvent;
import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachine.monitoring.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.time.Duration;

/**
 * Call Machine demonstration with comprehensive monitoring enabled.
 * Shows real-world call flow with rich events and context tracking.
 */
public class CallMachineMonitoringDemo {
    
    public static void main(String[] args) {
        System.out.println("📞 Call Machine Monitoring Demo");
        System.out.println("================================");
        
        // Demo 1: Incoming call flow with debugging
        runIncomingCallWithMonitoring();
        
        System.out.println("\n" + "=".repeat(60));
        
        // Demo 2: Call with session progress and rich events
        runCallWithSessionProgressMonitoring();
        
        System.out.println("\n✅ Call machine monitoring demo completed!");
        System.out.println("📊 Generated reports:");
        System.out.println("   • call_machine_basic_flow.html");
        System.out.println("   • call_machine_session_progress.html");
        System.out.println("   • combined_call_machines.html");
        System.out.println("\n🌐 Open these HTML files in your browser to view detailed reports!");
    }
    
    /**
     * Basic incoming call flow with comprehensive monitoring
     */
    private static void runIncomingCallWithMonitoring() {
        System.out.println("\n📞 === DEMO 1: Basic Call Flow with Monitoring ===");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedNow = LocalDateTime.now().format(formatter);

        String testRunId = "call-basic-" + formattedNow+"-"+Math.random();

        // Create call entity and context
        CallEntity callEntity = new CallEntity();
        callEntity.setCallId("CALL-BASIC-001");
        callEntity.setFromNumber("+1234567890");
        callEntity.setToNumber("+0987654321");
        callEntity.setCurrentState("IDLE");
        
        CallContext context = new CallContext("CALL-BASIC-001", "+1234567890", "+0987654321");
        
        // Create recorder with comprehensive monitoring
        DefaultSnapshotRecorder<CallEntity, CallContext> recorder = 
            new DefaultSnapshotRecorder<>(SnapshotConfig.comprehensiveConfig());
        
        // Create call machine with monitoring enabled
        GenericStateMachine<CallEntity, CallContext> machine = FluentStateMachineBuilder
            .<CallEntity, CallContext>create("call-machine-basic")
            .enableDebug(recorder)
            .withRunId(testRunId)
            .withCorrelationId("call-basic-correlation")
            .withDebugSessionId("call-basic-debug")
            .initialState("IDLE")
            .finalState("HUNGUP")
            
            .state("IDLE")
                .on("INCOMING_CALL").to("RINGING")
            .done()
            
            .state("RINGING")
                .on("ANSWER").to("CONNECTED")
                .on("REJECT").to("HUNGUP")
                .on("TIMEOUT").to("MISSED")
            .done()
            
            .state("CONNECTED")
                .on("HANGUP").to("HUNGUP")
                .on("TRANSFER").to("TRANSFERRING")
            .done()
            
            .state("TRANSFERRING")
                .on("TRANSFER_SUCCESS").to("CONNECTED")
                .on("TRANSFER_FAILED").to("CONNECTED")
            .done()
            
            .state("MISSED")
                .finalState()
            .done()
            
            .state("HUNGUP")
                .finalState()
            .done()
            
            .build();
        
        machine.setPersistingEntity(callEntity);
        machine.setContext(context);
        
        // Start monitoring the call flow
        System.out.println("📋 Initial Context: " + context);
        System.out.println("🔄 Initial state: " + machine.getCurrentState());
        machine.start();
        
        try {
            // Simulate incoming call
            System.out.println("\n--- 📱 Incoming Call ---");
            IncomingCallEvent incomingCall = new IncomingCallEvent("+1234567890");
            machine.fire(incomingCall);
            
            // Update context after incoming call
            context.setCallStartTime(System.currentTimeMillis());
            context.setCallStatus("RINGING");
            context.addSessionEvent("Incoming call from " + incomingCall.getFromNumber());
            
            System.out.println("🔄 State: " + machine.getCurrentState());
            System.out.println("📊 Ring count: " + context.getRingCount());
            Thread.sleep(1000);
            
            // Answer the call
            System.out.println("\n--- ✅ Answer Call ---");
            AnswerEvent answerEvent = new AnswerEvent();
            answerEvent.setAnsweredBy("USER");
            answerEvent.setAnswerTime(System.currentTimeMillis());
            machine.fire(answerEvent);
            
            // Update context after answer
            context.setCallStatus("CONNECTED");
            context.setConnectedTime(System.currentTimeMillis());
            context.addSessionEvent("Call answered by user");
            
            System.out.println("🔄 State: " + machine.getCurrentState());
            System.out.println("📊 Call duration: " + context.getCallDuration().toSeconds() + "s");
            Thread.sleep(2000);
            
            // Hangup call
            System.out.println("\n--- 📴 Hangup Call ---");
            HangupEvent hangupEvent = new HangupEvent();
            hangupEvent.setReason("USER_HANGUP");
            hangupEvent.setInitiatedBy("CALLER");
            machine.fire(hangupEvent);
            
            // Update context after hangup
            context.setCallStatus("COMPLETED");
            context.setDisconnectReason(hangupEvent.getReason());
            context.addSessionEvent("Call ended - " + hangupEvent.getReason());
            
            System.out.println("🔄 Final state: " + machine.getCurrentState());
            
            // Show comprehensive call summary
            showCallSummary(context);
            
            machine.stop();
            
            // Generate HTML report
            System.out.println("📊 Generating comprehensive report...");
            recorder.generateHtmlViewer("call-machine-basic", "call_machine_basic_flow.html");
            System.out.println("✅ Report generated: call_machine_basic_flow.html");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Call with session progress events and rich monitoring
     */
    private static void runCallWithSessionProgressMonitoring() {
        System.out.println("\n📞 === DEMO 2: Call with Session Progress & Rich Events ===");
        
        String testRunId = "call-session-" + UUID.randomUUID().toString().substring(0, 8);
        
        // Create enhanced call entity and context
        CallEntity callEntity = new CallEntity();
        callEntity.setCallId("CALL-SESSION-002");
        callEntity.setFromNumber("+1800555123");
        callEntity.setToNumber("+0987654321");
        callEntity.setCurrentState("IDLE");
        
        CallContext context = new CallContext("CALL-SESSION-002", "+1800555123", "+0987654321");
        
        // Create recorder for session progress tracking
        DefaultSnapshotRecorder<CallEntity, CallContext> recorder = 
            new DefaultSnapshotRecorder<>(SnapshotConfig.comprehensiveConfig());
        
        // Create call machine with enhanced monitoring
        GenericStateMachine<CallEntity, CallContext> machine = FluentStateMachineBuilder
            .<CallEntity, CallContext>create("call-machine-session")
            .enableDebug(recorder)
            .withRunId(testRunId)
            .withCorrelationId("call-session-correlation")
            .withDebugSessionId("call-session-debug")
            .initialState("IDLE")
            .finalState("HUNGUP")
            
            .state("IDLE")
                .on("INCOMING_CALL").to("RINGING")
            .done()
            
            .state("RINGING")
                .on("SESSION_PROGRESS").to("RINGING") // Stay in ringing but handle progress
                .on("ANSWER").to("CONNECTED")
                .on("REJECT").to("HUNGUP")
                .on("TIMEOUT").to("MISSED")
            .done()
            
            .state("CONNECTED")
                .on("DTMF").to("CONNECTED") // Stay connected but handle DTMF
                .on("HANGUP").to("HUNGUP")
            .done()
            
            .state("MISSED")
                .finalState()
            .done()
            
            .state("HUNGUP")
                .finalState()
            .done()
            
            .build();
        
        machine.setPersistingEntity(callEntity);
        machine.setContext(context);
        
        System.out.println("📋 Initial Context: " + context);
        System.out.println("🔄 Initial state: " + machine.getCurrentState());
        machine.start();
        
        try {
            // Start call with enhanced event
            System.out.println("\n--- 📱 Enhanced Incoming Call ---");
            EnhancedIncomingCallEvent incomingCall = new EnhancedIncomingCallEvent("+1800555123");
            incomingCall.setCallType("TOLL_FREE");
            incomingCall.setPriority("HIGH");
            incomingCall.setRecordingRequired(true);
            machine.fire(incomingCall);
            
            // Update context for toll-free call
            context.setCallStartTime(System.currentTimeMillis());
            context.setCallStatus("RINGING");
            context.setCallType(incomingCall.getCallType());
            context.addSessionEvent("Enhanced incoming call - " + incomingCall.getCallType());
            
            System.out.println("🔄 State: " + machine.getCurrentState());
            Thread.sleep(500);
            
            // Send session progress events
            System.out.println("\n--- 📡 Session Progress Events ---");
            
            // Progress: Trying
            SessionProgressEvent progress1 = new SessionProgressEvent("100", "Trying");
            progress1.setProgressPercentage(10);
            machine.fire(progress1);
            context.addSessionEvent("Session Progress: " + progress1.getDescription());
            Thread.sleep(300);
            
            // Progress: Ringing
            SessionProgressEvent progress2 = new SessionProgressEvent("180", "Ringing");
            progress2.setProgressPercentage(50);
            machine.fire(progress2);
            context.addSessionEvent("Session Progress: " + progress2.getDescription());
            context.incrementRingCount();
            Thread.sleep(500);
            
            // Progress: Session Progress
            SessionProgressEvent progress3 = new SessionProgressEvent("183", "Session Progress");
            progress3.setProgressPercentage(75);
            progress3.addCustomParameter("media", "audio");
            progress3.addCustomParameter("codec", "G.711");
            machine.fire(progress3);
            context.addSessionEvent("Session Progress: " + progress3.getDescription() + " with media");
            Thread.sleep(300);
            
            // Answer call with rich event
            System.out.println("\n--- ✅ Enhanced Answer ---");
            EnhancedAnswerEvent answerEvent = new EnhancedAnswerEvent();
            answerEvent.setAnsweredBy("AUTO_ATTENDANT");
            answerEvent.setAnswerTime(System.currentTimeMillis());
            answerEvent.setAnswerMethod("IVR_SYSTEM");
            machine.fire(answerEvent);
            
            // Update context after enhanced answer
            context.setCallStatus("CONNECTED");
            context.setConnectedTime(System.currentTimeMillis());
            context.addSessionEvent("Call answered by " + answerEvent.getAnsweredBy());
            
            System.out.println("🔄 State: " + machine.getCurrentState());
            Thread.sleep(1000);
            
            // Simulate DTMF input
            System.out.println("\n--- 🔢 DTMF Input ---");
            DTMFEvent dtmfEvent = new DTMFEvent("1");
            dtmfEvent.setDuration(200);
            dtmfEvent.setVolume(-10);
            machine.fire(dtmfEvent);
            
            context.addSessionEvent("DTMF pressed: " + dtmfEvent.getDigit());
            Thread.sleep(500);
            
            // Another DTMF
            DTMFEvent dtmfEvent2 = new DTMFEvent("*");
            dtmfEvent2.setDuration(150);
            dtmfEvent2.setVolume(-12);
            machine.fire(dtmfEvent2);
            
            context.addSessionEvent("DTMF pressed: " + dtmfEvent2.getDigit());
            Thread.sleep(500);
            
            // End call with detailed event
            System.out.println("\n--- 📴 Enhanced Hangup ---");
            EnhancedHangupEvent hangupEvent = new EnhancedHangupEvent();
            hangupEvent.setReason("NORMAL_CLEARING");
            hangupEvent.setInitiatedBy("CALLEE");
            hangupEvent.setCause("16"); // Q.850 cause code
            hangupEvent.setBillableSeconds(45);
            machine.fire(hangupEvent);
            
            // Final context update
            context.setCallStatus("COMPLETED");
            context.setDisconnectReason(hangupEvent.getReason());
            context.addSessionEvent("Call ended - " + hangupEvent.getReason() + " (Q.850: " + hangupEvent.getCause() + ")");
            
            System.out.println("🔄 Final state: " + machine.getCurrentState());
            
            // Show comprehensive call summary
            showCallSummary(context);
            
            machine.stop();
            
            // Generate HTML report
            System.out.println("📊 Generating session progress report...");
            recorder.generateHtmlViewer("call-machine-session", "call_machine_session_progress.html");
            
            // Generate combined report
            recorder.generateCombinedHtmlViewer("combined_call_machines.html");
            
            System.out.println("✅ Reports generated:");
            System.out.println("   • call_machine_session_progress.html");
            System.out.println("   • combined_call_machines.html");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Display comprehensive call summary from context
     */
    private static void showCallSummary(CallContext context) {
        System.out.println("\n📊 === Comprehensive Call Summary ===");
        System.out.println("📞 Call ID: " + context.getCallId());
        System.out.println("📋 From: " + context.getFromNumber() + " → To: " + context.getToNumber());
        System.out.println("📞 Call Status: " + context.getCallStatus());
        System.out.println("⏱️ Call Duration: " + context.getCallDuration().toSeconds() + "s");
        System.out.println("🔔 Ring Duration: " + context.getRingDuration().toSeconds() + "s");
        System.out.println("🔢 Ring Count: " + context.getRingCount());
        System.out.println("🎙️ Recording Enabled: " + (context.isRecordingEnabled() ? "Yes" : "No"));
        System.out.println("📈 Long Call: " + (context.isLongCall() ? "Yes" : "No"));
        
        if (context.getCallType() != null) {
            System.out.println("📞 Call Type: " + context.getCallType());
        }
        
        if (context.getDisconnectReason() != null) {
            System.out.println("💡 Disconnect Reason: " + context.getDisconnectReason());
        }
        
        System.out.println("\n📝 Session Events (" + context.getSessionEvents().size() + " events):");
        for (String event : context.getSessionEvents()) {
            System.out.println("   • " + event);
        }
        
        System.out.println("=" + "=".repeat(50));
    }
    
    // Rich event classes for comprehensive monitoring
    
    public static class IncomingCallEvent extends GenericStateMachineEvent {
        private String fromNumber;
        private long timestamp = System.currentTimeMillis();
        
        public IncomingCallEvent(String fromNumber) {
            super("INCOMING_CALL");
            this.fromNumber = fromNumber;
        }
        
        public String getFromNumber() { return fromNumber; }
        public long getTimestamp() { return timestamp; }
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
    
    public static class AnswerEvent extends GenericStateMachineEvent {
        private String answeredBy;
        private long answerTime;
        
        public AnswerEvent() {
            super("ANSWER");
        }
        
        public String getAnsweredBy() { return answeredBy; }
        public void setAnsweredBy(String answeredBy) { this.answeredBy = answeredBy; }
        public long getAnswerTime() { return answerTime; }
        public void setAnswerTime(long answerTime) { this.answerTime = answerTime; }
    }
    
    public static class EnhancedAnswerEvent extends AnswerEvent {
        private String answerMethod;
        
        public String getAnswerMethod() { return answerMethod; }
        public void setAnswerMethod(String answerMethod) { this.answerMethod = answerMethod; }
    }
    
    public static class SessionProgressEvent extends GenericStateMachineEvent {
        private String responseCode;
        private String description;
        private int progressPercentage;
        private java.util.Map<String, String> customParameters = new java.util.HashMap<>();
        
        public SessionProgressEvent(String responseCode, String description) {
            super("SESSION_PROGRESS");
            this.responseCode = responseCode;
            this.description = description;
        }
        
        public void addCustomParameter(String key, String value) {
            customParameters.put(key, value);
        }
        
        public String getResponseCode() { return responseCode; }
        public String getDescription() { return description; }
        public int getProgressPercentage() { return progressPercentage; }
        public void setProgressPercentage(int progressPercentage) { this.progressPercentage = progressPercentage; }
        public java.util.Map<String, String> getCustomParameters() { return customParameters; }
    }
    
    public static class DTMFEvent extends GenericStateMachineEvent {
        private String digit;
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
    
    public static class HangupEvent extends GenericStateMachineEvent {
        private String reason;
        private String initiatedBy;
        
        public HangupEvent() {
            super("HANGUP");
        }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getInitiatedBy() { return initiatedBy; }
        public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }
    }
    
    public static class EnhancedHangupEvent extends HangupEvent {
        private String cause;
        private int billableSeconds;
        
        public String getCause() { return cause; }
        public void setCause(String cause) { this.cause = cause; }
        public int getBillableSeconds() { return billableSeconds; }
        public void setBillableSeconds(int billableSeconds) { this.billableSeconds = billableSeconds; }
    }
    
    // Call Entity for monitoring
    public static class CallEntity implements StateMachineContextEntity<String> {
        private String callId;
        private String fromNumber;
        private String toNumber;
        private String currentState;
        private boolean complete = false;
        private LocalDateTime lastStateChange = LocalDateTime.now();
        
        @Override
        public boolean isComplete() { return complete; }
        @Override
        public void setComplete(boolean complete) { this.complete = complete; }
        
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
    
    // Call Context for rich call tracking
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
        }
        
        public void addSessionEvent(String event) {
            sessionEvents.add(event);
        }
        
        public void incrementRingCount() {
            ringCount++;
        }
        
        public Duration getCallDuration() {
            if (callStartTime == 0) return Duration.ZERO;
            return Duration.ofMillis(System.currentTimeMillis() - callStartTime);
        }
        
        public Duration getRingDuration() {
            if (callStartTime == 0 || connectedTime == null) return Duration.ZERO;
            return Duration.ofMillis(connectedTime - callStartTime);
        }
        
        public boolean isLongCall() {
            return getCallDuration().toSeconds() > 60;
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