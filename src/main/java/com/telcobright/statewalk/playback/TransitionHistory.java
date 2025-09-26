package com.telcobright.statewalk.playback;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages transition history for a single state machine
 */
public class TransitionHistory {

    private final String machineId;
    private final int maxSize;
    private final List<TransitionRecord> transitions;
    private int currentPosition;
    private final ReadWriteLock lock;

    public TransitionHistory(String machineId, int maxSize) {
        this.machineId = machineId;
        this.maxSize = maxSize;
        this.transitions = new ArrayList<>();
        this.currentPosition = -1;
        this.lock = new ReentrantReadWriteLock();
    }

    /**
     * Add a new transition to history
     */
    public void addTransition(TransitionRecord record) {
        lock.writeLock().lock();
        try {
            // If we're not at the end, remove everything after current position
            if (currentPosition < transitions.size() - 1) {
                transitions.subList(currentPosition + 1, transitions.size()).clear();
            }

            // Add new transition
            transitions.add(record);

            // Enforce max size (remove oldest)
            while (transitions.size() > maxSize) {
                transitions.remove(0);
            }

            // Update current position
            currentPosition = transitions.size() - 1;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get the next transition (for forward playback)
     */
    public TransitionRecord getNextTransition() {
        lock.readLock().lock();
        try {
            if (currentPosition < transitions.size() - 1) {
                currentPosition++;
                return transitions.get(currentPosition);
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get the previous transition (for backward playback)
     */
    public TransitionRecord getPreviousTransition() {
        lock.readLock().lock();
        try {
            if (currentPosition > 0) {
                currentPosition--;
                return transitions.get(currentPosition);
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Jump to a specific transition index
     */
    public TransitionRecord jumpToTransition(int index) {
        lock.writeLock().lock();
        try {
            if (index >= 0 && index < transitions.size()) {
                currentPosition = index;
                return transitions.get(index);
            }
            return null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get all transitions (read-only copy)
     */
    public List<TransitionRecord> getAllTransitions() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(transitions);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get current position in history
     */
    public int getCurrentPosition() {
        lock.readLock().lock();
        try {
            return currentPosition;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Set current position
     */
    public void setCurrentPosition(int position) {
        lock.writeLock().lock();
        try {
            if (position >= -1 && position < transitions.size()) {
                this.currentPosition = position;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get statistics about the history
     */
    public PlaybackStatistics getStatistics() {
        lock.readLock().lock();
        try {
            PlaybackStatistics stats = new PlaybackStatistics();
            stats.setMachineId(machineId);
            stats.setTotalTransitions(transitions.size());
            stats.setCurrentPosition(currentPosition);
            stats.setMaxSize(maxSize);

            // Count state occurrences
            Map<String, Integer> stateCount = new HashMap<>();
            for (TransitionRecord record : transitions) {
                stateCount.merge(record.getFromState(), 1, Integer::sum);
                stateCount.merge(record.getToState(), 1, Integer::sum);
            }
            stats.setStateOccurrences(stateCount);

            return stats;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get summary of the history
     */
    public PlaybackSummary getSummary() {
        lock.readLock().lock();
        try {
            PlaybackSummary summary = new PlaybackSummary();
            summary.setMachineId(machineId);
            summary.setTransitionCount(transitions.size());
            summary.setCurrentPosition(currentPosition);

            if (!transitions.isEmpty()) {
                summary.setFirstTransition(transitions.get(0).getTimestamp());
                summary.setLastTransition(transitions.get(transitions.size() - 1).getTimestamp());
                summary.setCurrentState(transitions.get(Math.max(0, currentPosition)).getToState());
            }

            return summary;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Clear all history
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            transitions.clear();
            currentPosition = -1;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getMachineId() {
        return machineId;
    }

    public int getMaxSize() {
        return maxSize;
    }
}