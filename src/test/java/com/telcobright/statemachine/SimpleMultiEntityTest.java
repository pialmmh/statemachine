package com.telcobright.statemachine;

import com.telcobright.core.entity.ShardingEntity;
import com.telcobright.core.annotation.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Simplified test demonstrating multi-entity with ID consistency
 */
public class SimpleMultiEntityTest {

    // ========== Entity Classes ==========

    @Table(name = "simple_context")
    static class SimpleContext implements ShardingEntity {
        @Id
        @Column(name = "id")
        private String id;

        @ShardingKey
        @Column(name = "created_at")
        private LocalDateTime createdAt;

        private SimpleCall call;
        private SimpleCdr cdr;
        private SimpleDevice device; // Singleton per machine

        public SimpleContext(String machineId) {
            this.id = machineId;
            this.createdAt = LocalDateTime.now();
            initializeEntities();
        }

        private void initializeEntities() {
            // All entities share the same machine ID
            this.call = new SimpleCall(id);
            this.cdr = new SimpleCdr(id);
            this.device = new SimpleDevice(id);

            // Share singleton device
            this.call.setDevice(this.device);
        }

        public boolean validateIdConsistency() {
            if (id == null) return false;
            if (call != null && !id.equals(call.getId())) return false;
            if (cdr != null && !id.equals(cdr.getId())) return false;
            if (device != null && !id.equals(device.getId())) return false;

            // Check child entities
            if (call != null && call.getEvents() != null) {
                for (SimpleEvent event : call.getEvents()) {
                    if (!id.equals(event.getCallId())) return false;
                }
            }
            return true;
        }

        @Override
        public String getId() { return id; }

        @Override
        public void setId(String id) {
            this.id = id;
            // Propagate to all entities
            if (call != null) call.setId(id);
            if (cdr != null) cdr.setId(id);
            if (device != null) device.setId(id);
        }

        @Override
        public LocalDateTime getCreatedAt() { return createdAt; }

        @Override
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        // Getters
        public SimpleCall getCall() { return call; }
        public SimpleCdr getCdr() { return cdr; }
        public SimpleDevice getDevice() { return device; }
    }

    @Table(name = "simple_call")
    static class SimpleCall implements ShardingEntity {
        @Id
        @Column(name = "id")
        private String id;

        @ShardingKey
        @Column(name = "created_at")
        private LocalDateTime createdAt;

        @Column(name = "caller_number")
        private String callerNumber;

        private List<SimpleEvent> events = new ArrayList<>();
        private transient SimpleDevice device;

        public SimpleCall(String machineId) {
            this.id = machineId;
            this.createdAt = LocalDateTime.now();
        }

        public void addEvent(String type, String data) {
            SimpleEvent event = new SimpleEvent(type, data);
            event.setCallId(this.id); // Reference machine ID
            events.add(event);
        }

        @Override
        public String getId() { return id; }

        @Override
        public void setId(String id) {
            this.id = id;
            // Propagate to events
            for (SimpleEvent event : events) {
                event.setCallId(id);
            }
        }

        @Override
        public LocalDateTime getCreatedAt() { return createdAt; }

        @Override
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public String getCallerNumber() { return callerNumber; }
        public void setCallerNumber(String callerNumber) { this.callerNumber = callerNumber; }
        public List<SimpleEvent> getEvents() { return events; }
        public SimpleDevice getDevice() { return device; }
        public void setDevice(SimpleDevice device) { this.device = device; }
    }

    @Table(name = "simple_cdr")
    static class SimpleCdr implements ShardingEntity {
        @Id
        @Column(name = "id")
        private String id;

        @ShardingKey
        @Column(name = "created_at")
        private LocalDateTime createdAt;

        @Column(name = "duration")
        private Integer duration;

        @Column(name = "charge_amount")
        private BigDecimal chargeAmount;

        public SimpleCdr(String machineId) {
            this.id = machineId;
            this.createdAt = LocalDateTime.now();
            this.chargeAmount = BigDecimal.ZERO;
        }

        @Override
        public String getId() { return id; }

        @Override
        public void setId(String id) { this.id = id; }

        @Override
        public LocalDateTime getCreatedAt() { return createdAt; }

        @Override
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public Integer getDuration() { return duration; }
        public void setDuration(Integer duration) { this.duration = duration; }
        public BigDecimal getChargeAmount() { return chargeAmount; }
        public void setChargeAmount(BigDecimal chargeAmount) { this.chargeAmount = chargeAmount; }
    }

    @Table(name = "simple_device")
    static class SimpleDevice implements ShardingEntity {
        @Id
        @Column(name = "id")
        private String id;

        @ShardingKey
        @Column(name = "created_at")
        private LocalDateTime createdAt;

        @Column(name = "model")
        private String model;

        @Column(name = "network_type")
        private String networkType;

        public SimpleDevice(String machineId) {
            this.id = machineId; // Singleton still uses machine ID
            this.createdAt = LocalDateTime.now();
            this.networkType = "4G";
        }

        @Override
        public String getId() { return id; }

        @Override
        public void setId(String id) { this.id = id; }

