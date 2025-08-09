-- State Machine Monitoring Schema for Docker Grafana Setup
-- This schema is automatically loaded when PostgreSQL container starts

-- Enable necessary extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";

-- Main snapshots table (partitioned by date for performance)
CREATE TABLE IF NOT EXISTS state_machine_snapshots (
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
    context_after_hash VARCHAR(64)
);

-- Create indexes for optimal Grafana performance
CREATE INDEX IF NOT EXISTS idx_snapshots_machine_time ON state_machine_snapshots (machine_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_snapshots_run_id ON state_machine_snapshots (run_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_snapshots_event_type ON state_machine_snapshots (event_type, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_snapshots_state_transitions ON state_machine_snapshots (state_before, state_after, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_snapshots_duration ON state_machine_snapshots (transition_duration) WHERE transition_duration > 100;
CREATE INDEX IF NOT EXISTS idx_snapshots_status ON state_machine_snapshots (machine_online_status, registry_status);
CREATE INDEX IF NOT EXISTS idx_snapshots_timestamp ON state_machine_snapshots (timestamp DESC);

-- Entity-specific table for CallEntity snapshots
CREATE TABLE IF NOT EXISTS call_entity_snapshots (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id BIGINT REFERENCES state_machine_snapshots(id) ON DELETE CASCADE,
    
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
    codec VARCHAR(20)
);

-- Create indexes for call-specific queries
CREATE INDEX IF NOT EXISTS idx_call_snapshots_snapshot_id ON call_entity_snapshots (snapshot_id);
CREATE INDEX IF NOT EXISTS idx_call_snapshots_numbers ON call_entity_snapshots (from_number, to_number);
CREATE INDEX IF NOT EXISTS idx_call_snapshots_type ON call_entity_snapshots (call_type);
CREATE INDEX IF NOT EXISTS idx_call_snapshots_duration ON call_entity_snapshots (call_duration_ms);

-- Views for easier Grafana queries
CREATE OR REPLACE VIEW v_machine_history AS
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
    s.version,
    s.correlation_id,
    s.debug_session_id,
    -- Decode Base64 for display (PostgreSQL 14+)
    CASE 
        WHEN s.context_before_json IS NOT NULL AND s.context_before_json != '' THEN 
            convert_from(decode(s.context_before_json, 'base64'), 'UTF8')
        ELSE NULL 
    END as context_before_decoded,
    CASE 
        WHEN s.context_after_json IS NOT NULL AND s.context_after_json != '' THEN 
            convert_from(decode(s.context_after_json, 'base64'), 'UTF8')
        ELSE NULL 
    END as context_after_decoded,
    CASE 
        WHEN s.event_payload_json IS NOT NULL AND s.event_payload_json != '' THEN 
            convert_from(decode(s.event_payload_json, 'base64'), 'UTF8')
        ELSE NULL 
    END as event_payload_decoded
FROM state_machine_snapshots s
ORDER BY s.timestamp DESC;

-- View for call-specific history with enhanced analytics
CREATE OR REPLACE VIEW v_call_history AS
SELECT 
    s.timestamp,
    s.machine_id,
    s.run_id,
    s.state_before,
    s.state_after,
    s.event_type,
    s.transition_duration,
    s.version,
    c.from_number,
    c.to_number,
    c.call_type,
    c.ring_count,
    c.call_duration_ms,
    ROUND(c.call_duration_ms::numeric / 1000, 2) as call_duration_sec,
    c.disconnect_reason,
    c.recording_enabled,
    c.billing_seconds,
    c.codec,
    -- Call flow analysis
    CASE 
        WHEN s.state_after IN ('COMPLETED', 'HANGUP') THEN 'SUCCESSFUL'
        WHEN s.state_after IN ('REJECTED', 'MISSED', 'FAILED') THEN 'FAILED'
        ELSE 'IN_PROGRESS'
    END as call_outcome,
    -- Performance categories
    CASE 
        WHEN s.transition_duration > 1000 THEN 'SLOW'
        WHEN s.transition_duration > 500 THEN 'MEDIUM'
        ELSE 'FAST'
    END as performance_category
FROM state_machine_snapshots s
LEFT JOIN call_entity_snapshots c ON s.id = c.snapshot_id
WHERE s.machine_type = 'CallEntity'
ORDER BY s.timestamp DESC;

-- Materialized view for aggregated metrics (refresh periodically for performance)
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_machine_metrics AS
SELECT 
    machine_id,
    machine_type,
    DATE_TRUNC('hour', timestamp) as hour,
    COUNT(*) as total_transitions,
    COUNT(DISTINCT run_id) as unique_sessions,
    AVG(transition_duration) as avg_transition_ms,
    MIN(transition_duration) as min_transition_ms,
    MAX(transition_duration) as max_transition_ms,
    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY transition_duration) as median_transition_ms,
    PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY transition_duration) as p95_transition_ms,
    COUNT(DISTINCT event_type) as unique_events,
    COUNT(CASE WHEN machine_online_status = false THEN 1 END) as offline_transitions,
    COUNT(CASE WHEN transition_duration > 1000 THEN 1 END) as slow_transitions,
    -- JSON aggregation of event types for analysis
    json_object_agg(event_type, event_count) as event_distribution
FROM (
    SELECT 
        machine_id,
        machine_type,
        timestamp,
        transition_duration,
        machine_online_status,
        event_type,
        COUNT(*) OVER (PARTITION BY machine_id, DATE_TRUNC('hour', timestamp), event_type) as event_count
    FROM state_machine_snapshots
) sub
GROUP BY machine_id, machine_type, DATE_TRUNC('hour', timestamp);

-- Create index for the materialized view
CREATE UNIQUE INDEX IF NOT EXISTS idx_machine_metrics_unique 
ON mv_machine_metrics (machine_id, machine_type, hour);

-- Function to refresh metrics (call this periodically)
CREATE OR REPLACE FUNCTION refresh_machine_metrics()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_machine_metrics;
END;
$$ LANGUAGE plpgsql;

-- Create a function to get latest run for a machine (useful for Grafana variables)
CREATE OR REPLACE FUNCTION get_latest_runs(machine_id_param text DEFAULT NULL, limit_count integer DEFAULT 20)
RETURNS TABLE(
    run_id text,
    machine_id text,
    start_time timestamp with time zone,
    end_time timestamp with time zone,
    total_transitions bigint,
    duration_ms bigint
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        r.run_id::text,
        r.machine_id::text,
        r.start_time,
        r.end_time,
        r.total_transitions,
        EXTRACT(EPOCH FROM (r.end_time - r.start_time))::bigint * 1000 as duration_ms
    FROM (
        SELECT 
            s.run_id,
            s.machine_id,
            MIN(s.timestamp) as start_time,
            MAX(s.timestamp) as end_time,
            COUNT(*) as total_transitions
        FROM state_machine_snapshots s
        WHERE (machine_id_param IS NULL OR s.machine_id = machine_id_param)
        GROUP BY s.run_id, s.machine_id
        ORDER BY MAX(s.timestamp) DESC
        LIMIT limit_count
    ) r;
END;
$$ LANGUAGE plpgsql;

-- Create monitoring stats view for dashboard summary
CREATE OR REPLACE VIEW v_monitoring_stats AS
SELECT 
    COUNT(DISTINCT machine_id) as total_machines,
    COUNT(DISTINCT run_id) as total_runs,
    COUNT(*) as total_snapshots,
    MIN(timestamp) as oldest_snapshot,
    MAX(timestamp) as newest_snapshot,
    COUNT(CASE WHEN timestamp >= NOW() - INTERVAL '1 hour' THEN 1 END) as snapshots_last_hour,
    COUNT(CASE WHEN timestamp >= NOW() - INTERVAL '24 hours' THEN 1 END) as snapshots_last_24h,
    ROUND(AVG(transition_duration), 2) as avg_transition_duration,
    COUNT(CASE WHEN transition_duration > 1000 THEN 1 END) as slow_transitions_total
FROM state_machine_snapshots;

-- Grant necessary permissions for Grafana user
GRANT SELECT ON ALL TABLES IN SCHEMA public TO statemachine;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO statemachine;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO statemachine;

-- Grant usage on schema
GRANT USAGE ON SCHEMA public TO statemachine;

-- Ensure future objects are accessible
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO statemachine;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON SEQUENCES TO statemachine;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT EXECUTE ON FUNCTIONS TO statemachine;