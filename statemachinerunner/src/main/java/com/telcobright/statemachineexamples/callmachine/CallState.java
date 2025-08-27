package com.telcobright.statemachineexamples.callmachine;

/**
 * Enum representing call states
 */
public enum CallState {
    IDLE,
    RINGING,
    CONNECTED,
    HUNGUP  // Final state - machine will be evicted when reaching this state
}