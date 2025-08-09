package com.telcobright.statemachine.test;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.events.GenericStateMachineEvent;
import com.telcobright.statemachine.monitoring.*;

/**
 * Quick demonstration of how to use monitoring and generate reports.
 * This is the simplest possible example to get started.
 */
public class QuickMonitoringDemo {
    
    public static void main(String[] args) {
        System.out.println("🚀 Quick Monitoring Demo");
        System.out.println("========================");
        
        // Step 1: Create your entities
        SimpleEntity entity = new SimpleEntity("demo-001");
        SimpleContext context = new SimpleContext();
        
        // Step 2: Create state machine with monitoring
        System.out.println("📋 Creating state machine with monitoring...");
        GenericStateMachine<SimpleEntity, SimpleContext> machine = FluentStateMachineBuilder
            .<SimpleEntity, SimpleContext>create("demo-machine")
            .enableDebug() // This enables monitoring!
            .withRunId("quick-demo-run")
            .initialState("START")
            .finalState("END")
            
            .state("START")
                .on("PROCESS").to("WORKING")
            .done()
            
            .state("WORKING") 
                .on("FINISH").to("END")
            .done()
            
            .state("END")
                .finalState()
            .done()
            
            .build();
        
        machine.setPersistingEntity(entity);
        machine.setContext(context);
        
        // Step 3: Run your workflow
        System.out.println("⚙️ Executing workflow...");
        machine.start();
        
        // Fire first event
        machine.fire(new GenericStateMachineEvent("PROCESS"));
        context.setStatus("Processing started");
        context.setItemsProcessed(50);
        
        // Fire second event  
        machine.fire(new GenericStateMachineEvent("FINISH"));
        context.setStatus("Completed");
        context.setItemsProcessed(100);
        
        machine.stop();
        
        // Step 4: Generate HTML report
        System.out.println("📊 Generating HTML report...");
        DefaultSnapshotRecorder<SimpleEntity, SimpleContext> recorder = 
            (DefaultSnapshotRecorder) machine.getSnapshotRecorder();
        
        recorder.generateHtmlViewer("demo-machine", "quick_demo_report.html");
        
        // Step 5: Instructions for viewing
        System.out.println("");
        System.out.println("✅ Demo completed successfully!");
        System.out.println("📄 Report generated: quick_demo_report.html");
        System.out.println("");
        System.out.println("🌐 To view the report:");
        System.out.println("   1. Open quick_demo_report.html in your web browser");
        System.out.println("   2. Click on any timeline entry to see detailed information");
        System.out.println("   3. Explore event payloads, context changes, and status info");
        System.out.println("");
        System.out.println("💡 What you'll see in the report:");
        System.out.println("   • State flow diagram: START → WORKING → END");
        System.out.println("   • Timeline with 2 transitions");
        System.out.println("   • Event details for PROCESS and FINISH events");
        System.out.println("   • Context changes showing status and item counts");
        System.out.println("   • Machine status and registry information");
        
        // Show the recorded data summary
        System.out.println("");
        System.out.println("📈 Monitoring Summary:");
        System.out.println("   • Machine ID: " + machine.getId());
        System.out.println("   • Debug enabled: " + machine.isDebugEnabled());
        System.out.println("   • Final state: " + machine.getCurrentState());
        System.out.println("   • Machine completed: " + machine.isComplete());
        System.out.println("   • Run ID: " + machine.getRunId());
    }
    
    // Simple entity for demo
    public static class SimpleEntity implements StateMachineContextEntity<String> {
        private String id;
        private boolean complete = false;
        
        public SimpleEntity(String id) {
            this.id = id;
        }
        
        @Override
        public boolean isComplete() { return complete; }
        @Override
        public void setComplete(boolean complete) { this.complete = complete; }
        
        public String getId() { return id; }
    }
    
    // Simple context for demo
    public static class SimpleContext {
        private String status = "Initial";
        private int itemsProcessed = 0;
        private long timestamp = System.currentTimeMillis();
        
        // Getters and setters
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getItemsProcessed() { return itemsProcessed; }
        public void setItemsProcessed(int itemsProcessed) { this.itemsProcessed = itemsProcessed; }
        public long getTimestamp() { return timestamp; }
    }
}