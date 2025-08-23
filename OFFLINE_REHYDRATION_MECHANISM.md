# Offline and Rehydration Mechanism in State Machine Registry

## Overview

The state machine framework provides a sophisticated **offline/rehydration mechanism** that allows state machines to be removed from memory when they reach certain states (marked as "offline") and later restored (rehydrated) when needed. This is crucial for managing memory efficiently in systems handling thousands of state machines.

## Key Components

### 1. **Offline State Configuration**

States can be marked as "offline" during state machine definition:

```java
// From CallMachineRunnerProper.java
.state(CallState.CONNECTED)
    .timeout(120, TimeUnit.SECONDS, CallState.IDLE.name())
    .offline() // Mark as offline state - machine is removed from online registry
    .on(Hangup.class).to(CallState.IDLE)
    .done()
```

### 2. **EnhancedStateConfig Class**

The `EnhancedStateConfig` class manages state properties including the offline flag:

```java
// From EnhancedStateConfig.java
public class EnhancedStateConfig {
    private boolean isOffline = false;
    private boolean isFinal = false;
    
    public EnhancedStateConfig offline() {
        this.isOffline = true;
        return this;
    }
    
    public boolean isOffline() { 
        return isOffline; 
    }
}
```

### 3. **Offline Transition Handling**

When a state machine transitions to an offline state, it triggers a callback:

```java
// From GenericStateMachine.java
private Consumer<GenericStateMachine<TPersistingEntity, TContext>> onOfflineTransition;

private void transitionToNewState(String newState) {
    // ... transition logic ...
    
    // Check if new state is offline
    EnhancedStateConfig config = stateConfigs.get(newState);
    if (config != null && config.isOffline()) {
        if (onOfflineTransition != null) {
            onOfflineTransition.accept(this);
        }
    }
}
```

## Registry Operations

### 1. **Eviction (Going Offline)**

The registry provides an `evict()` method to remove machines from memory:

```java
// From StateMachineRegistry.java
public void evict(String id) {
    GenericStateMachine<?, ?> machine = activeMachines.remove(id);
    if (machine != null) {
        lastRemovedMachine = id; // Track as removed/offline
    }
}
```

### 2. **Rehydration (Coming Back Online)**

The registry can restore machines from persistence:

```java
// From StateMachineRegistry.java
@Override
public <T extends StateMachineContextEntity<?>> T rehydrateMachine(
        String machineId, 
        Class<T> contextClass, 
        Supplier<T> contextSupplier, 
        Function<T, GenericStateMachine<T, ?>> machineBuilder) {
    
    // Check if already in memory
    GenericStateMachine<?, ?> existing = activeMachines.get(machineId);
    if (existing != null && existing.getPersistingEntity() != null) {
        return contextClass.cast(existing.getPersistingEntity());
    }
    
    // Create new context and machine
    T context = contextSupplier.get();
    GenericStateMachine<T, ?> machine = machineBuilder.apply(context);
    register(machineId, machine);
    lastAddedMachine = machineId; // Track as rehydrated
    
    // Notify listeners
    for (StateMachineListener listener : listeners) {
        listener.onRegistryRehydrate(machineId);
    }
    
    return context;
}
```

## Complete Workflow Example

### Step 1: Define State Machine with Offline State

```java
GenericStateMachine<CallEntity, CallContext> machine = 
    FluentStateMachineBuilder.<CallEntity, CallContext>create(machineId)
        .initialState(CallState.IDLE)
        
        .state(CallState.IDLE)
            .on(IncomingCall.class).to(CallState.RINGING)
            .done()
            
        .state(CallState.RINGING)
            .offline()  // Mark as offline state
            .timeout(30, TimeUnit.SECONDS, CallState.IDLE.name())
            .on(Answer.class).to(CallState.CONNECTED)
            .on(Hangup.class).to(CallState.IDLE)
            .done()
            
        .state(CallState.CONNECTED)
            .on(Hangup.class).to(CallState.IDLE)
            .done()
        .build();
```

### Step 2: Register Offline Transition Handler

```java
machine.setOnOfflineTransition(m -> {
    // Persist the state machine
    persistEntity(m.getPersistingEntity());
    
    // Remove from registry (evict from memory)
    registry.evict(m.getId());
    
    System.out.println("Machine " + m.getId() + " went offline in state: " + 
                       m.getCurrentState());
});
```

### Step 3: Machine Goes Offline Automatically

When the machine transitions to RINGING state:
1. The offline flag is detected
2. The `onOfflineTransition` callback is triggered
3. The machine state is persisted to database
4. The machine is removed from memory

### Step 4: Rehydration When Needed

```java
// Later, when the machine is needed again
CallEntity restoredEntity = loadEntityFromDatabase(machineId);

if (restoredEntity != null) {
    // Rehydrate the machine
    CallEntity rehydratedEntity = registry.rehydrateMachine(
        machineId,
        CallEntity.class,
        () -> restoredEntity,  // Provide the restored entity
        entity -> {
            // Rebuild the state machine
            GenericStateMachine<CallEntity, CallContext> machine = 
                createCallMachine(machineId);
            machine.setPersistingEntity(entity);
            machine.setCurrentState(entity.getCurrentState());
            return machine;
        }
    );
    
    // Machine is now back in memory and ready to process events
    GenericStateMachine<?, ?> machine = registry.getMachine(machineId);
    machine.fire(new Answer());  // Continue processing
}
```

## Benefits

1. **Memory Efficiency**: Machines in idle or waiting states don't consume memory
2. **Scalability**: Can handle thousands of state machines by keeping only active ones in memory
3. **Persistence**: State is preserved across system restarts
4. **Automatic Management**: Offline transitions happen automatically based on state configuration
5. **Transparent Recovery**: Machines can be rehydrated on-demand when events arrive

## Use Cases

1. **Call State Machines**: Calls in RINGING state waiting for answer can go offline
2. **Long-Running Processes**: Processes waiting for external events can be offlined
3. **Batch Processing**: Completed or paused batch jobs can be removed from memory
4. **Session Management**: Inactive user sessions can be offlined and restored when user returns

## Testing Example

The `OfflineRehydrationTest.java` demonstrates the complete cycle:

```java
// Phase 1: Machine goes offline
machine.fire(new IncomingCall("+1-555-1234"));
// State changes to RINGING (offline state)
// Machine is automatically persisted and removed from memory

// Phase 2: Rehydration
CallEntity restored = loadFromPersistence(machineId);
GenericStateMachine rehydrated = createAndRestoreMachine(restored);
rehydrated.fire(new Answer());
// Machine continues from RINGING -> CONNECTED
```

## WebSocket Integration

The WebSocket server tracks and broadcasts offline/rehydration events:

```java
// Listeners notify WebSocket clients
@Override
public void onRegistryRehydrate(String machineId) {
    broadcastEvent("REGISTRY_REHYDRATE", machineId, null, null, null, null);
}

@Override
public void onRegistryRemove(String machineId) {
    broadcastEvent("REGISTRY_REMOVE", machineId, null, null, null, null);
}
```

This allows the UI to show:
- Last added/rehydrated machine
- Last removed/offline machine
- Current registry status

## Summary

The offline/rehydration mechanism provides automatic memory management for state machines, allowing systems to scale efficiently by keeping only active machines in memory while preserving state for inactive ones in persistent storage. The mechanism is transparent, automatic, and integrates seamlessly with the registry's lifecycle management.