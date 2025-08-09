package com.telcobright.statemachine.test;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.events.GenericStateMachineEvent;
import com.telcobright.statemachine.monitoring.*;

import java.util.UUID;

/**
 * Test that demonstrates the HTML viewer functionality for state machine history.
 * This test creates multiple state machines and generates an interactive HTML report.
 */
public class HtmlViewerTest {
    
    public static void main(String[] args) {
        System.out.println("🧪 Starting HTML Viewer Test");
        
        // Create multiple state machines to demonstrate the viewer
        testMultipleMachinesWithHtmlViewer();
        
        System.out.println("✅ HTML Viewer test completed successfully!");
        System.out.println("📊 Check the generated HTML files in your project directory!");
    }
    
    /**
     * Test multiple state machines with HTML viewer generation
     */
    private static void testMultipleMachinesWithHtmlViewer() {
        System.out.println("\n📋 Test: Multiple Machines with HTML Viewer");
        
        String testRunId = "html-viewer-test-" + UUID.randomUUID().toString().substring(0, 8);
        
        // Create first machine - Call Simulation
        testCallStateMachine(testRunId);
        
        // Create second machine - Order Processing Simulation  
        testOrderStateMachine(testRunId);
        
        // Create third machine - File Processing Simulation
        testFileProcessingStateMachine(testRunId);
        
        System.out.println("✅ Multiple machines test completed");
    }
    
    private static void testCallStateMachine(String testRunId) {
        System.out.println("\n🔥 Testing Call State Machine");
        
        CallEntity entity = new CallEntity("call-001", "1234567890", "0987654321");
        CallContext context = new CallContext("call-001", "INBOUND");
        
        DefaultSnapshotRecorder<CallEntity, CallContext> recorder = 
            new DefaultSnapshotRecorder<>(SnapshotConfig.comprehensiveConfig());
        
        GenericStateMachine<CallEntity, CallContext> machine = FluentStateMachineBuilder
            .<CallEntity, CallContext>create("call-machine-001")
            .enableDebug(recorder)
            .withRunId(testRunId)
            .withCorrelationId("call-correlation-001")
            .withDebugSessionId("call-debug-session")
            .initialState("IDLE")
            .finalState("DISCONNECTED")
            
            .state("IDLE")
                .on("INCOMING_CALL").to("RINGING")
            .done()
            
            .state("RINGING")
                .on("ANSWER").to("CONNECTED")
                .on("REJECT").to("DISCONNECTED")
                .on("TIMEOUT").to("MISSED")
            .done()
            
            .state("CONNECTED")
                .on("TRANSFER").to("TRANSFERRING")
                .on("HOLD").to("ON_HOLD")
                .on("HANGUP").to("DISCONNECTED")
            .done()
            
            .state("TRANSFERRING")
                .on("TRANSFER_COMPLETE").to("CONNECTED")
                .on("TRANSFER_FAILED").to("CONNECTED")
            .done()
            
            .state("ON_HOLD")
                .on("UNHOLD").to("CONNECTED")
                .on("HANGUP").to("DISCONNECTED")
            .done()
            
            .state("MISSED")
                .finalState()
            .done()
            
            .state("DISCONNECTED")
                .finalState()
            .done()
            
            .build();
        
        machine.setPersistingEntity(entity);
        machine.setContext(context);
        machine.start();
        
        // Simulate call flow
        System.out.println("📞 Incoming call...");
        machine.fire(new GenericStateMachineEvent("INCOMING_CALL"));
        
        // Update context
        context.setConnectedTime(System.currentTimeMillis());
        
        System.out.println("📞 Call answered...");
        machine.fire(new GenericStateMachineEvent("ANSWER"));
        
        // Update context again
        context.setCallQuality("GOOD");
        context.setDuration(45000L); // 45 seconds
        
        System.out.println("📞 Put call on hold...");
        machine.fire(new GenericStateMachineEvent("HOLD"));
        
        System.out.println("📞 Resume call...");
        machine.fire(new GenericStateMachineEvent("UNHOLD"));
        
        System.out.println("📞 Hang up...");
        machine.fire(new GenericStateMachineEvent("HANGUP"));
        
        machine.stop();
        
        // Generate HTML viewer for this specific machine
        recorder.generateHtmlViewer("call-machine-001", "call_machine_history.html");
    }
    
