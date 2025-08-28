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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance batch history logger for telecom realtime processing
 * Batches database operations to reduce I/O overhead and improve throughput
 */
public class BatchHistoryLogger {
    
    // Batch configuration
    private static final int DEFAULT_BATCH_SIZE = 1000;
    private static final int DEFAULT_FLUSH_INTERVAL_MS = 100;
    private static final int MAX_PENDING_BATCHES = 10000;
    
    // Batch storage per table
    private final Map<String, ConcurrentLinkedQueue<HistoryEvent>> tableBatches = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> batchSizes = new ConcurrentHashMap<>();
    
    // Database connection
    private final MysqlConnectionProvider connectionProvider;
    
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
    
    public BatchHistoryLogger() {
        this(DEFAULT_BATCH_SIZE, DEFAULT_FLUSH_INTERVAL_MS);
    }
    
    public BatchHistoryLogger(int batchSize, int flushIntervalMs) {
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
        this.connectionProvider = new MysqlConnectionProvider();
        
        // Create thread pools optimized for batch processing
        this.batchFlushExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BatchHistoryLogger-Flush");
            t.setDaemon(true);
            return t;
        });
        
        this.batchProcessingExecutor = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2), 
            r -> {
                Thread t = new Thread(r, "BatchHistoryLogger-Process");
                t.setDaemon(true);
                return t;
            }
        );
        
        // Schedule periodic flush
        startPeriodicFlush();
    }
    
    private void startPeriodicFlush() {
        batchFlushExecutor.scheduleWithFixedDelay(
            this::flushAllBatches, 
            flushIntervalMs, 
            flushIntervalMs, 
            TimeUnit.MILLISECONDS
        );
    }
    
    /**
     * Log history event - high-performance async batching
     */
    public void logEvent(String machineId, String eventType, String fromState, String toState, 
                        long timestamp, String additionalData) {
        
        if (shutdown) {
            return;
        }
        
        // Create history event
        HistoryEvent event = new HistoryEvent(machineId, eventType, fromState, toState, timestamp, additionalData);
        
        // Determine table name
        String tableName = "history_" + machineId.replace("-", "_");
        
        // Add to batch
        ConcurrentLinkedQueue<HistoryEvent> batch = tableBatches.computeIfAbsent(
            tableName, k -> new ConcurrentLinkedQueue<>()
        );
        AtomicInteger currentSize = batchSizes.computeIfAbsent(
            tableName, k -> new AtomicInteger(0)
        );
        
        batch.offer(event);
        int newSize = currentSize.incrementAndGet();
        totalEventsQueued.incrementAndGet();
        
        // Trigger immediate flush if batch is full
        if (newSize >= batchSize) {
            flushTableBatch(tableName);
        }
    }
    
    /**
     * Flush all pending batches
     */
    public void flushAllBatches() {
        for (String tableName : tableBatches.keySet()) {
            if (batchSizes.get(tableName).get() > 0) {
                flushTableBatch(tableName);
            }
        }
    }
    
    /**
     * Flush batch for specific table
     */
    private void flushTableBatch(String tableName) {
        if (shutdown) {
            return;
        }
        
        ConcurrentLinkedQueue<HistoryEvent> batch = tableBatches.get(tableName);
        AtomicInteger currentSize = batchSizes.get(tableName);
        
        if (batch == null || currentSize.get() == 0) {
            return;
        }
        
        // Create batch copy for processing
        List<HistoryEvent> batchCopy = new ArrayList<>();
        HistoryEvent event;
        int count = 0;
        
        // Extract events from queue
        while ((event = batch.poll()) != null && count < batchSize) {
            batchCopy.add(event);
            count++;
        }
        
        if (batchCopy.isEmpty()) {
            return;
        }
        
        // Update size counter
        currentSize.addAndGet(-count);
        
        // Process batch asynchronously
        batchProcessingExecutor.submit(() -> processBatch(tableName, batchCopy));
    }
    
    /**
     * Process batch of events for single table
     */
    private void processBatch(String tableName, List<HistoryEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        
        long startTime = System.currentTimeMillis();
        
        try (Connection conn = connectionProvider.getConnection()) {
            conn.setAutoCommit(false);
            
            // Build optimized batch insert SQL
            StringBuilder sql = StringBuilderPool.getInstance().borrowLogBuilderGlobal();
            try {
                sql.append("INSERT INTO ").append(tableName)
                   .append(" (machine_id, event_type, from_state, to_state, event_timestamp, additional_data) VALUES ");
                
                // Build batch values
                for (int i = 0; i < events.size(); i++) {
                    if (i > 0) sql.append(", ");
                    sql.append("(?, ?, ?, ?, ?, ?)");
                }
                
                try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                    int paramIndex = 1;
                    
                    for (HistoryEvent event : events) {
                        stmt.setString(paramIndex++, event.machineId);
                        stmt.setString(paramIndex++, event.eventType);
                        stmt.setString(paramIndex++, event.fromState);
                        stmt.setString(paramIndex++, event.toState);
                        stmt.setTimestamp(paramIndex++, new java.sql.Timestamp(event.timestamp));
                        stmt.setString(paramIndex++, event.additionalData);
                    }
                    
                    stmt.executeBatch();
                    conn.commit();
                    
                    totalEventsFlushed.addAndGet(events.size());
                    totalBatchesProcessed.incrementAndGet();
                    
                } catch (SQLException e) {
                    conn.rollback();
                    System.err.println("Batch insert failed for table " + tableName + ": " + e.getMessage());
                }
            } finally {
                StringBuilderPool.getInstance().returnLogBuilder(sql);
            }
            
        } catch (SQLException e) {
            System.err.println("Database connection failed for batch processing: " + e.getMessage());
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
        
        int pendingEvents = tableBatches.values().stream()
            .mapToInt(queue -> queue.size())
            .sum();
        
        return String.format(
            "BatchHistoryLogger: queued=%d, flushed=%d, pending=%d, batches=%d, avg_flush_time=%dms, tables=%d",
            queued, flushed, pendingEvents, batches, avgFlushTime, tableBatches.size()
        );
    }
    
    /**
     * Shutdown batch logger gracefully
     */
    public void shutdown() {
        shutdown = true;
        
        // Flush all pending batches
        flushAllBatches();
        
        // Wait for processing to complete
        batchFlushExecutor.shutdown();
        batchProcessingExecutor.shutdown();
        
        try {
            if (!batchProcessingExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                batchProcessingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            batchProcessingExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        try {
            connectionProvider.close();
        } catch (Exception e) {
            System.err.println("Error closing connection provider: " + e.getMessage());
        }
    }
    
    /**
     * History event data structure
     */
    public static class HistoryEvent {
        public final String machineId;
        public final String eventType;
        public final String fromState;
        public final String toState;
        public final long timestamp;
        public final String additionalData;
        
        public HistoryEvent(String machineId, String eventType, String fromState, String toState, 
                           long timestamp, String additionalData) {
            this.machineId = machineId;
            this.eventType = eventType;
            this.fromState = fromState;
            this.toState = toState;
            this.timestamp = timestamp;
            this.additionalData = additionalData;
        }
    }
}