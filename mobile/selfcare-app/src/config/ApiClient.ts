/**
 * ApiClient — HTTP client for OMOBIO API Gateway.
 *
 * All requests carry X-Tenant-Id and, when authenticated, Authorization Bearer token.
 * Responses are unwrapped to data payload. Errors throw OmobioError.
 *
 * Built on axios. In production, replace with tRPC or GraphQL client if preferred.
 */

import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios';
import { OmobioError, ErrorCodes } from './errors';

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

        if (status === 401) {
          this.onUnauthorized?.();
          throw new OmobioError(ErrorCodes.UNAUTHORIZED, message);
        }
        if (status === 403) {
          throw new OmobioError(ErrorCodes.FORBIDDEN, message);
        }
        if (status === 404) {
          throw new OmobioError(ErrorCodes.NOT_FOUND, message);
        }
        if (status === 409) {
          throw new OmobioError(ErrorCodes.CONFLICT, message);
        }
        if (status === 429) {
          throw new OmobioError(ErrorCodes.RATE_LIMITED, message);
        }
        if (status === 503) {
          throw new OmobioError(ErrorCodes.SERVICE_UNAVAILABLE, message);
        }

        throw new OmobioError(code ?? 'API_ERROR', message, error.response?.data);
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
