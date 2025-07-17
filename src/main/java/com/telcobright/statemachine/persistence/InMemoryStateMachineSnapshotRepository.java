package com.telcobright.statemachine.persistence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * In-memory implementation of the StateMachineSnapshotRepository
 * This is a simple implementation for demonstration purposes.
 * In production, this would be replaced with a real JPA repository.
 */
public class InMemoryStateMachineSnapshotRepository implements StateMachineSnapshotRepository {
    
    private final Map<String, List<StateMachineSnapshotEntity>> storage = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private Long nextId = 1L;
    
    @Override
    public void saveAsync(StateMachineSnapshotEntity snapshot) {
        CompletableFuture.runAsync(() -> {
            try {
                // Simulate some persistence delay
                Thread.sleep(10);
                
                synchronized (this) {
                    if (snapshot.getId() == null) {
                        snapshot.setId(nextId++);
                    }
                }
                
                storage.computeIfAbsent(snapshot.getMachineId(), k -> new ArrayList<>())
                       .add(snapshot);
                
                System.out.println("Saved snapshot for machine " + snapshot.getMachineId() + 
                                 " in state " + snapshot.getStateId());
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Interrupted while saving snapshot: " + e.getMessage());
            }
        }, executor);
    }
    
    @Override
    public Optional<StateMachineSnapshotEntity> findLatestByMachineId(String machineId) {
        List<StateMachineSnapshotEntity> snapshots = storage.get(machineId);
        if (snapshots == null || snapshots.isEmpty()) {
            return Optional.empty();
        }
        
        return snapshots.stream()
                .max(Comparator.comparing(StateMachineSnapshotEntity::getTimestamp));
    }
    
    @Override
    public List<StateMachineSnapshotEntity> findAllByMachineId(String machineId) {
        return storage.getOrDefault(machineId, new ArrayList<>());
    }
    
    @Override
    public void deleteByMachineId(String machineId) {
        storage.remove(machineId);
        System.out.println("Deleted all snapshots for machine " + machineId);
    }
    
    @Override
    public List<StateMachineSnapshotEntity> findAllOfflineSnapshots() {
        return storage.values().stream()
                .flatMap(List::stream)
                .filter(snapshot -> snapshot.getIsOffline())
                .collect(Collectors.toList());
    }
    
    public void shutdown() {
        executor.shutdown();
    }
}
