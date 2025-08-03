package com.telcobright.statemachineexamples.callmachine;

import com.telcobright.statemachineexamples.callmachine.states.ringing.OnSessionProgress_Ringing;
import com.telcobright.statemachine.FluentStateMachineBuilder;
import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachineexamples.callmachine.events.IncomingCall;
import com.telcobright.statemachineexamples.callmachine.events.Answer;
import com.telcobright.statemachineexamples.callmachine.events.Hangup;
import com.telcobright.statemachineexamples.callmachine.events.SessionProgress;
import com.telcobright.statemachine.persistence.StateMachineSnapshotEntity;
import com.telcobright.statemachine.events.StateMachineEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Properties;
import java.io.InputStream;
import java.io.IOException;

/**
 * Call state machine definition with 3 states: IDLE, RINGING, CONNECTED
 * Defines the structure, transitions, and persistence for call management
 */
public class CallMachine extends GenericStateMachine<CallContext> {
    
    public CallMachine() {
        super("call-machine", null, null, null);
    }
    
    /**
     * Create and configure the call state machine using expressive fluent builder
     */
    public static CallMachine create(String machineId) {
        // Load profile-based configuration
        String activeProfile = loadActiveProfile();
        String configFile = "statemachine-" + activeProfile + ".properties";
        
        System.out.println("🔧 Creating CallMachine with profile: " + activeProfile);
        
        // Create state machine using generic FluentStateMachineBuilder - no custom builder needed!
        // This demonstrates the expressive builder syntax that works for ANY state machine
        return (CallMachine) FluentStateMachineBuilder.<CallContext>create(machineId)
            .initialState(CallState.IDLE)
            
            // IDLE state transitions
            .state(CallState.IDLE)
                .on(IncomingCall.class).to(CallState.RINGING)
                .done()
                
            // RINGING state (offline) with transitions and stay actions
            .state(CallState.RINGING)
                .offline()
                .on(Answer.class).to(CallState.CONNECTED)
                .on(Hangup.class).to(CallState.IDLE)
                .stay(SessionProgress.class, (machine, event) -> OnSessionProgress_Ringing.handle((GenericStateMachine<CallContext>) machine, (StateMachineEvent) event))
                .done()
                
            // CONNECTED state transitions
            .state(CallState.CONNECTED)
                .on(Hangup.class).to(CallState.IDLE)
                .done()
                
            .build();
    }
    
    
    /**
     * Load active profile from profile.properties
     */
    private static String loadActiveProfile() {
        Properties props = new Properties();
        try (InputStream input = CallMachine.class.getClassLoader().getResourceAsStream("profile.properties")) {
            if (input != null) {
                props.load(input);
                String profile = props.getProperty("active.profile", "dev");
                System.out.println("🔧 Active profile: " + profile);
                return profile;
            }
        } catch (IOException e) {
            System.err.println("⚠️ Could not load profile.properties, using default: dev");
        }
        return "dev"; // default profile
    }
    
    // ======================== CUSTOM SQL PERSISTENCE METHODS ========================
    
    /**
     * Custom save function for call snapshots
     */
    public static Boolean saveCallSnapshot(Connection conn, StateMachineSnapshotEntity snapshot) {
        String sql = "INSERT INTO call_snapshots (machine_id, state, context_data, timestamp, is_offline) VALUES (?, ?, ?, ?, ?)";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, snapshot.getMachineId());
            pstmt.setString(2, snapshot.getStateId());
            pstmt.setString(3, snapshot.getContext());
            pstmt.setTimestamp(4, Timestamp.valueOf(snapshot.getTimestamp()));
            pstmt.setBoolean(5, snapshot.getIsOffline());
            
            int rowsAffected = pstmt.executeUpdate();
            System.out.println("💾 Saved call snapshot: " + snapshot.getMachineId() + " in state " + snapshot.getStateId());
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            System.err.println("❌ Failed to save call snapshot: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Custom load function for call snapshots
     */
    public static StateMachineSnapshotEntity loadCallSnapshot(Connection conn, String machineId) {
        String sql = "SELECT machine_id, state, context_data, timestamp, is_offline " +
                    "FROM call_snapshots WHERE machine_id = ? ORDER BY timestamp DESC LIMIT 1";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, machineId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    StateMachineSnapshotEntity snapshot = new StateMachineSnapshotEntity();
                    snapshot.setMachineId(rs.getString("machine_id"));
                    snapshot.setStateId(rs.getString("state"));
                    snapshot.setContext(rs.getString("context_data"));
                    snapshot.setTimestamp(rs.getTimestamp("timestamp").toLocalDateTime());
                    snapshot.setIsOffline(rs.getBoolean("is_offline"));
                    
                    System.out.println("📖 Loaded call snapshot: " + machineId + " in state " + snapshot.getStateId());
                    return snapshot;
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Failed to load call snapshot: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Custom initialization function for call table
     */
    public static Boolean initCallTable(Connection conn) {
        String createTableSql = "CREATE TABLE IF NOT EXISTS call_snapshots (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "machine_id VARCHAR(255) NOT NULL, " +
                "state VARCHAR(100) NOT NULL, " +
                "context_data TEXT, " +
                "timestamp TIMESTAMP NOT NULL, " +
                "is_offline BOOLEAN DEFAULT FALSE, " +
                "INDEX idx_machine_id (machine_id), " +
                "INDEX idx_timestamp (timestamp)" +
                ")";
        
        try (PreparedStatement pstmt = conn.prepareStatement(createTableSql)) {
            pstmt.execute();
            System.out.println("🗄️ Call snapshots table initialized");
            return true;
        } catch (SQLException e) {
            System.err.println("❌ Failed to initialize call table: " + e.getMessage());
            return false;
        }
    }
}