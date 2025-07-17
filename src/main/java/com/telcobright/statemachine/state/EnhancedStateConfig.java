package com.telcobright.statemachine.state;

import java.util.function.Consumer;

import com.telcobright.statemachine.timeout.TimeoutConfig;

/**
 * Enhanced state configuration with timeout, offline support, and entry/exit actions
 */
public class EnhancedStateConfig {
    private final String stateId;
    private boolean isOffline = false;
    private TimeoutConfig timeoutConfig;
    private Runnable entryAction;
    private Runnable exitAction;
    private Consumer<String> onEntry;
    private Consumer<String> onExit;
    
    public EnhancedStateConfig(String stateId) {
        this.stateId = stateId;
    }
    
    public EnhancedStateConfig timeout(TimeoutConfig timeoutConfig) {
        this.timeoutConfig = timeoutConfig;
        return this;
    }
    
    public EnhancedStateConfig offline() {
        this.isOffline = true;
        return this;
    }
    
    public EnhancedStateConfig offline(boolean isOffline) {
        this.isOffline = isOffline;
        return this;
    }
    
    public EnhancedStateConfig onEntry(Runnable action) {
        this.entryAction = action;
        return this;
    }
    
    public EnhancedStateConfig onExit(Runnable action) {
        this.exitAction = action;
        return this;
    }
    
    public EnhancedStateConfig onEntry(Consumer<String> action) {
        this.onEntry = action;
        return this;
    }
    
    public EnhancedStateConfig onExit(Consumer<String> action) {
        this.onExit = action;
        return this;
    }
    
    // Getters
    public String getStateId() { return stateId; }
    public boolean isOffline() { return isOffline; }
    public TimeoutConfig getTimeoutConfig() { return timeoutConfig; }
    public Runnable getEntryAction() { return entryAction; }
    public Runnable getExitAction() { return exitAction; }
    public Consumer<String> getOnEntry() { return onEntry; }
    public Consumer<String> getOnExit() { return onExit; }
    
    public boolean hasTimeout() {
        return timeoutConfig != null;
    }
}
