# 🐳 Docker Grafana State Machine Monitoring

Complete Docker setup for XState-like state machine monitoring with Grafana dashboards.

## 🚀 Quick Start

### 1. Prerequisites
```bash
# Ensure you have these installed
docker --version
docker-compose --version
```

### 2. Start the Environment
```bash
cd /home/mustafa/telcobright-projects/statemachine/docker
docker-compose up -d
```

### 3. Verify Setup
```bash
# Check all containers are running
docker-compose ps

# Check logs if needed
docker-compose logs -f grafana
docker-compose logs -f postgres
```

### 4. Access the Services

| Service | URL | Username | Password |
|---------|-----|----------|----------|
| **Grafana Dashboard** | http://localhost:3000 | `admin` | `statemachine123` |
| **pgAdmin (Database)** | http://localhost:8080 | `admin@statemachine.com` | `pgadmin123` |
| **PostgreSQL** | `localhost:5432` | `statemachine` | `monitoring123` |

## 📊 Running Your Tests with Grafana

### Method 1: Use DockerGrafanaIntegration (Recommended)

```java
// In your test class
import com.telcobright.statemachine.monitoring.DockerGrafanaIntegration;

public class MyStateTest {
    public static void main(String[] args) throws Exception {
        // Create monitored state machine (auto-connects to Docker)
        GenericStateMachine<CallEntity, CallContext> machine = 
            DockerGrafanaIntegration
                .createMonitoredMachine("my-test-machine", CallEntity.class)
                .initialState("IDLE")
                .state("IDLE").on("START").to("RUNNING").done()
                .state("RUNNING").on("COMPLETE").to("DONE").done()
                .state("DONE").finalState().done()
                .build();
        
        machine.setPersistingEntity(new CallEntity());
        machine.setContext(new CallContext());
        
        // Run your state machine normally
        machine.start();
        machine.fire("START");
        machine.fire("COMPLETE");
        machine.stop();
        
        // Data automatically appears in Grafana!
    }
}
```

### Method 2: Run the Demo
```bash
# Add PostgreSQL driver to classpath
cd /home/mustafa/telcobright-projects/statemachine

# Run the comprehensive demo
java -cp "target/classes:target/test-classes:$(cat classpath.txt):~/.m2/repository/org/postgresql/postgresql/42.6.0/postgresql-42.6.0.jar" \
  com.telcobright.statemachine.test.DockerGrafanaDemo
```

## 🎛️ Grafana Dashboard Features

### State Machine Overview Dashboard
- **State Transition Timeline** - Visual flow of state changes
- **Event Distribution** - Pie chart of event types
- **Transition History Table** - Complete audit log
- **Performance Metrics** - Transition duration graphs
- **Summary Statistics** - Machine counts, runs, averages

### Dashboard Variables
- **Machine ID** - Filter by specific machine
- **Run ID** - View specific execution runs
- **Time Range** - Focus on specific time periods

### Key Panels

1. **State Flow Visualization**
   ```sql
   SELECT timestamp, CONCAT(state_before, ' → ', state_after) as metric, version
   FROM state_machine_snapshots 
   WHERE machine_id = '$machine_id' AND run_id = '$run_id'
   ```

2. **Performance Analysis**
   ```sql
   SELECT timestamp, transition_duration, 
          CONCAT(state_before, ' → ', state_after) as transition
   FROM state_machine_snapshots
   WHERE machine_id = '$machine_id'
   ```

3. **Event Distribution**
   ```sql
   SELECT event_type, COUNT(*) as count
   FROM state_machine_snapshots 
   GROUP BY event_type
   ```

## 🔍 Advanced Queries

### Find All Machines
```sql
SELECT DISTINCT machine_id, machine_type, 
       COUNT(*) as transitions,
       MAX(timestamp) as last_activity
FROM state_machine_snapshots 
GROUP BY machine_id, machine_type
ORDER BY last_activity DESC;
```

### Performance Analysis
```sql
SELECT 
    machine_id,
    AVG(transition_duration) as avg_ms,
    MAX(transition_duration) as max_ms,
    COUNT(CASE WHEN transition_duration > 100 THEN 1 END) as slow_transitions
FROM state_machine_snapshots
WHERE timestamp >= NOW() - INTERVAL '1 hour'
GROUP BY machine_id;
```

### State Distribution
```sql
SELECT 
    state_before,
    state_after,
    COUNT(*) as transition_count,
    AVG(transition_duration) as avg_duration
FROM state_machine_snapshots
GROUP BY state_before, state_after
ORDER BY transition_count DESC;
```

