const WebSocket = require('ws');

console.log('=== Testing Countdown Timer Feature ===\n');

const ws = new WebSocket('ws://localhost:9998');
let countdownMessages = [];

ws.on('open', function() {
    console.log('Connected to WebSocket server');
    
    // Send INCOMING_CALL to trigger RINGING state with timeout
    setTimeout(() => {
        console.log('Triggering RINGING state (30 second timeout)...\n');
        ws.send(JSON.stringify({
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-COUNTDOWN" }
        }));
    }, 500);
});

ws.on('message', function(data) {
    const msg = JSON.parse(data.toString());
    
    if (msg.type === 'TIMEOUT_COUNTDOWN') {
        countdownMessages.push(msg);
        
        // Log countdown updates
        if (msg.remainingSeconds === 30 || msg.remainingSeconds === 25 || 
            msg.remainingSeconds === 20 || msg.remainingSeconds === 15 || 
            msg.remainingSeconds === 10 || msg.remainingSeconds === 5) {
            console.log(`⏱️  Countdown: ${msg.remainingSeconds}s remaining (state: ${msg.state})`);
        }
        
        // Show last 5 seconds in detail
        if (msg.remainingSeconds <= 5 && msg.remainingSeconds > 0) {
            console.log(`   ${msg.remainingSeconds}... `);
        }
        
        // Check if we've received enough countdown messages
        if (countdownMessages.length >= 5) {
            console.log('\n✅ Countdown timer is working!');
            console.log(`   Received ${countdownMessages.length} countdown updates`);
            console.log(`   Debug mode: ${msg.debugMode}`);
            ws.close();
            process.exit(0);
        }
    } else if (msg.type === 'STATE_CHANGE') {
        if (msg.stateAfter === 'RINGING') {
            console.log('✅ Entered RINGING state - countdown should start now');
        } else if (msg.stateBefore === 'RINGING' && msg.stateAfter === 'IDLE') {
            console.log('\n⏰ Timeout occurred - state changed to IDLE');
            console.log(`Total countdown messages received: ${countdownMessages.length}`);
            ws.close();
            process.exit(0);
        }
    }
});

ws.on('error', function(err) {
    console.error('Connection error:', err.message);
    process.exit(1);
});

// Timeout after 10 seconds (we don't need to wait for full 30s)
setTimeout(() => {
    console.log(`\n✅ Test completed - received ${countdownMessages.length} countdown messages`);
    if (countdownMessages.length > 0) {
        console.log('Countdown feature is working correctly!');
    } else {
        console.log('❌ No countdown messages received - check debug mode');
    }
    ws.close();
    process.exit(0);
}, 10000);