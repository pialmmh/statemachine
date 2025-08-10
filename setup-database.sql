-- Database setup for state machine persistence testing
-- Run this script to create the database and required tables

CREATE DATABASE IF NOT EXISTS statedb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE statedb;

-- Call context table for persistence
CREATE TABLE IF NOT EXISTS call_contexts (
    call_id VARCHAR(255) NOT NULL PRIMARY KEY,
    current_state VARCHAR(50) NOT NULL,
    last_state_change DATETIME(6) NOT NULL,
    from_number VARCHAR(50) NOT NULL,
    to_number VARCHAR(50) NOT NULL,
    start_time DATETIME(6),
    end_time DATETIME(6),
    connect_time DATETIME(6),
    call_direction VARCHAR(20) DEFAULT 'INBOUND',
    call_status VARCHAR(50) DEFAULT 'INITIALIZING',
    ring_count INT DEFAULT 0,
    disconnect_reason VARCHAR(255),
    recording_enabled BOOLEAN DEFAULT FALSE,
    is_complete BOOLEAN DEFAULT FALSE,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    -- Sharding key for partitioned-repo
    partition_key VARCHAR(100) NOT NULL
);

-- Create index for performance
CREATE INDEX idx_call_contexts_state ON call_contexts(current_state);
CREATE INDEX idx_call_contexts_last_change ON call_contexts(last_state_change);
CREATE INDEX idx_call_contexts_partition ON call_contexts(partition_key);

-- Session events table for storing call session events
CREATE TABLE IF NOT EXISTS call_session_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    call_id VARCHAR(255) NOT NULL,
    event_text TEXT NOT NULL,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (call_id) REFERENCES call_contexts(call_id) ON DELETE CASCADE
);

CREATE INDEX idx_session_events_call_id ON call_session_events(call_id);

-- Show tables created
SHOW TABLES;

SELECT 'Database statedb setup completed successfully!' AS status;