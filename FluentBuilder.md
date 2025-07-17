# Fluent State Machine Builder

This document explains how to use the FluentStateMachineBuilder for creating state machines with an expressive, fluent API.

## Overview

The FluentStateMachineBuilder provides a more expressive and readable way to configure state machines compared to the basic wrapper approach. It supports:

- **Fluent method chaining** for better readability
- **Type-safe event handling** with event classes
- **State-specific configuration** with entry/exit actions
- **Timeout configuration** per state
- **Offline state management**
- **Clear separation** between state configuration and transitions

## Basic Usage

```java
GenericStateMachine machine = FluentStateMachineBuilder
    .create("machine-id")
    .initialState("INITIAL")

    .state("INITIAL")
        .onEntry((ctx, e) -> System.out.println("Entering initial state"))
        .onExit((ctx, e) -> System.out.println("Leaving initial state"))
        .on("START").target("RUNNING")
        .done()

    .state("RUNNING")
        .timeout(30, TimeUnit.SECONDS)
        .onEntry((ctx, e) -> System.out.println("System is running"))
        .on("STOP").target("STOPPED")
        .done()

    .state("STOPPED")
        .onEntry((ctx, e) -> System.out.println("System stopped"))
        .offline()
        .done()

    .buildAndStart();
```

## API Reference

### Builder Methods

- `create(String id)` - Create a new builder with machine ID
- `initialState(String state)` - Set the initial state
- `finalState(String state)` - Set the final state (optional)
- `withTimeoutEventType(Class<? extends StateMachineEvent> eventType)` - Set global timeout event type
- `state(String stateId)` - Start configuring a state
- `build()` - Build the state machine
- `buildAndStart()` - Build and start the state machine

### State Configuration Methods

- `timeout(long duration, TimeUnit unit)` - Set timeout for the state
- `onTimeout(String targetState, BiConsumer<GenericStateMachine, StateMachineEvent> action)` - Configure timeout behavior
- `onEntry(BiConsumer<GenericStateMachine, StateMachineEvent> action)` - Set entry action
- `onExit(BiConsumer<GenericStateMachine, StateMachineEvent> action)` - Set exit action
- `on(String eventName)` - Define transition on string event
- `on(Class<? extends StateMachineEvent> eventType)` - Define transition on event class
- `offline()` - Mark state as offline
- `done()` - Finish state configuration and return to builder

### Transition Configuration Methods

- `target(String targetState)` - Set the target state for the transition

## Event-Driven Transitions

The fluent builder clearly shows how **events drive state transitions**:

```java
.state("IDLE")
    .on("INCOMING_CALL").target("RINGING")  // Event → New State
    .done()

.state("RINGING")
    .on("ANSWER").target("CONNECTED")       // Event → New State
    .on("TIMEOUT").target("MISSED")         // Event → New State
    .done()
```

### How It Works

1. **State Configuration**: `.state("IDLE")` configures what happens in the IDLE state
2. **Event Mapping**: `.on("INCOMING_CALL").target("RINGING")` maps the event to a state transition
3. **Event Firing**: `machine.fire("INCOMING_CALL")` triggers the transition

This makes it crystal clear that:

- **States** define behavior (entry/exit actions, timeouts)
- **Events** trigger transitions between states
- **Transitions** are rules that map (current_state, event) → new_state

## Complete Call Session Example

```java
GenericStateMachine callSession = FluentStateMachineBuilder
    .create("call-session-1")
    .initialState("IDLE")

    .state("IDLE")
        .onEntry((ctx, e) -> System.out.println("📞 Ready for calls"))
        .onExit((ctx, e) -> System.out.println("📱 Leaving idle"))
        .on("INCOMING_CALL").target("RINGING")
        .done()

    .state("RINGING")
        .timeout(30, TimeUnit.SECONDS)
        .onEntry((ctx, e) -> System.out.println("📞 Phone ringing"))
        .on("ANSWER").target("CONNECTED")
        .on("TIMEOUT").target("MISSED")
        .done()

    .state("CONNECTED")
        .onEntry((ctx, e) -> System.out.println("✅ Call connected"))
        .onExit((ctx, e) -> System.out.println("📴 Call ending"))
        .on("HANGUP").target("HUNGUP")
        .done()

    .state("HUNGUP")
        .onEntry((ctx, e) -> System.out.println("📵 Call ended"))
        .done()

    .state("MISSED")
        .onEntry((ctx, e) -> System.out.println("📵 Missed call"))
        .offline()
        .done()

    .buildAndStart();

// Fire events to drive transitions
callSession.fire("INCOMING_CALL");  // IDLE → RINGING
callSession.fire("ANSWER");         // RINGING → CONNECTED
callSession.fire("HANGUP");         // CONNECTED → HUNGUP
```

## Comparison with Basic Wrapper

### Basic Wrapper Style

```java
StateMachineWrapper wrapper = StateMachineFactory.createWrapper("call-1")
    .initialState("IDLE")
    .state("IDLE",
        () -> System.out.println("Entry"),
        () -> System.out.println("Exit"))
    .state("RINGING", new TimeoutConfig(30, TimeUnit.SECONDS, "TIMEOUT"))
    .transition("IDLE", "INCOMING_CALL", "RINGING")
    .buildAndStart();
```

### Fluent Builder Style

```java
GenericStateMachine machine = FluentStateMachineBuilder
    .create("call-1")
    .initialState("IDLE")

    .state("IDLE")
        .onEntry((ctx, e) -> System.out.println("Entry"))
        .onExit((ctx, e) -> System.out.println("Exit"))
        .on("INCOMING_CALL").target("RINGING")
        .done()

    .state("RINGING")
        .timeout(30, TimeUnit.SECONDS)
        .done()

    .buildAndStart();
```

The fluent builder provides:

- **Better readability** - each state's configuration is grouped together
- **Clear event-to-state mapping** - `.on("EVENT").target("STATE")` is explicit
- **Type safety** - can use event classes instead of strings
- **More expressive** - each state's behavior is clearly defined

## Benefits

1. **Clarity**: State configuration and transitions are clearly separated
2. **Readability**: The fluent API reads like a specification
3. **Maintainability**: Easy to see all aspects of a state in one place
4. **Type Safety**: Support for both string events and typed event classes
5. **IDE Support**: Better autocompletion and refactoring support

The fluent builder makes it immediately obvious how events drive state transitions, which was the core concept you wanted to understand!
