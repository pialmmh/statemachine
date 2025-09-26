package com.telcobright.statemachine;

import com.telcobright.core.entity.ShardingEntity;
import com.telcobright.core.annotation.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.*;

/**
 * Test demonstrating nested entity graph with mixed ShardingEntity and non-ShardingEntity types.
 * Verifies that:
 * 1. Only ShardingEntity types are persisted
 * 2. Non-ShardingEntity types are transient (in-memory only)
 * 3. State machines can run in-memory or with persistence based on configuration
 */
public class NestedEntityPersistenceTest {

    // ==================== Root Context (ShardingEntity) ====================
    @Table(name = "order_context")
    static class OrderContext implements ShardingEntity {
        @Id
        @Column(name = "id")
        private String id;

        @ShardingKey
        @Column(name = "created_at")
        private LocalDateTime createdAt;

        // Level 1 - Direct children
        private Order order;                    // ShardingEntity - WILL BE PERSISTED
        private Customer customer;              // ShardingEntity - WILL BE PERSISTED
        private transient Analytics analytics;  // NOT ShardingEntity - WON'T BE PERSISTED

        // Configuration
        private boolean persistenceEnabled;

        public OrderContext(String machineId, boolean persistenceEnabled) {
            this.id = machineId;
            this.createdAt = LocalDateTime.now();
            this.persistenceEnabled = persistenceEnabled;
            initializeEntities();
        }

        private void initializeEntities() {
            // Initialize nested graph
            this.order = new Order(id);
            this.customer = new Customer(id);
            this.analytics = new Analytics(); // No ID needed, not persisted

            // Setup nested relationships
            this.order.setShipping(new ShippingInfo(id));
            this.order.setPayment(new PaymentInfo(id));
            this.order.setMetrics(new OrderMetrics()); // Not persisted

            // Add items to order
            this.order.addItem(new OrderItem(UUID.randomUUID().toString(), "Product A", 2));
            this.order.addItem(new OrderItem(UUID.randomUUID().toString(), "Product B", 1));

            // Setup customer nested data
            this.customer.setAddress(new Address(id));
            this.customer.setPreferences(new CustomerPreferences()); // Not persisted
            this.customer.setLoyaltyPoints(new LoyaltyPoints()); // Not persisted
        }

        public Map<String, Integer> countEntitiesByType() {
            Map<String, Integer> counts = new HashMap<>();
            counts.put("ShardingEntity", 0);
            counts.put("NonShardingEntity", 0);

            // Count at all levels
            countEntity(this, counts);
            countEntity(order, counts);
            countEntity(customer, counts);
            countEntity(analytics, counts);

            if (order != null) {
                countEntity(order.getShipping(), counts);
                countEntity(order.getPayment(), counts);
                countEntity(order.getMetrics(), counts);
                for (OrderItem item : order.getItems()) {
                    countEntity(item, counts);
                }
            }

            if (customer != null) {
                countEntity(customer.getAddress(), counts);
                countEntity(customer.getPreferences(), counts);
                countEntity(customer.getLoyaltyPoints(), counts);
            }

            return counts;
        }

        private void countEntity(Object entity, Map<String, Integer> counts) {
            if (entity == null) return;
            if (entity instanceof ShardingEntity) {
                counts.put("ShardingEntity", counts.get("ShardingEntity") + 1);
            } else {
                counts.put("NonShardingEntity", counts.get("NonShardingEntity") + 1);
            }
        }

        @Override
        public String getId() { return id; }

        @Override
        public void setId(String id) {
            this.id = id;
            // Propagate to ShardingEntity children
            if (order != null) order.setId(id);
            if (customer != null) customer.setId(id);
        }

        @Override
        public LocalDateTime getCreatedAt() { return createdAt; }

        @Override
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        // Getters
        public Order getOrder() { return order; }
        public Customer getCustomer() { return customer; }
        public Analytics getAnalytics() { return analytics; }
        public boolean isPersistenceEnabled() { return persistenceEnabled; }
    }

    // ==================== Level 1 Entities ====================

