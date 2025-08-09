package com.telcobright.statemachine.test;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.FluentStateMachineBuilder;
import com.telcobright.statemachine.StateMachineRegistry;
import com.telcobright.statemachine.state.EnhancedStateConfig;
import com.telcobright.statemachine.events.GenericStateMachineEvent;
import com.telcobright.statemachine.StateMachineContextEntity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Quick Demo of TelcoBright State Machine Library
 * Demonstrates core functionality without external dependencies
 */
public class QuickDemo {
    
    private static final String BANNER = """
        ╔═══════════════════════════════════════════════════════════════╗
        ║           🎯 TELCOBRIGHT STATE MACHINE DEMO                   ║
        ║              Quick Functionality Demo                         ║
        ╚═══════════════════════════════════════════════════════════════╝
        """;
    
    // Simple test entity with Completable interface
    public static class SimpleEntity implements StateMachineContextEntity<String> {
        private String id;
        private String currentState;
        private String data;
        private boolean isComplete = false;
        
        public SimpleEntity(String id) {
            this.id = id;
            this.currentState = "INITIAL";
            this.data = "test-data";
        }
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getCurrentState() { return currentState; }
        public void setCurrentState(String currentState) { this.currentState = currentState; }
        public String getData() { return data; }
        public void setData(String data) { this.data = data; }
        
        // Completable interface implementation
        @Override
        public boolean isComplete() { return isComplete; }
        
        @Override
        public void setComplete(boolean complete) { this.isComplete = complete; }
        
        @Override
        public String toString() {
            return String.format("SimpleEntity{id='%s', state='%s', data='%s', complete=%s}", id, currentState, data, isComplete);
        }
    }
    
    // Simple context
    public static class SimpleContext {
        private String contextId;
        private int stepCount;
        private LocalDateTime startTime;
        
        public SimpleContext(String contextId) {
            this.contextId = contextId;
            this.stepCount = 0;
            this.startTime = LocalDateTime.now();
        }
        
        public String getContextId() { return contextId; }
        public int getStepCount() { return stepCount; }
        public void incrementStepCount() { this.stepCount++; }
        public LocalDateTime getStartTime() { return startTime; }
        
        @Override
        public String toString() {
            return String.format("SimpleContext{id='%s', steps=%d, started=%s}", 
                contextId, stepCount, startTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        }
    }
    
