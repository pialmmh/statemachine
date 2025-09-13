package com.telcobright.statemachine.test;

import com.telcobright.statemachine.persistence.SplitVerseStateMachineEntity;
import com.telcobright.core.annotation.Table;
import com.telcobright.core.annotation.Column;

/**
 * Test entity for Split-Verse integration
 * Represents a call state machine with split-verse persistence
 */
@Table(name = "call_states")
public class SplitVerseCallEntity extends SplitVerseStateMachineEntity {

    @Column(name = "caller_id")
    private String callerId;

    @Column(name = "callee_id")
    private String calleeId;

    @Column(name = "call_duration")
    private Long callDuration;

    @Column(name = "call_type")
    private String callType;

    @Column(name = "disconnect_reason")
    private String disconnectReason;

    // Constructors
    public SplitVerseCallEntity() {
        super();
        this.callType = "VOICE";
    }

    public SplitVerseCallEntity(String id) {
        super(id);
        this.callType = "VOICE";
    }

    public SplitVerseCallEntity(String id, String callerId, String calleeId) {
        super(id);
        this.callerId = callerId;
        this.calleeId = calleeId;
        this.callType = "VOICE";
        this.callDuration = 0L;
    }

    // Getters and Setters
    public String getCallerId() {
        return callerId;
    }

    public void setCallerId(String callerId) {
        this.callerId = callerId;
    }

    public String getCalleeId() {
        return calleeId;
    }

    public void setCalleeId(String calleeId) {
        this.calleeId = calleeId;
    }

    public Long getCallDuration() {
        return callDuration;
    }

    public void setCallDuration(Long callDuration) {
        this.callDuration = callDuration;
    }

    public String getCallType() {
        return callType;
    }

    public void setCallType(String callType) {
        this.callType = callType;
    }

    public String getDisconnectReason() {
        return disconnectReason;
    }

    public void setDisconnectReason(String disconnectReason) {
        this.disconnectReason = disconnectReason;
    }

    @Override
    public String toString() {
        return "SplitVerseCallEntity{" +
                "id='" + id + '\'' +
                ", callerId='" + callerId + '\'' +
                ", calleeId='" + calleeId + '\'' +
                ", currentState='" + currentState + '\'' +
                ", callDuration=" + callDuration +
                ", callType='" + callType + '\'' +
                ", createdAt=" + createdAt +
                ", isComplete=" + isComplete +
                '}';
    }
}