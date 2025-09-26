package com.telcobright.statemachine.stresstest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.io.*;

/**
 * Multi-Entity State Distribution Test
 * Focus on state distribution patterns with complex entity graphs
 * Simulates realistic telecom scenarios with varying call completion rates
 * Tests entity consistency and rehydration under load
 */
public class MultiEntityStateDistributionTest {
    private static final int TARGET_TPS = 500; // Lower TPS for more detailed analysis
    private static final int TEST_DURATION = 15;
    private static final int SETTLE_TIME = 10;

    // Scenario probabilities (realistic telecom patterns)
    private static final double PROB_NO_ANSWER = 0.15;     // 15% calls not answered
    private static final double PROB_BUSY = 0.10;          // 10% busy signals
    private static final double PROB_FAILED = 0.05;        // 5% call failures
    private static final double PROB_SHORT_CALL = 0.30;    // 30% short calls (<30s)
    private static final double PROB_HOLD = 0.20;          // 20% calls put on hold
    private static final double PROB_TRANSFER = 0.10;      // 10% calls transferred

    // Track all machine contexts
    private static final Map<String, CallContext> allContexts = new ConcurrentHashMap<>();

    // Statistics
    private static final AtomicLong totalCalls = new AtomicLong();
    private static final AtomicLong successfulCalls = new AtomicLong();
    private static final AtomicLong failedCalls = new AtomicLong();
    private static final AtomicLong noAnswerCalls = new AtomicLong();
    private static final AtomicLong transferredCalls = new AtomicLong();

    // Entity statistics
    private static final AtomicLong totalShardingEntities = new AtomicLong();
    private static final AtomicLong totalTransientEntities = new AtomicLong();
    private static final AtomicLong rehydrationAttempts = new AtomicLong();
    private static final AtomicLong rehydrationSuccess = new AtomicLong();

    // Real-time state tracking
    private static final Map<String, AtomicLong> liveStates = new ConcurrentHashMap<>();
    static {
        liveStates.put("IDLE", new AtomicLong());
        liveStates.put("DIALING", new AtomicLong());
        liveStates.put("RINGING", new AtomicLong());
        liveStates.put("CONNECTED", new AtomicLong());
        liveStates.put("ON_HOLD", new AtomicLong());
        liveStates.put("TRANSFERRING", new AtomicLong());
        liveStates.put("FAILED", new AtomicLong());
        liveStates.put("BUSY", new AtomicLong());
        liveStates.put("NO_ANSWER", new AtomicLong());
        liveStates.put("COMPLETED", new AtomicLong());
    }

    // Call states enum
    enum CallState {
        IDLE, DIALING, RINGING, CONNECTED, ON_HOLD,
        TRANSFERRING, FAILED, BUSY, NO_ANSWER, COMPLETED
    }

    // Simple ShardingEntity interface
    interface ShardingEntity {
        String getId();
        void setId(String id);
        LocalDateTime getCreatedAt();
        void setCreatedAt(LocalDateTime createdAt);
    }

    // Complex call context with nested entities
    static class CallContext implements ShardingEntity {
        private String id;
        private LocalDateTime createdAt;
        private CallState state;
        private String callerNumber;
        private String calledNumber;

        // Nested ShardingEntity types
        private CallDetails callDetails;
        private BillingInfo billingInfo;
        private QualityMetrics qualityMetrics;
        private List<EventLog> eventLogs = new ArrayList<>();

        // Transient entities (not persisted)
        private transient RealTimeMetrics rtMetrics;
        private transient NetworkStatus networkStatus;
        private transient boolean isRehydrated = false;

        public CallContext(String machineId, String caller, String called) {
            this.id = machineId;
            this.createdAt = LocalDateTime.now();
            this.state = CallState.IDLE;
            this.callerNumber = caller;
            this.calledNumber = called;
            initializeEntities();
        }

        private void initializeEntities() {
            this.callDetails = new CallDetails(id);
            this.billingInfo = new BillingInfo(id);
            this.qualityMetrics = new QualityMetrics(id);
            this.rtMetrics = new RealTimeMetrics();
            this.networkStatus = new NetworkStatus();

            totalShardingEntities.addAndGet(4); // Context + 3 nested
            totalTransientEntities.addAndGet(2);
        }

