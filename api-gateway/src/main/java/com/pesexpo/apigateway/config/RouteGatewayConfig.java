package com.pesexpo.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Route Configuration for BFF Behind Gateway Pattern
 *
 * Browser → Gateway:8888 → NextJS BFF → Gateway → Microservices
 *
 * Routes:
 * - /bff/** → NextJS (strips /bff prefix)
 * - /api/v1/** → Microservices (direct with TokenRelay)
 */
@Configuration
public class RouteGatewayConfig {

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()

                // MICROSERVICES API ROUTES (direct access with TokenRelay)
                // Product Service
                .route("product-service", r -> r
                        .path("/api/v1/products/**")
                        .filters(GatewayFilterSpec::tokenRelay)
                        .uri("lb://PRODUCT-SERVICE"))

                // Order Service
                .route("order-service", r -> r
                        .path("/api/v1/orders/**")
                        .filters(GatewayFilterSpec::tokenRelay)
                        .uri("lb://ORDER-SERVICE"))

                // BFF ROUTE - Browser → Gateway → NextJS BFF
                // /bff/** → Next.js (strip /bff prefix)
                .route("nextjs-bff", r -> r
                        .path("/bff/**")
                        .filters(f -> f
                                .tokenRelay()
                                .rewritePath("/bff(?<segment>/?.*)", "${segment}"))
                        .uri(frontendUrl))

                // NEXTJS STATIC ASSETS
                .route("nextjs-static", r -> r
                        .path("/_next/**", "/favicon.ico", "/images/**", "/fonts/**")
                        .uri(frontendUrl))

                // NEXTJS PAGES (SSR) - catch-all (must be last)
//                 Temporarily disabled to test logout - if logout works, the .not() predicate is broken
                 .route("nextjs-pages", r -> r
                         .path("/**")
                         .and()
                         .not(p -> p.path("/logout", "/logout-success", "/login", "/oauth2/**", "/error"))
                         .filters(GatewayFilterSpec::tokenRelay)
                         .uri(frontendUrl))

                .build();
    }
}
