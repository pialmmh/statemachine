package com.telcobright.statemachine.persistence.example;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.timeout.TimeoutManager;
import com.telcobright.statemachine.persistence.*;
import com.telcobright.statemachine.db.PartitionedRepository;
import com.telcobright.examples.callmachine.events.*;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Complete example showing how to use partitioned repository persistence
 * with the state machine registry
 * 
 * This demonstrates:
 * 1. Creating a custom context entity that supports partitioning
 * 2. Configuring a PartitionedRepository
 * 3. Using it with the StateMachineRegistry
 * 4. Benefits of partitioned storage for high-volume scenarios
 */
public class PartitionedPersistenceExample {
    
    public static void main(String[] args) throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   PARTITIONED PERSISTENCE EXAMPLE");
        System.out.println("=".repeat(80));
        System.out.println();
        
        demonstratePartitionedPersistence();
    }
    
    /**
     * Demonstrate using partitioned persistence with state machines
     */
    private static void demonstratePartitionedPersistence() throws Exception {
        System.out.println("📋 Setting up Partitioned Persistence");
        System.out.println("-".repeat(50));
        
        // Step 1: Create the registry
        TimeoutManager timeoutManager = new TimeoutManager();
        StateMachineRegistry registry = new StateMachineRegistry("partitioned-demo", timeoutManager, 9995);
        
        // Step 2: Create and configure the PartitionedRepository
        // NOTE: In production, you would get the DataSource from your application context
        // DataSource dataSource = getDataSource(); // Your actual data source
        
        /* Example configuration (would need actual DataSource):
        
        // Create the partitioned repository
        PartitionedRepository<PartitionedCallContext, String> repository = 
            new PartitionedRepository<>(
                dataSource,
                PartitionedCallContext.class,
                "call_states",           // Base table name
                "MONTHLY",               // Partition strategy
                true                     // Auto-create partitions
            );
        
        // Configure partition settings
        repository.setPartitionNamingPattern("call_states_%s");  // e.g., call_states_2024_01
        repository.setRetentionMonths(6);                        // Keep 6 months of data
        repository.setArchiveOldPartitions(true);                // Archive before dropping
        
        // Step 3: Configure the registry to use partitioned persistence
        registry.usePartitionedPersistence(repository, PartitionedCallContext.class);
        
        System.out.println("✅ Configured partitioned persistence");
        System.out.println("   Base table: call_states");
        System.out.println("   Partition strategy: MONTHLY");
        System.out.println("   Retention: 6 months");
        */
        
        // For demonstration purposes (without actual database):
        System.out.println("📊 Partitioned Persistence Benefits:");
        System.out.println();
        System.out.println("1. SCALABILITY");
        System.out.println("   - Each partition is a separate table");
        System.out.println("   - Can handle millions of records efficiently");
        System.out.println("   - Parallel query execution across partitions");
        System.out.println();
        
        System.out.println("2. PERFORMANCE");
        System.out.println("   - Partition pruning for faster queries");
        System.out.println("   - Smaller indexes per partition");
        System.out.println("   - Better cache utilization");
        System.out.println();
        
        System.out.println("3. MAINTENANCE");
        System.out.println("   - Easy archival of old data (drop old partitions)");
        System.out.println("   - Faster backups (partition-level backups)");
        System.out.println("   - Online partition maintenance");
        System.out.println();
        
        System.out.println("4. EXAMPLE PARTITION STRUCTURE:");
        System.out.println("   Table: call_states_2024_01 (January 2024)");
        System.out.println("   Table: call_states_2024_02 (February 2024)");
        System.out.println("   Table: call_states_2024_03 (March 2024)");
        System.out.println("   ...");
        System.out.println();
        
        // Step 4: Create state machines with partitioned context
        System.out.println("📋 Creating State Machines with Partitioned Context");
        System.out.println("-".repeat(50));
        
        // Example of creating a machine with custom context
        String machineId = "call-12345";
        
        // Create the context
        PartitionedCallContext context = new PartitionedCallContext(machineId);
        context.setCallerId("+1-555-1234");
        context.setCalleeId("+1-555-5678");
        context.setCallType("INBOUND");
        context.setCustomerId("CUST-001");
        
        System.out.println("Created context:");
        System.out.println("  Machine ID: " + machineId);
        System.out.println("  Sharding Key: " + context.getShardingKey());
        System.out.println("  Partition: call_states_" + context.getShardingKey());
        System.out.println();
        
        // Create state machine with the context
        GenericStateMachine<PartitionedCallContext, Object> machine = 
            EnhancedFluentBuilder.<PartitionedCallContext, Object>create(machineId)
                .initialState("IDLE")
                .persistingEntity(context)  // Set the partitioned context
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
        
        // Register with the registry
        registry.register(machineId, machine);
        
        System.out.println("✅ Machine registered with partitioned context");
        System.out.println();
        
        // Step 5: Process events
        System.out.println("📋 Processing Events");
        System.out.println("-".repeat(50));
        
        // Send events
        registry.routeEvent(machineId, new IncomingCall(context.getCallerId(), context.getCalleeId()));
        System.out.println("Event: IncomingCall -> State: " + machine.getCurrentState());
        
        registry.routeEvent(machineId, new Answer());
        System.out.println("Event: Answer -> State: " + machine.getCurrentState());
        
        // Update context with call details
        context.setCallEndTime(java.time.LocalDateTime.now());
        
        registry.routeEvent(machineId, new Hangup());
        System.out.println("Event: Hangup -> State: " + machine.getCurrentState());
        System.out.println("Call Duration: " + context.getDuration() + " seconds");
        System.out.println();
        
        // Step 6: Query patterns with partitioned data
        System.out.println("📋 Query Patterns with Partitioned Data");
        System.out.println("-".repeat(50));
        System.out.println();
        
        System.out.println("Example queries that benefit from partitioning:");
        System.out.println();
        System.out.println("1. Get all calls for current month:");
        System.out.println("   SELECT * FROM call_states_2024_03");
        System.out.println("   (Only queries current partition)");
        System.out.println();
        
        System.out.println("2. Get call history for customer:");
        System.out.println("   SELECT * FROM call_states_* WHERE customer_id = 'CUST-001'");
        System.out.println("   (Parallel query across partitions)");
        System.out.println();
        
        System.out.println("3. Archive old data:");
        System.out.println("   ALTER TABLE call_states_2023_01 EXCHANGE PARTITION WITH archive_2023_01");
        System.out.println("   DROP TABLE call_states_2023_01");
        System.out.println();
        
        System.out.println("4. Get statistics per partition:");
        System.out.println("   SELECT table_name, table_rows FROM information_schema.tables");
        System.out.println("   WHERE table_name LIKE 'call_states_%'");
        System.out.println();
        
        // Cleanup
        registry.shutdown();
        timeoutManager.shutdown();
        
        System.out.println("=".repeat(80));
        System.out.println("   EXAMPLE COMPLETE");
        System.out.println("=".repeat(80));
        System.out.println();
        System.out.println("💡 Key Takeaways:");
        System.out.println("  - Use partitioned persistence for high-volume scenarios");
        System.out.println("  - Choose appropriate sharding key (date, customer, hash)");
        System.out.println("  - Automatic partition management reduces maintenance");
        System.out.println("  - Significant performance benefits for time-series data");
        System.out.println();
    }
    
    /**
     * Example method to create a DataSource
     * In production, this would come from your application configuration
     */
    private static DataSource getDataSource() {
        // This is just a placeholder
        // In real application, use HikariCP, C3P0, or your app server's connection pool
        return new DataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                // Return actual database connection
                throw new UnsupportedOperationException("Configure actual DataSource");
            }
            
            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                throw new UnsupportedOperationException("Configure actual DataSource");
            }
            
            // Other DataSource methods...
            @Override public java.io.PrintWriter getLogWriter() { return null; }
            @Override public void setLogWriter(java.io.PrintWriter out) {}
            @Override public void setLoginTimeout(int seconds) {}
            @Override public int getLoginTimeout() { return 0; }
            @Override public java.util.logging.Logger getParentLogger() { return null; }
            @Override public <T> T unwrap(Class<T> iface) { return null; }
            @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        };
    }
}