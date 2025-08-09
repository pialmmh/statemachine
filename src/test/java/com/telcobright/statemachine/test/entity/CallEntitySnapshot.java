package com.telcobright.statemachine.test.entity;

import com.telcobright.statemachine.monitoring.AbstractMachineSnapshot;

/**
 * Entity-specific snapshot class for CallEntity
 * Auto-generated/created based on the main entity type
 * 
 * Naming convention: {EntityName}Snapshot
 * Package convention: {EntityPackage}.entity.{EntityName}Snapshot
 * 
 * This class inherits from AbstractMachineSnapshot and can add call-specific fields
 */
public class CallEntitySnapshot extends AbstractMachineSnapshot {
    
    // Call-specific snapshot fields (optional)
    private String fromNumber;
    private String toNumber;
    private String callType;
    private Integer ringCount;
    private Long callDurationMs;
    private String disconnectReason;
    private Boolean recordingEnabled;
    
    /**
     * Default constructor required for reflection-based instantiation
     */
    public CallEntitySnapshot() {
        super();
    }
    
    // Call-specific getters and setters
    public String getFromNumber() { return fromNumber; }
    public void setFromNumber(String fromNumber) { this.fromNumber = fromNumber; }
    
    public String getToNumber() { return toNumber; }
    public void setToNumber(String toNumber) { this.toNumber = toNumber; }
    
    public String getCallType() { return callType; }
    public void setCallType(String callType) { this.callType = callType; }
    
    public Integer getRingCount() { return ringCount; }
    public void setRingCount(Integer ringCount) { this.ringCount = ringCount; }
    
    public Long getCallDurationMs() { return callDurationMs; }
    public void setCallDurationMs(Long callDurationMs) { this.callDurationMs = callDurationMs; }
    
    public String getDisconnectReason() { return disconnectReason; }
    public void setDisconnectReason(String disconnectReason) { this.disconnectReason = disconnectReason; }
    
    public Boolean getRecordingEnabled() { return recordingEnabled; }
    public void setRecordingEnabled(Boolean recordingEnabled) { this.recordingEnabled = recordingEnabled; }
    
    @Override
    public String getShardingKey() {
        // Use machine ID as sharding key for call snapshots
        return getMachineId();
    }
    
    /**
     * Extract call-specific data from context for enhanced snapshot storage
     * This method can be called by the DatabaseSnapshotRecorder to enrich the snapshot
     */
    public void enrichFromContext(Object context) {
        if (context != null) {
            try {
                // Use reflection to extract call-specific fields from context
                Class<?> contextClass = context.getClass();
                
                // Extract common call fields if they exist
                extractField(context, contextClass, "fromNumber", this::setFromNumber);
                extractField(context, contextClass, "toNumber", this::setToNumber);
                extractField(context, contextClass, "callType", this::setCallType);
                extractField(context, contextClass, "ringCount", this::setRingCount);
                extractField(context, contextClass, "disconnectReason", this::setDisconnectReason);
                extractField(context, contextClass, "recordingEnabled", this::setRecordingEnabled);
                
                // Calculate call duration if possible
                try {
                    Object callDuration = contextClass.getMethod("getCallDuration").invoke(context);
                    if (callDuration instanceof java.time.Duration) {
                        this.callDurationMs = ((java.time.Duration) callDuration).toMillis();
                    }
                } catch (Exception e) {
                    // Field doesn't exist or not accessible - that's ok
                }
                
            } catch (Exception e) {
                // Context enrichment is optional - don't let it break snapshot recording
                System.err.println("Warning: Could not enrich CallEntitySnapshot from context: " + e.getMessage());
            }
        }
    }
    
    /**
     * Generic field extraction helper using reflection
     */
    @SuppressWarnings("unchecked")
    private <T> void extractField(Object context, Class<?> contextClass, String fieldName, java.util.function.Consumer<T> setter) {
        try {
            // Try getter method first
            String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            Object value = contextClass.getMethod(getterName).invoke(context);
            if (value != null) {
                setter.accept((T) value);
            }
        } catch (Exception e1) {
            try {
                // Try direct field access
                java.lang.reflect.Field field = contextClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(context);
                if (value != null) {
                    setter.accept((T) value);
                }
            } catch (Exception e2) {
                // Field doesn't exist or not accessible - that's ok
            }
        }
    }
    
    @Override
    public String toString() {
        return String.format("CallEntitySnapshot{id=%s, from=%s, to=%s, %s→%s, v%d, runId=%s}", 
                           getMachineId(), fromNumber, toNumber, 
                           getStateBefore(), getStateAfter(), getVersion(), getRunId());
    }
}