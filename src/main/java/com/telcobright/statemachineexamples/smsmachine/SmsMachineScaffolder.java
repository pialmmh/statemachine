package com.telcobright.statemachineexamples.smsmachine;

  import com.telcobright.statemachine.dsl.StateMachineDsl;

  /**
   * Scaffolding specification for the SmsMachine state machine.
   * Used by code generation tools to generate the package structure and placeholder classes.
   */
  public class SmsMachineScaffolder {

      public static void define() {
          StateMachineDsl.packageBase("com.telcobright.statemachineexamples.smsmachine")
              .define("SmsMachine")
              .persistedIn("mysql")
              .startWith("QUEUED")

              .state("QUEUED")
                  .onEvent("SendAttempt").goTo("SENDING")
                  .endState()

              .state("SENDING")
                  .onEvent("DeliveryReport").goTo("DELIVERED")
                  .onEvent("SendFailed").goTo("FAILED")
                  .stayOn("StatusUpdate", "handleStatusUpdate")
                  .endState()

              .state("DELIVERED")
                  .endState()

              .state("FAILED")
                  .onEvent("Retry").goTo("QUEUED")
                  .endState()

              .contextFields(
                  "messageId:String",
                  "fromNumber:String",
                  "toNumber:String",
                  "messageText:String",
                  "attemptCount:int",
                  "sentAt:LocalDateTime"
              )

              .build();
      }
  }