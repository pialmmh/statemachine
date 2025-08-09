-- Test Database Schema for TelcoBright State Machine Library
-- Comprehensive tables for testing Call and SMS state machines

-- Drop existing tables if they exist
DROP TABLE IF EXISTS sms_state_snapshots;
DROP TABLE IF EXISTS call_state_snapshots;
DROP TABLE IF EXISTS sms_entities;
DROP TABLE IF EXISTS call_entities;
DROP TABLE IF EXISTS test_execution_log;

-- Create SMS entities table (for ShardingEntity persistence)
CREATE TABLE sms_entities (
    sms_id VARCHAR(50) PRIMARY KEY,
    current_state VARCHAR(20) NOT NULL,
    from_number VARCHAR(20) NOT NULL,
    to_number VARCHAR(20) NOT NULL,
    message_text TEXT,
    attempt_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    priority VARCHAR(10) DEFAULT 'NORMAL',
    message_status VARCHAR(20) DEFAULT 'PENDING',
    failure_reason VARCHAR(255),
    queued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP NULL,
    delivered_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sms_state (current_state),
    INDEX idx_sms_priority (priority),
    INDEX idx_sms_status (message_status),
    INDEX idx_sms_created (created_at)
);

-- Create Call entities table (for ShardingEntity persistence)
CREATE TABLE call_entities (
    call_id VARCHAR(50) PRIMARY KEY,
    current_state VARCHAR(20) NOT NULL,
    caller_number VARCHAR(20) NOT NULL,
    callee_number VARCHAR(20) NOT NULL,
    call_status VARCHAR(20) DEFAULT 'INITIATED',
    ring_count INT DEFAULT 0,
    session_events JSON,
    recording_enabled BOOLEAN DEFAULT FALSE,
    disconnect_reason VARCHAR(100),
    call_started_at TIMESTAMP NULL,
    call_answered_at TIMESTAMP NULL,
    call_ended_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_call_state (current_state),
    INDEX idx_call_status (call_status),
    INDEX idx_call_created (created_at)
);

-- Create SMS state snapshots table (for testing persistence and state transitions)
CREATE TABLE sms_state_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    test_run_id VARCHAR(50) NOT NULL,
    sms_id VARCHAR(50) NOT NULL,
    state_name VARCHAR(20) NOT NULL,
    persistent_data JSON NOT NULL,
    volatile_context JSON NOT NULL,
    transition_event VARCHAR(50),
    transition_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    test_notes TEXT,
    INDEX idx_snapshot_run (test_run_id),
    INDEX idx_snapshot_sms (sms_id),
    INDEX idx_snapshot_state (state_name),
    INDEX idx_snapshot_timestamp (transition_timestamp)
);

-- Create Call state snapshots table (for testing persistence and state transitions)
CREATE TABLE call_state_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    test_run_id VARCHAR(50) NOT NULL,
    call_id VARCHAR(50) NOT NULL,
    state_name VARCHAR(20) NOT NULL,
    persistent_data JSON NOT NULL,
    volatile_context JSON NOT NULL,
    transition_event VARCHAR(50),
    transition_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    test_notes TEXT,
    INDEX idx_snapshot_run (test_run_id),
    INDEX idx_snapshot_call (call_id),
    INDEX idx_snapshot_state (state_name),
    INDEX idx_snapshot_timestamp (transition_timestamp)
);

-- Create test execution log table (for comprehensive test reporting)
CREATE TABLE test_execution_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    test_run_id VARCHAR(50) NOT NULL,
    test_class VARCHAR(100) NOT NULL,
    test_method VARCHAR(100) NOT NULL,
    test_type VARCHAR(50) NOT NULL, -- 'CALL_MACHINE', 'SMS_MACHINE', 'PERSISTENCE', 'TIMEOUT'
    machine_id VARCHAR(50),
    start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    end_time TIMESTAMP NULL,
    status VARCHAR(20) DEFAULT 'RUNNING', -- 'RUNNING', 'PASSED', 'FAILED', 'TIMEOUT'
    error_message TEXT,
    execution_details JSON,
    assertions_count INT DEFAULT 0,
    assertions_passed INT DEFAULT 0,
    performance_metrics JSON,
    INDEX idx_test_run (test_run_id),
    INDEX idx_test_type (test_type),
    INDEX idx_test_status (status),
    INDEX idx_test_time (start_time)
);

-- Insert sample data for testing (will be used by initialization tests)
INSERT INTO sms_entities (sms_id, current_state, from_number, to_number, message_text, priority) VALUES
('test-sms-001', 'QUEUED', '+1234567890', '+0987654321', 'Test message for basic flow', 'NORMAL'),
('test-sms-002', 'QUEUED', '+1800911911', '+0987654321', 'URGENT: Emergency notification', 'HIGH'),
('test-sms-003', 'QUEUED', '+1555123456', '+0987654321', 'Long message that exceeds the normal 160 character limit and will need to be segmented into multiple parts for delivery across the SMS network infrastructure for comprehensive testing purposes.', 'NORMAL');

INSERT INTO call_entities (call_id, current_state, caller_number, callee_number, recording_enabled) VALUES
('test-call-001', 'IDLE', '+1234567890', '+0987654321', FALSE),
('test-call-002', 'IDLE', '+1800555123', '+0987654321', TRUE),
('test-call-003', 'IDLE', '+1555999888', '+0987654321', FALSE);

-- Create stored procedures for test automation
DELIMITER //

