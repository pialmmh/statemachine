package com.telcobright.statemachine.persistence;

import com.telcobright.statemachine.db.PartitionedRepository;
import com.telcobright.statemachine.idkit.IdGenerator;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;

/**
 * State machine snapshot repository implementation using TelcoBright's PartitionedRepository
 * Supports both ById and ByIdAndDateRange lookup modes for efficient state persistence
 */
public class PartitionedRepoStateMachineSnapshotRepository implements StateMachineSnapshotRepository {
    
    private final PartitionedRepository<StateMachineSnapshotEntity, String> repository;
    private final IdLookUpMode lookupMode;
    
    /**
     * Constructor with lookup mode configuration
     */
    public PartitionedRepoStateMachineSnapshotRepository(
            PartitionedRepository<StateMachineSnapshotEntity, String> repository, 
            IdLookUpMode lookupMode) {
        this.repository = repository;
        this.lookupMode = lookupMode;
    }
    
    @Override
    public void saveAsync(StateMachineSnapshotEntity snapshot) {
        CompletableFuture.runAsync(() -> saveSnapshot(snapshot));
    }
    
    @Override
    public Optional<StateMachineSnapshotEntity> findLatestByMachineId(String machineId) {
        try {
            StateMachineSnapshotEntity snapshot = loadSnapshot(machineId);
            return Optional.ofNullable(snapshot);
        } catch (Exception e) {
            System.err.println("Error finding snapshot for machine ID: " + machineId + " - " + e.getMessage());
            return Optional.empty();
        }
    }
    
    @Override
    public List<StateMachineSnapshotEntity> findAllByMachineId(String machineId) {
        // Note: PartitionedRepository API doesn't have findAll by ID in the provided interface
        // This would need to be enhanced or implemented differently
        List<StateMachineSnapshotEntity> results = new ArrayList<>();
        StateMachineSnapshotEntity snapshot = loadSnapshot(machineId);
        if (snapshot != null) {
            results.add(snapshot);
        }
        return results;
    }
    
    @Override
    public void deleteByMachineId(String machineId) {
        // Note: PartitionedRepository doesn't have explicit delete method in the provided API
        // This would need to be implemented based on the actual repository capabilities
        throw new UnsupportedOperationException("Delete operation not supported by PartitionedRepository");
    }
    
    @Override
    public List<StateMachineSnapshotEntity> findAllOfflineSnapshots() {
        // Note: This would require custom query support from PartitionedRepository
        // For now, return empty list
        return new ArrayList<>();
    }
    
    /**
     * Internal method to save snapshot based on lookup mode
     */
    private void saveSnapshot(StateMachineSnapshotEntity snapshot) {
        try {
            // Check if snapshot already exists
            StateMachineSnapshotEntity existing = loadSnapshot(snapshot.getMachineId());
            
            if (existing != null) {
                // Update existing snapshot
                updateSnapshot(snapshot);
            } else {
                // Insert new snapshot
                repository.insert(snapshot);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to save state machine snapshot for ID: " + 
                    snapshot.getMachineId(), e);
        }
    }
    
    /**
     * Internal method to load snapshot based on lookup mode
     */
    private StateMachineSnapshotEntity loadSnapshot(String machineId) {
        try {
            switch (lookupMode) {
                case ById:
                    return repository.findById(machineId);
                    
                case ByIdAndDateRange:
                    return loadByIdAndDateRange(machineId);
                    
                default:
                    throw new IllegalStateException("Unsupported lookup mode: " + lookupMode);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load state machine snapshot for ID: " + 
                    machineId, e);
        }
    }
    
    /**
     * Load snapshot by ID and date range for efficient partitioned lookup
     */
    private StateMachineSnapshotEntity loadByIdAndDateRange(String machineId) {
        try {
            // Convert string ID to long for timestamp extraction
            long longId = Long.parseLong(machineId);
            
            // Extract timestamp from ID using IdGenerator
            LocalDateTime timestamp = IdGenerator.extractTimestampLocal(longId);
            
            // Create a date range around the timestamp (±1 day for safety)
            LocalDateTime startDate = timestamp.minusDays(1);
            LocalDateTime endDate = timestamp.plusDays(1);
            
            // Use date range lookup for efficient partitioned search
            return repository.findByIdAndDateRange(startDate, endDate);
            
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Machine ID must be a valid long for ByIdAndDateRange mode: " + 
                    machineId, e);
        }
    }
    
    /**
     * Update existing snapshot based on lookup mode
     */
    private void updateSnapshot(StateMachineSnapshotEntity snapshot) {
        String machineId = snapshot.getMachineId();
        
        switch (lookupMode) {
            case ById:
                repository.updateById(machineId, snapshot);
                break;
                
            case ByIdAndDateRange:
                updateByIdAndDateRange(snapshot);
                break;
                
            default:
                throw new IllegalStateException("Unsupported lookup mode: " + lookupMode);
        }
    }
    
    /**
     * Update snapshot by ID and date range
     */
    private void updateByIdAndDateRange(StateMachineSnapshotEntity snapshot) {
        try {
            String machineId = snapshot.getMachineId();
            long longId = Long.parseLong(machineId);
            
            // Extract timestamp from ID
            LocalDateTime timestamp = IdGenerator.extractTimestampLocal(longId);
            
            // Create date range for partitioned update
            LocalDateTime startDate = timestamp.minusDays(1);
            LocalDateTime endDate = timestamp.plusDays(1);
            
            repository.updateByIdAndDateRange(machineId, snapshot, startDate, endDate);
            
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Machine ID must be a valid long for ByIdAndDateRange mode: " + 
                    snapshot.getMachineId(), e);
        }
    }
    
    /**
     * Get the configured lookup mode
     */
    public IdLookUpMode getLookupMode() {
        return lookupMode;
    }
}