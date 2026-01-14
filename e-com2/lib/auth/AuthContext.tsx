'use client';

import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import { API_CONFIG } from '@/lib/api/config';
import { authService } from '@/lib/api/client';

interface User {
  sub: string;
  uuid?: string;
  email?: string;
  name?: string;
  given_name?: string;
  family_name?: string;
  roles: string[];
  permissions: string[];
}

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: () => void;
  logout: () => void;
  refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

/**
 * Auth URLs - Gateway handles OAuth2 authentication
 * - Login redirects to Gateway's OAuth2 authorization endpoint
 * - Logout redirects to Gateway's logout endpoint (OIDC logout)
 */
const GATEWAY_URL = API_CONFIG.gatewayUrl;

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const fetchUser = useCallback(async () => {
    try {
      // Call Gateway's /api/auth/me endpoint directly
      // Gateway returns user info from the session (no token exposed)
      const response = await authService.getMe();

      if (response.status === 200 && response.data) {
        const data = response.data as { authenticated: boolean; user: User | null };
        if (data.authenticated && data.user) {
          setUser(data.user);
        } else {
          setUser(null);
        }
      } else {
        setUser(null);
      }
    } catch {
      setUser(null);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchUser();
  }, [fetchUser]);

  const login = useCallback(() => {
    // Redirect to Gateway's OAuth2 authorization endpoint
    // Gateway handles the OAuth2/PKCE flow with Authorization Server
    window.location.href = `${GATEWAY_URL}${API_CONFIG.endpoints.auth.login}`;
  }, []);

  const logout = useCallback(async () => {
    // POST to Gateway's logout endpoint (required for CSRF protection)
    // Gateway performs OIDC logout (clears session + auth server logout)
    const form = document.createElement('form');
    form.method = 'POST';
    form.action = `${GATEWAY_URL}${API_CONFIG.endpoints.auth.logout}`;

    // Add CSRF token
    const csrfToken = document.cookie
      .split('; ')
      .find(row => row.startsWith('XSRF-TOKEN='))
      ?.split('=')[1];

    if (csrfToken) {
      const csrfInput = document.createElement('input');
      csrfInput.type = 'hidden';
      csrfInput.name = '_csrf';
      csrfInput.value = decodeURIComponent(csrfToken);
      form.appendChild(csrfInput);
    }

    document.body.appendChild(form);
    form.submit();
  }, []);

  const refresh = useCallback(async () => {
    setIsLoading(true);
    await fetchUser();
  }, [fetchUser]);

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isLoading,
        login,
        logout,
        refresh,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
