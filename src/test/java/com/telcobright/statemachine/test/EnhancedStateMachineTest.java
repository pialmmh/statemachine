package com.telcobright.statemachine.test;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.telcobright.statemachine.StateMachineFactory;
import com.telcobright.statemachine.StateMachineWrapper;
import com.telcobright.statemachine.events.GenericStateMachineEvent;
import com.telcobright.statemachine.state.EnhancedStateConfig;
import com.telcobright.statemachine.timeout.TimeUnit;
import com.telcobright.statemachine.timeout.TimeoutConfig;

/**
 * Comprehensive tests for the enhanced state machine functionality
 */
public class EnhancedStateMachineTest {
    
    private StateMachineWrapper stateMachine;
    
    @BeforeEach
    public void setUp() {
        // Clean start for each test
        StateMachineFactory.shutdown();
    }
    
    @AfterEach
    public void tearDown() {
        StateMachineFactory.shutdown();
    }
    
    @Test
    public void testBasicStateTransitions() {
        stateMachine = StateMachineFactory.createWrapper("test-machine-1")
            .initialState("IDLE")
            .state("IDLE")
            .state("ACTIVE")
            .state("DONE")
            .transition("IDLE", "START", "ACTIVE")
            .transition("ACTIVE", "FINISH", "DONE")
            .buildAndStart();
        
        assertEquals("IDLE", stateMachine.getCurrentState());
        
        stateMachine.fire("START");
        assertEquals("ACTIVE", stateMachine.getCurrentState());
        
        stateMachine.fire("FINISH");
        assertEquals("DONE", stateMachine.getCurrentState());
    }
    
    @Test
    public void testEntryAndExitActions() {
        final StringBuilder log = new StringBuilder();
        
        stateMachine = StateMachineFactory.createWrapper("test-machine-2")
            .initialState("STATE_A")
            .state("STATE_A", 
                () -> log.append("ENTER_A "),
                () -> log.append("EXIT_A "))
            .state("STATE_B",
                () -> log.append("ENTER_B "),
                () -> log.append("EXIT_B "))
            .transition("STATE_A", "GO_TO_B", "STATE_B")
            .transition("STATE_B", "GO_TO_A", "STATE_A")
            .buildAndStart();
        
        // Initial state should trigger entry action
        assertTrue(log.toString().contains("ENTER_A"));
        
        // Transition should trigger exit and entry actions
        stateMachine.fire("GO_TO_B");
        assertTrue(log.toString().contains("EXIT_A"));
        assertTrue(log.toString().contains("ENTER_B"));
        
        assertEquals("STATE_B", stateMachine.getCurrentState());
    }
    
    @Test
    public void testOfflineStates() {
        final boolean[] offlineCallbackCalled = {false};
        
        stateMachine = StateMachineFactory.createWrapper("test-machine-3")
            .initialState("ONLINE")
            .state("ONLINE")
            .offlineState("OFFLINE")
            .transition("ONLINE", "GO_OFFLINE", "OFFLINE")
            .onOfflineTransition(machine -> {
                offlineCallbackCalled[0] = true;
                assertEquals("OFFLINE", machine.getCurrentState());
            })
            .buildAndStart();
        
        assertEquals("ONLINE", stateMachine.getCurrentState());
        assertTrue(StateMachineFactory.getDefaultRegistry().isActive("test-machine-3"));
        
        stateMachine.fire("GO_OFFLINE");
        
        assertEquals("OFFLINE", stateMachine.getCurrentState());
        assertTrue(offlineCallbackCalled[0]);
        
        // Machine should be removed from active registry
        assertFalse(StateMachineFactory.getDefaultRegistry().isActive("test-machine-3"));
    }
    