        public void addEvent(String type, String description) {
            EventLog event = new EventLog(id, type, description);
            eventLogs.add(event);
            totalShardingEntities.incrementAndGet();
        }

        public void simulateRehydration() {
            // Simulate clearing and recreating transient data
            this.rtMetrics = null;
            this.networkStatus = null;

            // Recreate transient data
            this.rtMetrics = new RealTimeMetrics();
            this.networkStatus = new NetworkStatus();
            this.isRehydrated = true;

            rehydrationAttempts.incrementAndGet();
            if (validateConsistency()) {
                rehydrationSuccess.incrementAndGet();
            }
        }

        public boolean validateConsistency() {
            // Check ID consistency across all entities
            return id.equals(callDetails.getId()) &&
                   id.equals(billingInfo.getId()) &&
                   id.equals(qualityMetrics.getId()) &&
                   eventLogs.stream().allMatch(e -> id.equals(e.getCallId()));
        }

        public void transition(CallState newState) {
            if (state != null) {
                liveStates.get(state.name()).decrementAndGet();
            }
            state = newState;
            liveStates.get(newState.name()).incrementAndGet();
            addEvent("STATE_CHANGE", state + " -> " + newState);
        }

        @Override
        public String getId() { return id; }
        @Override
        public void setId(String id) { this.id = id; }
        @Override
        public LocalDateTime getCreatedAt() { return createdAt; }
        @Override
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public CallState getState() { return state; }
        public CallDetails getCallDetails() { return callDetails; }
        public BillingInfo getBillingInfo() { return billingInfo; }
        public QualityMetrics getQualityMetrics() { return qualityMetrics; }
    }

    static class CallDetails implements ShardingEntity {
        private String id;
        private LocalDateTime createdAt;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private int duration = 0;
        private String callType = "VOICE";
        private String codec = "G.711";

        public CallDetails(String machineId) {
            this.id = machineId;
            this.createdAt = LocalDateTime.now();
        }

        public void startCall() {
            this.startTime = LocalDateTime.now();
        }

        public void endCall() {
            this.endTime = LocalDateTime.now();
            if (startTime != null) {
                this.duration = (int) java.time.Duration.between(startTime, endTime).getSeconds();
            }
        }

        @Override
        public String getId() { return id; }
        @Override
        public void setId(String id) { this.id = id; }
        @Override
        public LocalDateTime getCreatedAt() { return createdAt; }
        @Override
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public int getDuration() { return duration; }
    }

    static class BillingInfo implements ShardingEntity {
        private String id;
        private LocalDateTime createdAt;
        private BigDecimal connectionCharge = BigDecimal.ZERO;
        private BigDecimal usageCharge = BigDecimal.ZERO;
        private BigDecimal totalCharge = BigDecimal.ZERO;
        private String billingType = "POSTPAID";

        public BillingInfo(String machineId) {
            this.id = machineId;
            this.createdAt = LocalDateTime.now();
        }

        public void addConnectionCharge(BigDecimal amount) {
            this.connectionCharge = connectionCharge.add(amount);
            this.totalCharge = totalCharge.add(amount);
        }

        public void addUsageCharge(BigDecimal amount) {
            this.usageCharge = usageCharge.add(amount);
            this.totalCharge = totalCharge.add(amount);
        }

        @Override
        public String getId() { return id; }
        @Override
        public void setId(String id) { this.id = id; }
        @Override
        public LocalDateTime getCreatedAt() { return createdAt; }
        @Override
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public BigDecimal getTotalCharge() { return totalCharge; }
    }

    static class QualityMetrics implements ShardingEntity {
        private String id;
        private LocalDateTime createdAt;
        private double mos = 4.5; // Mean Opinion Score
        private double jitter = 0.0;
        private double packetLoss = 0.0;
        private double latency = 20.0;

        public QualityMetrics(String machineId) {
            this.id = machineId;
            this.createdAt = LocalDateTime.now();
        }

