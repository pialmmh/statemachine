package com.telcobright.statewalk.playback;

import java.util.HashMap;
import java.util.Map;

/**
 * Statistics for playback history
 */
public class PlaybackStatistics {
    private String machineId;
    private int totalTransitions;
    private int currentPosition;
    private int maxSize;
    private Map<String, Integer> stateOccurrences = new HashMap<>();

    // Getters and setters
    public String getMachineId() {
        return machineId;
    }

    public void setMachineId(String machineId) {
        this.machineId = machineId;
    }

    public int getTotalTransitions() {
        return totalTransitions;
    }

    public void setTotalTransitions(int totalTransitions) {
        this.totalTransitions = totalTransitions;
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    public void setCurrentPosition(int currentPosition) {
        this.currentPosition = currentPosition;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    public Map<String, Integer> getStateOccurrences() {
        return stateOccurrences;
    }

    public void setStateOccurrences(Map<String, Integer> stateOccurrences) {
        this.stateOccurrences = stateOccurrences;
    }
}