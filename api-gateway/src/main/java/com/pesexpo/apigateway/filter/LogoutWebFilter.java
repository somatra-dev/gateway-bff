package com.pesexpo.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * WebFilter for handling logout requests.
 */
@Component
@Slf4j
public class LogoutWebFilter implements WebFilter, Ordered {

    private final String authServerUrl;
    private final String frontendUrl;
    private final String gatewayUrl;
    private final String clientId;
    private final String clientSecret;
    private final ReactiveOAuth2AuthorizedClientService authorizedClientService;
    private final WebClient webClient;

    public LogoutWebFilter(
            @Value("${app.auth-server.url:http://localhost:9000}") String authServerUrl,
            @Value("${app.gateway.url:http://localhost:3000}") String frontendUrl,
            @Value("${app.gateway.url:http://localhost:8888}") String gatewayUrl,
            @Value("${spring.security.oauth2.client.registration.api-gateway-client.client-id:api-gateway}") String clientId,
            @Value("${spring.security.oauth2.client.registration.api-gateway-client.client-secret:gateway-secret}") String clientSecret,
            ReactiveOAuth2AuthorizedClientService authorizedClientService,
            WebClient.Builder webClientBuilder) {
        this.authServerUrl = authServerUrl;
        this.frontendUrl = frontendUrl;
        this.gatewayUrl = gatewayUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.authorizedClientService = authorizedClientService;
        this.webClient = webClientBuilder.build();
    }

    @Override
    public int getOrder() {
        return -50;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        if (!"/logout".equals(path)) {
            return chain.filter(exchange);
        }

        // GET /logout - auth server callback
        if (HttpMethod.GET.equals(request.getMethod())) {
            log.info("=== LogoutWebFilter: GET /logout - redirecting to home ===");
            return simpleRedirect(exchange, "/?logout=success&oidc=true");
        }

        // Only handle POST /logout
        if (!HttpMethod.POST.equals(request.getMethod())) {
            return chain.filter(exchange);
        }

        log.info("=== LogoutWebFilter: Processing POST /logout ===");

        // Get authentication and handle logout
        return ReactiveSecurityContextHolder.getContext()
                .flatMap(ctx -> handleLogout(exchange, ctx.getAuthentication()))
                .onErrorResume(error -> {
                    log.error("Error during logout: {}", error.getMessage());
                    return simpleRedirect(exchange, "/?logout=error");
                });
    }

    private Mono<Void> handleLogout(ServerWebExchange exchange, Authentication authentication) {
        // Check if OIDC user
        if (authentication instanceof OAuth2AuthenticationToken oauthToken &&
                authentication.getPrincipal() instanceof OidcUser oidcUser) {

            String clientRegistrationId = oauthToken.getAuthorizedClientRegistrationId();
            String principalName = oauthToken.getName();

            log.info("Processing OIDC logout for user: {}", principalName);

            // Build logout URL before any async ops (capture values now)
            String idToken = oidcUser.getIdToken().getTokenValue();
            String sid = oidcUser.getIdToken().getClaim("sid");
            String logoutUrl = buildOidcLogoutUrl(idToken, sid);

            return revokeTokens(clientRegistrationId, principalName)
                    .then(authorizedClientService.removeAuthorizedClient(clientRegistrationId, principalName))
                    .doOnSuccess(v -> log.info("Removed authorized client for user: {}", principalName))
                    .then(exchange.getSession())
                    .flatMap(session -> {
                        log.info("Invalidating session: {}", session.getId());
                        return session.invalidate();
                    })
                    .then(Mono.defer(() -> {
                        log.info("Redirecting to OIDC logout: {}", logoutUrl);
                        return performRedirect(exchange, logoutUrl);
                    }));
        }

        // No OIDC authentication
        log.info("No OIDC authentication, performing simple logout");
        return exchange.getSession()
                .flatMap(session -> {
                    log.info("Invalidating session: {}", session.getId());
                    return session.invalidate();
                })
                .then(Mono.defer(() -> simpleRedirect(exchange, "/?logout=success")));
    }

    private Mono<Void> performRedirect(ServerWebExchange exchange, String url) {
        ServerHttpResponse response = exchange.getResponse();
        clearCookies(response);
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(url));
        return response.setComplete();
    }

    private Mono<Void> simpleRedirect(ServerWebExchange exchange, String url) {
        ServerHttpResponse response = exchange.getResponse();
        clearCookies(response);
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(url));
        return response.setComplete();
    }

    private Mono<Void> revokeTokens(String clientRegistrationId, String principalName) {
        return authorizedClientService.loadAuthorizedClient(clientRegistrationId, principalName)
                .flatMap(authorizedClient -> {
                    OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
                    OAuth2RefreshToken refreshToken = authorizedClient.getRefreshToken();

                    log.info("Revoking tokens for user: {}", principalName);

                    Mono<Void> revokeAccess = accessToken != null
                            ? revokeToken(accessToken.getTokenValue(), "access_token")
                            : Mono.empty();

                    Mono<Void> revokeRefresh = refreshToken != null
                            ? revokeToken(refreshToken.getTokenValue(), "refresh_token")
                            : Mono.empty();

                    return Mono.when(revokeAccess, revokeRefresh)
                            .doOnSuccess(v -> log.info("Successfully revoked tokens for user: {}", principalName));
                })
                .onErrorResume(e -> {
                    log.warn("Could not revoke tokens: {}", e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

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
                .doOnSuccess(response -> log.info("Revoked {}", tokenTypeHint))
                .doOnError(e -> log.warn("Failed to revoke {}: {}", tokenTypeHint, e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    private String buildOidcLogoutUrl(String idToken, String sid) {
        StringBuilder url = new StringBuilder(authServerUrl)
                .append("/connect/logout");

        boolean hasParam = false;

        if (idToken != null) {
            url.append("?id_token_hint=").append(URLEncoder.encode(idToken, StandardCharsets.UTF_8));
            hasParam = true;
        }

        if (sid != null && !sid.isEmpty()) {
            url.append(hasParam ? "&" : "?")
                    .append("sid=").append(URLEncoder.encode(sid, StandardCharsets.UTF_8));
            hasParam = true;
        }

        String redirectUri = gatewayUrl + "/logout-success";
        url.append(hasParam ? "&" : "?")
                .append("post_logout_redirect_uri=")
                .append(URLEncoder.encode(redirectUri, StandardCharsets.UTF_8));

        url.append("&logout=true");

        return url.toString();
    }

    private void clearCookies(ServerHttpResponse response) {
        try {
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
        } catch (Exception e) {
            log.debug("Could not clear cookies: {}", e.getMessage());
        }
    }
}
