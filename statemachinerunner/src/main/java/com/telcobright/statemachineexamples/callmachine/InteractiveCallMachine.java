package com.telcobright.statemachineexamples.callmachine;

import com.telcobright.statemachine.FluentStateMachineBuilder;
import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.events.TimeoutEvent;
import com.telcobright.statemachineexamples.callmachine.entity.CallEntity;
import com.telcobright.statemachineexamples.callmachine.events.IncomingCall;
import com.telcobright.statemachineexamples.callmachine.events.Answer;
import com.telcobright.statemachineexamples.callmachine.events.Hangup;
import com.telcobright.statemachineexamples.callmachine.events.SessionProgress;
import com.telcobright.statemachineexamples.callmachine.states.ringing.OnSessionProgress_Ringing;
import com.telcobright.statemachine.timeout.TimeUnit;

/**
 * Interactive CallMachine with timeout support
 * 
 * Features:
 * - 30-second timeouts for each state
 * - Interactive event sending via keypress
 * - Timeout handling for missed calls and automatic hangup
 */
public class InteractiveCallMachine {
    
    /**
     * Create an interactive CallMachine with timeouts
     * 
     * @param machineId Unique identifier for the call machine instance
     * @return Configured CallMachine with timeout support
     */
    public static GenericStateMachine<CallEntity, CallContext> create(String machineId) {
        return FluentStateMachineBuilder.<CallEntity, CallContext>create(machineId)
            // Set initial state
            .initialState(CallState.IDLE)
            
            // IDLE state - waiting for incoming calls (30 sec timeout)
            .state(CallState.IDLE)
                .timeout(30, TimeUnit.SECONDS, CallState.IDLE.name()) // Stay in IDLE on timeout
                .on(IncomingCall.class).to(CallState.RINGING)
                .done()
                
            // RINGING state - call is being alerted (30 sec timeout)
            .state(CallState.RINGING)
                .offline()  // Offline state for persistence
                .timeout(30, TimeUnit.SECONDS, CallState.IDLE.name()) // Missed call timeout to IDLE
                .on(Answer.class).to(CallState.CONNECTED)
                .on(Hangup.class).to(CallState.IDLE)
                .stay(SessionProgress.class, OnSessionProgress_Ringing::handle)
                .done()
                
            // CONNECTED state - active call (30 sec timeout for demo)
            .state(CallState.CONNECTED)
                .timeout(30, TimeUnit.SECONDS, CallState.IDLE.name()) // Auto hangup on timeout to IDLE
                .on(Hangup.class).to(CallState.IDLE)
                .done()
                
            .build();
    }
    
    /**
     * Create CallMachine with custom timeout configuration
     */
    public static GenericStateMachine<CallEntity, CallContext> createWithCustomTimeout(String machineId, long timeoutSeconds) {
        return FluentStateMachineBuilder.<CallEntity, CallContext>create(machineId)
            .initialState(CallState.IDLE)
            
            .state(CallState.IDLE)
                .timeout(timeoutSeconds, TimeUnit.SECONDS, CallState.IDLE.name())
                .on(IncomingCall.class).to(CallState.RINGING)
                .done()
                
            .state(CallState.RINGING)
                .offline()
                .timeout(timeoutSeconds, TimeUnit.SECONDS, CallState.IDLE.name())
                .on(Answer.class).to(CallState.CONNECTED)
                .on(Hangup.class).to(CallState.IDLE)
                .stay(SessionProgress.class, OnSessionProgress_Ringing::handle)
                .done()
                
            .state(CallState.CONNECTED)
                .timeout(timeoutSeconds, TimeUnit.SECONDS, CallState.IDLE.name())
                .on(Hangup.class).to(CallState.IDLE)
                .done()
                
            .build();
    }
}