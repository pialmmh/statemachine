# How to Use State Machine Monitoring & View Reports

## ⚡ Quick Start (30 seconds)

### 1. Add One Line to Enable Monitoring
```java
GenericStateMachine<YourEntity, YourContext> machine = FluentStateMachineBuilder
    .<YourEntity, YourContext>create("your-machine-id")
    .enableDebug() // ← ADD THIS LINE
    // ... rest of your configuration
    .build();
```

### 2. Generate Report After Execution
```java
DefaultSnapshotRecorder<YourEntity, YourContext> recorder = 
    (DefaultSnapshotRecorder) machine.getSnapshotRecorder();
recorder.generateHtmlViewer("your-machine-id", "report.html");
```

### 3. Open `report.html` in Your Browser
Double-click the HTML file or run:
```bash
# Mac/Linux
open report.html

# Windows  
start report.html
```

---

## 🎯 What You'll See in the Report

### **Interactive Timeline**
- Click any transition to expand details
- See state flow: `IDLE → PROCESSING → COMPLETED`
- View event types and durations

### **Comprehensive Event Details**
- **Full Event Payload**: Complete JSON of your event object
- **Event Parameters**: All event fields and custom data
- **Timing Information**: How long each transition took

### **Context Evolution**
- **Before/After Context**: See how context changes with each event
- **JSON Diff**: Visual comparison of context changes
- **Data Integrity**: SHA-256 hashes for verification

### **Status Monitoring** 
- **Machine Status**: ONLINE/OFFLINE indicators
- **Registry Status**: Registration and activity tracking  
- **State Configuration**: Which states are marked as offline

---

## 📋 Complete Working Example

Run this example to see it in action:

```bash
# Compile and run the demo
javac -cp "target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout)" \
  -d target/test-classes src/test/java/com/telcobright/statemachine/test/QuickMonitoringDemo.java

java -cp "target/test-classes:target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout)" \
  com.telcobright.statemachine.test.QuickMonitoringDemo
```

This generates `quick_demo_report.html` - open it to see the interactive report!

---

## 🔧 Available Reports

After running the tests, you'll have these example reports:

| File | Description | 
|------|-------------|
| `quick_demo_report.html` | Simple 2-transition example |
| `enhanced_machine_history.html` | Complex example with rich events |
| `call_machine_history.html` | Call state machine example |
| `order_machine_history.html` | Order processing workflow |
| `all_machines_history.html` | Combined view of multiple machines |

---

## ⚙️ Monitoring Levels

```java
// Development - captures everything
.enableDebugComprehensive()

// Standard - good balance of detail and performance  
.enableDebug()

// Production - async, minimal overhead
.enableDebugProduction()
```

---

## 🚀 Real-World Integration

### For Your Existing Code:
```java
// Before - no monitoring
GenericStateMachine<OrderEntity, OrderContext> machine = FluentStateMachineBuilder
    .<OrderEntity, OrderContext>create("order-001")
    .initialState("PENDING")
    // ... states
    .build();

// After - with monitoring
GenericStateMachine<OrderEntity, OrderContext> machine = FluentStateMachineBuilder
    .<OrderEntity, OrderContext>create("order-001")
    .enableDebug()  // ← ADD THIS
    .withRunId("production-run-" + timestamp)  // ← AND THIS
    .initialState("PENDING")
    // ... states  
    .build();

// At the end, generate report
DefaultSnapshotRecorder<OrderEntity, OrderContext> recorder = 
    (DefaultSnapshotRecorder) machine.getSnapshotRecorder();
recorder.generateHtmlViewer("order-001", "order_audit_" + timestamp + ".html");
```

---

## 🎨 Report Features

### **Visual State Flow**
```
START → [PROCESS] → WORKING → [FINISH] → END
```

### **Expandable Timeline Entries**
```
v1: START → WORKING [PROCESS] 5ms ⏱️ 2024-01-15T10:30:45
    Status: 🟢 ONLINE | Registry: REGISTERED_ACTIVE
    Event: {"eventType":"PROCESS","timestamp":1736431234567}
    Context Before: {"status":"Initial","itemsProcessed":0}
    Context After: {"status":"Processing started","itemsProcessed":50}
    
v2: WORKING → END [FINISH] 0ms ⏱️ 2024-01-15T10:30:45  
    Status: 🟢 ONLINE | Registry: REGISTERED_ACTIVE
    Event: {"eventType":"FINISH","timestamp":1736431234892}
    Context Before: {"status":"Processing started","itemsProcessed":50}
    Context After: {"status":"Completed","itemsProcessed":100}
```

### **JSON Syntax Highlighting**
All event payloads and context data are formatted with syntax highlighting for easy reading.

---

## 🏆 Benefits

✅ **Zero Code Changes** - Just add `.enableDebug()`  
✅ **Complete Audit Trail** - Every event and context change captured  
✅ **Visual Debugging** - See exactly what happened when  
✅ **Production Ready** - Async monitoring with minimal overhead  
✅ **Security Built-in** - Sensitive fields automatically redacted  
✅ **Self-Contained** - HTML reports work without external dependencies  

---

## 🔍 Troubleshooting

**No snapshots recorded?**
```java
if (!machine.isDebugEnabled()) {
    System.out.println("Debug not enabled - add .enableDebug()");
}
```

**Report file not found?**  
Check the console output for the full file path, or use absolute paths:
```java
recorder.generateHtmlViewer("machine-id", "/full/path/to/report.html");
```

**Want multiple machines in one report?**
```java
// This captures all machines that used the same recorder
recorder.generateCombinedHtmlViewer("all_machines.html");
```

---

Start with the `QuickMonitoringDemo` to see it in action, then integrate monitoring into your own state machines! 🚀