package com.pesexpo.apigateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;

@Slf4j
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.gateway.url:http://localhost:8888}")
    private String gatewayUrl;


    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                // Disable Spring Security logout - using LogoutWebFilter
                .logout(ServerHttpSecurity.LogoutSpec::disable)

                // disable CSRF
                .csrf(ServerHttpSecurity.CsrfSpec::disable)

                .authorizeExchange(exchange -> exchange
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers(
                                "/",
                                "/login",
                                "/oauth2/**",
                                "/logout",
                                "/logout-success",
                                "/error",
                                "/favicon.ico",
                                // Next.js static assets
                                "/_next/**",
                                "/images/**",
                                "/fonts/**"
                        ).permitAll()
                        .anyExchange().authenticated()
                )

                // OAuth2 Login for browser (session-based)
                .oauth2Login(oauth2 -> oauth2
                        .authenticationSuccessHandler(
                                new RedirectServerAuthenticationSuccessHandler(gatewayUrl + "/")
                        )
                )
                .build();
    }

    // @Bean
    // public WebFilter csrfCookieWebFilter() {
    //     return (exchange, chain) -> {
    //         Mono<org.springframework.security.web.server.csrf.CsrfToken> csrfToken =
    //                 exchange.getAttribute(org.springframework.security.web.server.csrf.CsrfToken.class.getName());
    //         if (csrfToken == null) {
    //             return chain.filter(exchange);
    //         }
    //         return csrfToken
    //                 .doOnSuccess(token -> {
    //                     if (token != null) {
    //                         exchange.getResponse().getHeaders().add("X-XSRF-TOKEN", token.getToken());
    //                     }
    //                 })
    //                 .then(chain.filter(exchange));
    //     };
    // }
}