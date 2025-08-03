package com.telcobright.statemachineexamples.callmachine.events;

import com.telcobright.statemachine.events.StateMachineEvent;

//@Data
//@EqualsAndHashCode(callSuper = false)
public class SessionProgress implements StateMachineEvent {
    public static final String EVENT_TYPE = "SESSION_PROGRESS";
    
    private final String progressType;
    private final int percentage;
    private final long timestamp;

    public SessionProgress(String progressType, int percentage) {
        this.progressType = progressType;
        this.percentage = percentage;
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }

    @Override
    public String getDescription() {
        return String.format("Session progress: %s (%d%%)", progressType, percentage);
    }

    @Override
    public Object getPayload() {
        return new Object() {
            public final String progressType = SessionProgress.this.progressType;
            public final int percentage = SessionProgress.this.percentage;
        };
    }

    // Explicitly provide getters since Lombok might not be recognized yet
    @Override
    public long getTimestamp() {
        return timestamp;
    }

    public String getProgressType() {
        return progressType;
    }

    public int getPercentage() {
        return percentage;
    }
}
