# State Machine Framework

A high-performance, distributed state machine framework with multi-entity persistence, event-driven architecture, and comprehensive monitoring capabilities.

## Features

### Core Capabilities
- **Distributed State Management**: Support for millions of concurrent state machines
- **Multi-Entity Persistence**: Complex object graph persistence with selective ShardingEntity support
- **Event-Driven Architecture**: Asynchronous event processing with guaranteed delivery
- **Rehydration Support**: Automatic state recovery from persistent storage
- **MySQL Partitioning**: Native RANGE partitioning for time-series data optimization

### Advanced Features
- **State-Walk Library**: Higher-level state machine abstraction with event playback
- **Singleton Entity Management**: Shared entities within machine context
- **In-Memory/Persistent Modes**: Configurable persistence strategies
- **WebSocket Monitoring**: Real-time state machine debugging and visualization
- **Grafana Integration**: Production-ready metrics and dashboards

## Quick Start

### Prerequisites
- Java 17+
- MySQL 8.0+
- Maven 3.6+

### Installation

```bash
# Clone the repository
git clone https://github.com/your-org/statemachine.git
cd statemachine

# Build the project
mvn clean install

# Run tests
mvn test
```

### Basic Usage

```java
// Create a simple state machine
StateMachine<States, Events> machine = StateMachine.builder()
    .id("MACHINE-001")
    .initialState(States.IDLE)
    .transition(States.IDLE, Events.START, States.RUNNING)
    .transition(States.RUNNING, Events.STOP, States.IDLE)
    .build();

// Send events
machine.sendEvent(Events.START);
```

## API Documentation

### StateMachineRegistry

Central registry for managing state machines with persistence and rehydration support.

```java
public class StateMachineRegistry {
    // Core Methods
    public <S, E, C> StateMachine<S, E, C> getOrCreate(String id, Supplier<StateMachine<S, E, C>> supplier)
    public <S, E, C> StateMachine<S, E, C> get(String id)
    public void remove(String id)

    // Persistence Control
    public void disableRehydration(boolean disable)
    public void setPersistenceType(PersistenceType type)

    // Configuration
    public void setDatabase(String dbName)
    public void setTableRetentionDays(int days)
}
```

#### Configuration Options

| Option | Description | Default |
|--------|-------------|---------|
| `disableRehydration` | Disable automatic state recovery | `false` |
| `persistenceType` | `IN_MEMORY`, `DATABASE`, `PARTITIONED_REPO` | `DATABASE` |
| `database` | MySQL database name | Registry name |
| `tableRetentionDays` | Days to retain partition tables | `30` |

### StateMachine

Core state machine implementation with transition management and event processing.

```java
public class StateMachine<S, E, C> {
    // Builder Pattern
    public static Builder builder() { ... }

    // Event Processing
    public void sendEvent(E event)
    public void sendEventAsync(E event)

    // State Management
    public S getCurrentState()
    public C getContext()
    public void setContext(C context)

    // Lifecycle
    public void start()
    public void stop()
    public void persist()
}
```

#### Builder Configuration

```java
StateMachine<States, Events, Context> machine = StateMachine.builder()
    .id("unique-id")
    .initialState(States.IDLE)
    .context(new Context())

    // Transitions
    .transition(fromState, event, toState)
    .transition(fromState, event, toState, action)

    // Guards
    .guard(state, event, condition)

    // Actions
    .onEntry(state, action)
    .onExit(state, action)
    .onTransition(action)

    // Error Handling
    .onError(errorHandler)

    .build();
```

### Multi-Entity Persistence

Support for complex object graphs with selective persistence based on ShardingEntity interface.

```java
@Table(name = "contexts")
public class MachineContext implements ShardingEntity {
    @Id
    private String id;

    @ShardingKey
    private LocalDateTime createdAt;

    // Persisted entities (implement ShardingEntity)
    private OrderEntity order;
    private CustomerEntity customer;

    // Non-persisted (transient)
    private transient SessionInfo session;
    private transient CacheData cache;
}
```

#### ShardingEntity Requirements
- Must implement `ShardingEntity` interface
- Requires `@Id` field (String type)
- Requires `@ShardingKey` field (typically LocalDateTime)
- All entities in a machine must share the same ID

### State-Walk Library

Higher-level abstraction for state machines with enhanced features.

```java
StateWalk walk = StateWalk.builder()
    .registry("order-processing")
    .database("order_db")
    .persistenceType(PersistenceType.PARTITIONED_REPO)
    .retentionDays(90)
    .build();

// Create machine with multi-entity context
OrderMachine machine = walk.create(OrderMachine.class, "ORDER-123");
machine.process(new OrderEvent());
```

