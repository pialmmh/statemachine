-- State Machine Snapshots Schema for Grafana Integration
-- This schema supports the comprehensive monitoring system with entity-specific snapshots

-- Main snapshots table (partitioned by date for performance)
CREATE TABLE state_machine_snapshots (
    id BIGSERIAL PRIMARY KEY,
    machine_id VARCHAR(255) NOT NULL,
    machine_type VARCHAR(100) NOT NULL,
    version BIGINT NOT NULL,
    run_id VARCHAR(500) NOT NULL,
    correlation_id VARCHAR(255),
    debug_session_id VARCHAR(255),
    
    -- State transition data
    state_before VARCHAR(100) NOT NULL,
    state_after VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    transition_duration BIGINT DEFAULT 0,
    
    -- Timestamps
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    -- Status information
    machine_online_status BOOLEAN DEFAULT true,
    state_offline_status BOOLEAN DEFAULT false,
    registry_status VARCHAR(50) DEFAULT 'NOT_REGISTERED',
    
    -- JSON data (Base64 encoded for storage efficiency)
    event_payload_json TEXT,
    event_parameters_json TEXT,
    context_before_json TEXT,
    context_before_hash VARCHAR(64),
    context_after_json TEXT,
    context_after_hash VARCHAR(64),
    
    -- Indexes for Grafana queries
    INDEX idx_snapshots_machine_time (machine_id, timestamp DESC),
    INDEX idx_snapshots_run_id (run_id, timestamp DESC),
    INDEX idx_snapshots_event_type (event_type, timestamp DESC),
    INDEX idx_snapshots_state_transitions (state_before, state_after, timestamp DESC)
) PARTITION BY RANGE (timestamp);

-- Create monthly partitions for better performance
CREATE TABLE state_machine_snapshots_2025_01 PARTITION OF state_machine_snapshots
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');

CREATE TABLE state_machine_snapshots_2025_02 PARTITION OF state_machine_snapshots
    FOR VALUES FROM ('2025-02-01') TO ('2025-03-01');

-- Add more partitions as needed...

-- Entity-specific table for CallEntity snapshots
CREATE TABLE call_entity_snapshots (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id BIGINT REFERENCES state_machine_snapshots(id),
    
    -- Call-specific fields  
    from_number VARCHAR(50),
    to_number VARCHAR(50),
    call_type VARCHAR(50),
    ring_count INTEGER DEFAULT 0,
    call_duration_ms BIGINT,
    disconnect_reason VARCHAR(100),
    recording_enabled BOOLEAN DEFAULT false,
    
    -- Additional call metrics
    answer_time_ms BIGINT,
    billing_seconds INTEGER,
    codec VARCHAR(20),
    
    INDEX idx_call_snapshots_numbers (from_number, to_number),
    INDEX idx_call_snapshots_type (call_type),
    INDEX idx_call_snapshots_duration (call_duration_ms)
);

-- Views for easier Grafana queries
CREATE VIEW v_machine_history AS
SELECT 
    s.timestamp,
    s.machine_id,
    s.run_id,
    s.state_before,
    s.state_after,
    s.event_type,
    s.transition_duration,
    s.machine_online_status,
    s.registry_status,
    -- Decode Base64 for display (PostgreSQL 14+)
    CASE 
        WHEN s.context_before_json IS NOT NULL THEN 
            convert_from(decode(s.context_before_json, 'base64'), 'UTF8')
        ELSE NULL 
    END as context_before_decoded,
    CASE 
        WHEN s.context_after_json IS NOT NULL THEN 
            convert_from(decode(s.context_after_json, 'base64'), 'UTF8')
        ELSE NULL 
    END as context_after_decoded
FROM state_machine_snapshots s
ORDER BY s.timestamp DESC;

-- View for call-specific history  
CREATE VIEW v_call_history AS
SELECT 
    s.timestamp,
    s.machine_id,
    s.run_id,
    s.state_before,
    s.state_after,
    s.event_type,
    s.transition_duration,
    c.from_number,
    c.to_number,
    c.call_type,
    c.ring_count,
    c.call_duration_ms,
    c.disconnect_reason,
    c.recording_enabled
FROM state_machine_snapshots s
LEFT JOIN call_entity_snapshots c ON s.id = c.snapshot_id
WHERE s.machine_type = 'CallEntity'
ORDER BY s.timestamp DESC;

-- Materialized view for aggregated metrics (refresh periodically)
CREATE MATERIALIZED VIEW mv_machine_metrics AS
SELECT 
    machine_id,
    DATE_TRUNC('hour', timestamp) as hour,
    COUNT(*) as total_transitions,
    COUNT(DISTINCT run_id) as unique_sessions,
    AVG(transition_duration) as avg_transition_ms,
    MIN(transition_duration) as min_transition_ms,
    MAX(transition_duration) as max_transition_ms,
    COUNT(DISTINCT event_type) as unique_events,
    COUNT(CASE WHEN machine_online_status = false THEN 1 END) as offline_transitions
FROM state_machine_snapshots
GROUP BY machine_id, DATE_TRUNC('hour', timestamp);

-- Index for the materialized view
CREATE INDEX idx_machine_metrics_hour ON mv_machine_metrics (machine_id, hour DESC);

-- Function to refresh metrics (call this periodically)
CREATE OR REPLACE FUNCTION refresh_machine_metrics()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_machine_metrics;
END;
$$ LANGUAGE plpgsql;