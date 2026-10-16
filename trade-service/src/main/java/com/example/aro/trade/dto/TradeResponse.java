package com.example.aro.trade.dto;

import java.math.BigDecimal;
import java.time.Instant;

public class TradeResponse {

    private String tradeId;
    private String status;
    private String accountId;
    private String symbol;
    private String side;
    private int quantity;
    private BigDecimal price;
    private BigDecimal totalValue;
    private String portfolioStatus;
    private String timestamp;

    public TradeResponse() {
    }

    public TradeResponse(String tradeId, String status, String accountId, String symbol, String side, int quantity, BigDecimal price, String portfolioStatus) {
        this.tradeId = tradeId;
        this.status = status;
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.totalValue = price.multiply(BigDecimal.valueOf(quantity));
        this.portfolioStatus = portfolioStatus;
        this.timestamp = Instant.now().toString();
    }

    public String getTradeId() {
        return tradeId;
    }

    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(BigDecimal totalValue) {
        this.totalValue = totalValue;
    }

    public String getPortfolioStatus() {
        return portfolioStatus;
    }

    public void setPortfolioStatus(String portfolioStatus) {
        this.portfolioStatus = portfolioStatus;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