        public void degradeQuality() {
            mos = Math.max(1.0, mos - Math.random() * 0.5);
            jitter = Math.min(50.0, jitter + Math.random() * 10);
            packetLoss = Math.min(5.0, packetLoss + Math.random() * 0.5);
            latency = Math.min(200.0, latency + Math.random() * 30);
        }

        @Override
        public String getId() { return id; }
        @Override
        public void setId(String id) { this.id = id; }
        @Override
        public LocalDateTime getCreatedAt() { return createdAt; }
        @Override
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public double getMos() { return mos; }
    }

    static class EventLog implements ShardingEntity {
        private String id;
        private String callId;
        private LocalDateTime createdAt;
        private String eventType;
        private String description;

        public EventLog(String callId, String type, String desc) {
            this.id = UUID.randomUUID().toString();
            this.callId = callId;
            this.eventType = type;
            this.description = desc;
            this.createdAt = LocalDateTime.now();
        }

        @Override
        public String getId() { return id; }
        @Override
        public void setId(String id) { this.id = id; }
        @Override
        public LocalDateTime getCreatedAt() { return createdAt; }
        @Override
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public String getCallId() { return callId; }
    }

    // Transient entities (not persisted)
    static class RealTimeMetrics {
        private long packetsTransmitted = 0;
        private long packetsReceived = 0;
        private LocalDateTime lastUpdate;

        public RealTimeMetrics() {
            this.lastUpdate = LocalDateTime.now();
        }

        public void update() {
            packetsTransmitted += Math.random() * 1000;
            packetsReceived += Math.random() * 1000;
            lastUpdate = LocalDateTime.now();
        }
    }

    static class NetworkStatus {
        private String signalStrength = "GOOD";
        private String networkType = "5G";
        private boolean isRoaming = false;

        public NetworkStatus() {
            double rand = Math.random();
            if (rand < 0.7) signalStrength = "EXCELLENT";
            else if (rand < 0.9) signalStrength = "GOOD";
            else signalStrength = "FAIR";
        }
    }

