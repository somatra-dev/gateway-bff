/**
 * API Configuration for BFF Pattern
 *
 * In this optimized BFF pattern:
 * - Browser calls Gateway directly for API requests
 * - Gateway handles OAuth2/PKCE authentication (session-based)
 * - Gateway uses TokenRelay to forward access token to microservices
 * - No double-routing through BFF for API calls
 */

export const API_CONFIG = {
  // Gateway URL - Browser calls Gateway directly
  gatewayUrl: process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8888',

  // API endpoints (relative to Gateway)
  endpoints: {
    // Auth endpoints (handled by Gateway)
    auth: {
      me: '/api/auth/me',
      status: '/api/auth/status',
      login: '/oauth2/authorization/api-gateway-client',
      logout: '/logout',
    },
    // Microservice endpoints (routed through Gateway with TokenRelay)
    products: '/api/v1/products',
    orders: '/api/v1/orders',
  },
} as const;

/**
 * Get CSRF token from cookie
 * Spring Security sets XSRF-TOKEN cookie, we need to send it in X-XSRF-TOKEN header
 */
export function getCsrfToken(): string | null {
  if (typeof document === 'undefined') return null;

  const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}
