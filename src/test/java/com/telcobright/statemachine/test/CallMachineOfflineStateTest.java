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
 * Test that verifies RINGING state goes offline and persists to MySQL database
 */
public class CallMachineOfflineStateTest {
    
    private GenericStateMachine machine;
    private final String machineId = "call-offline-test-001";
    
    @BeforeAll
    public static void setupDatabase() throws SQLException {
        TestDatabaseHelper.createTestDatabase();
    }
    
    @BeforeEach
    public void setup() throws SQLException {
        TestDatabaseHelper.cleanupTestData();
        
        // Create call machine
        machine = CallMachine.create(machineId);
        
        // Verify initial state
        assertEquals("IDLE", machine.getCurrentState());
    }
    
    @AfterEach
    public void teardown() throws SQLException {
        TestDatabaseHelper.cleanupTestData();
    }
    
    @Test
    public void testRingingStateGoesOfflineAndPersistsToMySQL() throws SQLException {
        // Send IncomingCall event to trigger IDLE -> RINGING transition
        machine.sendEvent(new IncomingCall("555-1234"));
        
        // Verify machine is in RINGING state
        assertEquals("RINGING", machine.getCurrentState());
        
        // Query MySQL directly to verify state persistence
        CallSnapshotData snapshot = TestDatabaseHelper.getCallSnapshot(machineId);
        assertNotNull(snapshot, "Snapshot should be saved to database");
        
        // Verify the persisted state matches expected values
        assertEquals(machineId, snapshot.machineId);
        assertEquals("RINGING", snapshot.state);
        assertTrue(snapshot.isOffline, "RINGING state should be marked as offline");
        assertNotNull(snapshot.timestamp);
        
        // Verify context data is present (should contain call details)
        assertNotNull(snapshot.contextData);
        assertTrue(snapshot.contextData.contains("555-1234"), 
                  "Context should contain the caller number");
    }
    
    @Test
    public void testOnlineStateDoesNotSetOfflineFlag() throws SQLException {
        // Send IncomingCall to go to RINGING (offline)
        machine.sendEvent(new IncomingCall("555-1234"));
        
        // Send Answer to go to CONNECTED (online)
        machine.sendEvent(new Answer());
        
        // Verify machine is in CONNECTED state
        assertEquals("CONNECTED", machine.getCurrentState());
        
        // Query MySQL to verify CONNECTED state is not marked as offline
        CallSnapshotData snapshot = TestDatabaseHelper.getCallSnapshot(machineId);
        assertNotNull(snapshot);
        assertEquals("CONNECTED", snapshot.state);
        assertFalse(snapshot.isOffline, "CONNECTED state should NOT be marked as offline");
    }
    
    @Test
    public void testMultipleOfflineTransitionsUpdateDatabase() throws SQLException {
        // IDLE -> RINGING (offline)
        machine.sendEvent(new IncomingCall("555-1234"));
        
        CallSnapshotData snapshot1 = TestDatabaseHelper.getCallSnapshot(machineId);
        assertNotNull(snapshot1);
        assertEquals("RINGING", snapshot1.state);
        assertTrue(snapshot1.isOffline);
        
        // RINGING -> CONNECTED (online)
        machine.sendEvent(new Answer());
        
        CallSnapshotData snapshot2 = TestDatabaseHelper.getCallSnapshot(machineId);
        assertNotNull(snapshot2);
        assertEquals("CONNECTED", snapshot2.state);
        assertFalse(snapshot2.isOffline);
        
        // Verify timestamp updated (later snapshot should have newer timestamp)
        assertTrue(snapshot2.timestamp.after(snapshot1.timestamp),
                  "Second snapshot should have newer timestamp");
        
        // CONNECTED -> IDLE (online)
        machine.sendEvent(new Hangup());
        
        CallSnapshotData snapshot3 = TestDatabaseHelper.getCallSnapshot(machineId);
        assertNotNull(snapshot3);
        assertEquals("IDLE", snapshot3.state);
        assertFalse(snapshot3.isOffline);
    }
}