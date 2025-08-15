package com.telcobright.statemachine.examples;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.config.RegistryConfig;
import com.telcobright.statemachine.timeout.TimeoutManager;

/**
 * Examples showing how to use the registry with the new snapshotDebug and liveDebug flags
 * No more enableDebugMode - everything is controlled via configuration
 */
public class RegistryUsageExample {
    
    public static void main(String[] args) {
        System.out.println("=== Registry Usage Examples (No enableDebugMode) ===\n");
        
        example1_NoDebugging();
        example2_SnapshotOnly();
        example3_LiveOnly();
        example4_BothEnabled();
        example5_ConfigurationBased();
        example6_DirectMethods();
    }
    
    /**
     * Example 1: No debugging (production mode)
     */
    private static void example1_NoDebugging() {
        System.out.println("Example 1: No Debugging (Production)");
        System.out.println("-------------------------------------");
        
        // Create registry without any debugging
        StateMachineRegistry registry = new StateMachineRegistry();
        
        // No debugging flags are set
        System.out.println("Snapshot Debug: " + registry.isSnapshotDebugEnabled()); // false
        System.out.println("Live Debug: " + registry.isLiveDebugEnabled());         // false
        System.out.println("Any Debug: " + registry.isDebugEnabled());              // false
        System.out.println();
    }
    
    /**
     * Example 2: Only snapshot debugging
     */
    private static void example2_SnapshotOnly() {
        System.out.println("Example 2: Snapshot Debug Only");
        System.out.println("-------------------------------");
        
        StateMachineRegistry registry = new StateMachineRegistry();
        
        // Enable only snapshot debugging
        registry.enableSnapshotDebug();
        
        System.out.println("Snapshot Debug: " + registry.isSnapshotDebugEnabled()); // true
        System.out.println("Live Debug: " + registry.isLiveDebugEnabled());         // false
        System.out.println("Any Debug: " + registry.isDebugEnabled());              // true
        System.out.println();
    }
    
    /**
     * Example 3: Only live debugging
     */
    private static void example3_LiveOnly() {
        System.out.println("Example 3: Live Debug Only");
        System.out.println("---------------------------");
        
        StateMachineRegistry registry = new StateMachineRegistry();
        
        // Enable only live debugging
        registry.enableLiveDebug(8888);
        
        System.out.println("Snapshot Debug: " + registry.isSnapshotDebugEnabled()); // false
        System.out.println("Live Debug: " + registry.isLiveDebugEnabled());         // true
        System.out.println("Any Debug: " + registry.isDebugEnabled());              // true
        
        // Clean up
        registry.disableLiveDebug();
        System.out.println();
    }
    
    /**
     * Example 4: Both debugging modes enabled
     */
    private static void example4_BothEnabled() {
        System.out.println("Example 4: Both Debug Modes");
        System.out.println("----------------------------");
        
        StateMachineRegistry registry = new StateMachineRegistry();
        
        // Enable both debugging modes
        registry.enableSnapshotDebug();
        registry.enableLiveDebug(9999);
        
        System.out.println("Snapshot Debug: " + registry.isSnapshotDebugEnabled()); // true
        System.out.println("Live Debug: " + registry.isLiveDebugEnabled());         // true
        System.out.println("Any Debug: " + registry.isDebugEnabled());              // true
        
        // Disable all debugging
        registry.disableAllDebug();
        System.out.println("After disableAllDebug:");
        System.out.println("Any Debug: " + registry.isDebugEnabled());              // false
        System.out.println();
    }
    
    /**
     * Example 5: Using ConfigurableStateMachineRegistry
     */
    private static void example5_ConfigurationBased() {
        System.out.println("Example 5: Configuration-Based Registry");
        System.out.println("----------------------------------------");
        
        // Using builder pattern
        ConfigurableStateMachineRegistry registry = new ConfigurableStateMachineRegistry.Builder()
            .withSnapshotDebug(true)    // Enable snapshot
            .withLiveDebug(false)        // Disable live
            .withEventLogging(true)
            .build();
        
        System.out.println("Configuration applied via builder");
        
        // Using pre-configured profiles
        ConfigurableStateMachineRegistry devRegistry = ConfigurableStateMachineRegistry.development();
        System.out.println("Development registry created with full debugging");
        
        ConfigurableStateMachineRegistry prodRegistry = ConfigurableStateMachineRegistry.production();
        System.out.println("Production registry created with no debugging");
        System.out.println();
    }
    
    /**
     * Example 6: Direct method usage
     */
    private static void example6_DirectMethods() {
        System.out.println("Example 6: Direct Method Control");
        System.out.println("---------------------------------");
        
        AbstractStateMachineRegistry registry = new StateMachineRegistry();
        
        // Start with no debugging
        System.out.println("Initial state: No debugging");
        
        // Enable snapshot debugging
        registry.enableSnapshotDebug();
        System.out.println("After enableSnapshotDebug()");
        
        // Add live debugging
        registry.enableLiveDebug(7777);
        System.out.println("After enableLiveDebug(7777)");
        
        // Disable snapshot only
        registry.disableSnapshotDebug();
        System.out.println("After disableSnapshotDebug() - only live remains");
        
        // Disable live
        registry.disableLiveDebug();
        System.out.println("After disableLiveDebug() - all debugging off");
        
        System.out.println("\n✅ No more enableDebugMode() - use specific flags!");
    }
}