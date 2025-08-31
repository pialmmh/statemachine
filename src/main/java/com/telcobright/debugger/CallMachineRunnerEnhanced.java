package com.telcobright.debugger;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.events.EventTypeRegistry;
import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachine.events.GenericStateMachineEvent;
import com.telcobright.examples.callmachine.CallState;
import com.telcobright.examples.callmachine.events.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * Enhanced CallMachine runner using proper context separation pattern.
 * Demonstrates best practice with separate persistent and volatile contexts.
 */
public class CallMachineRunnerEnhanced {
    
    // Hardcoded port numbers
    private static final int WS_PORT = 9999;
    
    private final StateMachineRegistry registry;
    private final Map<String, GenericStateMachine<CallPersistentContext, CallVolatileContext>> machines = new ConcurrentHashMap<>();
    private final com.telcobright.statemachine.timeout.TimeoutManager timeoutManager;
    
    /**
     * Persistent context - saved to database
     */
    public static class CallPersistentContext implements StateMachineContextEntity<String> {
        private String callId;
        private String callerId;
        private String calleeId;
        private LocalDateTime callStartTime;
        private LocalDateTime callEndTime;
        private int ringCount;
        private long ringDuration;
        private String currentState;
        private LocalDateTime lastStateChange;
        private boolean complete;
        private double billingAmount;
        private String callQuality;
        
        public CallPersistentContext(String callId, String callerId, String calleeId) {
            this.callId = callId;
            this.callerId = callerId;
            this.calleeId = calleeId;
            setCurrentState(CallState.ADMISSION.name());
            setLastStateChange(LocalDateTime.now());
            setComplete(false);
            this.ringCount = 0;
            this.billingAmount = 0.0;
        }
        
        // Getters and setters
        public String getCallId() { return callId; }
        public String getCallerId() { return callerId; }
        public String getCalleeId() { return calleeId; }
        public LocalDateTime getCallStartTime() { return callStartTime; }
        public void setCallStartTime(LocalDateTime time) { this.callStartTime = time; }
        public LocalDateTime getCallEndTime() { return callEndTime; }
        public void setCallEndTime(LocalDateTime time) { this.callEndTime = time; }
        public int getRingCount() { return ringCount; }
        public void setRingCount(int count) { this.ringCount = count; }
        public long getRingDuration() { return ringDuration; }
        public void setRingDuration(long duration) { this.ringDuration = duration; }
        public double getBillingAmount() { return billingAmount; }
        public void setBillingAmount(double amount) { this.billingAmount = amount; }
        public String getCallQuality() { return callQuality; }
        public void setCallQuality(String quality) { this.callQuality = quality; }
        
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
        public void setLastStateChange(LocalDateTime time) { this.lastStateChange = time; }
        
        @Override
        public StateMachineContextEntity<String> deepCopy() {
            CallPersistentContext copy = new CallPersistentContext(callId, callerId, calleeId);
            copy.setCallStartTime(this.callStartTime);
            copy.setCallEndTime(this.callEndTime);
            copy.setRingCount(this.ringCount);
            copy.setRingDuration(this.ringDuration);
            copy.setCurrentState(this.getCurrentState());
            copy.setLastStateChange(this.getLastStateChange());
            copy.setComplete(this.isComplete());
            copy.setBillingAmount(this.billingAmount);
            copy.setCallQuality(this.callQuality);
            return copy;
        }
    }
    
    /**
     * Volatile context - NOT persisted, recreated on rehydration
     */
    public static class CallVolatileContext {
        private Map<String, Object> runtimeCache;
        private List<String> eventLog;
        private long sessionStartTime;
        private String sipSessionId;
        private String mediaServerEndpoint;
        private boolean isRecording;
        private Map<String, String> headers;
        
