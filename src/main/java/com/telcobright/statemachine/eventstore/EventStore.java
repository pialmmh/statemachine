package com.telcobright.statemachine.eventstore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * File-based event store with automatic log rotation and retention management
 * Events are stored in daily JSON files with automatic cleanup of old files
 */
public class EventStore {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String FILE_EXTENSION = ".jsonl"; // JSON Lines format
    private static final String STORE_DIR_NAME = "event-store";
    
    private final Path storeDirectory;
    private final int retentionDays;
    private final Gson gson;
    private final ExecutorService writeExecutor;
    private final ScheduledExecutorService cleanupScheduler;
    private final AtomicBoolean isEnabled;
    private final BlockingQueue<EventLogEntry> writeQueue;
    private volatile boolean isShutdown = false;
    
    // Singleton instance
    private static EventStore instance;
    
    private EventStore(Path baseDirectory, int retentionDays) {
        this.storeDirectory = baseDirectory.resolve(STORE_DIR_NAME);
        this.retentionDays = retentionDays;
        this.isEnabled = new AtomicBoolean(false);
        this.writeQueue = new LinkedBlockingQueue<>();
        
        // Configure Gson with LocalDateTime support
        this.gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, 
                (JsonSerializer<LocalDateTime>) (src, typeOfSrc, context) -> 
                    context.serialize(src.format(DATETIME_FORMAT)))
            .registerTypeAdapter(LocalDateTime.class,
                (JsonDeserializer<LocalDateTime>) (json, typeOfT, context) ->
                    LocalDateTime.parse(json.getAsString(), DATETIME_FORMAT))
            .setPrettyPrinting()
            .create();
        
