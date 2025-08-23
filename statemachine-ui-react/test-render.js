// Test what StateTreeView would render with the correct data

const stateInstances = [{
  "state": "IDLE",
  "instanceNumber": 1,
  "transitions": [
    {
      "stepNumber": 1,
      "fromState": "Initial",
      "toState": "IDLE",
      "event": "Start",
      "timestamp": "03:43:18.333",
      "duration": 0,
      "machineId": "call-001"
    },
    {
      "stepNumber": 1,
      "fromState": "Initial",
      "toState": "IDLE",
      "event": "Start",
      "timestamp": "03:43:18.409",
      "duration": 0,
      "machineId": "call-002"
    },
    {
      "stepNumber": 1,
      "fromState": "Initial",
      "toState": "IDLE",
      "event": "Start",
      "timestamp": "03:43:18.473",
      "duration": 0,
      "machineId": "call-003"
    }
  ]
}];

console.log('=== Testing StateTreeView Rendering ===\n');
console.log('Input: 1 state instance (IDLE) with 3 transitions\n');

// Simulate what StateTreeView.map would do
console.log('StateTreeView would render:');
console.log('------------------------------');

stateInstances.forEach((instance, idx) => {
  const stateKey = `${instance.state}-${instance.instanceNumber}`;
  const transitionCount = instance.transitions?.length || 0;
  
  console.log(`\n[State Header ${idx + 1}]`);
  console.log(`  Key: ${stateKey}-${idx}`);
  console.log(`  ▶ ${instance.state}` + (instance.instanceNumber > 1 ? ` #${instance.instanceNumber}` : ''));
  console.log(`  Badge: "${transitionCount} events"`);
  console.log(`  Style: Gray background, green left border`);
  
  console.log(`\n  [Nested Transitions when expanded]`);
  instance.transitions.forEach((transition, tIdx) => {
    console.log(`    - Transition ${tIdx + 1}:`);
    console.log(`      #${transition.stepNumber} 🎯 ${transition.event}`);
    console.log(`      ${transition.fromState} → ${transition.toState}`);
    console.log(`      Machine: ${transition.machineId}`);
    console.log(`      Time: ${transition.timestamp}`);
  });
});

console.log('\n\n=== What the user SHOULD see ===');
console.log('State Tree');
console.log('  [Expand All] [Collapse All]');
console.log('  ');
console.log('  ▶ IDLE                    [3 events]');
console.log('     |');
console.log('     +-- #1 🎯 Start  Initial → IDLE  call-001  03:43:18.333');
console.log('     +-- #1 🎯 Start  Initial → IDLE  call-002  03:43:18.409');
console.log('     +-- #1 🎯 Start  Initial → IDLE  call-003  03:43:18.473');

console.log('\n\n=== What the user IS seeing ===');
console.log('State Tree');
console.log('  [Expand All] [Collapse All]');
console.log('  ');
console.log('  #1 🎯 Start  Initial → IDLE  call-001  03:43:18.333');
console.log('  #1 🎯 Start  Initial → IDLE  call-002  03:43:18.409');
console.log('  #1 🎯 Start  Initial → IDLE  call-003  03:43:18.473');
console.log('\n❌ The IDLE state header is missing!');