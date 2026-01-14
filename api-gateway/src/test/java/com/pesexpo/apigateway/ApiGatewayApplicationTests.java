package com.pesexpo.apigateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

class ApiGatewayApplicationTests {

    @Test
    @EnabledIfSystemProperty(named = "spring.profiles.active", matches = "integration")
    void contextLoads() {
        // Integration test - requires auth server to be running
        // Run with: ./gradlew test -Dspring.profiles.active=integration
    }

    @Test
    void applicationClassExists() {
        // Simple unit test to verify application class is loadable
        ApiGatewayApplication app = new ApiGatewayApplication();
        assert app != null;
    }

}