### Event Playback

Replay historical events for debugging and analysis.

```java
EventPlayer player = new EventPlayer(registry);

// Replay specific machine events
player.replay("MACHINE-001", startTime, endTime);

// Replay with filters
player.replayFiltered(filter -> filter
    .machineId("MACHINE-001")
    .eventType(Events.CRITICAL)
    .timeRange(startTime, endTime)
);
```

## Monitoring & Debugging

### WebSocket Debug Interface

Real-time monitoring via WebSocket connection:

```java
// Enable debug mode
System.setProperty("statemachine.debug", "true");

// Connect to WebSocket endpoint
ws://localhost:8080/statemachine/debug

// Message format
{
  "type": "STATE_CHANGE",
  "machineId": "MACHINE-001",
  "fromState": "IDLE",
  "toState": "RUNNING",
  "event": "START",
  "timestamp": "2025-01-27T10:30:00Z"
}
```

### Metrics & Grafana

Prometheus metrics exposed at `/metrics`:

- `statemachine_total` - Total number of state machines
- `statemachine_state_transitions_total` - State transition counter
- `statemachine_event_processing_duration` - Event processing time
- `statemachine_persistence_operations_total` - Persistence operation counter
- `statemachine_errors_total` - Error counter by type

## Database Schema

### Partitioned Tables

Automatic MySQL RANGE partitioning for time-series optimization:

```sql
CREATE TABLE IF NOT EXISTS machine_contexts (
    id VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    state VARCHAR(50),
    context JSON,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (TO_DAYS(created_at)) (
    PARTITION p_20250127 VALUES LESS THAN (TO_DAYS('2025-01-28')),
    PARTITION p_20250128 VALUES LESS THAN (TO_DAYS('2025-01-29')),
    -- ... auto-managed partitions
);
```

### Partition Management

Automatic partition lifecycle:
- Daily partition creation
- Configurable retention period
- Automatic old partition cleanup
- Zero-downtime partition operations

## Performance Optimization

### Configuration Tuning

```properties
# Connection Pool
statemachine.db.pool.size=50
statemachine.db.pool.timeout=30000

# Batch Processing
statemachine.batch.size=1000
statemachine.batch.timeout=100

# Cache Settings
statemachine.cache.enabled=true
statemachine.cache.size=10000
statemachine.cache.ttl=3600

# Partition Management
statemachine.partition.retention.days=30
statemachine.partition.cleanup.enabled=true
```

### Best Practices

1. **ID Strategy**: Use consistent ID format (e.g., `MACHINE-UUID`)
2. **Context Size**: Keep context objects lean (< 1MB)
3. **Event Batching**: Batch events for high-throughput scenarios
4. **Partition Alignment**: Align partition strategy with query patterns
5. **Connection Pooling**: Size pool based on concurrent machine count

## Testing

### Unit Tests
```bash
mvn test
```

### Integration Tests
```bash
mvn verify -P integration
```

### Performance Tests
```bash
mvn test -P performance -Dthreads=100 -Dduration=60
```

## Examples

### Call Processing State Machine
```java
CallMachine machine = CallMachine.builder()
    .id("CALL-" + UUID.randomUUID())
    .context(new CallContext())
    .build();

machine.dial("+1-555-0100");
machine.connect();
machine.hold();
machine.resume();
machine.hangup();
```

### Order Processing with Multi-Entity
```java
OrderContext context = new OrderContext("ORDER-123");
context.setCustomer(new Customer("CUST-456"));
context.setPayment(new Payment("PAY-789"));
context.addLineItem(new LineItem("ITEM-001", 99.99));

OrderMachine machine = new OrderMachine(context);
machine.process();
machine.approve();
machine.ship();
machine.complete();
```

## Troubleshooting

### Common Issues

1. **Rehydration Failures**
   - Check database connectivity
   - Verify partition exists for the time range
   - Ensure ShardingEntity implementation is correct

2. **Memory Issues**
   - Reduce context size
   - Enable context compression
   - Increase JVM heap size

3. **Performance Degradation**
   - Check partition pruning effectiveness
   - Analyze slow queries
   - Review connection pool sizing

### Debug Logging

```properties
logging.level.com.telcobright.statemachine=DEBUG
logging.level.com.telcobright.statemachine.persistence=TRACE
```

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct and the process for submitting pull requests.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Support

For issues and questions:
- GitHub Issues: [github.com/your-org/statemachine/issues](https://github.com/your-org/statemachine/issues)
- Documentation: [docs.your-org.com/statemachine](https://docs.your-org.com/statemachine)
- Email: statemachine@your-org.com