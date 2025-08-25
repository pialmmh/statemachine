#!/usr/bin/env node

const WebSocket = require('ws');

async function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

class EdgeCaseTests {
    constructor() {
        this.ws = null;
    }

    async connect() {
        this.ws = new WebSocket('ws://localhost:9999');
        return new Promise((resolve, reject) => {
            this.ws.on('open', resolve);
            this.ws.on('error', reject);
        });
    }

    async testRapidStateChanges() {
        console.log('\n🧪 EDGE CASE 1: Rapid state changes before offline');
        console.log('Testing: Multiple rapid transitions before machine goes offline\n');
        
        const machineId = 'call-001';
        
        // Rapid state changes
        for (let i = 0; i < 5; i++) {
            console.log(`  Cycle ${i + 1}:`);
            
            // IDLE -> RINGING
            this.ws.send(JSON.stringify({
                type: 'SEND_EVENT',
                machineId: machineId,
                eventType: 'INCOMING_CALL',
                payload: { phoneNumber: `+1-555-${1000 + i}` }
            }));
            await sleep(100);
            
            // RINGING -> IDLE (reject)
            this.ws.send(JSON.stringify({
                type: 'SEND_EVENT',
                machineId: machineId,
                eventType: 'REJECT',
                payload: { reason: 'Busy' }
            }));
            await sleep(100);
        }
        
        // Now go to offline state
        console.log('\n  Moving to CONNECTED (offline)...');
        this.ws.send(JSON.stringify({
            type: 'SEND_EVENT',
            machineId: machineId,
            eventType: 'INCOMING_CALL',
            payload: { phoneNumber: '+1-555-9999' }
        }));
        await sleep(500);
        
        this.ws.send(JSON.stringify({
            type: 'SEND_EVENT',
            machineId: machineId,
            eventType: 'ANSWER',
            payload: {}
        }));
        await sleep(500);
        
        // Wait for timeout
        console.log('  Waiting 35 seconds for timeout...');
        await sleep(35000);
        
        // Trigger rehydration
        console.log('  Triggering rehydration...');
        this.ws.send(JSON.stringify({
            type: 'EVENT_TO_ARBITRARY',
            machineId: machineId,
            eventType: 'HANGUP',
            payload: {}
        }));
        
        await sleep(1000);
        console.log('  ✅ Test completed - check logs for proper state tracking\n');
    }

    async testConcurrentEvents() {
        console.log('\n🧪 EDGE CASE 2: Concurrent events to offline machine');
        console.log('Testing: Multiple simultaneous events during rehydration\n');
        
        const machineId = 'call-002';
        
        // Move to CONNECTED (offline)
        console.log('  Setting up offline state...');
        this.ws.send(JSON.stringify({
            type: 'SEND_EVENT',
            machineId: machineId,
            eventType: 'INCOMING_CALL',
            payload: { phoneNumber: '+1-555-7777' }
        }));
        await sleep(500);
        
        this.ws.send(JSON.stringify({
            type: 'SEND_EVENT',
            machineId: machineId,
            eventType: 'ANSWER',
            payload: {}
        }));
        await sleep(500);
        
        // Wait for timeout
        console.log('  Waiting 35 seconds for timeout...');
        await sleep(35000);
        
        // Send multiple concurrent events
        console.log('  Sending 5 concurrent events...');
        const events = ['HANGUP', 'SESSION_PROGRESS', 'HANGUP', 'SESSION_PROGRESS', 'HANGUP'];
        
        events.forEach((eventType, index) => {
            setTimeout(() => {
                this.ws.send(JSON.stringify({
                    type: 'EVENT_TO_ARBITRARY',
                    machineId: machineId,
                    eventType: eventType,
                    payload: { index: index }
                }));
            }, index * 10); // Stagger by 10ms
        });
        
        await sleep(2000);
        console.log('  ✅ Test completed - check for race conditions\n');
    }

