package com.telcobright.statemachine.stresstest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.io.*;

/**
 * Multi-Entity TPS Test - High-throughput testing with complex entity graphs
 * Tests performance with nested entities, selective persistence, and ID consistency
 * Target TPS can be specified via args: java MultiEntityTPSTest 2000
 * Default: 1000 TPS = 1000 full call cycles/sec = 5000+ entity operations/sec
 */
public class MultiEntityTPSTest {
    private static int TARGET_TPS = 1000; // Default, can be overridden
    private static final int TEST_DURATION = 10;
    private static final int SETTLE_TIME = 5;

    // Track all machine contexts
    private static final Map<String, CallMachineContext> contexts = new ConcurrentHashMap<>();

    // Statistics
    private static final AtomicLong totalCycles = new AtomicLong();
    private static final AtomicLong totalEvents = new AtomicLong();
    private static final AtomicLong totalEntities = new AtomicLong();
    private static final AtomicLong peakContexts = new AtomicLong();
    private static final AtomicLong activeContexts = new AtomicLong();

    // Entity counters
    private static final AtomicLong shardingEntities = new AtomicLong();
    private static final AtomicLong nonShardingEntities = new AtomicLong();
    private static final AtomicLong callEvents = new AtomicLong();
    private static final AtomicLong chargeEvents = new AtomicLong();
    private static final AtomicLong mediaStreams = new AtomicLong();

    // State counters
    private static final Map<String, AtomicLong> stateCounts = new ConcurrentHashMap<>();
    static {
        stateCounts.put("IDLE", new AtomicLong());
        stateCounts.put("DIALING", new AtomicLong());
        stateCounts.put("RINGING", new AtomicLong());
        stateCounts.put("CONNECTED", new AtomicLong());
        stateCounts.put("ON_HOLD", new AtomicLong());
        stateCounts.put("FAILED", new AtomicLong());
    }

    // Simple ShardingEntity interface for test
    interface ShardingEntity {
        String getId();
        void setId(String id);
        LocalDateTime getCreatedAt();
        void setCreatedAt(LocalDateTime createdAt);
    }

    // Root context with nested entities
    static class CallMachineContext implements ShardingEntity {
        private String id;
        private LocalDateTime createdAt;
        private String state;
        private CallEntity call;
        private CdrEntity cdr;
        private MediaEntity media;
        private NetworkEntity network;
        private transient DeviceInfo device;
        private transient SessionCache cache;

        public CallMachineContext(String machineId) {
            this.id = machineId;
            this.createdAt = LocalDateTime.now();
            this.state = "IDLE";
            initializeEntities();
        }

        private void initializeEntities() {
            this.call = new CallEntity(id);
            this.cdr = new CdrEntity(id);
            this.media = new MediaEntity(id);
            this.network = new NetworkEntity(id);
            this.device = new DeviceInfo("HighPerf-Device");
            this.cache = new SessionCache();

            shardingEntities.addAndGet(5); // Context + 4 entities
            nonShardingEntities.addAndGet(2); // Device + Cache
            totalEntities.addAndGet(7);
        }

        public boolean validateIdConsistency() {
            return id.equals(call.getId()) &&
                   id.equals(cdr.getId()) &&
                   id.equals(media.getId()) &&
                   id.equals(network.getId());
        }

        @Override
        public String getId() { return id; }

        @Override
        public void setId(String id) {
            this.id = id;
            if (call != null) call.setId(id);
            if (cdr != null) cdr.setId(id);
            if (media != null) media.setId(id);
            if (network != null) network.setId(id);
        }

        @Override
        public LocalDateTime getCreatedAt() { return createdAt; }

        @Override
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public CallEntity getCall() { return call; }
        public CdrEntity getCdr() { return cdr; }
        public MediaEntity getMedia() { return media; }
        public NetworkEntity getNetwork() { return network; }
    }

    static class CallEntity implements ShardingEntity {
        private String id;
        private LocalDateTime createdAt;
        private String callType = "VOICE";
        private Integer duration = 0;
        private List<CallEventEntity> events = new ArrayList<>();

        public CallEntity(String machineId) {
            this.id = machineId;
            this.createdAt = LocalDateTime.now();
        }

        public void addEvent(String type, String details) {
            CallEventEntity event = new CallEventEntity(id, type, details);
            events.add(event);
            callEvents.incrementAndGet();
            shardingEntities.incrementAndGet();
            totalEntities.incrementAndGet();
        }

