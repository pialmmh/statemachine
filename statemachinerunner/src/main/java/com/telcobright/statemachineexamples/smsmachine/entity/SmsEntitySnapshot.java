package com.telcobright.statemachineexamples.smsmachine.entity;

import com.telcobright.statemachine.monitoring.AbstractMachineSnapshot;

/**
 * Concrete snapshot entity for SMS state machine transitions.
 * This entity extends AbstractMachineSnapshot and provides SMS-specific persistence.
 */
public class SmsEntitySnapshot extends AbstractMachineSnapshot {
    
    // SMS-specific fields for better querying and analysis
    private String smsId;
    private String fromNumber;
    private String toNumber;
    private String messageType; // SMS, MMS, etc.
    private Integer messageLength;
    
    // Default constructor for JPA/Hibernate
    public SmsEntitySnapshot() {
        super();
    }
    
    /**
     * Constructor for creating SMS snapshots
     */
    public SmsEntitySnapshot(String smsId, String fromNumber, String toNumber, String messageType, Integer messageLength) {
        super();
        this.smsId = smsId;
        this.fromNumber = fromNumber;
        this.toNumber = toNumber;
        this.messageType = messageType;
        this.messageLength = messageLength;
    }
    
    @Override
    public Object getShardingKey() {
        // Use smsId for sharding to keep all snapshots of the same SMS together
        return smsId != null ? smsId : getMachineId();
    }
    
    // Getters and setters
    public String getSmsId() {
        return smsId;
    }
    
    public void setSmsId(String smsId) {
        this.smsId = smsId;
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
    
    public String getMessageType() {
        return messageType;
    }
    
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }
    
    public Integer getMessageLength() {
        return messageLength;
    }
    
    public void setMessageLength(Integer messageLength) {
        this.messageLength = messageLength;
    }
    
    @Override
    public String toString() {
        return String.format("SmsEntitySnapshot{smsId=%s, %s->%s, %s→%s, v%d, len=%d, duration=%dms}", 
                            smsId, fromNumber, toNumber, getStateBefore(), getStateAfter(), 
                            getVersion(), messageLength, getTransitionDurationMillis());
    }
}