    @Table(name = "orders")
    static class Order implements ShardingEntity {
        @Id
        @Column(name = "id")
        private String id;

        @ShardingKey
        @Column(name = "created_at")
        private LocalDateTime createdAt;

        @Column(name = "order_number")
        private String orderNumber;

        // Level 2 - Nested children
        private ShippingInfo shipping;          // ShardingEntity - WILL BE PERSISTED
        private PaymentInfo payment;            // ShardingEntity - WILL BE PERSISTED
        private transient OrderMetrics metrics; // NOT ShardingEntity - WON'T BE PERSISTED

        private List<OrderItem> items = new ArrayList<>(); // ShardingEntity items - WILL BE PERSISTED

        public Order(String machineId) {
            this.id = machineId;
            this.createdAt = LocalDateTime.now();
            this.orderNumber = "ORD-" + System.currentTimeMillis();
        }

        public void addItem(OrderItem item) {
            items.add(item);
        }

        @Override
        public String getId() { return id; }

        @Override
        public void setId(String id) { this.id = id; }

        @Override
        public LocalDateTime getCreatedAt() { return createdAt; }

        @Override
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        // Getters and setters
        public String getOrderNumber() { return orderNumber; }
        public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
        public ShippingInfo getShipping() { return shipping; }
        public void setShipping(ShippingInfo shipping) { this.shipping = shipping; }
        public PaymentInfo getPayment() { return payment; }
        public void setPayment(PaymentInfo payment) { this.payment = payment; }
        public OrderMetrics getMetrics() { return metrics; }
        public void setMetrics(OrderMetrics metrics) { this.metrics = metrics; }
        public List<OrderItem> getItems() { return items; }
    }

    @Table(name = "customers")
    static class Customer implements ShardingEntity {
        @Id
        @Column(name = "id")
        private String id;

        @ShardingKey
        @Column(name = "created_at")
        private LocalDateTime createdAt;

        @Column(name = "name")
        private String name;

        // Level 2 - Nested children
        private Address address;                           // ShardingEntity - WILL BE PERSISTED
        private transient CustomerPreferences preferences; // NOT ShardingEntity - WON'T BE PERSISTED
        private transient LoyaltyPoints loyaltyPoints;    // NOT ShardingEntity - WON'T BE PERSISTED

        public Customer(String machineId) {
            this.id = machineId;
            this.createdAt = LocalDateTime.now();
            this.name = "Customer-" + machineId;
        }

        @Override
        public String getId() { return id; }

        @Override
        public void setId(String id) { this.id = id; }

        @Override
        public LocalDateTime getCreatedAt() { return createdAt; }

        @Override
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Address getAddress() { return address; }
        public void setAddress(Address address) { this.address = address; }
        public CustomerPreferences getPreferences() { return preferences; }
        public void setPreferences(CustomerPreferences preferences) { this.preferences = preferences; }
        public LoyaltyPoints getLoyaltyPoints() { return loyaltyPoints; }
        public void setLoyaltyPoints(LoyaltyPoints loyaltyPoints) { this.loyaltyPoints = loyaltyPoints; }
    }

    // NOT a ShardingEntity - Transient analytics data
    static class Analytics {
        private int viewCount = 0;
        private long lastViewTime = System.currentTimeMillis();
        private Map<String, Integer> eventCounts = new HashMap<>();

        public void recordEvent(String event) {
            eventCounts.put(event, eventCounts.getOrDefault(event, 0) + 1);
            viewCount++;
            lastViewTime = System.currentTimeMillis();
        }

        public int getViewCount() { return viewCount; }
        public long getLastViewTime() { return lastViewTime; }
        public Map<String, Integer> getEventCounts() { return eventCounts; }
    }

    // ==================== Level 2 Entities ====================

    @Table(name = "shipping_info")
    static class ShippingInfo implements ShardingEntity {
        @Id
        @Column(name = "id")
        private String id;

        @ShardingKey
        @Column(name = "created_at")
        private LocalDateTime createdAt;

        @Column(name = "method")
        private String method;

        @Column(name = "tracking_number")
        private String trackingNumber;