    async testPersistenceFailure() {
        console.log('\n🧪 EDGE CASE 3: Rehydration with corrupted/missing persistence');
        console.log('Testing: Attempting to rehydrate non-existent machine\n');
        
        const machineId = 'call-nonexistent';
        
        // Try to send event to non-existent machine
        console.log('  Sending event to non-existent machine...');
        this.ws.send(JSON.stringify({
            type: 'EVENT_TO_ARBITRARY',
            machineId: machineId,
            eventType: 'HANGUP',
            payload: {}
        }));
        
        await sleep(1000);
        console.log('  ✅ Test completed - check error handling\n');
    }

    async testTimeoutPrecision() {
        console.log('\n🧪 EDGE CASE 4: Timeout precision test');
        console.log('Testing: Exact timeout boundaries with millisecond precision\n');
        
        const machineId = 'call-003';
        const timeouts = [29900, 30000, 30100]; // Just before, at, just after
        
        for (const timeout of timeouts) {
            console.log(`\n  Testing ${timeout}ms (timeout is 30000ms):`);
            
            // Setup CONNECTED state
            this.ws.send(JSON.stringify({
                type: 'SEND_EVENT',
                machineId: machineId,
                eventType: 'INCOMING_CALL',
                payload: { phoneNumber: '+1-555-8888' }
            }));
            await sleep(200);
            
            this.ws.send(JSON.stringify({
                type: 'SEND_EVENT',
                machineId: machineId,
                eventType: 'ANSWER',
                payload: {}
            }));
            await sleep(200);
            
            // Wait specific timeout
            console.log(`    Waiting ${timeout}ms...`);
            await sleep(timeout);
            
            // Trigger rehydration
            this.ws.send(JSON.stringify({
                type: 'EVENT_TO_ARBITRARY',
                machineId: machineId,
                eventType: 'SESSION_PROGRESS',
                payload: { timeout: timeout }
            }));
            await sleep(500);
            
            // Reset for next test
            this.ws.send(JSON.stringify({
                type: 'EVENT_TO_ARBITRARY',
                machineId: machineId,
                eventType: 'HANGUP',
                payload: {}
            }));
            await sleep(500);
        }
        
        console.log('\n  ✅ Precision test completed\n');
    }

    async testMemoryLeak() {
        console.log('\n🧪 EDGE CASE 5: Memory leak test');
        console.log('Testing: Many offline/online cycles\n');
        
        const machineId = 'call-001';
        const cycles = 10;
        
        for (let i = 0; i < cycles; i++) {
            console.log(`  Cycle ${i + 1}/${cycles}`);
            
            // Go to CONNECTED (offline)
            this.ws.send(JSON.stringify({
                type: 'SEND_EVENT',
                machineId: machineId,
                eventType: 'INCOMING_CALL',
                payload: { phoneNumber: `+1-555-${2000 + i}` }
            }));
            await sleep(50);
            
            this.ws.send(JSON.stringify({
                type: 'SEND_EVENT',
                machineId: machineId,
                eventType: 'ANSWER',
                payload: {}
            }));
            await sleep(50);
            
            // Come back online
            this.ws.send(JSON.stringify({
                type: 'SEND_EVENT',
                machineId: machineId,
                eventType: 'HANGUP',
                payload: {}
            }));
            await sleep(50);
        }
        
        console.log('  ✅ Memory leak test completed - check memory usage\n');
    }

    async runAll() {
        try {
            await this.connect();
            console.log('Connected to WebSocket server\n');
            console.log('='.repeat(60));
            console.log('   EDGE CASE TEST SUITE');
            console.log('='.repeat(60));
            
            await this.testRapidStateChanges();
            await this.testConcurrentEvents();
            await this.testPersistenceFailure();
            await this.testTimeoutPrecision();
            await this.testMemoryLeak();
            
            console.log('='.repeat(60));
            console.log('   ALL EDGE CASE TESTS COMPLETED');
            console.log('='.repeat(60));
            console.log('\n⚠️  Review backend logs for detailed verification\n');
            
        } catch (error) {
            console.error('Test error:', error);
        } finally {
            if (this.ws) {
                this.ws.close();
            }
        }
    }
}

// Run edge case tests
const tester = new EdgeCaseTests();
tester.runAll().catch(console.error);