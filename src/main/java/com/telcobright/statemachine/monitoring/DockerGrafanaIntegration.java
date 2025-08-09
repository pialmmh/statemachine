package com.telcobright.statemachine.monitoring;

import com.telcobright.statemachine.StateMachineContextEntity;
import com.telcobright.statemachine.FluentStateMachineBuilder;
import com.telcobright.statemachine.GenericStateMachine;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/**
 * Docker Grafana Integration for State Machine Monitoring
 * 
 * Automatically connects to the Docker PostgreSQL instance and sends
 * snapshot data for visualization in Grafana dashboards.
 * 
 * Usage:
 * 1. Start Docker containers: docker-compose up -d
 * 2. Use this class to create monitored state machines
 * 3. View data in Grafana at http://localhost:3000
 */
public class DockerGrafanaIntegration {
    
    // Docker container connection details
    private static final String DOCKER_DB_HOST = "localhost";
    private static final String DOCKER_DB_PORT = "5432";
    private static final String DOCKER_DB_NAME = "statemachine_monitoring";
    private static final String DOCKER_DB_USER = "statemachine";
    private static final String DOCKER_DB_PASSWORD = "monitoring123";
    
    private static final String DOCKER_GRAFANA_URL = "http://localhost:3000";
    private static final String DOCKER_GRAFANA_USER = "admin";
    private static final String DOCKER_GRAFANA_PASSWORD = "statemachine123";
    
    private static Connection dbConnection;
    private static GrafanaIntegration grafanaIntegration;
    
