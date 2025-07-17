package com.telcobright.statemachine.test;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachineexamples.callmachine.CallMachine;
import com.telcobright.statemachineexamples.callmachine.events.IncomingCall;
import com.telcobright.statemachineexamples.callmachine.events.Answer;
import com.telcobright.statemachineexamples.callmachine.events.Hangup;
import com.telcobright.statemachine.test.TestDatabaseHelper;
import com.telcobright.statemachine.test.TestDatabaseHelper.CallSnapshotData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;

/**
 * Test that verifies each state transition is properly persisted to MySQL
 */
public class CallMachineStateTransitionPersistenceTest {
    
    private GenericStateMachine machine;
    private final String machineId = "call-transition-test-001";
    
    @BeforeAll
    public static void setupDatabase() throws SQLException {
        TestDatabaseHelper.createTestDatabase();
    }
    
    @BeforeEach
    public void setup() throws SQLException {
        TestDatabaseHelper.cleanupTestData();
        
        machine = new CallMachine(machineId, null, null, null);
        
        // Verify initial state
        assertEquals("IDLE", machine.getCurrentState());
    }
    
    @AfterEach
    public void teardown() throws SQLException {
        TestDatabaseHelper.cleanupTestData();
    }
    
    @Test
    public void testIdleToRingingTransitionPersistence() throws SQLException {
        // Trigger IDLE -> RINGING transition
        machine.sendEvent(new IncomingCall("555-1234"));
        
        // Verify machine state
        assertEquals("RINGING", machine.getCurrentState());
        
        // Verify database persistence
        CallSnapshotData snapshot = TestDatabaseHelper.getCallSnapshot(machineId);
        assertNotNull(snapshot);
        assertEquals(machineId, snapshot.machineId);
        assertEquals("RINGING", snapshot.state);
        assertTrue(snapshot.isOffline, "RINGING state should be offline");
        assertTrue(snapshot.contextData.contains("555-1234"));
    }
    
    @Test
    public void testRingingToConnectedTransitionPersistence() throws SQLException {
        // Setup: Go to RINGING state first
        machine.sendEvent(new IncomingCall("555-1234"));
        assertEquals("RINGING", machine.getCurrentState());
        
        // Trigger RINGING -> CONNECTED transition
        machine.sendEvent(new Answer());
        
        // Verify machine state
        assertEquals("CONNECTED", machine.getCurrentState());
        
        // Verify database persistence
        CallSnapshotData snapshot = TestDatabaseHelper.getCallSnapshot(machineId);
        assertNotNull(snapshot);
        assertEquals("CONNECTED", snapshot.state);
        assertFalse(snapshot.isOffline, "CONNECTED state should be online");
    }
    
    @Test
    public void testConnectedToIdleTransitionPersistence() throws SQLException {
        // Setup: Go to CONNECTED state
        machine.sendEvent(new IncomingCall("555-1234"));
        machine.sendEvent(new Answer());
        assertEquals("CONNECTED", machine.getCurrentState());
        
        // Trigger CONNECTED -> IDLE transition
        machine.sendEvent(new Hangup());
        
        // Verify machine state
        assertEquals("IDLE", machine.getCurrentState());
        
        // Verify database persistence
        CallSnapshotData snapshot = TestDatabaseHelper.getCallSnapshot(machineId);
        assertNotNull(snapshot);
        assertEquals("IDLE", snapshot.state);
        assertFalse(snapshot.isOffline, "IDLE state should be online");
    }
    
    @Test
    public void testRingingToIdleTransitionPersistence() throws SQLException {
        // Setup: Go to RINGING state
        machine.sendEvent(new IncomingCall("555-1234"));
        assertEquals("RINGING", machine.getCurrentState());
        
        // Trigger RINGING -> IDLE transition (hangup during ringing)
        machine.sendEvent(new Hangup());
        
        // Verify machine state
        assertEquals("IDLE", machine.getCurrentState());
        
        // Verify database persistence
        CallSnapshotData snapshot = TestDatabaseHelper.getCallSnapshot(machineId);
        assertNotNull(snapshot);
        assertEquals("IDLE", snapshot.state);
        assertFalse(snapshot.isOffline, "IDLE state should be online");
    }
    
