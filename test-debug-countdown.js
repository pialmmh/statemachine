const WebSocket = require('ws');

console.log('=== Testing Debug Mode Countdown Messages ===\n');

const ws = new WebSocket('ws://localhost:9998');
let messageLog = [];

ws.on('open', function() {
    console.log('✅ Connected to WebSocket');
    
    // Trigger RINGING state after 1 second
    setTimeout(() => {
        console.log('📞 Triggering RINGING state (30s timeout)...\n');
        ws.send(JSON.stringify({
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-DEBUG" }
        }));
    }, 1000);
});

ws.on('message', function(data) {
    const msg = JSON.parse(data.toString());
    
    // Log all message types for debugging
    messageLog.push(msg.type);
    
    if (msg.type === 'TIMEOUT_COUNTDOWN') {
        console.log(`⏱️  COUNTDOWN MESSAGE: ${msg.remainingSeconds}s remaining`);
        console.log(`   State: ${msg.state}, Debug: ${msg.debugMode}`);
        console.log(`   Full message:`, JSON.stringify(msg));
        
        if (msg.remainingSeconds === 25) {
            console.log('\n✅ Countdown messages are being sent correctly!');
            console.log('Message types received:', messageLog.join(', '));
            ws.close();
            process.exit(0);
        }
    } else {
        console.log(`📨 Received: ${msg.type}`);
        if (msg.type === 'STATE_CHANGE') {
            console.log(`   State: ${msg.stateBefore} → ${msg.stateAfter}`);
        }
    }
});

ws.on('error', function(err) {
    console.error('❌ Connection error:', err.message);
    process.exit(1);
});

// Timeout after 10 seconds
setTimeout(() => {
    console.log('\n⚠️  Test timeout - No countdown messages received');
    console.log('Message types received:', messageLog.join(', '));
    console.log('\nPossible issues:');
    console.log('1. Debug mode not enabled (use --debug flag)');
    console.log('2. Countdown timer not starting');
    console.log('3. Messages not being broadcast');
    ws.close();
    process.exit(1);
}, 10000);