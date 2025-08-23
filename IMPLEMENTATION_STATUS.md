# Offline/Rehydration Implementation Status

## ✅ Completed Implementation

### 1. **PersistenceProvider Interface** (`PersistenceProvider.java`)
- Generic interface for persisting and loading state machine contexts
- Methods: `save()`, `load()`, `exists()`, `delete()`, `isComplete()`
- Supports any backend storage implementation

### 2. **MySQLPersistenceProvider** (`MySQLPersistenceProvider.java`)
- MySQL implementation of PersistenceProvider
- Stores contexts as JSON in database
- Automatic table creation
- Handles serialization/deserialization with Jackson
- Table schema includes: machine_id, context_class, context_data, current_state, last_state_change, is_complete

### 3. **Enhanced StateMachineRegistry** (`StateMachineRegistry.java`)
Key features implemented:
- **Automatic Persistence on Offline**: When a machine enters an offline state, it's automatically persisted to MySQL
- **Event-Driven Rehydration**: `createOrGet()` method now checks database and rehydrates machines
- **Completion Check**: Completed machines are not rehydrated
- **State Restoration**: Restored machines have their state and timeouts properly set
- **Event Routing**: New `routeEvent()` method for event-driven rehydration

### 4. **WebSocket Server Integration** (`StateMachineWebSocketServer.java`)
- Enhanced `routeEventToMachine()` to trigger rehydration
- Automatic machine creation from factory when receiving events for offline machines
- Support for call machine rehydration with proper state definition

### 5. **ShardingEntity Support**
- `ShardingEntityStateMachineRepository` for partitioned database storage
- Registry integration with `createOrGetWithSharding()` method
- Support for both ById and ByIdAndDateRange lookup modes

## 🔄 How It Works

### Offline Flow:
1. Machine transitions to state marked with `.offline()`
2. Entry actions complete
3. `onOfflineTransition` callback fires
4. Registry persists context to MySQL
5. Machine is evicted from memory (removed from hashtable)

### Rehydration Flow:
1. Event arrives for machine not in memory
2. Registry checks persistence provider
3. Context loaded from MySQL
4. New machine instance created with same configuration
5. Persistent context restored (volatile context starts fresh)
6. State restored without re-executing entry actions
7. Timeouts checked and scheduled if still valid
8. Machine registered back in registry

## 📝 Example Usage

```java
// Mark state as offline
.state(CallState.CONNECTED)
    .offline()  // This state will trigger offline storage
    .timeout(120, TimeUnit.SECONDS, CallState.IDLE.name())
    .on(Hangup.class).to(CallState.IDLE)
    .done()

// Event routing with automatic rehydration
registry.routeEvent(
    machineId,
    new Hangup(),
    () -> createMachineDefinition(machineId)  // Factory for rehydration
);
```

## 🧪 Testing

### CompleteOfflineRehydrationTest
Tests the complete flow:
- Machine creation and offline transition
- Persistence to database
- Memory eviction
- Event-driven rehydration
- State restoration
- Completion blocking

## 📊 Database Schema

```sql
CREATE TABLE state_machine_contexts (
    machine_id VARCHAR(255) PRIMARY KEY,
    context_class VARCHAR(500),
    context_data TEXT,  -- JSON serialized context
    current_state VARCHAR(100),
    last_state_change TIMESTAMP,
    is_complete BOOLEAN,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

## ⚠️ Known Limitations

1. **Factory Management**: Currently requires manual factory definition for rehydration
2. **Type Detection**: Machine type is determined by ID pattern (e.g., "call-*")
3. **Volatile Context**: Not persisted, starts fresh on rehydration
4. **Manual Configuration**: Machine configuration must be recreated exactly during rehydration

## 🚀 Future Enhancements

1. **Factory Registry**: Central registry for machine factories
2. **Configuration Persistence**: Store machine configuration alongside context
3. **Batch Rehydration**: Load multiple machines in one query
4. **Cache Layer**: Add caching between registry and database
5. **Metrics**: Track rehydration success/failure rates

## Integration Points

### With WebSocket Server
```java
// Automatic rehydration on event arrival
if (machine == null) {
    machine = registry.createOrGet(machineId, factory);
}
```

### With Timeout Manager
- Timeouts are recalculated on rehydration
- Expired timeouts trigger immediately
- Remaining time is scheduled

### With History Tracking
- History continues seamlessly after rehydration
- MySQL history tracker reconnects automatically

## Summary

The offline/rehydration mechanism is **fully implemented** and integrated:
- ✅ Automatic persistence on offline state
- ✅ Database storage with MySQL
- ✅ Event-driven rehydration
- ✅ WebSocket integration
- ✅ Timeout handling
- ✅ Completion checking

The system can now efficiently manage memory by offloading idle machines to database and seamlessly restoring them when needed.