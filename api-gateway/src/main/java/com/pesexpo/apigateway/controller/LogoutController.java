package com.pesexpo.apigateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Controller
public class LogoutController {

    private final String authServerUrl;
    private final String gatewayUrl;
    private final String clientId;
    private final String clientSecret;
    private final ReactiveOAuth2AuthorizedClientService authorizedClientService;
    private final WebClient webClient;

    public LogoutController(
            @Value("${app.auth-server.url:http://localhost:9000}") String authServerUrl,
            @Value("${app.gateway.url:http://localhost:8888}") String gatewayUrl,
            @Value("${spring.security.oauth2.client.registration.api-gateway-client.client-id:api-gateway}") String clientId,
            @Value("${spring.security.oauth2.client.registration.api-gateway-client.client-secret:gateway-secret}") String clientSecret,
            ReactiveOAuth2AuthorizedClientService authorizedClientService,
            WebClient.Builder webClientBuilder) {
        this.authServerUrl = authServerUrl;
        this.gatewayUrl = gatewayUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.authorizedClientService = authorizedClientService;
        this.webClient = webClientBuilder.build();
    }

    @PostMapping("/logout")
    public Mono<Void> logout(ServerWebExchange exchange) {
        log.info("=== Processing logout request ===");

        return ReactiveSecurityContextHolder.getContext()
                .flatMap(securityContext -> {
                    Authentication authentication = securityContext.getAuthentication();

                    if (authentication instanceof OAuth2AuthenticationToken oauthToken &&
                            authentication.getPrincipal() instanceof OidcUser oidcUser) {

                        String clientRegistrationId = oauthToken.getAuthorizedClientRegistrationId();
                        String principalName = oauthToken.getName();

                        // 1. Revoke tokens first
                        return revokeTokens(clientRegistrationId, principalName)
                                // 2. Remove authorized client from storage
                                .then(authorizedClientService.removeAuthorizedClient(clientRegistrationId, principalName))
                                .doOnSuccess(v -> log.info("Removed authorized client for user: {}", principalName))
                                // 3. Invalidate session
                                .then(exchange.getSession())
                                .flatMap(session -> {
                                    log.info("Invalidating local session: {}", session.getId());
                                    return session.invalidate();
                                })
                                // 4. Clear cookies and perform OIDC logout
                                .then(Mono.defer(() -> {
                                    clearCookies(exchange.getResponse());
                                    return performOidcLogout(oidcUser, exchange);
                                }));
                    }

                    // No OAuth2 authentication, just clear session
                    return exchange.getSession()
                            .flatMap(session -> {
                                log.info("Invalidating local session: {}", session.getId());
                                return session.invalidate();
                            })
                            .then(Mono.defer(() -> {
                                clearCookies(exchange.getResponse());
                                return redirectToFrontend(exchange);
                            }));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    clearCookies(exchange.getResponse());
                    return redirectToFrontend(exchange);
                }));
    }

    /**
     * Revoke both access_token and refresh_token at the authorization server
     */
    private Mono<Void> revokeTokens(String clientRegistrationId, String principalName) {
        return authorizedClientService.loadAuthorizedClient(clientRegistrationId, principalName)
                .flatMap(authorizedClient -> {
                    OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
                    OAuth2RefreshToken refreshToken = authorizedClient.getRefreshToken();

                    log.info("Revoking tokens for user: {}", principalName);

                    // Revoke both tokens in parallel
                    Mono<Void> revokeAccess = accessToken != null
                            ? revokeToken(accessToken.getTokenValue(), "access_token")
                            : Mono.empty();

                    Mono<Void> revokeRefresh = refreshToken != null
                            ? revokeToken(refreshToken.getTokenValue(), "refresh_token")
                            : Mono.empty();

                    return Mono.when(revokeAccess, revokeRefresh)
                            .doOnSuccess(v -> log.info("Successfully revoked all tokens for user: {}", principalName))
                            .doOnError(e -> log.error("Error revoking tokens: {}", e.getMessage()));
                })
                .onErrorResume(e -> {
                    log.warn("Could not load authorized client for token revocation: {}", e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    /**
     * Call the OAuth2 token revocation endpoint
     */
    private Mono<Void> revokeToken(String token, String tokenTypeHint) {
        String credentials = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

        return webClient
                .post()
                .uri(authServerUrl + "/oauth2/revoke")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters
                        .fromFormData("token", token)
                        .with("token_type_hint", tokenTypeHint))
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(response -> log.info("Successfully revoked {}", tokenTypeHint))
                .doOnError(e -> log.error("Failed to revoke {}: {}", tokenTypeHint, e.getMessage()))
                .onErrorResume(e -> Mono.empty()) // Continue even if revocation fails
                .then();
    }

    private Mono<Void> performOidcLogout(OidcUser oidcUser, ServerWebExchange exchange) {
        String idToken = oidcUser.getIdToken().getTokenValue();
        String sid = oidcUser.getIdToken().getClaim("sid");

        log.info("Performing OIDC logout for user: {}", oidcUser.getName());
        log.info("ID Token present: {}", idToken != null);
        log.info("SID from token: {}", sid);

        // Build OIDC logout URL
        String logoutUrl = buildOidcLogoutUrl(idToken, sid);
        log.info("Redirecting to OIDC logout: {}", logoutUrl);

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(logoutUrl));

        return response.setComplete();
    }

    private String buildOidcLogoutUrl(String idToken, String sid) {
        StringBuilder url = new StringBuilder(authServerUrl)
                .append("/connect/logout");

        boolean hasParam = false;

        // Add id_token_hint if available
        if (idToken != null) {
            url.append("?id_token_hint=").append(URLEncoder.encode(idToken, StandardCharsets.UTF_8));
            hasParam = true;
        }

        // Add sid if available
        if (sid != null && !sid.isEmpty()) {
            url.append(hasParam ? "&" : "?")
                    .append("sid=").append(URLEncoder.encode(sid, StandardCharsets.UTF_8));
            hasParam = true;
        }

        // Add post_logout_redirect_uri
        String redirectUri = gatewayUrl + "/logout-success";
        url.append(hasParam ? "&" : "?")
                .append("post_logout_redirect_uri=")
                .append(URLEncoder.encode(redirectUri, StandardCharsets.UTF_8));

        // Add logout=true to skip confirmation (if your auth server supports it)
        url.append("&logout=true");

        return url.toString();
    }

    private Mono<Void> redirectToFrontend(ServerWebExchange exchange) {
        log.info("No OIDC user, redirecting directly to frontend");

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(gatewayUrl + "?logout=success"));

        return response.setComplete();
    }

    private void clearCookies(ServerHttpResponse response) {
        response.addCookie(ResponseCookie.from("SESSION", "")
                .path("/")
                .maxAge(0)
                .build());

        response.addCookie(ResponseCookie.from("XSRF-TOKEN", "")
                .path("/")
                .maxAge(0)
                .build());

        response.addCookie(ResponseCookie.from("JSESSIONID", "")
                .path("/")
                .maxAge(0)
                .build());
    }

    @GetMapping("/logout-success")
    public Mono<Void> logoutSuccess(ServerWebExchange exchange) {
        log.info("=== OIDC Logout Successful ===");

        ServerHttpResponse response = exchange.getResponse();

        // Clear any remaining cookies
        clearCookies(response);

        // Redirect to frontend(gateway port 8888)
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().set(HttpHeaders.LOCATION, gatewayUrl + "?logout=success&oidc=true");

        return response.setComplete();
    }
}