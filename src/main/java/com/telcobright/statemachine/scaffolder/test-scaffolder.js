#!/usr/bin/env node

const { parseDSL } = require('./scaffolder');

// Test DSL parsing
const testSpec = `
StateMachineDsl.packageBase("com.telcobright.statemachine.examples.callmachine")
    .define("CallMachine")
    .persistedIn("mysql")
    .startWith("IDLE")

    .state("IDLE")
        .onEvent("IncomingCall").goTo("RINGING")
        .endState()

    .state("RINGING").offline()
        .onEvent("Answer").goTo("CONNECTED")
        .onEvent("Hangup").goTo("IDLE")
        .stayOn("SessionProgress", "handleSessionProgressInRinging")
        .endState()

    .state("CONNECTED")
        .onEvent("Hangup").goTo("IDLE")
        .stayOn("Dtmf", "handleDtmfInConnected")
        .endState()

    .contextFields(
        "callId:String",
        "fromNumber:String",
        "toNumber:String",
        "startTime:LocalDateTime"
    )

    .build();
`;

console.log('Testing DSL Parser...\n');
const parsed = parseDSL(testSpec);
console.log('Parsed DSL:', JSON.stringify(parsed, null, 2));

console.log('\n✅ Test completed!');
console.log('\nTo generate code from this spec:');
console.log('1. Save the spec to a file (e.g., CallMachineScaffolder.java)');
console.log('2. Run: node scaffolder.js generate -s CallMachineScaffolder.java -o ./output');