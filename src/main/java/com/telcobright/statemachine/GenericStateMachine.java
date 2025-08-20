package com.telcobright.statemachine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachine.events.TimeoutEvent;
import com.telcobright.statemachine.state.EnhancedStateConfig;
import com.telcobright.statemachine.timeout.TimeoutManager;
import com.telcobright.statemachine.StateMachineContextEntity;
import com.telcobright.statemachine.monitoring.SnapshotRecorder;
import com.telcobright.statemachine.monitoring.SnapshotConfig;
import com.telcobright.statemachine.persistence.StateMachineSnapshotRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.telcobright.statemachine.eventstore.EventLogEntry;
import java.util.HashMap;
import com.telcobright.statemachine.history.MachineHistoryTracker;
import com.telcobright.statemachine.history.MachineHistoryMySQLTracker;
import com.telcobright.statemachine.persistence.MysqlConnectionProvider;
import java.io.IOException;
import java.sql.SQLException;

/**
 * Enhanced Generic State Machine with timeout, persistence, and offline support
 * @param <TPersistingEntity> the StateMachineContextEntity type that gets persisted - ID is the state machine ID
 * @param <TContext> the volatile context type (not persisted)
 */
public class GenericStateMachine<TPersistingEntity extends StateMachineContextEntity<?>, TContext> {
    private final String id;
    private String currentState;
    private final Map<String, EnhancedStateConfig> stateConfigs = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> transitions = new ConcurrentHashMap<>();
    private final Map<String, Map<String, BiConsumer<GenericStateMachine<TPersistingEntity, TContext>, StateMachineEvent>>> stayActions = new ConcurrentHashMap<>();
    private final TimeoutManager timeoutManager;
    private final StateMachineRegistry registry;
    private ScheduledFuture<?> currentTimeout;
    private TPersistingEntity persistingEntity;  // The entity that gets persisted
    private TContext context;  // Volatile context
    
    // Monitoring and debugging
    private boolean debugEnabled = false;
    private boolean persistSnapshotsToDb = false;
    private boolean registryControlledDebug = false;
    private SnapshotRecorder<TPersistingEntity, TContext> snapshotRecorder;
    private String runId;
    private String correlationId;
    private String debugSessionId;
    private StateMachineSnapshotRepository snapshotRepository;
    
    // Callbacks
    private Consumer<String> onStateTransition;
    private Consumer<GenericStateMachine<TPersistingEntity, TContext>> onOfflineTransition;
    
    // Entry action tracking
    private boolean entryActionExecuted = false;
    private String entryActionStatus = "none"; // "none", "executed", "skipped", "failed"
    
    // History tracking - use MySQL tracker
    private MachineHistoryMySQLTracker historyTracker;
    
    public GenericStateMachine(String id, TimeoutManager timeoutManager, StateMachineRegistry registry) {
        this.id = id;
        this.currentState = "initial";
        this.timeoutManager = timeoutManager;
        this.registry = registry;
        
        if (timeoutManager == null) {
            System.err.println("⚠️ WARNING: GenericStateMachine " + id + " created with NULL TimeoutManager!");
        } else {
            System.out.println("✓ GenericStateMachine " + id + " created with valid TimeoutManager");
        }
        
        // Auto-generate run ID based on timestamp if debug is enabled
        this.runId = generateTimestampRunId();
        
        // Initialize history tracker if debug is enabled
        initializeHistoryTracker();
    }
    
    /**
     * Define a state with configuration
     */
    public GenericStateMachine<TPersistingEntity, TContext> state(String stateId, EnhancedStateConfig config) {
        stateConfigs.put(stateId, config);
        return this;
    }
    
    /**
     * Define a transition from one state to another on an event
     */
    public GenericStateMachine<TPersistingEntity, TContext> transition(String fromState, String event, String toState) {
        transitions.computeIfAbsent(fromState, k -> new ConcurrentHashMap<>())
                   .put(event, toState);
        return this;
    }
    
    /**
     * Set the initial state
     */
    public GenericStateMachine<TPersistingEntity, TContext> initialState(String state) {
        this.currentState = state;
        return this;
    }
    
    /**
     * Fire an event to trigger state transitions
     */
    public void fire(StateMachineEvent event) {
        handleEvent(event);
    }
    
