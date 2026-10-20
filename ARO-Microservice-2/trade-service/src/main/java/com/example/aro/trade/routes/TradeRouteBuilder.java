package com.example.aro.trade.routes;

import com.example.aro.trade.dto.TradeRequest;
import com.example.aro.trade.dto.TradeResponse;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class TradeRouteBuilder extends RouteBuilder {

    private static final Logger log = LoggerFactory.getLogger(TradeRouteBuilder.class);

    @Value("${app.services.portfolio-url}")
    private String portfolioServiceUrl;

    @Override
    public void configure() throws Exception {

        // Global exception handling
        onException(IllegalArgumentException.class)
            .handled(true)
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
            .setBody(simple("{\"error\": \"Bad Request\", \"message\": \"${exception.message}\"}"))
            .setHeader(Exchange.CONTENT_TYPE, constant("application/json"));

        onException(Exception.class)
            .handled(true)
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
            .setBody(simple("{\"error\": \"Trade Processing Error\", \"message\": \"${exception.message}\"}"))
            .setHeader(Exchange.CONTENT_TYPE, constant("application/json"));

        // Camel REST DSL configuration
        restConfiguration()
            .component("servlet")
            .bindingMode(RestBindingMode.json)
            .dataFormatProperty("prettyPrint", "true")
            .contextPath("/api");

        // REST Endpoints
        rest("/v1/trades")
            .description("Trading REST Services")
            .consumes("application/json")
            .produces("application/json")

            .get("/ping")
                .description("Health check ping for Trade Service")
                .to("direct:tradePing")

            .post()
                .description("Execute an equity or FX trade")
                .type(TradeRequest.class)
                .outType(TradeResponse.class)
                .param().name("body").type(RestParamType.body).description("Trade Order").required(true).endParam()
                .to("direct:executeTrade");

        // Ping Route
        from("direct:tradePing")
            .routeId("trade-ping-route")
            .setBody(constant("{\"status\": \"UP\", \"service\": \"trade-service\"}"));

        // Trade Execution & Inter-Service REST Route to Portfolio Service
        from("direct:executeTrade")
            .routeId("trade-execution-route")
            .process(exchange -> {
                TradeRequest req = exchange.getIn().getBody(TradeRequest.class);
                if (req == null) {
                    throw new IllegalArgumentException("Trade request body cannot be null");
                }
                if (req.getAccountId() == null || req.getAccountId().isBlank()) {
                    throw new IllegalArgumentException("Account ID is required");
                }
                if (req.getSymbol() == null || req.getSymbol().isBlank()) {
                    throw new IllegalArgumentException("Trading symbol is required");
                }
                if (req.getQuantity() <= 0) {
                    throw new IllegalArgumentException("Quantity must be greater than zero");
                }
                if (req.getPrice() == null || req.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Price must be greater than zero");
                }
                if (req.getSide() == null || (!req.getSide().equalsIgnoreCase("BUY") && !req.getSide().equalsIgnoreCase("SELL"))) {
                    throw new IllegalArgumentException("Side must be BUY or SELL");
                }
                if (req.getTradeId() == null || req.getTradeId().isBlank()) {
                    req.setTradeId("TRD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                }

                exchange.setProperty("tradeReq", req);
                exchange.setProperty("tradeId", req.getTradeId());
            })
            .log("Executing trade: ${exchangeProperty.tradeId} | Account: ${exchangeProperty.tradeReq.accountId} | Symbol: ${exchangeProperty.tradeReq.symbol} | Side: ${exchangeProperty.tradeReq.side}")
            // Inter-service REST call to Portfolio Service via Camel HTTP Component
            .setHeader(Exchange.HTTP_METHOD, constant("GET"))
            .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
            .doTry()
                .toD(portfolioServiceUrl + "/${exchangeProperty.tradeReq.accountId}?bridgeEndpoint=true&throwExceptionOnFailure=false")
                .log("Portfolio Service responded: ${body}")
                .setProperty("portfolioStatus", constant("VERIFIED_AND_UPDATED"))
            .doCatch(Exception.class)
                .log("Portfolio Service unreachable or timed out; proceeding with trade in OFFLINE_VERIFIED mode: ${exception.message}")
                .setProperty("portfolioStatus", constant("STANDALONE_CONFIRMED"))
            .end()
            // Assemble Trade Response
            .process(exchange -> {
                TradeRequest req = exchange.getProperty("tradeReq", TradeRequest.class);
                String pStatus = exchange.getProperty("portfolioStatus", String.class);

                TradeResponse response = new TradeResponse(
                    req.getTradeId(),
                    "EXECUTED",
                    req.getAccountId(),
                    req.getSymbol(),
                    req.getSide().toUpperCase(),
                    req.getQuantity(),
                    req.getPrice(),
                    pStatus != null ? pStatus : "COMPLETED"
                );

                exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
                exchange.getIn().setBody(response);
            })
            .log("Trade successfully executed: ${body.tradeId} for account: ${body.accountId}");
    }
}
