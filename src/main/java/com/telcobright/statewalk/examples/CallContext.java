package com.telcobright.statewalk.examples;

import com.telcobright.statemachine.StateMachineContextEntity;
import com.telcobright.statewalk.annotation.*;

import java.time.LocalDateTime;

/**
 * Example CallContext demonstrating multi-entity graph persistence.
 * This context contains multiple related entities that form an object graph.
 */
public class CallContext implements StateMachineContextEntity<CallContext> {

    // State machine fields
    private String id;
    private String currentState;
    private LocalDateTime lastStateChange;
    private boolean isComplete = false;

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
        this.lastStateChange = LocalDateTime.now();
    }

    public CallContext(String callId) {
        this();
        setId(callId);
    }

    // Implement StateMachineContextEntity methods
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
        // Also set ID in related entities
        if (call != null) call.setCallId(id);
        if (cdr != null) cdr.setCallId(id);
        if (billInfo != null) billInfo.setCallId(id);
    }

    @Override
    public String getCurrentState() {
        return currentState;
    }

    @Override
    public void setCurrentState(String state) {
        this.currentState = state;
    }

    @Override
    public LocalDateTime getLastStateChange() {
        return lastStateChange;
    }

    @Override
    public void setLastStateChange(LocalDateTime lastStateChange) {
        this.lastStateChange = lastStateChange;
    }

    @Override
    public boolean isComplete() {
        return isComplete;
    }

    @Override
    public void setComplete(boolean complete) {
        this.isComplete = complete;
    }

    @Override
    public CallContext deepCopy() {
        CallContext copy = new CallContext();
        copy.id = this.id;
        copy.currentState = this.currentState;
        copy.lastStateChange = this.lastStateChange;
        copy.isComplete = this.isComplete;
        // Note: Related entities are not copied (shallow copy of references)
        copy.call = this.call;
        copy.cdr = this.cdr;
        copy.billInfo = this.billInfo;
        copy.deviceInfo = this.deviceInfo;
        return copy;
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