        public CallVolatileContext() {
            this.runtimeCache = new HashMap<>();
            this.eventLog = new ArrayList<>();
            this.sessionStartTime = System.currentTimeMillis();
            this.sipSessionId = "SIP-" + UUID.randomUUID().toString().substring(0, 8);
            this.mediaServerEndpoint = "media-server-" + (new Random().nextInt(5) + 1) + ".example.com";
            this.isRecording = false;
            this.headers = new HashMap<>();
            
            // Initialize some runtime data
            headers.put("User-Agent", "CallMachine/1.0");
            headers.put("Session-ID", sipSessionId);
            
            logEvent("Volatile context initialized");
        }
        
        /**
         * Factory method to recreate from persistent context during rehydration
         */
        public static CallVolatileContext createFromPersistent(CallPersistentContext persistent) {
            CallVolatileContext volatile_ = new CallVolatileContext();
            
            // Recreate runtime state based on persistent data
            volatile_.runtimeCache.put("callId", persistent.getCallId());
            volatile_.runtimeCache.put("state", persistent.getCurrentState());
            volatile_.runtimeCache.put("rehydrated", true);
            volatile_.runtimeCache.put("rehydrationTime", LocalDateTime.now());
            
            // Reinitialize session
            volatile_.logEvent("Context rehydrated for call: " + persistent.getCallId());
            volatile_.logEvent("Current state: " + persistent.getCurrentState());
            
            // If call is in CONNECTED state, recreate active session data
            if (CallState.CONNECTED.name().equals(persistent.getCurrentState())) {
                volatile_.isRecording = true;
                volatile_.logEvent("Call recording resumed");
            }
            
            System.out.println("[Volatile Context] Rehydrated for call: " + persistent.getCallId());
            
            return volatile_;
        }
        
        public void logEvent(String event) {
            eventLog.add(LocalDateTime.now() + " - " + event);
        }
        
        public void startRecording() {
            isRecording = true;
            logEvent("Recording started on " + mediaServerEndpoint);
        }
        
        public void stopRecording() {
            isRecording = false;
            logEvent("Recording stopped");
        }
        
        public long getSessionDuration() {
            return System.currentTimeMillis() - sessionStartTime;
        }
        
        // Getters
        public Map<String, Object> getRuntimeCache() { return runtimeCache; }
        public List<String> getEventLog() { return eventLog; }
        public String getSipSessionId() { return sipSessionId; }
        public String getMediaServerEndpoint() { return mediaServerEndpoint; }
        public boolean isRecording() { return isRecording; }
        public Map<String, String> getHeaders() { return headers; }
    }
    
    /**
     * Create runner with enhanced context separation
     */
    public CallMachineRunnerEnhanced() {
        // Create timeout manager for handling state timeouts
        this.timeoutManager = new com.telcobright.statemachine.timeout.TimeoutManager();
        
        // Create registry with unique ID, timeout manager and hardcoded WebSocket port
        this.registry = new StateMachineRegistry("call", timeoutManager, WS_PORT);
        
        // IMPORTANT: Set the factory's default instances BEFORE creating any state machines
        StateMachineFactory.setDefaultInstances(timeoutManager, registry);
        
        // Enable debug mode (combines WebSocket and history tracking)
        registry.enableDebugMode(WS_PORT);
        
        // Set optimized persistence provider for CallPersistentContext
        // Option 1: Auto-infer table name from class (CallPersistentContext -> call_persistent_context)
        registry.setOptimizedPersistenceProvider(CallPersistentContext.class);
        
        // Option 2: Specify custom table name explicitly
        // registry.setOptimizedPersistenceProvider(CallPersistentContext.class, "call_contexts");
        
        // Initialize state machines AFTER setting factory defaults
        initializeStateMachines();
    }
    
    private void initializeStateMachines() {
        System.out.println("\n[Init] Initializing enhanced state machines with proper context separation...");
        registerEventTypes();
        
        // Create 3 CallMachine instances with enhanced context
        createAndRegisterMachine("call-001", "+1-555-0001", "+1-555-1001");
        createAndRegisterMachine("call-002", "+1-555-0002", "+1-555-1002");
        createAndRegisterMachine("call-003", "+1-555-0003", "+1-555-1003");
        
        System.out.println("[Init] Created 3 enhanced CallMachine instances with separate contexts");
    }
    
