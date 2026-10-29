package com.example.aro.portfolio;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "app.security.password=unit-test-secret",
    "camel.springboot.main-run-controller=false"
})
class PortfolioServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
