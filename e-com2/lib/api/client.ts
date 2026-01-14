import { API_CONFIG, getCsrfToken } from './config';

interface ApiResponse<T> {
  data: T | null;
  error: string | null;
  status: number;
}

interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
}

/**
 * API Client for BFF Pattern
 *
 * Browser calls Gateway directly:
 * - Gateway handles session-based OAuth2 authentication
 * - Gateway uses TokenRelay to forward access token to microservices
 * - CSRF token is sent for state-changing requests (POST, PUT, DELETE)
 */
class ApiClient {
  private readonly baseUrl: string;

  constructor() {
    this.baseUrl = API_CONFIG.gatewayUrl;
  }

  /**
   * Make a request to Gateway
   * Automatically includes credentials (cookies) and CSRF token
   */
  async request<T>(endpoint: string, options: RequestOptions = {}): Promise<ApiResponse<T>> {
    const { body, ...fetchOptions } = options;
    const method = fetchOptions.method || 'GET';

    const headers = new Headers(fetchOptions.headers);
    headers.set('Content-Type', 'application/json');

    // Add CSRF token for state-changing requests
    if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
      const csrfToken = getCsrfToken();
      if (csrfToken) {
        headers.set('X-XSRF-TOKEN', csrfToken);
      }
    }

    const url = `${this.baseUrl}${endpoint}`;

    try {
      const response = await fetch(url, {
        ...fetchOptions,
        method,
        headers,
        credentials: 'include', // Always include cookies for session
        body: body ? JSON.stringify(body) : undefined,
      });

      // Handle 401 - not authenticated
      if (response.status === 401) {
        return {
          data: null,
          error: 'Not authenticated',
          status: 401,
        };
      }

      if (!response.ok) {
        const errorText = await response.text();
        console.error(`API Error [${response.status}] ${url}:`, errorText);
        return {
          data: null,
          error: errorText || `Request failed with status ${response.status}`,
          status: response.status,
        };
      }

      // Handle empty or non-JSON responses from API response
      const text = await response.text();
      let data: T | null = null;

      if (text) {
        try {
          data = JSON.parse(text) as T;
        } catch {
          data = text as unknown as T;
        }
      }

      return { data, error: null, status: response.status };
    } catch (error) {
      console.error(`Network Error ${url}:`, error);
      return {
        data: null,
        error: error instanceof Error ? error.message : 'Network error',
        status: 500,
      };
    }
  }

  get<T>(endpoint: string) {
    return this.request<T>(endpoint, { method: 'GET' });
  }

  post<T>(endpoint: string, body: unknown) {
    return this.request<T>(endpoint, { method: 'POST', body });
  }

  put<T>(endpoint: string, body: unknown) {
    return this.request<T>(endpoint, { method: 'PUT', body });
  }

  delete<T>(endpoint: string) {
    return this.request<T>(endpoint, { method: 'DELETE' });
  }
}

// Singleton instance
export const apiClient = new ApiClient();

/**
 * Product Service - calls Gateway directly
 */
export const productService = {
  endpoint: API_CONFIG.endpoints.products,

  getAll: () => apiClient.get(API_CONFIG.endpoints.products),

  getById: (uuid: string) =>
    apiClient.get(`${API_CONFIG.endpoints.products}/${uuid}`),

  create: (data: { productName: string; price: number }) =>
    apiClient.post(API_CONFIG.endpoints.products, data),

  update: (uuid: string, data: { productName?: string; price?: number }) =>
    apiClient.put(`${API_CONFIG.endpoints.products}/${uuid}`, data),

  delete: (uuid: string) =>
    apiClient.delete(`${API_CONFIG.endpoints.products}/${uuid}`),
};

/**
 * Order Service - calls Gateway directly
 */
export const orderService = {
  endpoint: API_CONFIG.endpoints.orders,

  getAll: () => apiClient.get(API_CONFIG.endpoints.orders),

  getById: (uuid: string) =>
    apiClient.get(`${API_CONFIG.endpoints.orders}/${uuid}`),

  create: (data: { productUuid: string; quantity: number }) =>
    apiClient.post(API_CONFIG.endpoints.orders, data),

  delete: (uuid: string) =>
    apiClient.delete(`${API_CONFIG.endpoints.orders}/${uuid}`),
};

/**
 * Auth Service - calls Gateway auth endpoints
 */
export const authService = {
  getMe: () => apiClient.get(API_CONFIG.endpoints.auth.me),
  getStatus: () => apiClient.get(API_CONFIG.endpoints.auth.status),
};
