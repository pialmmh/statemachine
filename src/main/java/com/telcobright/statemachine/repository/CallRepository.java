package com.telcobright.statemachine.repository;

import com.telcobright.core.repository.SplitVerseRepository;
import com.telcobright.core.repository.GenericMultiTableRepository;
import com.telcobright.splitverse.config.ShardConfig;
import com.telcobright.splitverse.config.RepositoryMode;
import com.telcobright.core.partition.PartitionType;
import com.telcobright.statemachine.entities.CallRecord;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Call entities with ID-only lookup pattern.
 * Optimized for simple primary key lookups without date range queries.
 */
public class CallRepository {

    private final SplitVerseRepository<CallRecord> repository;

    /**
     * Create CallRepository with PARTITIONED mode for optimal ID-based lookups
     */
    public CallRepository(ShardConfig shardConfig) {
        this(shardConfig, RepositoryMode.PARTITIONED, 30);
    }

    /**
     * Create CallRepository with specified mode and retention
     */
    public CallRepository(ShardConfig shardConfig, RepositoryMode mode, int retentionDays) {
        if (mode == RepositoryMode.PARTITIONED) {
            this.repository = SplitVerseRepository.<CallRecord>builder()
                .withSingleShard(shardConfig)
                .withEntityClass(CallRecord.class)
                .withRepositoryMode(RepositoryMode.PARTITIONED)
                .withPartitionType(PartitionType.DATE_BASED)
                .withPartitionKeyColumn("created_at")
                .withRetentionDays(retentionDays)
                .build();
        } else {
            this.repository = SplitVerseRepository.<CallRecord>builder()
                .withSingleShard(shardConfig)
                .withEntityClass(CallRecord.class)
                .withRepositoryMode(RepositoryMode.MULTI_TABLE)
                .withTableGranularity(GenericMultiTableRepository.TableGranularity.DAILY)
                .withRetentionDays(retentionDays)
                .build();
        }
    }

    /**
     * Primary lookup method - Find call by ID only
     * This is the main use case for Call entities
     */
    public CallRecord findById(String callId) throws SQLException {
        return repository.findById(callId);
    }

    /**
     * Insert a new call record
     */
    public void insert(CallRecord call) throws SQLException {
        repository.insert(call);
    }

    /**
     * Batch insert multiple call records
     */
    public void insertBatch(List<CallRecord> calls) throws SQLException {
        repository.insertMultiple(calls);
    }

    /**
     * Update call status and duration
     */
    public void updateCall(String callId, String status, Integer duration) throws SQLException {
        CallRecord call = findById(callId);
        if (call != null) {
            CallRecord updateData = new CallRecord();
            updateData.setCallStatus(status);
            updateData.setDurationSeconds(duration);
            repository.updateById(callId, updateData);
        }
    }

    /**
     * Check if a call exists
     */
    public boolean exists(String callId) throws SQLException {
        return repository.findById(callId) != null;
    }

    /**
     * Delete a call record
     * Note: Implement custom delete if needed
     */
    public void delete(String callId) throws SQLException {
        // TODO: Implement delete operation if required
        // repository.deleteById(callId);
    }

    /**
     * Get calls within a date range (secondary use case)
     */
    public List<CallRecord> findByDateRange(LocalDateTime start, LocalDateTime end) throws SQLException {
        return repository.findAllByDateRange(start, end);
    }

    /**
     * Shutdown the repository
     */
    public void shutdown() {
        repository.shutdown();
    }
}