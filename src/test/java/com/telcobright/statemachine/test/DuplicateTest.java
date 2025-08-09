package com.telcobright.statemachine.test;

import com.telcobright.statemachine.*;

public class DuplicateTest {
    public static void main(String[] args) {
        try {
            StateMachineRegistry registry = new StateMachineRegistry();
            
            // Create first machine
            GenericStateMachine<RegistryBasedTest.TestEntity, RegistryBasedTest.TestContext> machine1 = 
                registry.create("duplicate-test", () -> {
                    return FluentStateMachineBuilder.<RegistryBasedTest.TestEntity, RegistryBasedTest.TestContext>create("duplicate-test")
                        .initialState("PENDING")
                        .state("PENDING").done()
                        .build();
                });
            System.out.println("✅ First machine created successfully");
            
            // Try to create duplicate - should throw exception
            try {
                registry.create("duplicate-test", () -> null);
                System.out.println("❌ ERROR: Duplicate creation should have failed");
            } catch (IllegalStateException e) {
                System.out.println("✅ Duplicate creation properly rejected: " + e.getMessage());
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}