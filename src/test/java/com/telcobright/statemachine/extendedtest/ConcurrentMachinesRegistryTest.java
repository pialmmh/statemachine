package com.telcobright.statemachine.extendedtest;

import com.telcobright.statemachine.StateMachineRegistry;
import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.timeout.TimeoutManager;
import com.telcobright.statemachine.SampleLoggingConfig;
import com.telcobright.statemachine.EnhancedFluentBuilder;
import com.telcobright.statemachine.websocket.CallMachineRunnerEnhanced;
import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachineexamples.callmachine.events.IncomingCall;
import com.telcobright.statemachineexamples.callmachine.events.Answer;
import com.telcobright.statemachineexamples.callmachine.events.Hangup;
import com.telcobright.statemachine.persistence.MysqlConnectionProvider;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class ConcurrentMachinesRegistryTest {
    
    private final ConcurrentTestConfig config;
    private final StateMachineRegistry registry;
    private final TimeoutManager timeoutManager;
    private final ExecutorService machineCreationExecutor;
    private final ScheduledExecutorService eventScheduler;
    private final DatabaseValidator dbValidator;
    private final TestReporter reporter;
    private final Random random = new Random();
    
    // Test metrics
    private final AtomicInteger totalMachinesCreated = new AtomicInteger(0);
    private final AtomicLong totalEventsSent = new AtomicLong(0);
    private final AtomicLong totalEventsProcessed = new AtomicLong(0);
    private final AtomicInteger failedMachines = new AtomicInteger(0);
    private final AtomicLong startTime = new AtomicLong(0);
    private final ConcurrentHashMap<String, AtomicInteger> machineEventCounts = new ConcurrentHashMap<>();
    
    public ConcurrentMachinesRegistryTest(ConcurrentTestConfig config) {
        this.config = config;
        this.timeoutManager = new TimeoutManager();
        this.registry = new StateMachineRegistry(config.getRegistryId(), timeoutManager, config.getWebSocketPort());
        this.machineCreationExecutor = Executors.newFixedThreadPool(config.getThreadPoolSize());
        this.eventScheduler = Executors.newScheduledThreadPool(config.getEventSchedulerThreads());
        this.dbValidator = new DatabaseValidator();
        this.reporter = new TestReporter(config);
        
        // Configure registry for testing
        if (config.isEnableMetrics()) {
            registry.enableDebugMode(config.getWebSocketPort());
        }
        registry.setRegistrySampleLogging(SampleLoggingConfig.oneIn2());
    }
    
    public void runConcurrentTest() throws Exception {
        System.out.println("=== Starting Concurrent Machines Registry Test ===");
        System.out.println(config.toString());
        
        if (config.isValidateDatabase()) {
            dbValidator.clearTestTables();
        }
        
        startTime.set(System.currentTimeMillis());
        
        // Phase 1: Create machines concurrently
        System.out.println("\n🏗️ Phase 1: Creating " + config.getMachineCount() + " machines concurrently...");
        createMachinesConcurrently();
        
        // Phase 2: Warmup period
        System.out.println("\n🔥 Phase 2: Warmup period (" + ConcurrentTestConfig.WARMUP_DURATION_SECONDS + "s)...");
        Thread.sleep(ConcurrentTestConfig.WARMUP_DURATION_SECONDS * 1000);
        
        // Phase 3: Send events concurrently
        System.out.println("\n🚀 Phase 3: Sending events concurrently for " + ConcurrentTestConfig.TEST_DURATION_MINUTES + " minutes...");
        sendEventsConcurrently();
        
        // Phase 4: Cooldown and validation
        System.out.println("\n❄️ Phase 4: Cooldown period (" + ConcurrentTestConfig.COOLDOWN_DURATION_SECONDS + "s)...");
        Thread.sleep(ConcurrentTestConfig.COOLDOWN_DURATION_SECONDS * 1000);
        
        // Phase 5: Validation
        if (config.isValidateDatabase()) {
            System.out.println("\n✅ Phase 5: Database validation...");
            validateResults();
        }
        
        // Phase 6: Generate report
        System.out.println("\n📊 Phase 6: Generating test report...");
        generateFinalReport();
        
        cleanup();
    }
    
    private void createMachinesConcurrently() {
        List<CompletableFuture<Void>> creationTasks = new ArrayList<>();
        
        for (int i = 1; i <= config.getMachineCount(); i++) {
            final String machineId = "concurrent-test-" + String.format("%06d", i);
            
            CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
                try {
                    createTestMachine(machineId);
                    totalMachinesCreated.incrementAndGet();
                    
                    if (totalMachinesCreated.get() % 100 == 0) {
                        System.out.println("Created " + totalMachinesCreated.get() + " machines...");
                    }
                } catch (Exception e) {
                    failedMachines.incrementAndGet();
                    if (config.isDebugMode()) {
                        System.err.println("Failed to create machine " + machineId + ": " + e.getMessage());
                    }
                }
            }, machineCreationExecutor);
            
            creationTasks.add(task);
        }
        
        try {
            CompletableFuture.allOf(creationTasks.toArray(new CompletableFuture[0]))
                    .get(5, TimeUnit.MINUTES);
        } catch (Exception e) {
            System.err.println("Machine creation timeout or error: " + e.getMessage());
        }
        
        System.out.println("✅ Created " + totalMachinesCreated.get() + " machines successfully");
        System.out.println("❌ Failed to create " + failedMachines.get() + " machines");
    }
    
    private void createTestMachine(String machineId) throws Exception {
        // Create context objects
        CallMachineRunnerEnhanced.CallPersistentContext persistentContext = 
            new CallMachineRunnerEnhanced.CallPersistentContext(
                machineId, 
                "+1-555-" + String.format("%04d", random.nextInt(10000)), 
                "+1-555-DEST"
            );
        CallMachineRunnerEnhanced.CallVolatileContext volatileContext = 
            new CallMachineRunnerEnhanced.CallVolatileContext();
        
        // Build the state machine with sample logging
        GenericStateMachine<CallMachineRunnerEnhanced.CallPersistentContext, CallMachineRunnerEnhanced.CallVolatileContext> machine = 
            EnhancedFluentBuilder.<CallMachineRunnerEnhanced.CallPersistentContext, CallMachineRunnerEnhanced.CallVolatileContext>create(machineId)
                .withPersistentContext(persistentContext)
                .withVolatileContext(volatileContext)
                .withSampleLogging(SampleLoggingConfig.oneIn2())
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
        
        // Register and start the machine
        registry.register(machineId, machine);
        machine.start();
        
        // Initialize event counter
        machineEventCounts.put(machineId, new AtomicInteger(0));
    }
    
    private void sendEventsConcurrently() {
        List<ScheduledFuture<?>> scheduledTasks = new ArrayList<>();
        long testDurationMs = ConcurrentTestConfig.TEST_DURATION_MINUTES * 60 * 1000;
        long testEndTime = System.currentTimeMillis() + testDurationMs;
        
        // Schedule event generators for each machine
        for (String machineId : machineEventCounts.keySet()) {
            ScheduledFuture<?> task = eventScheduler.scheduleWithFixedDelay(() -> {
                if (System.currentTimeMillis() < testEndTime) {
                    sendRandomEvents(machineId);
                }
            }, 
            random.nextInt((int)ConcurrentTestConfig.MAX_EVENT_DELAY_MS), 
            ConcurrentTestConfig.MIN_EVENT_DELAY_MS + random.nextInt((int)(ConcurrentTestConfig.MAX_EVENT_DELAY_MS - ConcurrentTestConfig.MIN_EVENT_DELAY_MS)), 
            TimeUnit.MILLISECONDS);
            
            scheduledTasks.add(task);
        }
        
        // Wait for test duration
        try {
            Thread.sleep(testDurationMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Cancel all scheduled tasks
        scheduledTasks.forEach(task -> task.cancel(false));
        
        System.out.println("✅ Event generation phase completed");
        System.out.println("📊 Total events sent: " + totalEventsSent.get());
    }
    
    private void sendRandomEvents(String machineId) {
        try {
            int eventsInCycle = ConcurrentTestConfig.MIN_EVENTS_PER_MACHINE + 
                random.nextInt(ConcurrentTestConfig.MAX_EVENTS_PER_MACHINE - ConcurrentTestConfig.MIN_EVENTS_PER_MACHINE);
            
            for (int i = 0; i < eventsInCycle; i++) {
                StateMachineEvent event;
                int eventType = random.nextInt(3);
                switch (eventType) {
                    case 0:
                        event = new IncomingCall();
                        break;
                    case 1:
                        event = new Answer();
                        break;
                    case 2:
                        event = new Hangup();
                        break;
                    default:
                        event = new IncomingCall();
                }
                
                registry.sendEvent(machineId, event);
                totalEventsSent.incrementAndGet();
                machineEventCounts.get(machineId).incrementAndGet();
                
                // Small delay between events in the same cycle
                if (i < eventsInCycle - 1) {
                    Thread.sleep(10 + random.nextInt(50));
                }
            }
        } catch (Exception e) {
            if (config.isDebugMode()) {
                System.err.println("Error sending events to " + machineId + ": " + e.getMessage());
            }
        }
    }
    
    private void validateResults() throws Exception {
        System.out.println("🔍 Validating database results...");
        
        // Wait for async logging to complete
        Thread.sleep(ConcurrentTestConfig.DB_VALIDATION_TIMEOUT_SECONDS * 1000);
        
        int registryEvents = dbValidator.countRegistryEvents();
        int historyEvents = dbValidator.countHistoryEvents();
        
        System.out.println("📊 Database Validation Results:");
        System.out.println("   Registry events logged: " + registryEvents);
        System.out.println("   History events logged: " + historyEvents);
        System.out.println("   Total events sent: " + totalEventsSent.get());
        
        double registryLogRate = (registryEvents * 100.0) / totalEventsSent.get();
        double historyLogRate = (historyEvents * 100.0) / totalEventsSent.get();
        
        System.out.println("   Registry log rate: " + String.format("%.2f%%", registryLogRate));
        System.out.println("   History log rate: " + String.format("%.2f%%", historyLogRate));
        
        if (config.isEnableMetrics()) {
            System.out.println("✅ Debug mode enabled - expecting 100% logging rate");
            if (registryLogRate > 80 && historyLogRate > 80) {
                System.out.println("✅ Database validation PASSED");
            } else {
                System.out.println("❌ Database validation FAILED - not enough events logged");
            }
        } else {
            System.out.println("📊 Sampling mode - expecting ~50% logging rate");
            if (Math.abs(registryLogRate - 50) < 10 && Math.abs(historyLogRate - 50) < 10) {
                System.out.println("✅ Database validation PASSED");
            } else {
                System.out.println("❌ Database validation FAILED - sampling rate not as expected");
            }
        }
    }
    
    private void generateFinalReport() {
        long totalTestTime = System.currentTimeMillis() - startTime.get();
        reporter.generateReport(
            totalMachinesCreated.get(),
            totalEventsSent.get(),
            failedMachines.get(),
            totalTestTime,
            machineEventCounts
        );
    }
    
    private void cleanup() {
        System.out.println("🧹 Cleaning up resources...");
        
        machineCreationExecutor.shutdown();
        eventScheduler.shutdown();
        
        try {
            if (!machineCreationExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                machineCreationExecutor.shutdownNow();
            }
            if (!eventScheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                eventScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            machineCreationExecutor.shutdownNow();
            eventScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        registry.shutdownAsyncLogging();
        timeoutManager.shutdown();
        
        System.out.println("✅ Cleanup completed");
    }
    
    public static void main(String[] args) throws Exception {
        // Create test configuration
        ConcurrentTestConfig config = new ConcurrentTestConfig.Builder()
            .machineCount(1000)
            .threadPoolSize(50)
            .eventSchedulerThreads(20)
            .debugMode(true)
            .validateDatabase(true)
            .enableMetrics(true)
            .registryId("concurrent_test_registry")
            .webSocketPort(19999)
            .build();
        
        // Run the test
        ConcurrentMachinesRegistryTest test = new ConcurrentMachinesRegistryTest(config);
        test.runConcurrentTest();
        
        System.out.println("\n🎉 Concurrent test completed successfully!");
        System.exit(0);
    }
}