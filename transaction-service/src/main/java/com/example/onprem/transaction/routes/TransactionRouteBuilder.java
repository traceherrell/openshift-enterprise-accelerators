package com.example.onprem.transaction.routes;

import com.example.onprem.transaction.dto.TransactionEvent;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TransactionRouteBuilder extends RouteBuilder {

    private static final Logger log = LoggerFactory.getLogger(TransactionRouteBuilder.class);

    private final AtomicLong processedCount = new AtomicLong(0);
    private final Map<String, TransactionEvent> recentTransactions = new ConcurrentHashMap<>();

    @Value("${app.kafka.bootstrap-servers}")
    private String kafkaBrokers;

    @Value("${app.kafka.topic}")
    private String kafkaTopic;

    @Value("${app.kafka.group-id}")
    private String kafkaGroupId;

    @Value("${app.kafka.security-protocol:PLAINTEXT}")
    private String kafkaSecurityProtocol;

    @Override
    public void configure() throws Exception {

        // Global Exception Handling
        onException(Exception.class)
            .handled(true)
            .log("Error processing transaction message: ${exception.message}");

        // Camel REST DSL configuration
        restConfiguration()
            .component("servlet")
            .bindingMode(RestBindingMode.json)
            .dataFormatProperty("prettyPrint", "true")
            .contextPath("/api");

        // REST Endpoints
        rest("/v1/transactions")
            .description("Transaction Status & Query Services")
            .produces("application/json")

            .get("/ping")
                .description("Health ping for Transaction Service")
                .to("direct:transactionPing")

            .get("/stats")
                .description("Get transaction processing metrics")
                .to("direct:transactionStats");

        // Ping Route
        from("direct:transactionPing")
            .routeId("transaction-ping-route")
            .setBody(constant("{\"status\": \"UP\", \"service\": \"transaction-service\"}"));

        // Stats Route
        from("direct:transactionStats")
            .routeId("transaction-stats-route")
            .process(exchange -> {
                long total = processedCount.get();
                exchange.getIn().setBody(Map.of(
                    "status", "HEALTHY",
                    "totalProcessedTransactions", total,
                    "recentTransactionsCount", recentTransactions.size(),
                    "kafkaTopic", kafkaTopic,
                    "consumerGroup", kafkaGroupId
                ));
            });

        // AMQ Streams Kafka Consumer Route (Interconnection with Payment Service)
        from("kafka:" + kafkaTopic + "?brokers=" + kafkaBrokers + "&groupId=" + kafkaGroupId + "&securityProtocol=" + kafkaSecurityProtocol)
            .routeId("amq-transaction-consumer-route")
            .log("Received payment event from AMQ Streams topic [" + kafkaTopic + "]: ${body}")
            .unmarshal().json(TransactionEvent.class)
            .process(exchange -> {
                TransactionEvent event = exchange.getIn().getBody(TransactionEvent.class);
                if (event == null) {
                    event = new TransactionEvent();
                }

                String transactionId = "TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                event.setTransactionId(transactionId);
                event.setStatus("SETTLED");

                processedCount.incrementAndGet();
                if (event.getPaymentId() != null) {
                    recentTransactions.put(event.getPaymentId(), event);
                }

                log.info("Transaction settled: TX_ID={}, Payment_ID={}, Amount={} {}",
                    transactionId, event.getPaymentId(), event.getAmount(), event.getCurrency());

                exchange.getIn().setBody(event);
            })
            .log("Successfully settled transaction: ${body.transactionId} for payment: ${body.paymentId}");
    }
}
