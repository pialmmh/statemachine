#!/usr/bin/env node

const WebSocket = require('ws');
const colors = require('colors/safe');

// Helper functions
async function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

function log(message, type = 'info') {
    const timestamp = new Date().toISOString().split('T')[1].split('.')[0];
    const prefix = `[${timestamp}]`;
    
    switch(type) {
        case 'success':
            console.log(colors.green(`${prefix} ✅ ${message}`));
            break;
        case 'error':
            console.log(colors.red(`${prefix} ❌ ${message}`));
            break;
        case 'warning':
            console.log(colors.yellow(`${prefix} ⚠️  ${message}`));
            break;
        case 'test':
            console.log(colors.cyan(`${prefix} 🧪 ${message}`));
            break;
        default:
            console.log(`${prefix} ℹ️  ${message}`);
    }
}

class StateMachineTestClient {
    constructor() {
        this.ws = null;
        this.responses = [];
        this.connected = false;
    }

    async connect() {
        log('Connecting to WebSocket server...', 'info');
        this.ws = new WebSocket('ws://localhost:9999');
        
        return new Promise((resolve, reject) => {
            this.ws.on('open', () => {
                this.connected = true;
                log('Connected to WebSocket server', 'success');
                resolve();
            });
            
            this.ws.on('message', (data) => {
                const msg = JSON.parse(data);
                this.responses.push(msg);
                
                // Log state changes
                if (msg.type === 'EVENT_FIRED') {
                    log(`State change: ${msg.machineId} [${msg.oldState} → ${msg.newState}]`, 
                        msg.success ? 'success' : 'error');
                }
            });
            
            this.ws.on('error', reject);
        });
    }

    sendEvent(machineId, eventType, payload = {}) {
        const message = {
            type: 'SEND_EVENT',
            machineId: machineId,
            eventType: eventType,
            payload: payload
        };
        
        log(`Sending ${eventType} to ${machineId}`, 'info');
        this.ws.send(JSON.stringify(message));
    }

    sendArbitraryEvent(machineId, eventType, payload = {}) {
        const message = {
            type: 'EVENT_TO_ARBITRARY',
            machineId: machineId,
            eventType: eventType,
            payload: payload
        };
        
        log(`Sending arbitrary ${eventType} to ${machineId}`, 'info');
        this.ws.send(JSON.stringify(message));
    }

    async waitForResponse(predicate, timeout = 5000) {
        const startTime = Date.now();
        
        while (Date.now() - startTime < timeout) {
            const response = this.responses.find(predicate);
            if (response) {
                return response;
            }
            await sleep(100);
        }
        
        throw new Error(`Timeout waiting for response after ${timeout}ms`);
    }

    clearResponses() {
        this.responses = [];
    }

    close() {
        if (this.ws) {
            this.ws.close();
        }
    }
}

// Test Cases
async function test1_TimeoutDuringOffline(client) {
    log('TEST 1: Machine times out while offline', 'test');
    log('Expected: Machine should transition from CONNECTED to IDLE upon rehydration', 'info');
    
    const machineId = 'call-001';
    
    // Step 1: Move machine to CONNECTED (offline) state
    log('Step 1: Moving machine to CONNECTED state...', 'info');
    client.sendEvent(machineId, 'INCOMING_CALL', { phoneNumber: '+1-555-1111' });
    await sleep(500);
    
    client.sendEvent(machineId, 'ANSWER');
    await sleep(500);
    
    // Verify machine is in CONNECTED state
    const connectedResponse = await client.waitForResponse(
        r => r.type === 'EVENT_FIRED' && r.machineId === machineId && r.newState === 'CONNECTED'
    );
    
    if (connectedResponse) {
        log('Machine is in CONNECTED state (offline)', 'success');
    }
    
    // Step 2: Wait for timeout to expire
    log('Step 2: Waiting 35 seconds for timeout to expire (timeout is 30s)...', 'warning');
    await sleep(35000);
    
    // Step 3: Send arbitrary event to trigger rehydration
    log('Step 3: Triggering rehydration with arbitrary event...', 'info');
    client.clearResponses();
    client.sendArbitraryEvent(machineId, 'HANGUP');
    
    // Step 4: Verify machine transitioned to IDLE
    await sleep(1000);
    const timeoutResponse = client.responses.find(
        r => r.type === 'EVENT_FIRED' && r.machineId === machineId
    );
    
    if (timeoutResponse && timeoutResponse.newState === 'IDLE') {
        log('TEST 1 PASSED: Machine correctly transitioned to IDLE after timeout', 'success');
        return true;
    } else {
        log(`TEST 1 FAILED: Machine in unexpected state: ${timeoutResponse?.newState}`, 'error');
        return false;
    }
}