    private void createAndRegisterMachine(String machineId, String caller, String callee) {
        // Create persistent context
        CallPersistentContext persistentContext = new CallPersistentContext(machineId, caller, callee);
        
        // Create volatile context
        CallVolatileContext volatileContext = new CallVolatileContext();
        volatileContext.logEvent("Machine initialized for " + caller + " -> " + callee);
        
        // Build machine using enhanced builder
        GenericStateMachine<CallPersistentContext, CallVolatileContext> machine = 
            EnhancedFluentBuilder.<CallPersistentContext, CallVolatileContext>create(machineId)
                .withPersistentContext(persistentContext)
                .withVolatileContext(volatileContext)
                .withVolatileContextFactory(() -> CallVolatileContext.createFromPersistent(persistentContext))
                .initialState(CallState.ADMISSION.name())
                
                .state(CallState.ADMISSION.name())
                    .onEntry(m -> {
                        String runId = m.getRunId() != null ? m.getRunId() : "unknown";
                        System.out.println("🔄 [ENTRY-" + runId + "] Entered ADMISSION state for " + m.getId());
                        CallVolatileContext vol = m.getContext();
                        if (vol != null) {
                            vol.logEvent("[ENTRY-" + runId + "] Entered ADMISSION state");
                        }
                        // IDLE is no longer a final state - just log entry
                        CallPersistentContext persistent = m.getPersistingEntity();
                        if (persistent != null) {
                            // Reset call start time when returning to IDLE (for reuse)
                            persistent.setCallStartTime(null);
                            persistent.setCallEndTime(null);
                        }
                    })
                    .onExit(m -> {
                        String runId = m.getRunId() != null ? m.getRunId() : "unknown";
                        System.out.println("🔄 [EXIT-" + runId + "] Exiting ADMISSION state for " + m.getId());
                        CallVolatileContext vol = m.getContext();
                        if (vol != null) {
                            vol.logEvent("[EXIT-" + runId + "] Exiting ADMISSION state");
                        }
                    })
                    .on(IncomingCall.class).to(CallState.RINGING.name())
                    .on(Hangup.class).to(CallState.HUNGUP.name())
                    .done()
                    
                .state(CallState.RINGING.name())
                    .onEntry(m -> {
                        String runId = m.getRunId() != null ? m.getRunId() : "unknown";
                        System.out.println("🔄 [ENTRY-" + runId + "] Entered RINGING state for " + m.getId());
                        CallVolatileContext vol = m.getContext();
                        if (vol != null) {
                            vol.logEvent("[ENTRY-" + runId + "] Started ringing");
                        }
                    })
                    .onExit(m -> {
                        String runId = m.getRunId() != null ? m.getRunId() : "unknown";
                        System.out.println("🔄 [EXIT-" + runId + "] Exiting RINGING state for " + m.getId());
                        CallVolatileContext vol = m.getContext();
                        if (vol != null) {
                            vol.logEvent("[EXIT-" + runId + "] Stopped ringing");
                        }
                    })
                    .timeout(30, com.telcobright.statemachine.timeout.TimeUnit.SECONDS, CallState.HUNGUP.name())
                    .on(Answer.class).to(CallState.CONNECTED.name())
                    .on(Hangup.class).to(CallState.HUNGUP.name())
                    .stay(SessionProgress.class, (m, e) -> {
                        CallPersistentContext ctx = m.getPersistingEntity();
                        CallVolatileContext vol = m.getContext();
                        ctx.setRingCount(ctx.getRingCount() + 1);
                        vol.logEvent("Ring #" + ctx.getRingCount());
                    })
                    .done()
                    
                .state(CallState.CONNECTED.name())
                    .onEntry(m -> {
                        String runId = m.getRunId() != null ? m.getRunId() : "unknown";
                        System.out.println("🔄 [ENTRY-" + runId + "] Entered CONNECTED state for " + m.getId());
                        handleConnectedEntry(m, runId);
                    })
                    .onExit(m -> {
                        String runId = m.getRunId() != null ? m.getRunId() : "unknown";
                        System.out.println("🔄 [EXIT-" + runId + "] Exiting CONNECTED state for " + m.getId());
                        CallVolatileContext vol = m.getContext();
                        if (vol != null) {
                            vol.logEvent("[EXIT-" + runId + "] Call disconnecting");
                        }
                    })
                    .offline() // Mark CONNECTED as offline for testing
                    .timeout(30, com.telcobright.statemachine.timeout.TimeUnit.SECONDS, CallState.HUNGUP.name())
                    .on(Hangup.class).to(CallState.HUNGUP.name())
                    .done()
                    
                .state(CallState.HUNGUP.name())
                    .onEntry(m -> {
                        String runId = m.getRunId() != null ? m.getRunId() : "unknown";
                        System.out.println("🔄 [ENTRY-" + runId + "] Entered HUNGUP final state for " + m.getId());
                        handleCallEnd(m, runId);
                        CallVolatileContext vol = m.getContext();
                        if (vol != null) {
                            vol.logEvent("[ENTRY-" + runId + "] Call ended - final state reached");
                        }
                    })
                    .finalState() // Mark as final state
                    .done()
                    
                .build();
        
        // Set up rehydration callback
        machine.setOnRehydration(() -> {
            System.out.println("[Rehydration] Machine " + machineId + " rehydrated");
            CallVolatileContext vol = machine.getContext();
            if (vol != null) {
                vol.logEvent("Machine rehydrated from persistence");
            }
        });
        
        // Register with the registry
        registry.register(machineId, machine);
        machine.start();
        
        // Store reference
        machines.put(machineId, machine);
        
        System.out.println("[Init] Registered enhanced CallMachine: " + machineId);
        System.out.println("  - Persistent context: " + persistentContext.getClass().getSimpleName());
        System.out.println("  - Volatile context: " + volatileContext.getClass().getSimpleName());
        System.out.println("  - SIP Session: " + volatileContext.getSipSessionId());
        System.out.println("  - Media Server: " + volatileContext.getMediaServerEndpoint());
    }
    
