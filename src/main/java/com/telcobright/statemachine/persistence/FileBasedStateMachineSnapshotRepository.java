package com.telcobright.statemachine.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * File-based implementation of StateMachineSnapshotRepository
 * Persists snapshots as structured text files to disk for durability across application restarts
 */
public class FileBasedStateMachineSnapshotRepository implements StateMachineSnapshotRepository {
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final Path persistenceDir;
    private final ExecutorService executor;
    private final Map<String, Long> lastSnapshotIds = new ConcurrentHashMap<>();
    private Long nextId = 1L;
    
    public FileBasedStateMachineSnapshotRepository(String persistenceDirectory) {
        this.persistenceDir = Paths.get(persistenceDirectory);
        this.executor = Executors.newFixedThreadPool(2);
        
        // Create persistence directory if it doesn't exist
        try {
            Files.createDirectories(persistenceDir);
            loadExistingSnapshots();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize file-based persistence directory: " + persistenceDirectory, e);
        }
    }
    
    /**
     * Load existing snapshots from disk to determine next ID
     */
    private void loadExistingSnapshots() throws IOException {
        if (!Files.exists(persistenceDir)) {
            return;
        }
        
        Files.list(persistenceDir)
             .filter(path -> path.toString().endsWith(".snapshot"))
             .forEach(path -> {
                 try {
                     StateMachineSnapshotEntity snapshot = readSnapshotFromFile(path);
                     if (snapshot.getId() != null && snapshot.getId() >= nextId) {
                         nextId = snapshot.getId() + 1;
                     }
                     
                     // Track the latest snapshot for each machine
                     String machineId = snapshot.getMachineId();
                     lastSnapshotIds.merge(machineId, snapshot.getId(), Long::max);
                     
                 } catch (IOException e) {
                     System.err.println("Failed to load snapshot from " + path + ": " + e.getMessage());
                 }
             });
    }
    
    @Override
    public void saveAsync(StateMachineSnapshotEntity snapshot) {
        CompletableFuture.runAsync(() -> {
            try {
                // Assign ID if not present
                synchronized (this) {
                    if (snapshot.getId() == null) {
                        snapshot.setId(nextId++);
                    }
                }
                
                // Create filename with timestamp and machine ID for easy identification
                String filename = String.format("%s_snapshot_%d_%s.snapshot",
                    snapshot.getMachineId(),
                    snapshot.getId(),
                    LocalDateTime.now().toString().replace(":", "-"));
                
                Path snapshotFile = persistenceDir.resolve(filename);
                
                // Write to file
                writeSnapshotToFile(snapshot, snapshotFile);
                
                // Update tracking
                lastSnapshotIds.put(snapshot.getMachineId(), snapshot.getId());
                
                System.out.println("Persisted snapshot for machine " + snapshot.getMachineId() + 
                                 " in state " + snapshot.getStateId() + " to file: " + filename);
                
            } catch (IOException e) {
                System.err.println("Failed to persist snapshot for machine " + snapshot.getMachineId() + ": " + e.getMessage());
            }
        }, executor);
    }
    
