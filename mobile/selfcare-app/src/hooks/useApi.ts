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
import { servicePath } from '../config/serviceEndpoints';
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
    current: (opts: { tenantId: string | null }) => Promise<any>;
  };
  usage: {
    getCurrent: (opts: { tenantId: string | null }) => Promise<any>;
    history: (opts: { tenantId: string | null; connectionId?: string | null }) => Promise<{ items: any[] }>;
  };
  payments: {
    pay: (req: any) => Promise<any>;
    history: (opts: { tenantId: string | null; limit?: number }) => Promise<{ items: any[] }>;
    savedCards: (opts: { tenantId: string | null }) => Promise<{ items: any[] }>;
  };
  catalog: {
    list: (opts: { tenantId: string | null; category?: string }) => Promise<{ items: any[] }>;
    getActive: (opts: { tenantId: string | null }) => Promise<any>;
    recommendations: (opts: { tenantId: string | null }) => Promise<{ items: any[] }>;
  };
  content: {
    banners: (opts: { tenantId: string | null; type?: string }) => Promise<{ items: any[] }>;
    faqs: (opts: { tenantId: string | null }) => Promise<{ items: any[] }>;
    articles: (opts: { tenantId: string | null }) => Promise<{ items: any[] }>;
  };
  notifications: {
    list: (opts: { tenantId: string | null; limit?: number }) => Promise<{ items: any[] }>;
  };
  profile: {
    summary: (opts: { tenantId: string | null }) => Promise<any>;
    connections: (opts: { tenantId: string | null }) => Promise<{ items: any[] }>;
  };
  support: {
    tickets: (opts: { tenantId: string | null; connectionId?: string | null }) => Promise<any[]>;
    ticket: (opts: { tenantId: string | null; ticketId: string }) => Promise<any>;
    create: (req: {
      subject?: string;
      category?: string;
      priority?: string;
      description: string;
      connectionId?: string;
      productCode?: string;
      attachments?: string[];
    }) => Promise<any>;
    updateStatus: (opts: { tenantId: string | null; ticketId: string; status: string; resolution?: string }) => Promise<any>;
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
          servicePath('auth', 'otp', '/api/v1/auth/otp'),
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
          servicePath('auth', 'otpVerify', '/api/v1/auth/otp/verify'),
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
      await client.post(servicePath('auth', 'signout', '/api/v1/auth/signout'), {});
    } catch {
      // Ignore network errors on signout
    } finally {
      auth.signOutLocally();
    }
  }, [client, auth]);

  const refresh = useCallback(async (): Promise<boolean> => {
    try {
      const resp = await client.post<{ accessToken: string; expiresIn: number }>(
        servicePath('auth', 'refresh', '/api/v1/auth/refresh'),
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
      list: (opts) => client.get(servicePath('billing', 'bills', '/api/v1/bills'), { tenantId: opts.tenantId, status: opts.status }),
      get: (billId) => client.get(servicePath('billing', 'bill', '/api/v1/bills/{billId}', { billId })),
      current: (opts) => client.get(servicePath('billing', 'current', '/api/v1/bills/current'), { tenantId: opts.tenantId }),
    },
    usage: {
      getCurrent: (opts) => client.get(servicePath('usage', 'current', '/api/v1/usage/current'), { tenantId: opts.tenantId }),
      history: (opts) =>
        client.get(servicePath('usage', 'history', '/api/v1/usage/{connectionId}/history', {
          connectionId: opts.connectionId ?? '',
        }), { tenantId: opts.tenantId }),
    },
    payments: {
      pay: (req) => client.post(servicePath('payments', 'create', '/api/v1/payments'), req),
      history: (opts) => client.get(servicePath('payments', 'history', '/api/v1/payments/history'), { tenantId: opts.tenantId, limit: opts.limit }),
      savedCards: (opts) => client.get(servicePath('payments', 'savedCards', '/api/v1/payments/saved-cards'), { tenantId: opts.tenantId }),
    },
    catalog: {
      list: (opts) => client.get(servicePath('catalog', 'list', '/api/v1/products'), { tenantId: opts.tenantId, category: opts.category }),
      getActive: (opts) => client.get(servicePath('catalog', 'getActive', '/api/v1/offers/active'), { tenantId: opts.tenantId }),
      recommendations: (opts) => client.get(servicePath('catalog', 'recommendations', '/api/v1/offers/recommendations'), { tenantId: opts.tenantId }),
    },
    content: {
      banners: (opts) => client.get(servicePath('content', 'banners', '/api/v1/content/banners'), { tenantId: opts.tenantId, type: opts.type }),
      faqs: (opts) => client.get(servicePath('content', 'faqs', '/api/v1/content/faqs'), { tenantId: opts.tenantId }),
      articles: (opts) => client.get(servicePath('content', 'articles', '/api/v1/content/articles'), { tenantId: opts.tenantId }),
    },
    notifications: {
      list: (opts) => client.get(servicePath('notifications', 'list', '/api/v1/notifications'), { tenantId: opts.tenantId, limit: opts.limit }),
    },
    profile: {
      summary: (opts) => client.get(servicePath('profile', 'summary', '/api/v1/me'), { tenantId: opts.tenantId }),
      connections: (opts) => client.get(servicePath('profile', 'connections', '/api/v1/me/connections'), { tenantId: opts.tenantId }),
    },
    support: {
      tickets: (opts) => client.get(servicePath('support', 'tickets', '/api/v1/support/tickets'),
        { tenantId: opts.tenantId, connectionId: opts.connectionId || undefined }),
      ticket: (opts) => client.get(servicePath('support', 'ticket', '/api/v1/support/tickets/{ticketId}', { ticketId: opts.ticketId }),
        { tenantId: opts.tenantId }),
      create: (req) => client.post(servicePath('support', 'create', '/api/v1/support/tickets'), req),
      updateStatus: (opts) => client.put(
        servicePath('support', 'status', '/api/v1/support/tickets/{ticketId}/status', { ticketId: opts.ticketId }),
        { status: opts.status, resolution: opts.resolution ?? null }),
    },
    config: {
      forceRefresh: async () => {
        // The actual ETag round-trip is owned by ConfigSDK; this just
        // signals it to bypass cache. We import lazily to avoid cycles.
        const { SelfcareSDK } = await import('../config/ConfigSDK');
        const sdk = (globalThis as any).__SELFCARE_SDK__ as InstanceType<typeof SelfcareSDK> | undefined;
        if (sdk) {
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          await (sdk as any).config?.forceRefresh?.();
        }
      },
    },
  };
}