    /**
     * Fire a simple event by type
     */
    public void fire(String eventType) {
        fire(new com.telcobright.statemachine.events.GenericStateMachineEvent(eventType));
    }
    
    /**
     * Start the state machine
     */
    public void start() {
        System.out.println("StateMachine " + id + " started in state: " + currentState);
        
        // Don't record initial state here - enterState will handle it via recordStateEntry
        
        enterState(currentState, false);
    }
    
    /**
     * Restore state from persisted entity (for rehydration)
     * Checks if the state has timed out and transitions to timeout target state if necessary
     */
    public void restoreState(String state) {
        this.currentState = state;
        
        // Enhanced rehydration logging
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🔄 REHYDRATION SUCCESSFUL");
        System.out.println("=".repeat(60));
        System.out.println("📍 Machine ID: " + id);
        System.out.println("📍 Restored State: " + currentState);
        System.out.println("📍 Timestamp: " + java.time.LocalDateTime.now());
        
        // Check if this is an offline state
        EnhancedStateConfig config = stateConfigs.get(currentState);
        if (config != null && config.isOffline()) {
            System.out.println("⚠️  This is an OFFLINE state - machine was previously removed from registry");
            System.out.println("   Entry actions will be SKIPPED during rehydration");
        }
        
        if (persistingEntity != null) {
            System.out.println("📊 Persisted Entity Info:");
            System.out.println("   Last State Change: " + persistingEntity.getLastStateChange());
            System.out.println("   Is Complete: " + persistingEntity.isComplete());
        }
        System.out.println("=".repeat(60) + "\n");
        
        // Check for timeout after rehydration
        checkAndHandleTimeout();
    }
    
    /**
     * Check if the current state has timed out based on persisted lastStateChange
     * and transition to timeout target state if timeout has occurred
     */
    private void checkAndHandleTimeout() {
        if (persistingEntity == null) {
            return; // No entity to check timeout against
        }
        
        LocalDateTime lastStateChange = persistingEntity.getLastStateChange();
        if (lastStateChange == null) {
            return; // No timestamp to check against
        }
        
        // Get timeout configuration for current state
        EnhancedStateConfig stateConfig = stateConfigs.get(currentState);
        if (stateConfig == null || !stateConfig.hasTimeout()) {
            return; // No timeout configured for this state
        }
        
        // Calculate time elapsed since last state change
        LocalDateTime now = LocalDateTime.now();
        java.time.Duration elapsed = java.time.Duration.between(lastStateChange, now);
        long elapsedMillis = elapsed.toMillis();
        
        // Get timeout duration in milliseconds
        long timeoutMillis = stateConfig.getTimeoutConfig().getDurationInMillis();
        
        System.out.println("Timeout check for state " + currentState + ": elapsed=" + elapsedMillis + "ms, timeout=" + timeoutMillis + "ms");
        
        // Check if timeout has occurred
        if (elapsedMillis > timeoutMillis) {
            String targetState = stateConfig.getTimeoutConfig().getTargetState();
            System.out.println("⏰ State " + currentState + " has timed out after " + elapsedMillis + "ms. Transitioning to: " + targetState);
            
            // Fire timeout event to trigger transition
            TimeoutEvent timeoutEvent = new TimeoutEvent(currentState, targetState);
            handleEvent(timeoutEvent);
        } else {
            System.out.println("✅ State " + currentState + " has not timed out. Remaining: " + (timeoutMillis - elapsedMillis) + "ms");
        }
    }
    
    /**
     * Stop the state machine
     */
    public void stop() {
        if (currentTimeout != null) {
            currentTimeout.cancel(false);
        }
        System.out.println("StateMachine " + id + " stopped.");
    }
    
    /**
     * Transition to a new state
     */
    public void transitionTo(String newState) {
        String oldState = currentState;
        long startTime = System.currentTimeMillis();
        
        System.out.println("StateMachine " + id + " transitioning from " + oldState + " to " + newState);
        
        // Exit current state
        exitState(oldState);
        
        // Change state
        this.currentState = newState;
        
        // Enter new state (but don't record entry yet - it will be done after transition is recorded)
        enterState(newState, true, false);  // Added flag to delay entry recording
        
        // Notify callback
        if (onStateTransition != null) {
            onStateTransition.accept(newState);
        }
        
        // Create snapshot asynchronously
        // TODO: Implement ShardingEntity persistence
        persistState();
        
        // Check if new state is offline or final
        EnhancedStateConfig config = stateConfigs.get(newState);
        if (config != null) {
            if (config.isOffline()) {
                if (onOfflineTransition != null) {
                    onOfflineTransition.accept(this);
                }
            }
            
            // Check if this is a final state and mark entity as complete
            if (config.isFinal() && persistingEntity != null) {
                persistingEntity.markComplete();
                System.out.println("StateMachine " + id + " marked as complete - reached final state: " + newState);
            }
        }
        
        // State transition logging handled by MySQL history tracker
        
        // Print current state after transition
        System.out.println("📍 Current State: " + newState);
    }
    
