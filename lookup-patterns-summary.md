# Call and SMS Lookup Patterns Implementation

## Summary
Successfully implemented two distinct lookup patterns for statemachine using Split-Verse:

### 1. Call Entity - ID-Only Lookup
- **Entity**: `CallRecord.java`
- **Repository**: `CallRepository.java`
- **Primary Method**: `findById(String callId)`
- **Use Case**: Simple primary key lookups
- **Optimization**: Best for direct ID access without date filtering

### 2. SMS Entity - ID and Date Range Lookup
- **Entity**: `SmsRecord.java`
- **Repository**: `SmsRepository.java`
- **Primary Method**: `findByIdAndPartitionedColRange(String smsId, LocalDateTime start, LocalDateTime end)`
- **Use Case**: Finding records by ID within specific time windows
- **Optimization**: Leverages partition pruning for efficient date range queries

## Implementation Details

### Call Repository - ID-Only Pattern
```java
public class CallRepository {
    // Simple ID lookup
    public CallRecord findById(String callId) throws SQLException {
        return repository.findById(callId);
    }
}
```

### SMS Repository - ID + Date Range Pattern
```java
public class SmsRepository {
    // ID lookup within date range
    public SmsRecord findByIdAndPartitionedColRange(
            String smsId,
            LocalDateTime startTime,
            LocalDateTime endTime) throws SQLException {

        // For PARTITIONED mode: Uses partition pruning
        // For MULTI_TABLE mode: Queries specific daily tables
        return findByIdInPartitionedMode(smsId, startTime, endTime);
    }

    // Overloaded for string dates
    public SmsRecord findByIdAndPartitionedColRange(
            String smsId,
            String startTime,  // "2025-11-10 00:00:30"
            String endTime)    // "2025-11-10 00:01:00"
}
```

## Usage Examples

### Call - Simple ID Lookup
```java
CallRepository callRepo = new CallRepository(shardConfig);

// Direct ID lookup
CallRecord call = callRepo.findById("CALL-12345");
```

### SMS - ID with Date Range
```java
SmsRepository smsRepo = new SmsRepository(shardConfig);

// Find SMS by ID within time window
SmsRecord sms = smsRepo.findByIdAndPartitionedColRange(
    "SMS-100",
    "2025-11-10 00:00:30",
    "2025-11-10 00:01:00"
);
```

## Repository Modes Support

Both repositories support:
- **PARTITIONED Mode**: Single table with MySQL partitions
- **MULTI_TABLE Mode**: Separate daily tables

### Configuration
```java
// PARTITIONED mode (recommended)
new CallRepository(shardConfig, RepositoryMode.PARTITIONED, 30);
new SmsRepository(shardConfig, RepositoryMode.PARTITIONED, 30);

// MULTI_TABLE mode
new CallRepository(shardConfig, RepositoryMode.MULTI_TABLE, 30);
new SmsRepository(shardConfig, RepositoryMode.MULTI_TABLE, 30);
```

## Files Created

1. **Entities**:
   - `/src/main/java/com/telcobright/statemachine/entities/CallRecord.java`
   - `/src/main/java/com/telcobright/statemachine/entities/SmsRecord.java`

2. **Repositories**:
   - `/src/main/java/com/telcobright/statemachine/repository/CallRepository.java`
   - `/src/main/java/com/telcobright/statemachine/repository/SmsRepository.java`

3. **Test**:
   - `/src/main/java/com/telcobright/statemachine/test/CallSmsLookupTest.java`

## Key Features

- ✅ Call entities optimized for simple ID lookups
- ✅ SMS entities support ID + date range queries
- ✅ Both PARTITIONED and MULTI_TABLE modes supported
- ✅ Efficient partition pruning for date-based queries
- ✅ String date format support for convenience
- ✅ Batch operations (insert/query) supported

## Performance Considerations

1. **Call (ID-only)**:
   - Direct primary key access
   - O(1) lookup in hash index
   - No partition scanning needed

2. **SMS (ID + Date Range)**:
   - Partition pruning reduces scan scope
   - Only relevant partitions/tables queried
   - Composite index on (id, created_at) for optimal performance

## Next Steps

To use these repositories:
1. Ensure Split-Verse 1.0.0 is properly installed in local Maven
2. Configure ShardConfig with database credentials
3. Choose repository mode (PARTITIONED recommended)
4. Use the appropriate lookup pattern based on requirements