-- Procedure to clean test data
CREATE PROCEDURE CleanTestData(IN testRunId VARCHAR(50))
BEGIN
    DELETE FROM sms_state_snapshots WHERE test_run_id = testRunId;
    DELETE FROM call_state_snapshots WHERE test_run_id = testRunId;
    DELETE FROM test_execution_log WHERE test_run_id = testRunId;
    DELETE FROM sms_entities WHERE sms_id LIKE CONCAT(testRunId, '%');
    DELETE FROM call_entities WHERE call_id LIKE CONCAT(testRunId, '%');
END //

-- Procedure to get test summary
CREATE PROCEDURE GetTestSummary(IN testRunId VARCHAR(50))
BEGIN
    SELECT 
        test_type,
        COUNT(*) as total_tests,
        SUM(CASE WHEN status = 'PASSED' THEN 1 ELSE 0 END) as passed_tests,
        SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed_tests,
        SUM(assertions_count) as total_assertions,
        SUM(assertions_passed) as passed_assertions,
        AVG(TIMESTAMPDIFF(MICROSECOND, start_time, end_time) / 1000) as avg_execution_ms
    FROM test_execution_log 
    WHERE test_run_id = testRunId 
    GROUP BY test_type
    ORDER BY test_type;
END //

-- Procedure to validate state machine integrity
CREATE PROCEDURE ValidateStateMachineIntegrity(IN testRunId VARCHAR(50), IN machineType VARCHAR(20))
BEGIN
    IF machineType = 'SMS' THEN
        SELECT 
            s.sms_id,
            s.state_name,
            s.transition_timestamp,
            LAG(s.state_name) OVER (PARTITION BY s.sms_id ORDER BY s.transition_timestamp) as previous_state,
            s.transition_event,
            CASE 
                WHEN s.state_name = 'QUEUED' AND LAG(s.state_name) OVER (PARTITION BY s.sms_id ORDER BY s.transition_timestamp) IS NULL THEN 'VALID'
                WHEN s.state_name = 'SENDING' AND LAG(s.state_name) OVER (PARTITION BY s.sms_id ORDER BY s.transition_timestamp) = 'QUEUED' THEN 'VALID'
                WHEN s.state_name = 'DELIVERED' AND LAG(s.state_name) OVER (PARTITION BY s.sms_id ORDER BY s.transition_timestamp) = 'SENDING' THEN 'VALID'
                WHEN s.state_name = 'FAILED' AND LAG(s.state_name) OVER (PARTITION BY s.sms_id ORDER BY s.transition_timestamp) IN ('SENDING', 'QUEUED') THEN 'VALID'
                ELSE 'INVALID'
            END as transition_validity
        FROM sms_state_snapshots s
        WHERE s.test_run_id = testRunId
        ORDER BY s.sms_id, s.transition_timestamp;
    ELSE
        SELECT 
            c.call_id,
            c.state_name,
            c.transition_timestamp,
            LAG(c.state_name) OVER (PARTITION BY c.call_id ORDER BY c.transition_timestamp) as previous_state,
            c.transition_event,
            CASE 
                WHEN c.state_name = 'IDLE' AND LAG(c.state_name) OVER (PARTITION BY c.call_id ORDER BY c.transition_timestamp) IS NULL THEN 'VALID'
                WHEN c.state_name = 'RINGING' AND LAG(c.state_name) OVER (PARTITION BY c.call_id ORDER BY c.transition_timestamp) = 'IDLE' THEN 'VALID'
                WHEN c.state_name = 'ACTIVE' AND LAG(c.state_name) OVER (PARTITION BY c.call_id ORDER BY c.transition_timestamp) = 'RINGING' THEN 'VALID'
                WHEN c.state_name = 'ENDED' AND LAG(c.state_name) OVER (PARTITION BY c.call_id ORDER BY c.transition_timestamp) IN ('ACTIVE', 'RINGING') THEN 'VALID'
                ELSE 'INVALID'
            END as transition_validity
        FROM call_state_snapshots c
        WHERE c.test_run_id = testRunId
        ORDER BY c.call_id, c.transition_timestamp;
    END IF;
END //

DELIMITER ;

-- Create test views for easy querying
CREATE VIEW test_sms_summary AS
SELECT 
    e.sms_id,
    e.current_state,
    e.priority,
    e.attempt_count,
    COUNT(s.id) as snapshot_count,
    MIN(s.transition_timestamp) as first_transition,
    MAX(s.transition_timestamp) as last_transition,
    TIMESTAMPDIFF(SECOND, MIN(s.transition_timestamp), MAX(s.transition_timestamp)) as total_duration_seconds
FROM sms_entities e
LEFT JOIN sms_state_snapshots s ON e.sms_id = s.sms_id
GROUP BY e.sms_id, e.current_state, e.priority, e.attempt_count;

CREATE VIEW test_call_summary AS
SELECT 
    e.call_id,
    e.current_state,
    e.call_status,
    e.ring_count,
    e.recording_enabled,
    COUNT(s.id) as snapshot_count,
    MIN(s.transition_timestamp) as first_transition,
    MAX(s.transition_timestamp) as last_transition,
    TIMESTAMPDIFF(SECOND, MIN(s.transition_timestamp), MAX(s.transition_timestamp)) as total_duration_seconds
FROM call_entities e
LEFT JOIN call_state_snapshots s ON e.call_id = s.call_id
GROUP BY e.call_id, e.current_state, e.call_status, e.ring_count, e.recording_enabled;