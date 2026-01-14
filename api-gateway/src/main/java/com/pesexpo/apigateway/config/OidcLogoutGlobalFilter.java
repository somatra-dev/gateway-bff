package com.pesexpo.apigateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class OidcLogoutGlobalFilter implements GlobalFilter {

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.auth-server.url:http://localhost:9000}")
    private String authServerUrl;

    @Value("${app.gateway.url:http://localhost:8888}")
    private String gatewayUrl;

    @Value("${spring.security.oauth2.client.registration.api-gateway-client.client-id}")
    private String clientId;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Filter only POST /logout requests
        if (!request.getMethod().matches("POST") || !request.getPath().value().equals("/logout")) {
            return chain.filter(exchange);
        }

        log.info("OIDC Logout GlobalFilter triggered");

        // Get authentication from security context
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(authentication -> {
                    // Invalidate session first
                    return exchange.getSession()
                            .flatMap(WebSession::invalidate)
                            .then(Mono.defer(() -> {
                                // Build OIDC logout URL
                                String logoutUrl = buildOidcLogoutUrl(authentication);

                                // Redirect to OIDC logout
                                ServerHttpResponse response = exchange.getResponse();
                                response.setStatusCode(HttpStatus.FOUND);
                                response.getHeaders().setLocation(URI.create(logoutUrl));

                                log.info("Redirecting to OIDC logout: {}", logoutUrl);
                                return response.setComplete();
                            }));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // No authentication found, just redirect to frontend
                    return redirectToFrontend(exchange);
                }));
    }

    private String buildOidcLogoutUrl(org.springframework.security.core.Authentication authentication) {
        StringBuilder url = new StringBuilder(authServerUrl)
                .append("/connect/logout");

        // Check if we have OIDC user with ID token
        if (authentication != null && authentication.getPrincipal() instanceof OidcUser oidcUser) {
            String idToken = oidcUser.getIdToken().getTokenValue();
            String sid = oidcUser.getIdToken().getClaim("sid");

            log.info("Building logout URL with ID token");

            // Add id_token_hint
            url.append("?id_token_hint=")
                    .append(URLEncoder.encode(idToken, StandardCharsets.UTF_8));

            // Add sid if present
            if (sid != null && !sid.isEmpty()) {
                url.append("&sid=")
                        .append(URLEncoder.encode(sid, StandardCharsets.UTF_8));
            }

            // Add post_logout_redirect_uri
            String postLogoutUri = gatewayUrl + "/logout-success";
            url.append("&post_logout_redirect_uri=")
                    .append(URLEncoder.encode(postLogoutUri, StandardCharsets.UTF_8));

            // Add client_id (optional, but Keycloak style)
            url.append("&client_id=")
                    .append(URLEncoder.encode(clientId, StandardCharsets.UTF_8));
        } else {
            // Fallback: logout without ID token (will show confirmation page)
            log.info("No OIDC user found, building basic logout URL");
            String postLogoutUri = gatewayUrl + "/logout-success";
            url.append("?post_logout_redirect_uri=")
                    .append(URLEncoder.encode(postLogoutUri, StandardCharsets.UTF_8));
        }

        return url.toString();
    }

    private Mono<Void> redirectToFrontend(ServerWebExchange exchange) {
        log.info("No authentication found, redirecting directly to frontend");

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(frontendUrl + "?logout=success"));

        return response.setComplete();
    }
}