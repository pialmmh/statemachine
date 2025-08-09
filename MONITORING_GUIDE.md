# State Machine Monitoring & Reporting Guide

This guide explains how to enable monitoring in your state machines and view the comprehensive HTML reports.

## 🚀 Quick Start

### 1. Enable Monitoring in Your State Machine

```java
// Create a state machine with monitoring enabled
GenericStateMachine<YourEntity, YourContext> machine = FluentStateMachineBuilder
    .<YourEntity, YourContext>create("your-machine-id")
    .enableDebug() // Enable default monitoring
    .withRunId("run-2024-001")
    .withCorrelationId("correlation-123")
    // ... configure your states
    .build();

// Set your entities
machine.setPersistingEntity(yourEntity);
machine.setContext(yourContext);

// Use the machine normally
machine.start();
machine.fire(new YourEvent("EVENT_TYPE"));
```

### 2. Generate HTML Report

```java
// Get the recorder and generate report
DefaultSnapshotRecorder<YourEntity, YourContext> recorder = 
    (DefaultSnapshotRecorder) machine.getSnapshotRecorder();

// Generate report for this specific machine
recorder.generateHtmlViewer("your-machine-id", "machine_report.html");

// Or generate combined report for all machines
recorder.generateCombinedHtmlViewer("all_machines_report.html");
```

### 3. View the Report

Open the generated HTML file in any web browser:
- **Chrome/Firefox/Safari**: Double-click the HTML file
- **VS Code**: Right-click → "Open with Live Server" (if extension installed)
- **Command line**: `open machine_report.html` (Mac) or `start machine_report.html` (Windows)

---

## 📊 Report Features

### Main Dashboard
- **Machine Overview**: Type, total transitions, run ID
- **State Flow Diagram**: Visual representation of the complete state journey
- **Timeline**: Chronological list of all transitions

### Detailed View (Click any transition)
- **Status Information**: Machine online/offline, registry status, state configuration
- **Event Details**: Full event payload and parameters
- **Context Changes**: Before/after context with JSON diff
- **Timing**: Transition duration and timestamps

---

## ⚙️ Configuration Options

### Monitoring Levels

```java
// Basic monitoring (recommended for development)
.enableDebug()

// Comprehensive monitoring (captures everything)
.enableDebugComprehensive()

// Production-safe monitoring (async, minimal overhead)
.enableDebugProduction()
```

### Custom Configuration

```java
// Create custom snapshot configuration
SnapshotConfig customConfig = SnapshotConfig.defaultConfig()
    .storeBeforeJson(true)      // Capture context before transitions
    .storeAfterJson(true)       // Capture context after transitions
    .async(true)                // Use async recording
    .asyncQueueSize(1000)       // Queue size for async processing
    .redactSensitiveFields(true); // Redact passwords, tokens, etc.

DefaultSnapshotRecorder<YourEntity, YourContext> recorder = 
    new DefaultSnapshotRecorder<>(customConfig);

machine.enableDebug(recorder);
```

---

## 🎯 Complete Example

```java
public class MonitoringExample {
    
    public static void main(String[] args) {
        // 1. Create your entities
        OrderEntity order = new OrderEntity("ORDER-001", "customer-123", 299.99);
        OrderContext context = new OrderContext("ORDER-001");
        
        // 2. Create machine with monitoring
        GenericStateMachine<OrderEntity, OrderContext> machine = FluentStateMachineBuilder
            .<OrderEntity, OrderContext>create("order-machine-001")
            .enableDebugComprehensive() // Full monitoring
            .withRunId("test-run-" + System.currentTimeMillis())
            .withCorrelationId("order-correlation-001")
            .withDebugSessionId("debug-session-001")
            .initialState("PENDING")
            .finalState("COMPLETED")
            
            .state("PENDING")
                .on("VALIDATE").to("VALIDATED")
                .on("CANCEL").to("CANCELLED")
            .done()
            
            .state("VALIDATED")
                .on("PAYMENT_RECEIVED").to("PAID")
            .done()
            
            .state("PAID")
                .on("SHIP").to("SHIPPED")
            .done()
            
            .state("SHIPPED")
                .on("DELIVER").to("DELIVERED")
            .done()
            
            .state("DELIVERED")
                .on("CONFIRM").to("COMPLETED")
            .done()
            
            .state("CANCELLED")
                .finalState()
            .done()
            
            .state("COMPLETED")
                .finalState()
            .done()
            
            .build();
        
        machine.setPersistingEntity(order);
        machine.setContext(context);
        
        // 3. Execute your workflow
        machine.start();
        
        // Fire events with context updates
        machine.fire(new GenericStateMachineEvent("VALIDATE"));
        context.setValidatedTime(System.currentTimeMillis());
        
        machine.fire(new GenericStateMachineEvent("PAYMENT_RECEIVED"));
        context.setPaymentId("payment-xyz-789");
        
        machine.fire(new GenericStateMachineEvent("SHIP"));
        context.setTrackingNumber("TRACK123456789");
        
        machine.fire(new GenericStateMachineEvent("DELIVER"));
        context.setDeliveredTime(System.currentTimeMillis());
        
        machine.fire(new GenericStateMachineEvent("CONFIRM"));
        
        machine.stop();
        
        // 4. Generate and view report
        DefaultSnapshotRecorder<OrderEntity, OrderContext> recorder = 
            (DefaultSnapshotRecorder) machine.getSnapshotRecorder();
        
        recorder.generateHtmlViewer("order-machine-001", "order_report.html");
        
        System.out.println("✅ Report generated: order_report.html");
        System.out.println("📊 Open order_report.html in your browser to view the detailed report");
    }
}
```

