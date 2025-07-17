package com.telcobright.statemachine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachine.events.TimeoutEvent;
import com.telcobright.statemachine.persistence.StateMachineSnapshotEntity;
import com.telcobright.statemachine.persistence.StateMachineSnapshotRepository;
import com.telcobright.statemachine.state.EnhancedStateConfig;
import com.telcobright.statemachine.timeout.TimeoutManager;

/**
 * Enhanced Generic State Machine with timeout, persistence, and offline support
 */
public class GenericStateMachine {
    private final String id;
    private String currentState;
    private final Map<String, EnhancedStateConfig> stateConfigs = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> transitions = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Consumer<StateMachineEvent>>> stayActions = new ConcurrentHashMap<>();
    private final StateMachineSnapshotRepository snapshotRepository;
    private final TimeoutManager timeoutManager;
    private final StateMachineRegistry registry;
    private ScheduledFuture<?> currentTimeout;
    private Object context;
    
    // Callbacks
    private Consumer<String> onStateTransition;
    private Consumer<GenericStateMachine> onOfflineTransition;
    
    public GenericStateMachine(String id, StateMachineSnapshotRepository snapshotRepository, 
                               TimeoutManager timeoutManager, StateMachineRegistry registry) {
        this.id = id;
        this.currentState = "initial";
        this.snapshotRepository = snapshotRepository;
        this.timeoutManager = timeoutManager;
        this.registry = registry;
    }
    
    /**
     * Define a state with configuration
     */
    public GenericStateMachine state(String stateId, EnhancedStateConfig config) {
        stateConfigs.put(stateId, config);
        return this;
    }
    
    /**
     * Define a transition from one state to another on an event
     */
    public GenericStateMachine transition(String fromState, String event, String toState) {
        transitions.computeIfAbsent(fromState, k -> new ConcurrentHashMap<>())
                   .put(event, toState);
        return this;
    }
    
    /**
     * Set the initial state
     */
    public GenericStateMachine initialState(String state) {
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
        enterState(currentState, false);
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
        System.out.println("StateMachine " + id + " transitioning from " + oldState + " to " + newState);
        
        // Exit current state
        exitState(oldState);
        
        // Change state
        this.currentState = newState;
        
        // Enter new state
        enterState(newState, true);
        
        // Notify callback
        if (onStateTransition != null) {
            onStateTransition.accept(newState);
        }
        
        // Create snapshot asynchronously
        createSnapshotAsync();
        
        // Check if new state is offline
        EnhancedStateConfig config = stateConfigs.get(newState);
        if (config != null && config.isOffline()) {
            if (onOfflineTransition != null) {
                onOfflineTransition.accept(this);
            }
        }
    }
    
    /**
     * Rehydrate from snapshot
     */
    public void rehydrate(StateMachineSnapshotEntity snapshot) {
        this.currentState = snapshot.getStateId();
        this.context = snapshot.getContext();
        
        // If not offline, enter the state (without firing entry actions for offline states)
        if (!snapshot.getIsOffline()) {
            enterState(currentState, false);
        }
        
        System.out.println("StateMachine " + id + " rehydrated to state: " + currentState);
    }
    
    /**
     * Handle incoming events
     */
    private void handleEvent(StateMachineEvent event) {
        Map<String, String> stateTransitions = transitions.get(currentState);
        if (stateTransitions != null) {
            String targetState = stateTransitions.get(event.getEventType());
            if (targetState != null) {
                transitionTo(targetState);
            } else {
                // Check for stay actions
                Map<String, Consumer<StateMachineEvent>> stateStayActions = stayActions.get(currentState);
                if (stateStayActions != null) {
                    Consumer<StateMachineEvent> action = stateStayActions.get(event.getEventType());
                    if (action != null) {
                        action.accept(event);
                    }
                }
            }
        }
    }
    
    /**
     * Enter a state and execute entry actions
     */
    private void enterState(String state, boolean executeEntryActions) {
        EnhancedStateConfig config = stateConfigs.get(state);
        if (config != null) {
            // Execute entry actions
            if (executeEntryActions) {
                if (config.getEntryAction() != null) {
                    config.getEntryAction().run();
                }
                if (config.getOnEntry() != null) {
                    config.getOnEntry().accept(state);
                }
            }
            
            // Setup timeout
            if (config.hasTimeout()) {
                setupTimeout(config);
            }
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
        
        currentTimeout = timeoutManager.scheduleTimeout(
            () -> {
                // Fire timeout event
                TimeoutEvent timeoutEvent = new TimeoutEvent(currentState, config.getTimeoutConfig().getTargetState());
                handleEvent(timeoutEvent);
            },
            config.getTimeoutConfig().getDuration(),
            java.util.concurrent.TimeUnit.MILLISECONDS
        );
    }
    
    /**
     * Create a snapshot asynchronously
     */
    private void createSnapshotAsync() {
        EnhancedStateConfig config = stateConfigs.get(currentState);
        boolean isOffline = config != null && config.isOffline();
        
        StateMachineSnapshotEntity snapshot = new StateMachineSnapshotEntity(
            id,
            currentState,
            context != null ? context.toString() : null,
            isOffline
        );
        
        snapshotRepository.saveAsync(snapshot);
    }
    
    // ===================== TEST SUPPORT METHODS =====================
    
    /**
     * Get current state (for testing)
     */
    public String getCurrentState() {
        return currentState;
    }
    
    /**
     * Send event to state machine (for testing)
     */
    public void sendEvent(StateMachineEvent event) {
        handleEvent(event);
    }
    
    /**
     * Create snapshot synchronously (for testing)
     */
    public StateMachineSnapshotEntity createSnapshot() {
        EnhancedStateConfig config = stateConfigs.get(currentState);
        boolean isOffline = config != null && config.isOffline();
        
        return new StateMachineSnapshotEntity(
            id,
            currentState,
            context != null ? context.toString() : null,
            isOffline
        );
    }
    
    /**
     * Restore from snapshot (for testing)
     */
    public void restoreFromSnapshot(StateMachineSnapshotEntity snapshot) {
        this.currentState = snapshot.getStateId();
        this.context = snapshot.getContext();
        
        // If not offline, enter the state (without firing entry actions for offline states)
        if (!snapshot.getIsOffline()) {
            enterState(currentState, false);
        }
        
        System.out.println("StateMachine " + id + " restored from snapshot to state: " + currentState);
    }
    
    /**
     * Define a stay action - handle an event within a state without transitioning
     */
    public GenericStateMachine stayAction(String stateId, String eventType, Consumer<StateMachineEvent> action) {
        stayActions.computeIfAbsent(stateId, k -> new ConcurrentHashMap<>())
                   .put(eventType, action);
        return this;
    }

    // Getters and setters
    public String getId() { return id; }
    public Object getContext() { return context; }
    public void setContext(Object context) { this.context = context; }
    
    public void setOnStateTransition(Consumer<String> callback) {
        this.onStateTransition = callback;
    }
    
    public void setOnOfflineTransition(Consumer<GenericStateMachine> callback) {
        this.onOfflineTransition = callback;
    }
    
    public EnhancedStateConfig getStateConfig(String stateId) {
        return stateConfigs.get(stateId);
    }
    
    public boolean isInState(String state) {
        return currentState.equals(state);
    }
}