package com.example.aro.trade;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "app.security.password=unit-test-secret",
    "app.services.portfolio-url=http://localhost:8080/api/v1/portfolios",
    "camel.springboot.main-run-controller=false"
})
class TradeServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