---

## 📋 Report Sections Explained

### 1. **Machine Information Panel**
- **Machine ID**: Unique identifier for this state machine
- **Machine Type**: Class name of your entity
- **Total Transitions**: Number of state changes recorded
- **Run ID**: Your test run or correlation identifier

### 2. **State Flow Diagram**
- **Visual Flow**: Shows the complete journey from initial to final state
- **State Boxes**: Green for final states, blue for normal states
- **Event Labels**: Shows which events triggered each transition
- **Arrows**: Direction of state transitions

### 3. **Timeline (Expandable Entries)**
Each timeline entry shows:
- **Version**: Sequential number (v1, v2, v3...)
- **Transition**: FROM_STATE → TO_STATE
- **Event Badge**: Event type that triggered the transition
- **Duration**: How long the transition took
- **Status Badges**: ONLINE/OFFLINE indicators
- **Timestamp**: When the transition occurred

### 4. **Detailed View (Click to Expand)**

#### Status Information:
- **Machine Status**: ONLINE/OFFLINE - whether machine is running
- **State Mode**: ONLINE/OFFLINE - whether the state is configured as offline
- **Registry**: Registration status (REGISTERED_ACTIVE, NOT_REGISTERED, etc.)

#### Event Details:
- **Full Event Payload**: Complete JSON of the event object
- **Event Parameters**: Extracted parameters and fields
- **Syntax Highlighting**: Formatted JSON for easy reading

#### Context Changes:
- **Context Before**: State of context before the event
- **Context After**: State of context after the event
- **Hash Values**: SHA-256 hashes for integrity verification
- **Sensitive Field Redaction**: Passwords, tokens automatically hidden

---

## 🔧 Troubleshooting

### No Snapshots Recorded
```java
// Ensure debug is enabled
if (!machine.isDebugEnabled()) {
    machine.enableDebug();
}

// Check if recorder exists
if (machine.getSnapshotRecorder() == null) {
    System.out.println("No snapshot recorder configured!");
}
```

### HTML File Not Generated
```java
try {
    DefaultSnapshotRecorder<YourEntity, YourContext> recorder = 
        (DefaultSnapshotRecorder) machine.getSnapshotRecorder();
    
    if (recorder != null) {
        recorder.generateHtmlViewer("machine-id", "report.html");
        System.out.println("✅ Report generated successfully");
    } else {
        System.out.println("❌ No recorder available");
    }
} catch (Exception e) {
    System.out.println("❌ Error generating report: " + e.getMessage());
}
```

### Empty Report
- Ensure you call `machine.start()` and fire events
- Check that the machine ID matches when generating the report
- Verify that state transitions actually occurred

---

## 🎨 Report Customization

The HTML reports are self-contained with embedded CSS and JavaScript. You can:

1. **Customize Colors**: Edit the CSS in the generated HTML
2. **Add Custom Sections**: Modify the `SnapshotHistoryViewer.java` 
3. **Change Layout**: Update the HTML generation methods
4. **Export Data**: Extract JSON data from the snapshots for external tools

---

## 📈 Best Practices

### For Development:
```java
.enableDebugComprehensive() // Capture everything
.withRunId("dev-test-" + timestamp)
```

### For Testing:
```java
.enableDebug() // Standard monitoring
.withCorrelationId(testId)
```

### For Production:
```java
.enableDebugProduction() // Async, minimal overhead
.withRunId(deploymentId)
```

### Context Updates:
```java
// Update context after each event for better tracking
machine.fire(new ProcessingEvent("START"));
context.setPhase("PROCESSING");
context.setStartTime(System.currentTimeMillis());

machine.fire(new ProcessingEvent("COMPLETE"));  
context.setPhase("COMPLETED");
context.setEndTime(System.currentTimeMillis());
```

---

## 📊 Sample Generated Files

After running the examples, you'll find these HTML files:
- `enhanced_machine_history.html` - Comprehensive example with rich events
- `call_machine_history.html` - Call state machine example
- `order_machine_history.html` - Order processing example
- `all_machines_history.html` - Combined view of multiple machines

Open any of these in your browser to see the interactive monitoring reports!

---

## 🚀 Next Steps

1. **Run the Examples**: Execute the test classes to see live reports
2. **Integrate in Your Code**: Add monitoring to your existing state machines
3. **Customize Reports**: Modify the viewer to match your needs
4. **Production Deployment**: Use async monitoring for performance

The monitoring system provides complete visibility into your state machine behavior, making debugging and analysis effortless!