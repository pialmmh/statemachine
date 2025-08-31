# Debug Mode & WebSocket Integration

## Overview
The state machine library has **built-in debug mode** with WebSocket server integration for real-time monitoring. This feature is:
- ✅ Built into the generic `AbstractStateMachineRegistry`
- ✅ Available for ANY state machine type (not just calls)
- ✅ Automatically launches WebSocket server when enabled
- ✅ Provides real-time state updates to connected UI clients

## How to Enable Debug Mode

### ✅ RECOMMENDED: Configure via Builder (NEW)
```java
StateMachineLibrary<Entity, Context> library = StateMachineLibraryBuilder
    .<Entity, Context>create("my-system")
    .registryConfig()
        .targetTps(1000)
        .webSocketPort(9999)  // Enables debug mode with WebSocket
        // OR
        .debugMode(true)      // Enable with default port (9999)
        .done()
    // ... rest of configuration ...
    .build();

// Debug mode starts automatically when library is built!
```

### Alternative Methods (Not Recommended)

#### Method 1: Via StateMachineLibrary (After Creation)
```java
library.enableDebugMode(9999);  // Can still call after creation
```

#### Method 2: Via Registry (Lower Level)
```java
StateMachineRegistry registry = new StateMachineRegistry("my-registry");
registry.enableDebugMode(9999);
```

## What Happens When Debug Mode is Enabled

1. **WebSocket Server Starts**
   - Launches on specified port (default: 9999)
   - URL: `ws://localhost:9999`
   - Handles multiple client connections

2. **History Tracking Activates**
   - Creates MySQL history tables for each machine
   - Records all state transitions
   - Broadcasts updates to connected clients

3. **Real-time Broadcasting**
   - State changes
   - Machine lifecycle events (create/destroy)
   - Transition history
   - Tree view data
   - Event metadata

## Features Available in Debug Mode

### For ANY State Machine Type:
- **Real-time Monitoring**: See state changes as they happen
- **Tree View**: Hierarchical visualization of state machines
- **Event Viewer**: Complete transition history
- **Machine Lifecycle**: Track creation and removal
- **Offline Machine Cache**: Debug machines even after removal
- **Performance Metrics**: When configured in registry

## WebSocket Message Types

The server broadcasts these message types:
- `REGISTRY_CREATE`: Machine created
- `REGISTRY_REMOVE`: Machine removed  
- `STATE_CHANGE`: State transition occurred
- `HISTORY_UPDATE`: History data changed
- `TREE_UPDATE`: Tree view structure changed
- `EVENT_METADATA`: Available events for machines

## Connecting the UI

1. **Start the Backend with Debug Mode**:
```bash
# Run your application with debug mode enabled
mvn exec:java -Dexec.mainClass="YourMainClass"
```

2. **Start the React UI**:
```bash
cd statemachine-ui-react
npm start  # Runs on http://localhost:4001
```

3. **UI Auto-connects to WebSocket**:
- The UI automatically connects to `ws://localhost:9999`
- Shows connection status
- Displays real-time updates

## Example Test

Run the included test to see it in action:
```bash
mvn exec:java -Dexec.mainClass="com.telcobright.statemachine.test.DebugModeWebSocketTest" -Dexec.classpathScope=test
```

This test:
1. Creates a call machine library
2. Enables debug mode on port 9999
3. Creates sample state machines
4. Shows WebSocket broadcasting in action

## Generic Architecture

The debug/WebSocket infrastructure is **NOT specific to call machines**. It's built into:

- `AbstractStateMachineRegistry` - Base class with WebSocket support
- `StateMachineRegistry` - Concrete implementation
- `StateMachineLibrary` - High-level API

This means ANY new state machine type automatically gets:
- WebSocket monitoring in debug mode
- Real-time UI updates
- History tracking
- Tree view visualization

## Production vs Development Configuration

### Development Configuration
```java
// Development: Enable debug mode for monitoring
var library = StateMachineLibraryBuilder.<Entity, Context>create("dev-system")
    .registryConfig()
        .targetTps(100)
        .webSocketPort(9999)  // ✅ Enable debug mode
        .done()
    // ... configuration ...
    .build();
```

### Production Configuration
```java
// Production: No debug mode for maximum performance
var library = StateMachineLibraryBuilder.<Entity, Context>create("prod-system")
    .registryConfig()
        .targetTps(2000)
        .maxConcurrentMachines(10000)
        .enablePerformanceMetrics(true)
        // ❌ No debug mode configuration
        .done()
    // ... configuration ...
    .build();
```

### Using CallMachine with Debug Mode
```java
// Development with debug
CallMachine devMachine = new CallMachine(
    "dev-calls",     // systemId
    100,            // targetCPS
    500,            // maxConcurrentCalls
    true,           // debugMode
    9999            // webSocketPort
);

// Production without debug
CallMachine prodMachine = new CallMachine(
    "prod-calls",    // systemId
    2000,           // targetCPS
    10000           // maxConcurrentCalls
    // No debug parameters - uses defaults (false, 0)
);
```

## Production Considerations

- Debug mode should be **disabled in production** for performance
- WebSocket server adds overhead for broadcasting
- History tracking creates MySQL tables per machine
- Use only for development and testing
- Configure debug mode via builder, not after creation

## Configuration Options

```java
// Registry with custom WebSocket port
StateMachineRegistry registry = new StateMachineRegistry(
    "my-registry",
    timeoutManager,
    9999  // WebSocket port
);

// Enable with custom port
registry.enableDebugMode(8080);
```

## Summary

✅ **Built-in Feature**: Debug mode with WebSocket is built into the generic registry  
✅ **Universal**: Works with ANY state machine type  
✅ **Automatic**: WebSocket server launches automatically  
✅ **Real-time**: Provides live updates to connected UI clients  
✅ **Complete**: Includes history, tree view, and event tracking  

The infrastructure is ready for production use and can handle any new state machine implementations without modification!