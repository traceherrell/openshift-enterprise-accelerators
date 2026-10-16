package com.example.aro.trade.dto;

import java.math.BigDecimal;

public class PortfolioPosition {

    private String accountId;
    private String symbol;
    private int currentHoldings;
    private BigDecimal cashBalance;

    public PortfolioPosition() {
    }

    public PortfolioPosition(String accountId, String symbol, int currentHoldings, BigDecimal cashBalance) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.currentHoldings = currentHoldings;
        this.cashBalance = cashBalance;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public int getCurrentHoldings() {
        return currentHoldings;
    }

    public void setCurrentHoldings(int currentHoldings) {
        this.currentHoldings = currentHoldings;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public void setCashBalance(BigDecimal cashBalance) {
        this.cashBalance = cashBalance;
    }
}
