package com.telcobright.statemachine.persistence;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing state machine snapshots
 */
public interface StateMachineSnapshotRepository {
    
    /**
     * Save a snapshot asynchronously
     */
    void saveAsync(StateMachineSnapshotEntity snapshot);
    
    /**
     * Find the latest snapshot for a machine ID
     */
    Optional<StateMachineSnapshotEntity> findLatestByMachineId(String machineId);
    
    /**
     * Find all snapshots for a machine ID
     */
    List<StateMachineSnapshotEntity> findAllByMachineId(String machineId);
    
    /**
     * Delete all snapshots for a machine ID
     */
    void deleteByMachineId(String machineId);
    
    /**
     * Find all offline snapshots
     */
    List<StateMachineSnapshotEntity> findAllOfflineSnapshots();
}
