package com.telcobright.statemachine.test;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachineexamples.smsmachine.SmsMachine;
import com.telcobright.statemachineexamples.smsmachine.events.SendAttempt;
import com.telcobright.statemachineexamples.smsmachine.events.DeliveryReport;
import com.telcobright.statemachineexamples.smsmachine.events.SendFailed;
import com.telcobright.statemachineexamples.smsmachine.events.Retry;
import com.telcobright.statemachineexamples.smsmachine.events.StatusUpdate;
import com.telcobright.statemachine.test.TestDatabaseHelper;
import com.telcobright.statemachine.test.TestDatabaseHelper.SmsSnapshotData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;

/**
 * Test that verifies SMS machine state transitions are properly persisted to MySQL
 */
public class SmsStateTransitionPersistenceTest {
    
    private GenericStateMachine machine;
    private final String machineId = "sms-transition-test-001";
    
    @BeforeAll
    public static void setupDatabase() throws SQLException {
        TestDatabaseHelper.createTestDatabase();
    }
    
    @BeforeEach
    public void setup() throws SQLException {
        TestDatabaseHelper.cleanupTestData();
        
        machine = new SmsMachine(machineId, null, null, null);
        
        // Verify initial state
        assertEquals("QUEUED", machine.getCurrentState());
    }
    
    @AfterEach
    public void teardown() throws SQLException {
        TestDatabaseHelper.cleanupTestData();
    }
    
    @Test
    public void testQueuedToSendingTransitionPersistence() throws SQLException {
        // Trigger QUEUED -> SENDING transition
        machine.sendEvent(new SendAttempt());
        
        // Verify machine state
        assertEquals("SENDING", machine.getCurrentState());
        
        // Verify database persistence
        SmsSnapshotData snapshot = TestDatabaseHelper.getSmsSnapshot(machineId);
        assertNotNull(snapshot);
        assertEquals(machineId, snapshot.machineId);
        assertEquals("SENDING", snapshot.state);
        assertFalse(snapshot.isOffline, "SENDING state should be online");
    }
    
    @Test
    public void testSendingToDeliveredTransitionPersistence() throws SQLException {
        // Setup: Go to SENDING state
        machine.sendEvent(new SendAttempt());
        assertEquals("SENDING", machine.getCurrentState());
        
        // Trigger SENDING -> DELIVERED transition
        machine.sendEvent(new DeliveryReport("delivered"));
        
        // Verify machine state
        assertEquals("DELIVERED", machine.getCurrentState());
        
        // Verify database persistence
        SmsSnapshotData snapshot = TestDatabaseHelper.getSmsSnapshot(machineId);
        assertNotNull(snapshot);
        assertEquals("DELIVERED", snapshot.state);
        assertFalse(snapshot.isOffline, "DELIVERED state should be online");
    }
    
    @Test
    public void testSendingToFailedTransitionPersistence() throws SQLException {
        // Setup: Go to SENDING state
        machine.sendEvent(new SendAttempt());
        assertEquals("SENDING", machine.getCurrentState());
        
        // Trigger SENDING -> FAILED transition
        machine.sendEvent(new SendFailed("network_error"));
        
        // Verify machine state
        assertEquals("FAILED", machine.getCurrentState());
        
        // Verify database persistence
        SmsSnapshotData snapshot = TestDatabaseHelper.getSmsSnapshot(machineId);
        assertNotNull(snapshot);
        assertEquals("FAILED", snapshot.state);
        assertFalse(snapshot.isOffline, "FAILED state should be online");
    }
    
    @Test
    public void testFailedToQueuedRetryTransitionPersistence() throws SQLException {
        // Setup: Go to FAILED state
        machine.sendEvent(new SendAttempt());
        machine.sendEvent(new SendFailed("timeout"));
        assertEquals("FAILED", machine.getCurrentState());
        
        // Trigger FAILED -> QUEUED transition (retry)
        machine.sendEvent(new Retry());
        
        // Verify machine state
        assertEquals("QUEUED", machine.getCurrentState());
        
        // Verify database persistence
        SmsSnapshotData snapshot = TestDatabaseHelper.getSmsSnapshot(machineId);
        assertNotNull(snapshot);
        assertEquals("QUEUED", snapshot.state);
        assertFalse(snapshot.isOffline, "QUEUED state should be online");
    }
    
    @Test
    public void testStatusUpdateStayEventDoesNotChangeState() throws SQLException {
        // Setup: Go to SENDING state
        machine.sendEvent(new SendAttempt());
        assertEquals("SENDING", machine.getCurrentState());
        
        // Get timestamp before status update
        SmsSnapshotData beforeSnapshot = TestDatabaseHelper.getSmsSnapshot(machineId);
        assertNotNull(beforeSnapshot);
        assertEquals("SENDING", beforeSnapshot.state);
        
        // Send StatusUpdate (stay event) - should not change state
        machine.sendEvent(new StatusUpdate("progress: 50%"));
        
        // Verify machine state unchanged
        assertEquals("SENDING", machine.getCurrentState());
        
        // Verify database state unchanged
        SmsSnapshotData afterSnapshot = TestDatabaseHelper.getSmsSnapshot(machineId);
        assertNotNull(afterSnapshot);
        assertEquals("SENDING", afterSnapshot.state);
        assertEquals(beforeSnapshot.state, afterSnapshot.state);
    }
    
