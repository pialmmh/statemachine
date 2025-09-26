package com.telcobright.statewalk.examples;

import com.telcobright.statewalk.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Billing information entity with party relationship
 */
public class BillInfo {

    private String billId;
    private String callId;
    private String accountNumber;
    private BigDecimal totalAmount;
    private String currency;
    private LocalDateTime billingDate;
    private String billingPeriod;

    @Entity(table = "party", relation = RelationType.ONE_TO_ONE)
    private Party party;

    private DeviceInfo deviceInfo; // Shared singleton instance

    // Additional billing fields
    private BigDecimal taxAmount;
    private BigDecimal discountAmount;
    private String paymentMethod;
    private String invoiceNumber;

    public BillInfo() {
        this.billId = java.util.UUID.randomUUID().toString();
        this.billingDate = LocalDateTime.now();
        this.currency = "USD";
        this.totalAmount = BigDecimal.ZERO;
        this.taxAmount = BigDecimal.ZERO;
        this.discountAmount = BigDecimal.ZERO;
    }

    // Helper methods
    public String getPartyNumber() {
        return party != null ? party.getPhoneNumber() : null;
    }

    public void calculateTotal() {
        if (totalAmount != null && taxAmount != null && discountAmount != null) {
            BigDecimal subtotal = totalAmount.add(taxAmount).subtract(discountAmount);
            this.totalAmount = subtotal.max(BigDecimal.ZERO);
        }
    }

    // Getters and setters
    public String getBillId() {
        return billId;
    }

    public void setBillId(String billId) {
        this.billId = billId;
    }

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDateTime getBillingDate() {
        return billingDate;
    }

    public void setBillingDate(LocalDateTime billingDate) {
        this.billingDate = billingDate;
    }

    public String getBillingPeriod() {
        return billingPeriod;
    }

    public void setBillingPeriod(String billingPeriod) {
        this.billingPeriod = billingPeriod;
    }

    public Party getParty() {
        return party;
    }

    public void setParty(Party party) {
        this.party = party;
        if (party != null) {
            party.setBillId(this.billId);
        }
    }

    public DeviceInfo getDeviceInfo() {
        return deviceInfo;
    }

    public void setDeviceInfo(DeviceInfo deviceInfo) {
        this.deviceInfo = deviceInfo;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }
}