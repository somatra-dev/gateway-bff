/**
 * Auth utilities for BFF Behind Gateway Pattern
 *
 * Flow:
 * 1. Client -> Gateway (8888) -> BFF (3000) -> Microservices
 * 2. Gateway handles OAuth2/PKCE authentication
 * 3. Gateway forwards Authorization header to BFF via tokenRelay
 * 4. BFF extracts and uses the token for microservice calls
 */

export interface User {
  sub: string;
  uuid?: string;
  email?: string;
  email_verified?: boolean;
  name?: string;
  given_name?: string;
  family_name?: string;
  roles?: string[];
  permissions?: string[];
  scope?: string;
}

export interface AuthConfig {
  loginUrl: string;
  logoutUrl: string;
  baseUrl: string;
}

/**
 * Get auth configuration from environment
 */
export function getAuthConfig(): AuthConfig {
  return {
    loginUrl: process.env.NEXT_PUBLIC_LOGIN_URL || 'http://localhost:8888/oauth2/authorization/api-gateway-client',
    logoutUrl: process.env.NEXT_PUBLIC_LOGOUT_URL || 'http://localhost:8888/logout',
    baseUrl: process.env.NEXT_PUBLIC_BASE_URL || 'http://localhost:8888/bff',
  };
}

/**
 * Decode JWT payload without verification (verification done by Gateway)
 * Gateway already validates the token, we just need to read claims
 */
export function decodeJwtPayload(token: string): User | null {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) {
      return null;
    }

    const payload = parts[1];
    // Handle base64url encoding
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = Buffer.from(base64, 'base64').toString('utf-8');

    return JSON.parse(jsonPayload) as User;
  } catch {
    return null;
  }
}

/**
 * Extract Bearer token from Authorization header
 */
export function extractToken(authHeader: string | null): string | undefined {
  if (!authHeader?.startsWith('Bearer ')) {
    return undefined;
  }
  return authHeader.slice(7);
}

/**
 * Check if token is expired
 */
export function isTokenExpired(token: string): boolean {
  try {
    const payload = decodeJwtPayload(token);
    if (!payload) return true;

    const exp = (payload as unknown as { exp?: number }).exp;
    if (!exp) return false;

    // Check if expired (with 30 second buffer)
    return Date.now() >= (exp * 1000) - 30000;
  } catch {
    return true;
  }
}

/**
 * Get user from token
 */
export function getUserFromToken(token: string | undefined): User | null {
  if (!token) return null;
  if (isTokenExpired(token)) return null;
  return decodeJwtPayload(token);
}