    @Test
    public void testCompleteCallFlowPersistence() throws SQLException {
        java.sql.Timestamp startTime = new java.sql.Timestamp(System.currentTimeMillis());
        
        // Step 1: IDLE -> RINGING
        machine.sendEvent(new IncomingCall("555-1234"));
        CallSnapshotData snapshot1 = TestDatabaseHelper.getCallSnapshot(machineId);
        assertNotNull(snapshot1);
        assertEquals("RINGING", snapshot1.state);
        assertTrue(snapshot1.isOffline);
        assertTrue(snapshot1.timestamp.after(startTime));
        
        // Step 2: RINGING -> CONNECTED
        machine.sendEvent(new Answer());
        CallSnapshotData snapshot2 = TestDatabaseHelper.getCallSnapshot(machineId);
        assertNotNull(snapshot2);
        assertEquals("CONNECTED", snapshot2.state);
        assertFalse(snapshot2.isOffline);
        assertTrue(snapshot2.timestamp.after(snapshot1.timestamp));
        
        // Step 3: CONNECTED -> IDLE
        machine.sendEvent(new Hangup());
        CallSnapshotData snapshot3 = TestDatabaseHelper.getCallSnapshot(machineId);
        assertNotNull(snapshot3);
        assertEquals("IDLE", snapshot3.state);
        assertFalse(snapshot3.isOffline);
        assertTrue(snapshot3.timestamp.after(snapshot2.timestamp));
    }
    
    @Test
    public void testInvalidTransitionDoesNotUpdateDatabase() throws SQLException {
        // Start in IDLE state
        assertEquals("IDLE", machine.getCurrentState());
        
        // Get initial snapshot (should be null or IDLE)
        CallSnapshotData initialSnapshot = TestDatabaseHelper.getCallSnapshot(machineId);
        
        // Try invalid transition: IDLE -> Answer (should be rejected)
        machine.sendEvent(new Answer());
        
        // Verify machine state didn't change
        assertEquals("IDLE", machine.getCurrentState());
        
        // Verify database wasn't updated with invalid transition
        CallSnapshotData afterSnapshot = TestDatabaseHelper.getCallSnapshot(machineId);
        
        // Either no snapshot exists, or it's still IDLE
        if (afterSnapshot != null) {
            assertEquals("IDLE", afterSnapshot.state);
            
            // If initial snapshot existed, timestamps should be the same
            if (initialSnapshot != null) {
                assertEquals(initialSnapshot.timestamp, afterSnapshot.timestamp);
            }
        }
    }
    
    @Test
    public void testMultipleMachinesIndependentPersistence() throws SQLException {
        // Create second machine
        String machineId2 = "call-transition-test-002";
        GenericStateMachine machine2 = new CallMachine(machineId2, null, null, null);
        
        // Machine 1: IDLE -> RINGING
        machine.sendEvent(new IncomingCall("555-1111"));
        assertEquals("RINGING", machine.getCurrentState());
        
        // Machine 2: IDLE -> RINGING -> CONNECTED
        machine2.sendEvent(new IncomingCall("555-2222"));
        machine2.sendEvent(new Answer());
        assertEquals("CONNECTED", machine2.getCurrentState());
        
        // Verify both machines persisted independently
        CallSnapshotData snapshot1 = TestDatabaseHelper.getCallSnapshot(machineId);
        CallSnapshotData snapshot2 = TestDatabaseHelper.getCallSnapshot(machineId2);
        
        assertNotNull(snapshot1);
        assertNotNull(snapshot2);
        
        assertEquals("RINGING", snapshot1.state);
        assertEquals("CONNECTED", snapshot2.state);
        
        assertTrue(snapshot1.isOffline);
        assertFalse(snapshot2.isOffline);
        
        assertTrue(snapshot1.contextData.contains("555-1111"));
        assertTrue(snapshot2.contextData.contains("555-2222"));
    }
}