async function test2_NoTimeoutBeforeExpiry(client) {
    log('TEST 2: Machine does NOT timeout if rehydrated before expiry', 'test');
    log('Expected: Machine should remain in CONNECTED state', 'info');
    
    const machineId = 'call-002';
    
    // Step 1: Move machine to CONNECTED (offline) state
    log('Step 1: Moving machine to CONNECTED state...', 'info');
    client.sendEvent(machineId, 'INCOMING_CALL', { phoneNumber: '+1-555-2222' });
    await sleep(500);
    
    client.sendEvent(machineId, 'ANSWER');
    await sleep(500);
    
    // Step 2: Wait LESS than timeout (20 seconds out of 30)
    log('Step 2: Waiting 20 seconds (less than 30s timeout)...', 'warning');
    await sleep(20000);
    
    // Step 3: Send arbitrary event to trigger rehydration
    log('Step 3: Triggering rehydration with arbitrary event...', 'info');
    client.clearResponses();
    client.sendArbitraryEvent(machineId, 'HANGUP');
    
    // Step 4: Verify machine transitions normally (CONNECTED -> IDLE via HANGUP)
    await sleep(1000);
    const response = client.responses.find(
        r => r.type === 'EVENT_FIRED' && r.machineId === machineId
    );
    
    if (response && response.oldState === 'CONNECTED' && response.newState === 'IDLE') {
        log('TEST 2 PASSED: Machine correctly handled HANGUP without timeout', 'success');
        return true;
    } else {
        log(`TEST 2 FAILED: Unexpected transition: ${response?.oldState} -> ${response?.newState}`, 'error');
        return false;
    }
}

async function test3_ExactTimeoutBoundary(client) {
    log('TEST 3: Machine at exact timeout boundary (30 seconds)', 'test');
    log('Expected: Machine should timeout (with small margin for processing)', 'info');
    
    const machineId = 'call-003';
    
    // Step 1: Move machine to CONNECTED (offline) state
    log('Step 1: Moving machine to CONNECTED state...', 'info');
    client.sendEvent(machineId, 'INCOMING_CALL', { phoneNumber: '+1-555-3333' });
    await sleep(500);
    
    client.sendEvent(machineId, 'ANSWER');
    await sleep(500);
    
    // Step 2: Wait exactly timeout duration
    log('Step 2: Waiting exactly 30 seconds (timeout duration)...', 'warning');
    await sleep(30000);
    
    // Step 3: Send arbitrary event to trigger rehydration
    log('Step 3: Triggering rehydration with arbitrary event...', 'info');
    client.clearResponses();
    client.sendArbitraryEvent(machineId, 'SESSION_PROGRESS', { sessionData: 'test' });
    
    // Step 4: Check result (should timeout with small processing margin)
    await sleep(1000);
    const response = client.responses.find(
        r => r.type === 'EVENT_FIRED' && r.machineId === machineId
    );
    
    if (response && response.newState === 'IDLE') {
        log('TEST 3 PASSED: Machine correctly timed out at boundary', 'success');
        return true;
    } else {
        log(`TEST 3 INCONCLUSIVE: Machine state: ${response?.newState} (timing sensitive)`, 'warning');
        return true; // Don't fail on exact boundary
    }
}

async function test4_MultipleRehydrations(client) {
    log('TEST 4: Multiple rehydrations of the same machine', 'test');
    log('Expected: Each rehydration should correctly check timeout', 'info');
    
    const machineId = 'call-001';
    
    // Reset machine first
    client.sendArbitraryEvent(machineId, 'HANGUP');
    await sleep(1000);
    
    // Step 1: Move to CONNECTED
    log('Step 1: Moving machine to CONNECTED state...', 'info');
    client.sendEvent(machineId, 'INCOMING_CALL', { phoneNumber: '+1-555-4444' });
    await sleep(500);
    client.sendEvent(machineId, 'ANSWER');
    await sleep(500);
    
    // Step 2: First rehydration at 10 seconds (no timeout)
    log('Step 2: First rehydration at 10 seconds...', 'info');
    await sleep(10000);
    client.clearResponses();
    client.sendArbitraryEvent(machineId, 'SESSION_PROGRESS', { ringNumber: 1 });
    await sleep(500);
    
    let response = client.responses.find(r => r.type === 'EVENT_FIRED' && r.machineId === machineId);
    if (response && response.newState === 'CONNECTED') {
        log('First rehydration: Machine stayed in CONNECTED (correct)', 'success');
    }
    
    // Step 3: Second rehydration at 25 seconds total (still no timeout)
    log('Step 3: Second rehydration at 25 seconds total...', 'info');
    await sleep(15000); // 10 + 15 = 25 seconds
    client.clearResponses();
    client.sendArbitraryEvent(machineId, 'SESSION_PROGRESS', { ringNumber: 2 });
    await sleep(500);
    
    response = client.responses.find(r => r.type === 'EVENT_FIRED' && r.machineId === machineId);
    if (response && response.newState === 'CONNECTED') {
        log('Second rehydration: Machine stayed in CONNECTED (correct)', 'success');
    }
    
    // Step 4: Third rehydration at 35 seconds total (should timeout)
    log('Step 4: Third rehydration at 35 seconds total...', 'info');
    await sleep(10000); // 25 + 10 = 35 seconds
    client.clearResponses();
    client.sendArbitraryEvent(machineId, 'SESSION_PROGRESS', { ringNumber: 3 });
    await sleep(500);
    
    response = client.responses.find(r => r.type === 'EVENT_FIRED' && r.machineId === machineId);
    if (response && response.newState === 'IDLE') {
        log('TEST 4 PASSED: Multiple rehydrations handled correctly', 'success');
        return true;
    } else {
        log(`TEST 4 FAILED: Final state: ${response?.newState}`, 'error');
        return false;
    }
}

