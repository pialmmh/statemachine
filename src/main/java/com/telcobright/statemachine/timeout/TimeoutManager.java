package com.telcobright.statemachine.timeout;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generic timeout manager for all types of state machines.
 * This class is thread-safe and can be shared across multiple state machines
 * of different types (Call, SMS, Order, etc.).
 * 
 * Features:
 * - Configurable thread pool size
 * - Named threads for better debugging
 * - Timeout tracking and statistics
 * - Graceful shutdown support
 * - Memory-efficient for large numbers of timeouts
 */
public class TimeoutManager {
    private static final int DEFAULT_THREAD_POOL_SIZE = 4;
    private static final String THREAD_NAME_PREFIX = "StateMachine-Timeout-";
    
    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> activeTimeouts;
    private final AtomicLong scheduledCount;
    private final AtomicLong executedCount;
    private final AtomicLong cancelledCount;
    private final String name;
    private final int threadPoolSize;
    
    /**
     * Create a TimeoutManager with default settings
     */
    public TimeoutManager() {
        this("Default", DEFAULT_THREAD_POOL_SIZE);
    }
    
    /**
     * Create a TimeoutManager with custom thread pool size
     * @param threadPoolSize number of threads in the pool
     */
    public TimeoutManager(int threadPoolSize) {
        this("Default", threadPoolSize);
    }
    
    /**
     * Create a named TimeoutManager with custom settings
     * @param name name for this manager (useful for debugging)
     * @param threadPoolSize number of threads in the pool
     */
    public TimeoutManager(String name, int threadPoolSize) {
        this.name = name;
        this.threadPoolSize = threadPoolSize;
        this.activeTimeouts = new ConcurrentHashMap<>();
        this.scheduledCount = new AtomicLong(0);
        this.executedCount = new AtomicLong(0);
        this.cancelledCount = new AtomicLong(0);
        
        // Create scheduler with named threads for better debugging
        this.scheduler = Executors.newScheduledThreadPool(threadPoolSize, new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, THREAD_NAME_PREFIX + name + "-" + threadNumber.getAndIncrement());
                thread.setDaemon(true); // Daemon threads don't prevent JVM shutdown
                return thread;
            }
        });
        
        // Configure the executor for better performance
        if (scheduler instanceof ScheduledThreadPoolExecutor) {
            ScheduledThreadPoolExecutor executor = (ScheduledThreadPoolExecutor) scheduler;
            executor.setRemoveOnCancelPolicy(true); // Remove cancelled tasks immediately
            executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
            executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        }
    }
    
    /**
     * Schedule a timeout action
     * @param timeoutAction the action to execute when timeout occurs
     * @param delay the delay before timeout
     * @param unit the time unit for delay
     * @return a ScheduledFuture that can be used to cancel the timeout
     */
    public ScheduledFuture<?> scheduleTimeout(Runnable timeoutAction, long delay, TimeUnit unit) {
        scheduledCount.incrementAndGet();
        
        // Wrap the action to track execution
        Runnable wrappedAction = () -> {
            try {
                executedCount.incrementAndGet();
                timeoutAction.run();
            } catch (Exception e) {
                System.err.println("[TimeoutManager-" + name + "] Error executing timeout action: " + e.getMessage());
                e.printStackTrace();
            }
        };
        
        return scheduler.schedule(wrappedAction, delay, unit);
    }
    
    /**
     * Schedule a timeout with tracking
     * @param machineId the state machine ID (for tracking)
     * @param timeoutAction the action to execute
     * @param delay the delay before timeout
     * @param unit the time unit
     * @return a ScheduledFuture that can be used to cancel the timeout
     */
    public ScheduledFuture<?> scheduleTrackedTimeout(String machineId, Runnable timeoutAction, long delay, TimeUnit unit) {
        // Cancel any existing timeout for this machine
        cancelTimeout(machineId);
        
        ScheduledFuture<?> future = scheduleTimeout(timeoutAction, delay, unit);
        activeTimeouts.put(machineId, future);
        return future;
    }
    
    /**
     * Cancel a tracked timeout for a specific machine
     * @param machineId the state machine ID
     * @return true if a timeout was cancelled, false otherwise
     */
    public boolean cancelTimeout(String machineId) {
        ScheduledFuture<?> future = activeTimeouts.remove(machineId);
        if (future != null && !future.isDone()) {
            boolean cancelled = future.cancel(false);
            if (cancelled) {
                cancelledCount.incrementAndGet();
            }
            return cancelled;
        }
        return false;
    }
    
    /**
     * Get statistics about timeout operations
     * @return a map of statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("name", name);
        stats.put("threadPoolSize", threadPoolSize);
        stats.put("scheduledCount", scheduledCount.get());
        stats.put("executedCount", executedCount.get());
        stats.put("cancelledCount", cancelledCount.get());
        stats.put("activeTimeouts", activeTimeouts.size());
        stats.put("pendingTasks", getPendingTaskCount());
        return stats;
    }
    
    /**
     * Get the number of pending timeout tasks
     * @return number of pending tasks
     */
    public long getPendingTaskCount() {
        if (scheduler instanceof ScheduledThreadPoolExecutor) {
            return ((ScheduledThreadPoolExecutor) scheduler).getQueue().size();
        }
        return -1; // Unknown
    }
    
    /**
     * Check if the manager is shutdown
     * @return true if shutdown, false otherwise
     */
    public boolean isShutdown() {
        return scheduler.isShutdown();
    }
    
    /**
     * Check if the manager is terminated
     * @return true if terminated, false otherwise
     */
    public boolean isTerminated() {
        return scheduler.isTerminated();
    }
    
    /**
     * Gracefully shutdown the timeout manager
     * Waits for currently executing tasks to complete
     */
    public void shutdown() {
        System.out.println("[TimeoutManager-" + name + "] Shutting down gracefully...");
        scheduler.shutdown();
        
        // Cancel all active tracked timeouts
        for (Map.Entry<String, ScheduledFuture<?>> entry : activeTimeouts.entrySet()) {
            entry.getValue().cancel(false);
        }
        activeTimeouts.clear();
    }
    
    /**
     * Forcefully shutdown the timeout manager
     * Attempts to stop all actively executing tasks
     */
    public void shutdownNow() {
        System.out.println("[TimeoutManager-" + name + "] Shutting down immediately...");
        scheduler.shutdownNow();
        activeTimeouts.clear();
    }
    
    /**
     * Wait for termination after shutdown
     * @param timeout the maximum time to wait
     * @param unit the time unit
     * @return true if terminated, false if timeout elapsed
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return scheduler.awaitTermination(timeout, unit);
    }
    
    @Override
    public String toString() {
        return String.format("TimeoutManager[name=%s, threads=%d, scheduled=%d, executed=%d, cancelled=%d, active=%d]",
            name, threadPoolSize, scheduledCount.get(), executedCount.get(), 
            cancelledCount.get(), activeTimeouts.size());
    }
}