    /**
     * Initialize connection to Docker PostgreSQL instance
     */
    public static void initialize() throws SQLException {
        if (dbConnection == null || dbConnection.isClosed()) {
            String jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s", 
                                         DOCKER_DB_HOST, DOCKER_DB_PORT, DOCKER_DB_NAME);
            
            Properties props = new Properties();
            props.setProperty("user", DOCKER_DB_USER);
            props.setProperty("password", DOCKER_DB_PASSWORD);
            props.setProperty("ssl", "false");
            props.setProperty("autoReconnect", "true");
            props.setProperty("maxReconnects", "3");
            
            System.out.println("🐳 Connecting to Docker PostgreSQL: " + jdbcUrl);
            
            try {
                // Load PostgreSQL driver
                Class.forName("org.postgresql.Driver");
                
                dbConnection = DriverManager.getConnection(jdbcUrl, props);
                dbConnection.setAutoCommit(false); // Use transactions
                
                grafanaIntegration = new GrafanaIntegration(dbConnection);
                
                // Test connection
                if (grafanaIntegration.testGrafanaIntegration()) {
                    System.out.println("✅ Docker Grafana integration initialized successfully!");
                    System.out.println("📊 Grafana Dashboard: " + DOCKER_GRAFANA_URL);
                    System.out.println("👤 Login: " + DOCKER_GRAFANA_USER + " / " + DOCKER_GRAFANA_PASSWORD);
                } else {
                    System.err.println("⚠️  Grafana integration test failed, but connection established");
                }
                
            } catch (ClassNotFoundException e) {
                throw new SQLException("PostgreSQL driver not found. Add postgresql dependency to your project.", e);
            } catch (SQLException e) {
                System.err.println("❌ Failed to connect to Docker PostgreSQL");
                System.err.println("💡 Make sure Docker containers are running: docker-compose up -d");
                throw e;
            }
        }
    }
    
    /**
     * Create a state machine that automatically sends data to Docker Grafana
     */
    public static <TPersistingEntity extends StateMachineContextEntity<?>, TContext> 
           FluentStateMachineBuilder<TPersistingEntity, TContext> createMonitoredMachine(
               String machineId, Class<TPersistingEntity> entityClass) throws SQLException {
        
        initialize();
        
        // Create Grafana-integrated recorder
        DatabaseSnapshotRecorder<TPersistingEntity, TContext> recorder = 
            GrafanaIntegration.createGrafanaRecorder(dbConnection, entityClass, SnapshotConfig.comprehensiveConfig());
        
        System.out.println("🔍 Created monitored state machine: " + machineId);
        System.out.println("📊 Data will be sent to Docker Grafana automatically");
        System.out.println("🎯 View dashboard at: " + DOCKER_GRAFANA_URL + "/d/statemachine-overview");
        
        return FluentStateMachineBuilder
            .<TPersistingEntity, TContext>create(machineId)
            .enableDebug(recorder)
            .enableDebugWithAutoRunId();
    }
    
    /**
     * Create monitored machine with custom configuration
     */
    public static <TPersistingEntity extends StateMachineContextEntity<?>, TContext> 
           FluentStateMachineBuilder<TPersistingEntity, TContext> createMonitoredMachine(
               String machineId, Class<TPersistingEntity> entityClass, SnapshotConfig config) throws SQLException {
        
        initialize();
        
        DatabaseSnapshotRecorder<TPersistingEntity, TContext> recorder = 
            GrafanaIntegration.createGrafanaRecorder(dbConnection, entityClass, config);
        
        return FluentStateMachineBuilder
            .<TPersistingEntity, TContext>create(machineId)
            .enableDebug(recorder);
    }
    
    /**
     * Quick setup method for testing - creates machine and runs basic validation
     */
    public static void quickSetupTest() {
        try {
            System.out.println("🧪 Running Docker Grafana integration test...");
            
            initialize();
            
            // Test database connectivity
            var stmt = dbConnection.createStatement();
            var rs = stmt.executeQuery("SELECT COUNT(*) FROM state_machine_snapshots");
            
            if (rs.next()) {
                int count = rs.getInt(1);
                System.out.println("✅ Database connectivity test passed. Existing snapshots: " + count);
            }
            
            // Test table structure
            rs = stmt.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'state_machine_snapshots'");
            if (rs.next() && rs.getInt(1) > 0) {
                System.out.println("✅ Database schema test passed");
            } else {
                System.err.println("❌ Database schema missing. Run docker-compose up to initialize.");
                return;
            }
            
            System.out.println("\n🎉 Docker Grafana integration ready!");
            System.out.println("📋 Next steps:");
            System.out.println("   1. Use DockerGrafanaIntegration.createMonitoredMachine() in your tests");
            System.out.println("   2. Run your state machine operations normally");
            System.out.println("   3. View results at: " + DOCKER_GRAFANA_URL);
            System.out.println("   4. Login with: " + DOCKER_GRAFANA_USER + " / " + DOCKER_GRAFANA_PASSWORD);
            
        } catch (Exception e) {
            System.err.println("❌ Docker Grafana integration test failed: " + e.getMessage());
            System.err.println("💡 Ensure Docker containers are running: docker-compose up -d");
        }
    }
    
    /**
     * Save a test snapshot to verify integration
     */
    public static void sendTestSnapshot() {
        try {
            initialize();
            
            // Create a test snapshot
            DefaultMachineSnapshot testSnapshot = new DefaultMachineSnapshot();
            testSnapshot.setMachineId("test-machine-" + System.currentTimeMillis());
            testSnapshot.setMachineType("TestEntity");
            testSnapshot.setVersion(1L);
            testSnapshot.setStateBefore("INITIAL");
            testSnapshot.setStateAfter("PROCESSED");
            testSnapshot.setEventType("TEST_EVENT");
            testSnapshot.setTransitionDurationMillis(42L);
            testSnapshot.setCreatedAt(java.time.LocalDateTime.now());
            testSnapshot.setRunId("test-run-" + System.currentTimeMillis());
            testSnapshot.setMachineOnlineStatus(true);
            testSnapshot.setStateOfflineStatus(false);
            testSnapshot.setRegistryStatus("TEST_ACTIVE");
            
            // Send to Grafana
            grafanaIntegration.saveSnapshotForGrafana(testSnapshot, null).join();
            
            System.out.println("✅ Test snapshot sent to Docker Grafana successfully!");
            System.out.println("🔍 Check the dashboard to see the new data point");
            
        } catch (Exception e) {
            System.err.println("❌ Failed to send test snapshot: " + e.getMessage());
        }
    }
    
    /**
     * Get connection info for manual database access
     */
    public static void printConnectionInfo() {
        System.out.println("\n🐳 Docker Environment Connection Details:");
        System.out.println("═══════════════════════════════════════");
        System.out.println("📊 Grafana Dashboard:");
        System.out.println("   URL: " + DOCKER_GRAFANA_URL);
        System.out.println("   Username: " + DOCKER_GRAFANA_USER);
        System.out.println("   Password: " + DOCKER_GRAFANA_PASSWORD);
        System.out.println();
        System.out.println("🗄️  PostgreSQL Database:");
        System.out.println("   Host: " + DOCKER_DB_HOST + ":" + DOCKER_DB_PORT);
        System.out.println("   Database: " + DOCKER_DB_NAME);
        System.out.println("   Username: " + DOCKER_DB_USER);
        System.out.println("   Password: " + DOCKER_DB_PASSWORD);
        System.out.println();
        System.out.println("🔧 pgAdmin (Database Management):");
        System.out.println("   URL: http://localhost:8080");
        System.out.println("   Username: admin@statemachine.com");
        System.out.println("   Password: pgadmin123");
        System.out.println();
        System.out.println("🚀 Docker Commands:");
        System.out.println("   Start: docker-compose up -d");
        System.out.println("   Stop: docker-compose down");
        System.out.println("   Logs: docker-compose logs -f");
        System.out.println("   Reset: docker-compose down -v && docker-compose up -d");
    }
    
    /**
     * Gracefully shutdown connections
     */
    public static void shutdown() {
        try {
            if (grafanaIntegration != null) {
                grafanaIntegration.close();
            }
            if (dbConnection != null && !dbConnection.isClosed()) {
                dbConnection.close();
            }
            System.out.println("🔌 Docker Grafana integration connections closed");
        } catch (SQLException e) {
            System.err.println("Warning: Error closing connections: " + e.getMessage());
        }
    }
    
    /**
     * Add shutdown hook to cleanup connections
     */
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(DockerGrafanaIntegration::shutdown));
    }
}