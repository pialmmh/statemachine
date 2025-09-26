package com.telcobright.statewalk.examples;

import com.telcobright.statewalk.annotation.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Call entity with event history
 */
public class Call {

    private String callId;
    private String callerNumber;
    private String calleeNumber;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String callStatus;
    private Integer durationSeconds;

    @Entity(table = "call_events", relation = RelationType.ONE_TO_MANY)
    private List<CallEvent> events = new ArrayList<>();

    private DeviceInfo deviceInfo; // Shared singleton instance

    // Constructors
    public Call() {
        this.startTime = LocalDateTime.now();
        this.callStatus = "INITIATED";
    }

    public Call(String callId, String callerNumber, String calleeNumber) {
        this();
        this.callId = callId;
        this.callerNumber = callerNumber;
        this.calleeNumber = calleeNumber;
    }

    // Event management
    public void addEvent(CallEvent event) {
        if (events == null) {
            events = new ArrayList<>();
        }
        event.setCallId(this.callId);
        events.add(event);
    }

    // Getters and setters
    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
        // Update events with call ID
        if (events != null) {
            for (CallEvent event : events) {
                event.setCallId(callId);
            }
        }
    }

    public String getCallerNumber() {
        return callerNumber;
    }

    public void setCallerNumber(String callerNumber) {
        this.callerNumber = callerNumber;
    }

    public String getCalleeNumber() {
        return calleeNumber;
    }

    public void setCalleeNumber(String calleeNumber) {
        this.calleeNumber = calleeNumber;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
        // Calculate duration
        if (startTime != null && endTime != null) {
            this.durationSeconds = (int) java.time.Duration.between(startTime, endTime).getSeconds();
        }
    }

    public String getCallStatus() {
        return callStatus;
    }

    public void setCallStatus(String callStatus) {
        this.callStatus = callStatus;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public List<CallEvent> getEvents() {
        return events;
    }

    public void setEvents(List<CallEvent> events) {
        this.events = events;
    }

    public DeviceInfo getDeviceInfo() {
        return deviceInfo;
    }

    public void setDeviceInfo(DeviceInfo deviceInfo) {
        this.deviceInfo = deviceInfo;
    }
}