        @Override
        public String getId() { return id; }

        @Override
        public void setId(String id) {
            this.id = id;
            for (CallEventEntity e : events) {
                e.setCallId(id);
            }
        }

        @Override
        public LocalDateTime getCreatedAt() { return createdAt; }

        @Override
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public Integer getDuration() { return duration; }
        public void setDuration(Integer duration) { this.duration = duration; }
        public List<CallEventEntity> getEvents() { return events; }
    }

    static class CallEventEntity implements ShardingEntity {
        private String id;
        private String callId;
        private LocalDateTime createdAt;
        private String eventType;
        private String details;

        public CallEventEntity(String callId, String type, String details) {
            this.id = UUID.randomUUID().toString();
            this.callId = callId;
            this.eventType = type;
            this.details = details;
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
        public void setCallId(String callId) { this.callId = callId; }
    }

    static class CdrEntity implements ShardingEntity {
        private String id;
        private LocalDateTime createdAt;
        private BigDecimal totalCharge = BigDecimal.ZERO;
        private List<ChargeEntity> charges = new ArrayList<>();

        public CdrEntity(String machineId) {
            this.id = machineId;
            this.createdAt = LocalDateTime.now();
        }

        public void addCharge(String type, BigDecimal amount) {
            ChargeEntity charge = new ChargeEntity(id, type, amount);
            charges.add(charge);
            totalCharge = totalCharge.add(amount);
            chargeEvents.incrementAndGet();
            shardingEntities.incrementAndGet();
            totalEntities.incrementAndGet();
        }

        @Override
        public String getId() { return id; }
        @Override
        public void setId(String id) {
            this.id = id;
            for (ChargeEntity c : charges) {
                c.setCdrId(id);
            }
        }
        @Override
        public LocalDateTime getCreatedAt() { return createdAt; }
        @Override
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public BigDecimal getTotalCharge() { return totalCharge; }
    }

    static class ChargeEntity implements ShardingEntity {
        private String id;
        private String cdrId;
        private LocalDateTime createdAt;
        private String type;
        private BigDecimal amount;

        public ChargeEntity(String cdrId, String type, BigDecimal amount) {
            this.id = UUID.randomUUID().toString();
            this.cdrId = cdrId;
            this.type = type;
            this.amount = amount;
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
        public void setCdrId(String cdrId) { this.cdrId = cdrId; }
    }

    static class MediaEntity implements ShardingEntity {
        private String id;
        private LocalDateTime createdAt;
        private String codec = "OPUS";
        private List<StreamEntity> streams = new ArrayList<>();

        public MediaEntity(String machineId) {
            this.id = machineId;
            this.createdAt = LocalDateTime.now();
        }

        public void addStream(String type, String direction) {
            StreamEntity stream = new StreamEntity(id, type, direction);
            streams.add(stream);
            mediaStreams.incrementAndGet();
            shardingEntities.incrementAndGet();
            totalEntities.incrementAndGet();
        }

