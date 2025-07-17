# Enhanced State Machine Library

This is an enhanced version of the Armin Reichert state machine library, extended with enterprise-grade features for telecom applications.

## 🚀 Key Features

### ✅ All Functional Requirements Implemented

1. **Asynchronous Persistence** - Every state transition creates a snapshot saved asynchronously
2. **Timeout per State** - States can define timeouts with real-world units (seconds, minutes)
3. **State Machine Registry** - `createOrGet(id)` method for lifecycle management
4. **Offline State Handling** - States can be marked as offline, removing machines from active registry
5. **State Entry/Exit Support** - Optional entry and exit actions for each state
6. **Snapshot Hook** - Automatic snapshot creation on every state transition
7. **External Event Dispatch** - `fire(event)` method for external event handling
8. **Rehydration Support** - Automatic restoration from persisted snapshots
9. **Manual Removal** - Registry provides `evict(id)` method for manual cleanup

## 🏗️ Architecture

### Core Components

- **`GenericStateMachine`** - Enhanced state machine with timeout, persistence, and offline support
- **`StateMachineRegistry`** - Registry managing machine lifecycle and persistence
- **`StateMachineWrapper`** - High-level fluent API for easy configuration
- **`StateMachineFactory`** - Factory for creating and managing registry instances
- **`TimeoutManager`** - Manages timeout scheduling for state machines
- **`StateMachineSnapshotRepository`** - Persistence layer (in-memory implementation provided)

### Event System

- **`StateMachineEvent`** - Base interface for all events
- **`GenericStateMachineEvent`** - General-purpose event implementation
- **`TimeoutEvent`** - Special event for timeout occurrences

### State Configuration

- **`EnhancedStateConfig`** - Rich state configuration with timeout, offline, and action support
- **`TimeoutConfig`** - Timeout configuration with duration and target state
- **`TimeUnit`** - Time units (MILLISECONDS, SECONDS, MINUTES)

## 📋 Usage Examples

### Basic State Machine

```java
StateMachineWrapper machine = StateMachineFactory.createWrapper("my-machine")
    .initialState("IDLE")
    .state("IDLE")
    .state("ACTIVE")
    .state("DONE")
    .transition("IDLE", "START", "ACTIVE")
    .transition("ACTIVE", "FINISH", "DONE")
    .buildAndStart();

machine.fire("START");
machine.fire("FINISH");
```

### State Machine with Timeouts

```java
StateMachineWrapper sms = StateMachineFactory.createWrapper("sms-delivery")
    .initialState("PENDING")
    .state("PENDING")
    .state("SENT")
    .state("WAIT_FOR_DELIVERY", new TimeoutConfig(60, TimeUnit.SECONDS, "FAILED"))
    .state("DELIVERED")
    .state("FAILED")
    .transition("PENDING", "SEND", "SENT")
    .transition("SENT", "WAIT", "WAIT_FOR_DELIVERY")
    .transition("WAIT_FOR_DELIVERY", "DELIVERED", "DELIVERED")
    .transition("WAIT_FOR_DELIVERY", "TIMEOUT", "FAILED")
    .buildAndStart();
```

### State Machine with Entry/Exit Actions

```java
StateMachineWrapper machine = StateMachineFactory.createWrapper("call-session")
    .initialState("IDLE")
    .state("IDLE",
        () -> System.out.println("Call ready"),
        () -> System.out.println("Call starting"))
    .state("RINGING",
        () -> System.out.println("Phone ringing"),
        () -> System.out.println("Call answered"))
    .state("CONNECTED")
    .transition("IDLE", "INCOMING", "RINGING")
    .transition("RINGING", "ANSWER", "CONNECTED")
    .buildAndStart();
```

### Offline States

```java
StateMachineWrapper machine = StateMachineFactory.createWrapper("session")
    .initialState("ONLINE")
    .state("ONLINE")
    .offlineState("OFFLINE")
    .transition("ONLINE", "DISCONNECT", "OFFLINE")
    .onOfflineTransition(offlineMachine ->
        System.out.println("Machine went offline: " + offlineMachine.getId()))
    .buildAndStart();
```

### Registry Operations

```java
// Create or retrieve existing machine
StateMachineWrapper machine = StateMachineFactory.createWrapper("machine-1");

// Check if machine is active
boolean isActive = StateMachineFactory.getDefaultRegistry().isActive("machine-1");

// Manually evict a machine
StateMachineFactory.getDefaultRegistry().evict("machine-1");

// Get active machine count
int activeCount = StateMachineFactory.getDefaultRegistry().getActiveCount();
```

## 🔧 Configuration

### Timeout Configuration

States can define timeouts with various time units:

```java
.state("PROCESSING", new EnhancedStateConfig("PROCESSING")
    .timeout(new TimeoutConfig(30, TimeUnit.SECONDS, "TIMEOUT"))
    .onEntry(() -> System.out.println("Processing started"))
    .onExit(() -> System.out.println("Processing completed")))
```

### Complex State Configuration

```java
.state("COMPLEX_STATE", new EnhancedStateConfig("COMPLEX_STATE")
    .timeout(new TimeoutConfig(5, TimeUnit.MINUTES, "TIMEOUT"))
    .offline(false)
    .onEntry(() -> System.out.println("Entering complex state"))
    .onExit(() -> System.out.println("Exiting complex state")))
```

## 🧪 Testing

The library includes comprehensive tests covering:

- Basic state transitions
- Entry/exit actions
- Offline state handling
- Timeout behavior
- Registry operations
- Event handling
- Persistence and rehydration

Run tests with: `mvn test`

## 🚀 Quick Start

1. Add the library to your project
2. Use `StateMachineFactory.createWrapper(id)` to create state machines
3. Configure states, transitions, and actions using the fluent API
4. Call `buildAndStart()` to start the machine
5. Use `fire(event)` to trigger transitions
6. Call `StateMachineFactory.shutdown()` when done

## 📖 Examples

See the `examples` package for complete working examples:

- **TelecomStateMachineExample** - Comprehensive examples for telecom use cases
- **Main.java** - Simple demonstration of key features
- **EnhancedStateMachineTest** - Unit tests showing all features

## 🔍 Key Benefits

1. **Asynchronous Persistence** - Non-blocking state snapshots
2. **Real-time Timeouts** - Seconds/minutes instead of ticks
3. **Offline State Management** - Automatic cleanup of inactive machines
4. **Fluent API** - Easy configuration and usage
5. **Enterprise Ready** - Built for telecom-grade applications
6. **Backward Compatible** - Existing code continues to work
7. **Comprehensive Testing** - Full test coverage of all features

## 📋 Requirements Met

✅ **Asynchronous Persistence** - Every state transition triggers async snapshot save  
✅ **Timeout per State** - States support timeout with target state configuration  
✅ **State Machine Registry** - `createOrGet(id)` with persistence integration  
✅ **Offline State Handling** - States can be marked offline, removing from active registry  
✅ **State Entry/Exit Support** - Optional entry/exit actions for each state  
✅ **Snapshot Hook** - Automatic snapshot creation on every transition  
✅ **External Event Dispatch** - `fire(event)` and `handle(event)` methods  
✅ **Rehydration Support** - Automatic restoration from persisted snapshots  
✅ **Manual Removal** - Registry provides `evict(id)` method

The enhanced state machine library is now ready for production use in telecom applications!
# statemachine
