# Grafana Dashboard Queries for State Machine History

## 🎯 **Core Visualization Queries**

### **1. State Transition Timeline**
```sql
-- Time series of state transitions for a specific machine
SELECT 
    timestamp as time,
    CONCAT(state_before, ' → ', state_after) as metric,
    transition_duration as value
FROM state_machine_snapshots
WHERE 
    machine_id = '${machine_id}'
    AND timestamp >= $__timeFrom()
    AND timestamp <= $__timeTo()
ORDER BY timestamp ASC
```

### **2. Event Flow Over Time** 
```sql
-- Event distribution timeline
SELECT 
    $__timeGroupAlias(timestamp, $__interval),
    event_type,
    COUNT(*) as count
FROM state_machine_snapshots
WHERE 
    $__timeFilter(timestamp)
    AND machine_id = '${machine_id}'
GROUP BY 1, event_type
ORDER BY 1
```

### **3. Machine Performance Metrics**
```sql
-- Average transition duration over time
SELECT 
    $__timeGroupAlias(timestamp, $__interval),
    AVG(transition_duration) as avg_duration,
    MIN(transition_duration) as min_duration,
    MAX(transition_duration) as max_duration,
    PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY transition_duration) as p95_duration
FROM state_machine_snapshots
WHERE 
    $__timeFilter(timestamp)
    AND machine_id IN (${machine_ids})
GROUP BY 1
ORDER BY 1
```

### **4. State Distribution**
```sql
-- Current state distribution across machines
SELECT 
    state_after as state,
    COUNT(DISTINCT machine_id) as machine_count
FROM state_machine_snapshots s1
WHERE 
    timestamp = (
        SELECT MAX(timestamp) 
        FROM state_machine_snapshots s2 
        WHERE s2.machine_id = s1.machine_id
    )
    AND timestamp >= $__timeFrom()
GROUP BY state_after
ORDER BY machine_count DESC
```

## 🔍 **Detailed Analysis Queries**

### **5. Complete Run History** 
```sql
-- Full timeline for a specific run ID (like XState inspector)
SELECT 
    timestamp as time,
    version,
    CONCAT(state_before, ' → ', state_after) as transition,
    event_type,
    transition_duration,
    registry_status,
    machine_online_status,
    -- Decode context for display
    convert_from(decode(context_after_json, 'base64'), 'UTF8')::json->>'callStatus' as call_status
FROM state_machine_snapshots
WHERE 
    run_id = '${run_id}'
    AND $__timeFilter(timestamp)
ORDER BY version ASC
```

### **6. Error Analysis**
```sql
-- Find problematic transitions (long duration, offline states)
SELECT 
    timestamp as time,
    machine_id,
    CONCAT(state_before, ' → ', state_after) as transition,
    transition_duration,
    CASE 
        WHEN transition_duration > 1000 THEN 'SLOW'
        WHEN machine_online_status = false THEN 'OFFLINE'
        WHEN registry_status != 'REGISTERED_ACTIVE' THEN 'REGISTRY_ISSUE'
        ELSE 'OK'
    END as issue_type
FROM state_machine_snapshots
WHERE 
    $__timeFilter(timestamp)
    AND (
        transition_duration > 1000 
        OR machine_online_status = false
        OR registry_status != 'REGISTERED_ACTIVE'
    )
ORDER BY timestamp DESC
```

### **7. Call-Specific Analysis** (for CallEntity)
```sql
-- Call flow analysis with business metrics
SELECT 
    s.timestamp as time,
    s.machine_id,
    CONCAT(s.state_before, ' → ', s.state_after) as transition,
    c.from_number,
    c.to_number,
    c.call_type,
    c.call_duration_ms / 1000.0 as call_duration_sec,
    c.ring_count,
    c.recording_enabled
FROM state_machine_snapshots s
JOIN call_entity_snapshots c ON s.id = c.snapshot_id
WHERE 
    $__timeFilter(s.timestamp)
    AND s.run_id = '${run_id}'
ORDER BY s.timestamp ASC
```