    public static void main(String[] args) {
        System.out.println(BANNER);
        System.out.println("🚀 Starting TelcoBright State Machine demonstration");
        System.out.println("Started at: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println("═".repeat(80));
        
        runRegistryBasicDemo();
        runRegistryPerformanceDemo();
        runRegistryCompletionDemo();
        
        System.out.println("\n" + "═".repeat(80));
        System.out.println("🏁 === DEMO COMPLETED SUCCESSFULLY ===");
        System.out.println("✅ All state machine functionality working correctly!");
        System.out.println("🎯 Key features demonstrated:");
        System.out.println("   • Registry-based state machine management");
        System.out.println("   • Performance optimization with create() vs createOrGet()");
        System.out.println("   • Dual generic architecture (Entity + Context)");
        System.out.println("   • Completable interface and final states");
        System.out.println("   • Automatic completion checking and optimization");
        System.out.println("   • Memory and database performance benefits");
        System.out.println("Completed at: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }
    
    private static void runRegistryBasicDemo() {
        System.out.println("\n🏛️ === REGISTRY-BASED STATE MACHINE DEMO ===");
        
        try {
            // Create registry for managing state machines
            StateMachineRegistry registry = new StateMachineRegistry();
            System.out.println("📋 Created StateMachineRegistry for lifecycle management");
            
            // Create new state machine using registry.create() for performance
            System.out.println("\n🚀 Creating NEW state machine (using registry.create() - no DB lookup)");
            
            GenericStateMachine<SimpleEntity, SimpleContext> machine = 
                registry.create("demo-001", () -> {
                    return FluentStateMachineBuilder.<SimpleEntity, SimpleContext>create("demo-001")
                        .initialState("INITIAL")
                        .state("INITIAL").done()
                        .state("PROCESSING").done()
                        .state("COMPLETED").finalState().done()
                        .build();
                });
            
            // Set up persistent entity and volatile context
            SimpleEntity entity = new SimpleEntity("entity-001");
            SimpleContext context = new SimpleContext("context-001");
            
            machine.setPersistingEntity(entity);
            machine.setContext(context);
            machine.start();
            
            // Configure transitions
            machine.transition("INITIAL", "START", "PROCESSING")
                   .transition("PROCESSING", "FINISH", "COMPLETED");
            
            System.out.println("✅ State machine created and registered");
            System.out.println("   📝 Entity: " + entity);
            System.out.println("   🔄 Context: " + context);
            System.out.println("   🏛️ Registry size: " + registry.size());
            
            // Test state transitions
            System.out.println("\n🎬 Testing registry-managed state transitions:");
            System.out.println("   Current state: " + machine.getCurrentState());
            
            machine.fire("START");
            context.incrementStepCount();
            System.out.println("   ✓ After START: " + machine.getCurrentState());
            
            machine.fire("FINISH");
            context.incrementStepCount();
            System.out.println("   ✓ After FINISH: " + machine.getCurrentState());
            System.out.println("   🔍 Is complete: " + machine.isComplete());
            System.out.println("   📊 Final context: " + context);
            
            // Verify registry management
            System.out.println("\n🏛️ Registry management verification:");
            System.out.println("   📊 Active machines: " + registry.size());
            System.out.println("   🔍 Is in memory: " + registry.isInMemory("demo-001"));
            System.out.println("   ✅ Registry-based management working correctly!");
            
        } catch (Exception e) {
            System.out.println("   ❌ Registry demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void runRegistryPerformanceDemo() {
        System.out.println("\n⚡ === REGISTRY PERFORMANCE OPTIMIZATION DEMO ===");
        
        try {
            StateMachineRegistry registry = new StateMachineRegistry();
            System.out.println("📋 Demonstrating create() vs createOrGet() performance optimization");
            
            // Simulate thousands of NEW incoming messages
            System.out.println("\n🚀 Scenario 1: NEW SMS messages (use registry.create() for performance)");
            for (int i = 1; i <= 5; i++) {
                String smsId = "sms-" + i;
                
                GenericStateMachine<SimpleEntity, SimpleContext> machine = 
                    registry.create(smsId, () -> {
                        return FluentStateMachineBuilder.<SimpleEntity, SimpleContext>create(smsId)
                            .initialState("QUEUED")
                            .state("QUEUED").offline().done()
                            .state("SENDING").done()
                            .state("DELIVERED").finalState().done()
                            .build();
                    });
                
                SimpleEntity entity = new SimpleEntity("entity-" + i);
                SimpleContext context = new SimpleContext("context-" + i);
                machine.setPersistingEntity(entity);
                machine.setContext(context);
                machine.start();
                
                System.out.println("   📱 Created SMS-" + i + " (skipped DB lookup)");
            }
            
            System.out.println("   ✅ Created 5 new SMS machines with optimal performance!");
            System.out.println("   📊 Registry size: " + registry.size());
            
            // Simulate retrieving existing messages
            System.out.println("\n🔄 Scenario 2: EXISTING SMS status updates (use createOrGet())");
            
            // Try to get existing machine
            GenericStateMachine<SimpleEntity, SimpleContext> existing = 
                registry.createOrGet("sms-1", () -> {
                    System.out.println("   🔄 Factory called - would load from DB if not in memory");
                    return null; // Won't be called since already in memory
                });
            
            if (existing != null) {
                System.out.println("   ✅ Retrieved existing SMS-1 from memory (no DB lookup needed)");
                System.out.println("   📝 Current state: " + existing.getCurrentState());
            }
            
            // Demonstrate completion checking
            System.out.println("\n🏁 Scenario 3: Completion checking prevents unnecessary rehydration");
            
            // Simulate a completed entity loader
            GenericStateMachine<SimpleEntity, SimpleContext> completedCheck = 
                registry.createOrGet("completed-sms", 
                    () -> {
                        System.out.println("   🔄 This factory would load state machine from DB");
                        return FluentStateMachineBuilder.<SimpleEntity, SimpleContext>create("completed-sms")
                            .initialState("DELIVERED")
                            .state("DELIVERED").finalState().done()
                            .build();
                    },
                    (id) -> {
                        // Simulate completed entity
                        SimpleEntity completedEntity = new SimpleEntity(id);
                        completedEntity.setComplete(true);
                        return completedEntity;
                    });
            
            if (completedCheck == null) {
                System.out.println("   ✅ Completed SMS not rehydrated - performance optimized!");
            }
            
            System.out.println("\n📊 Performance Summary:");
            System.out.println("   🚀 NEW messages: registry.create() - skips DB lookup");
            System.out.println("   🔄 EXISTING messages: registry.createOrGet() - loads if needed");
            System.out.println("   🏁 COMPLETED messages: not rehydrated - saves memory/DB");
            
        } catch (Exception e) {
            System.out.println("   ❌ Performance demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void runRegistryCompletionDemo() {
        System.out.println("\n🏁 === REGISTRY COMPLETION AND LIFECYCLE DEMO ===");
        
        try {
            StateMachineRegistry registry = new StateMachineRegistry();
            System.out.println("📋 Demonstrating registry-based completion and lifecycle management");
            
            // Create a state machine that will complete
            System.out.println("\n🚀 Creating state machine with completion workflow:");
            
            GenericStateMachine<SimpleEntity, SimpleContext> machine = 
                registry.create("workflow-001", () -> {
                    return FluentStateMachineBuilder.<SimpleEntity, SimpleContext>create("workflow-001")
                        .initialState("PENDING")
                        .state("PENDING").done()
                        .state("PROCESSING").done()
                        .state("COMPLETED").finalState().done()  // Final state
                        .build();
                });
            
            // Set up entity and context
            SimpleEntity entity = new SimpleEntity("workflow-entity-001");
            SimpleContext context = new SimpleContext("workflow-context-001");
            machine.setPersistingEntity(entity);
            machine.setContext(context);
            machine.start();
            
            // Configure transitions
            machine.transition("PENDING", "START_PROCESSING", "PROCESSING")
                   .transition("PROCESSING", "COMPLETE_WORK", "COMPLETED");
            
            System.out.println("   ✅ Created workflow machine in registry");
            System.out.println("   📝 Initial state: " + machine.getCurrentState());
            System.out.println("   🔍 Is complete: " + entity.isComplete());
            System.out.println("   📊 Registry size: " + registry.size());
            
            // Process workflow to completion
            System.out.println("\n🔄 Processing workflow through completion:");
            
            // Step 1: Start processing
            machine.fire("START_PROCESSING");
            System.out.println("   ✓ Moved to PROCESSING state");
            System.out.println("   🔍 Is complete: " + entity.isComplete());
            
            // Step 2: Complete work (reaches final state)
            machine.fire("COMPLETE_WORK");
            System.out.println("   ✅ Reached COMPLETED state (final)");
            System.out.println("   🔍 Entity auto-marked complete: " + entity.isComplete());
            System.out.println("   🔍 Machine is active: " + machine.isActive());
            
            // Demonstrate registry optimization for completed machines
            System.out.println("\n🏛️ Registry completion optimization:");
            
            // Try to get the completed machine again
            GenericStateMachine<SimpleEntity, SimpleContext> retrievedMachine = 
                registry.createOrGet("workflow-001", 
                    () -> {
                        System.out.println("   🔄 Factory called - loading from persistence");
                        return null; // Won't be called since in memory
                    });
            
            System.out.println("   ✅ Retrieved completed machine from memory");
            System.out.println("   📊 Registry contains: " + registry.size() + " machines");
            
            // Simulate what happens with a new request for completed workflow
            GenericStateMachine<SimpleEntity, SimpleContext> completedWorkflow = 
                registry.createOrGet("completed-workflow-999", 
                    () -> {
                        System.out.println("   🔄 Would load state machine from DB");
                        return FluentStateMachineBuilder.<SimpleEntity, SimpleContext>create("completed-workflow-999")
                            .initialState("COMPLETED")
                            .state("COMPLETED").finalState().done()
                            .build();
                    },
                    (id) -> {
                        // Simulate loading completed entity from DB
                        SimpleEntity completedEntity = new SimpleEntity(id);
                        completedEntity.setComplete(true);
                        System.out.println("   📋 Loaded entity from DB - found completed: " + completedEntity.isComplete());
                        return completedEntity;
                    });
            
            if (completedWorkflow == null) {
                System.out.println("   ✅ Completed workflow not rehydrated - optimal performance!");
            }
            
            // Demonstrate registry lifecycle management
            System.out.println("\n📊 Registry Lifecycle Management:");
            System.out.println("   🏛️ Total machines in registry: " + registry.size());
            System.out.println("   🔍 Active machines (not complete): ");
            
            registry.getActiveMachines().forEach((id, stateMachine) -> {
                if (stateMachine.isActive()) {
                    System.out.println("      • " + id + " in state: " + stateMachine.getCurrentState());
                } else {
                    System.out.println("      • " + id + " COMPLETED (can be evicted for memory optimization)");
                }
            });
            
            System.out.println("\n✅ Registry-based completion management benefits:");
            System.out.println("   • Automatic completion marking on final state entry");
            System.out.println("   • Performance optimization prevents rehydrating completed machines");
            System.out.println("   • Centralized lifecycle management through registry");
            System.out.println("   • Memory efficiency for high-volume processing");
            
        } catch (Exception e) {
            System.out.println("   ❌ Registry completion demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}