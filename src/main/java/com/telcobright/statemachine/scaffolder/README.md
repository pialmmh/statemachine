# State Machine Scaffolder

A Node.js CLI tool that generates Java state machine code from DSL specifications.

## Installation

```bash
cd src/main/java/com/telcobright/statemachine/scaffolder
npm install
npm link  # To make the CLI available globally
```

## Usage

### Generate from existing specification

```bash
statemachine-scaffolder generate -s CallMachineScaffolder.java -o ./src/main/java
```

### Create a new specification template

```bash
statemachine-scaffolder init -n OrderMachine -p com.company.orders
```

## DSL Specification Format

The scaffolder parses Java-like DSL specifications:

```java
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
        .stayOn("SessionProgress", "handleSessionProgress")
        .endState()
        
    .contextFields(
        "callId:String",
        "fromNumber:String"
    )
    
    .build();
```

## Generated Structure

```
com/telcobright/statemachine/examples/callmachine/
├── CallMachine.java                    # Main state machine class
├── CallMachineState.java              # State enum
├── CallContext.java                   # Context data class
├── CallMachineTestRunner.java         # Test runner
├── package-info.java
├── events/
│   ├── IncomingCall.java
│   ├── Answer.java
│   ├── Hangup.java
│   └── CallMachineEventTypeRegistry.java
└── states/
    ├── idle/
    │   ├── OnEntry.java
    │   └── OnExit.java
    ├── ringing/
    │   ├── OnEntry.java
    │   ├── OnExit.java
    │   └── OnSessionProgress_Ringing.java
    └── connected/
        ├── OnEntry.java
        └── OnExit.java
```

## Features

- **Complete Code Generation**: Generates all required Java classes
- **MySQL Persistence**: Includes custom save/load methods when persistence is specified
- **Event Registry**: Generates event type registry to avoid reflection
- **State Handlers**: Creates OnEntry/OnExit handlers for each state
- **Stay Events**: Generates handlers for events that don't cause state transitions
- **Test Runner**: Includes a test runner with example scenarios
- **Profile Support**: Integrates with Spring-like profile configuration

## Templates

The scaffolder uses Handlebars templates to generate:
- State machine class with FluentStateMachineBuilder
- State enum with all defined states
- Context class with Jackson annotations
- Event classes implementing StateMachineEvent
- Event registry for type mapping
- State entry/exit handlers
- Stay event handlers
- Test runner with scenarios

## Customization

Templates can be modified in the `templates` object within `scaffolder.js` to customize the generated code style and structure.