## 📊 **Dashboard Panels Configuration**

### **Panel 1: State Flow Visualization**
- **Type**: State timeline  
- **Query**: #1 (State Transition Timeline)
- **Legend**: Show transition labels
- **Colors**: Green for successful states, Red for error states

### **Panel 2: Event Heatmap**
- **Type**: Heatmap
- **Query**: #2 (Event Flow Over Time)
- **X-axis**: Time buckets
- **Y-axis**: Event types
- **Color**: Event count intensity

### **Panel 3: Performance Graph**
- **Type**: Time series
- **Query**: #3 (Machine Performance Metrics)  
- **Left Y-axis**: Average duration (ms)
- **Right Y-axis**: P95 duration (ms)
- **Thresholds**: Warning at 100ms, Critical at 500ms

### **Panel 4: Current Status**
- **Type**: Pie chart
- **Query**: #4 (State Distribution)
- **Show**: Current state distribution across all machines

### **Panel 5: Detailed Log Table**
- **Type**: Table
- **Query**: #5 (Complete Run History)
- **Columns**: Time, Version, Transition, Event, Duration, Status
- **Enable**: Row expansion for context data

## 🎛️ **Dashboard Variables**

```json
{
  "templating": {
    "list": [
      {
        "name": "machine_id",
        "type": "query",
        "query": "SELECT DISTINCT machine_id FROM state_machine_snapshots ORDER BY machine_id",
        "multi": false,
        "includeAll": false
      },
      {
        "name": "run_id", 
        "type": "query",
        "query": "SELECT DISTINCT run_id FROM state_machine_snapshots WHERE machine_id = '$machine_id' ORDER BY run_id DESC LIMIT 50",
        "multi": false,
        "includeAll": false
      },
      {
        "name": "machine_ids",
        "type": "query", 
        "query": "SELECT DISTINCT machine_id FROM state_machine_snapshots ORDER BY machine_id",
        "multi": true,
        "includeAll": true
      }
    ]
  }
}
```

## ⚡ **Performance Optimization**

### **Indexes for Fast Queries**
```sql
-- Primary indexes (already in schema)
CREATE INDEX CONCURRENTLY idx_snapshots_machine_time ON state_machine_snapshots (machine_id, timestamp DESC);
CREATE INDEX CONCURRENTLY idx_snapshots_run_id ON state_machine_snapshots (run_id, timestamp DESC);

-- Additional indexes for complex queries
CREATE INDEX CONCURRENTLY idx_snapshots_duration ON state_machine_snapshots (transition_duration) WHERE transition_duration > 100;
CREATE INDEX CONCURRENTLY idx_snapshots_status ON state_machine_snapshots (machine_online_status, registry_status);
```

### **Query Optimization Tips**
- Use `$__timeFilter(timestamp)` in all time-based queries
- Limit results with `LIMIT` clause for table panels
- Use materialized views for heavy aggregation queries
- Consider partitioning by machine_id for very large datasets

## 🔧 **Setup Steps**

1. **Create Database Schema**: Run `sql/grafana_schema.sql`
2. **Configure Data Source**: Add PostgreSQL connection in Grafana
3. **Import Dashboard**: Use the queries above to build panels
4. **Set Refresh**: Auto-refresh every 30 seconds for real-time monitoring
5. **Configure Alerts**: Set up alerts for slow transitions or offline machines

## 📱 **Real-time Monitoring**

For live monitoring, configure:
- **Auto-refresh**: 10-30 seconds
- **Live tail**: Show latest transitions at top of table
- **Alerting**: Slack/email notifications for anomalies
- **Annotations**: Mark deployment events and incidents

This setup gives you **complete XState-like visibility** into your state machine history with powerful filtering, drilling down, and real-time monitoring capabilities!