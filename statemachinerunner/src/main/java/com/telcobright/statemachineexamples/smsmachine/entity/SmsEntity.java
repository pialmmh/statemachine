package com.telcobright.statemachineexamples.smsmachine.entity;

import com.telcobright.db.entity.Id;
import com.telcobright.db.entity.ShardingKey;
import com.telcobright.db.entity.Column;
import com.telcobright.statemachine.StateMachineContextEntity;
import java.time.LocalDateTime;

/**
 * SMS Entity for persistence - the ID is the state machine ID
 * Uses ByIdAndDateRange lookup mode for high-volume SMS processing
 */
public class SmsEntity implements StateMachineContextEntity<String> {
    
    @Id
    @Column("sms_id")
    private String smsId;  // This is the state machine ID
    
    @Column("current_state")
    private String currentState;
    
    @Column("from_number")
    private String fromNumber;
    
    @Column("to_number") 
    private String toNumber;
    
    @Column("message_text")
    private String messageText;
    
    @Column("attempt_count")
    private int attemptCount;
    
    @Column("max_retries")
    private int maxRetries;
    
    @Column("priority")
    private String priority;
    
    @Column("failure_reason")
    private String failureReason;
    
    @ShardingKey
    @Column("created_at")
    private LocalDateTime createdAt;
    
    @Column("updated_at")
    private LocalDateTime updatedAt;
    
    @Column("is_complete")
    private boolean isComplete = false;
    
    // Default constructor
    public SmsEntity() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    // Constructor with required fields
    public SmsEntity(String smsId, String currentState, String fromNumber, String toNumber, String messageText) {
        this();
        this.smsId = smsId;
        this.currentState = currentState;
        this.fromNumber = fromNumber;
        this.toNumber = toNumber;
        this.messageText = messageText;
        this.attemptCount = 0;
        this.maxRetries = 3;
        this.priority = "NORMAL";
    }
    
    // Getters and setters
    public String getSmsId() { return smsId; }
    public void setSmsId(String smsId) { this.smsId = smsId; }
    
    public String getCurrentState() { return currentState; }
    public void setCurrentState(String currentState) { 
        this.currentState = currentState;
        this.updatedAt = LocalDateTime.now();
    }
    
    public String getFromNumber() { return fromNumber; }
    public void setFromNumber(String fromNumber) { this.fromNumber = fromNumber; }
    
    public String getToNumber() { return toNumber; }
    public void setToNumber(String toNumber) { this.toNumber = toNumber; }
    
    public String getMessageText() { return messageText; }
    public void setMessageText(String messageText) { this.messageText = messageText; }
    
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    // Completable interface implementation
    @Override
    public boolean isComplete() { return isComplete; }
    
    @Override
    public void setComplete(boolean complete) { 
        this.isComplete = complete; 
        this.updatedAt = LocalDateTime.now();
    }
    
    @Override
    public String toString() {
        return String.format("SmsEntity{smsId='%s', state='%s', from='%s', to='%s', message='%s', attempts=%d/%d, priority='%s'}", 
                smsId, currentState, fromNumber, toNumber, 
                messageText != null && messageText.length() > 20 ? messageText.substring(0, 20) + "..." : messageText,
                attemptCount, maxRetries, priority);
    }
}