        @Override
        public LocalDateTime getCreatedAt() { return createdAt; }

        @Override
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getNetworkType() { return networkType; }
        public void setNetworkType(String networkType) { this.networkType = networkType; }
    }

    @Table(name = "simple_event")
    static class SimpleEvent implements ShardingEntity {
        @Id
        @Column(name = "id")
        private String id;

        @ShardingKey
        @Column(name = "created_at")
        private LocalDateTime createdAt;

        @Column(name = "call_id")
        private String callId; // References machine ID

        @Column(name = "event_type")
        private String eventType;

        @Column(name = "event_data")
        private String eventData;

        public SimpleEvent(String type, String data) {
            this.id = java.util.UUID.randomUUID().toString();
            this.createdAt = LocalDateTime.now();
            this.eventType = type;
            this.eventData = data;
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
        public void setCallId(String callId) { this.callId = callId; }
        public String getEventType() { return eventType; }
        public String getEventData() { return eventData; }
    }

    // ========== Test Methods ==========

    public static void main(String[] args) {
        System.out.println("\n================================================================================");
        System.out.println("   MULTI-ENTITY TEST WITH ID CONSISTENCY");
        System.out.println("================================================================================\n");

        // Test 1: ID Consistency
        testIdConsistency();

        // Test 2: Singleton Behavior
        testSingletonBehavior();

        // Test 3: Child Entity References
        testChildReferences();

        System.out.println("\n================================================================================");
        System.out.println("   ✅ ALL TESTS PASSED");
        System.out.println("================================================================================");
    }

    private static void testIdConsistency() {
        System.out.println("TEST 1: ID Consistency Across Entities");
        System.out.println("----------------------------------------");

        String machineId = "MACHINE-001";
        SimpleContext context = new SimpleContext(machineId);

        // Verify all entities have the same ID
        assert machineId.equals(context.getId()) : "Context ID mismatch";
        assert machineId.equals(context.getCall().getId()) : "Call ID mismatch";
        assert machineId.equals(context.getCdr().getId()) : "CDR ID mismatch";
        assert machineId.equals(context.getDevice().getId()) : "Device ID mismatch";

        System.out.println("✅ All entities share machine ID: " + machineId);
        System.out.println("  - Context ID: " + context.getId());
        System.out.println("  - Call ID: " + context.getCall().getId());
        System.out.println("  - CDR ID: " + context.getCdr().getId());
        System.out.println("  - Device ID: " + context.getDevice().getId());

        // Validate consistency
        assert context.validateIdConsistency() : "ID consistency validation failed";
        System.out.println("✅ ID consistency validated");
    }

    private static void testSingletonBehavior() {
        System.out.println("\nTEST 2: Singleton Device Behavior");
        System.out.println("----------------------------------------");

        String machineId = "MACHINE-002";
        SimpleContext context = new SimpleContext(machineId);

        // Device should be shared (singleton)
        SimpleDevice device1 = context.getDevice();
        SimpleDevice device2 = context.getCall().getDevice();

        assert device1 == device2 : "Device not singleton within machine";
        assert machineId.equals(device1.getId()) : "Singleton device ID mismatch";

        // Update device through one reference
        device1.setModel("iPhone 15");
        device1.setNetworkType("5G");

        // Should be visible through other reference
        assert "iPhone 15".equals(device2.getModel()) : "Singleton update not visible";
        assert "5G".equals(device2.getNetworkType()) : "Singleton update not visible";

        System.out.println("✅ Singleton device shared within machine");
        System.out.println("  - Same instance: " + (device1 == device2));
        System.out.println("  - Device ID: " + device1.getId());
        System.out.println("  - Model: " + device1.getModel());
        System.out.println("  - Network: " + device1.getNetworkType());
    }

    private static void testChildReferences() {
        System.out.println("\nTEST 3: Child Entity References");
        System.out.println("----------------------------------------");

        String machineId = "MACHINE-003";
        SimpleContext context = new SimpleContext(machineId);

        // Add events to call
        context.getCall().setCallerNumber("+1-555-1234");
        context.getCall().addEvent("INITIATED", "Call started");
        context.getCall().addEvent("RINGING", "Phone ringing");
        context.getCall().addEvent("CONNECTED", "Call connected");

        // Set CDR data
        context.getCdr().setDuration(120);
        context.getCdr().setChargeAmount(new BigDecimal("5.50"));

        // Verify child entities reference machine ID
        for (SimpleEvent event : context.getCall().getEvents()) {
            assert machineId.equals(event.getCallId()) : "Event doesn't reference machine ID";
        }

        System.out.println("✅ Child entities reference machine ID");
        System.out.println("  - Machine ID: " + machineId);
        System.out.println("  - Call has " + context.getCall().getEvents().size() + " events");
        for (SimpleEvent event : context.getCall().getEvents()) {
            System.out.println("    * " + event.getEventType() + " -> callId: " + event.getCallId());
        }

        // Validate consistency including children
        assert context.validateIdConsistency() : "ID consistency with children failed";
        System.out.println("✅ ID consistency validated with child entities");
    }
}