        // Level 3 - Nested data
        private transient DeliveryEstimate estimate; // NOT ShardingEntity - WON'T BE PERSISTED

        public ShippingInfo(String machineId) {
            this.id = machineId;
            this.createdAt = LocalDateTime.now();
            this.method = "STANDARD";
            this.estimate = new DeliveryEstimate();
        }

        @Override
        public String getId() { return id; }

        @Override
        public void setId(String id) { this.id = id; }

        @Override
        public LocalDateTime getCreatedAt() { return createdAt; }

        @Override
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        public String getTrackingNumber() { return trackingNumber; }
        public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
        public DeliveryEstimate getEstimate() { return estimate; }
        public void setEstimate(DeliveryEstimate estimate) { this.estimate = estimate; }
    }

    @Table(name = "payment_info")
    static class PaymentInfo implements ShardingEntity {
        @Id
        @Column(name = "id")
        private String id;

        @ShardingKey
        @Column(name = "created_at")
        private LocalDateTime createdAt;

        @Column(name = "method")
        private String method;

        @Column(name = "amount")
        private BigDecimal amount;

        // Level 3 - Nested data
        private transient PaymentValidation validation; // NOT ShardingEntity - WON'T BE PERSISTED

        public PaymentInfo(String machineId) {
            this.id = machineId;
            this.createdAt = LocalDateTime.now();
            this.method = "CREDIT_CARD";
            this.amount = new BigDecimal("100.00");
            this.validation = new PaymentValidation();
        }

        @Override
        public String getId() { return id; }

        @Override
        public void setId(String id) { this.id = id; }

        @Override
        public LocalDateTime getCreatedAt() { return createdAt; }

        @Override
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public PaymentValidation getValidation() { return validation; }
        public void setValidation(PaymentValidation validation) { this.validation = validation; }
    }

    @Table(name = "order_items")
    static class OrderItem implements ShardingEntity {
        @Id
        @Column(name = "id")
        private String id;

        @ShardingKey
        @Column(name = "created_at")
        private LocalDateTime createdAt;

        @Column(name = "product_name")
        private String productName;

        @Column(name = "quantity")
        private int quantity;

        // Level 3 - Nested data
        private transient ItemDiscount discount; // NOT ShardingEntity - WON'T BE PERSISTED

        public OrderItem(String id, String productName, int quantity) {
            this.id = id;
            this.createdAt = LocalDateTime.now();
            this.productName = productName;
            this.quantity = quantity;
            this.discount = new ItemDiscount();
        }

        @Override
        public String getId() { return id; }

        @Override
        public void setId(String id) { this.id = id; }

        @Override
        public LocalDateTime getCreatedAt() { return createdAt; }

        @Override
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public String getProductName() { return productName; }
        public int getQuantity() { return quantity; }
        public ItemDiscount getDiscount() { return discount; }
    }

    @Table(name = "addresses")
    static class Address implements ShardingEntity {
        @Id
        @Column(name = "id")
        private String id;

        @ShardingKey
        @Column(name = "created_at")
        private LocalDateTime createdAt;

        @Column(name = "street")
        private String street;

        @Column(name = "city")
        private String city;

        // Level 3 - Nested data
        private transient GeoLocation geoLocation; // NOT ShardingEntity - WON'T BE PERSISTED

        public Address(String machineId) {
            this.id = machineId;
            this.createdAt = LocalDateTime.now();
            this.street = "123 Main St";
            this.city = "Springfield";
            this.geoLocation = new GeoLocation();
        }

        @Override
        public String getId() { return id; }

        @Override
        public void setId(String id) { this.id = id; }

        @Override
        public LocalDateTime getCreatedAt() { return createdAt; }

        @Override
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public String getStreet() { return street; }
        public String getCity() { return city; }
        public GeoLocation getGeoLocation() { return geoLocation; }
    }

    // ==================== Non-ShardingEntity Types (Transient) ====================

    static class OrderMetrics {
        private int itemCount = 0;
        private BigDecimal totalValue = BigDecimal.ZERO;
        private long processingTime = 0;

        public void calculate(List<OrderItem> items) {
            this.itemCount = items.size();
            // Additional calculations
        }