        // Single thread for writing events
        this.writeExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "EventStore-Writer");
            t.setDaemon(true);
            return t;
        });
        
        // Scheduler for cleanup tasks
        this.cleanupScheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "EventStore-Cleanup");
            t.setDaemon(true);
            return t;
        });
        
        // Initialize store
        initialize();
    }
    
    /**
     * Get or create the singleton instance
     */
    public static synchronized EventStore getInstance(Path baseDirectory, int retentionDays) {
        if (instance == null) {
            instance = new EventStore(baseDirectory, retentionDays);
        }
        return instance;
    }
    
    /**
     * Get the existing instance (returns null if not initialized)
     */
    public static EventStore getInstance() {
        return instance;
    }
    
    /**
     * Check if EventStore is initialized
     */
    public static boolean isInitialized() {
        return instance != null;
    }
    
    /**
     * Initialize the event store
     */
    private void initialize() {
        try {
            // Create store directory if it doesn't exist
            Files.createDirectories(storeDirectory);
            
            // Start the write worker
            writeExecutor.submit(this::writeWorker);
            
            // Schedule daily cleanup at midnight
            scheduleCleanup();
            
            // Initial cleanup on startup
            cleanupOldFiles();
            
            System.out.println("📁 EventStore initialized at: " + storeDirectory);
            System.out.println("   Retention: " + retentionDays + " days");
            
        } catch (IOException e) {
            System.err.println("Failed to initialize EventStore: " + e.getMessage());
        }
    }
    
    /**
     * Enable or disable event logging
     */
    public void setEnabled(boolean enabled) {
        this.isEnabled.set(enabled);
        System.out.println("📝 EventStore logging " + (enabled ? "ENABLED" : "DISABLED"));
    }
    
    /**
     * Check if event logging is enabled
     */
    public boolean isEnabled() {
        return isEnabled.get();
    }
    
    /**
     * Log an event asynchronously
     */
    public void logEvent(EventLogEntry event) {
        if (!isEnabled.get() || isShutdown) {
            return;
        }
        
        try {
            writeQueue.offer(event, 100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Log a state change event
     */
    public void logStateChange(String machineId, String stateBefore, String stateAfter, 
                               Map<String, Object> details, long processingTimeMs) {
        if (!isEnabled.get()) return;
        
        EventLogEntry event = new EventLogEntry.Builder()
            .eventCategory(EventLogEntry.Category.STATE_CHANGE)
            .eventType("STATE_TRANSITION")
            .source("StateMachine:" + machineId)
            .destination("Registry")
            .machineId(machineId)
            .stateBefore(stateBefore)
            .stateAfter(stateAfter)
            .eventDetails(details)
            .processingTimeMs(processingTimeMs)
            .build();
        
        logEvent(event);
    }
    
    /**
     * Log a WebSocket incoming event
     */
    public void logWebSocketIn(String clientAddress, String machineId, String eventType, 
                               Map<String, Object> payload, boolean success, String error) {
        if (!isEnabled.get()) return;
        
        EventLogEntry.Builder builder = new EventLogEntry.Builder()
            .eventCategory(EventLogEntry.Category.WEBSOCKET_IN)
            .eventType(eventType)
            .source("WebSocketClient:" + clientAddress)
            .destination("StateMachine:" + machineId)
            .machineId(machineId)
            .eventDetails(payload)
            .success(success);
        
        if (error != null) {
            builder.errorMessage(error);
        }
        
        logEvent(builder.build());
    }
    
    /**
     * Log a WebSocket outgoing event
     */
    public void logWebSocketOut(String machineId, String eventType, String stateBefore, 
                                String stateAfter, Map<String, Object> details) {
        if (!isEnabled.get()) return;
        
        EventLogEntry event = new EventLogEntry.Builder()
            .eventCategory(EventLogEntry.Category.WEBSOCKET_OUT)
            .eventType(eventType)
            .source("StateMachine:" + machineId)
            .destination("WebSocketClients")
            .machineId(machineId)
            .stateBefore(stateBefore)
            .stateAfter(stateAfter)
            .eventDetails(details)
            .build();
        
        logEvent(event);
    }
    
    /**
     * Log a registry event
     */
    public void logRegistryEvent(String eventType, String machineId, Map<String, Object> details) {
        if (!isEnabled.get()) return;
        
        String category;
        switch (eventType) {
            case "CREATE":
                category = EventLogEntry.Category.REGISTRY_CREATE;
                break;
            case "REMOVE":
                category = EventLogEntry.Category.REGISTRY_REMOVE;
                break;
            case "REHYDRATE":
                category = EventLogEntry.Category.REGISTRY_REHYDRATE;
                break;
            default:
                category = "REGISTRY_" + eventType;
        }
        
        EventLogEntry event = new EventLogEntry.Builder()
            .eventCategory(category)
            .eventType(eventType)
            .source("Registry")
            .destination("StateMachine:" + machineId)
            .machineId(machineId)
            .eventDetails(details)
            .build();
        
        logEvent(event);
    }
    
    /**
     * Log a timeout event
     */
    public void logTimeoutEvent(String machineId, String currentState, String targetState, 
                                long timeoutDuration) {
        if (!isEnabled.get()) return;
        
        Map<String, Object> details = new HashMap<>();
        details.put("timeoutDuration", timeoutDuration);
        details.put("currentState", currentState);
        details.put("targetState", targetState);
        
        EventLogEntry event = new EventLogEntry.Builder()
            .eventCategory(EventLogEntry.Category.TIMEOUT)
            .eventType("TIMEOUT_FIRED")
            .source("TimeoutManager")
            .destination("StateMachine:" + machineId)
            .machineId(machineId)
            .stateBefore(currentState)
            .stateAfter(targetState)
            .eventDetails(details)
            .build();
        
        logEvent(event);
    }
    
    /**
     * Worker thread for writing events to files
     */
    private void writeWorker() {
        while (!isShutdown) {
            try {
                EventLogEntry event = writeQueue.poll(1, TimeUnit.SECONDS);
                if (event != null) {
                    writeEventToFile(event);
                    
                    // Batch write if queue has more events
                    List<EventLogEntry> batch = new ArrayList<>();
                    writeQueue.drainTo(batch, 100);
                    for (EventLogEntry e : batch) {
                        writeEventToFile(e);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Error in EventStore write worker: " + e.getMessage());
            }
        }
    }
    
    /**
     * Write a single event to the appropriate daily file
     */
    private void writeEventToFile(EventLogEntry event) {
        try {
            LocalDate date = event.getTimestamp().toLocalDate();
            Path filePath = getFilePathForDate(date);
            
            // Convert event to JSON
            String json = gson.toJson(event);
            
            // Append to file (create if doesn't exist)
            Files.write(filePath, 
                       (json + System.lineSeparator()).getBytes(),
                       StandardOpenOption.CREATE, 
                       StandardOpenOption.APPEND);
                       
        } catch (IOException e) {
            System.err.println("Failed to write event to file: " + e.getMessage());
        }
    }
    
    /**
     * Get the file path for a specific date
     */
    private Path getFilePathForDate(LocalDate date) {
        String filename = "events-" + date.format(DATE_FORMAT) + FILE_EXTENSION;
        return storeDirectory.resolve(filename);
    }
    
    /**
     * Schedule daily cleanup task
     */
    private void scheduleCleanup() {
        // Calculate delay until next midnight
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay();
        long initialDelay = java.time.Duration.between(now, nextMidnight).toMinutes();
        
        // Schedule cleanup to run daily at midnight
        cleanupScheduler.scheduleAtFixedRate(
            this::cleanupOldFiles,
            initialDelay,
            24 * 60, // 24 hours in minutes
            TimeUnit.MINUTES
        );
    }
    
    /**
     * Clean up files older than retention period
     */
    private void cleanupOldFiles() {
        try {
            LocalDate cutoffDate = LocalDate.now().minusDays(retentionDays);
            
            // List all event files
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(storeDirectory, "events-*.jsonl")) {
                for (Path file : stream) {
                    String filename = file.getFileName().toString();
                    // Extract date from filename
                    String dateStr = filename.substring(7, 17); // "events-YYYY-MM-DD.jsonl"
                    LocalDate fileDate = LocalDate.parse(dateStr, DATE_FORMAT);
                    
                    // Delete if older than retention period
                    if (fileDate.isBefore(cutoffDate)) {
                        Files.deleteIfExists(file);
                        System.out.println("🗑️ Deleted old event file: " + filename);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }
    
    /**
     * Read events for a specific date
     */
    public List<EventLogEntry> readEvents(LocalDate date) {
        List<EventLogEntry> events = new ArrayList<>();
        Path filePath = getFilePathForDate(date);
        
        if (Files.exists(filePath)) {
            try (BufferedReader reader = Files.newBufferedReader(filePath)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        EventLogEntry event = gson.fromJson(line, EventLogEntry.class);
                        events.add(event);
                    } catch (Exception e) {
                        System.err.println("Failed to parse event: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                System.err.println("Failed to read events file: " + e.getMessage());
            }
        }
        
        return events;
    }
    
    /**
     * Read events for a date range
     */
    public List<EventLogEntry> readEvents(LocalDate startDate, LocalDate endDate) {
        List<EventLogEntry> allEvents = new ArrayList<>();
        
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            allEvents.addAll(readEvents(current));
            current = current.plusDays(1);
        }
        
        return allEvents;
    }
    
    /**
     * Get events for a specific machine
     */
    public List<EventLogEntry> getEventsForMachine(String machineId, LocalDate date) {
        return readEvents(date).stream()
            .filter(e -> machineId.equals(e.getMachineId()))
            .collect(Collectors.toList());
    }
    
    /**
     * Get store statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            long totalFiles = Files.list(storeDirectory)
                .filter(p -> p.toString().endsWith(FILE_EXTENSION))
                .count();
            
            long totalSize = Files.list(storeDirectory)
                .filter(p -> p.toString().endsWith(FILE_EXTENSION))
                .mapToLong(p -> {
                    try {
                        return Files.size(p);
                    } catch (IOException e) {
                        return 0;
                    }
                }).sum();
            
            stats.put("totalFiles", totalFiles);
            stats.put("totalSizeBytes", totalSize);
            stats.put("totalSizeMB", totalSize / (1024.0 * 1024.0));
            stats.put("retentionDays", retentionDays);
            stats.put("storeDirectory", storeDirectory.toString());
            stats.put("queueSize", writeQueue.size());
            stats.put("enabled", isEnabled.get());
            
        } catch (IOException e) {
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }
    
    /**
     * Shutdown the event store
     */
    public void shutdown() {
        isShutdown = true;
        
        // Stop accepting new events
        isEnabled.set(false);
        
        // Flush remaining events
        int remaining = writeQueue.size();
        if (remaining > 0) {
            System.out.println("Flushing " + remaining + " remaining events...");
            EventLogEntry event;
            while ((event = writeQueue.poll()) != null) {
                writeEventToFile(event);
            }
        }
        
        // Shutdown executors
        writeExecutor.shutdown();
        cleanupScheduler.shutdown();
        
        try {
            if (!writeExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                writeExecutor.shutdownNow();
            }
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            writeExecutor.shutdownNow();
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        System.out.println("📁 EventStore shutdown complete");
    }
}