    /**
     * Persist current state using ShardingEntity
     */
    private void persistState() {
        if (persistingEntity != null) {
            // Update state and lastStateChange fields
            persistingEntity.setCurrentState(currentState);
            persistingEntity.setLastStateChange(LocalDateTime.now());
            System.out.println("Updated entity state to: " + currentState + " at " + persistingEntity.getLastStateChange());
        }
    }
    
    /**
     * Handle incoming events with snapshot recording
     */
    private void handleEvent(StateMachineEvent event) {
        System.out.println("📨 handleEvent called for machine " + id + " with event: " + event.getClass().getSimpleName() + " (type: " + event.getEventType() + ")");
        
        // Capture before state for snapshot
        String stateBefore = currentState;
        TContext contextBefore = null;
        if (isDebugEnabled()) {
            // Create a snapshot of context before processing
            contextBefore = context; // Note: In real implementation, you might want to deep copy
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Special handling for TimeoutEvent - use its embedded target state
            if (event instanceof TimeoutEvent) {
                TimeoutEvent timeoutEvent = (TimeoutEvent) event;
                System.out.println("   ⏰ Processing TimeoutEvent:");
                System.out.println("      Source state: " + timeoutEvent.getSourceState());
                System.out.println("      Target state: " + timeoutEvent.getTargetState());
                System.out.println("      Current state: " + currentState);
                transitionTo(timeoutEvent.getTargetState());
            } else {
                // Normal event handling through transitions map
                Map<String, String> stateTransitions = transitions.get(currentState);
                if (stateTransitions != null) {
                    String targetState = stateTransitions.get(event.getEventType());
                    if (targetState != null) {
                        System.out.println("   → Found transition: " + currentState + " --[" + event.getEventType() + "]--> " + targetState);
                        transitionTo(targetState);
                    } else {
                        System.out.println("   ↻ No transition for event " + event.getEventType() + " in state " + currentState + ", checking stay actions...");
                        // Check for stay actions
                        Map<String, BiConsumer<GenericStateMachine<TPersistingEntity, TContext>, StateMachineEvent>> stateStayActions = stayActions.get(currentState);
                        if (stateStayActions != null) {
                            BiConsumer<GenericStateMachine<TPersistingEntity, TContext>, StateMachineEvent> action = stateStayActions.get(event.getEventType());
                            if (action != null) {
                                System.out.println("   ✓ Executing stay action for " + event.getEventType());
                                action.accept(this, event);
                            } else {
                                System.out.println("   ✗ No stay action found for " + event.getEventType());
                            }
                        }
                    }
                } else {
                    System.out.println("   ⚠️ No transitions defined for state " + currentState);
                }
            }
        } catch (Exception e) {
            System.err.println("   ✗ Error in handleEvent: " + e.getMessage());
            e.printStackTrace();
        } finally {
            long transitionDuration = System.currentTimeMillis() - startTime;
            
            // Record in history
            boolean transitioned = !stateBefore.equals(currentState);
            recordEventInHistory(event, stateBefore, currentState, transitionDuration, transitioned);
            
            // Record snapshot if debug is enabled
            if (isDebugEnabled()) {
                recordSnapshot(stateBefore, currentState, event, contextBefore, context, transitionDuration);
            }
        }
    }
    
