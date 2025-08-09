-- Sample data for testing Grafana dashboards
-- This creates some initial test data so you can see the dashboards working immediately

-- Insert sample state machine snapshots
INSERT INTO state_machine_snapshots (
    machine_id, machine_type, version, run_id, correlation_id, debug_session_id,
    state_before, state_after, event_type, transition_duration, timestamp,
    machine_online_status, state_offline_status, registry_status,
    event_payload_json, event_parameters_json, context_before_json, context_after_json
) VALUES
-- Sample call machine run 1
('call-demo-001', 'CallEntity', 1, 'call-demo-2025-01-15_10-30-00-12345', 'demo-correlation-001', 'demo-session-001',
 'IDLE', 'RINGING', 'INCOMING_CALL', 5, NOW() - INTERVAL '5 minutes',
 true, false, 'NOT_REGISTERED',
 encode('{"eventType":"INCOMING_CALL","fromNumber":"+1234567890","timestamp":1705392600000}', 'base64'),
 encode('{"eventType":"INCOMING_CALL","fromNumber":"+1234567890"}', 'base64'),
 encode('{"callId":"DEMO-001","callStatus":"IDLE"}', 'base64'),
 encode('{"callId":"DEMO-001","callStatus":"RINGING","fromNumber":"+1234567890"}', 'base64')),

('call-demo-001', 'CallEntity', 2, 'call-demo-2025-01-15_10-30-00-12345', 'demo-correlation-001', 'demo-session-001',
 'RINGING', 'CONNECTED', 'ANSWER', 2, NOW() - INTERVAL '4 minutes 30 seconds',
 true, false, 'NOT_REGISTERED',
 encode('{"eventType":"ANSWER","answeredBy":"USER","answerTime":1705392630000}', 'base64'),
 encode('{"eventType":"ANSWER","answeredBy":"USER"}', 'base64'),
 encode('{"callId":"DEMO-001","callStatus":"RINGING","fromNumber":"+1234567890"}', 'base64'),
 encode('{"callId":"DEMO-001","callStatus":"CONNECTED","fromNumber":"+1234567890","connectedTime":1705392630000}', 'base64')),

('call-demo-001', 'CallEntity', 3, 'call-demo-2025-01-15_10-30-00-12345', 'demo-correlation-001', 'demo-session-001',
 'CONNECTED', 'COMPLETED', 'HANGUP', 1, NOW() - INTERVAL '2 minutes',
 true, false, 'NOT_REGISTERED',
 encode('{"eventType":"HANGUP","reason":"NORMAL_CLEARING","initiatedBy":"CALLER"}', 'base64'),
 encode('{"eventType":"HANGUP","reason":"NORMAL_CLEARING"}', 'base64'),
 encode('{"callId":"DEMO-001","callStatus":"CONNECTED","fromNumber":"+1234567890","connectedTime":1705392630000}', 'base64'),
 encode('{"callId":"DEMO-001","callStatus":"COMPLETED","fromNumber":"+1234567890","disconnectReason":"NORMAL_CLEARING"}', 'base64')),

-- Sample call machine run 2 (with session progress)
('call-demo-002', 'CallEntity', 1, 'call-demo-2025-01-15_10-35-00-67890', 'demo-correlation-002', 'demo-session-002',
 'IDLE', 'RINGING', 'INCOMING_CALL', 3, NOW() - INTERVAL '3 minutes',
 true, false, 'REGISTERED_ACTIVE',
 encode('{"eventType":"INCOMING_CALL","fromNumber":"+1800555123","callType":"TOLL_FREE","priority":"HIGH"}', 'base64'),
 encode('{"eventType":"INCOMING_CALL","fromNumber":"+1800555123","callType":"TOLL_FREE"}', 'base64'),
 encode('{"callId":"DEMO-002","callStatus":"IDLE"}', 'base64'),
 encode('{"callId":"DEMO-002","callStatus":"RINGING","fromNumber":"+1800555123","callType":"TOLL_FREE"}', 'base64')),

