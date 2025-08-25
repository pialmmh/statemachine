package com.telcobright.statemachine.examples;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.events.StateMachineEvent;
import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * Example showing proper separation of persistent and volatile contexts
 */
public class ProperContextExample {
    
    /**
     * Persistent Context - Gets saved to database
     * Contains only data that needs to survive restarts
     */
    public static class OrderPersistentContext implements StateMachineContextEntity<String> {
        private String orderId;
        private String customerId;
        private Double totalAmount;
        private LocalDateTime orderDate;
        private String paymentStatus;
        private String currentState = "PENDING";
        private LocalDateTime lastStateChange;
        private boolean complete = false;
        
        public OrderPersistentContext(String orderId) {
            this.orderId = orderId;
            this.orderDate = LocalDateTime.now();
            this.lastStateChange = LocalDateTime.now();
        }
        
        // Getters and setters
        public String getOrderId() { return orderId; }
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public Double getTotalAmount() { return totalAmount; }
        public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }
        public String getPaymentStatus() { return paymentStatus; }
        public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
        
        @Override
        public StateMachineContextEntity<String> deepCopy() {
            OrderPersistentContext copy = new OrderPersistentContext(this.orderId);
            copy.setCustomerId(this.customerId);
            copy.setTotalAmount(this.totalAmount);
            copy.setPaymentStatus(this.paymentStatus);
            copy.setCurrentState(this.currentState);
            copy.setLastStateChange(this.lastStateChange);
            copy.setComplete(this.complete);
            return copy;
        }
        
        @Override
        public boolean isComplete() { return complete; }
        
        @Override
        public void setComplete(boolean complete) { this.complete = complete; }
        
        @Override
        public String getCurrentState() { return currentState; }
        
        @Override
        public void setCurrentState(String state) { this.currentState = state; }
        
        @Override
        public LocalDateTime getLastStateChange() { return lastStateChange; }
        
