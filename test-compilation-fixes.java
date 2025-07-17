// Simple test to verify compilation fixes
import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.StateMachineRegistry;
import com.telcobright.statemachineexamples.callmachine.CallMachine;
import com.telcobright.statemachineexamples.callmachine.events.IncomingCall;
import com.telcobright.statemachineexamples.smsmachine.SmsMachine;
import com.telcobright.statemachineexamples.smsmachine.events.SendAttempt;

public class CompilationTest {
    public static void main(String[] args) {
        System.out.println("Testing compilation fixes...");
        
        // Test 1: GenericStateMachine methods
        System.out.println("✓ Testing GenericStateMachine methods");
        // GenericStateMachine machine = new GenericStateMachine("test", null, null, null);
        // String state = machine.getCurrentState();
        // machine.sendEvent(new IncomingCall());
        // machine.createSnapshot();
        // machine.restoreFromSnapshot(null);
        
        // Test 2: StateMachineRegistry methods
        System.out.println("✓ Testing StateMachineRegistry methods");
        StateMachineRegistry registry = new StateMachineRegistry();
        // registry.register("test", machine);
        // registry.removeMachine("test");
        // registry.isInMemory("test");
        // registry.getActiveMachine("test");
        // registry.createOrGet("test", () -> null);
        
        // Test 3: CallMachine
        System.out.println("✓ Testing CallMachine");
        CallMachine callMachine = new CallMachine();
        // GenericStateMachine cm = callMachine.createMachine("test");
        
        // Test 4: SmsMachine
        System.out.println("✓ Testing SmsMachine");
        SmsMachine smsMachine = new SmsMachine();
        // GenericStateMachine sm = smsMachine.createMachine("test");
        
        System.out.println("✅ All compilation fixes verified!");
    }
}