package com.telcobright.statemachine.repository;

import com.telcobright.core.repository.SplitVerseRepository;
import com.telcobright.core.repository.GenericMultiTableRepository;
import com.telcobright.splitverse.config.ShardConfig;
import com.telcobright.splitverse.config.RepositoryMode;
import com.telcobright.core.partition.PartitionType;
import com.telcobright.statemachine.entities.SmsRecord;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;

/**
 * Repository for SMS entities with ID and date range lookup pattern.
 * Optimized for querying by ID within specific time windows.
 */
public class SmsRepository {

    private final SplitVerseRepository<SmsRecord> repository;
    private final ShardConfig shardConfig;
    private final String tableName;
    private final RepositoryMode mode;

    /**
     * Create SmsRepository with PARTITIONED mode for optimal date range queries
     */
    public SmsRepository(ShardConfig shardConfig) {
        this(shardConfig, RepositoryMode.PARTITIONED, 30);
    }

    /**
     * Create SmsRepository with specified mode and retention
     */
    public SmsRepository(ShardConfig shardConfig, RepositoryMode mode, int retentionDays) {
        this.shardConfig = shardConfig;
        this.mode = mode;
        this.tableName = "sms_records";

        if (mode == RepositoryMode.PARTITIONED) {
            this.repository = SplitVerseRepository.<SmsRecord>builder()
                .withSingleShard(shardConfig)
                .withEntityClass(SmsRecord.class)
                .withRepositoryMode(RepositoryMode.PARTITIONED)
                .withPartitionType(PartitionType.DATE_BASED)
                .withPartitionKeyColumn("created_at")
                .withRetentionDays(retentionDays)
                .build();
        } else {
            this.repository = SplitVerseRepository.<SmsRecord>builder()
                .withSingleShard(shardConfig)
                .withEntityClass(SmsRecord.class)
                .withRepositoryMode(RepositoryMode.MULTI_TABLE)
                .withTableGranularity(GenericMultiTableRepository.TableGranularity.DAILY)
                .withRetentionDays(retentionDays)
                .build();
        }
    }

    /**
     * Primary lookup method - Find SMS by ID and date range
     * This is the main use case for SMS entities
     *
     * @param smsId The SMS ID to search for
     * @param startTime Start of the time range
     * @param endTime End of the time range
     * @return The SMS record if found within the date range, null otherwise
     */
    public SmsRecord findByIdAndPartitionedColRange(String smsId, LocalDateTime startTime, LocalDateTime endTime)
            throws SQLException {

        // For MULTI_TABLE mode, we need to query specific daily tables
        if (mode == RepositoryMode.MULTI_TABLE) {
            return findByIdInMultiTableMode(smsId, startTime, endTime);
        }

        // For PARTITIONED mode, use optimized query with partition pruning
        return findByIdInPartitionedMode(smsId, startTime, endTime);
    }

