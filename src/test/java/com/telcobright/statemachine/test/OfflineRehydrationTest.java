package com.telcobright.statemachine.test;

import com.telcobright.statemachine.FluentStateMachineBuilder;
import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.StateMachineRegistry;
import com.telcobright.statemachine.events.EventTypeRegistry;
import com.telcobright.statemachineexamples.callmachine.CallContext;
import com.telcobright.statemachineexamples.callmachine.CallState;
import com.telcobright.statemachineexamples.callmachine.entity.CallEntity;
import com.telcobright.statemachineexamples.callmachine.events.Answer;
import com.telcobright.statemachineexamples.callmachine.events.Hangup;
import com.telcobright.statemachineexamples.callmachine.events.IncomingCall;
import com.telcobright.statemachineexamples.callmachine.events.SessionProgress;
import com.telcobright.statemachineexamples.callmachine.states.ringing.OnSessionProgress_Ringing;

import java.util.HashMap;
import java.util.Map;

/**
 * Test for offline state machine behavior and rehydration
 * 
 * This test verifies:
 * 1. State machine can be sent offline when reaching an offline state
 * 2. State is persisted when going offline
 * 3. Machine can be rehydrated with the correct state
 * 4. Rehydrated machine continues from where it left off
 */
public class OfflineRehydrationTest {
    
    // Simulated persistence storage
    private static final Map<String, CallEntity> persistedEntities = new HashMap<>();
    
    public static void main(String[] args) throws Exception {
        System.out.println("🧪 OFFLINE/REHYDRATION TEST");
        System.out.println("═".repeat(60));
        
        // Register event types
        EventTypeRegistry.register(IncomingCall.class, "INCOMING_CALL");
        EventTypeRegistry.register(SessionProgress.class, "SESSION_PROGRESS");
        EventTypeRegistry.register(Answer.class, "ANSWER");
        EventTypeRegistry.register(Hangup.class, "HANGUP");
        
        String machineId = "call-" + System.currentTimeMillis();
        
        // Phase 1: Create and run machine until it goes offline
        System.out.println("\n📞 PHASE 1: Initial Call Flow");
        System.out.println("-".repeat(40));
        
        StateMachineRegistry registry = new StateMachineRegistry();
        
        // Create the initial machine
        GenericStateMachine<CallEntity, CallContext> machine = createCallMachine(machineId);
        registry.register(machineId, machine);
        
        // Set up initial entity and context
        CallEntity entity = new CallEntity(machineId, CallState.IDLE.name(), "+1-555-1234", "+1-555-5678");
        CallContext context = new CallContext();
        context.setCallId(machineId);
        context.setFromNumber("+1-555-1234");
        context.setToNumber("+1-555-5678");
        
        machine.setPersistingEntity(entity);
        machine.setContext(context);
        machine.start();
        
        System.out.println("✅ Machine started in state: " + machine.getCurrentState());
        
        // Send IncomingCall event - should transition to RINGING (offline state)
        System.out.println("\n📨 Sending INCOMING_CALL event...");
        IncomingCall incomingCall = new IncomingCall("+1-555-1234");
        machine.fire(incomingCall);
        
        System.out.println("📍 State after event: " + machine.getCurrentState());
        
        // Verify machine is in RINGING state (which is marked as offline)
        if (!CallState.RINGING.name().equals(machine.getCurrentState())) {
            System.err.println("❌ ERROR: Expected RINGING state, got: " + machine.getCurrentState());
            return;
        }
        
        // Simulate going offline - persist and remove from registry
        System.out.println("\n💾 Simulating offline transition...");
        persistEntity(entity);
        registry.removeMachine(machineId);
        
        System.out.println("✅ Machine removed from registry (went offline)");
        System.out.println("   Persisted state: " + entity.getCurrentState());
        System.out.println("   Is in memory: " + registry.isInMemory(machineId));
        
        // Clear references to simulate complete offline
        machine = null;
        entity = null;
        context = null;
        
        // Phase 2: Rehydrate the machine
        System.out.println("\n🔄 PHASE 2: Rehydration");
        System.out.println("-".repeat(40));
        
        Thread.sleep(1000); // Simulate some time passing
        
        // Load persisted entity
        CallEntity rehydratedEntity = loadEntity(machineId);
        if (rehydratedEntity == null) {
            System.err.println("❌ ERROR: Failed to load persisted entity");
            return;
        }
        
        System.out.println("📂 Loaded persisted entity:");
        System.out.println("   State: " + rehydratedEntity.getCurrentState());
        System.out.println("   Call ID: " + rehydratedEntity.getCallId());
        
        // Recreate the machine with the same configuration
        GenericStateMachine<CallEntity, CallContext> rehydratedMachine = createCallMachine(machineId);
        registry.register(machineId, rehydratedMachine);
        
        // Restore context
        CallContext rehydratedContext = new CallContext();
        rehydratedContext.setCallId(machineId);
        rehydratedContext.setFromNumber(rehydratedEntity.getFromNumber());
        rehydratedContext.setToNumber(rehydratedEntity.getToNumber());
        
        // Set the persisted entity and context
        rehydratedMachine.setPersistingEntity(rehydratedEntity);
        rehydratedMachine.setContext(rehydratedContext);
        
        // IMPORTANT: Set the current state from persisted entity
        rehydratedMachine.restoreState(rehydratedEntity.getCurrentState());
        
        System.out.println("✅ Machine rehydrated in state: " + rehydratedMachine.getCurrentState());
        
        // Phase 3: Continue processing from where we left off
        System.out.println("\n📞 PHASE 3: Continue Call Flow");
        System.out.println("-".repeat(40));
        
        // Send Answer event - should transition from RINGING to CONNECTED
        System.out.println("\n📨 Sending ANSWER event...");
        Answer answer = new Answer();
        rehydratedMachine.fire(answer);
        
        System.out.println("📍 State after answer: " + rehydratedMachine.getCurrentState());
        
        if (!CallState.CONNECTED.name().equals(rehydratedMachine.getCurrentState())) {
            System.err.println("❌ ERROR: Expected CONNECTED state, got: " + rehydratedMachine.getCurrentState());
            return;
        }
        
        // Send Hangup event - should transition to IDLE
        System.out.println("\n📨 Sending HANGUP event...");
        Hangup hangup = new Hangup();
        rehydratedMachine.fire(hangup);
        
        System.out.println("📍 Final state: " + rehydratedMachine.getCurrentState());
        
        // Verify final state
        if (!CallState.IDLE.name().equals(rehydratedMachine.getCurrentState())) {
            System.err.println("❌ ERROR: Expected IDLE state, got: " + rehydratedMachine.getCurrentState());
            return;
        }
        
        // Cleanup
        registry.shutdown();
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("✅ OFFLINE/REHYDRATION TEST PASSED!");
        System.out.println("   • Machine successfully went offline in RINGING state");
        System.out.println("   • State was persisted correctly");
        System.out.println("   • Machine was rehydrated with correct state");
        System.out.println("   • Call flow continued successfully after rehydration");
        System.out.println("   • Final state transition completed as expected");
    }
    
