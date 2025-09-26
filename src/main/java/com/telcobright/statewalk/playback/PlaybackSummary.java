package com.telcobright.statewalk.playback;

import java.time.LocalDateTime;

/**
 * Summary of playback history
 */
public class PlaybackSummary {
    private String machineId;
    private int transitionCount;
    private int currentPosition;
    private LocalDateTime firstTransition;
    private LocalDateTime lastTransition;
    private String currentState;

    // Getters and setters
    public String getMachineId() {
        return machineId;
    }

    public void setMachineId(String machineId) {
        this.machineId = machineId;
    }

    public int getTransitionCount() {
        return transitionCount;
    }

    public void setTransitionCount(int transitionCount) {
        this.transitionCount = transitionCount;
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    public void setCurrentPosition(int currentPosition) {
        this.currentPosition = currentPosition;
    }

    public LocalDateTime getFirstTransition() {
        return firstTransition;
    }

    public void setFirstTransition(LocalDateTime firstTransition) {
        this.firstTransition = firstTransition;
    }

    public LocalDateTime getLastTransition() {
        return lastTransition;
    }

    public void setLastTransition(LocalDateTime lastTransition) {
        this.lastTransition = lastTransition;
    }

    public String getCurrentState() {
        return currentState;
    }

    public void setCurrentState(String currentState) {
        this.currentState = currentState;
    }
}