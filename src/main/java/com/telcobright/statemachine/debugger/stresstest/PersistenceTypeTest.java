package com.telcobright.statemachine.debugger.stresstest;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.timeout.TimeoutManager;
import com.telcobright.statemachine.persistence.*;
import com.telcobright.statemachine.db.PartitionedRepository;
import com.telcobright.statemachine.db.entity.ShardingEntity;
import com.telcobright.examples.callmachine.events.*;
import java.time.LocalDateTime;

/**
 * Test class to demonstrate using different persistence types
 * Shows how to configure the registry with:
 * 1. Direct MySQL persistence
 * 2. Optimized MySQL persistence  
 * 3. Partitioned repository persistence
 * 4. No persistence (in-memory only)
 */
public class PersistenceTypeTest {
    
    public static void main(String[] args) throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   PERSISTENCE TYPE TEST");
        System.out.println("=".repeat(80));
        System.out.println();
        
        // Create registries with different persistence types
        testDirectMySQL();
        testOptimizedMySQL();
        testNoPersistence();
        testPartitionedRepo();
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   TEST COMPLETE");
        System.out.println("=".repeat(80));
    }
    
    /**
     * Test with direct MySQL persistence (default)
     */
    private static void testDirectMySQL() throws Exception {
        System.out.println("📋 Test 1: Direct MySQL Persistence");
        System.out.println("-".repeat(50));
        
        TimeoutManager tm = new TimeoutManager();
        StateMachineRegistry registry = new StateMachineRegistry("mysql-direct", tm, 9991);
        
        // Default is MYSQL_DIRECT
        System.out.println("Persistence type: " + registry.getPersistenceType());
        
        // Create and register a machine
        GenericStateMachine machine = createTestMachine("m1");
        registry.register("m1", machine);
        
        // Send an event
        registry.routeEvent("m1", new IncomingCall("+1", "+2"));
        System.out.println("Machine m1 state: " + machine.getCurrentState());
        
        // Simulate removing from memory (would be persisted)
        registry.removeMachine("m1");
        System.out.println("Machine removed from memory");
        
        registry.shutdown();
        tm.shutdown();
        System.out.println("✅ Direct MySQL test complete\n");
    }
    
    /**
     * Test with optimized MySQL persistence
     */
    private static void testOptimizedMySQL() throws Exception {
        System.out.println("📋 Test 2: Optimized MySQL Persistence");
        System.out.println("-".repeat(50));
        
        TimeoutManager tm = new TimeoutManager();
        StateMachineRegistry registry = new StateMachineRegistry("mysql-optimized", tm, 9992);
        
        // Set to optimized MySQL
        registry.setPersistenceType(PersistenceType.MYSQL_OPTIMIZED);
        System.out.println("Persistence type: " + registry.getPersistenceType());
        
        // Create and register machines
        for (int i = 0; i < 5; i++) {
            GenericStateMachine machine = createTestMachine("opt-" + i);
            registry.register("opt-" + i, machine);
        }
        
        // Send events to all machines
        for (int i = 0; i < 5; i++) {
            registry.routeEvent("opt-" + i, new IncomingCall("+1", "+2"));
        }
        
        System.out.println("Created and processed 5 machines with optimized persistence");
        
        registry.shutdown();
        tm.shutdown();
        System.out.println("✅ Optimized MySQL test complete\n");
    }
    
    /**
     * Test with no persistence (in-memory only)
     */
    private static void testNoPersistence() throws Exception {
        System.out.println("📋 Test 3: No Persistence (In-Memory Only)");
        System.out.println("-".repeat(50));
        
        TimeoutManager tm = new TimeoutManager();
        StateMachineRegistry registry = new StateMachineRegistry("in-memory", tm, 9993);
        
        // Set to no persistence
        registry.setPersistenceType(PersistenceType.NONE);
        System.out.println("Persistence type: " + registry.getPersistenceType());
        
        // Disable rehydration since there's no persistence
        registry.disableRehydration();
        System.out.println("Rehydration disabled: " + registry.isRehydrationDisabled());
        
        // Create and register a machine
        GenericStateMachine machine = createTestMachine("mem1");
        registry.register("mem1", machine);
        
        // Send events
        registry.routeEvent("mem1", new IncomingCall("+1", "+2"));
        System.out.println("Machine mem1 state: " + machine.getCurrentState());
        
        // Remove from memory
        registry.removeMachine("mem1");
        
        // Try to send event - should fail with warning
        System.out.println("Attempting to send event to removed machine...");
        boolean result = registry.routeEvent("mem1", new Hangup());
        System.out.println("Result: " + (result ? "SUCCESS" : "FAILED (expected - no persistence)"));
        
        registry.shutdown();
        tm.shutdown();
        System.out.println("✅ No persistence test complete\n");
    }
    
    /**
     * Test with partitioned repository persistence
     * NOTE: This is a demonstration - actual implementation would need:
     * - Proper entity class that implements both StateMachineContextEntity and ShardingEntity
     * - Configured PartitionedRepository with database connection
     */
    private static void testPartitionedRepo() throws Exception {
        System.out.println("📋 Test 4: Partitioned Repository Persistence");
        System.out.println("-".repeat(50));
        
        TimeoutManager tm = new TimeoutManager();
        StateMachineRegistry registry = new StateMachineRegistry("partitioned", tm, 9994);
        
        // In a real implementation, you would:
        // 1. Create an entity class that implements both interfaces
        // 2. Configure a PartitionedRepository with proper database settings
        // 3. Call registry.usePartitionedPersistence(repository, entityClass)
        
        System.out.println("Partitioned repository configuration would include:");
        System.out.println("  - Entity class implementing StateMachineContextEntity & ShardingEntity");
        System.out.println("  - Partition strategy (DAILY, MONTHLY, HASH, etc.)");
        System.out.println("  - Table naming pattern");
        System.out.println("  - Automatic partition creation and management");
        
        /* Example (would need actual implementation):
        
        // Create entity class
        class PartitionedCallContext extends BaseStateMachineEntity implements ShardingEntity<String> {
            @Override
            public String getShardingKey() {
                return getId(); // or use date, customer ID, etc.
            }
        }
        
        // Configure repository
        DataSource dataSource = ...; // Your data source
        PartitionedRepository<PartitionedCallContext, String> repository = 
            new PartitionedRepository<>(
                dataSource,
                PartitionedCallContext.class,
                "call_states",     // base table name
                "MONTHLY"          // partition strategy
            );
        
        // Use with registry
        registry.usePartitionedPersistence(repository, PartitionedCallContext.class);
        */
        
        System.out.println("\nFor production use:");
        System.out.println("  - Handles millions of state machines");
        System.out.println("  - Automatic partition management");
        System.out.println("  - Better query performance with partition pruning");
        System.out.println("  - Easy archival of old data");
        
        registry.shutdown();
        tm.shutdown();
        System.out.println("✅ Partitioned repository test complete\n");
    }
    
    /**
     * Helper method to create a test state machine
     */
    private static GenericStateMachine createTestMachine(String id) {
        return EnhancedFluentBuilder.create(id)
            .initialState("IDLE")
            .state("IDLE")
                .on(IncomingCall.class).to("ACTIVE")
            .done()
            .state("ACTIVE")
                .on(Hangup.class).to("IDLE")
            .done()
            .build();
    }
}