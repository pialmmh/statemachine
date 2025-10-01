package com.telcobright.statemachine.history;

import com.telcobright.splitverse.config.ShardConfig;
import com.telcobright.statewalk.persistence.EntityGraphMapper;
import com.telcobright.statewalk.persistence.SplitVerseGraphAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages archival of completed state machines to history database
 *
 * Features:
 * - Asynchronous archival with guaranteed delivery
 * - Atomic transaction (INSERT + DELETE)
 * - Archives entire entity object graph
 * - Retry mechanism with exponential backoff
 * - Critical error handling with registry shutdown
 */
public class HistoryArchivalManager {

    private static final Logger log = LoggerFactory.getLogger(HistoryArchivalManager.class);

    private final String activeDbName;
    private final String historyDbName;
    private final ShardConfig shardConfig;
    private final EntityGraphMapper graphMapper;

    // Async archival
    private final ExecutorService archivalExecutor;
    private final BlockingQueue<ArchivalTask> archivalQueue;
    private final int maxRetries;
    private final int retryDelayMs;

    // Statistics
    private final AtomicLong successfulArchivals = new AtomicLong(0);
    private final AtomicLong failedArchivals = new AtomicLong(0);
    private final AtomicInteger queueSize = new AtomicInteger(0);

    // Shutdown callback
    private Runnable onCriticalFailure;

    private volatile boolean shutdown = false;

