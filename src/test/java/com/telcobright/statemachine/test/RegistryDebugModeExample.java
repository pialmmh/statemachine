package com.telcobright.statemachine.test;

/**
 * Example demonstrating StateMachineRegistry debug mode control
 * 
 * This shows how debug mode should be controlled at the registry level:
 * 1. Registry has separate enableSnapshotDebug() and enableLiveDebug() methods
 * 2. Registry automatically applies debug mode to all registered machines  
 * 3. Direct machine.enableDebug() is deprecated
 * 4. Users should use registry debug methods instead
 */
public class RegistryDebugModeExample {
    
    public static void main(String[] args) {
        System.out.println("📋 StateMachineRegistry Debug Mode Control");
        System.out.println();
        
        showProperUsage();
        System.out.println();
        showDeprecatedUsage();
    }
    
    private static void showProperUsage() {
        System.out.println("✅ PROPER USAGE - Registry Controls Debug Mode:");
        System.out.println();
        
        System.out.println("// 1. Enable snapshot debugging at registry level");
        System.out.println("StateMachineRegistry registry = new StateMachineRegistry();");
        System.out.println("registry.enableSnapshotDebug(); // 📸 Snapshot debug ENABLED for all machines");
        System.out.println();
        
        System.out.println("// 2. Enable live debugging with WebSocket");
        System.out.println("registry.enableLiveDebug(9999); // 🔴 Live debug ENABLED on port 9999");
        System.out.println();
        
        System.out.println("// 3. Create and register machines - debug modes applied automatically");
        System.out.println("GenericStateMachine<CallEntity, CallContext> machine = CallMachine.create(\"call-001\");");
        System.out.println("registry.register(\"call-001\", machine); // 📊 Applied debug modes to machine: call-001");
        System.out.println();
        
        System.out.println("// 4. All machines registered with this registry get debug modes");
        System.out.println("GenericStateMachine<CallEntity, CallContext> machine2 = CallMachine.create(\"call-002\");");
        System.out.println("registry.register(\"call-002\", machine2); // 📊 Applied debug modes to machine: call-002");
        System.out.println();
        
        System.out.println("// 5. Factory methods also apply debug modes automatically");
        System.out.println("registry.create(\"call-003\", () -> CallMachine.create(\"call-003\"));");
        System.out.println("// 📊 Applied debug modes to machine: call-003");
        System.out.println();
        
        System.out.println("// 6. You can enable/disable debug modes independently");
        System.out.println("registry.disableSnapshotDebug(); // Disable snapshot only");
        System.out.println("registry.disableLiveDebug();     // Disable live only");
        System.out.println("registry.disableAllDebug();      // Disable all debugging");
    }
    
    private static void showDeprecatedUsage() {
        System.out.println("⚠️  DEPRECATED USAGE - Direct Machine Debug Mode:");
        System.out.println();
        
        System.out.println("// DON'T DO THIS - Direct debug enablement is deprecated");
        System.out.println("GenericStateMachine<CallEntity, CallContext> machine = CallMachine.create(\"call-001\");");
        System.out.println("machine.enableDebug(snapshotRecorder); // ⚠️ WARNING: Direct debug mode enablement is deprecated!");
        System.out.println("                                       //    Use registry debug methods instead.");
        System.out.println();
        
        System.out.println("// INSTEAD, USE REGISTRY:");
        System.out.println("StateMachineRegistry registry = new StateMachineRegistry();");
        System.out.println("registry.enableSnapshotDebug();     // ✅ For snapshot debugging");
        System.out.println("registry.enableLiveDebug(9999);     // ✅ For live debugging");
        System.out.println("registry.register(\"call-001\", machine); // Debug modes applied automatically");
    }
    
    // This would be the actual usage in CallMachineTestRunner:
    public static void demonstrateUsageInTestRunner() {
        System.out.println("📞 Usage in CallMachineTestRunner:");
        System.out.println();
        System.out.println("public static void main(String[] args) {");
        System.out.println("    // 1. Create registry and enable debug modes");
        System.out.println("    StateMachineRegistry registry = new StateMachineRegistry();");
        System.out.println("    registry.enableSnapshotDebug();  // 📸 Enable snapshot debugging");
        System.out.println("    registry.enableLiveDebug(9999);  // 🔴 Enable live debugging");
        System.out.println();
        System.out.println("    // 2. Create machines through registry");
        System.out.println("    GenericStateMachine<CallEntity, CallContext> machine = ");
        System.out.println("        registry.create(\"call-001\", () -> CallMachine.create(\"call-001\"));");
        System.out.println();
        System.out.println("    // 3. Set context and fire events - monitoring happens automatically");
        System.out.println("    CallContext context = new CallContext(\"call-001\", \"+1234\", \"+5678\");");
        System.out.println("    machine.setContext(context);");
        System.out.println("    machine.fire(new IncomingCall(\"+1234\")); // Monitored automatically");
        System.out.println("    machine.fire(new Answer());               // Monitored automatically");
        System.out.println("    machine.fire(new Hangup());               // Monitored automatically");
        System.out.println("}");
    }
}