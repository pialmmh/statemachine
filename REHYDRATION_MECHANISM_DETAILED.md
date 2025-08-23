# Rehydration Mechanism - Event-Driven Recovery

## Overview

Rehydration is an **event-driven process** that occurs when an event arrives for a state machine that's not currently in memory (hashtable). The registry automatically loads the machine from the database using the partitioned repository pattern.

## The Trigger: Event Arrival for Offline Machine

```java
// When an event arrives (e.g., from WebSocket, API, or timeout)
public void handleEvent(String machineId, StateMachineEvent event) {
    // Check if machine exists in memory
    GenericStateMachine machine = activeMachines.get(machineId);
    
    if (machine == null) {
        // Machine not in memory - trigger rehydration
        machine = rehydrateMachine(machineId);
    }
    
    if (machine != null) {
        machine.fire(event);
    }
}
```

## The Rehydration Process

### Step 1: Create Fresh Machine Instance

```java
// Create a new machine instance with initial configuration
GenericStateMachine<CallEntity, CallContext> machine = 
    FluentStateMachineBuilder.<CallEntity, CallContext>create(machineId)
        .initialState(CallState.IDLE)
        .state(CallState.IDLE)
            .on(IncomingCall.class).to(CallState.RINGING)
            .done()
        .state(CallState.RINGING)
            .offline()
            .timeout(30, TimeUnit.SECONDS, CallState.IDLE.name())
            .on(Answer.class).to(CallState.CONNECTED)
            .done()
        .state(CallState.CONNECTED)
            .on(Hangup.class).to(CallState.IDLE)
            .done()
        .build();
```

### Step 2: Load Persistent Context from Database

```java
// Load the persistent context from partitioned repository
SimpleCallContext loadedContext = loadCallContext(machineId);

// This uses the PartitionedRepository to fetch from database
private SimpleCallContext loadCallContext(String callId) {
    String sql = "SELECT * FROM call_contexts WHERE call_id = ?";
    try (Connection conn = dataSource.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {
        stmt.setString(1, callId);
        ResultSet rs = stmt.executeQuery();
        
        if (rs.next()) {
            SimpleCallContext context = new SimpleCallContext(
                rs.getString("call_id"),
                rs.getString("from_number"),
                rs.getString("to_number")
            );
            context.setCurrentState(rs.getString("current_state"));
            context.setLastStateChange(rs.getTimestamp("last_state_change").toLocalDateTime());
            context.setCallStatus(rs.getString("call_status"));
            context.setRingCount(rs.getInt("ring_count"));
            // ... load other fields
            return context;
        }
    }
    return null;
}
```

### Step 3: Replace Persistent Context Only

```java
// IMPORTANT: Only the persistent context is replaced
// Volatile context remains fresh/new
machine.setPersistingEntity(loadedContext);

// Volatile context stays as initially created (not loaded from DB)
// This is intentional - volatile data is meant to be transient
```

### Step 4: Restore Current State

```java
// Set the machine's current state from the loaded context
machine.restoreState(loadedContext.getCurrentState());

// OR more commonly:
machine.setCurrentState(loadedContext.getCurrentState());

// The machine is now in the same state it was when it went offline
```

### Step 5: Re-register with Registry

```java
// Add the rehydrated machine back to the registry
registry.register(machineId, machine);

// Update tracking
lastAddedMachine = machineId;  // Track as rehydrated

// Notify listeners
for (StateMachineListener listener : listeners) {
    listener.onRegistryRehydrate(machineId);
}
```

## Complete Example from Test

From `SimpleDatabaseRehydrationTest.java`:

```java
private static void testRehydrationWithTimeout(String callId) throws Exception {
    // 1. Load context from database
    SimpleCallContext loadedContext = loadCallContext(callId);
    
    System.out.println("📂 Loaded context:");
    System.out.println("   Current state: " + loadedContext.getCurrentState());
    System.out.println("   Last state change: " + loadedContext.getLastStateChange());
    
    // 2. Create new machine with same configuration
    GenericStateMachine<SimpleCallContext, Void> rehydratedMachine = 
        FluentStateMachineBuilder.<SimpleCallContext, Void>create(callId)
            .initialState(CallState.IDLE)
            .state(CallState.RINGING)
                .offline()
                .timeout(5, TimeUnit.SECONDS, CallState.IDLE.name())
                .on(Answer.class).to(CallState.CONNECTED)
                .done()
            // ... other states
            .build();
    
    // 3. Set the loaded persistent context
    rehydratedMachine.setPersistingEntity(loadedContext);
    
    // 4. Restore the state
    rehydratedMachine.restoreState(loadedContext.getCurrentState());
    
    // Machine is now rehydrated and ready to process events
    rehydratedMachine.fire(new Answer());
}
```

