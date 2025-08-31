package com.telcobright.examples.callmachine;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Call Context - Volatile/transient data for active calls
 * 
 * Contains runtime information that doesn't need persistence
 */
public class CallContext {
    
    // Call identifiers
    private String callId;
    private String sessionId;
    
    // Call parties
    private String fromNumber;
    private String toNumber;
    
    // Timing information
    private LocalDateTime startTime;
    private LocalDateTime ringStartTime;
    private LocalDateTime connectTime;
    private LocalDateTime endTime;
    
    // Call metrics
    private int ringCount = 0;
    private AtomicLong eventCount = new AtomicLong(0);
    
    // Call status
    private String disconnectReason;
    private boolean recordingEnabled = false;
    
    // Performance tracking
    private long lastEventTimestamp = System.currentTimeMillis();
    
    /**
     * Default constructor
     */
    public CallContext() {
        this.startTime = LocalDateTime.now();
    }
    
    /**
     * Constructor with basic call information
     */
    public CallContext(String callId, String fromNumber, String toNumber) {
        this();
        this.callId = callId;
        this.fromNumber = fromNumber;
        this.toNumber = toNumber;
        this.sessionId = callId + "-" + System.currentTimeMillis();
    }
    
    /**
     * Mark call as ringing
     */
    public void startRinging() {
        this.ringStartTime = LocalDateTime.now();
        this.ringCount++;
    }
    
    /**
     * Mark call as connected
     */
    public void connect() {
        this.connectTime = LocalDateTime.now();
    }
    
    /**
     * Mark call as ended
     */
    public void end(String reason) {
        this.endTime = LocalDateTime.now();
        this.disconnectReason = reason;
    }
    
    /**
     * Record an event
     */
    public void recordEvent() {
        eventCount.incrementAndGet();
        lastEventTimestamp = System.currentTimeMillis();
    }
    
    /**
     * Get call duration
     */
    public Duration getCallDuration() {
        if (connectTime != null && endTime != null) {
            return Duration.between(connectTime, endTime);
        } else if (connectTime != null) {
            return Duration.between(connectTime, LocalDateTime.now());
        }
        return Duration.ZERO;
    }
    
    /**
     * Get ring duration
     */
    public Duration getRingDuration() {
        if (ringStartTime != null && connectTime != null) {
            return Duration.between(ringStartTime, connectTime);
        } else if (ringStartTime != null && connectTime == null) {
            return Duration.between(ringStartTime, LocalDateTime.now());
        }
        return Duration.ZERO;
    }
    
    /**
     * Get total call duration (from start to end)
     */
    public Duration getTotalDuration() {
        if (startTime != null && endTime != null) {
            return Duration.between(startTime, endTime);
        } else if (startTime != null) {
            return Duration.between(startTime, LocalDateTime.now());
        }
        return Duration.ZERO;
    }
    
    /**
     * Check if call is connected
     */
    public boolean isConnected() {
        return connectTime != null && endTime == null;
    }
    
    /**
     * Check if call has ended
     */
    public boolean isEnded() {
        return endTime != null;
    }
    
    // Getters and setters
    
    public String getCallId() {
        return callId;
    }
    
    public void setCallId(String callId) {
        this.callId = callId;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
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
    
    public LocalDateTime getStartTime() {
        return startTime;
    }
    
    public LocalDateTime getRingStartTime() {
        return ringStartTime;
    }
    
    public LocalDateTime getConnectTime() {
        return connectTime;
    }
    
    public LocalDateTime getEndTime() {
        return endTime;
    }
    
    public int getRingCount() {
        return ringCount;
    }
    
    public void setRingCount(int ringCount) {
        this.ringCount = ringCount;
    }
    
    public long getEventCount() {
        return eventCount.get();
    }
    
    public String getDisconnectReason() {
        return disconnectReason;
    }
    
    public void setDisconnectReason(String disconnectReason) {
        this.disconnectReason = disconnectReason;
    }
    
    public boolean isRecordingEnabled() {
        return recordingEnabled;
    }
    
    public void setRecordingEnabled(boolean recordingEnabled) {
        this.recordingEnabled = recordingEnabled;
    }
    
    public long getLastEventTimestamp() {
        return lastEventTimestamp;
    }
    
    @Override
    public String toString() {
        return String.format("CallContext{callId='%s', from='%s', to='%s', connected=%s, duration=%s}",
            callId, fromNumber, toNumber, isConnected(), getCallDuration());
    }
}