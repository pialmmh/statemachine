# State Machine Lifecycle Flow Diagram

## Complete Lifecycle Overview

```mermaid
graph TB
    Start([Start]) --> Create[Create State Machine]
    
    Create --> Register[Register with Registry]
    Register --> SetCallbacks[Set Callbacks:<br/>- onStateTransition<br/>- onOfflineTransition]
    SetCallbacks --> Active[Active in Memory<br/>🟢 In HashMap]
    
    Active --> Event{Event Received}
    Event -->|Normal State| ProcessEvent[Process Event]
    ProcessEvent --> StateChange[State Transition]
    StateChange --> CheckOffline{Is New State<br/>Marked .offline?}
    
    CheckOffline -->|No| Active
    CheckOffline -->|Yes| ExecuteEntry[Execute Entry Actions]
    ExecuteEntry --> TriggerOffline[Trigger onOfflineTransition]
    TriggerOffline --> Persist[Persist to MySQL]
    Persist --> Evict[Evict from Registry<br/>🔴 Remove from HashMap]
    Evict --> Offline[Offline State<br/>💾 In Database Only]
    
    Offline --> EventForOffline{Event Arrives<br/>for Offline Machine}
    EventForOffline --> CheckMemory[Check Registry HashMap]
    CheckMemory -->|Not Found| LoadFromDB[Load Context from MySQL]
    LoadFromDB --> CheckComplete{Is Complete?}
    CheckComplete -->|Yes| Reject[Reject Event<br/>Don't Rehydrate]
    CheckComplete -->|No| Rehydrate[Rehydrate Machine]
    
    Rehydrate --> CreateNew[Create New Instance<br/>with Same Config]
    CreateNew --> SetContext[Set Persistent Context<br/>from Database]
    SetContext --> RestoreState[Restore Current State<br/>without Entry Actions]
    RestoreState --> CheckTimeout{Timeout Expired?}
    CheckTimeout -->|Yes| TriggerTimeout[Trigger Timeout<br/>Transition]
    CheckTimeout -->|No| ScheduleTimeout[Schedule Remaining<br/>Timeout]
    TriggerTimeout --> RegisterRehydrated
    ScheduleTimeout --> RegisterRehydrated[Register in Registry]
    RegisterRehydrated --> Active
    
    Active --> Shutdown{System Shutdown?}
    Shutdown -->|Yes| PersistAll[Persist All Active<br/>Machines to MySQL]
    PersistAll --> End([End])
    
    Reject --> End
```

## Detailed Sub-Flows

### 1. Machine Creation Flow
```mermaid
graph LR
    A[New Event/Request] --> B{Machine Exists?}
    B -->|In Memory| C[Return Existing]
    B -->|In Database| D[Rehydrate]
    B -->|Not Found| E[Create New]
    E --> F[Initialize Context]
    F --> G[Set Initial State]
    G --> H[Register]
```

### 2. Offline Transition Flow
```mermaid
graph TD
    A[State Transition] --> B[Enter New State]
    B --> C{State has .offline?}
    C -->|No| D[Continue Normal]
    C -->|Yes| E[Complete Entry Actions]
    E --> F[Fire onOfflineTransition]
    F --> G[Save to MySQL]
    G --> H[Remove from HashMap]
    H --> I[Machine Offline]
```

### 3. Rehydration Flow
```mermaid
graph TD
    A[Event for Machine ID] --> B[Registry.getMachine]
    B -->|null| C[Check Persistence]
    C --> D[Load from MySQL]
    D -->|Found| E{Is Complete?}
    E -->|No| F[Create Machine Instance]
    F --> G[Set Loaded Context]
    G --> H[Restore State]
    H --> I[Check/Schedule Timeouts]
    I --> J[Register in HashMap]
    J --> K[Process Event]
    E -->|Yes| L[Don't Rehydrate]
```

## State Machine States

```mermaid
stateDiagram-v2
    [*] --> Created: new()
    Created --> InMemory: register()
    InMemory --> InMemory: events
    InMemory --> Offline: .offline() state
    Offline --> InMemory: rehydrate
    InMemory --> Completed: setComplete()
    Offline --> Completed: setComplete()
    Completed --> [*]
```

## Key Components Interaction

