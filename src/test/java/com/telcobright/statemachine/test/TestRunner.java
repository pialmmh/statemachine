package com.telcobright.statemachine.test;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;

/**
 * Test runner for state machine persistence tests
 * Usage: java -cp classpath TestRunner
 */
public class TestRunner {
    
    public static void main(String[] args) {
        System.out.println("🚀 Running State Machine Persistence Tests");
        System.out.println("MySQL: localhost:3306, User: root");
        System.out.println("=========================================\n");
        
        // Create launcher
        Launcher launcher = LauncherFactory.create();
        
        // Create summary listener
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        
        // Build discovery request
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(selectPackage("com.telcobright.statemachine.test"))
            .build();
        
        // Execute tests
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);
        
        // Print results
        TestExecutionSummary summary = listener.getSummary();
        System.out.println("\n📊 Test Results:");
        System.out.println("================");
        System.out.println("Tests run: " + summary.getTestsFoundCount());
        System.out.println("Tests passed: " + summary.getTestsSucceededCount());
        System.out.println("Tests failed: " + summary.getTestsFailedCount());
        System.out.println("Tests skipped: " + summary.getTestsSkippedCount());
        
        if (summary.getTestsFailedCount() > 0) {
            System.out.println("\n❌ Failed tests:");
            summary.getFailures().forEach(failure -> {
                System.out.println("  - " + failure.getTestIdentifier().getDisplayName());
                System.out.println("    " + failure.getException().getMessage());
            });
        }
        
        System.out.println("\n" + (summary.getTestsFailedCount() == 0 ? "✅ All tests passed!" : "❌ Some tests failed!"));
    }
}

/**
 * Maven command to run tests:
 * mvn test -Dtest=com.telcobright.statemachine.test.**
 * 
 * Gradle command to run tests:
 * ./gradlew test --tests "com.telcobright.statemachine.test.*"
 * 
 * Direct Java command:
 * java -cp target/test-classes:target/classes:lib/* com.telcobright.statemachine.test.TestRunner
 */