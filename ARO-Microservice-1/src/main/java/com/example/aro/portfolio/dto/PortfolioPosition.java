package com.example.aro.portfolio.dto;

import java.math.BigDecimal;
import java.util.Map;

public class PortfolioPosition {

    private String accountId;
    private BigDecimal cashBalance;
    private String currency;
    private Map<String, Integer> holdings;
    private String status;

    public PortfolioPosition() {
    }

    public PortfolioPosition(String accountId, BigDecimal cashBalance, String currency, Map<String, Integer> holdings, String status) {
        this.accountId = accountId;
        this.cashBalance = cashBalance;
        this.currency = currency;
        this.holdings = holdings;
        this.status = status;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public void setCashBalance(BigDecimal cashBalance) {
        this.cashBalance = cashBalance;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Map<String, Integer> getHoldings() {
        return holdings;
    }

    public void setHoldings(Map<String, Integer> holdings) {
        this.holdings = holdings;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
