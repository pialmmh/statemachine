package com.telcobright.statemachine.websocket;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;

/**
 * WebSocket message logger with daily rotation
 */
public class WebSocketLogger {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final String LOG_DIR = "websocket-logs";
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final BlockingQueue<LogEntry> logQueue = new LinkedBlockingQueue<>();
    private volatile boolean running = true;
    private String currentDate;
    private PrintWriter currentWriter;
    
    private static class LogEntry {
        final LocalDateTime timestamp;
        final String direction; // "IN" or "OUT"
        final String machineId;
        final String message;
        
        LogEntry(String direction, String machineId, String message) {
            this.timestamp = LocalDateTime.now();
            this.direction = direction;
            this.machineId = machineId;
            this.message = message;
        }
    }
    
    public WebSocketLogger() {
        // Create log directory
        try {
            Files.createDirectories(Paths.get(LOG_DIR));
        } catch (IOException e) {
            System.err.println("Failed to create log directory: " + e.getMessage());
        }
        
        // Start the logger thread
        executor.submit(this::runLogger);
    }
    
    private void runLogger() {
        while (running || !logQueue.isEmpty()) {
            try {
                LogEntry entry = logQueue.poll(100, TimeUnit.MILLISECONDS);
                if (entry != null) {
                    writeEntry(entry);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        closeCurrentWriter();
    }
    
    private synchronized void writeEntry(LogEntry entry) {
        try {
            String date = entry.timestamp.format(DATE_FORMAT);
            
            // Check if we need to rotate the log file
            if (!date.equals(currentDate)) {
                closeCurrentWriter();
                currentDate = date;
                String filename = String.format("%s/ws-%s.log", LOG_DIR, date);
                currentWriter = new PrintWriter(new FileWriter(filename, true));
                System.out.println("[WSLogger] Logging to: " + filename);
            }
            
            // Write the log entry
            String logLine = String.format("[%s] [%s] [%s] %s",
                entry.timestamp.format(TIME_FORMAT),
                entry.direction,
                entry.machineId != null ? entry.machineId : "SYSTEM",
                entry.message
            );
            
            currentWriter.println(logLine);
            currentWriter.flush();
            
        } catch (IOException e) {
            System.err.println("Failed to write log entry: " + e.getMessage());
        }
    }
    
    private void closeCurrentWriter() {
        if (currentWriter != null) {
            currentWriter.close();
            currentWriter = null;
        }
    }
    
    public void logIncoming(String machineId, String message) {
        logQueue.offer(new LogEntry("IN", machineId, message));
    }
    
    public void logOutgoing(String machineId, String message) {
        logQueue.offer(new LogEntry("OUT", machineId, message));
    }
    
    public void logSystem(String message) {
        logQueue.offer(new LogEntry("SYS", null, message));
    }
    
    public void shutdown() {
        running = false;
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}