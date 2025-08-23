# Entry Actions and Offline State Transition Flow

## Complete Sequence When Entering an Offline State

When a state machine transitions to a state marked as `.offline()`, the following sequence occurs:

### 1. **State Transition Initiated**
```java
// From GenericStateMachine.java - transitionTo() method
public void transitionTo(String newState) {
    String oldState = currentState;
    
    // Step 1: Exit current state
    exitState(oldState);
    
    // Step 2: Change state
    this.currentState = newState;
    
    // Step 3: Enter new state (executes entry actions)
    enterState(newState, true, false);
    
    // Step 4: Notify state transition callback
    if (onStateTransition != null) {
        onStateTransition.accept(newState);
    }
    
    // Step 5: Persist state
    persistState();
    
    // Step 6: Check if new state is offline
    EnhancedStateConfig config = stateConfigs.get(newState);
    if (config != null && config.isOffline()) {
        if (onOfflineTransition != null) {
            onOfflineTransition.accept(this);
        }
    }
}
```

### 2. **Entry Action Execution**
```java
// From GenericStateMachine.java - enterState() method
private void enterState(String state, boolean executeEntryActions, boolean recordEntry) {
    EnhancedStateConfig config = stateConfigs.get(state);
    
    // Reset entry action tracking
    entryActionExecuted = false;
    entryActionStatus = "none";
    
    if (config != null) {
        boolean hasEntryActions = (config.getEntryAction() != null || 
                                  config.getOnEntry() != null);
        
        // Execute entry actions BEFORE offline check
        if (executeEntryActions && hasEntryActions) {
            try {
                if (config.getEntryAction() != null) {
                    config.getEntryAction().run();
                }
                if (config.getOnEntry() != null) {
                    config.getOnEntry().accept(state);
                }
                
                // Entry actions completed successfully
                entryActionExecuted = true;
                entryActionStatus = "executed";
                System.out.println("✓ Entry actions executed for state: " + state);
                
            } catch (Exception e) {
                entryActionExecuted = false;
                entryActionStatus = "failed";
                System.err.println("✗ Entry actions failed for state " + state);
            }
        }
        
        // Setup timeout (if configured)
        if (config.hasTimeout()) {
            scheduleTimeout(config.getTimeoutConfig());
        }
    }
}
```

### 3. **Offline Transition Callback**
After the entry actions complete (successfully or with failure), the offline transition callback is triggered:

```java
// This happens AFTER entry actions are complete
if (config.isOffline()) {
    if (onOfflineTransition != null) {
        onOfflineTransition.accept(this);
        // This callback typically:
        // 1. Persists the machine state to database
        // 2. Removes machine from registry (evicts from memory)
    }
}
```

## Example Flow with CallMachine

Let's trace through what happens when a CallMachine transitions to CONNECTED (marked as offline):

```java
// State definition
.state(CallState.CONNECTED)
    .timeout(120, TimeUnit.SECONDS, CallState.IDLE.name())
    .offline() // Marked as offline state
    .onEntry(() -> {
        // Entry action: Log call connection
        System.out.println("📞 Call connected!");
        // Update billing
        updateBillingSystem();
        // Start recording
        startCallRecording();
    })
    .on(Hangup.class).to(CallState.IDLE)
    .done()
```

### Execution Sequence:

1. **Event Received**: `Answer` event received while in RINGING state
2. **Exit RINGING**: Exit actions for RINGING state (if any) are executed
3. **State Changes**: Current state changes to CONNECTED
4. **Entry Actions Execute**:
   - "📞 Call connected!" is printed
   - Billing system is updated
   - Call recording starts
   - Entry action status = "executed"
5. **State Persisted**: Machine state saved to database
6. **Offline Check**: System detects CONNECTED is marked as `.offline()`
7. **Offline Callback**: 
   ```java
   machine.setOnOfflineTransition(m -> {
       // Entry actions are ALREADY COMPLETE at this point
       // Safe to remove from memory
       persistEntity(m.getPersistingEntity());
       registry.evict(m.getId());
   });
   ```
8. **Machine Removed**: Machine is evicted from registry hashtable

## Important Guarantees

### ✅ Entry Actions Complete Before Offline
The framework ensures that:
- Entry actions are **always executed** before the offline transition
- The registry only removes the machine **after** entry actions complete
- If entry actions fail, the machine still goes offline (with failed status recorded)

### ✅ State is Fully Established
When the `onOfflineTransition` callback is triggered:
- The machine is fully in the new state
- All entry actions have been attempted
- Timeouts (if any) have been scheduled
- State has been persisted

### ✅ Safe for Registry Removal
The registry can safely remove the machine because:
- All immediate work for entering the state is complete
- The state is persisted and can be rehydrated later
- Any scheduled timeouts are managed by the TimeoutManager

## Registry's Role

The registry doesn't need to "know" when entry actions complete because the sequence is deterministic:

```java
// In StateMachineRegistry
machine.setOnOfflineTransition(m -> {
    // This is ONLY called after entry actions complete
    // Registry can safely remove the machine
    
    // 1. Final persistence (with entry action results)
    persistFinalState(m.getPersistingEntity());
    
    // 2. Remove from active machines
    activeMachines.remove(m.getId());
    lastRemovedMachine = m.getId();
    
    // 3. Notify listeners (WebSocket, monitoring, etc.)
    notifyRegistryRemove(m.getId());
});
```

## Rehydration Considerations

When a machine is rehydrated:
```java
// Entry actions are NOT re-executed
enterState(restoredState, false, false); // executeEntryActions = false

// Entry action status will be "skipped"
// This prevents duplicate execution of entry actions
```

## Summary

The offline mechanism ensures a clean, safe transition:
1. **Entry actions execute first** - All state entry logic completes
2. **Then offline callback fires** - Registry is notified after entry is complete
3. **Finally machine is evicted** - Safe removal from memory

This design ensures that offline states can have complex entry logic (like starting call recording, updating databases, sending notifications) that completes before the machine is removed from memory. The registry doesn't need complex completion detection - it simply waits for the offline callback, which is guaranteed to come after entry actions.