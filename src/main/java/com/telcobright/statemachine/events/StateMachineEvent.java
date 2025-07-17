package com.telcobright.statemachine.events;

/**
 * Base interface for state machine events
 */
public interface StateMachineEvent {
    String getEventType();
    String getDescription();
    Object getPayload();
    long getTimestamp();
}