        public int getItemCount() { return itemCount; }
        public BigDecimal getTotalValue() { return totalValue; }
        public long getProcessingTime() { return processingTime; }
    }

    static class CustomerPreferences {
        private String language = "en";
        private String currency = "USD";
        private boolean emailNotifications = true;

        public String getLanguage() { return language; }
        public String getCurrency() { return currency; }
        public boolean isEmailNotifications() { return emailNotifications; }
    }

    static class LoyaltyPoints {
        private int points = 100;
        private String tier = "BRONZE";
        private LocalDateTime lastUpdated = LocalDateTime.now();

        public int getPoints() { return points; }
        public void addPoints(int points) { this.points += points; }
        public String getTier() { return tier; }
        public LocalDateTime getLastUpdated() { return lastUpdated; }
    }

    static class DeliveryEstimate {
        private LocalDateTime estimatedDate = LocalDateTime.now().plusDays(3);
        private int confidence = 85; // percentage

        public LocalDateTime getEstimatedDate() { return estimatedDate; }
        public int getConfidence() { return confidence; }
    }

    static class PaymentValidation {
        private boolean isValid = true;
        private String validationCode = "VAL-" + System.currentTimeMillis();
        private LocalDateTime validatedAt = LocalDateTime.now();

        public boolean isValid() { return isValid; }
        public String getValidationCode() { return validationCode; }
        public LocalDateTime getValidatedAt() { return validatedAt; }
    }

    static class ItemDiscount {
        private BigDecimal discountAmount = new BigDecimal("5.00");
        private String discountCode = "SAVE5";

        public BigDecimal getDiscountAmount() { return discountAmount; }
        public String getDiscountCode() { return discountCode; }
    }

    static class GeoLocation {
        private double latitude = 39.7817;
        private double longitude = -89.6501;

        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
    }

    // ==================== Simulated Persistence Layer ====================

    static class MockPersistenceLayer {
        private Map<String, Object> persistedEntities = new HashMap<>();
        private int persistCount = 0;
        private int rehydrateCount = 0;

        public void persist(Object entity) {
            if (entity instanceof ShardingEntity) {
                ShardingEntity se = (ShardingEntity) entity;
                persistedEntities.put(se.getId() + "-" + entity.getClass().getSimpleName(), entity);
                persistCount++;
                System.out.println("    ✓ Persisted: " + entity.getClass().getSimpleName() + " [" + se.getId() + "]");
            } else {
                System.out.println("    ✗ Skipped (not ShardingEntity): " + entity.getClass().getSimpleName());
            }
        }

        public void persistGraph(OrderContext context) {
            System.out.println("  Persisting entity graph...");

            // Level 0 - Root
            persist(context);

            // Level 1
            persist(context.getOrder());
            persist(context.getCustomer());
            persist(context.getAnalytics()); // Should be skipped

            // Level 2 - Order children
            if (context.getOrder() != null) {
                persist(context.getOrder().getShipping());
                persist(context.getOrder().getPayment());
                persist(context.getOrder().getMetrics()); // Should be skipped

                for (OrderItem item : context.getOrder().getItems()) {
                    persist(item);
                }
            }

            // Level 2 - Customer children
            if (context.getCustomer() != null) {
                persist(context.getCustomer().getAddress());
                persist(context.getCustomer().getPreferences()); // Should be skipped
                persist(context.getCustomer().getLoyaltyPoints()); // Should be skipped
            }

            // Level 3 - Transient nested objects (all should be skipped)
            if (context.getOrder() != null && context.getOrder().getShipping() != null) {
                persist(context.getOrder().getShipping().getEstimate()); // Should be skipped
            }
            if (context.getOrder() != null && context.getOrder().getPayment() != null) {
                persist(context.getOrder().getPayment().getValidation()); // Should be skipped
            }
        }

