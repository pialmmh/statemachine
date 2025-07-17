package com.telcobright.statemachine;

public class StateMachineSnapshot {
    private final String stateId;
    private final long timestamp;
    private final String machineId;
    private final Object context;

    public StateMachineSnapshot(String stateId, long timestamp, String machineId, Object context) {
        this.stateId = stateId;
        this.timestamp = timestamp;
        this.machineId = machineId;
        this.context = context;
    }

    // Getters for fields
    public String getStateId() {
        return stateId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getMachineId() {
        return machineId;
    }

    public Object getContext() {
        return context;
    }
}