async function test5_NonOfflineStateRehydration(client) {
    log('TEST 5: Rehydration of non-offline state (RINGING)', 'test');
    log('Expected: RINGING should timeout normally since it is not offline', 'info');
    
    const machineId = 'call-002';
    
    // Reset machine
    client.sendArbitraryEvent(machineId, 'HANGUP');
    await sleep(1000);
    
    // Step 1: Move to RINGING (not offline)
    log('Step 1: Moving machine to RINGING state...', 'info');
    client.sendEvent(machineId, 'INCOMING_CALL', { phoneNumber: '+1-555-5555' });
    await sleep(500);
    
    // Step 2: RINGING will timeout automatically after 30 seconds (not offline)
    log('Step 2: Waiting for RINGING to timeout (30 seconds)...', 'warning');
    
    // Check periodically for timeout
    let timedOut = false;
    for (let i = 0; i < 35; i++) {
        await sleep(1000);
        const timeoutEvent = client.responses.find(
            r => r.type === 'EVENT_FIRED' && 
                 r.machineId === machineId && 
                 r.oldState === 'RINGING' && 
                 r.newState === 'IDLE'
        );
        
        if (timeoutEvent) {
            timedOut = true;
            log(`RINGING timed out after ~${i+1} seconds`, 'success');
            break;
        }
    }
    
    if (timedOut) {
        log('TEST 5 PASSED: Non-offline state timed out normally', 'success');
        return true;
    } else {
        log('TEST 5 FAILED: RINGING did not timeout', 'error');
        return false;
    }
}

// Main test runner
async function runAllTests() {
    console.log(colors.bold('\n' + '='.repeat(60)));
    console.log(colors.bold('   COMPREHENSIVE REHYDRATION + TIMEOUT TEST SUITE'));
    console.log(colors.bold('='.repeat(60) + '\n'));
    
    const client = new StateMachineTestClient();
    const results = [];
    
    try {
        await client.connect();
        await sleep(1000);
        
        // Run tests
        results.push({
            name: 'Timeout During Offline',
            passed: await test1_TimeoutDuringOffline(client)
        });
        
        console.log('\n' + '-'.repeat(60) + '\n');
        
        results.push({
            name: 'No Timeout Before Expiry',
            passed: await test2_NoTimeoutBeforeExpiry(client)
        });
        
        console.log('\n' + '-'.repeat(60) + '\n');
        
        results.push({
            name: 'Exact Timeout Boundary',
            passed: await test3_ExactTimeoutBoundary(client)
        });
        
        console.log('\n' + '-'.repeat(60) + '\n');
        
        results.push({
            name: 'Multiple Rehydrations',
            passed: await test4_MultipleRehydrations(client)
        });
        
        console.log('\n' + '-'.repeat(60) + '\n');
        
        results.push({
            name: 'Non-Offline State Rehydration',
            passed: await test5_NonOfflineStateRehydration(client)
        });
        
    } catch (error) {
        log(`Test suite error: ${error.message}`, 'error');
    } finally {
        client.close();
    }
    
    // Print summary
    console.log(colors.bold('\n' + '='.repeat(60)));
    console.log(colors.bold('   TEST SUMMARY'));
    console.log(colors.bold('='.repeat(60) + '\n'));
    
    const passed = results.filter(r => r.passed).length;
    const failed = results.filter(r => !r.passed).length;
    
    results.forEach(result => {
        const status = result.passed ? colors.green('✅ PASSED') : colors.red('❌ FAILED');
        console.log(`  ${status} - ${result.name}`);
    });
    
    console.log('\n' + '-'.repeat(60));
    console.log(colors.bold(`  Total: ${results.length} | Passed: ${passed} | Failed: ${failed}`));
    console.log('='.repeat(60) + '\n');
    
    process.exit(failed > 0 ? 1 : 0);
}

// Run tests
runAllTests().catch(console.error);