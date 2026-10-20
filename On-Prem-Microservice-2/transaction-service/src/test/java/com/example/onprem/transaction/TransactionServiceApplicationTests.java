package com.example.onprem.transaction;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "app.security.password=unit-test-secret",
    "app.kafka.bootstrap-servers=localhost:9092",
    "app.kafka.topic=test-payments",
    "camel.springboot.main-run-controller=false"
})
class TransactionServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