    /**
     * Record a snapshot of the state transition with comprehensive status information
     */
    private void recordSnapshot(String stateBefore, String stateAfter, StateMachineEvent event, 
                               TContext contextBefore, TContext contextAfter, long transitionDuration) {
        try {
            if (snapshotRecorder != null) {
                Long version = snapshotRecorder.getNextVersion(id);
                String machineType = persistingEntity != null ? persistingEntity.getClass().getSimpleName() : this.getClass().getSimpleName();
                
                // Get registry status
                String registryStatus = getRegistryStatus();
                
                // Get machine online status (check if machine is actively running)
                boolean machineOnlineStatus = !Thread.currentThread().isInterrupted() && registry != null;
                
                // Get state offline configuration
                boolean stateOfflineStatus = isStateOffline(stateAfter);
                
                snapshotRecorder.recordTransition(
                    id, 
                    machineType,
                    version,
                    stateBefore,
                    stateAfter,
                    event,
                    contextBefore,
                    contextAfter,
                    transitionDuration,
                    runId,
                    correlationId,
                    debugSessionId,
                    machineOnlineStatus,
                    stateOfflineStatus,
                    registryStatus
                );
            }
        } catch (Exception e) {
            // Never let snapshot recording break the main flow
            System.err.println("Warning: Failed to record snapshot for machine " + id + ": " + e.getMessage());
        }
    }
    
    /**
     * Get the current registry status for this machine
     */
    private String getRegistryStatus() {
        if (registry == null) {
            return "NO_REGISTRY";
        }
        
        try {
            // Check if this machine is registered and active
            if (registry.isRegistered(id)) {
                return registry.isActive(id) ? "REGISTERED_ACTIVE" : "REGISTERED_INACTIVE";
            } else {
                return "NOT_REGISTERED";
            }
        } catch (Exception e) {
            return "REGISTRY_ERROR: " + e.getMessage();
        }
    }
    
    /**
     * Check if a state is configured as offline
     */
    private boolean isStateOffline(String stateId) {
        EnhancedStateConfig config = stateConfigs.get(stateId);
        return config != null && config.isOffline();
    }
    
    /**
     * Enter a state and execute entry actions
     */
    private void enterState(String state, boolean executeEntryActions) {
        enterState(state, executeEntryActions, true);  // Default to recording entry
    }
    
    /**
     * Enter a state and execute entry actions with control over recording
     */
    private void enterState(String state, boolean executeEntryActions, boolean recordEntry) {
        EnhancedStateConfig config = stateConfigs.get(state);
        
        // Reset entry action tracking for this state entry
        entryActionExecuted = false;
        entryActionStatus = "none";
        
        // Capture context before entry actions
        StateMachineContextEntity contextBeforeEntry = persistingEntity != null ? persistingEntity.deepCopy() : null;
        Object volatileContextBeforeEntry = context;  // Assuming volatile context is immutable or we clone it if needed
        
        if (config != null) {
            // Check if there are entry actions defined
            boolean hasEntryActions = (config.getEntryAction() != null || config.getOnEntry() != null);
            
            // Execute entry actions
            if (executeEntryActions && hasEntryActions) {
                try {
                    if (config.getEntryAction() != null) {
                        config.getEntryAction().run();
                    }
                    if (config.getOnEntry() != null) {
                        config.getOnEntry().accept(state);
                    }
                    // All entry actions executed successfully
                    entryActionExecuted = true;
                    entryActionStatus = "executed";
                    System.out.println("✓ Entry actions executed for state: " + state);
                } catch (Exception e) {
                    // Entry actions failed
                    entryActionExecuted = false;
                    entryActionStatus = "failed";
                    System.err.println("✗ Entry actions failed for state " + state + ": " + e.getMessage());
                }
            } else if (!executeEntryActions && hasEntryActions) {
                // Entry actions were skipped (e.g., during rehydration)
                entryActionExecuted = false;
                entryActionStatus = "skipped";
                System.out.println("⟳ Entry actions skipped for state: " + state + " (rehydration)");
            } else if (!hasEntryActions) {
                // No entry actions defined for this state
                entryActionStatus = "none";
                System.out.println("ℹ️ No entry actions defined for state: " + state);
            }
            
            // Record state entry in history (with before/after context if entry actions were executed)
            if (recordEntry && historyTracker != null && historyTracker.isActive()) {
                try {
                    historyTracker.recordStateEntry(state, contextBeforeEntry, volatileContextBeforeEntry,
                                                   persistingEntity, context, entryActionStatus);
                } catch (Exception e) {
                    System.err.println("[History] Error recording state entry: " + e.getMessage());
                }
            }
            
            // Setup timeout
            if (config.hasTimeout()) {
                setupTimeout(config);
            }
        } else {
            // No config for this state
            entryActionStatus = "none";
            System.out.println("ℹ️ No configuration found for state: " + state);
        }
    }
    
