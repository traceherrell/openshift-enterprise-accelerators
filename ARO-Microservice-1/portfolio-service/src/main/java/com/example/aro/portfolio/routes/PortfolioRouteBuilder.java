package com.example.aro.portfolio.routes;

import com.example.aro.portfolio.dto.PortfolioPosition;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PortfolioRouteBuilder extends RouteBuilder {

    private static final Logger log = LoggerFactory.getLogger(PortfolioRouteBuilder.class);

    private final Map<String, PortfolioPosition> accountStore = new ConcurrentHashMap<>();

    public PortfolioRouteBuilder() {
        // Initialize default sample accounts
        accountStore.put("ACC-1001", new PortfolioPosition(
            "ACC-1001",
            new BigDecimal("500000.00"),
            "USD",
            new HashMap<>(Map.of("AAPL", 500, "MSFT", 300, "NVDA", 150)),
            "ACTIVE"
        ));
        accountStore.put("ACC-1002", new PortfolioPosition(
            "ACC-1002",
            new BigDecimal("1250000.00"),
            "USD",
            new HashMap<>(Map.of("GOOGL", 250, "AMZN", 400, "TSLA", 100)),
            "ACTIVE"
        ));
    }

    @Override
    public void configure() throws Exception {

        // Global Exception Handling
        onException(Exception.class)
            .handled(true)
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
            .setBody(simple("{\"error\": \"Portfolio Service Error\", \"message\": \"${exception.message}\"}"))
            .setHeader(Exchange.CONTENT_TYPE, constant("application/json"));

        // Camel REST DSL configuration
        restConfiguration()
            .component("servlet")
            .bindingMode(RestBindingMode.json)
            .dataFormatProperty("prettyPrint", "true")
            .contextPath("/api");

        // REST Endpoints
        rest("/v1/portfolios")
            .description("Portfolio Management Services")
            .produces("application/json")

            .get("/ping")
                .description("Health ping for Portfolio Service")
                .to("direct:portfolioPing")

            .get("/{accountId}")
                .description("Retrieve portfolio holdings by account ID")
                .param().name("accountId").type(RestParamType.path).description("Account identifier").required(true).endParam()
                .to("direct:getPortfolio");

        // Ping Route
        from("direct:portfolioPing")
            .routeId("portfolio-ping-route")
            .setBody(constant("{\"status\": \"UP\", \"service\": \"portfolio-service\"}"));

        // Get Portfolio Route
        from("direct:getPortfolio")
            .routeId("portfolio-lookup-route")
            .process(exchange -> {
                String accountId = exchange.getIn().getHeader("accountId", String.class);
                if (accountId == null || accountId.isBlank()) {
                    accountId = "ACC-1001";
                }

                PortfolioPosition position = accountStore.computeIfAbsent(accountId, id -> new PortfolioPosition(
                    id,
                    new BigDecimal("100000.00"),
                    "USD",
                    new HashMap<>(Map.of("CASH", 1)),
                    "INITIALIZED"
                ));

                log.info("Retrieved portfolio for account: {} | Cash balance: {} {}",
                    accountId, position.getCashBalance(), position.getCurrency());

                exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
                exchange.getIn().setBody(position);
            });
    }
}
