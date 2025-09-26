# CallMachineMultiEntity Test Documentation

## Overview

Comprehensive test suite for call state machine with multi-entity persistence, demonstrating:
- Complex nested entity graphs with mixed ShardingEntity types
- Full call lifecycle (dial → ring → connect → hold → resume → hangup)
- Offline state persistence and rehydration
- State timeout handling
- ID consistency across all entities

## Entity Architecture

### Call Context Hierarchy

```
CallMachineContext (root)
├── CallEntity (call details)
│   ├── List<CallEventEntity> (call events)
│   └── DeviceInfoEntity (singleton device)
├── CdrEntity (billing records)
│   └── List<ChargeEventEntity> (charges)
├── MediaEntity (media handling)
│   ├── List<MediaStreamEntity> (streams)
│   └── CodecInfoEntity (codecs)
└── NetworkEntity (network info)
    ├── SignalingEntity (SIP/WebRTC)
    └── QosMetricsEntity (quality metrics)
```

### Entity Types

#### ShardingEntity Types (Persisted)
1. **CallMachineContext** - Root context with machine ID
2. **CallEntity** - Call details and metadata
3. **CdrEntity** - Call Detail Records for billing
4. **MediaEntity** - Media session information
5. **NetworkEntity** - Network and signaling data
6. **CallEventEntity** - Individual call events
7. **ChargeEventEntity** - Billing events
8. **MediaStreamEntity** - Media stream records

#### Non-ShardingEntity Types (Not Persisted)
1. **DeviceInfoEntity** - Singleton device information
2. **SessionCache** - Temporary session data
3. **RtpStats** - Real-time transport statistics
4. **BufferData** - Media buffering information

## Test Scenarios

### 1. Full Call Cycle Test
Tests complete call flow with state transitions:
- **IDLE** → dial() → **DIALING**
- **DIALING** → ring() → **RINGING**
- **RINGING** → answer() → **CONNECTED**
- **CONNECTED** → hold() → **ON_HOLD**
- **ON_HOLD** → resume() → **CONNECTED**
- **CONNECTED** → hangup() → **IDLE**

### 2. Offline and Rehydration Test
- Create call machine in CONNECTED state
- Persist all ShardingEntity data to database
- Simulate machine shutdown (offline)
- Rehydrate machine from database
- Verify:
  - All ShardingEntity data restored
  - Transient data recreated
  - State correctly restored
  - Call can continue normally

### 3. State Timeout Test
- Configure timeout for RINGING state (30 seconds)
- Enter RINGING state
- Wait for timeout
- Verify automatic transition to IDLE
- Check timeout event logged

### 4. Multi-Entity Consistency Test
- Verify all entities share same machine ID
- Test ID propagation on updates
- Validate entity relationships
- Check singleton behavior for DeviceInfo

## Call States

```java
public enum CallStates {
    IDLE,           // No active call
    DIALING,        // Initiating call
    RINGING,        // Call ringing
    CONNECTED,      // Call active
    ON_HOLD,        // Call on hold
    TRANSFERRING,   // Call being transferred
    DISCONNECTING,  // Ending call
    FAILED          // Call failed
}
```

## Call Events

```java
public enum CallEvents {
    DIAL,           // Start dialing
    RING,           // Phone ringing
    ANSWER,         // Call answered
    HOLD,           // Put on hold
    RESUME,         // Resume from hold
    TRANSFER,       // Transfer call
    HANGUP,         // End call
    TIMEOUT,        // State timeout
    ERROR           // Error occurred
}
```

## Database Schema

### Partitioned Tables
All tables use MySQL RANGE partitioning by created_at:

```sql
-- Call contexts table
CREATE TABLE call_machine_contexts (
    id VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    state VARCHAR(50),
    caller_number VARCHAR(50),
    called_number VARCHAR(50),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (TO_DAYS(created_at));

-- Call entities table
CREATE TABLE call_entities (
    id VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    call_type VARCHAR(20),
    duration INT,
    quality_score DECIMAL(3,2),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (TO_DAYS(created_at));

-- CDR entities table
CREATE TABLE cdr_entities (
    id VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    total_charge DECIMAL(10,2),
    billing_type VARCHAR(20),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (TO_DAYS(created_at));
```

## Test Implementation Details

### Setup
1. Initialize MySQL database
2. Create partitioned tables
3. Configure StateMachineRegistry with PARTITIONED_REPO
4. Set retention to 30 days

### Test Execution
1. Create CallMachineContext with unique machine ID
2. Initialize all nested entities
3. Execute state transitions
4. Verify entity persistence
5. Test rehydration
6. Test timeout handling

### Validation Points
- State transitions correct
- Events logged properly
- All ShardingEntity types persisted
- Transient data not persisted
- ID consistency maintained
- Rehydration successful
- Timeout triggers correctly

## Performance Metrics

### Expected Performance
- State transition: < 10ms
- Persistence operation: < 50ms
- Rehydration: < 100ms
- Timeout precision: ±100ms

### Resource Usage
- Memory per machine: ~10KB
- Database storage: ~5KB per call
- Network overhead: minimal

## Error Handling

### Common Errors
1. **Database Connection Failed**
   - Retry with exponential backoff
   - Fall back to in-memory mode

2. **Rehydration Failed**
   - Log error with machine ID
   - Create fresh instance
   - Mark for investigation

3. **Timeout Not Triggered**
   - Check timer thread pool
   - Verify timeout configuration
   - Review system clock

## Success Criteria

✅ All state transitions execute correctly
✅ Multi-entity graph maintains ID consistency
✅ Only ShardingEntity types are persisted
✅ Rehydration restores machine to correct state
✅ Timeout triggers state transition
✅ Performance within expected limits
✅ No memory leaks detected
✅ Database partitions created correctly

## Running the Test

```bash
# Compile
mvn compile

# Run test
java -cp "target/classes:lib/*" \
  com.telcobright.statemachine.test.CallMachineMultiEntityTest

# With debug output
java -Dstatemachine.debug=true \
  -cp "target/classes:lib/*" \
  com.telcobright.statemachine.test.CallMachineMultiEntityTest
```

## Expected Output

```
================================================================================
   CALL MACHINE MULTI-ENTITY TEST
================================================================================

TEST 1: Full Call Cycle
- IDLE → DIALING → RINGING → CONNECTED → ON_HOLD → CONNECTED → IDLE
✅ All transitions successful

TEST 2: Multi-Entity Persistence
- Created 8 ShardingEntity types
- Created 4 non-ShardingEntity types
✅ Selective persistence verified

TEST 3: Offline and Rehydration
- Machine persisted in CONNECTED state
- Machine rehydrated successfully
✅ State restored: CONNECTED
✅ All entities recovered

TEST 4: State Timeout
- RINGING state timeout set to 5 seconds
- Timeout triggered after 5 seconds
✅ Automatic transition to IDLE

================================================================================
   ✅ ALL TESTS PASSED
================================================================================
```