    /**
     * Exit a state and execute exit actions
     */
    private void exitState(String state) {
        // Cancel current timeout
        if (currentTimeout != null) {
            currentTimeout.cancel(false);
            currentTimeout = null;
        }
        
        // Execute exit actions
        EnhancedStateConfig config = stateConfigs.get(state);
        if (config != null) {
            if (config.getExitAction() != null) {
                config.getExitAction().run();
            }
            if (config.getOnExit() != null) {
                config.getOnExit().accept(state);
            }
        }
    }
    
    /**
     * Setup timeout for current state
     */
    private void setupTimeout(EnhancedStateConfig config) {
        if (currentTimeout != null) {
            currentTimeout.cancel(false);
        }
        
        if (timeoutManager == null) {
            System.err.println("⚠️ TimeoutManager is null for machine " + id + " - timeouts will not work!");
            return;
        }
        
        long timeoutMillis = config.getTimeoutConfig().getDurationInMillis();
        String targetState = config.getTimeoutConfig().getTargetState();
        
        System.out.println("⏱️ Setting up timeout for state " + currentState + 
                         " - will transition to " + targetState + " after " + 
                         (timeoutMillis/1000) + " seconds");
        
        // Capture the current state at setup time to validate later
        String stateAtSetup = currentState;
        
        currentTimeout = timeoutManager.scheduleTimeout(
            () -> {
                System.out.println("⏰ Timeout callback executing for machine " + id);
                System.out.println("   State when timeout was set: " + stateAtSetup);
                System.out.println("   Current state now: " + currentState);
                System.out.println("   Target state: " + targetState);
                
                // Only transition if we're still in the same state
                if (!currentState.equals(stateAtSetup)) {
                    System.out.println("   ⚠️ State changed since timeout was set, ignoring timeout");
                    return;
                }
                
                try {
                    System.out.println("   🔄 Firing timeout event...");
                    
                    // Timeout logging handled by MySQL history tracker
                    
                    // Fire timeout event
                    TimeoutEvent timeoutEvent = new TimeoutEvent(currentState, targetState);
                    handleEvent(timeoutEvent);
                    System.out.println("   ✓ Timeout event fired successfully");
                } catch (Exception e) {
                    System.err.println("   ✗ Error handling timeout: " + e.getMessage());
                    e.printStackTrace();
                }
            },
            timeoutMillis,
            java.util.concurrent.TimeUnit.MILLISECONDS
        );
        
        if (currentTimeout == null) {
            System.err.println("⚠️ Failed to schedule timeout!");
        } else {
            System.out.println("✓ Timeout scheduled successfully, will fire in " + (timeoutMillis/1000) + " seconds");
        }
    }
    
    
    // ===================== TEST SUPPORT METHODS =====================
    
    /**
     * Get current state (for testing)
     */
    public String getCurrentState() {
        return currentState;
    }
    
    public boolean isEntryActionExecuted() {
        return entryActionExecuted;
    }
    
    public String getEntryActionStatus() {
        return entryActionStatus;
    }
    
    /**
     * Send event to state machine (for testing)
     */
    /**
     * Define a stay action - handle an event within a state without transitioning
     */
    public GenericStateMachine<TPersistingEntity, TContext> stayAction(String stateId, String eventType, BiConsumer<GenericStateMachine<TPersistingEntity, TContext>, StateMachineEvent> action) {
        stayActions.computeIfAbsent(stateId, k -> new ConcurrentHashMap<>())
                   .put(eventType, action);
        return this;
    }

    // Getters and setters
    public String getId() { return id; }
    // Context methods (volatile, not persisted)
    public TContext getContext() { return context; }
    public void setContext(TContext context) { this.context = context; }
    
    // Persisting entity methods (gets persisted)
    public TPersistingEntity getPersistingEntity() { return persistingEntity; }
    public void setPersistingEntity(TPersistingEntity persistingEntity) { this.persistingEntity = persistingEntity; }
    
    public void setOnStateTransition(Consumer<String> callback) {
        this.onStateTransition = callback;
    }
    
