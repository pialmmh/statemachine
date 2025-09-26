package com.telcobright.statewalk.playback;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.StateMachineContextEntity;
import com.telcobright.statemachine.events.StateMachineEvent;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages event playback and transition history for State-Walk machines.
 * Allows forward and backward navigation through state transitions.
 */
public class EventPlayer {

    private final Map<String, TransitionHistory> machineHistories = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final int maxHistorySize;
    private boolean recordingEnabled = true;

    /**
     * Create event player with default history size
     */
    public EventPlayer() {
        this(1000); // Default to 1000 transitions per machine
    }

    /**
     * Create event player with specified max history size per machine
     */
    public EventPlayer(int maxHistorySize) {
        this.maxHistorySize = maxHistorySize;
    }

    /**
     * Record a transition for a machine
     */
    public void recordTransition(String machineId, StateMachineEvent event, String fromState, String toState,
                                 StateMachineContextEntity<?> contextSnapshot) {
        if (!recordingEnabled) {
            return;
        }

        TransitionHistory history = machineHistories.computeIfAbsent(machineId,
            k -> new TransitionHistory(machineId, maxHistorySize));

        TransitionRecord record = new TransitionRecord(
            machineId,
            event,
            fromState,
            toState,
            LocalDateTime.now(),
            cloneContext(contextSnapshot)
        );

        history.addTransition(record);
    }

    /**
     * Play forward one transition
     */
    public boolean playForward(String machineId, GenericStateMachine<?, ?> machine) {
        lock.readLock().lock();
        try {
            TransitionHistory history = machineHistories.get(machineId);
            if (history == null) {
                return false;
            }

            TransitionRecord next = history.getNextTransition();
            if (next == null) {
                return false;
            }

            // Apply the forward transition
            applyTransition(machine, next, true);
            return true;

        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Play backward one transition
     */
    public boolean playBackward(String machineId, GenericStateMachine<?, ?> machine) {
        lock.readLock().lock();
        try {
            TransitionHistory history = machineHistories.get(machineId);
            if (history == null) {
                return false;
            }

            TransitionRecord prev = history.getPreviousTransition();
            if (prev == null) {
                return false;
            }

            // Apply the backward transition
            applyTransition(machine, prev, false);
            return true;

        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Jump to a specific point in history
     */
    public boolean jumpToTransition(String machineId, GenericStateMachine<?, ?> machine, int index) {
        lock.readLock().lock();
        try {
            TransitionHistory history = machineHistories.get(machineId);
            if (history == null) {
                return false;
            }

            TransitionRecord record = history.jumpToTransition(index);
            if (record == null) {
                return false;
            }

            // Apply the transition at the target index
            applyTransition(machine, record, true);
            return true;

        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Apply a transition to a machine
     */
    @SuppressWarnings("unchecked")
    private void applyTransition(GenericStateMachine<?, ?> machine, TransitionRecord record, boolean forward) {
        if (forward) {
            // Restore to target state
            machine.restoreState(record.toState);
        } else {
            // Restore to source state
            machine.restoreState(record.fromState);
        }

        // Restore context snapshot
        if (record.contextSnapshot != null) {
            ((GenericStateMachine<StateMachineContextEntity<?>, ?>) machine)
                .setPersistingEntity(record.contextSnapshot);
        }

        System.out.println("[EventPlayer] " + (forward ? "Forward" : "Backward") + " transition applied: " +
                         record.fromState + " -> " + record.toState);
    }

    /**
     * Get full history for a machine
     */
    public List<TransitionRecord> getHistory(String machineId) {
        TransitionHistory history = machineHistories.get(machineId);
        return history != null ? history.getAllTransitions() : Collections.emptyList();
    }

    /**
     * Get current position in history
     */
    public int getCurrentPosition(String machineId) {
        TransitionHistory history = machineHistories.get(machineId);
        return history != null ? history.getCurrentPosition() : -1;
    }

    /**
     * Clear history for a machine
     */
    public void clearHistory(String machineId) {
        lock.writeLock().lock();
        try {
            machineHistories.remove(machineId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Clear all histories
     */
    public void clearAllHistories() {
        lock.writeLock().lock();
        try {
            machineHistories.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Enable or disable recording
     */
    public void setRecordingEnabled(boolean enabled) {
        this.recordingEnabled = enabled;
    }

    /**
     * Check if recording is enabled
     */
    public boolean isRecordingEnabled() {
        return recordingEnabled;
    }

    /**
     * Clone context for snapshot
     */
    private StateMachineContextEntity<?> cloneContext(StateMachineContextEntity<?> context) {
        if (context == null) {
            return null;
        }

        // Deep clone implementation would go here
        // For now, we'll store the reference (should be immutable or properly cloned)
        return context;
    }

    /**
     * Get statistics for a machine's history
     */
    public PlaybackStatistics getStatistics(String machineId) {
        TransitionHistory history = machineHistories.get(machineId);
        if (history == null) {
            return new PlaybackStatistics();
        }

        return history.getStatistics();
    }

    /**
     * Export history to a serializable format
     */
    public PlaybackExport exportHistory(String machineId) {
        TransitionHistory history = machineHistories.get(machineId);
        if (history == null) {
            return null;
        }

        return new PlaybackExport(
            machineId,
            history.getAllTransitions(),
            history.getCurrentPosition()
        );
    }

    /**
     * Import history from an export
     */
    public void importHistory(PlaybackExport export) {
        if (export == null || export.getMachineId() == null) {
            return;
        }

        lock.writeLock().lock();
        try {
            TransitionHistory history = new TransitionHistory(export.getMachineId(), maxHistorySize);
            for (TransitionRecord record : export.getTransitions()) {
                history.addTransition(record);
            }
            history.setCurrentPosition(export.getCurrentPosition());
            machineHistories.put(export.getMachineId(), history);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get summary of all machine histories
     */
    public Map<String, PlaybackSummary> getAllSummaries() {
        Map<String, PlaybackSummary> summaries = new HashMap<>();
        for (Map.Entry<String, TransitionHistory> entry : machineHistories.entrySet()) {
            summaries.put(entry.getKey(), entry.getValue().getSummary());
        }
        return summaries;
    }
}