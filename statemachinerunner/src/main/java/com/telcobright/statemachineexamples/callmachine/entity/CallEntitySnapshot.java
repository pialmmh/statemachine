package com.telcobright.statemachineexamples.callmachine.entity;

import com.telcobright.statemachine.monitoring.AbstractMachineSnapshot;

/**
 * Concrete snapshot entity for Call state machine transitions.
 * This entity extends AbstractMachineSnapshot and provides call-specific persistence.
 */
public class CallEntitySnapshot extends AbstractMachineSnapshot {
    
    // Call-specific fields for better querying and analysis
    private String callId;
    private String fromNumber;
    private String toNumber;
    private String callDirection; // INBOUND, OUTBOUND
    
    // Default constructor for JPA/Hibernate
    public CallEntitySnapshot() {
        super();
    }
    
    /**
     * Constructor for creating call snapshots
     */
    public CallEntitySnapshot(String callId, String fromNumber, String toNumber, String callDirection) {
        super();
        this.callId = callId;
        this.fromNumber = fromNumber;
        this.toNumber = toNumber;
        this.callDirection = callDirection;
    }
    
    @Override
    public Object getShardingKey() {
        // Use callId for sharding to keep all snapshots of the same call together
        return callId != null ? callId : getMachineId();
    }
    
    // Getters and setters
    public String getCallId() {
        return callId;
    }
    
    public void setCallId(String callId) {
        this.callId = callId;
    }
    
    public String getFromNumber() {
        return fromNumber;
    }
    
    public void setFromNumber(String fromNumber) {
        this.fromNumber = fromNumber;
    }
    
    public String getToNumber() {
        return toNumber;
    }
    
    public void setToNumber(String toNumber) {
        this.toNumber = toNumber;
    }
    
    public String getCallDirection() {
        return callDirection;
    }
    
    public void setCallDirection(String callDirection) {
        this.callDirection = callDirection;
    }
    
    @Override
    public String toString() {
        return String.format("CallEntitySnapshot{callId=%s, %s->%s, %s→%s, v%d, duration=%dms}", 
                            callId, fromNumber, toNumber, getStateBefore(), getStateAfter(), 
                            getVersion(), getTransitionDurationMillis());
    }
}