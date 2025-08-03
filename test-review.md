# Test Code Review - Potential Issues

## 🔍 Issues Found in Test Code

### **1. Missing Dependencies in Test Classes**

```java
// ISSUE: StateMachineRegistry class doesn't exist yet

// ISSUE: SMS machine classes may not exist
import com.telcobright.statemachineexamples.smsmachine.SmsMachine;  // ❌ May not exist
```

### **2. Registry Methods Not Implemented**
```java
// In CallMachineRehydrationTest.java
registry.removeMachine(machineId);           // ❌ Method doesn't exist
registry.isInMemory(machineId);             // ❌ Method doesn't exist
registry.createOrGet(machineId, factory);   // ❌ Method doesn't exist
```

### **3. Machine Method Assumptions**
```java
// Assumed methods that may not exist
machine.getCurrentState();                   // ❌ May not exist
machine.sendEvent(event);                   // ❌ May not exist
machine.restoreFromSnapshot(snapshot);      // ❌ May not exist
machine.createSnapshot();                   // ❌ May not exist
```

### **4. Test Database Dependencies**
```java
// MySQL driver dependency needed in pom.xml
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <version>8.0.33</version>
    <scope>test</scope>
</dependency>
```

## 🔧 Required Fixes

### **1. Update pom.xml for Test Dependencies**
```xml
<dependencies>
    <!-- Test dependencies -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.10.0</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>mysql</groupId>
        <artifactId>mysql-connector-java</artifactId>
        <version>8.0.33</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### **2. Create Missing Registry Class**
```java
// Need to implement StateMachineRegistry with required methods
public class StateMachineRegistry {
    private final Map<String, GenericStateMachine> machines = new ConcurrentHashMap<>();
    
    public void register(String id, GenericStateMachine machine) { /* implement */ }
    public void removeMachine(String id) { /* implement */ }
    public boolean isInMemory(String id) { /* implement */ }
    public GenericStateMachine createOrGet(String id, Supplier<GenericStateMachine> factory) { /* implement */ }
}
```

### **3. Add Missing Methods to GenericStateMachine**
```java
// Need to add these methods to GenericStateMachine
public String getCurrentState() { /* implement */ }
public void sendEvent(StateMachineEvent event) { /* implement */ }
public void restoreFromSnapshot(StateMachineSnapshotEntity snapshot) { /* implement */ }
public StateMachineSnapshotEntity createSnapshot() { /* implement */ }
```

## 📋 Test Execution Order

### **Phase 1: Basic Infrastructure Tests**
1. `TestDatabaseHelper` - Database connection and setup
2. `CallMachineOfflineStateTest` - Basic persistence

### **Phase 2: State Machine Tests**
3. `CallMachineStateTransitionPersistenceTest` - All transitions
4. `SmsStateTransitionPersistenceTest` - SMS flow (if SMS machine exists)

### **Phase 3: Advanced Features**
5. `CallMachineRehydrationTest` - Registry and rehydration (requires registry implementation)

## 🚨 Expected Test Failures

### **Compilation Errors**
- `StateMachineRegistry` class not found
- SMS machine classes may not exist
- Missing methods in `GenericStateMachine`

### **Runtime Errors**
- Database connection issues
- Missing MySQL driver
- Table creation failures

## 📊 Success Criteria

### **Green Tests (Should Pass)**
- Database connection and setup
- Basic state transitions
- MySQL persistence operations

### **Red Tests (Expected Failures)**
- Registry-related tests (until registry is implemented)
- SMS machine tests (if SMS machine doesn't exist)
- Rehydration tests (complex logic not implemented)

## 🛠️ Immediate Actions Needed

1. **Add MySQL dependency** to pom.xml
2. **Create StateMachineRegistry** class
3. **Implement missing methods** in GenericStateMachine
4. **Verify SMS machine** exists and is compiled
5. **Run tests individually** to isolate failures