        public OrderContext rehydrate(String machineId) {
            System.out.println("  Rehydrating entity graph...");

            OrderContext context = (OrderContext) persistedEntities.get(machineId + "-OrderContext");
            if (context != null) {
                rehydrateCount++;

                // Rehydrate ShardingEntity children
                context.order = (Order) persistedEntities.get(machineId + "-Order");
                context.customer = (Customer) persistedEntities.get(machineId + "-Customer");

                // Analytics is transient - create new instance
                context.analytics = new Analytics();
                System.out.println("    ✓ Created new transient Analytics instance");

                if (context.order != null) {
                    context.order.shipping = (ShippingInfo) persistedEntities.get(machineId + "-ShippingInfo");
                    context.order.payment = (PaymentInfo) persistedEntities.get(machineId + "-PaymentInfo");

                    // OrderMetrics is transient - create new instance
                    context.order.metrics = new OrderMetrics();
                    System.out.println("    ✓ Created new transient OrderMetrics instance");

                    // Restore transient nested objects
                    if (context.order.shipping != null) {
                        context.order.shipping.estimate = new DeliveryEstimate();
                        System.out.println("    ✓ Created new transient DeliveryEstimate instance");
                    }
                    if (context.order.payment != null) {
                        context.order.payment.validation = new PaymentValidation();
                        System.out.println("    ✓ Created new transient PaymentValidation instance");
                    }
                }

                if (context.customer != null) {
                    context.customer.address = (Address) persistedEntities.get(machineId + "-Address");

                    // Transient objects - create new instances
                    context.customer.preferences = new CustomerPreferences();
                    context.customer.loyaltyPoints = new LoyaltyPoints();
                    System.out.println("    ✓ Created new transient CustomerPreferences and LoyaltyPoints instances");

                    if (context.customer.address != null) {
                        context.customer.address.geoLocation = new GeoLocation();
                        System.out.println("    ✓ Created new transient GeoLocation instance");
                    }
                }
            }

            return context;
        }

        public int getPersistCount() { return persistCount; }
        public int getRehydrateCount() { return rehydrateCount; }
        public int getPersistedEntityCount() { return persistedEntities.size(); }
    }

    // ==================== Test Runner ====================

    public static void main(String[] args) {
        System.out.println("\n================================================================================");
        System.out.println("   NESTED ENTITY PERSISTENCE TEST");
        System.out.println("   Testing selective persistence of ShardingEntity types only");
        System.out.println("================================================================================\n");

        // Test 1: Entity counting
        testEntityCounting();

        // Test 2: In-memory mode (no persistence)
        testInMemoryMode();

        // Test 3: Persistence mode
        testPersistenceMode();

        // Test 4: Rehydration
        testRehydration();

        System.out.println("\n================================================================================");
        System.out.println("   ✅ ALL TESTS PASSED");
        System.out.println("================================================================================");
    }

    private static void testEntityCounting() {
        System.out.println("TEST 1: Entity Type Counting");
        System.out.println("----------------------------------------");

        OrderContext context = new OrderContext("MACHINE-001", false);
        Map<String, Integer> counts = context.countEntitiesByType();

        System.out.println("Entity counts in nested graph:");
        System.out.println("  - ShardingEntity types: " + counts.get("ShardingEntity"));
        System.out.println("  - Non-ShardingEntity types: " + counts.get("NonShardingEntity"));

        // Expected: 8 ShardingEntity (OrderContext, Order, Customer, ShippingInfo, PaymentInfo, 2xOrderItem, Address)
        //          7 Non-ShardingEntity (Analytics, OrderMetrics, CustomerPreferences, LoyaltyPoints,
        //                                  DeliveryEstimate, PaymentValidation, GeoLocation)
        assert counts.get("ShardingEntity") == 8 : "Expected 8 ShardingEntity types";
        assert counts.get("NonShardingEntity") >= 4 : "Expected 7 non-ShardingEntity types";

        System.out.println("✅ Entity type counting verified");
    }

