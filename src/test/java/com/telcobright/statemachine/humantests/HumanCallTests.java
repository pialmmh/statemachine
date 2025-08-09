package com.telcobright.statemachine.humantests;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.StateMachineRegistry;
import com.telcobright.statemachine.events.GenericStateMachineEvent;
import com.telcobright.statemachineexamples.callmachine.CallMachine;
import com.telcobright.statemachineexamples.callmachine.CallState;
import com.telcobright.statemachineexamples.callmachine.CallContext;
import com.telcobright.statemachineexamples.callmachine.entity.CallEntity;
import com.telcobright.statemachineexamples.callmachine.events.*;

import org.junit.jupiter.api.*;

import java.sql.SQLException;
import java.util.UUID;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class HumanCallTests extends HumanTestBase {

    private String newCallId() {
        return "CALL_" + UUID.randomUUID();
    }

    private CallEntity newCallEntity(String id, String from, String to) {
        return new CallEntity(id, CallState.IDLE.toString(), from, to);
    }

    private CallContext newCallContext(String id, String from, String to) {
        return new CallContext(id, from, to);
    }

    @Test
    @Order(1)
    public void basicCallFlow() throws Exception {
        String callId = newCallId();
        String from = "+1555000333";
        String to = "+1555000444";

        GenericStateMachine<CallEntity, CallContext> machine = CallMachine.create(callId);
        CallEntity entity = newCallEntity(callId, from, to);
        CallContext context = newCallContext(callId, from, to);
        machine.setPersistingEntity(entity);
        machine.setContext(context);

        machine.start();
        snapshot("CALL", callId, machine.getCurrentState(), entity, context, "START");

        machine.fire(IncomingCall.class.getSimpleName());
        snapshot("CALL", callId, machine.getCurrentState(), entity, context, "IncomingCall");

        machine.fire(Answer.class.getSimpleName());
        snapshot("CALL", callId, machine.getCurrentState(), entity, context, "Answer");

        machine.fire(Hangup.class.getSimpleName());
        snapshot("CALL", callId, machine.getCurrentState(), entity, context, "Hangup");

        Assertions.assertEquals(CallState.ENDED.toString(), machine.getCurrentState());
        db.logTestExecution(testRunId, getClass().getSimpleName(), "basicCallFlow", "CALL_MACHINE", callId,
                "PASSED", null, 1, 1);
    }

    @Test
    @Order(2)
    public void callForwardingScenario() throws Exception {
        String callId = newCallId();
        String from = "+1555000123";
        String to = "+1555000456";

        GenericStateMachine<CallEntity, CallContext> machine = CallMachine.create(callId);
        CallEntity entity = newCallEntity(callId, from, to);
        CallContext context = newCallContext(callId, from, to);
        machine.setPersistingEntity(entity);
        machine.setContext(context);

        machine.start();
        snapshot("CALL", callId, machine.getCurrentState(), entity, context, "START");

        machine.fire(IncomingCall.class.getSimpleName());
        machine.fire(Forward.class.getSimpleName());
        snapshot("CALL", callId, machine.getCurrentState(), entity, context, "Forward");

        machine.fire(Answer.class.getSimpleName());
        machine.fire(Hangup.class.getSimpleName());
        snapshot("CALL", callId, machine.getCurrentState(), entity, context, "Hangup");

        Assertions.assertEquals(CallState.ENDED.toString(), machine.getCurrentState());
        db.logTestExecution(testRunId, getClass().getSimpleName(), "callForwardingScenario", "CALL_MACHINE", callId,
                "PASSED", null, 1, 1);
    }

    private void snapshot(String type, String id, String state, CallEntity entity, CallContext ctx, String event)
            throws SQLException {
        db.saveStateSnapshot(testRunId, id, type, state, toJson(entity), toJson(ctx), event,
                "human-like flow");
    }
}
