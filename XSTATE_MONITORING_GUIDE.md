# XState-like State Machine Monitoring Guide

Complete runtime history tracking with database persistence, similar to XState's history features.

## 🎯 Key Features

- **Complete Runtime History**: Track every state transition with before/after states
- **Auto-generated Run IDs**: Timestamp-based run identification  
- **Database Persistence**: Snapshots saved in partitioned repository with JSON+Base64 encoding
- **Entity-specific Snapshots**: Auto-generate `EntitySnapshot` classes (e.g., `CallEntity` → `CallEntitySnapshot`)
- **Multiple Viewing Options**: HTML viewer + integration with open source tools

## 🚀 Quick Start

### 1. Basic Debug Mode (In-Memory)

```java
GenericStateMachine<CallEntity, CallContext> machine = FluentStateMachineBuilder
    .<CallEntity, CallContext>create("my-call-machine")
    .enableDebugWithAutoRunId()  // 🎯 Auto timestamp run ID + monitoring
    .initialState("IDLE")
    // ... configure states
    .build();
```

### 2. Database Persistence Mode

```java
// Create your repository
StateMachineSnapshotRepository repository = new PartitionedRepoStateMachineSnapshotRepository(
    partitionedRepo, IdLookUpMode.ByIdAndDateRange);

// Enable database monitoring
GenericStateMachine<CallEntity, CallContext> machine = FluentStateMachineBuilder
    .<CallEntity, CallContext>create("my-call-machine")
    .enableDebugWithDatabase(repository, CallEntity.class)  // 🎯 DB persistence + entity snapshots
    .initialState("IDLE")
    // ... configure states  
    .build();
```

### 3. Manual Debug Control

```java
GenericStateMachine<CallEntity, CallContext> machine = FluentStateMachineBuilder
    .<CallEntity, CallContext>create("my-call-machine")
    .enableDebugMode(true)  // true = enable database persistence
    .withRunId("custom-run-id-2025-01-15")
    .withCorrelationId("correlation-123")
    .initialState("IDLE")
    .build();
```

## 📊 Snapshot Data Structure

Snapshots capture complete transition information:

```json
{
  "machineId": "call-machine-001",
  "version": 3,
  "runId": "call-machine-2025-01-15_14-30-25-98765", 
  "stateBefore": "RINGING",
  "stateAfter": "CONNECTED",
  "eventType": "ANSWER",
  "transitionDuration": 12,
  "timestamp": "2025-01-15T14:30:25.123",
  "machineOnlineStatus": true,
  "stateOfflineStatus": false,
  "registryStatus": "REGISTERED_ACTIVE",
  "eventPayloadJson": "base64-encoded-json-data",
  "contextBeforeJson": "base64-encoded-context-before",
  "contextAfterJson": "base64-encoded-context-after"
}
```

## 🏗️ Entity-Specific Snapshots

### Auto-Generation Pattern

When you use `enableDebugWithDatabase(repository, CallEntity.class)`, the system looks for:

**Expected Class**: `{EntityPackage}.entity.CallEntitySnapshot`

**Example**: If `CallEntity` is in `com.example.entities`, it looks for:
`com.example.entities.entity.CallEntitySnapshot`

### Creating Entity Snapshots

```java
package com.example.entities.entity;

import com.telcobright.statemachine.monitoring.AbstractMachineSnapshot;

public class CallEntitySnapshot extends AbstractMachineSnapshot {
    
    // Call-specific fields
    private String fromNumber;
    private String toNumber;
    private String callType;
    private Integer ringCount;
    
    public CallEntitySnapshot() {
        super(); // Required for reflection
    }
    
    @Override
    public String getShardingKey() {
        return getMachineId(); // Use machine ID for partitioning
    }
    
    // Add call-specific getters/setters
    public String getFromNumber() { return fromNumber; }
    public void setFromNumber(String fromNumber) { this.fromNumber = fromNumber; }
    // ... other getters/setters
}
```

## 🔧 JSON + Base64 Encoding

Snapshots are stored with dual encoding:

1. **Context/Events** → **JSON** → **Base64** → **Database**
2. **Reading** → **Base64** → **JSON** → **Objects**

This provides:
- **Efficient storage** in database columns
- **Full fidelity** of complex objects  
- **Query capability** when needed

