# Rehydration + Timeout Feature Testing Checklist

## Automated Test Suites

### 1. Comprehensive Test Suite (`test-rehydration-comprehensive.js`)
Run with: `npm install colors && node test-rehydration-comprehensive.js`

- [ ] **Test 1**: Timeout During Offline (35s wait)
  - Machine goes to CONNECTED (offline)
  - Wait > 30 seconds
  - Send event to trigger rehydration
  - Verify: Machine should be in IDLE (timed out)

- [ ] **Test 2**: No Timeout Before Expiry (20s wait)
  - Machine goes to CONNECTED (offline)
  - Wait < 30 seconds
  - Send HANGUP event
  - Verify: Normal CONNECTED → IDLE transition

- [ ] **Test 3**: Exact Timeout Boundary (30s wait)
  - Machine goes to CONNECTED (offline)
  - Wait exactly 30 seconds
  - Send event to trigger rehydration
  - Verify: Should timeout (with small margin)

- [ ] **Test 4**: Multiple Rehydrations
  - Rehydrate at 10s: Should stay CONNECTED
  - Rehydrate at 25s: Should stay CONNECTED
  - Rehydrate at 35s: Should timeout to IDLE

- [ ] **Test 5**: Non-Offline State Timeout
  - Machine in RINGING (not offline)
  - Verify: Normal timeout behavior (not rehydration-based)

### 2. Edge Case Test Suite (`test-edge-cases.js`)
Run with: `node test-edge-cases.js`

- [ ] **Edge Case 1**: Rapid State Changes
  - Multiple rapid transitions before offline
  - Verify: State history integrity

- [ ] **Edge Case 2**: Concurrent Events
  - Multiple simultaneous events during rehydration
  - Verify: No race conditions

- [ ] **Edge Case 3**: Non-existent Machine
  - Try to rehydrate non-existent machine
  - Verify: Proper error handling

- [ ] **Edge Case 4**: Timeout Precision
  - Test at 29.9s, 30.0s, 30.1s
  - Verify: Consistent timeout behavior

- [ ] **Edge Case 5**: Memory Leak Test
  - Many offline/online cycles
  - Verify: No memory leaks

## Manual Testing via UI

### Setup
1. Start backend: `mvn exec:java -Dexec.mainClass="com.telcobright.statemachine.websocket.CallMachineRunnerProper"`
2. Start UI: `cd statemachine-ui-react && npm start`
3. Open browser: `http://localhost:4001`

### Test Scenarios

#### Scenario A: Basic Timeout Test
- [ ] Select machine call-002
- [ ] Send INCOMING_CALL event
- [ ] Send ANSWER event (machine goes offline)
- [ ] Wait 35 seconds
- [ ] Use "Select Offline Machine" dropdown
- [ ] Send any event (e.g., HANGUP)
- [ ] **Verify**: Machine appears back in online list in IDLE state

#### Scenario B: Manual Machine ID Entry
- [ ] Send call-003 to CONNECTED state
- [ ] Wait for it to go offline
- [ ] In offline section, manually type "call-003" in text box
- [ ] Send event
- [ ] **Verify**: Event is processed correctly

#### Scenario C: Timeout Visualization
- [ ] Watch the Event Viewer during rehydration
- [ ] **Verify**: You should see:
  1. Event received entry
  2. Timeout detection log
  3. State transition to IDLE
  4. Machine coming back online

## Backend Log Verification

Check for these log patterns:

### Successful Timeout Detection
```
🔄 REHYDRATION SUCCESSFUL
📍 Machine ID: call-002
📍 Restored State: CONNECTED
📊 Persisted Entity Info:
   Last State Change: [timestamp]
Timeout check for state CONNECTED: elapsed=35000ms, timeout=30000ms
⏰ State CONNECTED has timed out after 35000ms. Transitioning to: IDLE
```

### Successful Online Transition
```
StateMachine call-002 coming back online from CONNECTED to IDLE
[Registry] Machine call-002 brought back online (moved from offline cache to active)
[WS] Broadcasted active machines list update (3 machines)
```

### No Timeout (Within Limit)
```
Timeout check for state CONNECTED: elapsed=20000ms, timeout=30000ms
✅ State CONNECTED has not timed out. Remaining: 10000ms
```

## Database Verification

### Check MySQL Tables
```sql
-- Check state history
SELECT * FROM state_machine_history 
WHERE machine_id = 'call-002' 
ORDER BY created_at DESC LIMIT 10;

-- Check persisted context
SELECT * FROM state_machine_contexts 
WHERE machine_id = 'call-002';

-- Verify timeout transitions
SELECT * FROM state_machine_history 
WHERE machine_id = 'call-002' 
  AND event_type = 'TIMEOUT'
  AND state_before = 'CONNECTED'
  AND state_after = 'IDLE';
```

## Performance Metrics

### Monitor During Tests
- [ ] **Memory Usage**: Check for leaks during multiple cycles
- [ ] **CPU Usage**: Should not spike during rehydration
- [ ] **Response Time**: Rehydration should complete < 500ms
- [ ] **WebSocket Latency**: Events should process < 100ms

### Load Testing
- [ ] Run 10 machines simultaneously
- [ ] Send each to offline state
- [ ] Trigger rehydration for all at once
- [ ] Verify: All timeout correctly

## Error Scenarios to Test

1. **Database Connection Loss**
   - [ ] Disconnect MySQL during offline period
   - [ ] Attempt rehydration
   - [ ] Verify: Graceful error handling

2. **WebSocket Disconnection**
   - [ ] Disconnect UI during timeout wait
   - [ ] Reconnect and send event
   - [ ] Verify: State is correct

3. **Invalid Event to Offline Machine**
   - [ ] Send invalid event type
   - [ ] Verify: Proper error message

4. **Concurrent Modifications**
   - [ ] Two clients sending events simultaneously
   - [ ] Verify: Consistent state

## Regression Tests

After any code changes, ensure:
- [ ] Normal timeout (non-offline) still works
- [ ] Regular state transitions work
- [ ] Online machines don't accidentally go offline
- [ ] Offline machines don't lose their state
- [ ] History tracking remains accurate

## Sign-off Criteria

✅ All automated tests pass
✅ Manual UI tests successful
✅ Backend logs show correct behavior
✅ Database contains accurate records
✅ No memory leaks detected
✅ Performance within acceptable limits
✅ Error handling works correctly
✅ No regression in existing features

---

**Test Environment:**
- Java Version: [version]
- Node Version: [version]
- MySQL Version: [version]
- Browser: [browser and version]

**Test Date:** _______________
**Tested By:** _______________
**Test Result:** PASS / FAIL
**Notes:** _________________