package com.telcobright.statewalk.examples;

import com.telcobright.statemachine.StateMachineContextEntity;
import com.telcobright.statewalk.annotation.*;

/**
 * Example CallContext demonstrating multi-entity graph persistence.
 * This context contains multiple related entities that form an object graph.
 */
public class CallContext extends StateMachineContextEntity<String> {

    @Entity(table = "calls", relation = RelationType.ONE_TO_ONE)
    private Call call;

    @Entity(table = "cdrs", relation = RelationType.ONE_TO_ONE)
    private Cdr cdr;

    @Entity(table = "bill_info", relation = RelationType.ONE_TO_ONE)
    private BillInfo billInfo;

    @Singleton(key = "device_info")
    @Entity(table = "device_info")
    private DeviceInfo deviceInfo;

    // Constructor
    public CallContext() {
        super();
    }

    public CallContext(String callId) {
        super();
        setId(callId);
    }

    // Override methods from StateMachineContextEntity
    @Override
    public String getId() {
        return super.getId();
    }

    @Override
    public void setId(String id) {
        super.setId(id);
        // Also set ID in related entities
        if (call != null) call.setCallId(id);
        if (cdr != null) cdr.setCallId(id);
        if (billInfo != null) billInfo.setCallId(id);
    }

    // Getters and setters
    public Call getCall() {
        return call;
    }

    public void setCall(Call call) {
        this.call = call;
        if (call != null && getId() != null) {
            call.setCallId(getId());
        }
    }

    public Cdr getCdr() {
        return cdr;
    }

    public void setCdr(Cdr cdr) {
        this.cdr = cdr;
        if (cdr != null && getId() != null) {
            cdr.setCallId(getId());
        }
    }

    public BillInfo getBillInfo() {
        return billInfo;
    }

    public void setBillInfo(BillInfo billInfo) {
        this.billInfo = billInfo;
        if (billInfo != null && getId() != null) {
            billInfo.setCallId(getId());
        }
    }

    public DeviceInfo getDeviceInfo() {
        return deviceInfo;
    }

    public void setDeviceInfo(DeviceInfo deviceInfo) {
        this.deviceInfo = deviceInfo;
        // DeviceInfo is singleton, shared across the graph
        if (this.call != null) {
            this.call.setDeviceInfo(deviceInfo);
        }
        if (this.billInfo != null) {
            this.billInfo.setDeviceInfo(deviceInfo);
        }
    }

    /**
     * Initialize the context with default entities
     */
    public void initialize() {
        this.call = new Call();
        this.cdr = new Cdr();
        this.billInfo = new BillInfo();
        this.deviceInfo = new DeviceInfo();

        // Set relationships
        if (getId() != null) {
            call.setCallId(getId());
            cdr.setCallId(getId());
            billInfo.setCallId(getId());
        }

        // Share singleton device info
        call.setDeviceInfo(deviceInfo);
        billInfo.setDeviceInfo(deviceInfo);
    }

    @Override
    public String toString() {
        return String.format("CallContext[id=%s, state=%s, call=%s, cdr=%s, billInfo=%s, device=%s]",
            getId(), getCurrentState(),
            call != null ? call.getCallId() : "null",
            cdr != null ? cdr.getCallId() : "null",
            billInfo != null ? billInfo.getPartyNumber() : "null",
            deviceInfo != null ? deviceInfo.getDeviceType() : "null");
    }
}