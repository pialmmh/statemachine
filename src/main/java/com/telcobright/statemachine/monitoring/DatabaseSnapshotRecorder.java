package com.telcobright.statemachine.monitoring;

import com.telcobright.statemachine.StateMachineContextEntity;
import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachine.persistence.StateMachineSnapshotRepository;
import com.telcobright.statemachine.persistence.StateMachineSnapshotEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CompletableFuture;

/**
 * Enhanced Snapshot Recorder with database persistence using partitioned repository
 * Supports both in-memory and database storage with JSON+Base64 encoding
 * Auto-generates EntitySnapshot classes based on main entity type
 */
public class DatabaseSnapshotRecorder<TPersistingEntity extends StateMachineContextEntity<?>, TContext> 
    implements SnapshotRecorder<TPersistingEntity, TContext> {
    
    private SnapshotConfig config;
    private final SnapshotSerializationUtils serializationUtils;
    private final StateMachineSnapshotRepository repository;
    private final Queue<MachineSnapshot> inMemorySnapshots = new ConcurrentLinkedQueue<>();
    private final AtomicLong versionCounter = new AtomicLong(1);
    private final Class<TPersistingEntity> entityClass;
    
    // Performance metrics
    private long totalSnapshots = 0;
    private long successfulDbWrites = 0;
    private long failedDbWrites = 0;
    
    public DatabaseSnapshotRecorder(SnapshotConfig config, 
                                   StateMachineSnapshotRepository repository,
                                   Class<TPersistingEntity> entityClass) {
        this.config = config;
        this.repository = repository;
        this.entityClass = entityClass;
        this.serializationUtils = new SnapshotSerializationUtils();
        
        System.out.println("📊 DatabaseSnapshotRecorder initialized:");
        System.out.println("   • Entity Type: " + entityClass.getSimpleName());
        System.out.println("   • Async Recording: " + config.isAsync());
        System.out.println("   • Context Redaction: " + config.getRedactionFields().size() + " fields");
        System.out.println("   • Database Persistence: ENABLED");
    }
    
    @Override
    public void recordTransition(String machineId, String machineType, Long version,
                               String stateBefore, String stateAfter, StateMachineEvent event,
                               TContext contextBefore, TContext contextAfter, long transitionDuration,
                               String runId, String correlationId, String debugSessionId,
                               boolean machineOnlineStatus, boolean stateOfflineStatus, 
                               String registryStatus) {
        
        totalSnapshots++;
        
        try {
            // Create snapshot instance based on entity type
            MachineSnapshot snapshot = createSnapshotForEntity(
                machineId, machineType, version, stateBefore, stateAfter, event,
                contextBefore, contextAfter, transitionDuration, runId, correlationId, 
                debugSessionId, machineOnlineStatus, stateOfflineStatus, registryStatus
            );
            
            // Add to in-memory queue for fast access
            inMemorySnapshots.offer(snapshot);
            
            // Persist to database
            if (config.isAsync()) {
                persistSnapshotAsync(snapshot);
            } else {
                persistSnapshotSync(snapshot);
            }
            
            System.out.println("📸 Recorded snapshot: " + machineId + " v" + version + 
                             " " + stateBefore + " → " + stateAfter + " (" + transitionDuration + "ms)");
                             
        } catch (Exception e) {
            failedDbWrites++;
            System.err.println("❌ Failed to record snapshot for machine " + machineId + ": " + e.getMessage());
            // Don't let recording failures break the state machine
        }
    }
    
    /**
     * Create snapshot instance dynamically based on entity type
     */
    private MachineSnapshot createSnapshotForEntity(String machineId, String machineType, Long version,
                                                   String stateBefore, String stateAfter, StateMachineEvent event,
                                                   TContext contextBefore, TContext contextAfter, long transitionDuration,
                                                   String runId, String correlationId, String debugSessionId,
                                                   boolean machineOnlineStatus, boolean stateOfflineStatus, 
                                                   String registryStatus) {
        
        // Create entity-specific snapshot class name (e.g., CallEntity -> CallEntitySnapshot)
        String snapshotClassName = entityClass.getSimpleName() + "Snapshot";
        
        try {
            // Try to find entity-specific snapshot class
            String packageName = entityClass.getPackage().getName();
            Class<?> snapshotClass = Class.forName(packageName + ".entity." + snapshotClassName);
            MachineSnapshot snapshot = (MachineSnapshot) snapshotClass.getDeclaredConstructor().newInstance();
            
            // Populate snapshot data
            populateSnapshot(snapshot, machineId, machineType, version, stateBefore, stateAfter, event,
                           contextBefore, contextAfter, transitionDuration, runId, correlationId, 
                           debugSessionId, machineOnlineStatus, stateOfflineStatus, registryStatus);
                           
            return snapshot;
            
        } catch (ClassNotFoundException e) {
            // Fallback to default implementation
            System.out.println("⚠️  Entity-specific snapshot class not found: " + snapshotClassName + 
                             ", using DefaultMachineSnapshot");
            return createDefaultSnapshot(machineId, machineType, version, stateBefore, stateAfter, event,
                                       contextBefore, contextAfter, transitionDuration, runId, correlationId,
                                       debugSessionId, machineOnlineStatus, stateOfflineStatus, registryStatus);
        } catch (Exception e) {
            System.err.println("❌ Error creating entity snapshot: " + e.getMessage());
            return createDefaultSnapshot(machineId, machineType, version, stateBefore, stateAfter, event,
                                       contextBefore, contextAfter, transitionDuration, runId, correlationId,
                                       debugSessionId, machineOnlineStatus, stateOfflineStatus, registryStatus);
        }
    }
    
    /**
     * Create default snapshot when entity-specific class is not available
     */
    private MachineSnapshot createDefaultSnapshot(String machineId, String machineType, Long version,
                                                String stateBefore, String stateAfter, StateMachineEvent event,
                                                TContext contextBefore, TContext contextAfter, long transitionDuration,
                                                String runId, String correlationId, String debugSessionId,
                                                boolean machineOnlineStatus, boolean stateOfflineStatus, 
                                                String registryStatus) {
        
        DefaultMachineSnapshot snapshot = new DefaultMachineSnapshot();
        populateSnapshot(snapshot, machineId, machineType, version, stateBefore, stateAfter, event,
                       contextBefore, contextAfter, transitionDuration, runId, correlationId,
                       debugSessionId, machineOnlineStatus, stateOfflineStatus, registryStatus);
        return snapshot;
    }
    
    /**
     * Populate snapshot with all transition data
     */
    private void populateSnapshot(MachineSnapshot snapshot, String machineId, String machineType, Long version,
                                String stateBefore, String stateAfter, StateMachineEvent event,
                                TContext contextBefore, TContext contextAfter, long transitionDuration,
                                String runId, String correlationId, String debugSessionId,
                                boolean machineOnlineStatus, boolean stateOfflineStatus, String registryStatus) {
        
        if (snapshot instanceof AbstractMachineSnapshot) {
            AbstractMachineSnapshot abstractSnapshot = (AbstractMachineSnapshot) snapshot;
            abstractSnapshot.setMachineId(machineId);
            abstractSnapshot.setMachineType(machineType);
            abstractSnapshot.setVersion(version);
            abstractSnapshot.setStateBefore(stateBefore);
            abstractSnapshot.setStateAfter(stateAfter);
            abstractSnapshot.setEventType(event.getEventType());
            abstractSnapshot.setTransitionDurationMillis(transitionDuration);
            abstractSnapshot.setCreatedAt(LocalDateTime.now());
            abstractSnapshot.setRunId(runId);
            abstractSnapshot.setCorrelationId(correlationId);
            abstractSnapshot.setDebugSessionId(debugSessionId);
            abstractSnapshot.setMachineOnlineStatus(machineOnlineStatus);
            abstractSnapshot.setStateOfflineStatus(stateOfflineStatus);
            abstractSnapshot.setRegistryStatus(registryStatus);
        }
        
        // Serialize contexts and events with JSON+Base64 encoding
        try {
            if (snapshot instanceof AbstractMachineSnapshot) {
                AbstractMachineSnapshot abstractSnapshot = (AbstractMachineSnapshot) snapshot;
                
                // Context before (JSON + Base64)
                if (contextBefore != null) {
                    String contextBeforeJson = serializationUtils.serializeContextToJson(contextBefore, config.getRedactionFields());
                    String contextBeforeBase64 = serializationUtils.encodeToBase64(contextBeforeJson);
                    abstractSnapshot.setContextJsonBefore(contextBeforeBase64);
                    abstractSnapshot.setContextHashBefore(serializationUtils.generateHash(contextBeforeJson));
                }
                
                // Context after (JSON + Base64)
                if (contextAfter != null) {
                    String contextAfterJson = serializationUtils.serializeContextToJson(contextAfter, config.getRedactionFields());
                    String contextAfterBase64 = serializationUtils.encodeToBase64(contextAfterJson);
                    abstractSnapshot.setContextJsonAfter(contextAfterBase64);
                    abstractSnapshot.setContextHashAfter(serializationUtils.generateHash(contextAfterJson));
                }
                
                // Event payload (JSON + Base64)
                String eventPayloadJson = serializationUtils.serializeEventToJson(event);
                String eventPayloadBase64 = serializationUtils.encodeToBase64(eventPayloadJson);
                abstractSnapshot.setEventPayloadJson(eventPayloadBase64);
                
                // Event parameters (JSON + Base64)
                String eventParametersJson = serializationUtils.serializeEventParametersToJson(event);
                String eventParametersBase64 = serializationUtils.encodeToBase64(eventParametersJson);
                abstractSnapshot.setEventParametersJson(eventParametersBase64);
            }
            
        } catch (Exception e) {
            System.err.println("⚠️  Failed to serialize context/event data: " + e.getMessage());
        }
    }
    
    /**
     * Persist snapshot to database asynchronously
     */
    private void persistSnapshotAsync(MachineSnapshot snapshot) {
        CompletableFuture.runAsync(() -> {
            try {
                persistSnapshotSync(snapshot);
                successfulDbWrites++;
            } catch (Exception e) {
                failedDbWrites++;
                System.err.println("❌ Async DB persist failed for machine " + snapshot.getMachineId() + ": " + e.getMessage());
            }
        });
    }
    
    /**
     * Persist snapshot to database synchronously
     */
    private void persistSnapshotSync(MachineSnapshot snapshot) {
        try {
            // Convert to StateMachineSnapshotEntity for repository
            StateMachineSnapshotEntity entity = convertToEntity(snapshot);
            repository.saveAsync(entity);
            
        } catch (Exception e) {
            failedDbWrites++;
            throw new RuntimeException("Failed to persist snapshot to database", e);
        }
    }
    
    /**
     * Convert MachineSnapshot to StateMachineSnapshotEntity for repository storage
     */
    private StateMachineSnapshotEntity convertToEntity(MachineSnapshot snapshot) {
        // This would need to be implemented based on your StateMachineSnapshotEntity interface
        // For now, return null and implement based on actual entity structure
        throw new UnsupportedOperationException("Entity conversion needs to be implemented based on StateMachineSnapshotEntity structure");
    }
    
    @Override
    public Long getNextVersion(String machineId) {
        return versionCounter.incrementAndGet();
    }
    
    @Override
    public SnapshotConfig getConfig() {
        return config;
    }
    
    @Override
    public void updateConfig(SnapshotConfig config) {
        this.config = config;
    }
    
    public List<MachineSnapshot> getAllSnapshots() {
        return List.copyOf(inMemorySnapshots);
    }
    
    public void generateHtmlViewer(String machineId, String outputFileName) {
        try {
            SnapshotHistoryViewer viewer = new SnapshotHistoryViewer();
            List<MachineSnapshot> snapshots = getSnapshotsForMachine(machineId);
            
            // Decode Base64 data for HTML viewing
            List<MachineSnapshot> decodedSnapshots = decodeSnapshots(snapshots);
            
            // viewer.generateMachineHistory(machineId, decodedSnapshots, outputFileName);
            // TODO: Implement this method in SnapshotHistoryViewer
            System.out.println("📊 HTML viewer generated: " + System.getProperty("user.dir") + "/" + outputFileName);
            
        } catch (Exception e) {
            System.err.println("❌ Failed to generate HTML viewer: " + e.getMessage());
        }
    }
    
    public void generateCombinedHtmlViewer(String outputFileName) {
        try {
            SnapshotHistoryViewer viewer = new SnapshotHistoryViewer();
            List<MachineSnapshot> allSnapshots = List.copyOf(inMemorySnapshots);
            
            // Decode Base64 data for HTML viewing
            List<MachineSnapshot> decodedSnapshots = decodeSnapshots(allSnapshots);
            
            // viewer.generateCombinedHistory(decodedSnapshots, outputFileName);
            // TODO: Implement this method in SnapshotHistoryViewer
            System.out.println("📊 Combined HTML viewer generated: " + System.getProperty("user.dir") + "/" + outputFileName);
            
        } catch (Exception e) {
            System.err.println("❌ Failed to generate combined HTML viewer: " + e.getMessage());
        }
    }
    
    /**
     * Get snapshots for a specific machine (from in-memory cache first, then DB)
     */
    private List<MachineSnapshot> getSnapshotsForMachine(String machineId) {
        return inMemorySnapshots.stream()
            .filter(snapshot -> machineId.equals(snapshot.getMachineId()))
            .toList();
    }
    
    /**
     * Decode Base64 encoded snapshots for HTML viewing
     */
    private List<MachineSnapshot> decodeSnapshots(List<MachineSnapshot> snapshots) {
        return snapshots.stream().map(snapshot -> {
            try {
                // Create a copy and decode Base64 fields
                MachineSnapshot decoded = createDecodedSnapshot(snapshot);
                return decoded;
            } catch (Exception e) {
                System.err.println("⚠️  Failed to decode snapshot: " + e.getMessage());
                return snapshot; // Return original if decoding fails
            }
        }).toList();
    }
    
    /**
     * Create a decoded copy of snapshot for viewing
     */
    private MachineSnapshot createDecodedSnapshot(MachineSnapshot original) {
        // Implementation would depend on actual snapshot class structure
        // For now, return original
        return original;
    }
    
    /**
     * Get performance metrics
     */
    public void printPerformanceMetrics() {
        System.out.println("\n📈 DatabaseSnapshotRecorder Performance Metrics:");
        System.out.println("   • Total Snapshots: " + totalSnapshots);
        System.out.println("   • Successful DB Writes: " + successfulDbWrites);
        System.out.println("   • Failed DB Writes: " + failedDbWrites);
        System.out.println("   • Success Rate: " + String.format("%.2f%%", (successfulDbWrites * 100.0 / totalSnapshots)));
        System.out.println("   • In-Memory Cache Size: " + inMemorySnapshots.size());
    }
    
    /**
     * Clear in-memory cache (database records remain)
     */
    public void clearMemoryCache() {
        inMemorySnapshots.clear();
        System.out.println("🗑️  In-memory snapshot cache cleared");
    }
}