## Important Design Decisions

### 1. **Persistent vs Volatile Context**

- **Persistent Context**: Loaded from database, contains state that must survive restarts
- **Volatile Context**: NOT loaded, starts fresh, contains transient runtime data

```java
// Persistent context (loaded from DB)
public class CallEntity {
    String callId;        // Persisted
    String currentState;  // Persisted
    String fromNumber;    // Persisted
    String toNumber;      // Persisted
    LocalDateTime lastStateChange;  // Persisted
}

// Volatile context (NOT loaded, starts fresh)
public class CallContext {
    WebSocketSession session;  // Transient
    AudioStream stream;        // Transient
    Map<String, Object> runtimeCache;  // Transient
}
```

### 2. **Entry Actions Not Re-executed**

When restoring state, entry actions are NOT re-executed:

```java
// During rehydration
machine.restoreState("RINGING");  // Does NOT execute RINGING's entry actions

// This prevents duplicate side effects like:
// - Sending duplicate notifications
// - Starting duplicate timers
// - Creating duplicate records
```

### 3. **Timeout Check on Rehydration**

The framework checks for expired timeouts immediately upon rehydration:

```java
public void restoreState(String state) {
    // Set the state
    this.currentState = state;
    
    // Check if timeout has expired
    if (stateConfig.hasTimeout()) {
        LocalDateTime lastChange = persistingEntity.getLastStateChange();
        Duration elapsed = Duration.between(lastChange, LocalDateTime.now());
        
        if (elapsed.compareTo(timeout) > 0) {
            // Timeout expired - trigger transition
            transitionTo(timeoutTargetState);
        } else {
            // Schedule remaining timeout
            scheduleTimeout(timeout.minus(elapsed));
        }
    }
}
```

## Registry's Role in Rehydration

The registry provides the `createOrGet` pattern for automatic rehydration:

```java
public GenericStateMachine createOrGet(String id, 
                                      Supplier<GenericStateMachine> factory,
                                      Function<String, Entity> entityLoader) {
    // 1. Check memory first
    GenericStateMachine existing = activeMachines.get(id);
    if (existing != null) {
        return existing;  // Already in memory
    }
    
    // 2. Try to load entity from database
    Entity entity = entityLoader.apply(id);
    
    if (entity != null) {
        // 3. Check if machine is complete (shouldn't rehydrate)
        if (entity.isComplete()) {
            return null;  // Don't rehydrate completed machines
        }
        
        // 4. Create new machine instance
        GenericStateMachine machine = factory.get();
        
        // 5. Set persistent context
        machine.setPersistingEntity(entity);
        
        // 6. Restore state
        machine.setCurrentState(entity.getCurrentState());
        
        // 7. Register with registry
        register(id, machine);
        
        return machine;
    }
    
    // Entity doesn't exist - create brand new
    return create(id, factory);
}
```

## WebSocket Event Routing Example

When an event arrives via WebSocket for an offline machine:

```java
// From StateMachineWebSocketServer.java
private void routeEventToMachine(String machineId, String eventType, JsonObject payload) {
    // Try to get machine from registry
    GenericStateMachine machine = registry.getMachine(machineId);
    
    if (machine == null) {
        // Machine not in memory - try to rehydrate
        machine = registry.createOrGet(
            machineId,
            () -> createMachineDefinition(machineId),
            id -> loadEntityFromDatabase(id)
        );
    }
    
    if (machine != null) {
        // Machine is now in memory (either was there or just rehydrated)
        StateMachineEvent event = createEvent(eventType, payload);
        machine.fire(event);
    }
}
```

## Summary

The rehydration mechanism is:

1. **Event-driven**: Triggered when events arrive for offline machines
2. **Selective**: Only loads persistent context, not volatile context
3. **State-preserving**: Restores exact state from when machine went offline
4. **Side-effect aware**: Doesn't re-execute entry actions
5. **Timeout-aware**: Checks and handles expired timeouts immediately
6. **Automatic**: Registry handles the complexity transparently

This design ensures that state machines can be efficiently removed from memory when idle and seamlessly restored when needed, maintaining consistency while avoiding duplicate side effects.