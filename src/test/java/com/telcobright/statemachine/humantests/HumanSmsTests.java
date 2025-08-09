package com.telcobright.statemachine.humantests;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.StateMachineRegistry;
import com.telcobright.statemachine.events.GenericStateMachineEvent;
import com.telcobright.statemachine.persistence.IdLookUpMode;
import com.telcobright.statemachineexamples.smsmachine.SmsContext;
import com.telcobright.statemachineexamples.smsmachine.SmsMachine;
import com.telcobright.statemachineexamples.smsmachine.SmsMachineState;
import com.telcobright.statemachineexamples.smsmachine.entity.SmsEntity;
import com.telcobright.statemachineexamples.smsmachine.events.*;

import org.junit.jupiter.api.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class HumanSmsTests extends HumanTestBase {

    private String newSmsId() {
        // Use timestamp-embedded long as string; fallback to UUID suffix if needed
        long id = com.telcobright.idkit.IdGenerator.generateId();
        return String.valueOf(id);
    }

    private SmsEntity newSmsEntity(String smsId, String text) {
        return new SmsEntity(smsId, SmsMachineState.QUEUED.toString(), "+1555000111", "+1555000222", text);
    }

    private SmsContext newSmsContext(String smsId, String text) {
        return new SmsContext(smsId, "+1555000111", "+1555000222", text);
    }

    @Test
    @Order(1)
    public void basicSmsDeliveryFlow() throws Exception {
        String smsId = newSmsId();
        String text = "Hello, human-like integration test!";

        GenericStateMachine<SmsEntity, SmsContext> machine = SmsMachine.create(smsId);
        SmsEntity entity = newSmsEntity(smsId, text);
        SmsContext context = newSmsContext(smsId, text);
        machine.setPersistingEntity(entity);
        machine.setContext(context);

        machine.start();
        snapshot("SMS", smsId, machine.getCurrentState(), entity, context, "START");

        machine.fire(SendAttempt.class.getSimpleName());
        snapshot("SMS", smsId, machine.getCurrentState(), entity, context, "SendAttempt");

        machine.fire(new GenericStateMachineEvent(DeliveryReport.class.getSimpleName(), "DELIVERED"));
        snapshot("SMS", smsId, machine.getCurrentState(), entity, context, "DeliveryReport");

        Assertions.assertEquals(SmsMachineState.DELIVERED.toString(), machine.getCurrentState());
        db.logTestExecution(testRunId, getClass().getSimpleName(), "basicSmsDeliveryFlow", "SMS_MACHINE", smsId,
                "PASSED", null, 1, 1);
    }

    @Test
    @Order(2)
    public void smsFailureAndRetryFlow() throws Exception {
        String smsId = newSmsId();
        String text = "This message may fail first";

        GenericStateMachine<SmsEntity, SmsContext> machine = SmsMachine.create(smsId);
        SmsEntity entity = newSmsEntity(smsId, text);
        SmsContext context = newSmsContext(smsId, text);
        machine.setPersistingEntity(entity);
        machine.setContext(context);

        machine.start();
        snapshot("SMS", smsId, machine.getCurrentState(), entity, context, "START");

        machine.fire(SendAttempt.class.getSimpleName());
        snapshot("SMS", smsId, machine.getCurrentState(), entity, context, "SendAttempt");

        machine.fire(SendFailed.class.getSimpleName());
        snapshot("SMS", smsId, machine.getCurrentState(), entity, context, "SendFailed");

        machine.fire(Retry.class.getSimpleName());
        snapshot("SMS", smsId, machine.getCurrentState(), entity, context, "Retry");

        machine.fire(new GenericStateMachineEvent(DeliveryReport.class.getSimpleName(), "DELIVERED"));
        snapshot("SMS", smsId, machine.getCurrentState(), entity, context, "DeliveryReport");

        Assertions.assertEquals(SmsMachineState.DELIVERED.toString(), machine.getCurrentState());
        db.logTestExecution(testRunId, getClass().getSimpleName(), "smsFailureAndRetryFlow", "SMS_MACHINE", smsId,
                "PASSED", null, 1, 1);
    }

    @Test
    @Order(3)
    public void bulkSmsProcessing() throws Exception {
        int total = 10;
        int delivered = 0;
        for (int i = 0; i < total; i++) {
            String smsId = newSmsId();
            String text = "Bulk msg #" + i;
            GenericStateMachine<SmsEntity, SmsContext> machine = SmsMachine.create(smsId);
            SmsEntity entity = newSmsEntity(smsId, text);
            SmsContext context = newSmsContext(smsId, text);
            machine.setPersistingEntity(entity);
            machine.setContext(context);
            machine.start();
            machine.fire(SendAttempt.class.getSimpleName());
            // Alternate success/failure
            if (i % 3 == 0) {
                machine.fire(SendFailed.class.getSimpleName());
                machine.fire(Retry.class.getSimpleName());
            }
            machine.fire(new GenericStateMachineEvent(DeliveryReport.class.getSimpleName(), "DELIVERED"));
            if (SmsMachineState.DELIVERED.toString().equals(machine.getCurrentState())) delivered++;
        }
        Assertions.assertEquals(total, delivered);
        db.logTestExecution(testRunId, getClass().getSimpleName(), "bulkSmsProcessing", "SMS_MACHINE", "-",
                "PASSED", null, 1, 1);
    }

    private void snapshot(String type, String id, String state, SmsEntity entity, SmsContext ctx, String event)
            throws SQLException {
        db.saveStateSnapshot(testRunId, id, type, state, toJson(entity), toJson(ctx), event,
                "human-like flow");
    }
}