```mermaid
sequenceDiagram
    participant Client
    participant WebSocket
    participant Registry
    participant Machine
    participant MySQL
    participant TimeoutMgr
    
    Client->>WebSocket: Send Event
    WebSocket->>Registry: routeEvent(machineId, event)
    Registry->>Registry: Check HashMap
    
    alt Machine in Memory
        Registry->>Machine: fire(event)
        Machine->>Machine: Process Event
        Machine->>Registry: onStateTransition
        Registry->>WebSocket: Broadcast State Change
    else Machine Offline
        Registry->>MySQL: Load Context
        MySQL-->>Registry: Context Data
        Registry->>Registry: Check isComplete()
        opt Not Complete
            Registry->>Machine: Create New Instance
            Registry->>Machine: Set Context
            Registry->>Machine: Restore State
            Machine->>TimeoutMgr: Check/Schedule Timeouts
            Registry->>Machine: fire(event)
        end
    end
    
    opt Offline State Entered
        Machine->>Registry: onOfflineTransition
        Registry->>MySQL: Save Context
        Registry->>Registry: Evict from HashMap
    end
```

## Memory Management States

| State | In Memory | In Database | Can Process Events | Notes |
|-------|-----------|-------------|-------------------|--------|
| **Active** | ✅ Yes | ❌ No* | ✅ Yes | Normal operation |
| **Offline** | ❌ No | ✅ Yes | 🔄 Via Rehydration | Memory efficient |
| **Rehydrating** | 🔄 Loading | ✅ Yes | ⏳ After Load | Transient state |
| **Completed** | ❌ No | ✅ Yes | ❌ No | Terminal state |

*Can be persisted on shutdown or for debugging

## Lifecycle Events & Callbacks

```mermaid
graph LR
    A[Machine Lifecycle] --> B[onCreate]
    B --> C[onStateTransition]
    C --> D[onOfflineTransition]
    D --> E[onRehydrate]
    E --> F[onComplete]
    F --> G[onShutdown]
```

## Data Flow During Lifecycle

### What Gets Persisted:
- ✅ Persistent Context (Entity)
- ✅ Current State
- ✅ Last State Change
- ✅ Completion Status
- ✅ Custom Fields (JSON)

### What Starts Fresh:
- 🔄 Volatile Context
- 🔄 Event Handlers
- 🔄 Runtime Caches
- 🔄 WebSocket Connections

## Timeout Handling Across Lifecycle

```mermaid
graph TD
    A[State with Timeout] --> B[Schedule Timeout]
    B --> C{Machine Goes Offline?}
    C -->|Yes| D[Timeout Remains Scheduled]
    D --> E{Timeout Fires?}
    E -->|Machine Offline| F[Rehydrate Machine]
    F --> G[Process Timeout Event]
    C -->|No| H[Normal Timeout]
    
    I[Rehydration] --> J[Check Last State Change]
    J --> K{Timeout Expired?}
    K -->|Yes| L[Immediate Transition]
    K -->|No| M[Schedule Remaining Time]
```

## Performance Considerations

| Operation | Time Complexity | Space Impact | Database Calls |
|-----------|----------------|--------------|----------------|
| Create New | O(1) | +1 Machine | 0 |
| Fire Event (Active) | O(1) | 0 | 0 |
| Go Offline | O(1) | -1 Machine | 1 (Save) |
| Rehydrate | O(1) | +1 Machine | 1 (Load) |
| Check Complete | O(1) | 0 | 1 (Query) |
| Bulk Shutdown | O(n) | 0 | n (Save All) |

## Error Recovery

```mermaid
graph TD
    A[Error During Operation] --> B{Error Type}
    B -->|Persistence Failed| C[Log Error<br/>Keep in Memory]
    B -->|Rehydration Failed| D[Create New<br/>Log Warning]
    B -->|Event Failed| E[Log Error<br/>Stay in State]
    B -->|Timeout Failed| F[Retry Once<br/>Then Log]
```

## Best Practices

1. **Mark Appropriate States as Offline**
   - Long-running states (CONNECTED, WAITING)
   - States with minimal activity
   - States before external callbacks

2. **Don't Mark as Offline**
   - Transient states (PROCESSING)
   - States with frequent events
   - States with complex volatile data

3. **Rehydration Optimization**
   - Use ID patterns for type detection
   - Cache frequently accessed machines
   - Batch load related machines

4. **Monitoring**
   - Track rehydration frequency
   - Monitor persistence latency
   - Alert on rehydration failures