    public void setOnOfflineTransition(Consumer<GenericStateMachine<TPersistingEntity, TContext>> callback) {
        this.onOfflineTransition = callback;
    }
    
    public EnhancedStateConfig getStateConfig(String stateId) {
        return stateConfigs.get(stateId);
    }
    
    public boolean isInState(String state) {
        return currentState.equals(state);
    }
    
    /**
     * Check if the state machine is complete (reached a final state)
     */
    public boolean isComplete() {
        if (persistingEntity != null) {
            return persistingEntity.isComplete();
        }
        return false;
    }
    
    /**
     * Check if the state machine is active (not complete)
     */
    public boolean isActive() {
        return !isComplete();
    }
    
    // ===================== MONITORING AND DEBUG METHODS =====================
    
    /**
     * Enable debug mode with snapshot recording
     * WARNING: This should only be called by StateMachineRegistry
     * Direct usage is deprecated - use StateMachineRegistry.enableSnapshotDebug() or enableLiveDebug() instead
     */
    @Deprecated
    public GenericStateMachine<TPersistingEntity, TContext> enableDebug(SnapshotRecorder<TPersistingEntity, TContext> snapshotRecorder) {
        if (!registryControlledDebug) {
            System.err.println("⚠️  WARNING: Direct debug mode enablement is deprecated!");
            System.err.println("   Use StateMachineRegistry.enableSnapshotDebug() or enableLiveDebug() instead.");
            System.err.println("   Machine: " + this.id);
        }
        this.debugEnabled = true;
        this.snapshotRecorder = snapshotRecorder;
        return this;
    }
    
    /**
     * Enable debug mode from registry (internal method)
     */
    /* package-private */ GenericStateMachine<TPersistingEntity, TContext> enableDebugFromRegistry(SnapshotRecorder<TPersistingEntity, TContext> snapshotRecorder) {
        this.registryControlledDebug = true;
        this.debugEnabled = true;
        this.snapshotRecorder = snapshotRecorder;
        return this;
    }
    
    /**
     * Disable debug mode
     */
    public GenericStateMachine<TPersistingEntity, TContext> disableDebug() {
        this.debugEnabled = false;
        this.snapshotRecorder = null;
        return this;
    }
    
    /**
     * Check if debug mode is enabled
     */
    public boolean isDebugEnabled() {
        return debugEnabled && snapshotRecorder != null;
    }
    
    /**
     * Set the run ID for correlation
     */
    public GenericStateMachine<TPersistingEntity, TContext> setRunId(String runId) {
        this.runId = runId;
        return this;
    }
    