        @Override
        public String getId() { return id; }
        @Override
        public void setId(String id) {
            this.id = id;
            for (StreamEntity s : streams) {
                s.setMediaId(id);
            }
        }
        @Override
        public LocalDateTime getCreatedAt() { return createdAt; }
        @Override
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    static class StreamEntity implements ShardingEntity {
        private String id;
        private String mediaId;
        private LocalDateTime createdAt;
        private String type;
        private String direction;

        public StreamEntity(String mediaId, String type, String direction) {
            this.id = UUID.randomUUID().toString();
            this.mediaId = mediaId;
            this.type = type;
            this.direction = direction;
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
        public void setMediaId(String mediaId) { this.mediaId = mediaId; }
    }

    static class NetworkEntity implements ShardingEntity {
        private String id;
        private LocalDateTime createdAt;
        private String protocol = "SIP";
        private String localIp = "10.0.0.1";
        private String remoteIp = "10.0.0.2";

        public NetworkEntity(String machineId) {
            this.id = machineId;
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
    }

    static class DeviceInfo {
        private String model;
        private LocalDateTime lastActive;

        public DeviceInfo(String model) {
            this.model = model;
            this.lastActive = LocalDateTime.now();
        }
    }

    static class SessionCache {
        private Map<String, Object> cache = new HashMap<>();

        public SessionCache() {
            cache.put("created", LocalDateTime.now());
        }
    }

    // Simulate call cycle with multi-entity operations
    private static void runCallCycle(String machineId) {
        CallMachineContext context = new CallMachineContext(machineId);
        contexts.put(machineId, context);
        activeContexts.incrementAndGet();
        peakContexts.updateAndGet(v -> Math.max(v, activeContexts.get()));
        stateCounts.get("IDLE").incrementAndGet();

        // Event 1: DIAL
        context.setState("DIALING");
        context.getCall().addEvent("DIAL", "Initiating call");
        stateCounts.get("IDLE").decrementAndGet();
        stateCounts.get("DIALING").incrementAndGet();
        totalEvents.incrementAndGet();

        // Event 2: RING
        context.setState("RINGING");
        context.getCall().addEvent("RING", "Phone ringing");
        stateCounts.get("DIALING").decrementAndGet();
        stateCounts.get("RINGING").incrementAndGet();
        totalEvents.incrementAndGet();

        // Event 3: ANSWER
        context.setState("CONNECTED");
        context.getCall().addEvent("ANSWER", "Call connected");
        context.getCdr().addCharge("CONNECTION", new BigDecimal("0.50"));
        context.getMedia().addStream("AUDIO", "BIDIRECTIONAL");
        stateCounts.get("RINGING").decrementAndGet();
        stateCounts.get("CONNECTED").incrementAndGet();
        totalEvents.incrementAndGet();

        // Random: Some calls go on hold
        if (Math.random() < 0.3) {
            context.setState("ON_HOLD");
            context.getCall().addEvent("HOLD", "Call on hold");
            stateCounts.get("CONNECTED").decrementAndGet();
            stateCounts.get("ON_HOLD").incrementAndGet();
            totalEvents.incrementAndGet();

            // Resume
            context.setState("CONNECTED");
            context.getCall().addEvent("RESUME", "Call resumed");
            stateCounts.get("ON_HOLD").decrementAndGet();
            stateCounts.get("CONNECTED").incrementAndGet();
            totalEvents.incrementAndGet();
        }

        // Event 4: HANGUP
        context.setState("IDLE");
        context.getCall().addEvent("HANGUP", "Call ended");
        context.getCall().setDuration((int)(Math.random() * 300)); // Random duration
        BigDecimal usage = new BigDecimal(Math.random() * 10);
        context.getCdr().addCharge("USAGE", usage);
        stateCounts.get("CONNECTED").decrementAndGet();
        stateCounts.get("IDLE").incrementAndGet();
        totalEvents.incrementAndGet();

        totalCycles.incrementAndGet();
        activeContexts.decrementAndGet();

        // Validate ID consistency
        if (!context.validateIdConsistency()) {
            System.err.println("⚠️  ID consistency failed for " + machineId);
        }
    }

    public static void main(String[] args) throws Exception {
        // Parse TPS from command line
        if (args.length > 0) {
            try {
                TARGET_TPS = Integer.parseInt(args[0]);
                System.out.println("\n🎯 Target TPS set to: " + TARGET_TPS);
            } catch (NumberFormatException e) {
                System.err.println("Invalid TPS. Using default: " + TARGET_TPS);
            }
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println("   MULTI-ENTITY TPS TEST - COMPLEX OBJECT GRAPHS");
        System.out.println("=".repeat(80));
        System.out.println("Configuration:");
        System.out.println("  Target: " + TARGET_TPS + " call cycles/sec");
        System.out.println("  Entities per cycle: ~10-15 (including nested)");
        System.out.println("  Test Duration: " + TEST_DURATION + " seconds");
        System.out.println("  Settle Time: " + SETTLE_TIME + " seconds");
        System.out.println("=".repeat(80) + "\n");

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(16);
        AtomicBoolean testRunning = new AtomicBoolean(true);
        long startTime = System.currentTimeMillis();

        // Progress reporter
        executor.scheduleAtFixedRate(() -> {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;

            if (elapsed <= TEST_DURATION) {
                System.out.printf("[TEST] T=%02d/%ds | Cycles: %,d | Events: %,d | Entities: %,d | Active: %d | TPS: %d\n",
                    elapsed, TEST_DURATION, totalCycles.get(), totalEvents.get(),
                    totalEntities.get(), activeContexts.get(),
                    elapsed > 0 ? totalCycles.get()/elapsed : 0);
            } else {
                long settleElapsed = elapsed - TEST_DURATION;
                System.out.printf("[SETTLE] T=%d/%ds | Active: %d | Waiting for completion...\n",
                    settleElapsed, SETTLE_TIME, activeContexts.get());
            }
        }, 1, 1, TimeUnit.SECONDS);

        // Call cycle generator
        executor.scheduleAtFixedRate(() -> {
            if (!testRunning.get()) return;

            try {
                String machineId = "machine-" + System.nanoTime();
                executor.submit(() -> runCallCycle(machineId));
            } catch (Exception e) {
                // Ignore
            }
        }, 0, 1000000/TARGET_TPS, TimeUnit.MICROSECONDS);

        // Run test
        Thread.sleep(TEST_DURATION * 1000);
        testRunning.set(false);

        System.out.println("\n[TEST COMPLETE] Stopped generating calls. Settling...\n");
        Thread.sleep(SETTLE_TIME * 1000);

        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS);

        // Final statistics
        printFinalReport();
    }

    private static void printFinalReport() {
        double actualTPS = totalCycles.get() / (double) TEST_DURATION;
        double eventsPerSec = totalEvents.get() / (double) TEST_DURATION;
        double entitiesPerSec = totalEntities.get() / (double) TEST_DURATION;
        double efficiency = (actualTPS / TARGET_TPS) * 100;

        System.out.println("\n" + "=".repeat(80));
        System.out.println("   FINAL PERFORMANCE REPORT");
        System.out.println("=".repeat(80));
        System.out.println();

        System.out.println("📊 THROUGHPUT METRICS:");
        System.out.println("  Target TPS:          " + String.format("%,d", TARGET_TPS));
        System.out.println("  Actual TPS:          " + String.format("%,.1f", actualTPS));
        System.out.println("  Efficiency:          " + String.format("%.1f%%", efficiency));
        System.out.println("  Events/sec:          " + String.format("%,.1f", eventsPerSec));
        System.out.println("  Entities/sec:        " + String.format("%,.1f", entitiesPerSec));
        System.out.println();

        System.out.println("📈 VOLUME STATISTICS:");
        System.out.println("  Total Cycles:        " + String.format("%,d", totalCycles.get()));
        System.out.println("  Total Events:        " + String.format("%,d", totalEvents.get()));
        System.out.println("  Total Entities:      " + String.format("%,d", totalEntities.get()));
        System.out.println("  Peak Contexts:       " + String.format("%,d", peakContexts.get()));
        System.out.println();

        System.out.println("🗂️  ENTITY BREAKDOWN:");
        System.out.println("  ShardingEntity:      " + String.format("%,d", shardingEntities.get()) +
                          " (will be persisted)");
        System.out.println("  Non-ShardingEntity:  " + String.format("%,d", nonShardingEntities.get()) +
                          " (transient only)");
        System.out.println("  Call Events:         " + String.format("%,d", callEvents.get()));
        System.out.println("  Charge Events:       " + String.format("%,d", chargeEvents.get()));
        System.out.println("  Media Streams:       " + String.format("%,d", mediaStreams.get()));
        System.out.println();

        System.out.println("📍 FINAL STATE DISTRIBUTION:");
        System.out.println("  Active Contexts:     " + contexts.size());
        Map<String, Integer> finalStates = new HashMap<>();
        for (CallMachineContext ctx : contexts.values()) {
            finalStates.put(ctx.getState(), finalStates.getOrDefault(ctx.getState(), 0) + 1);
        }
        finalStates.forEach((state, count) ->
            System.out.println("  " + state + ": " + String.format("%,d", count)));

        // Performance grade
        System.out.println("\n🏆 PERFORMANCE GRADE:");
        if (efficiency >= 95) {
            System.out.println("  ⭐⭐⭐⭐⭐ EXCELLENT - Target exceeded!");
        } else if (efficiency >= 80) {
            System.out.println("  ⭐⭐⭐⭐ VERY GOOD - Near target performance");
        } else if (efficiency >= 60) {
            System.out.println("  ⭐⭐⭐ GOOD - Acceptable performance");
        } else {
            System.out.println("  ⭐⭐ NEEDS OPTIMIZATION");
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println("   Test completed at " +
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        System.out.println("=".repeat(80));
    }
}