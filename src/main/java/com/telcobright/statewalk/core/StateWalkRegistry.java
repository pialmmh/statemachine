package com.telcobright.statewalk.core;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachine.persistence.PersistenceProvider;
import com.telcobright.statemachine.timeout.TimeoutManager;
import com.telcobright.statewalk.persistence.EntityGraphMapper;
import com.telcobright.statewalk.persistence.SplitVerseGraphAdapter;
import com.telcobright.statewalk.playback.EventPlayer;
import com.telcobright.statewalk.playback.TransitionRecord;

import java.util.function.Supplier;
import java.util.function.Function;

/**
 * State-Walk Registry with enhanced multi-entity support and playback capabilities.
 * Maintains backward compatibility with legacy single-entity mode.
 */
public class StateWalkRegistry<T extends StateMachineContextEntity<?>> extends StateMachineRegistry {

    private final StateWalkBuilder<T> config;
    private final boolean legacyMode;
    private final EntityGraphMapper graphMapper;
    private final SplitVerseGraphAdapter graphAdapter;
    private final EventPlayer eventPlayer;

    /**
     * Package-private constructor called by builder
     */
    StateWalkRegistry(StateWalkBuilder<T> builder) {
        super(builder.getRegistryName(), builder.getPerformanceConfig());

        this.config = builder;
        this.legacyMode = builder.isLegacyMode();
        this.graphMapper = builder.getGraphMapper();

        // Initialize graph adapter for multi-entity mode
        if (!legacyMode && builder.getShardConfig() != null) {
            this.graphAdapter = new SplitVerseGraphAdapter(
                builder.getRegistryName(),
                builder.getShardConfig()
            );

            // Create tables for entity graph
            if (graphMapper != null) {
                graphAdapter.createTablesForGraph(graphMapper);
            }

            // Set up graph-based persistence
            setupGraphPersistence();
        } else {
            this.graphAdapter = null;
            // Use legacy persistence
            setupLegacyPersistence();
        }

        // Initialize event player for playback support
        if (builder.isPlaybackEnabled()) {
            this.eventPlayer = new EventPlayer();
        } else {
            this.eventPlayer = null;
        }

        System.out.println("[StateWalk] Registry '" + builder.getRegistryName() + "' initialized in " +
                         (legacyMode ? "LEGACY" : "MULTI-ENTITY") + " mode");
    }

    /**
     * Setup graph-based multi-entity persistence
     */
    private void setupGraphPersistence() {
        // Create a custom persistence provider that uses the graph adapter
        PersistenceProvider<StateMachineContextEntity<?>> graphPersistenceProvider =
            new GraphPersistenceProvider(graphAdapter, graphMapper);

        super.setPersistenceProvider(graphPersistenceProvider);
        System.out.println("[StateWalk] Graph-based persistence configured");
    }

    /**
     * Setup legacy single-entity persistence
     */
    private void setupLegacyPersistence() {
        // Use the existing persistence setup from parent class
        System.out.println("[StateWalk] Legacy single-entity persistence configured");
    }

    /**
     * Override register to add playback recording
     */
    @Override
    public void register(String id, GenericStateMachine<?, ?> machine) {
        super.register(id, machine);

        // Set up playback recording if enabled
        if (eventPlayer != null) {
            String[] previousState = {machine.getCurrentState()};

            // Wrap existing transition callback to record transitions
            machine.setOnStateTransition(newState -> {
                String oldState = previousState[0];
                previousState[0] = newState;

                // Record transition for playback
                eventPlayer.recordTransition(
                    id,
                    null, // Event would need to be captured separately
                    oldState,
                    newState,
                    machine.getPersistingEntity()
                );

                // Notify listeners (including parent's WebSocket)
                notifyStateMachineEvent(id, oldState, newState,
                    machine.getPersistingEntity(), machine.getContext());

                // Check for final state eviction
                evictIfFinalState(id);
            });
        }
    }

    /**
     * Create or get a state machine with graph persistence support
     */
    @SuppressWarnings("unchecked")
    public GenericStateMachine<T, ?> createOrGetWithGraph(String id,
                                                          Supplier<GenericStateMachine<T, ?>> factory) {
        // Check if already in memory
        GenericStateMachine<T, ?> existing = (GenericStateMachine<T, ?>) getActiveMachine(id);
        if (existing != null) {
            return existing;
        }

        // Try to load from graph persistence
        if (!legacyMode && graphMapper != null && graphAdapter != null && !isRehydrationDisabled()) {
            T loadedContext = graphMapper.loadGraph(id, config.getContextClass(), graphAdapter);
            if (loadedContext != null && !loadedContext.isComplete()) {
                System.out.println("[StateWalk] Rehydrating machine " + id + " from graph persistence");

                GenericStateMachine<T, ?> machine = factory.get();
                machine.setPersistingEntity(loadedContext);
                machine.restoreState(loadedContext.getCurrentState());

                register(id, machine);
                return machine;
            }
        }

        // Fall back to regular creation
        return createOrGet(id, factory);
    }

    /**
     * Play forward one transition in history
     */
    public boolean playForward(String machineId) {
        if (eventPlayer == null) {
            System.err.println("[StateWalk] Playback not enabled");
            return false;
        }

        GenericStateMachine<?, ?> machine = getActiveMachine(machineId);
        if (machine == null) {
            System.err.println("[StateWalk] Machine " + machineId + " not found");
            return false;
        }

        return eventPlayer.playForward(machineId, machine);
    }