        @Override
        public void setLastStateChange(LocalDateTime lastStateChange) { this.lastStateChange = lastStateChange; }
    }
    
    /**
     * Volatile Context - NOT persisted
     * Contains runtime data like connections, caches, calculated values
     */
    public static class OrderVolatileContext {
        private java.sql.Connection dbConnection;
        private java.util.Map<String, Object> runtimeCache;
        private java.util.List<String> validationErrors;
        private long processingStartTime;
        
        public OrderVolatileContext() {
            this.runtimeCache = new java.util.HashMap<>();
            this.validationErrors = new java.util.ArrayList<>();
            this.processingStartTime = System.currentTimeMillis();
        }
        
        // Factory method to recreate from persistent context during rehydration
        public static OrderVolatileContext createFromPersistent(OrderPersistentContext persistent) {
            OrderVolatileContext volatile_ = new OrderVolatileContext();
            
            // Recreate runtime state based on persistent data
            // For example, reconnect to database, rebuild caches, etc.
            volatile_.initializeForOrder(persistent.getOrderId());
            
            return volatile_;
        }
        
        private void initializeForOrder(String orderId) {
            // Initialize connection, load related data into cache, etc.
            System.out.println("[Volatile Context] Initialized for order: " + orderId);
            runtimeCache.put("orderId", orderId);
            // In real app: dbConnection = DataSource.getConnection();
        }
        
        public void cleanup() {
            // Clean up resources
            if (dbConnection != null) {
                try { dbConnection.close(); } catch (Exception e) {}
            }
            runtimeCache.clear();
        }
        
        // Getters
        public java.sql.Connection getDbConnection() { return dbConnection; }
        public java.util.Map<String, Object> getRuntimeCache() { return runtimeCache; }
        public java.util.List<String> getValidationErrors() { return validationErrors; }
        public long getProcessingDuration() { return System.currentTimeMillis() - processingStartTime; }
    }
    
    // Events
    public static class OrderPlaced implements com.telcobright.statemachine.events.StateMachineEvent {
        private final String customerId;
        private final Double amount;
        private final long timestamp;
        
        public OrderPlaced(String customerId, Double amount) {
            this.customerId = customerId;
            this.amount = amount;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getCustomerId() { return customerId; }
        public Double getAmount() { return amount; }
        
        @Override
        public String getEventType() { return "ORDER_PLACED"; }
        
        @Override
        public String getDescription() { return "Order placed by customer " + customerId; }
        
        @Override
        public Object getPayload() { return this; }
        
        @Override
        public long getTimestamp() { return timestamp; }
    }
    
    public static class PaymentReceived implements com.telcobright.statemachine.events.StateMachineEvent {
        private final String transactionId;
        private final long timestamp;
        
        public PaymentReceived(String transactionId) {
            this.transactionId = transactionId;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getTransactionId() { return transactionId; }
        
        @Override
        public String getEventType() { return "PAYMENT_RECEIVED"; }
        
        @Override
        public String getDescription() { return "Payment received with transaction " + transactionId; }
        
        @Override
        public Object getPayload() { return this; }
        
        @Override
        public long getTimestamp() { return timestamp; }
    }
    
    public static class OrderShipped implements com.telcobright.statemachine.events.StateMachineEvent {
        private final String trackingNumber;
        private final long timestamp;
        
        public OrderShipped(String trackingNumber) {
            this.trackingNumber = trackingNumber;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getTrackingNumber() { return trackingNumber; }
        
        @Override
        public String getEventType() { return "ORDER_SHIPPED"; }
        
        @Override
        public String getDescription() { return "Order shipped with tracking " + trackingNumber; }
        
        @Override
        public Object getPayload() { return this; }
        
        @Override
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Create a state machine with proper context separation
     */
    public static GenericStateMachine<OrderPersistentContext, OrderVolatileContext> createOrderStateMachine(
            String orderId) {
        
        // Create persistent context
        OrderPersistentContext persistentContext = new OrderPersistentContext(orderId);
        
        // Create volatile context  
        OrderVolatileContext volatileContext = new OrderVolatileContext();
        
        // Build the state machine using enhanced builder
        return EnhancedFluentBuilder.<OrderPersistentContext, OrderVolatileContext>create(orderId)
            .withPersistentContext(persistentContext)
            .withVolatileContext(volatileContext)
            .withVolatileContextFactory(() -> OrderVolatileContext.createFromPersistent(persistentContext))
            .initialState("PENDING")
            
            .state("PENDING")
                .on(OrderPlaced.class).to("AWAITING_PAYMENT")
                .done()
                
            .state("AWAITING_PAYMENT")
                .timeout(24 * 60, com.telcobright.statemachine.timeout.TimeUnit.MINUTES, "CANCELLED") // 24 hours
                .on(PaymentReceived.class).to("PROCESSING")
                .done()
                
            .state("PROCESSING")
                .offline() // This state runs in background, machine can be evicted
                .on(OrderShipped.class).to("SHIPPED")
                .done()
                
            .state("SHIPPED")
                .done()
                
            .state("CANCELLED")
                .done()
                
            .build();
    }
    
    /**
     * Create a factory for use with StateMachineRegistry that properly handles rehydration
     */
    public static Supplier<GenericStateMachine<OrderPersistentContext, OrderVolatileContext>> 
            createRehydrationFactory(String orderId) {
        
        return () -> {
            // Create the machine structure
            GenericStateMachine<OrderPersistentContext, OrderVolatileContext> machine = 
                FluentStateMachineBuilder.<OrderPersistentContext, OrderVolatileContext>create(orderId)
                    .initialState("PENDING")
                    
                    .state("PENDING")
                        .on(OrderPlaced.class).to("AWAITING_PAYMENT")
                        .done()
                        
                    .state("AWAITING_PAYMENT")
                        .timeout(24 * 60, com.telcobright.statemachine.timeout.TimeUnit.MINUTES, "CANCELLED") // 24 hours
                        .on(PaymentReceived.class).to("PROCESSING")
                        .done()
                        
                    .state("PROCESSING")
                        .offline()
                        .on(OrderShipped.class).to("SHIPPED")
                        .done()
                        
                    .state("SHIPPED")
                        .done()
                        
                    .state("CANCELLED")
                        .done()
                        
                    .build();
            
            // Set up rehydration callback to recreate volatile context
            machine.setOnRehydration(() -> {
                OrderPersistentContext persistent = machine.getPersistingEntity();
                if (persistent != null) {
                    // Recreate volatile context from persistent data
                    OrderVolatileContext volatileCtx = OrderVolatileContext.createFromPersistent(persistent);
                    machine.setContext(volatileCtx);
                    System.out.println("[Rehydration] Volatile context recreated for order: " + orderId);
                }
            });
            
            return machine;
        };
    }
    
    /**
     * Example usage
     */
    public static void main(String[] args) {
        // Create a registry
        StateMachineRegistry registry = new StateMachineRegistry();
        
        String orderId = "ORDER-12345";
        
        // Register the machine with the factory for rehydration support
        GenericStateMachine<OrderPersistentContext, OrderVolatileContext> machine = 
            registry.createOrGet(orderId, createRehydrationFactory(orderId));
        
        // Start the machine
        machine.start();
        
        // Process order
        machine.fire(new OrderPlaced("CUST-001", 99.99));
        System.out.println("Order state: " + machine.getCurrentState()); // AWAITING_PAYMENT
        
        // Access contexts
        OrderPersistentContext persistent = machine.getPersistingEntity();
        OrderVolatileContext volatile_ = machine.getContext();
        
        System.out.println("Persistent - Order ID: " + persistent.getOrderId());
        System.out.println("Persistent - Customer: " + persistent.getCustomerId());
        System.out.println("Volatile - Processing time: " + volatile_.getProcessingDuration() + "ms");
        System.out.println("Volatile - Cache size: " + volatile_.getRuntimeCache().size());
        
        // Simulate payment
        machine.fire(new PaymentReceived("TXN-98765"));
        System.out.println("Order state: " + machine.getCurrentState()); // PROCESSING
        
        // The machine is now in PROCESSING (offline) state
        // It would be evicted from memory but persistent context is saved
        
        // Later, when we need to ship...
        // The registry would rehydrate the machine, recreating the volatile context
        GenericStateMachine<OrderPersistentContext, OrderVolatileContext> rehydratedMachine = 
            registry.createOrGet(orderId, createRehydrationFactory(orderId));
        
        // The volatile context is recreated, persistent context is loaded
        rehydratedMachine.fire(new OrderShipped("TRACK-54321"));
        System.out.println("Final state: " + rehydratedMachine.getCurrentState()); // SHIPPED
        
        // Cleanup
        if (rehydratedMachine.getContext() != null) {
            rehydratedMachine.getContext().cleanup();
        }
    }
}