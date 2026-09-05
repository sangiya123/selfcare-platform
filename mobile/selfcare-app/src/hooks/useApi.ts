/**
 * useApi — Domain-API hook for the Selfcare App.
 *
 * Returns a structured API object grouped by domain:
 *   api.auth.*    — OTP send/verify, signout, session
 *   api.bills.*   — bill list, detail, pay
 *   api.usage.*   — balance, usage
 *   api.payments.* — payment processing
 *   api.config.*  — manifest refresh
 *
 * All calls are tenant-scoped via X-Tenant-Id. Authentication
 * headers are added automatically when an access token is present.
 */
import { useMemo, useCallback } from 'react';
import { useTenant } from './useTenant';
import { ApiClient } from '../config/ApiClient';
import { useAuthStore } from './useAuth';

export interface OtpRequest {
  identifier: string;
  channel: 'sms' | 'whatsapp' | 'email';
  /** @deprecated tenantId is read from X-Tenant-Id header; kept for client-side bookkeeping */
  tenantId?: string | null;
}

export interface OtpVerifyRequest {
  identifier: string;
  code: string;
  /** @deprecated tenantId is read from X-Tenant-Id header */
  tenantId?: string | null;
}

export interface OtpResult {
  success: boolean;
  correlationId?: string;
  expiresIn?: number;
  errorMessage?: string;
}

export interface ApiHandle {
  auth: {
    sendOtp: (req: OtpRequest) => Promise<OtpResult>;
    verifyOtp: (req: OtpVerifyRequest) => Promise<OtpResult>;
    signOut: () => Promise<void>;
    refresh: () => Promise<boolean>;
  };
  bills: {
    list: (opts: { tenantId: string | null; status?: string }) => Promise<{ items: any[] }>;
    get: (billId: string) => Promise<any>;
  };
  usage: {
    getCurrent: (opts: { tenantId: string | null }) => Promise<any>;
  };
  payments: {
    pay: (req: any) => Promise<any>;
  };
  config: {
    forceRefresh: () => Promise<void>;
  };
}

/**
 * Resolve a stable base URL for the SDK HTTP client.
 * In dev this falls back to localhost; in production it's set
 * via the .env at build time.
 */
function resolveBaseUrl(): string {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const fromEnv = (typeof process !== 'undefined' && (process as any)?.env?.MOBILE_API_BASE_URL) as
    | string
    | undefined;
  if (fromEnv) return fromEnv;
  // Default for local dev with API gateway on :8080
  return 'http://localhost:8080';
}

export function useApi(): ApiHandle {
  const { tenantId } = useTenant();
  const auth = useAuthStore();

  const client = useMemo(() => {
    const c = new ApiClient({
      tenantId: tenantId ?? 'unknown',
      baseUrl: resolveBaseUrl(),
      getAccessToken: () => auth.accessToken,
      onUnauthorized: () => {
        auth.signOutLocally();
      },
    });
    // Attach a response interceptor that unwraps { data, meta } envelopes
    // to just the data payload. The SDK's ApiClient.post/get return the
    // raw response body; this hook assumes the data is the body directly.
    c.httpClient.interceptors.response.use((response) => {
      if (
        response.data &&
        typeof response.data === 'object' &&
        'data' in response.data &&
        (response.data as any).data !== null &&
        (response.data as any).data !== undefined
      ) {
        response.data = (response.data as any).data;
      }
      return response;
    });
    return c;
  }, [tenantId, auth]);

  // Suppress unused-var lint: `auth` is read inside the memo
  void auth;

  const sendOtp = useCallback(
    async (req: OtpRequest): Promise<OtpResult> => {
      try {
        const resp = await client.post<{ correlationId: string; expiresIn: number }>(
          '/api/v1/auth/otp',
          {
            identifier: req.identifier,
            channel: (req.channel ?? 'sms').toUpperCase(),
          }
        );
        return { success: true, correlationId: resp.correlationId, expiresIn: resp.expiresIn };
      } catch (err: any) {
        return { success: false, errorMessage: err?.message ?? 'Failed to send OTP' };
      }
    },
    [client]
  );

  const verifyOtp = useCallback(
    async (req: OtpVerifyRequest): Promise<OtpResult> => {
      try {
        const resp = await client.post<{
          accessToken: string;
          refreshToken: string;
          sessionId: string;
          expiresIn: number;
        }>(
          '/api/v1/auth/otp/verify',
          {
            identifier: req.identifier,
            code: req.code,
            correlationId: undefined,
          }
        );
        auth.persistSession({
          accessToken: resp.accessToken,
          refreshToken: resp.refreshToken,
          expiresIn: resp.expiresIn,
        });
        return { success: true };
      } catch (err: any) {
        return { success: false, errorMessage: err?.message ?? 'Invalid code' };
      }
    },
    [client, auth]
  );

  const signOut = useCallback(async () => {
    try {
      await client.post('/api/v1/customer/auth/signout', {});
    } catch {
      // Ignore network errors on signout
    } finally {
      auth.signOutLocally();
    }
  }, [client, auth]);

  const refresh = useCallback(async (): Promise<boolean> => {
    try {
      const resp = await client.post<{ accessToken: string; expiresIn: number }>(
        '/api/v1/customer/auth/refresh',
        { refreshToken: auth.refreshToken }
      );
      auth.updateAccessToken(resp.accessToken, resp.expiresIn);
      return true;
    } catch {
      return false;
    }
  }, [client, auth]);

  return {
    auth: {
      sendOtp,
      verifyOtp,
      signOut,
      refresh,
    },
    bills: {
      list: (opts) => client.get('/api/v1/billing/bills', { tenantId: opts.tenantId, status: opts.status }),
      get: (billId) => client.get(`/api/v1/billing/bills/${billId}`),
    },
    usage: {
      getCurrent: (opts) => client.get('/api/v1/usage/current', { tenantId: opts.tenantId }),
    },
    payments: {
      pay: (req) => client.post('/api/v1/payments', req),
    },
    config: {
      forceRefresh: async () => {
        // The actual ETag round-trip is owned by ConfigSDK; this just
        // signals it to bypass cache. We import lazily to avoid cycles.
        const { SelfcareSDK } = await import('../config/ConfigSDK');
        const sdk = (globalThis as any).__OMOBIO_SDK__ as SelfcareSDK | undefined;
        if (sdk) {
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          await (sdk as any).config?.forceRefresh?.();
        }
      },
    },
  };
}
