package com.pesexpo.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Security Filter for Microservices - Hybrid CSR/SSR Pattern
 *
 * - GET (CSR): Permitted without authentication - microservices have permitAll
 * - POST/PUT/DELETE (SSR): Require authentication via OAuth2
 * - User context propagation to downstream services for authenticated requests
 */
@Component
@Slf4j
public class MicroserviceSecurityFilter implements GlobalFilter, Ordered {

    private static final String USER_ID_HEADER = "X-User-ID";
    private static final String USER_EMAIL_HEADER = "X-User-Email";
    private static final String USER_ROLES_HEADER = "X-User-Roles";

    // Protected service paths
    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/v1/products",
            "/api/v1/orders"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Only apply to protected service routes
        if (!isProtectedPath(path)) {
            return chain.filter(exchange);
        }

        // CSR: GET requests are permitted without authentication
        if (HttpMethod.GET.equals(request.getMethod())) {
            log.debug("CSR: Permitting GET request without auth: {}", path);
            return chain.filter(exchange);
        }

        // SSR: Write operations require authentication
        log.debug("SSR: Checking auth for write operation: {} {}", request.getMethod(), path);

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(authentication -> {
                    // Check if authenticated
                    if (authentication == null || !authentication.isAuthenticated()) {
                        log.warn("Unauthorized access attempt: {}", path);
                        return handleUnauthorized(exchange);
                    }

                    // Check role-based access for write operations (POST, PUT, DELETE)
                    boolean hasWriteAccess = authentication.getAuthorities().stream()
                            .anyMatch(auth ->
                                auth.getAuthority().contains("SCOPE_write") ||
                                auth.getAuthority().contains("ROLE_USER") ||
                                auth.getAuthority().contains("ROLE_ADMIN") ||
                                auth.getAuthority().contains("ROLE_MANAGER")
                            );

                    if (!hasWriteAccess) {
                        log.warn("Forbidden: User lacks write permission for {}", path);
                        return handleForbidden(exchange);
                    }

                    // Propagate user context to downstream service
                    ServerHttpRequest modifiedRequest = propagateUserContext(request, authentication);

                    ServerWebExchange modifiedExchange = exchange.mutate()
                            .request(modifiedRequest)
                            .build();

                    return chain.filter(modifiedExchange);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("No authentication context for: {}", path);
                    return handleUnauthorized(exchange);
                }));
    }

    private boolean isProtectedPath(String path) {
        return PROTECTED_PATHS.stream().anyMatch(path::startsWith);
    }

    private boolean isWriteOperation(ServerHttpRequest request) {
        HttpMethod method = request.getMethod();
        return HttpMethod.POST.equals(method) ||
               HttpMethod.PUT.equals(method) ||
               HttpMethod.DELETE.equals(method);
    }

    private ServerHttpRequest propagateUserContext(ServerHttpRequest request,
                                                   org.springframework.security.core.Authentication authentication) {
        ServerHttpRequest.Builder requestBuilder = request.mutate();

        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            requestBuilder.header(USER_ID_HEADER, oidcUser.getSubject());

            String email = oidcUser.getEmail();
            if (email != null) {
                requestBuilder.header(USER_EMAIL_HEADER, email);
            }

            String roles = authentication.getAuthorities().stream()
                    .map(Object::toString)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");
            if (!roles.isEmpty()) {
                requestBuilder.header(USER_ROLES_HEADER, roles);
            }

            log.debug("User context propagated: userId={}, email={}",
                    oidcUser.getSubject(), email);
        }

        return requestBuilder.build();
    }

    private Mono<Void> handleUnauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return response.setComplete();
    }

    private Mono<Void> handleForbidden(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