('call-demo-002', 'CallEntity', 2, 'call-demo-2025-01-15_10-35-00-67890', 'demo-correlation-002', 'demo-session-002',
 'RINGING', 'RINGING', 'SESSION_PROGRESS', 1, NOW() - INTERVAL '2 minutes 45 seconds',
 true, false, 'REGISTERED_ACTIVE',
 encode('{"eventType":"SESSION_PROGRESS","responseCode":"100","description":"Trying","progressPercentage":10}', 'base64'),
 encode('{"eventType":"SESSION_PROGRESS","responseCode":"100","description":"Trying"}', 'base64'),
 encode('{"callId":"DEMO-002","callStatus":"RINGING","fromNumber":"+1800555123","callType":"TOLL_FREE"}', 'base64'),
 encode('{"callId":"DEMO-002","callStatus":"RINGING","fromNumber":"+1800555123","callType":"TOLL_FREE","ringCount":1}', 'base64')),

('call-demo-002', 'CallEntity', 3, 'call-demo-2025-01-15_10-35-00-67890', 'demo-correlation-002', 'demo-session-002',
 'RINGING', 'CONNECTED', 'ANSWER', 0, NOW() - INTERVAL '2 minutes 30 seconds',
 true, false, 'REGISTERED_ACTIVE',
 encode('{"eventType":"ANSWER","answeredBy":"AUTO_ATTENDANT","answerMethod":"IVR_SYSTEM"}', 'base64'),
 encode('{"eventType":"ANSWER","answeredBy":"AUTO_ATTENDANT","answerMethod":"IVR_SYSTEM"}', 'base64'),
 encode('{"callId":"DEMO-002","callStatus":"RINGING","fromNumber":"+1800555123","callType":"TOLL_FREE","ringCount":1}', 'base64'),
 encode('{"callId":"DEMO-002","callStatus":"CONNECTED","fromNumber":"+1800555123","callType":"TOLL_FREE","connectedTime":1705392870000}', 'base64')),

('call-demo-002', 'CallEntity', 4, 'call-demo-2025-01-15_10-35-00-67890', 'demo-correlation-002', 'demo-session-002',
 'CONNECTED', 'CONNECTED', 'DTMF', 0, NOW() - INTERVAL '2 minutes',
 true, false, 'REGISTERED_ACTIVE',
 encode('{"eventType":"DTMF","digit":"1","duration":200,"volume":-10}', 'base64'),
 encode('{"eventType":"DTMF","digit":"1","duration":200}', 'base64'),
 encode('{"callId":"DEMO-002","callStatus":"CONNECTED","fromNumber":"+1800555123","callType":"TOLL_FREE","connectedTime":1705392870000}', 'base64'),
 encode('{"callId":"DEMO-002","callStatus":"CONNECTED","fromNumber":"+1800555123","callType":"TOLL_FREE","connectedTime":1705392870000,"lastDtmf":"1"}', 'base64')),

('call-demo-002', 'CallEntity', 5, 'call-demo-2025-01-15_10-35-00-67890', 'demo-correlation-002', 'demo-session-002',
 'CONNECTED', 'COMPLETED', 'HANGUP', 1, NOW() - INTERVAL '1 minute',
 true, false, 'REGISTERED_ACTIVE',
 encode('{"eventType":"HANGUP","reason":"NORMAL_CLEARING","cause":"16","billableSeconds":45}', 'base64'),
 encode('{"eventType":"HANGUP","reason":"NORMAL_CLEARING","cause":"16","billableSeconds":45}', 'base64'),
 encode('{"callId":"DEMO-002","callStatus":"CONNECTED","fromNumber":"+1800555123","callType":"TOLL_FREE","connectedTime":1705392870000,"lastDtmf":"1"}', 'base64'),
 encode('{"callId":"DEMO-002","callStatus":"COMPLETED","fromNumber":"+1800555123","callType":"TOLL_FREE","disconnectReason":"NORMAL_CLEARING","billableSeconds":45}', 'base64')),

-- Sample SMS machine run
('sms-demo-001', 'SmsEntity', 1, 'sms-demo-2025-01-15_10-40-00-11111', 'sms-correlation-001', 'sms-session-001',
 'QUEUED', 'SENDING', 'SEND_REQUEST', 8, NOW() - INTERVAL '30 seconds',
 true, false, 'REGISTERED_ACTIVE',
 encode('{"eventType":"SEND_REQUEST","toNumber":"+1987654321","message":"Hello from state machine!","messageId":"SMS-001"}', 'base64'),
 encode('{"eventType":"SEND_REQUEST","toNumber":"+1987654321","messageId":"SMS-001"}', 'base64'),
 encode('{"smsId":"SMS-001","status":"QUEUED"}', 'base64'),
 encode('{"smsId":"SMS-001","status":"SENDING","toNumber":"+1987654321","sentTime":1705393200000}', 'base64')),

