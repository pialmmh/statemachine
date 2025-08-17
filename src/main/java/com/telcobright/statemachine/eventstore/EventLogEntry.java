package com.telcobright.statemachine.eventstore;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.Map;

/**
 * Represents a single event log entry in the event store
 */
public class EventLogEntry {
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    private final String id;
    private final LocalDateTime timestamp;
    private final String source;
    private final String destination;
    private final String eventType;
    private final String eventCategory; // STATE_CHANGE, WEBSOCKET_IN, WEBSOCKET_OUT, REGISTRY, TIMEOUT, etc.
    private final Map<String, Object> eventDetails;
    private final String machineId;
    private final String stateBefore;
    private final String stateAfter;
    private final boolean success;
    private final String errorMessage;
    private final long processingTimeMs;
    
    private EventLogEntry(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.timestamp = builder.timestamp != null ? builder.timestamp : LocalDateTime.now();
        this.source = builder.source;
        this.destination = builder.destination;
        this.eventType = builder.eventType;
        this.eventCategory = builder.eventCategory;
        this.eventDetails = builder.eventDetails;
        this.machineId = builder.machineId;
        this.stateBefore = builder.stateBefore;
        this.stateAfter = builder.stateAfter;
        this.success = builder.success;
        this.errorMessage = builder.errorMessage;
        this.processingTimeMs = builder.processingTimeMs;
    }
    
    // Getters
    public String getId() { return id; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getSource() { return source; }
    public String getDestination() { return destination; }
    public String getEventType() { return eventType; }
    public String getEventCategory() { return eventCategory; }
    public Map<String, Object> getEventDetails() { return eventDetails; }
    public String getMachineId() { return machineId; }
    public String getStateBefore() { return stateBefore; }
    public String getStateAfter() { return stateAfter; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
    public long getProcessingTimeMs() { return processingTimeMs; }
    
    public String getFormattedTimestamp() {
        return timestamp.format(TIMESTAMP_FORMATTER);
    }
    
    // Builder pattern for flexible construction
    public static class Builder {
        private String id;
        private LocalDateTime timestamp;
        private String source;
        private String destination;
        private String eventType;
        private String eventCategory;
        private Map<String, Object> eventDetails;
        private String machineId;
        private String stateBefore;
        private String stateAfter;
        private boolean success = true;
        private String errorMessage;
        private long processingTimeMs;
        
        public Builder source(String source) {
            this.source = source;
            return this;
        }
        
        public Builder destination(String destination) {
            this.destination = destination;
            return this;
        }
        
        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }
        
        public Builder eventCategory(String eventCategory) {
            this.eventCategory = eventCategory;
            return this;
        }
        
        public Builder eventDetails(Map<String, Object> eventDetails) {
            this.eventDetails = eventDetails;
            return this;
        }
        
        public Builder machineId(String machineId) {
            this.machineId = machineId;
            return this;
        }
        
        public Builder stateBefore(String stateBefore) {
            this.stateBefore = stateBefore;
            return this;
        }
        
        public Builder stateAfter(String stateAfter) {
            this.stateAfter = stateAfter;
            return this;
        }
        
        public Builder success(boolean success) {
            this.success = success;
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            this.success = false;
            return this;
        }
        
        public Builder processingTimeMs(long processingTimeMs) {
            this.processingTimeMs = processingTimeMs;
            return this;
        }
        
        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public EventLogEntry build() {
            return new EventLogEntry(this);
        }
    }
    
    // Event categories as constants
    public static class Category {
        public static final String STATE_CHANGE = "STATE_CHANGE";
        public static final String WEBSOCKET_IN = "WEBSOCKET_IN";
        public static final String WEBSOCKET_OUT = "WEBSOCKET_OUT";
        public static final String REGISTRY_CREATE = "REGISTRY_CREATE";
        public static final String REGISTRY_REMOVE = "REGISTRY_REMOVE";
        public static final String REGISTRY_REHYDRATE = "REGISTRY_REHYDRATE";
        public static final String TIMEOUT = "TIMEOUT";
        public static final String EVENT_FIRED = "EVENT_FIRED";
        public static final String ERROR = "ERROR";
    }
}