    private static void testOrderStateMachine(String testRunId) {
        System.out.println("\n📦 Testing Order Processing State Machine");
        
        OrderEntity entity = new OrderEntity("order-001", "customer-123", 199.99);
        OrderContext context = new OrderContext("order-001", "electronics", 2);
        
        DefaultSnapshotRecorder<OrderEntity, OrderContext> recorder = 
            new DefaultSnapshotRecorder<>(SnapshotConfig.defaultConfig());
        
        GenericStateMachine<OrderEntity, OrderContext> machine = FluentStateMachineBuilder
            .<OrderEntity, OrderContext>create("order-machine-001")
            .enableDebug(recorder)
            .withRunId(testRunId)
            .withCorrelationId("order-correlation-001")
            .initialState("PENDING")
            .finalState("COMPLETED")
            
            .state("PENDING")
                .on("VALIDATE").to("VALIDATED")
                .on("CANCEL").to("CANCELLED")
            .done()
            
            .state("VALIDATED")
                .on("PAYMENT_RECEIVED").to("PAID")
                .on("PAYMENT_FAILED").to("PAYMENT_FAILED")
            .done()
            
            .state("PAID")
                .on("SHIP").to("SHIPPED")
            .done()
            
            .state("SHIPPED")
                .on("DELIVER").to("DELIVERED")
                .on("RETURN").to("RETURNED")
            .done()
            
            .state("DELIVERED")
                .on("CONFIRM").to("COMPLETED")
            .done()
            
            .state("PAYMENT_FAILED")
                .on("RETRY_PAYMENT").to("VALIDATED")
                .on("CANCEL").to("CANCELLED")
            .done()
            
            .state("RETURNED")
                .on("REFUND").to("REFUNDED")
            .done()
            
            .state("REFUNDED")
                .finalState()
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
        machine.start();
        
        // Simulate order flow
        System.out.println("📦 Validating order...");
        machine.fire(new GenericStateMachineEvent("VALIDATE"));
        
        context.setValidatedTime(System.currentTimeMillis());
        
        System.out.println("💳 Payment received...");
        machine.fire(new GenericStateMachineEvent("PAYMENT_RECEIVED"));
        
        context.setPaymentId("payment-xyz-789");
        
        System.out.println("🚚 Shipping order...");
        machine.fire(new GenericStateMachineEvent("SHIP"));
        
        context.setTrackingNumber("TRACK123456789");
        
        System.out.println("📬 Order delivered...");
        machine.fire(new GenericStateMachineEvent("DELIVER"));
        
        context.setDeliveredTime(System.currentTimeMillis());
        
        System.out.println("✅ Order confirmed...");
        machine.fire(new GenericStateMachineEvent("CONFIRM"));
        
        machine.stop();
        
        // Generate HTML viewer for this specific machine
        recorder.generateHtmlViewer("order-machine-001", "order_machine_history.html");
    }
    
    private static void testFileProcessingStateMachine(String testRunId) {
        System.out.println("\n📄 Testing File Processing State Machine");
        
        FileEntity entity = new FileEntity("file-001", "document.pdf", 2048000);
        FileContext context = new FileContext("file-001", "/uploads/document.pdf");
        
        DefaultSnapshotRecorder<FileEntity, FileContext> recorder = 
            new DefaultSnapshotRecorder<>(SnapshotConfig.productionConfig());
        
        GenericStateMachine<FileEntity, FileContext> machine = FluentStateMachineBuilder
            .<FileEntity, FileContext>create("file-machine-001")
            .enableDebug(recorder)
            .withRunId(testRunId)
            .withCorrelationId("file-correlation-001")
            .initialState("UPLOADED")
            .finalState("ARCHIVED")
            
            .state("UPLOADED")
                .on("SCAN").to("SCANNING")
            .done()
            
            .state("SCANNING")
                .on("SCAN_COMPLETE").to("SCANNED")
                .on("VIRUS_DETECTED").to("QUARANTINED")
            .done()
            
            .state("SCANNED")
                .on("PROCESS").to("PROCESSING")
            .done()
            
            .state("PROCESSING")
                .on("PROCESS_COMPLETE").to("PROCESSED")
                .on("PROCESS_ERROR").to("ERROR")
            .done()
            
            .state("PROCESSED")
                .on("ARCHIVE").to("ARCHIVED")
                .on("DELETE").to("DELETED")
            .done()
            
            .state("QUARANTINED")
                .on("DELETE").to("DELETED")
            .done()
            
            .state("ERROR")
                .on("RETRY").to("PROCESSING")
                .on("DELETE").to("DELETED")
            .done()
            
            .state("DELETED")
                .finalState()
            .done()
            
            .state("ARCHIVED")
                .finalState()
            .done()
            
            .build();
        
        machine.setPersistingEntity(entity);
        machine.setContext(context);
        machine.start();
        
        // Simulate file processing flow
        System.out.println("🔍 Starting scan...");
        machine.fire(new GenericStateMachineEvent("SCAN"));
        
        context.setScanStartTime(System.currentTimeMillis());
        
        System.out.println("✅ Scan complete - file clean...");
        machine.fire(new GenericStateMachineEvent("SCAN_COMPLETE"));
        
        context.setScanResult("CLEAN");
        
        System.out.println("⚙️ Processing file...");
        machine.fire(new GenericStateMachineEvent("PROCESS"));
        
        context.setProcessingStartTime(System.currentTimeMillis());
        context.setProcessor("ImageProcessor v2.1");
        
        System.out.println("✅ Processing complete...");
        machine.fire(new GenericStateMachineEvent("PROCESS_COMPLETE"));
        
        context.setOutputFile("/processed/document_processed.pdf");
        
        System.out.println("📚 Archiving file...");
        machine.fire(new GenericStateMachineEvent("ARCHIVE"));
        
        machine.stop();
        
        // Generate HTML viewer for this specific machine
        recorder.generateHtmlViewer("file-machine-001", "file_machine_history.html");
        
        // Generate combined HTML viewer for all machines
        recorder.generateCombinedHtmlViewer("all_machines_history.html");
    }
    