    /**
     * Play backward one transition in history
     */
    public boolean playBackward(String machineId) {
        if (eventPlayer == null) {
            System.err.println("[StateWalk] Playback not enabled");
            return false;
        }

        GenericStateMachine<?, ?> machine = getActiveMachine(machineId);
        if (machine == null) {
            System.err.println("[StateWalk] Machine " + machineId + " not found");
            return false;
        }

        return eventPlayer.playBackward(machineId, machine);
    }

    /**
     * Jump to a specific point in transition history
     */
    public boolean jumpToTransition(String machineId, int index) {
        if (eventPlayer == null) {
            System.err.println("[StateWalk] Playback not enabled");
            return false;
        }

        GenericStateMachine<?, ?> machine = getActiveMachine(machineId);
        if (machine == null) {
            System.err.println("[StateWalk] Machine " + machineId + " not found");
            return false;
        }

        return eventPlayer.jumpToTransition(machineId, machine, index);
    }

    /**
     * Get playback history for a machine
     */
    public java.util.List<TransitionRecord> getPlaybackHistory(String machineId) {
        if (eventPlayer == null) {
            return java.util.Collections.emptyList();
        }
        return eventPlayer.getHistory(machineId);
    }

    /**
     * Clear playback history for a machine
     */
    public void clearPlaybackHistory(String machineId) {
        if (eventPlayer != null) {
            eventPlayer.clearHistory(machineId);
        }
    }

    /**
     * Enable or disable playback recording
     */
    public void setPlaybackRecording(boolean enabled) {
        if (eventPlayer != null) {
            eventPlayer.setRecordingEnabled(enabled);
        }
    }

    /**
     * Persist entity graph for a machine
     */
    public void persistGraph(String machineId) {
        if (legacyMode || graphMapper == null || graphAdapter == null) {
            // Fall back to regular persistence
            GenericStateMachine<?, ?> machine = getActiveMachine(machineId);
            if (machine != null && machine.getPersistingEntity() != null) {
                try {
                    getPersistenceProvider().save(machineId, machine.getPersistingEntity());
                } catch (Exception e) {
                    System.err.println("[StateWalk] Failed to persist machine " + machineId + ": " + e.getMessage());
                }
            }
            return;
        }

        // Persist using graph adapter
        GenericStateMachine<?, ?> machine = getActiveMachine(machineId);
        if (machine != null && machine.getPersistingEntity() != null) {
            graphMapper.persistGraph(machine.getPersistingEntity(), graphAdapter);
            System.out.println("[StateWalk] Persisted graph for machine " + machineId);
        }
    }

    /**
     * Override shutdown to clean up State-Walk specific resources
     */
    @Override
    public void shutdown() {
        // Persist all active machine graphs before shutdown
        if (!legacyMode && graphMapper != null && graphAdapter != null) {
            for (String machineId : getActiveMachines().keySet()) {
                persistGraph(machineId);
            }
        }

        // Shutdown graph adapter
        if (graphAdapter != null) {
            graphAdapter.shutdown();
        }

        // Clear event player
        if (eventPlayer != null) {
            eventPlayer.clearAllHistories();
        }

        // Call parent shutdown
        super.shutdown();

        System.out.println("[StateWalk] Registry '" + config.getRegistryName() + "' shutdown complete");
    }

    /**
     * Get the configuration used to build this registry
     */
    public StateWalkBuilder<T> getConfig() {
        return config;
    }

    /**
     * Check if registry is in legacy mode
     */
    public boolean isLegacyMode() {
        return legacyMode;
    }

    /**
     * Get the event player for playback operations
     */
    public EventPlayer getEventPlayer() {
        return eventPlayer;
    }

    /**
     * Get the entity graph mapper
     */
    public EntityGraphMapper getGraphMapper() {
        return graphMapper;
    }

    /**
     * Get the graph adapter
     */
    public SplitVerseGraphAdapter getGraphAdapter() {
        return graphAdapter;
    }

    /**
     * Custom persistence provider that uses graph adapter
     */
    private class GraphPersistenceProvider implements PersistenceProvider<StateMachineContextEntity<?>> {

        private final SplitVerseGraphAdapter adapter;
        private final EntityGraphMapper mapper;

        GraphPersistenceProvider(SplitVerseGraphAdapter adapter, EntityGraphMapper mapper) {
            this.adapter = adapter;
            this.mapper = mapper;
        }

        @Override
        public void initialize() {
            // Already initialized by adapter
        }

        @Override
        public void save(String id, StateMachineContextEntity<?> entity) {
            if (mapper != null && adapter != null) {
                mapper.persistGraph(entity, adapter);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <E extends StateMachineContextEntity<?>> E load(String id, Class<E> entityClass) {
            if (mapper != null && adapter != null) {
                return mapper.loadGraph(id, entityClass, adapter);
            }
            return null;
        }

        @Override
        public void delete(String id) {
            // Implementation would delete the entity graph
            System.out.println("[GraphPersistenceProvider] Delete not yet implemented for: " + id);
        }
    }
}