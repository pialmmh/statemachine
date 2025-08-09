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
    
    // Callbacks
    private Consumer<String> onStateTransition;
    private Consumer<GenericStateMachine<TPersistingEntity, TContext>> onOfflineTransition;
    
    public GenericStateMachine(String id, TimeoutManager timeoutManager, StateMachineRegistry registry) {
        this.id = id;
        this.currentState = "initial";
        this.timeoutManager = timeoutManager;
        this.registry = registry;
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
    }
    
    /**
     * Persist current state using ShardingEntity
     */
    private void persistState() {
        if (persistingEntity != null) {
            // TODO: Implement full ShardingEntity persistence integration
            // For now, just update the entity's state field if it exists
            try {
                java.lang.reflect.Method setCurrentState = persistingEntity.getClass().getMethod("setCurrentState", String.class);
                setCurrentState.invoke(persistingEntity, currentState);
                System.out.println("Updated entity state to: " + currentState);
            } catch (Exception e) {
                // Entity doesn't have setCurrentState method or other error - that's ok
            }
        }
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
                Map<String, BiConsumer<GenericStateMachine<TPersistingEntity, TContext>, StateMachineEvent>> stateStayActions = stayActions.get(currentState);
                if (stateStayActions != null) {
                    BiConsumer<GenericStateMachine<TPersistingEntity, TContext>, StateMachineEvent> action = stateStayActions.get(event.getEventType());
                    if (action != null) {
                        action.accept(this, event);
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
}