    @Test
    public void testStateTransitionCallbacks() {
        final StringBuilder transitionLog = new StringBuilder();
        
        stateMachine = StateMachineFactory.createWrapper("test-machine-4")
            .initialState("START")
            .state("START")
            .state("MIDDLE")
            .state("END")
            .transition("START", "NEXT", "MIDDLE")
            .transition("MIDDLE", "NEXT", "END")
            .onStateTransition(state -> transitionLog.append(state).append(" "))
            .buildAndStart();
        
        stateMachine.fire("NEXT");
        stateMachine.fire("NEXT");
        
        String log = transitionLog.toString();
        assertTrue(log.contains("MIDDLE"));
        assertTrue(log.contains("END"));
        assertEquals("END", stateMachine.getCurrentState());
    }
    
    @Test
    public void testRegistryCreateOrGet() {
        // First creation
        StateMachineWrapper machine1 = StateMachineFactory.createWrapper("registry-test-1")
            .initialState("INITIAL")
            .state("INITIAL")
            .state("FINAL")
            .transition("INITIAL", "COMPLETE", "FINAL")
            .buildAndStart();
        
        machine1.fire("COMPLETE");
        assertEquals("FINAL", machine1.getCurrentState());
        
        // Second retrieval should get the same instance
        StateMachineWrapper machine2 = StateMachineFactory.createWrapper("registry-test-1");
        assertEquals("FINAL", machine2.getCurrentState());
        assertEquals(machine1.getId(), machine2.getId());
    }
    
    @Test
    public void testManualEviction() {
        StateMachineWrapper machine = StateMachineFactory.createWrapper("eviction-test")
            .initialState("RUNNING")
            .state("RUNNING")
            .buildAndStart();
        
        assertTrue(StateMachineFactory.getDefaultRegistry().isActive("eviction-test"));
        assertEquals("RUNNING", machine.getCurrentState());
        
        // Manually evict
        StateMachineFactory.getDefaultRegistry().evict("eviction-test");
        
        assertFalse(StateMachineFactory.getDefaultRegistry().isActive("eviction-test"));
    }
    
    @Test
    public void testContextSupport() {
        stateMachine = StateMachineFactory.createWrapper("context-test")
            .initialState("INIT")
            .state("INIT")
            .buildAndStart();
        
        assertNull(stateMachine.getContext());
        
        stateMachine.setContext("Test Context");
        assertEquals("Test Context", stateMachine.getContext());
    }
    
    @Test
    public void testComplexConfiguration() {
        stateMachine = StateMachineFactory.createWrapper("complex-test")
            .initialState("IDLE")
            .state("IDLE", new EnhancedStateConfig("IDLE")
                .onEntry(() -> System.out.println("Entering IDLE"))
                .onExit(() -> System.out.println("Exiting IDLE")))
            .state("PROCESSING", new EnhancedStateConfig("PROCESSING")
                .timeout(new TimeoutConfig(5, TimeUnit.SECONDS, "TIMEOUT"))
                .onEntry(() -> System.out.println("Starting processing"))
                .onExit(() -> System.out.println("Processing complete")))
            .state("COMPLETED")
            .offlineState("TIMEOUT")
            .transition("IDLE", "START", "PROCESSING")
            .transition("PROCESSING", "DONE", "COMPLETED")
            .transition("PROCESSING", "TIMEOUT", "TIMEOUT")
            .buildAndStart();
        
        assertEquals("IDLE", stateMachine.getCurrentState());
        
        stateMachine.fire("START");
        assertEquals("PROCESSING", stateMachine.getCurrentState());
        
        stateMachine.fire("DONE");
        assertEquals("COMPLETED", stateMachine.getCurrentState());
    }
    
    @Test
    public void testEventTypes() {
        stateMachine = StateMachineFactory.createWrapper("event-test")
            .initialState("WAITING")
            .state("WAITING")
            .state("RECEIVED")
            .transition("WAITING", "MESSAGE", "RECEIVED")
            .buildAndStart();
        
        // Test string event
        stateMachine.fire("MESSAGE");
        assertEquals("RECEIVED", stateMachine.getCurrentState());
        
        // Reset for next test
        stateMachine.transitionTo("WAITING");
        
        // Test GenericStateMachineEvent
        stateMachine.fire(new GenericStateMachineEvent("MESSAGE", "payload"));
        assertEquals("RECEIVED", stateMachine.getCurrentState());
    }
}
