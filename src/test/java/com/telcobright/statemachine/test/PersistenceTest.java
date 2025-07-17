package com.telcobright.statemachine.test;

import java.util.Optional;

import com.telcobright.statemachine.persistence.FileBasedStateMachineSnapshotRepository;
import com.telcobright.statemachine.persistence.StateMachineSnapshotEntity;

/**
 * Simple test to verify file-based persistence functionality
 */
public class PersistenceTest {
    
    public static void main(String[] args) {
        System.out.println("=== Testing File-Based Persistence ===");
        
        // Create file-based repository
        FileBasedStateMachineSnapshotRepository repository = 
            new FileBasedStateMachineSnapshotRepository("./test_persistence");
        
        // Create a test snapshot
        StateMachineSnapshotEntity snapshot = new StateMachineSnapshotEntity(
            "test-machine-001",
            "RINGING", 
            "test context data",
            false
        );
        
        System.out.println("📝 Saving snapshot...");
        repository.saveAsync(snapshot);
        
        // Wait a bit for async save
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Try to retrieve the snapshot
        System.out.println("🔍 Retrieving snapshot...");
        Optional<StateMachineSnapshotEntity> retrieved = repository.findLatestByMachineId("test-machine-001");
        
        if (retrieved.isPresent()) {
            StateMachineSnapshotEntity entity = retrieved.get();
            System.out.println("✅ Snapshot retrieved successfully!");
            System.out.println("   Machine ID: " + entity.getMachineId());
            System.out.println("   State ID: " + entity.getStateId());
            System.out.println("   Context: " + entity.getContext());
            System.out.println("   Is Offline: " + entity.getIsOffline());
            System.out.println("   Timestamp: " + entity.getTimestamp());
        } else {
            System.out.println("❌ Failed to retrieve snapshot");
        }
        
        // Test cleanup
        System.out.println("🧹 Cleaning up...");
        repository.deleteByMachineId("test-machine-001");
        
        // Verify cleanup
        Optional<StateMachineSnapshotEntity> afterCleanup = repository.findLatestByMachineId("test-machine-001");
        if (afterCleanup.isEmpty()) {
            System.out.println("✅ Cleanup successful - no snapshots found");
        } else {
            System.out.println("❌ Cleanup failed - snapshot still exists");
        }
        
        repository.shutdown();
        System.out.println("✅ Persistence test completed");
    }
}
