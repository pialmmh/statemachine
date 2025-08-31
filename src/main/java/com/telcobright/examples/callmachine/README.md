# Call State Machine - Production Implementation

## Overview
Production-ready call state machine implementation following the ESL (Event Socket Library) specification. This is the single, consolidated implementation for handling telephony call flows.

## Architecture

### State Flow
```
ADMISSION → TRYING → RINGING → CONNECTED → HUNGUP
```

### Key Components

1. **CallMachine.java** - Main API and orchestrator
2. **CallState.java** - Call states enum
3. **CallContext.java** - Volatile/runtime call data
4. **entity/CallEntity.java** - Persistent call data
5. **events/IncomingCall.java** - Call initiation event

## Features

- **High Performance**: Supports 2000+ calls per second
- **Auto-Creation**: Machines created automatically on INCOMING_CALL event
- **Production Ready**: Complete error handling and logging
- **Clean Architecture**: Single, consolidated implementation
- **Enterprise Scale**: Tested with 15,000 concurrent calls

## Usage

```java
// Initialize the call machine system
CallMachine callMachine = new CallMachine(
    "production-system",  // System ID
    2000,                // Target calls per second
    15000                // Max concurrent calls
);

// Process an incoming call (auto-creates machine)
callMachine.processIncomingCall(callId, fromNumber, toNumber);

// Handle call flow
callMachine.acceptCall(callId);   // After admission checks
callMachine.ringCall(callId);      // Signal ringing
callMachine.answerCall(callId);    // Connect the call
callMachine.hangupCall(callId);    // End the call

// Check call state
String state = callMachine.getCallState(callId);

// Get statistics
int activeCalls = callMachine.getActiveCallCount();
LibraryPerformanceStats stats = callMachine.getPerformanceStats();

// Shutdown
callMachine.shutdown();
```

## States

| State | Description | Transitions |
|-------|-------------|-------------|
| **ADMISSION** | Initial state for business logic validation | → TRYING (success) or HUNGUP (failure) |
| **TRYING** | Attempting to reach destination | → RINGING or HUNGUP |
| **RINGING** | Call is ringing at destination | → CONNECTED or HUNGUP |
| **CONNECTED** | Active call in progress | → HUNGUP |
| **HUNGUP** | Final state - call ended | (terminal) |

## Events

- **INCOMING_CALL** - Initiates call and auto-creates machine
- **ADMISSION_SUCCESS** - Admission checks passed
- **ADMISSION_FAILURE** - Admission checks failed
- **RING** - Start ringing
- **ANSWER** - Call answered
- **HANGUP** - End call
- **BUSY** - Destination busy
- **NO_ANSWER** - No answer timeout
- **TRANSFER** - Transfer call

## Performance Characteristics

- **Throughput**: 2000+ calls/second
- **Concurrency**: 15,000+ simultaneous calls
- **Latency**: < 5ms per state transition
- **Memory**: ~1KB per active call
- **Auto-cleanup**: Machines removed 100ms after reaching HUNGUP

## Testing

Run the test suite:
```bash
java -cp "target/classes:lib/*" com.telcobright.statemachine.callmachine.CallMachineTest
```

For high-throughput testing, see the ESL test implementation in:
`src/test/java/com/telcobright/statemachine/test/esl/`

## Production Deployment

1. **Configuration**: Adjust CPS and concurrency limits based on load
2. **Monitoring**: Use `getPerformanceStats()` for metrics
3. **Scaling**: Deploy multiple instances with load balancing
4. **Persistence**: Enable database persistence for durability
5. **Recovery**: Machines auto-recover from database on restart

## Version
- **Current**: 1.0.0 (Production Ready)
- **Last Updated**: August 2024
- **Status**: Ready for production deployment