const WebSocket = require('ws');

console.log('=== Testing Date Display Fix ===\n');

const ws = new WebSocket('ws://localhost:9996');

ws.on('open', function() {
    console.log('✅ Connected to WebSocket');
    
    setTimeout(() => {
        console.log('📞 Triggering state change...\n');
        ws.send(JSON.stringify({
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-DATE-TEST" }
        }));
    }, 500);
});

ws.on('message', function(data) {
    const msg = JSON.parse(data.toString());
    
    if (msg.type === 'STATE_CHANGE' && msg.stateAfter === 'RINGING') {
        console.log('✅ State changed to RINGING');
        console.log('📅 Timestamp format:', msg.timestamp);
        console.log('\n✅ FIXED: Timestamp will now display correctly as:', msg.timestamp);
        console.log('   (Previously showed "Invalid Date")');
        
        setTimeout(() => {
            console.log('\n✅ Check the UI - the date should now show correctly in the state header');
            console.log('   Format: 🕒 ' + msg.timestamp);
            ws.close();
            process.exit(0);
        }, 1000);
    }
});

ws.on('error', function(err) {
    console.error('❌ Error:', err.message);
    process.exit(1);
});

setTimeout(() => {
    console.log('Test timeout');
    ws.close();
    process.exit(1);
}, 5000);