package com.telcobright.testrunner;

import com.telcobright.statemachine.FluentStateMachineBuilder;
import com.telcobright.statemachine.GenericStateMachine;

/**
 * Test class to verify generic type safety and absence of unchecked warnings
 */
public class GenericTypeTest {
    
    public static void main(String[] args) {
        // Example 1: Type-safe state machine with String context
        GenericStateMachine<String> stringMachine = FluentStateMachineBuilder.<String>create("string-machine")
            .initialState("START")
            .state("START")
                .on(TestEvent.class).to("END")
                .done()
            .state("END")
                .done()
            .build();
        
        // Set typed context - no casting needed
        stringMachine.setContext("Hello, World!");
        String context = stringMachine.getContext(); // Type-safe, no cast
        System.out.println("String context: " + context);
        
        // Example 2: Type-safe state machine with custom context
        GenericStateMachine<UserContext> userMachine = FluentStateMachineBuilder.<UserContext>create("user-machine")
            .initialState("LOGGED_OUT")
            .state("LOGGED_OUT")
                .on(LoginEvent.class).to("LOGGED_IN")
                .done()
            .state("LOGGED_IN")
                .on(LogoutEvent.class).to("LOGGED_OUT")
                .done()
            .build();
        
        // Set typed context
        UserContext userContext = new UserContext("John Doe", "john@example.com");
        userMachine.setContext(userContext);
        
        // Access context without casting
        UserContext retrievedContext = userMachine.getContext();
        System.out.println("User: " + retrievedContext.getName());
        
        System.out.println("✅ Generic type safety working correctly!");
    }
    
    // Test context class
    static class UserContext {
        private final String name;
        private final String email;
        
        public UserContext(String name, String email) {
            this.name = name;
            this.email = email;
        }
        
        public String getName() { return name; }
        public String getEmail() { return email; }
    }
    
    // Test events
    static class TestEvent extends com.telcobright.statemachine.events.GenericStateMachineEvent {
        public TestEvent() { super("TEST"); }
    }
    
    static class LoginEvent extends com.telcobright.statemachine.events.GenericStateMachineEvent {
        public LoginEvent() { super("LOGIN"); }
    }
    
    static class LogoutEvent extends com.telcobright.statemachine.events.GenericStateMachineEvent {
        public LogoutEvent() { super("LOGOUT"); }
    }
}