## 📈 Open Source Monitoring Integration

### Recommended: Grafana + PostgreSQL

1. **Setup PostgreSQL** with snapshot tables
2. **Configure Grafana** data source
3. **Create dashboards** with queries:

```sql
-- State transition timeline
SELECT 
  timestamp,
  machine_id,
  state_before,
  state_after,
  event_type,
  transition_duration
FROM call_entity_snapshots 
WHERE run_id = 'your-run-id'
ORDER BY timestamp;

-- Machine performance metrics
SELECT 
  machine_id,
  COUNT(*) as total_transitions,
  AVG(transition_duration) as avg_duration,
  COUNT(DISTINCT event_type) as unique_events
FROM call_entity_snapshots
WHERE timestamp > NOW() - INTERVAL '24 hours'
GROUP BY machine_id;
```

### Alternative Tools

**Apache Superset**: SQL-based exploration and BI reports
**Jaeger**: Export as distributed tracing spans
**Custom Dashboard**: REST API + React/Vue.js for XState-like UI

## 🎮 Usage Examples

### Run the Comprehensive Demo

```bash
cd /home/mustafa/telcobright-projects/statemachine
java -cp "target/classes:target/test-classes:$(cat classpath.txt)" \
  com.telcobright.statemachine.test.ComprehensiveMonitoringDemo
```

### Generate Reports

```java
// Get the recorder from your state machine
SnapshotRecorder<CallEntity, CallContext> recorder = machine.getSnapshotRecorder();

// Generate HTML report for specific machine
recorder.generateHtmlViewer(machine.getId(), "my_machine_report.html");

// Generate combined report for all machines
recorder.generateCombinedHtmlViewer("all_machines_report.html");
```

### View Performance Metrics

```java
if (recorder instanceof DatabaseSnapshotRecorder) {
    DatabaseSnapshotRecorder<CallEntity, CallContext> dbRecorder = 
        (DatabaseSnapshotRecorder<CallEntity, CallContext>) recorder;
    
    // Print performance stats
    dbRecorder.printPerformanceMetrics();
    
    // Clear memory cache (DB records remain)
    dbRecorder.clearMemoryCache();
}
```

## 🚦 Best Practices

### 1. Production Configuration

```java
// Use production-safe config
SnapshotConfig productionConfig = SnapshotConfig.productionConfig();
productionConfig.addRedactionField("password");
productionConfig.addRedactionField("creditCard");

machine.enableDebugWithDatabase(repository, CallEntity.class, productionConfig);
```

### 2. Performance Considerations

- **Async Recording**: Enabled by default to avoid blocking transitions
- **Memory Management**: Use `clearMemoryCache()` for long-running systems  
- **Database Partitioning**: Use date-based partitioning for large datasets
- **Index Strategy**: Index on `machine_id`, `run_id`, `timestamp`

### 3. Security

- **Field Redaction**: Configure sensitive fields in `SnapshotConfig`
- **Base64 Encoding**: Provides basic obfuscation, not encryption
- **Access Control**: Secure database access and HTML report locations

## 🔍 Debugging Workflow

1. **Enable Debug**: Use `enableDebugWithAutoRunId()` during development
2. **Run Your Flow**: Execute state machine operations normally
3. **Check Console**: Look for `📸 Recorded snapshot` messages  
4. **Generate Reports**: Create HTML viewers for visualization
5. **Analyze History**: Open HTML files in browser for interactive exploration
6. **Query Database**: Use SQL for advanced analysis and custom reports

## 📝 Example Output

When debug is enabled, you'll see:

```
🔍 Debug mode enabled for machine: call-machine-example
📊 Run ID: call-machine-example-2025-01-15_14-30-25-98765
💾 Database persistence: ENABLED

📸 Recorded snapshot: call-machine-example v1 IDLE → RINGING (5ms)
📸 Recorded snapshot: call-machine-example v2 RINGING → CONNECTED (0ms)
📸 Recorded snapshot: call-machine-example v3 CONNECTED → COMPLETED (1ms)

📊 HTML viewer generated: call_machine_report_1736932825.html
📈 Total snapshots captured: 3
```

This provides XState-like complete runtime history with enterprise-grade persistence and monitoring capabilities.