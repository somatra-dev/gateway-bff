package com.pesexpo.apigateway.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Auth Controller for BFF Pattern
 *
 * Provides endpoints for frontend to check authentication status
 * and get user information without exposing raw tokens.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * Get current authentication status and user info
     * Returns user claims from OIDC token without exposing the token itself
     */
    @GetMapping("/me")
    public Mono<Map<String, Object>> getCurrentUser(
            @AuthenticationPrincipal OidcUser oidcUser,
            @RegisteredOAuth2AuthorizedClient("api-gateway-client") OAuth2AuthorizedClient authorizedClient) {

        Map<String, Object> response = new HashMap<>();

        if (oidcUser == null) {
            response.put("authenticated", false);
            response.put("user", null);
            return Mono.just(response);
        }

        // Build user info from OIDC claims (don't expose raw token)
        Map<String, Object> user = new HashMap<>();
        user.put("sub", oidcUser.getSubject());
        user.put("email", oidcUser.getEmail());
        user.put("name", oidcUser.getFullName());
        user.put("given_name", oidcUser.getGivenName());
        user.put("family_name", oidcUser.getFamilyName());

        // Get custom claims if present
        if (oidcUser.getClaim("uuid") != null) {
            user.put("uuid", oidcUser.getClaim("uuid"));
        }
        if (oidcUser.getClaim("roles") != null) {
            user.put("roles", oidcUser.getClaim("roles"));
        }
        if (oidcUser.getClaim("permissions") != null) {
            user.put("permissions", oidcUser.getClaim("permissions"));
        }

        response.put("authenticated", true);
        response.put("user", user);

        // Include token expiry info (not the token itself)
        if (authorizedClient != null && authorizedClient.getAccessToken() != null) {
            response.put("expiresAt", authorizedClient.getAccessToken().getExpiresAt());
        }

        return Mono.just(response);
    }

    /**
     * Simple endpoint to check if user is authenticated
     * Returns 200 if authenticated, 401 if not (handled by security config)
     */
    @GetMapping("/status")
    public Mono<Map<String, Object>> getAuthStatus(@AuthenticationPrincipal OidcUser oidcUser) {
        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", oidcUser != null);
        return Mono.just(response);
    }


}