### Context Analysis (Decoded)
```sql
SELECT 
    timestamp,
    machine_id,
    state_before,
    state_after,
    convert_from(decode(context_after_json, 'base64'), 'UTF8') as context
FROM state_machine_snapshots
WHERE context_after_json IS NOT NULL
ORDER BY timestamp DESC
LIMIT 10;
```

## 🛠️ Configuration

### Environment Variables

**PostgreSQL Configuration:**
```yaml
POSTGRES_DB: statemachine_monitoring
POSTGRES_USER: statemachine
POSTGRES_PASSWORD: monitoring123
```

**Grafana Configuration:**
```yaml
GF_SECURITY_ADMIN_USER: admin
GF_SECURITY_ADMIN_PASSWORD: statemachine123
GF_AUTH_ANONYMOUS_ENABLED: true  # For easy access
GF_FEATURE_TOGGLES_ENABLE: publicDashboards
```

### Docker Compose Services

- **postgres**: PostgreSQL 15 with monitoring schema
- **grafana**: Grafana 10.2 with pre-configured dashboards  
- **pgadmin**: Database management interface

### Volumes and Persistence
- `postgres_data`: Database storage (persists between restarts)
- `grafana_data`: Dashboard and user settings
- `pgadmin_data`: pgAdmin configuration

## 🔧 Troubleshooting

### Container Issues
```bash
# Check container status
docker-compose ps

# View logs
docker-compose logs postgres
docker-compose logs grafana

# Restart services
docker-compose restart postgres grafana

# Complete reset (⚠️ deletes all data)
docker-compose down -v
docker-compose up -d
```

### Connection Issues
```bash
# Test PostgreSQL connection
docker exec -it statemachine-postgres psql -U statemachine -d statemachine_monitoring -c "SELECT COUNT(*) FROM state_machine_snapshots;"

# Test Grafana access
curl -f http://localhost:3000/api/health
```

### Database Schema Issues
```bash
# Recreate schema
docker exec -it statemachine-postgres psql -U statemachine -d statemachine_monitoring -f /docker-entrypoint-initdb.d/01-schema.sql
```

### Missing Dependencies
Add PostgreSQL JDBC driver to your project:

**Maven:**
```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.6.0</version>
</dependency>
```

**Gradle:**
```groovy
implementation 'org.postgresql:postgresql:42.6.0'
```

## 📈 Performance Optimization

### Database Tuning
```sql
-- Create additional indexes for your specific queries
CREATE INDEX IF NOT EXISTS idx_custom_machine_event 
ON state_machine_snapshots (machine_id, event_type, timestamp DESC);

-- Refresh materialized view for aggregated metrics
SELECT refresh_machine_metrics();
```

### Grafana Optimization
- Use time range filters to limit data
- Enable query caching in data source settings
- Use materialized views for heavy aggregations

## 🚦 Production Considerations

### Security
- Change default passwords
- Disable anonymous access in production
- Use environment files for secrets
- Enable SSL/TLS for database connections

### Scalability  
- Enable PostgreSQL connection pooling
- Use read replicas for Grafana queries
- Implement data retention policies
- Consider time-series databases for high-volume scenarios

### Monitoring
- Set up Grafana alerting rules
- Monitor container health and resources
- Log aggregation for troubleshooting

## 📝 Usage Examples

### Basic Test Integration
```java
@Test
public void testWithGrafanaMonitoring() throws Exception {
    GenericStateMachine<MyEntity, MyContext> machine = 
        DockerGrafanaIntegration.createMonitoredMachine("test-machine", MyEntity.class)
        // ... configure states
        .build();
    
    // Your test logic here
    machine.start();
    // ... run state machine
    machine.stop();
    
    // Check Grafana at http://localhost:3000 for results
}
```

### Custom Configuration
```java
SnapshotConfig customConfig = new SnapshotConfig();
customConfig.setAsync(false); // Synchronous for testing
customConfig.addRedactionField("password");

GenericStateMachine<MyEntity, MyContext> machine = 
    DockerGrafanaIntegration.createMonitoredMachine(
        "custom-machine", MyEntity.class, customConfig)
    // ... configure
    .build();
```

## 🎊 Success!

Once setup is complete, you'll have:

✅ **Complete State Machine History** - Every transition tracked  
✅ **Real-time Visualization** - Live Grafana dashboards  
✅ **Interactive Exploration** - Drill-down capabilities  
✅ **Performance Monitoring** - Duration and error tracking  
✅ **Business Intelligence** - Call/SMS analytics  
✅ **Automated Integration** - Zero-config data flow  

**View your dashboards at: http://localhost:3000**

This gives you XState-like complete runtime history with enterprise-grade monitoring! 🚀