    /**
     * Overloaded method accepting string dates
     */
    public SmsRecord findByIdAndPartitionedColRange(String smsId, String startTime, String endTime)
            throws SQLException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime start = LocalDateTime.parse(startTime, formatter);
        LocalDateTime end = LocalDateTime.parse(endTime, formatter);
        return findByIdAndPartitionedColRange(smsId, start, end);
    }

    /**
     * Find SMS by ID in partitioned table mode with date range
     */
    private SmsRecord findByIdInPartitionedMode(String smsId, LocalDateTime startTime, LocalDateTime endTime)
            throws SQLException {

        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
            shardConfig.getHost(), shardConfig.getPort(), shardConfig.getDatabase());

        try (Connection conn = DriverManager.getConnection(jdbcUrl, shardConfig.getUsername(), shardConfig.getPassword())) {
            String sql = "SELECT * FROM " + tableName +
                        " WHERE sms_id = ? AND created_at >= ? AND created_at <= ?";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, smsId);
                pstmt.setTimestamp(2, Timestamp.valueOf(startTime));
                pstmt.setTimestamp(3, Timestamp.valueOf(endTime));

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return mapResultSetToSmsRecord(rs);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Find SMS by ID in multi-table mode with date range
     */
    private SmsRecord findByIdInMultiTableMode(String smsId, LocalDateTime startTime, LocalDateTime endTime)
            throws SQLException {

        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
            shardConfig.getHost(), shardConfig.getPort(), shardConfig.getDatabase());

        try (Connection conn = DriverManager.getConnection(jdbcUrl, shardConfig.getUsername(), shardConfig.getPassword())) {
            // Generate table names for the date range
            LocalDateTime current = startTime.toLocalDate().atStartOfDay();
            LocalDateTime endDate = endTime.toLocalDate().atStartOfDay();

            while (!current.isAfter(endDate)) {
                String tableName = String.format("sms_records_%d%02d%02d",
                    current.getYear(), current.getMonthValue(), current.getDayOfMonth());

                // Check if table exists and query it
                String sql = "SELECT * FROM " + tableName +
                            " WHERE sms_id = ? AND created_at >= ? AND created_at <= ?";

                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, smsId);
                    pstmt.setTimestamp(2, Timestamp.valueOf(startTime));
                    pstmt.setTimestamp(3, Timestamp.valueOf(endTime));

                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            return mapResultSetToSmsRecord(rs);
                        }
                    }
                } catch (SQLException e) {
                    // Table might not exist, continue to next day
                    if (!e.getMessage().contains("doesn't exist")) {
                        throw e;
                    }
                }

                current = current.plusDays(1);
            }
        }
        return null;
    }

    /**
     * Find multiple SMS records by ID list within date range
     */
    public List<SmsRecord> findByIdsAndPartitionedColRange(List<String> smsIds,
            LocalDateTime startTime, LocalDateTime endTime) throws SQLException {

        List<SmsRecord> results = new ArrayList<>();
        for (String smsId : smsIds) {
            SmsRecord record = findByIdAndPartitionedColRange(smsId, startTime, endTime);
            if (record != null) {
                results.add(record);
            }
        }
        return results;
    }

    /**
     * Map ResultSet to SmsRecord
     */
    private SmsRecord mapResultSetToSmsRecord(ResultSet rs) throws SQLException {
        SmsRecord sms = new SmsRecord();
        sms.setId(rs.getString("sms_id"));
        sms.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        sms.setSenderNumber(rs.getString("sender_number"));
        sms.setReceiverNumber(rs.getString("receiver_number"));
        sms.setMessageContent(rs.getString("message_content"));
        sms.setMessageStatus(rs.getString("message_status"));
        sms.setDeliveryStatus(rs.getString("delivery_status"));
        sms.setMessageType(rs.getString("message_type"));
        sms.setRetryCount(rs.getInt("retry_count"));

        Timestamp deliveredAt = rs.getTimestamp("delivered_at");
        if (deliveredAt != null) {
            sms.setDeliveredAt(deliveredAt.toLocalDateTime());
        }

        return sms;
    }

    /**
     * Standard repository methods
     */
    public SmsRecord findById(String smsId) throws SQLException {
        return repository.findById(smsId);
    }

    public void insert(SmsRecord sms) throws SQLException {
        repository.insert(sms);
    }

    public void insertBatch(List<SmsRecord> smsList) throws SQLException {
        repository.insertMultiple(smsList);
    }

    public List<SmsRecord> findByDateRange(LocalDateTime start, LocalDateTime end) throws SQLException {
        return repository.findAllByDateRange(start, end);
    }

    public boolean exists(String smsId) throws SQLException {
        return repository.findById(smsId) != null;
    }

    public void delete(String smsId) throws SQLException {
        // TODO: Implement delete operation if required
        // repository.deleteById(smsId);
    }

    public void shutdown() {
        repository.shutdown();
    }
}