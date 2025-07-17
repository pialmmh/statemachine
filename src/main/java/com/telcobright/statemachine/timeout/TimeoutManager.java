package com.telcobright.statemachine.timeout;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Manages timeout scheduling for state machines
 */
public class TimeoutManager {
    private final ScheduledExecutorService scheduler;
    
    public TimeoutManager() {
        this.scheduler = Executors.newScheduledThreadPool(4);
    }
    
    public ScheduledFuture<?> scheduleTimeout(Runnable timeoutAction, long delay, TimeUnit unit) {
        return scheduler.schedule(timeoutAction, delay, unit);
    }
    
    public void shutdown() {
        scheduler.shutdown();
    }
    
    public void shutdownNow() {
        scheduler.shutdownNow();
    }
}
