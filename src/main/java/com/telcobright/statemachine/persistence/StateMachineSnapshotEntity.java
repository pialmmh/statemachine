package com.telcobright.statemachine.persistence;

import java.time.LocalDateTime;

/**
 * Entity for persisting state machine snapshots
 * Note: JPA annotations can be added when JPA is properly configured
 */
public class StateMachineSnapshotEntity {
    
    private Long id;
    private String machineId;
    private String stateId;
    private LocalDateTime timestamp;
    private String context;
    private Boolean isOffline = false;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Constructors
    public StateMachineSnapshotEntity() {}
    
    public StateMachineSnapshotEntity(String machineId, String stateId, String context, Boolean isOffline) {
        this.machineId = machineId;
        this.stateId = stateId;
        this.context = context;
        this.isOffline = isOffline;
        this.timestamp = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getMachineId() { return machineId; }
    public void setMachineId(String machineId) { this.machineId = machineId; }
    
    public String getStateId() { return stateId; }
    public void setStateId(String stateId) { this.stateId = stateId; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
    
    public Boolean getIsOffline() { return isOffline; }
    public void setIsOffline(Boolean isOffline) { this.isOffline = isOffline; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