    @Override
    public Optional<StateMachineSnapshotEntity> findLatestByMachineId(String machineId) {
        try {
            if (!Files.exists(persistenceDir)) {
                return Optional.empty();
            }
            
            return Files.list(persistenceDir)
                        .filter(path -> path.getFileName().toString().startsWith(machineId + "_snapshot_"))
                        .filter(path -> path.toString().endsWith(".snapshot"))
                        .map(path -> {
                            try {
                                return readSnapshotFromFile(path);
                            } catch (IOException e) {
                                System.err.println("Failed to read snapshot from " + path + ": " + e.getMessage());
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .filter(snapshot -> machineId.equals(snapshot.getMachineId()))
                        .max(Comparator.comparing(snapshot -> 
                            snapshot.getTimestamp() != null ? snapshot.getTimestamp() : LocalDateTime.MIN));
                        
        } catch (IOException e) {
            System.err.println("Failed to list snapshots for machine " + machineId + ": " + e.getMessage());
            return Optional.empty();
        }
    }
    
    @Override
    public List<StateMachineSnapshotEntity> findAllByMachineId(String machineId) {
        try {
            if (!Files.exists(persistenceDir)) {
                return new ArrayList<>();
            }
            
            return Files.list(persistenceDir)
                        .filter(path -> path.getFileName().toString().startsWith(machineId + "_snapshot_"))
                        .filter(path -> path.toString().endsWith(".snapshot"))
                        .map(path -> {
                            try {
                                return readSnapshotFromFile(path);
                            } catch (IOException e) {
                                System.err.println("Failed to read snapshot from " + path + ": " + e.getMessage());
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .filter(snapshot -> machineId.equals(snapshot.getMachineId()))
                        .sorted(Comparator.comparing(snapshot -> 
                            snapshot.getTimestamp() != null ? snapshot.getTimestamp() : LocalDateTime.MIN))
                        .collect(Collectors.toList());
                        
        } catch (IOException e) {
            System.err.println("Failed to list snapshots for machine " + machineId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public void deleteByMachineId(String machineId) {
        try {
            if (!Files.exists(persistenceDir)) {
                return;
            }
            
            List<Path> filesToDelete = Files.list(persistenceDir)
                                           .filter(path -> path.getFileName().toString().startsWith(machineId + "_snapshot_"))
                                           .filter(path -> path.toString().endsWith(".snapshot"))
                                           .collect(Collectors.toList());
            
            for (Path file : filesToDelete) {
                Files.deleteIfExists(file);
            }
            
            lastSnapshotIds.remove(machineId);
            System.out.println("Deleted " + filesToDelete.size() + " snapshot files for machine " + machineId);
            
        } catch (IOException e) {
            System.err.println("Failed to delete snapshots for machine " + machineId + ": " + e.getMessage());
        }
    }
    
    @Override
    public List<StateMachineSnapshotEntity> findAllOfflineSnapshots() {
        try {
            if (!Files.exists(persistenceDir)) {
                return new ArrayList<>();
            }
            
            return Files.list(persistenceDir)
                        .filter(path -> path.toString().endsWith(".snapshot"))
                        .map(path -> {
                            try {
                                return readSnapshotFromFile(path);
                            } catch (IOException e) {
                                System.err.println("Failed to read snapshot from " + path + ": " + e.getMessage());
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .filter(snapshot -> Boolean.TRUE.equals(snapshot.getIsOffline()))
                        .collect(Collectors.toList());
                        
        } catch (IOException e) {
            System.err.println("Failed to list offline snapshots: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Clean up old snapshots, keeping only the latest N snapshots per machine
     */
    public void cleanupOldSnapshots(int keepLatestCount) {
        try {
            if (!Files.exists(persistenceDir)) {
                return;
            }
            
            // Group files by machine ID
            Map<String, List<Path>> snapshotsByMachine = Files.list(persistenceDir)
                                                             .filter(path -> path.toString().endsWith(".json"))
                                                             .collect(Collectors.groupingBy(path -> {
                                                                 String filename = path.getFileName().toString();
                                                                 int snapshotIndex = filename.indexOf("_snapshot_");
                                                                 return snapshotIndex > 0 ? filename.substring(0, snapshotIndex) : "unknown";
                                                             }));
            
            int totalDeleted = 0;
            for (Map.Entry<String, List<Path>> entry : snapshotsByMachine.entrySet()) {
                List<Path> snapshots = entry.getValue();
                
                if (snapshots.size() > keepLatestCount) {
                    // Sort by file modification time (newest first)
                    snapshots.sort((path1, path2) -> {
                        try {
                            return Files.getLastModifiedTime(path2).compareTo(Files.getLastModifiedTime(path1));
                        } catch (IOException e) {
                            return 0;
                        }
                    });
                    
                    // Delete older files beyond the keepLatestCount
                    List<Path> filesToDelete = snapshots.subList(keepLatestCount, snapshots.size());
                    for (Path file : filesToDelete) {
                        Files.deleteIfExists(file);
                        totalDeleted++;
                    }
                }
            }
            
            if (totalDeleted > 0) {
                System.out.println("Cleaned up " + totalDeleted + " old snapshot files, keeping latest " + keepLatestCount + " per machine");
            }
            
        } catch (IOException e) {
            System.err.println("Failed to cleanup old snapshots: " + e.getMessage());
        }
    }
    
    public void shutdown() {
        executor.shutdown();
    }
    
    /**
     * Get persistence statistics
     */
    public PersistenceStats getStats() {
        try {
            if (!Files.exists(persistenceDir)) {
                return new PersistenceStats(0, 0, 0);
            }
            
            List<Path> snapshotFiles = Files.list(persistenceDir)
                                           .filter(path -> path.toString().endsWith(".json"))
                                           .collect(Collectors.toList());
            
            long totalSize = snapshotFiles.stream()
                                         .mapToLong(path -> {
                                             try {
                                                 return Files.size(path);
                                             } catch (IOException e) {
                                                 return 0;
                                             }
                                         })
                                         .sum();
            
            int uniqueMachines = lastSnapshotIds.size();
            
            return new PersistenceStats(snapshotFiles.size(), uniqueMachines, totalSize);
            
        } catch (IOException e) {
            System.err.println("Failed to calculate persistence stats: " + e.getMessage());
            return new PersistenceStats(0, 0, 0);
        }
    }
    
    /**
     * Persistence statistics holder
     */
    public static class PersistenceStats {
        private final int totalSnapshots;
        private final int uniqueMachines;
        private final long totalSizeBytes;
        
        public PersistenceStats(int totalSnapshots, int uniqueMachines, long totalSizeBytes) {
            this.totalSnapshots = totalSnapshots;
            this.uniqueMachines = uniqueMachines;
            this.totalSizeBytes = totalSizeBytes;
        }
        
        public int getTotalSnapshots() { return totalSnapshots; }
        public int getUniqueMachines() { return uniqueMachines; }
        public long getTotalSizeBytes() { return totalSizeBytes; }
        
        @Override
        public String toString() {
            return String.format("PersistenceStats{snapshots=%d, machines=%d, size=%d bytes}", 
                               totalSnapshots, uniqueMachines, totalSizeBytes);
        }
    }
    
    /**
     * Read snapshot from structured text file
     */
    private StateMachineSnapshotEntity readSnapshotFromFile(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        Map<String, String> properties = new HashMap<>();
        
        for (String line : lines) {
            if (line.contains("=")) {
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    properties.put(parts[0].trim(), parts[1].trim());
                }
            }
        }
        
        StateMachineSnapshotEntity snapshot = new StateMachineSnapshotEntity();
        
        String idStr = properties.get("id");
        if (idStr != null && !idStr.isEmpty()) {
            snapshot.setId(Long.parseLong(idStr));
        }
        
        snapshot.setMachineId(properties.get("machineId"));
        snapshot.setStateId(properties.get("stateId"));
        snapshot.setContext(properties.get("context"));
        
        String isOfflineStr = properties.get("isOffline");
        if (isOfflineStr != null) {
            snapshot.setIsOffline(Boolean.parseBoolean(isOfflineStr));
        }
        
        String timestampStr = properties.get("timestamp");
        if (timestampStr != null && !timestampStr.isEmpty()) {
            snapshot.setTimestamp(LocalDateTime.parse(timestampStr, FORMATTER));
        }
        
        String createdAtStr = properties.get("createdAt");
        if (createdAtStr != null && !createdAtStr.isEmpty()) {
            snapshot.setCreatedAt(LocalDateTime.parse(createdAtStr, FORMATTER));
        }
        
        String updatedAtStr = properties.get("updatedAt");
        if (updatedAtStr != null && !updatedAtStr.isEmpty()) {
            snapshot.setUpdatedAt(LocalDateTime.parse(updatedAtStr, FORMATTER));
        }
        
        return snapshot;
    }
    
    /**
     * Write snapshot to structured text file
     */
    private void writeSnapshotToFile(StateMachineSnapshotEntity snapshot, Path path) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("id=" + (snapshot.getId() != null ? snapshot.getId() : ""));
        lines.add("machineId=" + (snapshot.getMachineId() != null ? snapshot.getMachineId() : ""));
        lines.add("stateId=" + (snapshot.getStateId() != null ? snapshot.getStateId() : ""));
        lines.add("context=" + (snapshot.getContext() != null ? snapshot.getContext() : ""));
        lines.add("isOffline=" + (snapshot.getIsOffline() != null ? snapshot.getIsOffline() : "false"));
        lines.add("timestamp=" + (snapshot.getTimestamp() != null ? snapshot.getTimestamp().format(FORMATTER) : ""));
        lines.add("createdAt=" + (snapshot.getCreatedAt() != null ? snapshot.getCreatedAt().format(FORMATTER) : ""));
        lines.add("updatedAt=" + (snapshot.getUpdatedAt() != null ? snapshot.getUpdatedAt().format(FORMATTER) : ""));
        
        Files.write(path, lines, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}
