package com.telcobright.statemachine.debugger.stresstest;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.timeout.TimeoutManager;
import com.telcobright.statemachine.persistence.*;
import com.telcobright.statemachine.db.MySQLPartitionedRepository;
import com.telcobright.examples.callmachine.events.*;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.PrintStream;
import java.util.*;

/**
 * Test MySQL partitioned repository with real database
 */
public class MySQLPartitionedTest {
    
    private static final int TOTAL_MACHINES = 100;
    private static final PrintStream originalOut = System.out;
    private static final PrintStream originalErr = System.err;
    private static final PrintStream nullStream = new PrintStream(new java.io.OutputStream() {
        public void write(int b) {}
    });
    
    public static void main(String[] args) throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   MySQL PARTITIONED REPOSITORY TEST");
        System.out.println("=".repeat(80));
        System.out.println();
        
        runTest();
    }
    
    private static void suppressOutput() {
        System.setOut(nullStream);
        System.setErr(nullStream);
    }
    
    private static void restoreOutput() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }
    
    private static DataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/statemachine_test?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC");
        config.setUsername("root");
        config.setPassword("123456");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        return new HikariDataSource(config);
    }
    
    private static void runTest() throws Exception {
        DataSource dataSource = null;
        
        try {
            // Create database connection
            System.out.println("📋 Connecting to MySQL database...");
            dataSource = createDataSource();
            System.out.println("✅ Connected to MySQL");
            
            // Test both partitioning strategies
            testHashPartitioning(dataSource);
            testMonthlyPartitioning(dataSource);
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (dataSource instanceof HikariDataSource) {
                ((HikariDataSource) dataSource).close();
            }
        }
    }
    
    private static void testHashPartitioning(DataSource dataSource) throws Exception {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("   TEST 1: HASH PARTITIONING");
        System.out.println("=".repeat(60));
        
        // Create hash-partitioned repository
        MySQLPartitionedRepository<SimpleCallEntity, String> repository = 
            new MySQLPartitionedRepository<>(
                dataSource, 
                SimpleCallEntity.class, 
                "call_states_hash",
                MySQLPartitionedRepository.PartitionStrategy.HASH
            );
        repository.setHashPartitionCount(5); // 5 hash partitions
        
        // Setup registry
        suppressOutput();
        TimeoutManager timeoutManager = new TimeoutManager();
        StateMachineRegistry registry = new StateMachineRegistry("hash-test", timeoutManager, 9997);
        
        // Configure registry to use partitioned persistence
        PersistenceProvider<SimpleCallEntity> provider = 
            new PartitionedRepositoryPersistenceProvider<>(repository, SimpleCallEntity.class);
        registry.setPersistenceProvider(provider);
        restoreOutput();
        
        System.out.println("\n📋 Creating " + TOTAL_MACHINES + " machines with hash partitioning...");
        
        long startTime = System.currentTimeMillis();
        
        suppressOutput();
        for (int i = 0; i < TOTAL_MACHINES; i++) {
            String machineId = "hash-" + i;
            
            // Create entity
            SimpleCallEntity entity = new SimpleCallEntity(machineId);
            entity.setCallerId("+1-555-" + String.format("%04d", i));
            entity.setCalleeId("+1-555-" + String.format("%04d", (i + 1)));
            
            // Create and register machine
            GenericStateMachine<SimpleCallEntity, Object> machine = createMachine(machineId);
            machine.setPersistingEntity(entity);
            registry.register(machineId, machine);
            
            // Persist to repository
            repository.insert(entity);
        }
        restoreOutput();
        
        long creationTime = System.currentTimeMillis() - startTime;
        System.out.println("✅ Created " + TOTAL_MACHINES + " machines in " + 
            String.format("%.2f", creationTime/1000.0) + " seconds");
        
        // Test retrieval
        System.out.println("\n📋 Testing retrieval from partitioned tables...");
        int found = 0;
        for (int i = 0; i < 10; i++) {
            String id = "hash-" + i;
            SimpleCallEntity entity = repository.findById(id);
            if (entity != null) {
                found++;
            }
        }
        System.out.println("✅ Retrieved " + found + "/10 entities from partitioned storage");
        
        // Show statistics
        repository.printStatistics();
        
        // Cleanup
        suppressOutput();
        registry.shutdown();
        timeoutManager.shutdown();
        restoreOutput();
    }
    
    private static void testMonthlyPartitioning(DataSource dataSource) throws Exception {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("   TEST 2: MONTHLY PARTITIONING");
        System.out.println("=".repeat(60));
        
        // Create monthly-partitioned repository
        MySQLPartitionedRepository<SimpleCallEntity, String> repository = 
            new MySQLPartitionedRepository<>(
                dataSource, 
                SimpleCallEntity.class, 
                "call_states_monthly",
                MySQLPartitionedRepository.PartitionStrategy.MONTHLY
            );
        repository.setAutoCreatePartitions(true);
        
        // Setup registry
        suppressOutput();
        TimeoutManager timeoutManager = new TimeoutManager();
        StateMachineRegistry registry = new StateMachineRegistry("monthly-test", timeoutManager, 9996);
        
        // Configure registry to use partitioned persistence
        PersistenceProvider<SimpleCallEntity> provider = 
            new PartitionedRepositoryPersistenceProvider<>(repository, SimpleCallEntity.class);
        registry.setPersistenceProvider(provider);
        restoreOutput();
        
        System.out.println("\n📋 Creating " + TOTAL_MACHINES + " machines with monthly partitioning...");
        
        long startTime = System.currentTimeMillis();
        
        suppressOutput();
        for (int i = 0; i < TOTAL_MACHINES; i++) {
            String machineId = "monthly-" + i;
            
            // Create entity (will use current month for partitioning)
            SimpleCallEntity entity = new SimpleCallEntity(machineId);
            entity.setCallerId("+1-555-" + String.format("%04d", i));
            entity.setCalleeId("+1-555-" + String.format("%04d", (i + 1)));
            
            // Create and register machine
            GenericStateMachine<SimpleCallEntity, Object> machine = createMachine(machineId);
            machine.setPersistingEntity(entity);
            registry.register(machineId, machine);
            
            // Persist to repository
            repository.insert(entity);
        }
        restoreOutput();
        
        long creationTime = System.currentTimeMillis() - startTime;
        System.out.println("✅ Created " + TOTAL_MACHINES + " machines in " + 
            String.format("%.2f", creationTime/1000.0) + " seconds");
        
        // Test retrieval
        System.out.println("\n📋 Testing retrieval from monthly partitions...");
        int found = 0;
        for (int i = 0; i < 10; i++) {
            String id = "monthly-" + i;
            SimpleCallEntity entity = repository.findById(id);
            if (entity != null) {
                found++;
                System.out.println("  Found: " + id + " (state: " + entity.getCurrentState() + ")");
            }
        }
        System.out.println("✅ Retrieved " + found + "/10 entities from monthly partitions");
        
        // Test state updates
        System.out.println("\n📋 Testing state updates...");
        suppressOutput();
        registry.routeEvent("monthly-0", new IncomingCall("+1", "+2"));
        registry.routeEvent("monthly-0", new Answer());
        registry.routeEvent("monthly-1", new IncomingCall("+1", "+2"));
        restoreOutput();
        
        // Retrieve and check updated states
        SimpleCallEntity entity0 = repository.findById("monthly-0");
        SimpleCallEntity entity1 = repository.findById("monthly-1");
        
        if (entity0 != null) {
            System.out.println("  monthly-0 state after events: " + entity0.getCurrentState());
        }
        if (entity1 != null) {
            System.out.println("  monthly-1 state after events: " + entity1.getCurrentState());
        }
        
        // Show statistics
        repository.printStatistics();
        
        // Cleanup
        suppressOutput();
        registry.shutdown();
        timeoutManager.shutdown();
        restoreOutput();
        
        System.out.println("\n✅ MySQL Partitioned Repository Tests Complete!");
    }
    
    private static GenericStateMachine<SimpleCallEntity, Object> createMachine(String machineId) {
        return EnhancedFluentBuilder.<SimpleCallEntity, Object>create(machineId)
            .initialState("IDLE")
            .state("IDLE")
                .on(IncomingCall.class).to("RINGING")
            .done()
            .state("RINGING")
                .on(Answer.class).to("CONNECTED")
                .on(Hangup.class).to("IDLE")
            .done()
            .state("CONNECTED")
                .on(Hangup.class).to("IDLE")
            .done()
            .build();
    }
}