    private void handleConnectedEntry(GenericStateMachine<CallPersistentContext, CallVolatileContext> machine, String runId) {
        CallPersistentContext persistent = machine.getPersistingEntity();
        CallVolatileContext volatile_ = machine.getContext();
        
        if (persistent != null && volatile_ != null) {
            // Set call start time
            persistent.setCallStartTime(LocalDateTime.now());
            
            // Calculate ring duration
            long ringDuration = System.currentTimeMillis() - persistent.getLastStateChange().atZone(
                java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            persistent.setRingDuration(ringDuration);
            
            // Set call quality
            persistent.setCallQuality("HD");
            
            // Start recording in volatile context
            volatile_.startRecording();
            
            // Log connection details with runId
            System.out.println("📞 [CONNECTED-" + runId + "] Call established: " + persistent.getCallId());
            System.out.println("   - Caller: " + persistent.getCallerId());
            System.out.println("   - Callee: " + persistent.getCalleeId());
            System.out.println("   - Ring count: " + persistent.getRingCount());
            System.out.println("   - Ring duration: " + ringDuration + "ms");
            System.out.println("   - Quality: " + persistent.getCallQuality());
            System.out.println("   - Recording: " + volatile_.isRecording());
            System.out.println("   - Media server: " + volatile_.getMediaServerEndpoint());
            
            // Log to volatile context with runId
            volatile_.logEvent("[CONNECTED-" + runId + "] Call established with recording");
        }
    }
    
    private void handleCallEnd(GenericStateMachine<CallPersistentContext, CallVolatileContext> machine, String runId) {
        CallPersistentContext persistent = machine.getPersistingEntity();
        CallVolatileContext volatile_ = machine.getContext();
        
        if (persistent != null && volatile_ != null) {
            // Set call end time
            persistent.setCallEndTime(LocalDateTime.now());
            
            // Calculate billing if call was connected
            if (persistent.getCallStartTime() != null) {
                long durationSeconds = java.time.Duration.between(
                    persistent.getCallStartTime(), 
                    persistent.getCallEndTime()
                ).getSeconds();
                
                double billingRate = 0.05; // $0.05 per minute
                persistent.setBillingAmount(Math.ceil(durationSeconds / 60.0) * billingRate);
                
                System.out.println("📞 [ENDED-" + runId + "] Call completed: " + persistent.getCallId());
                System.out.println("   - Duration: " + durationSeconds + " seconds");
                System.out.println("   - Billing: $" + String.format("%.2f", persistent.getBillingAmount()));
            }
            
            // Stop recording
            if (volatile_.isRecording()) {
                volatile_.stopRecording();
            }
            
            // Log final event log with runId
            System.out.println("   - Event log entries: " + volatile_.getEventLog().size());
            System.out.println("   - Session duration: " + volatile_.getSessionDuration() + "ms");
            
            // Log to volatile context with runId  
            volatile_.logEvent("[ENDED-" + runId + "] Call finalized and cleaned up");
        }
    }
    
    private void registerEventTypes() {
        EventTypeRegistry.register(IncomingCall.class, "INCOMING_CALL");
        EventTypeRegistry.register(Answer.class, "ANSWER");
        EventTypeRegistry.register(Hangup.class, "HANGUP");
        EventTypeRegistry.register(SessionProgress.class, "SESSION_PROGRESS");
        EventTypeRegistry.register(Reject.class, "REJECT");
        System.out.println("[Init] Registered event types");
    }
    
    /**
     * Send event to a specific machine using registry with final state checking
     */
    public void sendEvent(String machineId, StateMachineEvent event) {
        // Use registry's sendEvent method which handles final state checking
        boolean sent = registry.sendEvent(machineId, event);
        
        if (sent) {
            // Log in volatile context if machine is still active
            GenericStateMachine<CallPersistentContext, CallVolatileContext> machine = machines.get(machineId);
            if (machine != null) {
                CallVolatileContext volatile_ = machine.getContext();
                if (volatile_ != null) {
                    volatile_.logEvent("Received event: " + event.getClass().getSimpleName());
                }
            }
        }
    }
    
    /**
     * Get machine state details
     */
    public void printMachineStatus(String machineId) {
        GenericStateMachine<CallPersistentContext, CallVolatileContext> machine = machines.get(machineId);
        if (machine != null) {
            CallPersistentContext persistent = machine.getPersistingEntity();
            CallVolatileContext volatile_ = machine.getContext();
            
            System.out.println("\n=== Machine Status: " + machineId + " ===");
            System.out.println("Persistent Context:");
            System.out.println("  - State: " + persistent.getCurrentState());
            System.out.println("  - Caller: " + persistent.getCallerId());
            System.out.println("  - Callee: " + persistent.getCalleeId());
            System.out.println("  - Ring count: " + persistent.getRingCount());
            System.out.println("  - Billing: $" + String.format("%.2f", persistent.getBillingAmount()));
            
            if (volatile_ != null) {
                System.out.println("Volatile Context:");
                System.out.println("  - SIP Session: " + volatile_.getSipSessionId());
                System.out.println("  - Recording: " + volatile_.isRecording());
                System.out.println("  - Session duration: " + volatile_.getSessionDuration() + "ms");
                System.out.println("  - Event log entries: " + volatile_.getEventLog().size());
                System.out.println("  - Cache entries: " + volatile_.getRuntimeCache().size());
            } else {
                System.out.println("Volatile Context: NOT LOADED (machine may be offline)");
            }
            System.out.println("=====================================\n");
        }
    }
    
    /**
     * Simulate machine eviction and rehydration
     */
    public void testRehydration(String machineId) {
        System.out.println("\n=== Testing Rehydration for " + machineId + " ===");
        
        // Print status before eviction
        printMachineStatus(machineId);
        
        // Evict the machine
        System.out.println("⚡ Evicting machine from memory...");
        registry.evict(machineId);
        
        // Wait a bit
        try { Thread.sleep(500); } catch (InterruptedException e) {}
        
        // Rehydrate by sending an event (this will trigger loading from persistence)
        System.out.println("🔄 Triggering rehydration with event...");
        sendEvent(machineId, new SessionProgress("ringing (progress 50%)"));
        
        // Print status after rehydration
        printMachineStatus(machineId);
    }
    
    /**
     * Shutdown the runner
     */
    public void shutdown() {
        System.out.println("\n[Shutdown] Shutting down enhanced runner...");
        
        // Print final status of all machines
        for (String machineId : machines.keySet()) {
            printMachineStatus(machineId);
        }
        
        // Shutdown registry
        registry.shutdown();
        
        System.out.println("[Shutdown] Complete");
    }
    
    public static void main(String[] args) {
        // Create and run with enhanced context separation
        CallMachineRunnerEnhanced runner = new CallMachineRunnerEnhanced();
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("   Enhanced CallMachine with Proper Context Separation");
        System.out.println("=".repeat(60));
        System.out.println("🔌 WebSocket API: ws://localhost:" + WS_PORT);
        System.out.println("=".repeat(60));
        System.out.println("");
        System.out.println("Features:");
        System.out.println("✅ Persistent context (CallPersistentContext) - saved to DB");
        System.out.println("✅ Volatile context (CallVolatileContext) - runtime only");
        System.out.println("✅ Automatic volatile context recreation on rehydration");
        System.out.println("✅ SIP session tracking and media server assignment");
        System.out.println("✅ Call recording and billing calculation");
        System.out.println("");
        System.out.println("To monitor the state machines:");
        System.out.println("1. Start the React UI: cd statemachine-ui-react && npm start");
        System.out.println("2. Open http://localhost:4001 in your browser");
        System.out.println("3. Click 'Live Viewer' to connect to this WebSocket");
        System.out.println("");
        System.out.println("Press Ctrl+C to stop\n");
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(runner::shutdown));
        
        // DEMO DISABLED - Control via UI only
        // To enable automatic demo, uncomment the following block:
        /*
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                System.out.println("\n=== Demo: Simulating call flow ===");
                
                // Make a call on machine call-001
                runner.sendEvent("call-001", new IncomingCall());
                Thread.sleep(2000);
                
                // Send ring progress
                runner.sendEvent("call-001", new SessionProgress("ringing", 30));
                Thread.sleep(1000);
                runner.sendEvent("call-001", new SessionProgress("ringing", 60));
                Thread.sleep(1000);
                
                // Answer the call
                runner.sendEvent("call-001", new Answer());
                Thread.sleep(3000);
                
                // Test rehydration while in CONNECTED state
                runner.testRehydration("call-001");
                Thread.sleep(2000);
                
                // Hang up
                runner.sendEvent("call-001", new Hangup());
                
                System.out.println("\n=== Demo complete ===\n");
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        */
        
        // TEST IGNORED EVENTS - Commented out to avoid cluttering the UI
        // Uncomment this block if you want to test ignored event tracking
        /*
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                System.out.println("\n=== Testing Ignored Event Tracking ===");
                
                // Test 1: Send DIAL event in ADMISSION state (no transition defined)
                System.out.println("Test 1: Sending DIAL event to call-001 in ADMISSION state (should be ignored)...");
                runner.sendEvent("call-001", new GenericStateMachineEvent("DIAL"));
                Thread.sleep(1000);
                
                // Test 2: Send undefined event type 
                System.out.println("Test 2: Sending undefined UNDEFINED_EVENT to call-002 (should be ignored)...");
                runner.sendEvent("call-002", new GenericStateMachineEvent("UNDEFINED_EVENT"));
                Thread.sleep(1000);
                
                // Test 3: Send REJECT event in ADMISSION state (no transition defined)
                System.out.println("Test 3: Sending REJECT event to call-003 in ADMISSION state (should be ignored)...");
                runner.sendEvent("call-003", new GenericStateMachineEvent("REJECT"));
                Thread.sleep(1000);
                
                System.out.println("\n=== Ignored Event Test Complete ===");
                System.out.println("Check the UI to see ignored events marked with 🚫 icon and '(ignored)' label\n");
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        */
        
        // Keep running
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            runner.shutdown();
        }
    }
}