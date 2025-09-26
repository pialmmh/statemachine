package com.telcobright.statewalk.examples;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * Call Detail Record entity (no child entities)
 */
public class Cdr {

    private String cdrId;
    private String callId;
    private String recordType;
    private LocalDateTime recordTime;
    private Integer duration;
    private String sourceNetwork;
    private String destinationNetwork;
    private BigDecimal chargeAmount;
    private String chargeUnit;
    private String serviceType;

    public Cdr() {
        this.cdrId = java.util.UUID.randomUUID().toString();
        this.recordTime = LocalDateTime.now();
        this.recordType = "VOICE";
        this.chargeUnit = "SECONDS";
    }

    // Getters and setters
    public String getCdrId() {
        return cdrId;
    }

    public void setCdrId(String cdrId) {
        this.cdrId = cdrId;
    }

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    public String getRecordType() {
        return recordType;
    }

    public void setRecordType(String recordType) {
        this.recordType = recordType;
    }

    public LocalDateTime getRecordTime() {
        return recordTime;
    }

    public void setRecordTime(LocalDateTime recordTime) {
        this.recordTime = recordTime;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public String getSourceNetwork() {
        return sourceNetwork;
    }

    public void setSourceNetwork(String sourceNetwork) {
        this.sourceNetwork = sourceNetwork;
    }

    public String getDestinationNetwork() {
        return destinationNetwork;
    }

    public void setDestinationNetwork(String destinationNetwork) {
        this.destinationNetwork = destinationNetwork;
    }

    public BigDecimal getChargeAmount() {
        return chargeAmount;
    }

    public void setChargeAmount(BigDecimal chargeAmount) {
        this.chargeAmount = chargeAmount;
    }

    public String getChargeUnit() {
        return chargeUnit;
    }

    public void setChargeUnit(String chargeUnit) {
        this.chargeUnit = chargeUnit;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }
}