    /**
     * Set the correlation ID for tracking
     */
    public GenericStateMachine<TPersistingEntity, TContext> setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
        return this;
    }
    
    /**
     * Set the debug session ID
     */
    public GenericStateMachine<TPersistingEntity, TContext> setDebugSessionId(String debugSessionId) {
        this.debugSessionId = debugSessionId;
        return this;
    }
    
    /**
     * Get the run ID
     */
    public String getRunId() {
        return runId;
    }
    
    /**
     * Get the correlation ID
     */
    public String getCorrelationId() {
        return correlationId;
    }
    
    /**
     * Get the debug session ID
     */
    public String getDebugSessionId() {
        return debugSessionId;
    }
    
    /**
     * Get the snapshot recorder
     */
    public SnapshotRecorder<TPersistingEntity, TContext> getSnapshotRecorder() {
        return snapshotRecorder;
    }
    
    /**
     * Enable database persistence for snapshots when debug mode is active
     */
    public GenericStateMachine<TPersistingEntity, TContext> enableSnapshotPersistence(StateMachineSnapshotRepository repository) {
        this.snapshotRepository = repository;
        this.persistSnapshotsToDb = true;
        return this;
    }
    
    /**
     * Auto-generate timestamp-based run ID
     */
    private String generateTimestampRunId() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        String timestamp = LocalDateTime.now().format(formatter);
        String randomSuffix = String.valueOf(System.nanoTime()).substring(8); // Last 5 digits of nanos
        return getId().toLowerCase() + "-" + timestamp + "-" + randomSuffix;
    }
    
    /**
     * Set debug mode with automatic run ID generation and optional database persistence
     * @deprecated Use StateMachineRegistry.enableSnapshotDebug() or enableLiveDebug() instead
     */
    @Deprecated
    public GenericStateMachine<TPersistingEntity, TContext> enableDebugMode(boolean enableDb) {
        this.debugEnabled = true;
        this.runId = generateTimestampRunId();
        this.persistSnapshotsToDb = enableDb;
        
        // Log the debug session info
        System.out.println("🔍 Debug mode enabled for machine: " + getId());
        System.out.println("📊 Run ID: " + this.runId);
        System.out.println("💾 Database persistence: " + (enableDb ? "ENABLED" : "DISABLED"));
        
        return this;
    }
    
    /**
     * Check if snapshot persistence to DB is enabled
     */
    public boolean isSnapshotPersistenceEnabled() {
        return persistSnapshotsToDb && snapshotRepository != null;
    }
    
    /**
     * Initialize history tracker if debug or snapshot mode is enabled
     */
    private void initializeHistoryTracker() {
        // Check if debug or snapshot mode is enabled through registry or local settings
        boolean shouldTrackHistory = false;
        
        if (registry != null && registry instanceof AbstractStateMachineRegistry) {
            AbstractStateMachineRegistry abstractRegistry = (AbstractStateMachineRegistry) registry;
            // Enable history for both debug mode and snapshot mode
            shouldTrackHistory = abstractRegistry.isDebugEnabled();
        } else {
            // Fallback to local debug setting or snapshot persistence
            shouldTrackHistory = debugEnabled || persistSnapshotsToDb;
        }
        
        if (shouldTrackHistory) {
            try {
                // Close any existing tracker for this machine
                if (historyTracker != null) {
                    historyTracker.close();
                }
                
                // Get MySQL connection provider from registry
                MysqlConnectionProvider connectionProvider = null;
                if (registry != null && registry instanceof StateMachineRegistry) {
                    StateMachineRegistry smRegistry = (StateMachineRegistry) registry;
                    connectionProvider = smRegistry.getConnectionProvider();
                }
                
                if (connectionProvider != null) {
                    // Create new MySQL history tracker
                    historyTracker = new MachineHistoryMySQLTracker(id, connectionProvider, true);
                    
                    // Initial state is already recorded in the constructor, don't duplicate it here
                } else {
                    System.err.println("[History] No MySQL connection provider available for history tracking");
                }
            } catch (SQLException e) {
                System.err.println("[History] Failed to initialize MySQL history tracker for machine " + id + ": " + e.getMessage());
                historyTracker = null;
            }
        }
    }
    
    /**
     * Record event and state transition in history
     */
    private void recordEventInHistory(StateMachineEvent event, String stateBefore, String stateAfter, 
                                     long duration, boolean transitioned) {
        if (historyTracker == null || !historyTracker.isActive()) {
            return;
        }
        
        try {
            // Record the event received
            historyTracker.recordEventReceived(stateBefore, event.getEventType(), event, persistingEntity, context);
            
            if (transitioned) {
                // Record state transition
                historyTracker.recordStateTransition(stateBefore, stateAfter, event.getEventType(), 
                                                    event, persistingEntity, context, duration, entryActionStatus);
            } else {
                // Record event with no transition
                historyTracker.recordEventNoTransition(stateBefore, event.getEventType(), 
                                                      event, persistingEntity, context, duration);
            }
        } catch (Exception e) {
            System.err.println("[History] Error recording event: " + e.getMessage());
        }
    }
    
    /**
     * Record timeout in history
     */
    private void recordTimeoutInHistory(String fromState, String toState, long timeoutDuration) {
        if (historyTracker == null || !historyTracker.isActive()) {
            return;
        }
        
        try {
            historyTracker.recordTimeout(fromState, toState, timeoutDuration, persistingEntity, context);
        } catch (Exception e) {
            System.err.println("[History] Error recording timeout: " + e.getMessage());
        }
    }
    
    /**
     * Close history tracker when machine is destroyed or completed
     */
    public void closeHistoryTracker() {
        if (historyTracker != null) {
            // Record completion if machine is in a final/complete state
            if (persistingEntity != null && persistingEntity.isComplete()) {
                historyTracker.recordCompletion(currentState, persistingEntity, context);
            }
            
            historyTracker.close();
            historyTracker = null;
        }
    }
    
    /**
     * Get the history tracker (for testing/debugging)
     */
    public MachineHistoryMySQLTracker getHistoryTracker() {
        return historyTracker;
    }
}