package com.telcobright.statemachine.batch;

import com.telcobright.statemachine.persistence.MysqlConnectionProvider;
import com.telcobright.statemachine.pool.StringBuilderPool;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;

/**
 * High-performance batch registry logger for telecom realtime processing
 * Batches registry event logging to reduce I/O overhead
 */
public class BatchRegistryLogger {
    
    // Batch configuration
    private static final int DEFAULT_BATCH_SIZE = 500;
    private static final int DEFAULT_FLUSH_INTERVAL_MS = 50;
    
    // Batch storage
    private final ConcurrentLinkedQueue<RegistryEvent> eventBatch = new ConcurrentLinkedQueue<>();
    private final AtomicInteger currentBatchSize = new AtomicInteger(0);
    
    // Database connection
    private final MysqlConnectionProvider connectionProvider;
    private final String registryTableName;
    
    // Async processing
    private final ScheduledExecutorService batchFlushExecutor;
    private final ExecutorService batchProcessingExecutor;
    private volatile boolean shutdown = false;
    
    // Statistics
    private final AtomicLong totalEventsQueued = new AtomicLong(0);
    private final AtomicLong totalEventsFlushed = new AtomicLong(0);
    private final AtomicLong totalBatchesProcessed = new AtomicLong(0);
    private final AtomicLong totalFlushTime = new AtomicLong(0);
    
    // Configuration
    private final int batchSize;
    private final int flushIntervalMs;
    
    public BatchRegistryLogger(String registryId) {
        this(registryId, DEFAULT_BATCH_SIZE, DEFAULT_FLUSH_INTERVAL_MS);
    }
    
    public BatchRegistryLogger(String registryId, int batchSize, int flushIntervalMs) {
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
        this.registryTableName = "registry_" + registryId;
        this.connectionProvider = new MysqlConnectionProvider();
        
        // Create optimized thread pools
        this.batchFlushExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BatchRegistryLogger-Flush-" + registryId);
            t.setDaemon(true);
            return t;
        });
        
        this.batchProcessingExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "BatchRegistryLogger-Process-" + registryId);
            t.setDaemon(true);
            return t;
        });
        
        // Schedule periodic flush
        startPeriodicFlush();
    }
    
    private void startPeriodicFlush() {
        batchFlushExecutor.scheduleWithFixedDelay(
            this::flushBatch, 
            flushIntervalMs, 
            flushIntervalMs, 
            TimeUnit.MILLISECONDS
        );
    }
    
    /**
     * Log registry event - high-performance async batching
     */
    public void logEvent(String machineId, String eventType, String reason, long timestamp) {
        if (shutdown) {
            return;
        }
        
        RegistryEvent event = new RegistryEvent(machineId, eventType, reason, timestamp);
        eventBatch.offer(event);
        
        int newSize = currentBatchSize.incrementAndGet();
        totalEventsQueued.incrementAndGet();
        
        // Trigger immediate flush if batch is full
        if (newSize >= batchSize) {
            flushBatch();
        }
    }
    
    /**
     * Flush current batch
     */
    public void flushBatch() {
        if (shutdown || currentBatchSize.get() == 0) {
            return;
        }
        
        // Create batch copy for processing
        List<RegistryEvent> batchCopy = new ArrayList<>();
        RegistryEvent event;
        int count = 0;
        
        // Extract events from queue
        while ((event = eventBatch.poll()) != null && count < batchSize) {
            batchCopy.add(event);
            count++;
        }
        
        if (batchCopy.isEmpty()) {
            return;
        }
        
        // Update size counter
        currentBatchSize.addAndGet(-count);
        
        // Process batch asynchronously
        batchProcessingExecutor.submit(() -> processBatch(batchCopy));
    }
    
    /**
     * Process batch of registry events
     */
    private void processBatch(List<RegistryEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        
        long startTime = System.currentTimeMillis();
        
        try (Connection conn = connectionProvider.getConnection()) {
            conn.setAutoCommit(false);
            
            // Build optimized batch insert SQL
            StringBuilder sql = StringBuilderPool.getInstance().borrowLogBuilderGlobal();
            try {
                sql.append("INSERT INTO ").append(registryTableName)
                   .append(" (machine_id, event_type, reason, event_timestamp) VALUES ");
                
                // Build batch values
                for (int i = 0; i < events.size(); i++) {
                    if (i > 0) sql.append(", ");
                    sql.append("(?, ?, ?, ?)");
                }
                
                try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                    int paramIndex = 1;
                    
                    for (RegistryEvent event : events) {
                        stmt.setString(paramIndex++, event.machineId);
                        stmt.setString(paramIndex++, event.eventType);
                        stmt.setString(paramIndex++, event.reason);
                        stmt.setTimestamp(paramIndex++, new java.sql.Timestamp(event.timestamp));
                    }
                    
                    stmt.executeBatch();
                    conn.commit();
                    
                    totalEventsFlushed.addAndGet(events.size());
                    totalBatchesProcessed.incrementAndGet();
                    
                } catch (SQLException e) {
                    conn.rollback();
                    System.err.println("Registry batch insert failed: " + e.getMessage());
                }
            } finally {
                StringBuilderPool.getInstance().returnLogBuilder(sql);
            }
            
        } catch (SQLException e) {
            System.err.println("Registry database connection failed for batch processing: " + e.getMessage());
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            totalFlushTime.addAndGet(duration);
        }
    }
    
    /**
     * Get batch processing statistics
     */
    public String getStatistics() {
        long queued = totalEventsQueued.get();
        long flushed = totalEventsFlushed.get();
        long batches = totalBatchesProcessed.get();
        long avgFlushTime = batches > 0 ? totalFlushTime.get() / batches : 0;
        int pending = eventBatch.size();
        
        return String.format(
            "BatchRegistryLogger[%s]: queued=%d, flushed=%d, pending=%d, batches=%d, avg_flush_time=%dms",
            registryTableName, queued, flushed, pending, batches, avgFlushTime
        );
    }
    
    /**
     * Shutdown batch logger gracefully
     */
    public void shutdown() {
        shutdown = true;
        
        // Flush remaining events
        flushBatch();
        
        // Wait for processing to complete
        batchFlushExecutor.shutdown();
        batchProcessingExecutor.shutdown();
        
        try {
            if (!batchProcessingExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                batchProcessingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            batchProcessingExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        try {
            connectionProvider.close();
        } catch (Exception e) {
            System.err.println("Error closing registry logger connection: " + e.getMessage());
        }
    }
    
    /**
     * Registry event data structure
     */
    public static class RegistryEvent {
        public final String machineId;
        public final String eventType;
        public final String reason;
        public final long timestamp;
        
        public RegistryEvent(String machineId, String eventType, String reason, long timestamp) {
            this.machineId = machineId;
            this.eventType = eventType;
            this.reason = reason;
            this.timestamp = timestamp;
        }
    }
}