package com.telcobright.statemachineexamples.callmachine;

import com.telcobright.statemachine.FluentStateMachineBuilder;
import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachine.persistence.IdLookUpMode;
import com.telcobright.statemachine.persistence.ShardingEntityStateMachineRepository;
import com.telcobright.statemachineexamples.callmachine.entity.CallEntity;
import com.telcobright.statemachineexamples.callmachine.events.IncomingCall;
import com.telcobright.statemachineexamples.callmachine.events.Answer;
import com.telcobright.statemachineexamples.callmachine.events.Hangup;
import com.telcobright.statemachineexamples.callmachine.events.SessionProgress;
import com.telcobright.statemachineexamples.callmachine.states.ringing.OnSessionProgress_Ringing;
import com.telcobright.db.PartitionedRepository;

/**
 * CallMachine example using ShardingEntity-based persistence with ById lookup mode
 * 
 * Features demonstrated:
 * - GenericStateMachine<CallEntity, CallContext> with ShardingEntity persistence
 * - CallEntity (persisted) vs CallContext (volatile)
 * - PartitionedRepository persistence with ById lookup
 * - State transitions and stay actions
 */
public class CallMachine {
    
    /**
     * Create CallMachine with PartitionedRepository persistence using ById lookup
     * 
     * @param machineId Unique identifier for the call machine instance
     * @param partitionedRepo Repository for state persistence
     * @return Configured CallMachine ready for use
     */
    public static GenericStateMachine<CallEntity, CallContext> create(String machineId, PartitionedRepository<CallEntity, String> partitionedRepo) {
        
        return FluentStateMachineBuilder.<CallEntity, CallContext>create(machineId)
            // Configure PartitionedRepository with ById lookup mode for ShardingEntity
            .withShardingRepo(partitionedRepo, IdLookUpMode.ById, CallEntity.class)
            
            // Set initial state
            .initialState(CallState.IDLE)
            
            // IDLE state - waiting for incoming calls
            .state(CallState.IDLE)
                .on(IncomingCall.class).to(CallState.RINGING)
                .done()
                
            // RINGING state - call is being alerted, with session progress events
            .state(CallState.RINGING)
                .offline()  // Offline state for persistence across restarts
                .on(Answer.class).to(CallState.CONNECTED)
                .on(Hangup.class).to(CallState.IDLE)
                .stay(SessionProgress.class, OnSessionProgress_Ringing::handle)
                .done()
                
            // CONNECTED state - active call in progress
            .state(CallState.CONNECTED)
                .on(Hangup.class).to(CallState.IDLE)
                .done()
                
            .build();
    }
    
    /**
     * Create CallMachine with default in-memory persistence (for testing)
     */
    public static GenericStateMachine<CallEntity, CallContext> create(String machineId) {
        return FluentStateMachineBuilder.<CallEntity, CallContext>create(machineId)
            .initialState(CallState.IDLE)
            
            .state(CallState.IDLE)
                .on(IncomingCall.class).to(CallState.RINGING)
                .done()
                
            .state(CallState.RINGING)
                .offline()
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
     * Convenience method to create CallMachine with context pre-configured
     */
    public static GenericStateMachine<CallEntity, CallContext> createWithContext(String machineId, CallContext context, 
                                              PartitionedRepository<CallEntity, String> partitionedRepo) {
        GenericStateMachine<CallEntity, CallContext> machine = create(machineId, partitionedRepo);
        machine.setContext(context);
        return machine;
    }
}