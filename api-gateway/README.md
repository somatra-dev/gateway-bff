# API Gateway (BFF Pattern)

A Spring Cloud Gateway implementation using the **Backend-for-Frontend (BFF)** pattern for secure OAuth2/OIDC authentication with token management.

## Tech Stack

| Technology | Version |
|------------|---------|
| Java | 21 |
| Spring Boot | 4.0.1 |
| Spring Cloud Gateway | 2025.1.0 |
| Spring Security OAuth2 Client | Latest |
| Eureka Client | Latest |
| Lombok | Latest |

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              BROWSER (Next.js)                              │
│                                                                             │
│   - Only has SESSION cookie (HttpOnly, Secure, SameSite)                   │
│   - Never sees access_token or refresh_token                               │
│   - Calls Gateway for ALL requests                                          │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           API GATEWAY (Port 8888)                           │
│                                                                             │
│   ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐            │
│   │  OAuth2 Client  │  │  Token Storage  │  │  Token Relay    │            │
│   │  (Login Flow)   │  │  (Server-side)  │  │  (To Services)  │            │
│   └─────────────────┘  └─────────────────┘  └─────────────────┘            │
│                                                                             │
│   Features:                                                                 │
│   - Session-based authentication                                            │
│   - Stores tokens server-side (never exposed to browser)                   │
│   - Token relay to microservices                                            │
│   - Token revocation on logout                                              │
│   - CSRF protection                                                         │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                    ┌─────────────────┼─────────────────┐
                    ▼                 ▼                 ▼
           ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
           │   Product    │  │    Order     │  │    Auth      │
           │   Service    │  │   Service    │  │   Server     │
           └──────────────┘  └──────────────┘  └──────────────┘
```

## Why BFF Pattern?

| Traditional SPA | BFF Pattern (This Gateway) |
|-----------------|----------------------------|
| Tokens in localStorage | Tokens stored server-side |
| Vulnerable to XSS | Protected from XSS |
| Token refresh in browser | Token refresh on server |
| Complex frontend logic | Simple frontend (just cookies) |
| CORS complexity | Single origin |

## Project Structure

```
src/main/java/com/pesexpo/apigateway/
├── ApiGatewayApplication.java       # Main application
├── config/
│   ├── CorsConfig.java              # CORS configuration
│   ├── OidcLogoutGlobalFilter.java  # OIDC logout filter
│   ├── RouteGatewayConfig.java      # Route definitions
│   └── SecurityConfig.java          # Security configuration
└── controller/
    ├── AuthController.java          # Auth status endpoints
    └── LogoutController.java        # Logout with token revocation
```

## API Endpoints

### Authentication

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/auth/me` | Get current user info & token expiry | Required |
| GET | `/api/auth/status` | Check authentication status | Required |
| POST | `/logout` | Logout with full token revocation | Required |
| GET | `/logout-success` | Post-logout redirect handler | Public |

