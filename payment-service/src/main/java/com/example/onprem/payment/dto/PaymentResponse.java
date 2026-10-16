package com.example.onprem.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;

public class PaymentResponse {

    private String paymentId;
    private String status;
    private String message;
    private BigDecimal amount;
    private String currency;
    private String timestamp;

    public PaymentResponse() {
    }

    public PaymentResponse(String paymentId, String status, String message, BigDecimal amount, String currency) {
        this.paymentId = paymentId;
        this.status = status;
        this.message = message;
        this.amount = amount;
        this.currency = currency;
        this.timestamp = Instant.now().toString();
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
