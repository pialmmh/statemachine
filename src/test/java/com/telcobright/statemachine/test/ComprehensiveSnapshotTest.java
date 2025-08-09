package com.telcobright.statemachine.test;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.events.GenericStateMachineEvent;
import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachine.monitoring.*;

import java.util.UUID;

/**
 * Test that demonstrates comprehensive snapshot capture including:
 * - Full event payloads and parameters
 * - Complete context changes with each event
 * - Machine online/offline status tracking
 * - State offline configuration tracking
 * - Registry status monitoring
 */
public class ComprehensiveSnapshotTest {
    
    public static void main(String[] args) {
        System.out.println("🧪 Starting Comprehensive Snapshot Test");
        
        testComprehensiveEventCapture();
        
        System.out.println("✅ Comprehensive snapshot test completed successfully!");
        System.out.println("📊 Check enhanced_machine_history.html for detailed view!");
    }
    
    private static void testComprehensiveEventCapture() {
        System.out.println("\n📋 Test: Comprehensive Event and Context Capture");
        
        String testRunId = "comprehensive-snapshot-" + UUID.randomUUID().toString().substring(0, 8);
        
        // Create test entities and contexts with rich data
        TestEntity entity = new TestEntity("enhanced-test-001");
        entity.setDescription("Enhanced test with comprehensive monitoring");
        entity.setPriority("HIGH");
        
        RichTestContext context = new RichTestContext("initial-data");
        context.setCustomerId("CUST-12345");
        context.setSessionId("SESSION-ABC-789");
        context.setProcessingFlags(new String[]{"FLAG_A", "FLAG_B"});
        
        // Create state machine with comprehensive monitoring
        DefaultSnapshotRecorder<TestEntity, RichTestContext> recorder = 
            new DefaultSnapshotRecorder<>(SnapshotConfig.comprehensiveConfig());
        
        GenericStateMachine<TestEntity, RichTestContext> machine = FluentStateMachineBuilder
            .<TestEntity, RichTestContext>create("enhanced-machine-001")
            .enableDebug(recorder)
            .withRunId(testRunId)
            .withCorrelationId("enhanced-correlation-456")
            .withDebugSessionId("debug-enhanced-session")
            .initialState("INIT")
            .finalState("COMPLETED")
            
            .state("INIT")
                .on("START_PROCESSING").to("PROCESSING")
                .on("SKIP_TO_VALIDATION").to("VALIDATING")
            .done()
            
            .state("PROCESSING")
                .on("PROCESSING_COMPLETE").to("VALIDATING")
                .on("PROCESSING_ERROR").to("ERROR")
                .on("PAUSE").to("PAUSED")
            .done()
            
            .state("PAUSED")
                .offline() // This state is marked as offline
                .on("RESUME").to("PROCESSING")
                .on("CANCEL").to("CANCELLED")
            .done()
            
            .state("VALIDATING")
                .on("VALIDATION_SUCCESS").to("COMPLETED")
                .on("VALIDATION_FAILED").to("ERROR")
            .done()
            
            .state("ERROR")
                .on("RETRY").to("PROCESSING")
                .on("ABORT").to("CANCELLED")
            .done()
            
            .state("CANCELLED")
                .finalState()
            .done()
            
            .state("COMPLETED")
                .finalState()
            .done()
            
            .build();
        
        machine.setPersistingEntity(entity);
        machine.setContext(context);
        
        // Start the machine
        machine.start();
        
        // Test 1: Rich event with payload
        System.out.println("🔥 Firing START_PROCESSING event with rich payload...");
        RichEvent startEvent = new RichEvent("START_PROCESSING");
        startEvent.setUserId("USER-789");
        startEvent.setRequestId("REQ-001-XYZ");
        startEvent.addParameter("priority", "HIGH");
        startEvent.addParameter("timeout", 30000);
        startEvent.addParameter("retryCount", 0);
        
        machine.fire(startEvent);
        
        // Update context after event
        context.setCurrentPhase("PROCESSING");
        context.setStartTime(System.currentTimeMillis());
        context.incrementEventCount();
        context.addProcessingStep("Data validation started");
        
        // Test 2: Pause event (will transition to OFFLINE state)
        System.out.println("🔥 Firing PAUSE event...");
        RichEvent pauseEvent = new RichEvent("PAUSE");
        pauseEvent.setUserId("USER-789");
        pauseEvent.setRequestId("REQ-002-ABC");
        pauseEvent.addParameter("reason", "USER_REQUESTED");
        pauseEvent.addParameter("duration", 5000);
        
        machine.fire(pauseEvent);
        
        // Update context after pause
        context.setCurrentPhase("PAUSED");
        context.setPausedTime(System.currentTimeMillis());
        context.incrementEventCount();
        context.addProcessingStep("Processing paused by user");
        
        // Test 3: Resume event
        System.out.println("🔥 Firing RESUME event...");
        RichEvent resumeEvent = new RichEvent("RESUME");
        resumeEvent.setUserId("USER-789");
        resumeEvent.setRequestId("REQ-003-DEF");
        resumeEvent.addParameter("resumeReason", "AUTOMATIC");
        
        machine.fire(resumeEvent);
        
        // Update context after resume
        context.setCurrentPhase("PROCESSING_RESUMED");
        context.setResumedTime(System.currentTimeMillis());
        context.incrementEventCount();
        context.addProcessingStep("Processing resumed automatically");
        
        // Test 4: Complete processing
        System.out.println("🔥 Firing PROCESSING_COMPLETE event...");
        RichEvent completeEvent = new RichEvent("PROCESSING_COMPLETE");
        completeEvent.setUserId("USER-789");
        completeEvent.setRequestId("REQ-004-GHI");
        completeEvent.addParameter("processingTime", System.currentTimeMillis() - context.getStartTime());
        completeEvent.addParameter("itemsProcessed", 150);
        completeEvent.addParameter("success", true);
        
        machine.fire(completeEvent);
        
        // Update context after completion
        context.setCurrentPhase("VALIDATING");
        context.setProcessingCompleteTime(System.currentTimeMillis());
        context.incrementEventCount();
        context.addProcessingStep("Processing completed successfully");
        
        // Test 5: Final validation
        System.out.println("🔥 Firing VALIDATION_SUCCESS event...");
        RichEvent validationEvent = new RichEvent("VALIDATION_SUCCESS");
        validationEvent.setUserId("SYSTEM");
        validationEvent.setRequestId("REQ-005-JKL");
        validationEvent.addParameter("validationScore", 95.5);
        validationEvent.addParameter("validationRules", new String[]{"RULE_1", "RULE_2", "RULE_3"});
        validationEvent.addParameter("finalStatus", "APPROVED");
        
        machine.fire(validationEvent);
        
        // Final context update
        context.setCurrentPhase("COMPLETED");
        context.setCompletionTime(System.currentTimeMillis());
        context.incrementEventCount();
        context.addProcessingStep("Validation successful - process complete");
        
        // Display final status
        System.out.println("Final state: " + machine.getCurrentState());
        System.out.println("Debug enabled: " + machine.isDebugEnabled());
        System.out.println("Machine complete: " + machine.isComplete());
        System.out.println("Total events processed: " + context.getEventCount());
        System.out.println("Total processing time: " + (context.getCompletionTime() - context.getStartTime()) + "ms");
        
        machine.stop();
        
        // Generate comprehensive HTML viewer
        recorder.generateHtmlViewer("enhanced-machine-001", "enhanced_machine_history.html");
        
        System.out.println("✅ Comprehensive event capture test completed");
    }
    
