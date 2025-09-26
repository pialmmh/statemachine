package com.telcobright.statewalk.examples;

import java.time.LocalDateTime;

/**
 * Individual call event
 */
public class CallEvent {

    private String eventId;
    private String callId;
    private String eventType;
    private String eventData;
    private LocalDateTime timestamp;

    public CallEvent() {
        this.timestamp = LocalDateTime.now();
        this.eventId = java.util.UUID.randomUUID().toString();
    }

    public CallEvent(String eventType, String eventData) {
        this();
        this.eventType = eventType;
        this.eventData = eventData;
    }

    // Getters and setters
    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEventData() {
        return eventData;
    }

    public void setEventData(String eventData) {
        this.eventData = eventData;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}