    @Test
    public void testCompleteSmsFlowWithRetry() throws SQLException {
        java.sql.Timestamp startTime = new java.sql.Timestamp(System.currentTimeMillis());
        
        // Step 1: QUEUED -> SENDING
        machine.sendEvent(new SendAttempt());
        SmsSnapshotData snapshot1 = TestDatabaseHelper.getSmsSnapshot(machineId);
        assertNotNull(snapshot1);
        assertEquals("SENDING", snapshot1.state);
        assertTrue(snapshot1.timestamp.after(startTime));
        
        // Step 2: SENDING -> FAILED
        machine.sendEvent(new SendFailed("network_error"));
        SmsSnapshotData snapshot2 = TestDatabaseHelper.getSmsSnapshot(machineId);
        assertNotNull(snapshot2);
        assertEquals("FAILED", snapshot2.state);
        assertTrue(snapshot2.timestamp.after(snapshot1.timestamp));
        
        // Step 3: FAILED -> QUEUED (retry)
        machine.sendEvent(new Retry());
        SmsSnapshotData snapshot3 = TestDatabaseHelper.getSmsSnapshot(machineId);
        assertNotNull(snapshot3);
        assertEquals("QUEUED", snapshot3.state);
        assertTrue(snapshot3.timestamp.after(snapshot2.timestamp));
        
        // Step 4: QUEUED -> SENDING (second attempt)
        machine.sendEvent(new SendAttempt());
        SmsSnapshotData snapshot4 = TestDatabaseHelper.getSmsSnapshot(machineId);
        assertNotNull(snapshot4);
        assertEquals("SENDING", snapshot4.state);
        assertTrue(snapshot4.timestamp.after(snapshot3.timestamp));
        
        // Step 5: SENDING -> DELIVERED (success)
        machine.sendEvent(new DeliveryReport("delivered"));
        SmsSnapshotData snapshot5 = TestDatabaseHelper.getSmsSnapshot(machineId);
        assertNotNull(snapshot5);
        assertEquals("DELIVERED", snapshot5.state);
        assertTrue(snapshot5.timestamp.after(snapshot4.timestamp));
    }
    
    @Test
    public void testInvalidTransitionDoesNotUpdateDatabase() throws SQLException {
        // Start in QUEUED state
        assertEquals("QUEUED", machine.getCurrentState());
        
        // Get initial snapshot
        SmsSnapshotData initialSnapshot = TestDatabaseHelper.getSmsSnapshot(machineId);
        
        // Try invalid transition: QUEUED -> DeliveryReport (should be rejected)
        machine.sendEvent(new DeliveryReport("invalid"));
        
        // Verify machine state didn't change
        assertEquals("QUEUED", machine.getCurrentState());
        
        // Verify database wasn't updated with invalid transition
        SmsSnapshotData afterSnapshot = TestDatabaseHelper.getSmsSnapshot(machineId);
        
        // Either no snapshot exists, or it's still QUEUED
        if (afterSnapshot != null) {
            assertEquals("QUEUED", afterSnapshot.state);
            
            // If initial snapshot existed, timestamps should be the same
            if (initialSnapshot != null) {
                assertEquals(initialSnapshot.timestamp, afterSnapshot.timestamp);
            }
        }
    }
    
    @Test
    public void testMultipleSmsMessagesIndependentPersistence() throws SQLException {
        // Create second SMS machine
        String machineId2 = "sms-transition-test-002";
        GenericStateMachine machine2 = new SmsMachine(machineId2, null, null, null);
        
        // Machine 1: QUEUED -> SENDING -> DELIVERED
        machine.sendEvent(new SendAttempt());
        machine.sendEvent(new DeliveryReport("delivered"));
        assertEquals("DELIVERED", machine.getCurrentState());
        
        // Machine 2: QUEUED -> SENDING -> FAILED
        machine2.sendEvent(new SendAttempt());
        machine2.sendEvent(new SendFailed("timeout"));
        assertEquals("FAILED", machine2.getCurrentState());
        
        // Verify both machines persisted independently
        SmsSnapshotData snapshot1 = TestDatabaseHelper.getSmsSnapshot(machineId);
        SmsSnapshotData snapshot2 = TestDatabaseHelper.getSmsSnapshot(machineId2);
        
        assertNotNull(snapshot1);
        assertNotNull(snapshot2);
        
        assertEquals("DELIVERED", snapshot1.state);
        assertEquals("FAILED", snapshot2.state);
        
        // Both should be online states
        assertFalse(snapshot1.isOffline);
        assertFalse(snapshot2.isOffline);
    }
}