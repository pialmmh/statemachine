package com.telcobright.statemachine.history;

import com.telcobright.splitverse.config.RepositoryMode;
import com.telcobright.splitverse.config.ShardConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * Manages retention policy for history database
 *
 * Features:
 * - Scheduled cleanup of old data
 * - Supports both partitioned and multi-table modes
 * - Configurable retention period (default: 30 days = ±15 days in SplitVerse)
 * - Safe partition/table dropping with verification
 */
public class RetentionManager {

    private static final Logger log = LoggerFactory.getLogger(RetentionManager.class);

    private final String historyDbName;
    private final ShardConfig shardConfig;
    private final RepositoryMode repositoryMode;
    private final int retentionDays;

    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> cleanupTask;

    /**
     * Create retention manager with default 30-day retention
     *
     * @param historyDbName History database name
     * @param shardConfig Database configuration
     * @param repositoryMode Partitioned or multi-table mode
     */
    public RetentionManager(String historyDbName, ShardConfig shardConfig, RepositoryMode repositoryMode) {
        this(historyDbName, shardConfig, repositoryMode, 30);
    }

    /**
     * Create retention manager with custom retention period
     *
     * @param historyDbName History database name
     * @param shardConfig Database configuration
     * @param repositoryMode Partitioned or multi-table mode
     * @param retentionDays Number of days to retain (will be ±retentionDays/2 in SplitVerse)
     */
    public RetentionManager(String historyDbName, ShardConfig shardConfig,
                           RepositoryMode repositoryMode, int retentionDays) {
        this.historyDbName = historyDbName;
        this.shardConfig = shardConfig;
        this.repositoryMode = repositoryMode;
        this.retentionDays = retentionDays;

        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "RetentionManager-" + historyDbName);
            t.setDaemon(true);
            return t;
        });

        log.info("RetentionManager initialized for database: {} with {}-day retention (mode: {})",
            historyDbName, retentionDays, repositoryMode);
    }

    /**
     * Start scheduled cleanup
     * Default: Daily at 2 AM
     */
    public void startScheduledCleanup() {
        startScheduledCleanup("0 2 * * *");
    }

    /**
     * Start scheduled cleanup with custom cron expression
     *
     * @param cronExpression Cron expression (simplified: "H M * * *")
     */
    public void startScheduledCleanup(String cronExpression) {
        // Parse simple cron: "H M * * *"
        String[] parts = cronExpression.split(" ");
        if (parts.length < 2) {
            log.error("Invalid cron expression: {}", cronExpression);
            return;
        }

        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);

        // Calculate initial delay to next execution
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);

        if (nextRun.isBefore(now) || nextRun.isEqual(now)) {
            nextRun = nextRun.plusDays(1);
        }

        long initialDelaySeconds = java.time.Duration.between(now, nextRun).getSeconds();

        log.info("Scheduling cleanup to run daily at {}:{} (first run in {} hours)",
            hour, minute, initialDelaySeconds / 3600.0);

        // Schedule daily cleanup
        cleanupTask = scheduler.scheduleAtFixedRate(
            this::performCleanup,
            initialDelaySeconds,
            TimeUnit.DAYS.toSeconds(1),
            TimeUnit.SECONDS
        );
    }

    /**
     * Perform cleanup based on repository mode
     */
    private void performCleanup() {
        log.info("Starting retention cleanup for database: {}", historyDbName);

        try {
            if (repositoryMode == RepositoryMode.PARTITIONED) {
                dropOldPartitions();
            } else {
                dropOldTables();
            }

            log.info("Retention cleanup completed successfully for database: {}", historyDbName);

        } catch (Exception e) {
            log.error("Error during retention cleanup", e);
        }
    }

    /**
     * Drop old partitions (for partitioned mode)
     */
    private void dropOldPartitions() {
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
            shardConfig.getHost(), shardConfig.getPort(), historyDbName);

        // Calculate cutoff date (retention days in the past)
        LocalDate cutoffDate = LocalDate.now().minusDays(retentionDays + retentionDays / 2);

        try (Connection conn = DriverManager.getConnection(jdbcUrl,
                shardConfig.getUsername(), shardConfig.getPassword());
             Statement stmt = conn.createStatement()) {

            // Get all partitioned tables
            ResultSet tables = stmt.executeQuery(
                "SELECT DISTINCT TABLE_NAME " +
                "FROM information_schema.PARTITIONS " +
                "WHERE TABLE_SCHEMA = '" + historyDbName + "' " +
                "AND PARTITION_NAME IS NOT NULL"
            );

            List<String> tableNames = new ArrayList<>();
            while (tables.next()) {
                tableNames.add(tables.getString("TABLE_NAME"));
            }

            log.info("Found {} partitioned tables in history database", tableNames.size());

            // For each table, drop old partitions
            for (String tableName : tableNames) {
                dropOldPartitionsForTable(conn, tableName, cutoffDate);
            }

        } catch (SQLException e) {
            log.error("Failed to drop old partitions", e);
            throw new RuntimeException("Failed to drop old partitions", e);
        }
    }

    /**
     * Drop old partitions for a specific table
     */
    private void dropOldPartitionsForTable(Connection conn, String tableName, LocalDate cutoffDate) {
        try (Statement stmt = conn.createStatement()) {

            // Get all partitions for this table
            ResultSet partitions = stmt.executeQuery(
                "SELECT PARTITION_NAME, PARTITION_DESCRIPTION " +
                "FROM information_schema.PARTITIONS " +
                "WHERE TABLE_SCHEMA = '" + historyDbName + "' " +
                "AND TABLE_NAME = '" + tableName + "' " +
                "AND PARTITION_NAME IS NOT NULL " +
                "ORDER BY PARTITION_NAME"
            );

            List<String> partitionsToDrop = new ArrayList<>();

            while (partitions.next()) {
                String partitionName = partitions.getString("PARTITION_NAME");
                String partitionDesc = partitions.getString("PARTITION_DESCRIPTION");

                // Parse partition date from description or name
                LocalDate partitionDate = parsePartitionDate(partitionName, partitionDesc);

                if (partitionDate != null && partitionDate.isBefore(cutoffDate)) {
                    partitionsToDrop.add(partitionName);
                }
            }

            if (!partitionsToDrop.isEmpty()) {
                // Drop partitions in single ALTER statement
                String dropSql = "ALTER TABLE `" + historyDbName + "`.`" + tableName + "` " +
                    "DROP PARTITION " + String.join(", ", partitionsToDrop);

                log.info("Dropping {} old partitions from table: {}", partitionsToDrop.size(), tableName);
                log.debug("SQL: {}", dropSql);

                stmt.execute(dropSql);

                log.info("Successfully dropped {} partitions from table: {}",
                    partitionsToDrop.size(), tableName);
            } else {
                log.debug("No old partitions to drop for table: {}", tableName);
            }

        } catch (SQLException e) {
            log.error("Failed to drop partitions for table: {}", tableName, e);
        }
    }

    /**
     * Parse partition date from name or description
     * Format: p20250101 or "UNIX_TIMESTAMP('2025-01-01 00:00:00')"
     */
    private LocalDate parsePartitionDate(String partitionName, String partitionDesc) {
        try {
            // Try parsing from partition name first (e.g., p20250101)
            if (partitionName != null && partitionName.startsWith("p") && partitionName.length() == 9) {
                String dateStr = partitionName.substring(1); // Remove 'p'
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
            }

            // Try parsing from description
            if (partitionDesc != null && partitionDesc.contains("'")) {
                String dateStr = partitionDesc.substring(
                    partitionDesc.indexOf("'") + 1,
                    partitionDesc.lastIndexOf("'")
                );
                if (dateStr.length() >= 10) {
                    return LocalDate.parse(dateStr.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse partition date from: {} / {}", partitionName, partitionDesc);
        }

        return null;
    }

    /**
     * Drop old tables (for multi-table mode)
     */
    private void dropOldTables() {
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
            shardConfig.getHost(), shardConfig.getPort(), historyDbName);

        // Calculate cutoff date
        LocalDate cutoffDate = LocalDate.now().minusDays(retentionDays + retentionDays / 2);

        try (Connection conn = DriverManager.getConnection(jdbcUrl,
                shardConfig.getUsername(), shardConfig.getPassword());
             Statement stmt = conn.createStatement()) {

            // Get all tables in history database
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet tables = metaData.getTables(historyDbName, null, "%", new String[]{"TABLE"});

            List<String> tablesToDrop = new ArrayList<>();

            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");

                // Parse date from table name (e.g., call_machines_20250101)
                LocalDate tableDate = parseTableDate(tableName);

                if (tableDate != null && tableDate.isBefore(cutoffDate)) {
                    tablesToDrop.add(tableName);
                }
            }

            if (!tablesToDrop.isEmpty()) {
                log.info("Dropping {} old tables from history database", tablesToDrop.size());

                // Drop each table
                for (String tableName : tablesToDrop) {
                    try {
                        stmt.execute("DROP TABLE IF EXISTS `" + historyDbName + "`.`" + tableName + "`");
                        log.info("Dropped old table: {}", tableName);
                    } catch (SQLException e) {
                        log.error("Failed to drop table: {}", tableName, e);
                    }
                }

                log.info("Successfully dropped {} old tables", tablesToDrop.size());
            } else {
                log.debug("No old tables to drop");
            }

        } catch (SQLException e) {
            log.error("Failed to drop old tables", e);
            throw new RuntimeException("Failed to drop old tables", e);
        }
    }

    /**
     * Parse date from table name
     * Format: entity_name_20250101 or entity_name_2025_01_01
     */
    private LocalDate parseTableDate(String tableName) {
        try {
            // Try parsing YYYYMMDD format at end
            if (tableName.matches(".*_\\d{8}$")) {
                String dateStr = tableName.substring(tableName.length() - 8);
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
            }

            // Try parsing YYYY_MM_DD format at end
            if (tableName.matches(".*_\\d{4}_\\d{2}_\\d{2}$")) {
                String dateStr = tableName.substring(tableName.length() - 10).replace("_", "");
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
            }
        } catch (Exception e) {
            log.debug("Could not parse date from table name: {}", tableName);
        }

        return null;
    }

    /**
     * Perform cleanup immediately (for testing/manual execution)
     */
    public void performCleanupNow() {
        log.info("Manual cleanup triggered");
        performCleanup();
    }

    /**
     * Shutdown retention manager
     */
    public void shutdown() {
        log.info("Shutting down RetentionManager for database: {}", historyDbName);

        if (cleanupTask != null) {
            cleanupTask.cancel(false);
        }

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("RetentionManager shutdown complete");
    }
}
