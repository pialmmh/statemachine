package com.telcobright.statemachine.persistence;

import java.util.List;
import java.util.Optional;

/**
 * Hybrid persistence implementation that can switch between different storage backends
 */
public class HybridStateMachineSnapshotRepository implements StateMachineSnapshotRepository {
    
    private final StateMachineSnapshotRepository primaryRepository;
    private final StateMachineSnapshotRepository fallbackRepository;
    private boolean useFallback = false;
    
    public HybridStateMachineSnapshotRepository(StateMachineSnapshotRepository primaryRepository,
                                              StateMachineSnapshotRepository fallbackRepository) {
        this.primaryRepository = primaryRepository;
        this.fallbackRepository = fallbackRepository;
    }
    
    /**
     * Switch to fallback repository if primary fails
     */
    private StateMachineSnapshotRepository getActiveRepository() {
        return useFallback ? fallbackRepository : primaryRepository;
    }
    
    @Override
    public void saveAsync(StateMachineSnapshotEntity snapshot) {
        try {
            getActiveRepository().saveAsync(snapshot);
        } catch (Exception e) {
            if (!useFallback) {
                System.err.println("Primary repository failed, switching to fallback: " + e.getMessage());
                useFallback = true;
                fallbackRepository.saveAsync(snapshot);
            } else {
                System.err.println("Both repositories failed to save snapshot: " + e.getMessage());
            }
        }
    }
    
    @Override
    public Optional<StateMachineSnapshotEntity> findLatestByMachineId(String machineId) {
        try {
            Optional<StateMachineSnapshotEntity> result = getActiveRepository().findLatestByMachineId(machineId);
            
            // If using fallback and found nothing, try primary as well
            if (useFallback && result.isEmpty()) {
                result = primaryRepository.findLatestByMachineId(machineId);
            }
            
            return result;
        } catch (Exception e) {
            if (!useFallback) {
                System.err.println("Primary repository failed, trying fallback: " + e.getMessage());
                return fallbackRepository.findLatestByMachineId(machineId);
            } else {
                System.err.println("Repository operation failed: " + e.getMessage());
                return Optional.empty();
            }
        }
    }
    
    @Override
    public List<StateMachineSnapshotEntity> findAllByMachineId(String machineId) {
        try {
            return getActiveRepository().findAllByMachineId(machineId);
        } catch (Exception e) {
            if (!useFallback) {
                System.err.println("Primary repository failed, trying fallback: " + e.getMessage());
                return fallbackRepository.findAllByMachineId(machineId);
            } else {
                System.err.println("Repository operation failed: " + e.getMessage());
                return List.of();
            }
        }
    }
    
    @Override
    public void deleteByMachineId(String machineId) {
        try {
            primaryRepository.deleteByMachineId(machineId);
        } catch (Exception e) {
            System.err.println("Primary repository delete failed: " + e.getMessage());
        }
        
        try {
            fallbackRepository.deleteByMachineId(machineId);
        } catch (Exception e) {
            System.err.println("Fallback repository delete failed: " + e.getMessage());
        }
    }
    
    @Override
    public List<StateMachineSnapshotEntity> findAllOfflineSnapshots() {
        try {
            return getActiveRepository().findAllOfflineSnapshots();
        } catch (Exception e) {
            if (!useFallback) {
                System.err.println("Primary repository failed, trying fallback: " + e.getMessage());
                return fallbackRepository.findAllOfflineSnapshots();
            } else {
                System.err.println("Repository operation failed: " + e.getMessage());
                return List.of();
            }
        }
    }
    
    /**
     * Get status of both repositories
     */
    public String getStatus() {
        return String.format("HybridRepository{primary=%s, fallback=%s, usingFallback=%s}",
                           primaryRepository.getClass().getSimpleName(),
                           fallbackRepository.getClass().getSimpleName(),
                           useFallback);
    }
    
    /**
     * Manually switch to fallback repository
     */
    public void switchToFallback() {
        useFallback = true;
        System.out.println("Manually switched to fallback repository");
    }
    
    /**
     * Attempt to switch back to primary repository
     */
    public boolean tryReconnectToPrimary() {
        try {
            // Test primary repository with a simple operation
            primaryRepository.findAllOfflineSnapshots();
            useFallback = false;
            System.out.println("Successfully reconnected to primary repository");
            return true;
        } catch (Exception e) {
            System.err.println("Failed to reconnect to primary repository: " + e.getMessage());
            return false;
        }
    }
}
