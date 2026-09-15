/**
 * ApiClient — HTTP client for selfcare API Gateway.
 *
 * All requests carry X-Tenant-Id and, when authenticated, Authorization Bearer token.
 * Responses are unwrapped to data payload. Errors throw selfcareError.
 *
 * Built on axios. In production, replace with tRPC or GraphQL client if preferred.
 */

import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios';
import { selfcareError, ErrorCodes } from './errors';
import { requestContextHeaders } from '../services/RequestContext';

export interface ApiClientOptions {
  tenantId: string;
  baseUrl: string;
  getAccessToken: () => string | null;
  onUnauthorized?: () => void;
  timeout?: number;
}

export class ApiClient {
  private readonly client: AxiosInstance;
  private readonly tenantId: string;
  private readonly getAccessToken: () => string | null;
  private readonly onUnauthorized?: () => void;

  /** Expose the underlying axios client for advanced usage (interceptors, etc.). */
  get httpClient(): AxiosInstance {
    return this.client;
  }

  constructor(options: ApiClientOptions) {
    this.tenantId = options.tenantId;
    this.getAccessToken = options.getAccessToken;
    this.onUnauthorized = options.onUnauthorized;

    this.client = axios.create({
      baseURL: options.baseUrl,
      timeout: options.timeout ?? 15_000,
      headers: {
        'Content-Type': 'application/json',
        'X-Tenant-Id': this.tenantId,
        Accept: 'application/json',
      },
    });

    // Request interceptor: attach the canonical RequestContext headers
    this.client.interceptors.request.use((config) => {
      const existing: Record<string, string> =
        config.headers && typeof config.headers.toJSON === 'function'
          ? (config.headers.toJSON() as Record<string, string>)
          : ((config.headers as Record<string, string>) ?? {});
      // Context headers first; explicit request headers win on collision.
      config.headers = { ...requestContextHeaders(), ...existing } as typeof config.headers;
      return config;
    });

    // Response interceptor: unwrap { data: { ... } } wrapper
    this.client.interceptors.response.use(
      (response: AxiosResponse) => {
        // If response follows ApiResponse wrapper, unwrap
        if (response.data && typeof response.data === 'object' && 'data' in response.data) {
          response.data = (response.data as any).data;
        }
        return response;
      },
      (error) => {
        const status = error.response?.status;
        const code = error.response?.data?.code;
        const message = error.response?.data?.message ?? error.message;
        const details = error.response?.data;

        if (status === 401) {
          this.onUnauthorized?.();
          return Promise.reject(
            new selfcareError(ErrorCodes.UNAUTHORIZED, message, details)
          );
        }
        switch (status) {
          case 403:
            return Promise.reject(new selfcareError(ErrorCodes.FORBIDDEN, message, details));
          case 404:
            return Promise.reject(new selfcareError(ErrorCodes.NOT_FOUND, message, details));
          case 409:
            return Promise.reject(new selfcareError(ErrorCodes.CONFLICT, message, details));
          case 429:
            return Promise.reject(new selfcareError(ErrorCodes.RATE_LIMITED, message, details));
          case 503:
            return Promise.reject(
              new selfcareError(ErrorCodes.SERVICE_UNAVAILABLE, message, details)
            );
          default:
            return Promise.reject(
              new selfcareError(code ?? 'API_ERROR', message, details)
            );
        }
      }
    );
  }

  /** GET request */
  async get<T>(path: string, params?: Record<string, unknown>): Promise<T> {
    const response = await this.client.get<T>(path, { params });
    return response.data;
  }

  /** POST request */
  async post<T>(path: string, body?: Record<string, unknown>): Promise<T> {
    const response = await this.client.post<T>(path, body ?? {});
    return response.data;
  }

  /** PUT request */
  async put<T>(path: string, body?: Record<string, unknown>): Promise<T> {
    const response = await this.client.put<T>(path, body ?? {});
    return response.data;
  }

  /** PATCH request */
  async patch<T>(path: string, body?: Record<string, unknown>): Promise<T> {
    const response = await this.client.patch<T>(path, body ?? {});
    return response.data;
  }

  /** DELETE request */
  async delete<T>(path: string): Promise<T> {
    const response = await this.client.delete<T>(path);
    return response.data;
  }

  /** Request with custom config */
  async request<T>(config: AxiosRequestConfig): Promise<T> {
    const response = await this.client.request<T>(config);
    return response.data;
  }

  /** Upload file (multipart/form-data) */
  async uploadFile<T>(
    path: string,
    file: { uri: string; name: string; type: string },
    extraFields?: Record<string, string>
  ): Promise<T> {
    const form = new FormData();
    form.append('file', {
      uri: file.uri,
      name: file.name,
      type: file.type,
    } as any);
    Object.entries(extraFields ?? {}).forEach(([k, v]) => form.append(k, v));

    const response = await this.client.post<T>(path, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data;
  }

  private authHeaders(): Record<string, string> {
    const token = this.getAccessToken();
    return token ? { Authorization: `Bearer ${token}` } : {};
  }
}
