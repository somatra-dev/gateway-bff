package com.pesexpo.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * CORS Configuration for BFF Pattern
 *
 * Important for CSRF:
 * - Must expose X-XSRF-TOKEN header so frontend can read it
 * - Must allow X-XSRF-TOKEN header in requests
 * - Credentials must be allowed for cookies
 */
@Configuration
public class CorsConfig {

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();

        // Allow frontend origins
        corsConfig.setAllowedOrigins(List.of(
                frontendUrl,
                "http://localhost:3000",
                "http://127.0.0.1:3000"
        ));

        // Allow all common HTTP methods
        corsConfig.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        // Allow headers including CSRF token header
        corsConfig.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With",
                "X-XSRF-TOKEN"  // CSRF token header from frontend
        ));

        // Expose CSRF cookie header so frontend (JavaScript) can read it
        corsConfig.setExposedHeaders(List.of(
                "X-XSRF-TOKEN"
        ));

        // Allow credentials (cookies for session and CSRF)
        corsConfig.setAllowCredentials(true);

        // Cache preflight response for 1 hour
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
