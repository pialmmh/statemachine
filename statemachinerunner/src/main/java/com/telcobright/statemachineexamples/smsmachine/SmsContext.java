package com.telcobright.statemachineexamples.smsmachine;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Rich SMS context with comprehensive message data and business logic
 */
public class SmsContext {
    @JsonProperty("messageId")
    private String messageId;
    
    @JsonProperty("fromNumber")
    private String fromNumber;
    
    @JsonProperty("toNumber")
    private String toNumber;
    
    @JsonProperty("messageText")
    private String messageText;
    
    @JsonProperty("attemptCount")
    private int attemptCount;
    
    @JsonProperty("sentAt")
    private LocalDateTime sentAt;
    
    @JsonProperty("deliveredAt")
    private LocalDateTime deliveredAt;
    
    @JsonProperty("queuedAt")
    private LocalDateTime queuedAt;
    
    @JsonProperty("priority")
    private String priority;
    
    @JsonProperty("messageStatus")
    private String messageStatus;
    
    @JsonProperty("failureReason")
    private String failureReason;
    
    @JsonProperty("deliveryEvents")
    private List<String> deliveryEvents;
    
    @JsonProperty("maxRetries")
    private int maxRetries;
    
    @JsonProperty("isHighPriority")
    private boolean isHighPriority;
    
    public SmsContext() {
        this.deliveryEvents = new ArrayList<>();
        this.queuedAt = LocalDateTime.now();
        this.messageStatus = "QUEUED";
        this.maxRetries = 3;
        this.priority = "NORMAL";
        addDeliveryEvent("SMS context initialized");
    }
    
    public SmsContext(String messageId, String fromNumber, String toNumber, String messageText) {
        this();
        this.messageId = messageId;
        this.fromNumber = fromNumber;
        this.toNumber = toNumber;
        this.messageText = messageText;
        
        // Determine if high priority based on content or sender
        this.isHighPriority = isUrgentMessage();
        if (this.isHighPriority) {
            this.priority = "HIGH";
            this.maxRetries = 5;
        }
        
        addDeliveryEvent("SMS created: " + messageText.substring(0, Math.min(20, messageText.length())) + "...");
    }
    
    public void startSending() {
        this.sentAt = LocalDateTime.now();
        this.messageStatus = "SENDING";
        this.attemptCount++;
        addDeliveryEvent("Sending attempt #" + attemptCount);
    }
    
    public void markDelivered() {
        this.deliveredAt = LocalDateTime.now();
        this.messageStatus = "DELIVERED";
        addDeliveryEvent("SMS successfully delivered");
    }
    
    public void markFailed(String reason) {
        this.failureReason = reason;
        this.messageStatus = "FAILED";
        addDeliveryEvent("SMS failed: " + reason);
    }
    
    public void addDeliveryEvent(String event) {
        String timestamp = LocalDateTime.now().toString();
        deliveryEvents.add("[" + timestamp + "] " + event);
    }
    
    public Duration getDeliveryTime() {
        if (queuedAt != null && deliveredAt != null) {
            return Duration.between(queuedAt, deliveredAt);
        }
        return Duration.ZERO;
    }
    
    public boolean canRetry() {
        return attemptCount < maxRetries;
    }
    
    public boolean isLongMessage() {
        return messageText != null && messageText.length() > 160;
    }
    
    private boolean isUrgentMessage() {
        if (messageText == null) return false;
        String text = messageText.toLowerCase();
        return text.contains("urgent") || text.contains("emergency") || 
               text.contains("911") || text.contains("alert");
    }
    
    // Getters and setters
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    
    public String getFromNumber() { return fromNumber; }
    public void setFromNumber(String fromNumber) { this.fromNumber = fromNumber; }
    
    public String getToNumber() { return toNumber; }
    public void setToNumber(String toNumber) { this.toNumber = toNumber; }
    
    public String getMessageText() { return messageText; }
    public void setMessageText(String messageText) { this.messageText = messageText; }
    
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
    
    public LocalDateTime getQueuedAt() { return queuedAt; }
    public void setQueuedAt(LocalDateTime queuedAt) { this.queuedAt = queuedAt; }
    
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    
    public String getMessageStatus() { return messageStatus; }
    public void setMessageStatus(String messageStatus) { this.messageStatus = messageStatus; }
    
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    
    public List<String> getDeliveryEvents() { return deliveryEvents; }
    public void setDeliveryEvents(List<String> deliveryEvents) { this.deliveryEvents = deliveryEvents; }
    
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    
    public boolean isHighPriority() { return isHighPriority; }
    public void setHighPriority(boolean highPriority) { this.isHighPriority = highPriority; }
    
    @Override
    public String toString() {
        return String.format("SmsContext{id='%s', from='%s', to='%s', status='%s', attempts=%d/%d}",
                messageId, fromNumber, toNumber, messageStatus, attemptCount, maxRetries);
    }
}