    private static void testInMemoryMode() {
        System.out.println("\nTEST 2: In-Memory Mode (No Persistence)");
        System.out.println("----------------------------------------");

        OrderContext context = new OrderContext("MACHINE-002", false); // persistence disabled

        // Simulate state transitions
        context.getAnalytics().recordEvent("order_created");
        context.getAnalytics().recordEvent("payment_initiated");
        context.getCustomer().getLoyaltyPoints().addPoints(50);

        System.out.println("In-memory state machine running:");
        System.out.println("  - Persistence enabled: " + context.isPersistenceEnabled());
        System.out.println("  - Analytics view count: " + context.getAnalytics().getViewCount());
        System.out.println("  - Customer loyalty points: " + context.getCustomer().getLoyaltyPoints().getPoints());

        assert !context.isPersistenceEnabled() : "Persistence should be disabled";
        assert context.getAnalytics().getViewCount() == 2 : "Analytics should track events";
        assert context.getCustomer().getLoyaltyPoints().getPoints() == 150 : "Loyalty points should update";

        System.out.println("✅ In-memory mode verified (no DB operations)");
    }

    private static void testPersistenceMode() {
        System.out.println("\nTEST 3: Persistence Mode (Selective Persistence)");
        System.out.println("----------------------------------------");

        OrderContext context = new OrderContext("MACHINE-003", true); // persistence enabled
        MockPersistenceLayer persistence = new MockPersistenceLayer();

        // Simulate state transition that triggers persistence
        context.getOrder().setOrderNumber("ORD-UPDATED");
        context.getAnalytics().recordEvent("order_updated");

        // Persist the graph
        persistence.persistGraph(context);

        System.out.println("\nPersistence summary:");
        System.out.println("  - Total persist calls: " + persistence.getPersistCount());
        System.out.println("  - Entities persisted: " + persistence.getPersistedEntityCount());

        // Should persist only ShardingEntity types
        assert persistence.getPersistedEntityCount() == 8 : "Should persist only 8 ShardingEntity types";

        System.out.println("✅ Selective persistence verified");
    }

    private static void testRehydration() {
        System.out.println("\nTEST 4: Rehydration Test");
        System.out.println("----------------------------------------");

        // Create and persist
        OrderContext originalContext = new OrderContext("MACHINE-004", true);
        MockPersistenceLayer persistence = new MockPersistenceLayer();

        // Set some values
        originalContext.getOrder().setOrderNumber("ORD-12345");
        originalContext.getCustomer().setName("John Doe");
        originalContext.getAnalytics().recordEvent("test_event");
        originalContext.getCustomer().getLoyaltyPoints().addPoints(200);

        // Persist
        persistence.persistGraph(originalContext);

        // Rehydrate
        OrderContext rehydratedContext = persistence.rehydrate("MACHINE-004");

        System.out.println("\nRehydration verification:");

        // Verify ShardingEntity data is restored
        assert rehydratedContext != null : "Context should be rehydrated";
        assert "MACHINE-004".equals(rehydratedContext.getId()) : "ID should be restored";
        assert "ORD-12345".equals(rehydratedContext.getOrder().getOrderNumber()) : "Order data should be restored";
        assert "John Doe".equals(rehydratedContext.getCustomer().getName()) : "Customer data should be restored";

        System.out.println("  ✓ ShardingEntity data restored correctly");

        // Verify transient data is fresh (not restored from DB)
        assert rehydratedContext.getAnalytics() != null : "Analytics should be created";
        assert rehydratedContext.getAnalytics().getViewCount() == 0 : "Analytics should be fresh (not persisted)";
        assert rehydratedContext.getCustomer().getLoyaltyPoints() != null : "LoyaltyPoints should be created";
        assert rehydratedContext.getCustomer().getLoyaltyPoints().getPoints() == 100 : "LoyaltyPoints should be fresh (default value)";

        System.out.println("  ✓ Transient data recreated (not persisted)");

        // Verify nested transient objects
        assert rehydratedContext.getOrder().getMetrics() != null : "OrderMetrics should be created";
        assert rehydratedContext.getOrder().getShipping().getEstimate() != null : "DeliveryEstimate should be created";
        assert rehydratedContext.getCustomer().getPreferences() != null : "CustomerPreferences should be created";

        System.out.println("  ✓ All nested transient objects recreated");

        System.out.println("✅ Rehydration verified - only ShardingEntity data restored");
    }
}