('sms-demo-001', 'SmsEntity', 2, 'sms-demo-2025-01-15_10-40-00-11111', 'sms-correlation-001', 'sms-session-001',
 'SENDING', 'DELIVERED', 'DELIVERY_REPORT', 15, NOW() - INTERVAL '10 seconds',
 true, false, 'REGISTERED_ACTIVE',
 encode('{"eventType":"DELIVERY_REPORT","status":"DELIVERED","deliveryTime":1705393215000}', 'base64'),
 encode('{"eventType":"DELIVERY_REPORT","status":"DELIVERED"}', 'base64'),
 encode('{"smsId":"SMS-001","status":"SENDING","toNumber":"+1987654321","sentTime":1705393200000}', 'base64'),
 encode('{"smsId":"SMS-001","status":"DELIVERED","toNumber":"+1987654321","deliveredTime":1705393215000}', 'base64'));

-- Insert corresponding call entity snapshots for call machines
INSERT INTO call_entity_snapshots (
    snapshot_id, from_number, to_number, call_type, ring_count, call_duration_ms, 
    disconnect_reason, recording_enabled, billing_seconds, codec
) VALUES
-- For call-demo-001 snapshots
((SELECT id FROM state_machine_snapshots WHERE machine_id = 'call-demo-001' AND version = 1),
 '+1234567890', '+0987654321', 'REGULAR', 0, NULL, NULL, false, NULL, NULL),

((SELECT id FROM state_machine_snapshots WHERE machine_id = 'call-demo-001' AND version = 2),
 '+1234567890', '+0987654321', 'REGULAR', 1, 150000, NULL, false, NULL, 'G.711'),

((SELECT id FROM state_machine_snapshots WHERE machine_id = 'call-demo-001' AND version = 3),
 '+1234567890', '+0987654321', 'REGULAR', 1, 150000, 'NORMAL_CLEARING', false, 150, 'G.711'),

-- For call-demo-002 snapshots  
((SELECT id FROM state_machine_snapshots WHERE machine_id = 'call-demo-002' AND version = 1),
 '+1800555123', '+0987654321', 'TOLL_FREE', 0, NULL, NULL, true, NULL, NULL),

((SELECT id FROM state_machine_snapshots WHERE machine_id = 'call-demo-002' AND version = 2),
 '+1800555123', '+0987654321', 'TOLL_FREE', 1, NULL, NULL, true, NULL, NULL),

((SELECT id FROM state_machine_snapshots WHERE machine_id = 'call-demo-002' AND version = 3),
 '+1800555123', '+0987654321', 'TOLL_FREE', 1, 45000, NULL, true, NULL, 'G.722'),

((SELECT id FROM state_machine_snapshots WHERE machine_id = 'call-demo-002' AND version = 4),
 '+1800555123', '+0987654321', 'TOLL_FREE', 1, 45000, NULL, true, NULL, 'G.722'),

((SELECT id FROM state_machine_snapshots WHERE machine_id = 'call-demo-002' AND version = 5),
 '+1800555123', '+0987654321', 'TOLL_FREE', 1, 45000, 'NORMAL_CLEARING', true, 45, 'G.722');

-- Refresh the materialized view with sample data
SELECT refresh_machine_metrics();

-- Show some sample queries that will work immediately
DO $$
BEGIN
    RAISE NOTICE '=== Sample Data Loaded Successfully ===';
    RAISE NOTICE 'Total snapshots: %', (SELECT COUNT(*) FROM state_machine_snapshots);
    RAISE NOTICE 'Unique machines: %', (SELECT COUNT(DISTINCT machine_id) FROM state_machine_snapshots);
    RAISE NOTICE 'Unique runs: %', (SELECT COUNT(DISTINCT run_id) FROM state_machine_snapshots);
    RAISE NOTICE '';
    RAISE NOTICE '🎯 Your Grafana dashboard is ready with sample data!';
    RAISE NOTICE '📊 Access Grafana at: http://localhost:3000';
    RAISE NOTICE '👤 Login: admin / statemachine123';
    RAISE NOTICE '';
    RAISE NOTICE '🔍 Try these sample queries in Grafana:';
    RAISE NOTICE '   • SELECT * FROM v_machine_history LIMIT 10';
    RAISE NOTICE '   • SELECT * FROM v_call_history';  
    RAISE NOTICE '   • SELECT * FROM mv_machine_metrics';
END
$$;