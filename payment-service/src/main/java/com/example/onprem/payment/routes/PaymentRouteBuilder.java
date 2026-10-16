package com.example.onprem.payment.routes;

import com.example.onprem.payment.dto.PaymentRequest;
import com.example.onprem.payment.dto.PaymentResponse;
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
public class PaymentRouteBuilder extends RouteBuilder {

    private static final Logger log = LoggerFactory.getLogger(PaymentRouteBuilder.class);

    @Value("${app.kafka.bootstrap-servers}")
    private String kafkaBrokers;

    @Value("${app.kafka.topic}")
    private String kafkaTopic;

    @Value("${app.kafka.security-protocol:PLAINTEXT}")
    private String kafkaSecurityProtocol;

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
            .setBody(simple("{\"error\": \"Internal Server Error\", \"message\": \"${exception.message}\"}"))
            .setHeader(Exchange.CONTENT_TYPE, constant("application/json"));

        // Camel REST DSL configuration
        restConfiguration()
            .component("servlet")
            .bindingMode(RestBindingMode.json)
            .dataFormatProperty("prettyPrint", "true")
            .contextPath("/api");

        // REST Endpoints
        rest("/v1/payments")
            .description("Payment REST Services")
            .consumes("application/json")
            .produces("application/json")

            .get("/ping")
                .description("Health check ping for Payment Service")
                .to("direct:paymentPing")

            .post()
                .description("Submit a new payment transaction")
                .type(PaymentRequest.class)
                .outType(PaymentResponse.class)
                .param().name("body").type(RestParamType.body).description("Payment Details").required(true).endParam()
                .to("direct:processPayment");

        // Ping Route
        from("direct:paymentPing")
            .routeId("payment-ping-route")
            .setBody(constant("{\"status\": \"UP\", \"service\": \"payment-service\"}"));

        // Payment Processing & Kafka Producer Route
        from("direct:processPayment")
            .routeId("payment-process-route")
            .process(exchange -> {
                PaymentRequest req = exchange.getIn().getBody(PaymentRequest.class);
                if (req == null) {
                    throw new IllegalArgumentException("Payment request body is required");
                }
                if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Payment amount must be greater than zero");
                }
                if (req.getSourceAccount() == null || req.getSourceAccount().isBlank()) {
                    throw new IllegalArgumentException("Source account cannot be empty");
                }
                if (req.getDestinationAccount() == null || req.getDestinationAccount().isBlank()) {
                    throw new IllegalArgumentException("Destination account cannot be empty");
                }
                if (req.getPaymentId() == null || req.getPaymentId().isBlank()) {
                    req.setPaymentId(UUID.randomUUID().toString());
                }
                if (req.getCurrency() == null || req.getCurrency().isBlank()) {
                    req.setCurrency("USD");
                }

                exchange.setProperty("paymentId", req.getPaymentId());
                exchange.setProperty("paymentReq", req);
            })
            .log("Processing payment: ID=${exchangeProperty.paymentId}, Amount=${exchangeProperty.paymentReq.amount} ${exchangeProperty.paymentReq.currency}")
            // Marshal request to JSON payload for Kafka message
            .marshal().json()
            .setHeader("kafka.KEY", simple("${exchangeProperty.paymentId}"))
            // Send to AMQ Streams (Kafka) topic
            .toD("kafka:" + kafkaTopic + "?brokers=" + kafkaBrokers + "&securityProtocol=" + kafkaSecurityProtocol)
            .log("Payment published to AMQ Streams topic [" + kafkaTopic + "] successfully: ${exchangeProperty.paymentId}")
            // Prepare HTTP 202 Accepted Response
            .process(exchange -> {
                PaymentRequest req = exchange.getProperty("paymentReq", PaymentRequest.class);
                PaymentResponse res = new PaymentResponse(
                    req.getPaymentId(),
                    "ACCEPTED",
                    "Payment accepted for processing and published to AMQ Streams",
                    req.getAmount(),
                    req.getCurrency()
                );
                exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 202);
                exchange.getIn().setBody(res);
            });
    }
}