    /**
     * Create a call machine with offline state configuration
     */
    private static GenericStateMachine<CallEntity, CallContext> createCallMachine(String machineId) {
        return FluentStateMachineBuilder.<CallEntity, CallContext>create(machineId)
            .initialState(CallState.IDLE)
            
            .state(CallState.IDLE)
                .on(IncomingCall.class).to(CallState.RINGING)
                .on(Hangup.class).to(CallState.IDLE)
                .done()
                
            .state(CallState.RINGING)
                .offline()  // Mark as offline state
                .on(Answer.class).to(CallState.CONNECTED)
                .on(Hangup.class).to(CallState.IDLE)
                .stay(SessionProgress.class, OnSessionProgress_Ringing::handle)
                .done()
                
            .state(CallState.CONNECTED)
                .on(Hangup.class).to(CallState.IDLE)
                .done()
                
            .build();
    }
    
    /**
     * Simulate persisting entity to storage
     */
    private static void persistEntity(CallEntity entity) {
        // In real implementation, this would persist to database
        // For testing, we use an in-memory map
        CallEntity cloned = new CallEntity(
            entity.getCallId(),
            entity.getCurrentState(),
            entity.getFromNumber(),
            entity.getToNumber()
        );
        cloned.setCallStatus(entity.getCallStatus());
        persistedEntities.put(entity.getCallId(), cloned);
        System.out.println("   💾 Entity persisted to storage");
    }
    
    /**
     * Simulate loading entity from storage
     */
    private static CallEntity loadEntity(String machineId) {
        // In real implementation, this would load from database
        CallEntity entity = persistedEntities.get(machineId);
        if (entity != null) {
            System.out.println("   📂 Entity loaded from storage");
        }
        return entity;
    }
}