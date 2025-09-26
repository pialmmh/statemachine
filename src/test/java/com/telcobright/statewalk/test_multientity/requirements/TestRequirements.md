# State-Walk Multi-Entity Test Requirements

## Core Principles
1. **All entities MUST implement ShardingEntity interface**
2. **All entities in a machine's context MUST share the same ID as the machine**
3. **Database MUST persist across registry restarts (never dropped)**
4. **Singleton entities share same instance but still use machine ID**

## Test Requirements

### 1. ID Consistency Test (IdConsistencyTest.java)
**Purpose**: Verify all entities in a machine's context share the same machine ID

**Requirements**:
- Create a CallMachine with ID "CALL-001"
- Verify Call entity has ID "CALL-001"
- Verify Cdr entity has ID "CALL-001"
- Verify BillInfo entity has ID "CALL-001"
- Verify DeviceInfo (singleton) has ID "CALL-001"
- Verify child entities (CallEvent, Party) maintain parent ID reference

**Expected Result**: All entities share machine ID, relationships intact

---

### 2. Database Persistence Test (DatabasePersistenceTest.java)
**Purpose**: Verify database survives registry restarts

**Requirements**:
- Create registry "test_persistence_db"
- Insert entities with machine ID "PERSIST-001"
- Shutdown registry
- Create new registry instance with same name
- Verify database still exists
- Verify entities can be retrieved
- Verify no data loss

**Expected Result**: Database and data persist across restarts

---

### 3. Singleton Management Test (SingletonManagementTest.java)
**Purpose**: Verify singleton entities work correctly with machine IDs

**Requirements**:
- Create two machines: "CALL-001" and "CALL-002"
- Each has DeviceInfo marked as singleton
- Verify DeviceInfo instance is shared within each machine's graph
- Verify DeviceInfo has correct machine ID for each machine
- Verify updates to singleton affect all references within same machine
- Verify singleton isolation between different machines

**Expected Result**: Singletons shared within machine, isolated between machines

---

### 4. Graph Integrity Test (GraphIntegrityTest.java)
**Purpose**: Verify entity relationships maintained after persistence/rehydration

**Requirements**:
- Create complex graph with machine ID "GRAPH-001"
- Add multiple CallEvents to Call entity
- Add Party to BillInfo
- Persist entire graph
- Shutdown and restart registry
- Rehydrate machine
- Verify all relationships intact
- Verify all IDs consistent

**Expected Result**: Complete graph restored with all relationships

---

### 5. Concurrent Machines Test (ConcurrentMachinesTest.java)
**Purpose**: Verify multiple machines can operate independently

**Requirements**:
- Create 10 machines concurrently (CALL-001 to CALL-010)
- Each machine has complete entity graph
- Verify no ID conflicts
- Verify each machine's entities isolated
- Persist all machines
- Randomly update 5 machines
- Verify updates don't affect other machines

**Expected Result**: Machines operate independently without interference

---

### 6. Partition Query Test (PartitionQueryTest.java)
**Purpose**: Verify partition pruning works for time-based queries

**Requirements**:
- Create machines across 7 days (CALL-DAY1 to CALL-DAY7)
- Query for specific date range (days 3-5)
- Verify only relevant partitions accessed
- Measure query performance
- Verify correct results returned

**Expected Result**: Efficient partition-based queries

---

### 7. Error Recovery Test (ErrorRecoveryTest.java)
**Purpose**: Verify system handles partial failures gracefully

**Requirements**:
- Start persisting machine "ERROR-001"
- Simulate failure during BillInfo persistence
- Verify Call and Cdr already persisted
- Attempt recovery
- Verify graph can be completed
- Verify no data corruption

**Expected Result**: Partial failures handled, recovery possible

---

### 8. Performance Load Test (PerformanceLoadTest.java)
**Purpose**: Verify system performs under load

**Requirements**:
- Create 1000 machines rapidly
- Each with complete entity graph
- Measure creation time
- Measure persistence time
- Measure rehydration time
- Verify all machines correct
- Check memory usage

**Expected Result**:
- Creation: < 10ms per machine
- Persistence: < 20ms per machine
- Rehydration: < 15ms per machine

---

### 9. Playback Consistency Test (PlaybackConsistencyTest.java)
**Purpose**: Verify playback maintains ID consistency

**Requirements**:
- Create machine "PLAYBACK-001"
- Record 10 transitions
- Play backward 5 steps
- Verify all entities still have ID "PLAYBACK-001"
- Play forward 3 steps
- Verify ID consistency maintained
- Jump to specific transition
- Verify complete graph integrity

**Expected Result**: ID consistency maintained during playback

---

### 10. Schema Evolution Test (SchemaEvolutionTest.java)
**Purpose**: Verify system handles entity schema changes

**Requirements**:
- Create machine "SCHEMA-001" with v1 entities
- Persist machine
- Add new field to Call entity
- Create machine "SCHEMA-002" with v2 entities
- Verify both machines work
- Verify v1 machine can be loaded with defaults for new fields

**Expected Result**: Backward compatibility maintained

---

## Entity Requirements

### Base Requirements for ALL Entities
```java
implements ShardingEntity<String> {
    private String id;         // MUST match machine ID
    private LocalDateTime createdAt;

    @Override
    public String getId() { return id; }

    @Override
    public void setId(String id) { this.id = id; }

    @Override
    public LocalDateTime getCreatedAt() { return createdAt; }

    @Override
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
```

### Entity Hierarchy
```
Machine (ID: MACHINE-001)
├── CallContext (extends StateMachineContextEntity)
│   ├── Call (id: MACHINE-001)
│   │   └── List<CallEvent> (parentId: MACHINE-001)
│   ├── Cdr (id: MACHINE-001)
│   ├── BillInfo (id: MACHINE-001)
│   │   └── Party (billId: references MACHINE-001)
│   └── DeviceInfo (id: MACHINE-001) [singleton per machine]
```

## Success Criteria

1. **100% ID Consistency**: Every entity in a machine's graph has the machine's ID
2. **Database Persistence**: Databases never dropped, always persist
3. **Graph Integrity**: All relationships maintained through persistence cycles
4. **Performance Targets**: Meet all performance benchmarks
5. **Error Resilience**: System recovers from partial failures
6. **Concurrent Operation**: Multiple machines operate independently
7. **Playback Accuracy**: State transitions preserve entity consistency

## Test Execution Order

1. IdConsistencyTest - Foundation
2. DatabasePersistenceTest - Persistence layer
3. SingletonManagementTest - Singleton behavior
4. GraphIntegrityTest - Relationship management
5. ConcurrentMachinesTest - Isolation
6. PartitionQueryTest - Query optimization
7. ErrorRecoveryTest - Resilience
8. PerformanceLoadTest - Scale
9. PlaybackConsistencyTest - Feature integration
10. SchemaEvolutionTest - Long-term compatibility