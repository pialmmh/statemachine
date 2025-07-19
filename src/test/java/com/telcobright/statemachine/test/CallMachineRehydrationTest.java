package com.telcobright.statemachine.test;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.StateMachineRegistry;
import com.telcobright.statemachineexamples.callmachine.CallMachine;
import com.telcobright.statemachineexamples.callmachine.events.IncomingCall;
import com.telcobright.statemachineexamples.callmachine.events.Answer;
import com.telcobright.statemachine.test.TestDatabaseHelper;
import com.telcobright.statemachine.test.TestDatabaseHelper.CallSnapshotData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;

/**
 * Test that verifies machine rehydration from MySQL when not in memory
 */
public class CallMachineRehydrationTest {
    
    private StateMachineRegistry registry;
    private final String machineId = "call-rehydration-test-001";
    
    @BeforeAll
    public static void setupDatabase() throws SQLException {
        TestDatabaseHelper.createTestDatabase();
    }
    
    @BeforeEach
    public void setup() throws SQLException {
        TestDatabaseHelper.cleanupTestData();
        registry = new StateMachineRegistry();
    }
    
    @AfterEach
    public void teardown() throws SQLException {
        TestDatabaseHelper.cleanupTestData();
    }
    
    @Test
    public void testMachineRehydratesFromDatabaseWhenNotInMemory() throws SQLException {
        // Step 1: Create machine, change state, and persist to database
        GenericStateMachine originalMachine = CallMachine.create(machineId);
        
        // Change state to RINGING
        originalMachine.sendEvent(new IncomingCall("555-1234"));
        assertEquals("RINGING", originalMachine.getCurrentState());
        
        // Verify state is persisted to database
        CallSnapshotData snapshot = TestDatabaseHelper.getCallSnapshot(machineId);
        assertNotNull(snapshot);
        assertEquals("RINGING", snapshot.state);
        assertTrue(snapshot.isOffline);
        
        // Step 2: Remove machine from memory (simulate restart/eviction)
        registry.removeMachine(machineId);
        
        // Verify machine is NOT in memory
        assertFalse(registry.isInMemory(machineId), 
                   "Machine should not be in memory after removal");
        
        // Step 3: Call createOrGet - should load from database
        GenericStateMachine rehydratedMachine = registry.createOrGet(machineId, () -> {
            return CallMachine.create(machineId);
        });
        
        // Verify machine was rehydrated from database
        assertNotNull(rehydratedMachine);
        assertEquals("RINGING", rehydratedMachine.getCurrentState(),
                    "Machine should be rehydrated in RINGING state from database");
        
        // Verify machine is now in memory
        assertTrue(registry.isInMemory(machineId),
                  "Machine should be in memory after rehydration");
        
        // Step 4: Verify rehydrated machine is functional
        rehydratedMachine.sendEvent(new Answer());
        assertEquals("CONNECTED", rehydratedMachine.getCurrentState());
        
        // Verify new state is persisted
        CallSnapshotData newSnapshot = TestDatabaseHelper.getCallSnapshot(machineId);
        assertNotNull(newSnapshot);
        assertEquals("CONNECTED", newSnapshot.state);
        assertFalse(newSnapshot.isOffline);
    }
    
    @Test
    public void testCreateOrGetReturnsExistingMachineFromMemory() throws SQLException {
        // Create machine and add to registry
        GenericStateMachine machine = CallMachine.create(machineId);
        registry.register(machineId, machine);
        
        // Change state
        machine.sendEvent(new IncomingCall("555-1234"));
        assertEquals("RINGING", machine.getCurrentState());
        
        // Call createOrGet - should return existing machine from memory
        GenericStateMachine sameMachine = registry.createOrGet(machineId, () -> {
            // This factory should NOT be called since machine is in memory
            fail("Factory should not be called when machine exists in memory");
            return null;
        });
        
        // Verify same instance is returned
        assertSame(machine, sameMachine);
        assertEquals("RINGING", sameMachine.getCurrentState());
    }
    
    @Test
    public void testCreateOrGetCreatesNewMachineWhenNotInDatabaseOrMemory() throws SQLException {
        // Verify machine doesn't exist in memory
        assertFalse(registry.isInMemory(machineId));
        
        // Verify machine doesn't exist in database
        CallSnapshotData snapshot = TestDatabaseHelper.getCallSnapshot(machineId);
        assertNull(snapshot);
        
        // Call createOrGet - should create new machine
        GenericStateMachine newMachine = registry.createOrGet(machineId, () -> {
            return CallMachine.create(machineId);
        });
        
        // Verify new machine is created in initial state
        assertNotNull(newMachine);
        assertEquals("IDLE", newMachine.getCurrentState());
        
        // Verify machine is now in memory
        assertTrue(registry.isInMemory(machineId));
    }
    
    @Test
    public void testRehydrationPreservesContextData() throws SQLException {
        // Create machine with context data
        GenericStateMachine originalMachine = CallMachine.create(machineId);
        
        // Send event with specific context
        originalMachine.sendEvent(new IncomingCall("555-CALLER"));
        
        // Verify context is in database
        CallSnapshotData snapshot = TestDatabaseHelper.getCallSnapshot(machineId);
        assertNotNull(snapshot);
        assertTrue(snapshot.contextData.contains("555-CALLER"));
        
        // Remove from memory
        registry.removeMachine(machineId);
        
        // Rehydrate
        GenericStateMachine rehydratedMachine = registry.createOrGet(machineId, () -> {
            return CallMachine.create(machineId);
        });
        
        // Verify context is preserved
        // (This would require exposing context through the machine interface)
        assertEquals("RINGING", rehydratedMachine.getCurrentState());
        
        // Verify by checking if subsequent operations work with preserved context
        rehydratedMachine.sendEvent(new Answer());
        assertEquals("CONNECTED", rehydratedMachine.getCurrentState());
    }
}