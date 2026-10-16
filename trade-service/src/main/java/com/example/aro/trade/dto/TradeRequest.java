package com.example.aro.trade.dto;

import java.math.BigDecimal;

public class TradeRequest {

    private String tradeId;
    private String accountId;
    private String symbol;
    private String side; // BUY or SELL
    private int quantity;
    private BigDecimal price;

    public TradeRequest() {
    }

    public TradeRequest(String tradeId, String accountId, String symbol, String side, int quantity, BigDecimal price) {
        this.tradeId = tradeId;
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
    }

    public String getTradeId() {
        return tradeId;
    }

    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
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
}
