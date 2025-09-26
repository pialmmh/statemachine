package com.telcobright.statewalk.playback;

import com.telcobright.statemachine.StateMachineContextEntity;
import com.telcobright.statemachine.events.StateMachineEvent;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Record of a single state transition
 */
public class TransitionRecord implements Serializable {

    private final String machineId;
    private final StateMachineEvent event;
    private final String fromState;
    private final String toState;
    private final LocalDateTime timestamp;
    private final StateMachineContextEntity<?> contextSnapshot;

    public TransitionRecord(String machineId, StateMachineEvent event, String fromState,
                          String toState, LocalDateTime timestamp, StateMachineContextEntity<?> contextSnapshot) {
        this.machineId = machineId;
        this.event = event;
        this.fromState = fromState;
        this.toState = toState;
        this.timestamp = timestamp;
        this.contextSnapshot = contextSnapshot;
    }

    // Getters
    public String getMachineId() {
        return machineId;
    }

    public StateMachineEvent getEvent() {
        return event;
    }

    public String getFromState() {
        return fromState;
    }

    public String getToState() {
        return toState;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public StateMachineContextEntity<?> getContextSnapshot() {
        return contextSnapshot;
    }

    @Override
    public String toString() {
        return String.format("TransitionRecord[%s: %s -> %s via %s at %s]",
            machineId, fromState, toState,
            event != null ? event.getClass().getSimpleName() : "null",
            timestamp);
    }
}