    // Enhanced test entity with rich data
    public static class TestEntity implements StateMachineContextEntity<String> {
        private String id;
        private String description;
        private String priority;
        private String currentState;
        private boolean complete = false;
        
        public TestEntity(String id) {
            this.id = id;
        }
        
        @Override
        public boolean isComplete() { return complete; }
        @Override
        public void setComplete(boolean complete) { this.complete = complete; }
        
        // Getters and setters
        public String getId() { return id; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        public void setCurrentState(String currentState) { this.currentState = currentState; }
        public String getCurrentState() { return currentState; }
    }
    
    // Rich context with comprehensive data
    public static class RichTestContext {
        private String testData;
        private String customerId;
        private String sessionId;
        private String currentPhase;
        private String[] processingFlags;
        private long startTime;
        private Long pausedTime;
        private Long resumedTime;
        private Long processingCompleteTime;
        private Long completionTime;
        private int eventCount = 0;
        private java.util.List<String> processingSteps = new java.util.ArrayList<>();
        
        public RichTestContext(String testData) {
            this.testData = testData;
        }
        
        public void incrementEventCount() { this.eventCount++; }
        public void addProcessingStep(String step) { 
            this.processingSteps.add(step); 
        }
        
        // Getters and setters
        public String getTestData() { return testData; }
        public void setTestData(String testData) { this.testData = testData; }
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getCurrentPhase() { return currentPhase; }
        public void setCurrentPhase(String currentPhase) { this.currentPhase = currentPhase; }
        public String[] getProcessingFlags() { return processingFlags; }
        public void setProcessingFlags(String[] processingFlags) { this.processingFlags = processingFlags; }
        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }
        public Long getPausedTime() { return pausedTime; }
        public void setPausedTime(Long pausedTime) { this.pausedTime = pausedTime; }
        public Long getResumedTime() { return resumedTime; }
        public void setResumedTime(Long resumedTime) { this.resumedTime = resumedTime; }
        public Long getProcessingCompleteTime() { return processingCompleteTime; }
        public void setProcessingCompleteTime(Long processingCompleteTime) { this.processingCompleteTime = processingCompleteTime; }
        public Long getCompletionTime() { return completionTime; }
        public void setCompletionTime(Long completionTime) { this.completionTime = completionTime; }
        public int getEventCount() { return eventCount; }
        public java.util.List<String> getProcessingSteps() { return processingSteps; }
    }
    
    // Rich event with comprehensive payload
    public static class RichEvent extends GenericStateMachineEvent {
        private String userId;
        private String requestId;
        private long timestamp = System.currentTimeMillis();
        private java.util.Map<String, Object> parameters = new java.util.HashMap<>();
        
        public RichEvent(String eventType) {
            super(eventType);
        }
        
        public void addParameter(String key, Object value) {
            parameters.put(key, value);
        }
        
        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        public long getTimestamp() { return timestamp; }
        public java.util.Map<String, Object> getParameters() { return parameters; }
        
        @Override
        public String toString() {
            return String.format("RichEvent{type=%s, userId=%s, requestId=%s, params=%d}", 
                                getEventType(), userId, requestId, parameters.size());
        }
    }
}