### OAuth2 Flow

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/oauth2/authorization/api-gateway-client` | Initiate OAuth2 login |
| GET | `/login/oauth2/code/api-gateway-client` | OAuth2 callback (automatic) |

### Route Proxying

| Path Pattern | Target Service | Token Relay |
|--------------|----------------|-------------|
| `/api/v1/products/**` | PRODUCT-SERVICE | Yes |
| `/api/v1/orders/**` | ORDER-SERVICE | Yes |
| `/_next/**` | Next.js (static) | No |
| `/app/**`, `/products/**` | Next.js (SSR) | Yes |

## Authentication Flow

### Login Flow

```
1. Browser → GET /oauth2/authorization/api-gateway-client
2. Gateway → Redirect to Auth Server /oauth2/authorize
3. User logs in at Auth Server
4. Auth Server → Redirect to Gateway /login/oauth2/code/...
5. Gateway exchanges code for tokens (server-side)
6. Gateway stores tokens in session
7. Gateway → Redirect to Frontend with SESSION cookie
```

### API Request Flow

```
1. Browser → GET /api/v1/products (with SESSION cookie)
2. Gateway validates session
3. Gateway extracts access_token from session
4. Gateway → GET /api/v1/products to PRODUCT-SERVICE
   (with Authorization: Bearer <access_token>)
5. Response flows back through Gateway to Browser
```

### Logout Flow (with Token Revocation)

```
1. Browser → POST /logout
2. Gateway → POST /oauth2/revoke (access_token)
3. Gateway → POST /oauth2/revoke (refresh_token)
4. Gateway removes OAuth2AuthorizedClient
5. Gateway invalidates session
6. Gateway clears cookies (SESSION, XSRF-TOKEN)
7. Gateway → GET /connect/logout (OIDC logout at Auth Server)
8. Auth Server clears its session
9. Redirect to Frontend with ?logout=success
```

## Security Features

### Cookie Security

| Cookie | HttpOnly | Secure | SameSite | Purpose |
|--------|----------|--------|----------|---------|
| SESSION | Yes | Yes* | Lax | Session identifier |
| XSRF-TOKEN | No | Yes* | Lax | CSRF protection |

*Secure=true in production (HTTPS)

### CSRF Protection

- Enabled for all state-changing requests
- Token stored in cookie (readable by JavaScript)
- Frontend must include `X-XSRF-TOKEN` header

### Token Management

| Token | Storage | Exposed to Browser | Revoked on Logout |
|-------|---------|-------------------|-------------------|
| Access Token | Gateway session | No | Yes |
| Refresh Token | Gateway session | No | Yes |
| ID Token | Gateway session | No | N/A |
| Session ID | Cookie | Yes (HttpOnly) | Yes |

## Configuration

### application.yml

```yaml
spring:
  application:
    name: api-gateway

  security:
    oauth2:
      client:
        registration:
          api-gateway-client:
            provider: auth-server
            client-id: ${OAUTH2_CLIENT_ID:api-gateway}
            client-secret: ${OAUTH2_CLIENT_SECRET:gateway-secret}
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope:
              - openid
              - profile
              - email
              - read
              - write
        provider:
          auth-server:
            authorization-uri: ${OAUTH2_ISSUER_URI:http://localhost:9000}/oauth2/authorize
            token-uri: ${OAUTH2_ISSUER_URI:http://localhost:9000}/oauth2/token
            jwk-set-uri: ${OAUTH2_ISSUER_URI:http://localhost:9000}/oauth2/jwks
            user-info-uri: ${OAUTH2_ISSUER_URI:http://localhost:9000}/userinfo
            user-name-attribute: sub

app:
  frontend:
    url: ${FRONTEND_URL:http://localhost:3000}
  auth-server:
    url: ${OAUTH2_ISSUER_URI:http://localhost:9000}

server:
  port: 8888

eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_SERVER_URL:http://localhost:8761/eureka/}
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `OAUTH2_CLIENT_ID` | api-gateway | OAuth2 client ID |
| `OAUTH2_CLIENT_SECRET` | gateway-secret | OAuth2 client secret |
| `OAUTH2_ISSUER_URI` | http://localhost:9000 | Authorization server URL |
| `FRONTEND_URL` | http://localhost:3000 | Frontend application URL |
| `EUREKA_SERVER_URL` | http://localhost:8761/eureka/ | Eureka server URL |

## Frontend Integration (Next.js)

### Check Authentication

```typescript
// GET /api/auth/me
const response = await fetch('http://localhost:8888/api/auth/me', {
  credentials: 'include'  // Important: include cookies
});

const data = await response.json();
// {
//   authenticated: true,
//   user: { sub: "...", email: "...", name: "...", roles: [...] },
//   expiresAt: "2024-01-15T10:30:00Z"
// }
```

### Initiate Login

```typescript
// Redirect to Gateway OAuth2 login
window.location.href = 'http://localhost:8888/oauth2/authorization/api-gateway-client';
```

### Logout

```typescript
// POST to logout endpoint
await fetch('http://localhost:8888/logout', {
  method: 'POST',
  credentials: 'include',
  headers: {
    'X-XSRF-TOKEN': getCsrfToken()  // From XSRF-TOKEN cookie
  }
});
```

### API Calls (Through Gateway)

```typescript
// All API calls go through Gateway
const products = await fetch('http://localhost:8888/api/v1/products', {
  credentials: 'include'  // Gateway adds Authorization header
});
```

## Session Configuration (Production)

Add to `application.yml` for production:

```yaml
server:
  reactive:
    session:
      timeout: 7d  # Match refresh token lifetime
      cookie:
        name: SESSION
        http-only: true
        secure: true      # HTTPS only
        same-site: lax
```

## Adding New Routes

Edit `RouteGatewayConfig.java`:

```java
// Add new microservice route
.route("inventory-service", r -> r
    .path("/api/v1/inventory/**")
    .filters(GatewayFilterSpec::tokenRelay)
    .uri("lb://INVENTORY-SERVICE"))
```

## Troubleshooting

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| 401 on API calls | Session expired | Re-login via OAuth2 |
| 403 Forbidden | Missing CSRF token | Include X-XSRF-TOKEN header |
| CORS errors | Missing credentials | Add `credentials: 'include'` |
| Redirect loop | Cookie not set | Check SameSite/Secure settings |

### Debug Logging

```yaml
logging:
  level:
    com.pesexpo.apigateway: DEBUG
    org.springframework.security: DEBUG
    org.springframework.security.oauth2: TRACE
```