    /**
     * Create history archival manager
     *
     * @param registryId Registry identifier (e.g., "call-machine-registry")
     * @param shardConfig Database configuration
     * @param graphMapper Entity graph mapper for discovering related entities
     */
    public HistoryArchivalManager(String registryId, ShardConfig shardConfig, EntityGraphMapper graphMapper) {
        this.activeDbName = registryId;
        this.historyDbName = registryId + "-history";
        this.shardConfig = shardConfig;
        this.graphMapper = graphMapper;

        // Configuration
        this.maxRetries = 3;
        this.retryDelayMs = 1000;

        // Async archival setup
        this.archivalQueue = new LinkedBlockingQueue<>();
        this.archivalExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "HistoryArchival-" + registryId);
            t.setDaemon(false); // Non-daemon to ensure completion
            return t;
        });

        // Initialize history database
        initializeHistoryDatabase();

        // Start archival workers
        startArchivalWorkers();

        log.info("HistoryArchivalManager initialized for registry: {} -> history: {}",
            activeDbName, historyDbName);
    }

    /**
     * Initialize history database with same schema as active database
     */
    private void initializeHistoryDatabase() {
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
            shardConfig.getHost(), shardConfig.getPort());

        try (Connection conn = DriverManager.getConnection(jdbcUrl,
                shardConfig.getUsername(), shardConfig.getPassword());
             Statement stmt = conn.createStatement()) {

            // Create history database if not exists
            stmt.execute("CREATE DATABASE IF NOT EXISTS `" + historyDbName + "` " +
                        "DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");

            log.info("History database initialized: {}", historyDbName);

            // Copy schema from active database to history database
            copyDatabaseSchema();

        } catch (SQLException e) {
            log.error("CRITICAL: Failed to initialize history database", e);
            throw new RuntimeException("Failed to initialize history database: " + historyDbName, e);
        }
    }

    /**
     * Copy all table schemas from active DB to history DB
     */
    private void copyDatabaseSchema() {
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
            shardConfig.getHost(), shardConfig.getPort(), activeDbName);

        try (Connection conn = DriverManager.getConnection(jdbcUrl,
                shardConfig.getUsername(), shardConfig.getPassword())) {

            // Get all tables in active database
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet tables = metaData.getTables(activeDbName, null, "%", new String[]{"TABLE"});

            List<String> tableNames = new ArrayList<>();
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                tableNames.add(tableName);
            }

            log.info("Found {} tables to replicate in history database", tableNames.size());

            // Create each table in history database
            try (Statement stmt = conn.createStatement()) {
                for (String tableName : tableNames) {
                    try {
                        // Get CREATE TABLE statement
                        ResultSet rs = stmt.executeQuery("SHOW CREATE TABLE `" + activeDbName + "`.`" + tableName + "`");
                        if (rs.next()) {
                            String createTableStmt = rs.getString(2);

                            // Execute in history database
                            String historyCreateStmt = createTableStmt.replace(
                                "CREATE TABLE `" + tableName + "`",
                                "CREATE TABLE IF NOT EXISTS `" + historyDbName + "`.`" + tableName + "`"
                            );

                            try (Connection historyConn = DriverManager.getConnection(
                                    jdbcUrl.replace(activeDbName, historyDbName),
                                    shardConfig.getUsername(), shardConfig.getPassword());
                                 Statement historyStmt = historyConn.createStatement()) {

                                historyStmt.execute(historyCreateStmt);
                                log.debug("Created table in history DB: {}", tableName);
                            }
                        }
                    } catch (SQLException e) {
                        log.warn("Failed to copy table schema: {} - {}", tableName, e.getMessage());
                    }
                }
            }

            log.info("Schema replication to history database completed");

        } catch (SQLException e) {
            log.error("Failed to copy database schema", e);
            throw new RuntimeException("Failed to copy database schema to history", e);
        }
    }

    /**
     * Start archival worker threads
     */
    private void startArchivalWorkers() {
        for (int i = 0; i < 2; i++) {
            archivalExecutor.submit(this::archivalWorker);
        }
    }

    /**
     * Archival worker thread - processes queue
     */
    private void archivalWorker() {
        while (!shutdown) {
            try {
                ArchivalTask task = archivalQueue.poll(1, TimeUnit.SECONDS);
                if (task != null) {
                    queueSize.decrementAndGet();
                    processArchivalTask(task);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Process archival task with retry logic
     */
    private void processArchivalTask(ArchivalTask task) {
        MDC.put("machineId", task.machineId);
        MDC.put("registryId", activeDbName);
        MDC.put("attempt", String.valueOf(task.attemptCount));

        try {
            log.debug("Processing archival for machine: {}", task.machineId);

            // Execute atomic archival
            archiveMachineAtomic(task.machineId, task.entityGraph);

            successfulArchivals.incrementAndGet();
            log.info("Successfully archived machine to history: {}", task.machineId);

        } catch (Exception e) {
            task.attemptCount++;

            if (task.attemptCount <= maxRetries) {
                // Retry with exponential backoff
                long delay = retryDelayMs * (long) Math.pow(2, task.attemptCount - 1);
                log.warn("Archival failed, will retry in {}ms (attempt {}/{}): {}",
                    delay, task.attemptCount, maxRetries, task.machineId, e);

                try {
                    Thread.sleep(delay);
                    archivalQueue.offer(task);
                    queueSize.incrementAndGet();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } else {
                // Max retries exceeded - CRITICAL ERROR
                failedArchivals.incrementAndGet();
                log.error("CRITICAL: Failed to archive machine after {} attempts - SHUTTING DOWN REGISTRY",
                    maxRetries, e);

                // Trigger registry shutdown
                if (onCriticalFailure != null) {
                    onCriticalFailure.run();
                }
            }
        } finally {
            MDC.clear();
        }
    }

    /**
     * Archive machine and all related entities atomically
     */
    private void archiveMachineAtomic(String machineId, Map<Class<?>, List<Object>> entityGraph) throws SQLException {
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
            shardConfig.getHost(), shardConfig.getPort());

        try (Connection conn = DriverManager.getConnection(jdbcUrl,
                shardConfig.getUsername(), shardConfig.getPassword())) {

            conn.setAutoCommit(false);

            try {
                // Archive each entity type
                for (Map.Entry<Class<?>, List<Object>> entry : entityGraph.entrySet()) {
                    Class<?> entityClass = entry.getKey();
                    List<Object> entities = entry.getValue();

                    String tableName = getTableName(entityClass);

                    for (Object entity : entities) {
                        // INSERT into history database
                        String insertSql = String.format(
                            "INSERT INTO `%s`.`%s` SELECT * FROM `%s`.`%s` WHERE id = ?",
                            historyDbName, tableName, activeDbName, tableName
                        );

                        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                            insertStmt.setString(1, machineId);
                            int inserted = insertStmt.executeUpdate();

                            if (inserted == 0) {
                                log.warn("No rows inserted for entity: {} with id: {}", tableName, machineId);
                            }
                        }

                        // DELETE from active database
                        String deleteSql = String.format(
                            "DELETE FROM `%s`.`%s` WHERE id = ?",
                            activeDbName, tableName
                        );

                        try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                            deleteStmt.setString(1, machineId);
                            int deleted = deleteStmt.executeUpdate();

                            if (deleted == 0) {
                                log.warn("No rows deleted for entity: {} with id: {}", tableName, machineId);
                            }
                        }
                    }

                    log.debug("Archived {} entities of type: {}", entities.size(), entityClass.getSimpleName());
                }

                conn.commit();
                log.debug("Transaction committed for machine: {}", machineId);

            } catch (SQLException e) {
                conn.rollback();
                log.error("Transaction rolled back for machine: {}", machineId, e);
                throw e;
            }
        }
    }

    /**
     * Get table name from entity class
     */
    private String getTableName(Class<?> entityClass) {
        // Check for @Table annotation
        if (entityClass.isAnnotationPresent(com.telcobright.core.annotation.Table.class)) {
            com.telcobright.core.annotation.Table tableAnnotation =
                entityClass.getAnnotation(com.telcobright.core.annotation.Table.class);
            return tableAnnotation.name();
        }

        // Default: convert class name to snake_case
        String className = entityClass.getSimpleName();
        return className.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase() + "s";
    }

    /**
     * Submit machine for archival (async)
     */
    public void archiveMachine(String machineId, Map<Class<?>, List<Object>> entityGraph) {
        if (shutdown) {
            log.warn("HistoryArchivalManager is shut down, cannot archive: {}", machineId);
            return;
        }

        ArchivalTask task = new ArchivalTask(machineId, entityGraph);
        archivalQueue.offer(task);
        queueSize.incrementAndGet();

        log.debug("Queued machine for archival: {} (queue size: {})", machineId, queueSize.get());
    }

    /**
     * Scan active database and move all finished machines to history
     * Called during startup
     */
    public void moveAllFinishedMachines(Set<String> finalStateNames) throws SQLException {
        log.info("Scanning active database for machines in final states...");

        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
            shardConfig.getHost(), shardConfig.getPort(), activeDbName);

        List<String> finishedMachineIds = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(jdbcUrl,
                shardConfig.getUsername(), shardConfig.getPassword());
             Statement stmt = conn.createStatement()) {

            // Find primary entity table (assumes first table is primary)
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet tables = metaData.getTables(activeDbName, null, "%", new String[]{"TABLE"});

            String primaryTable = null;
            if (tables.next()) {
                primaryTable = tables.getString("TABLE_NAME");
            }

            if (primaryTable == null) {
                log.warn("No tables found in active database");
                return;
            }

            // Query for machines in final states
            String query = String.format("SELECT id, current_state FROM `%s`.`%s`",
                activeDbName, primaryTable);

            try (ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    String machineId = rs.getString("id");
                    String currentState = rs.getString("current_state");

                    if (currentState != null && finalStateNames.contains(currentState)) {
                        finishedMachineIds.add(machineId);
                    }
                }
            }
        }

        log.info("Found {} machines in final states to archive", finishedMachineIds.size());

        // Archive each machine synchronously during startup
        for (String machineId : finishedMachineIds) {
            try {
                MDC.put("machineId", machineId);
                MDC.put("phase", "startup-scan");

                // Load entity graph for this machine
                Map<Class<?>, List<Object>> entityGraph = loadEntityGraph(machineId);

                // Archive synchronously
                archiveMachineAtomic(machineId, entityGraph);

                log.info("Archived machine during startup: {}", machineId);

            } catch (Exception e) {
                log.error("CRITICAL: Failed to archive machine during startup - ABORTING INITIALIZATION", e);
                MDC.clear();
                throw new SQLException("Failed to archive machine during startup: " + machineId, e);
            } finally {
                MDC.clear();
            }
        }

        log.info("Startup archival completed successfully");
    }

    /**
     * Load entity graph for a machine
     */
    private Map<Class<?>, List<Object>> loadEntityGraph(String machineId) {
        // TODO: Use graphMapper to discover and load all related entities
        // For now, return empty map as placeholder
        return new HashMap<>();
    }

    /**
     * Set callback for critical failures
     */
    public void setOnCriticalFailure(Runnable callback) {
        this.onCriticalFailure = callback;
    }

    /**
     * Get archival statistics
     */
    public ArchivalStats getStats() {
        return new ArchivalStats(
            successfulArchivals.get(),
            failedArchivals.get(),
            queueSize.get()
        );
    }

    /**
     * Graceful shutdown - wait for pending archivals
     */
    public void shutdown() {
        log.info("Shutting down HistoryArchivalManager...");
        shutdown = true;

        // Wait for queue to drain
        int waitSeconds = 30;
        for (int i = 0; i < waitSeconds && queueSize.get() > 0; i++) {
            log.info("Waiting for {} pending archivals to complete...", queueSize.get());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        archivalExecutor.shutdown();
        try {
            if (!archivalExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                archivalExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            archivalExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("HistoryArchivalManager shutdown complete. Stats: {}", getStats());
    }

    /**
     * Archival task for queue
     */
    private static class ArchivalTask {
        final String machineId;
        final Map<Class<?>, List<Object>> entityGraph;
        int attemptCount = 0;

        ArchivalTask(String machineId, Map<Class<?>, List<Object>> entityGraph) {
            this.machineId = machineId;
            this.entityGraph = entityGraph;
        }
    }

    /**
     * Archival statistics
     */
    public static class ArchivalStats {
        public final long successful;
        public final long failed;
        public final int queueSize;

        ArchivalStats(long successful, long failed, int queueSize) {
            this.successful = successful;
            this.failed = failed;
            this.queueSize = queueSize;
        }

        @Override
        public String toString() {
            return String.format("ArchivalStats[successful=%d, failed=%d, queued=%d]",
                successful, failed, queueSize);
        }
    }
}
