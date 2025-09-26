package com.telcobright.statewalk.playback;

import java.io.Serializable;
import java.util.List;

/**
 * Export container for playback history
 */
public class PlaybackExport implements Serializable {
    private final String machineId;
    private final List<TransitionRecord> transitions;
    private final int currentPosition;

    public PlaybackExport(String machineId, List<TransitionRecord> transitions, int currentPosition) {
        this.machineId = machineId;
        this.transitions = transitions;
        this.currentPosition = currentPosition;
    }

    public String getMachineId() {
        return machineId;
    }

    public List<TransitionRecord> getTransitions() {
        return transitions;
    }

    public int getCurrentPosition() {
        return currentPosition;
    }
}