    // Test entity classes
    public static class CallEntity implements StateMachineContextEntity<String> {
        private String id;
        private String fromNumber;
        private String toNumber;
        private String currentState;
        private boolean complete = false;
        
        public CallEntity(String id, String fromNumber, String toNumber) {
            this.id = id;
            this.fromNumber = fromNumber;
            this.toNumber = toNumber;
        }
        
        @Override
        public boolean isComplete() { return complete; }
        @Override
        public void setComplete(boolean complete) { this.complete = complete; }
        
        public String getId() { return id; }
        public String getFromNumber() { return fromNumber; }
        public String getToNumber() { return toNumber; }
        public void setCurrentState(String currentState) { this.currentState = currentState; }
        public String getCurrentState() { return currentState; }
    }
    
    public static class CallContext {
        private String callId;
        private String callType;
        private long startTime;
        private Long connectedTime;
        private String callQuality;
        private Long duration;
        
        public CallContext(String callId, String callType) {
            this.callId = callId;
            this.callType = callType;
            this.startTime = System.currentTimeMillis();
        }
        
        // Getters and setters
        public String getCallId() { return callId; }
        public String getCallType() { return callType; }
        public long getStartTime() { return startTime; }
        public Long getConnectedTime() { return connectedTime; }
        public void setConnectedTime(Long connectedTime) { this.connectedTime = connectedTime; }
        public String getCallQuality() { return callQuality; }
        public void setCallQuality(String callQuality) { this.callQuality = callQuality; }
        public Long getDuration() { return duration; }
        public void setDuration(Long duration) { this.duration = duration; }
    }
    
    public static class OrderEntity implements StateMachineContextEntity<String> {
        private String id;
        private String customerId;
        private double amount;
        private String currentState;
        private boolean complete = false;
        
        public OrderEntity(String id, String customerId, double amount) {
            this.id = id;
            this.customerId = customerId;
            this.amount = amount;
        }
        
        @Override
        public boolean isComplete() { return complete; }
        @Override
        public void setComplete(boolean complete) { this.complete = complete; }
        
        public String getId() { return id; }
        public String getCustomerId() { return customerId; }
        public double getAmount() { return amount; }
        public void setCurrentState(String currentState) { this.currentState = currentState; }
    }
    
    public static class OrderContext {
        private String orderId;
        private String category;
        private int quantity;
        private Long validatedTime;
        private String paymentId;
        private String trackingNumber;
        private Long deliveredTime;
        
        public OrderContext(String orderId, String category, int quantity) {
            this.orderId = orderId;
            this.category = category;
            this.quantity = quantity;
        }
        
        // Getters and setters
        public String getOrderId() { return orderId; }
        public String getCategory() { return category; }
        public int getQuantity() { return quantity; }
        public Long getValidatedTime() { return validatedTime; }
        public void setValidatedTime(Long validatedTime) { this.validatedTime = validatedTime; }
        public String getPaymentId() { return paymentId; }
        public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
        public String getTrackingNumber() { return trackingNumber; }
        public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
        public Long getDeliveredTime() { return deliveredTime; }
        public void setDeliveredTime(Long deliveredTime) { this.deliveredTime = deliveredTime; }
    }
    
    public static class FileEntity implements StateMachineContextEntity<String> {
        private String id;
        private String fileName;
        private long fileSize;
        private String currentState;
        private boolean complete = false;
        
        public FileEntity(String id, String fileName, long fileSize) {
            this.id = id;
            this.fileName = fileName;
            this.fileSize = fileSize;
        }
        
        @Override
        public boolean isComplete() { return complete; }
        @Override
        public void setComplete(boolean complete) { this.complete = complete; }
        
        public String getId() { return id; }
        public String getFileName() { return fileName; }
        public long getFileSize() { return fileSize; }
        public void setCurrentState(String currentState) { this.currentState = currentState; }
    }
    
    public static class FileContext {
        private String fileId;
        private String filePath;
        private Long scanStartTime;
        private String scanResult;
        private Long processingStartTime;
        private String processor;
        private String outputFile;
        
        public FileContext(String fileId, String filePath) {
            this.fileId = fileId;
            this.filePath = filePath;
        }
        
        // Getters and setters
        public String getFileId() { return fileId; }
        public String getFilePath() { return filePath; }
        public Long getScanStartTime() { return scanStartTime; }
        public void setScanStartTime(Long scanStartTime) { this.scanStartTime = scanStartTime; }
        public String getScanResult() { return scanResult; }
        public void setScanResult(String scanResult) { this.scanResult = scanResult; }
        public Long getProcessingStartTime() { return processingStartTime; }
        public void setProcessingStartTime(Long processingStartTime) { this.processingStartTime = processingStartTime; }
        public String getProcessor() { return processor; }
        public void setProcessor(String processor) { this.processor = processor; }
        public String getOutputFile() { return outputFile; }
        public void setOutputFile(String outputFile) { this.outputFile = outputFile; }
    }
}