    // Simulate realistic call scenario
    private static void simulateCall(String machineId, ScheduledExecutorService executor) {
        String caller = "+1-555-" + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        String called = "+1-555-" + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));

        CallContext context = new CallContext(machineId, caller, called);
        allContexts.put(machineId, context);
        totalCalls.incrementAndGet();

        context.transition(CallState.DIALING);
        context.addEvent("DIAL", "Calling " + called);

        // Random scenario selection
        double scenario = Math.random();

        if (scenario < PROB_BUSY) {
            // Busy signal
            context.transition(CallState.BUSY);
            context.addEvent("BUSY", "Line busy");
            failedCalls.incrementAndGet();
            return;
        }

        context.transition(CallState.RINGING);
        context.addEvent("RING", "Phone ringing");

        if (scenario < PROB_BUSY + PROB_NO_ANSWER) {
            // No answer
            executor.schedule(() -> {
                context.transition(CallState.NO_ANSWER);
                context.addEvent("NO_ANSWER", "Call not answered");
                noAnswerCalls.incrementAndGet();
            }, 5, TimeUnit.SECONDS);
            return;
        }

        if (scenario < PROB_BUSY + PROB_NO_ANSWER + PROB_FAILED) {
            // Call failed
            context.transition(CallState.FAILED);
            context.addEvent("FAILED", "Network error");
            context.getQualityMetrics().degradeQuality();
            failedCalls.incrementAndGet();
            return;
        }

        // Call answered
        context.transition(CallState.CONNECTED);
        context.addEvent("ANSWER", "Call connected");
        context.getCallDetails().startCall();
        context.getBillingInfo().addConnectionCharge(new BigDecimal("0.50"));
        successfulCalls.incrementAndGet();

        // Simulate hold scenario
        if (Math.random() < PROB_HOLD) {
            executor.schedule(() -> {
                context.transition(CallState.ON_HOLD);
                context.addEvent("HOLD", "Call on hold");

                // Resume after delay
                executor.schedule(() -> {
                    context.transition(CallState.CONNECTED);
                    context.addEvent("RESUME", "Call resumed");
                }, 2, TimeUnit.SECONDS);
            }, 3, TimeUnit.SECONDS);
        }

        // Simulate transfer scenario
        if (Math.random() < PROB_TRANSFER) {
            executor.schedule(() -> {
                context.transition(CallState.TRANSFERRING);
                context.addEvent("TRANSFER", "Transferring call");
                transferredCalls.incrementAndGet();

                // Complete transfer
                executor.schedule(() -> {
                    context.transition(CallState.CONNECTED);
                    context.addEvent("TRANSFER_COMPLETE", "Transfer successful");
                }, 1, TimeUnit.SECONDS);
            }, 4, TimeUnit.SECONDS);
        }

        // Determine call duration
        int callDuration = scenario < PROB_SHORT_CALL ?
            ThreadLocalRandom.current().nextInt(5, 30) :
            ThreadLocalRandom.current().nextInt(30, 300);

        // Schedule call end
        executor.schedule(() -> {
            context.getCallDetails().endCall();
            BigDecimal usage = new BigDecimal(callDuration * 0.01); // 1 cent per second
            context.getBillingInfo().addUsageCharge(usage);
            context.transition(CallState.COMPLETED);
            context.addEvent("HANGUP", "Call ended after " + callDuration + " seconds");

            // Randomly test rehydration on some completed calls
            if (Math.random() < 0.1) {
                context.simulateRehydration();
            }
        }, callDuration, TimeUnit.SECONDS);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   MULTI-ENTITY STATE DISTRIBUTION TEST");
        System.out.println("=".repeat(80));
        System.out.println("Configuration:");
        System.out.println("  Target: " + TARGET_TPS + " calls/sec");
        System.out.println("  Test Duration: " + TEST_DURATION + " seconds");
        System.out.println("  Settle Time: " + SETTLE_TIME + " seconds");
        System.out.println("  Realistic telecom scenarios with varying outcomes");
        System.out.println("=".repeat(80) + "\n");

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(32);
        AtomicBoolean testRunning = new AtomicBoolean(true);
        long startTime = System.currentTimeMillis();

        // Real-time progress reporter
        executor.scheduleAtFixedRate(() -> {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;

            if (elapsed <= TEST_DURATION) {
                System.out.printf("[TEST] T=%02d/%ds | Calls: %,d | Success: %,d | Failed: %,d | ",
                    elapsed, TEST_DURATION, totalCalls.get(),
                    successfulCalls.get(), failedCalls.get());

                // Show top 3 states
                liveStates.entrySet().stream()
                    .filter(e -> e.getValue().get() > 0)
                    .sorted((e1, e2) -> Long.compare(e2.getValue().get(), e1.getValue().get()))
                    .limit(3)
                    .forEach(e -> System.out.printf("%s:%d ",
                        e.getKey().substring(0, Math.min(4, e.getKey().length())),
                        e.getValue().get()));
                System.out.println();
            } else {
                long settleElapsed = elapsed - TEST_DURATION;
                System.out.printf("[SETTLE] T=%d/%ds | Active calls settling... | ",
                    settleElapsed, SETTLE_TIME);

                // Count non-completed calls
                long activeCount = liveStates.entrySet().stream()
                    .filter(e -> !e.getKey().equals("COMPLETED") &&
                                !e.getKey().equals("FAILED") &&
                                !e.getKey().equals("NO_ANSWER") &&
                                !e.getKey().equals("BUSY"))
                    .mapToLong(e -> e.getValue().get())
                    .sum();
                System.out.println("Active: " + activeCount);
            }
        }, 1, 1, TimeUnit.SECONDS);

        // Call generator
        executor.scheduleAtFixedRate(() -> {
            if (!testRunning.get()) return;

            for (int i = 0; i < TARGET_TPS / 10; i++) {
                String machineId = "call-" + System.nanoTime();
                executor.submit(() -> simulateCall(machineId, executor));
            }
        }, 0, 100, TimeUnit.MILLISECONDS);

        // Run test
        Thread.sleep(TEST_DURATION * 1000);
        testRunning.set(false);

        System.out.println("\n[TEST COMPLETE] Waiting for calls to complete...\n");
        Thread.sleep(SETTLE_TIME * 1000);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Generate final report
        printFinalReport();
    }

    private static void printFinalReport() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   FINAL STATE DISTRIBUTION REPORT");
        System.out.println("=".repeat(80));
        System.out.println();

        // Call outcome statistics
        System.out.println("📞 CALL OUTCOMES:");
        System.out.println("  Total Calls:         " + String.format("%,d", totalCalls.get()));
        System.out.println("  Successful:          " + String.format("%,d (%.1f%%)",
            successfulCalls.get(),
            100.0 * successfulCalls.get() / totalCalls.get()));
        System.out.println("  No Answer:           " + String.format("%,d (%.1f%%)",
            noAnswerCalls.get(),
            100.0 * noAnswerCalls.get() / totalCalls.get()));
        System.out.println("  Failed/Busy:         " + String.format("%,d (%.1f%%)",
            failedCalls.get(),
            100.0 * failedCalls.get() / totalCalls.get()));
        System.out.println("  Transferred:         " + String.format("%,d", transferredCalls.get()));
        System.out.println();

        // Entity statistics
        System.out.println("🗂️  ENTITY STATISTICS:");
        System.out.println("  ShardingEntities:    " + String.format("%,d", totalShardingEntities.get()));
        System.out.println("  Transient Entities:  " + String.format("%,d", totalTransientEntities.get()));
        System.out.println("  Rehydrations:        " + String.format("%,d / %,d successful",
            rehydrationSuccess.get(), rehydrationAttempts.get()));
        System.out.println();

        // Final state distribution
        System.out.println("📊 FINAL STATE DISTRIBUTION:");
        Map<CallState, Integer> finalStates = new HashMap<>();
        for (CallContext ctx : allContexts.values()) {
            finalStates.put(ctx.getState(), finalStates.getOrDefault(ctx.getState(), 0) + 1);
        }

        finalStates.entrySet().stream()
            .sorted(Map.Entry.<CallState, Integer>comparingByValue().reversed())
            .forEach(e -> {
                double percentage = 100.0 * e.getValue() / allContexts.size();
                System.out.println(String.format("  %-15s: %,6d (%.1f%%)",
                    e.getKey(), e.getValue(), percentage));
            });
        System.out.println();

        // Quality metrics summary
        System.out.println("📈 QUALITY METRICS:");
        double avgMos = allContexts.values().stream()
            .mapToDouble(c -> c.getQualityMetrics().getMos())
            .average().orElse(0.0);
        System.out.println("  Average MOS:         " + String.format("%.2f", avgMos));

        // Billing summary
        BigDecimal totalRevenue = allContexts.values().stream()
            .map(c -> c.getBillingInfo().getTotalCharge())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        System.out.println("  Total Revenue:       $" + String.format("%.2f", totalRevenue));

        // Call duration statistics
        int totalDuration = allContexts.values().stream()
            .mapToInt(c -> c.getCallDetails().getDuration())
            .sum();
        double avgDuration = allContexts.values().stream()
            .filter(c -> c.getCallDetails().getDuration() > 0)
            .mapToInt(c -> c.getCallDetails().getDuration())
            .average().orElse(0.0);
        System.out.println("  Total Call Time:     " + String.format("%,d seconds", totalDuration));
        System.out.println("  Avg Call Duration:   " + String.format("%.1f seconds", avgDuration));

        // Consistency validation
        long consistentContexts = allContexts.values().stream()
            .filter(CallContext::validateConsistency)
            .count();
        System.out.println("\n✅ CONSISTENCY CHECK:");
        System.out.println("  ID Consistent:       " + String.format("%,d / %,d (%.1f%%)",
            consistentContexts, allContexts.size(),
            100.0 * consistentContexts / allContexts.size()));

        System.out.println("\n" + "=".repeat(80));
        System.out.println("   Test completed at " +
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        System.out.println("=".repeat(80));
    }
}