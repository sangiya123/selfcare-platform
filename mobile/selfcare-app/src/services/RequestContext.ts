/**
 * RequestContext SDK — mobile side of the canonical context contract.
 *
 * Mirrors `com.selfcare.platform.common.context.RequestContext` in platform-common.
 * Holds tenant/user/session/trace/locale identity and materializes it as the exact
 * header set the backend resolves on every request. One singleton per app; refresh
 * after login or tenant switch.
 */

export type RequestChannel = 'MOBILE' | 'WEB' | 'API' | 'KIOSK' | 'IVR' | 'PARTNER';

export interface RequestContextSpec {
  tenantId?: string;
  userId?: string;
  userType?: string;
  sessionId?: string;
  token?: string;
  correlationId?: string;
  traceId?: string;
  channel?: RequestChannel;
  deviceId?: string;
  ipAddress?: string;
  locale?: string;
  environment?: 'dev' | 'qa' | 'staging' | 'prod';
  authMethod?: string;
}

export const REQUEST_HEADERS = {
  TENANT: 'X-Tenant-Id',
  USER: 'X-User-Id',
  USER_TYPE: 'X-User-Type',
  SESSION: 'X-Session-Id',
  CORRELATION: 'X-Correlation-Id',
  TRACE: 'X-Trace-Id',
  CHANNEL: 'X-Channel',
  DEVICE: 'X-Device-Id',
  LOCALE: 'Accept-Language',
  FORWARDED_FOR: 'X-Forwarded-For',
  ENVIRONMENT: 'X-Environment',
  AUTH_METHOD: 'X-Auth-Method',
} as const;

export type RequestContextHeaders = Record<string, string>;

const CORRELATION_PREFIX = 'selfcare-';

export function newCorrelationId(): string {
  const rand = Math.random().toString(16).slice(2, 10);
  return `${CORRELATION_PREFIX}${rand}`;
}

export class RequestContext {
  private static instance: RequestContext | null = null;
  private spec: RequestContextSpec;
  private readonly createdAt: number;

  private constructor(initial?: RequestContextSpec) {
    this.createdAt = Date.now();
    this.spec = { correlationId: newCorrelationId(), ...(initial ?? {}) };
  }

  /** Singleton instance. Create once at app boot, mutate via {@link update}. */
  static current(initial?: RequestContextSpec): RequestContext {
    if (!RequestContext.instance) {
      RequestContext.instance = new RequestContext(initial);
    }
    return RequestContext.instance;
  }

  /** Replace the running context (e.g., after login / tenant switch). */
  static reset(initial?: RequestContextSpec): RequestContext {
    RequestContext.instance = new RequestContext(initial);
    return RequestContext.instance;
  }

  update(patch: RequestContextSpec): this {
    this.spec = { ...this.spec, ...patch };
    return this;
  }

  get tenantId(): string | undefined {
    return this.spec.tenantId;
  }

  get userId(): string | undefined {
    return this.spec.userId;
  }

  get correlationId(): string | undefined {
    return this.spec.correlationId;
  }

  /** Full canonical header map sent with every API request. */
  headers(): RequestContextHeaders {
    const h: RequestContextHeaders = {};
    if (this.spec.tenantId) h[REQUEST_HEADERS.TENANT] = this.spec.tenantId;
    if (this.spec.userId) h[REQUEST_HEADERS.USER] = this.spec.userId;
    if (this.spec.userType) h[REQUEST_HEADERS.USER_TYPE] = this.spec.userType;
    if (this.spec.sessionId) h[REQUEST_HEADERS.SESSION] = this.spec.sessionId;
    if (this.spec.traceId) h[REQUEST_HEADERS.TRACE] = this.spec.traceId;
    if (this.spec.channel) h[REQUEST_HEADERS.CHANNEL] = this.spec.channel;
    if (this.spec.deviceId) h[REQUEST_HEADERS.DEVICE] = this.spec.deviceId;
    if (this.spec.locale) h[REQUEST_HEADERS.LOCALE] = this.spec.locale;
    if (this.spec.ipAddress) h[REQUEST_HEADERS.FORWARDED_FOR] = this.spec.ipAddress;
    if (this.spec.environment) h[REQUEST_HEADERS.ENVIRONMENT] = this.spec.environment;
    if (this.spec.authMethod) h[REQUEST_HEADERS.AUTH_METHOD] = this.spec.authMethod;
    h[REQUEST_HEADERS.CORRELATION] = this.spec.correlationId ?? newCorrelationId();
    if (this.spec.token) h['Authorization'] = this.spec.token.startsWith('Bearer ')
      ? this.spec.token
      : `Bearer ${this.spec.token}`;
    return h;
  }

  /** Copy the current header set onto an existing headers object (merges, does not clobber). */
  applyTo(headers: Record<string, string>): Record<string, string> {
    return { ...this.headers(), ...headers };
  }

  snapshot(): RequestContextSpec {
    return { ...this.spec };
  }

  get startedAt(): number {
    return this.createdAt;
  }
}

/** Drop-in singleton accessor. */
export function requestContext(): RequestContext {
  return RequestContext.current();
}

/** Convenience: canonical headers to attach to any outbound HTTP call. */
export function requestContextHeaders(): RequestContextHeaders {
  return RequestContext.current().headers();
}

/** Convenience: merge current context headers into a plain object for third-party clients. */
export function withRequestContext(headers: Record<string, string>): Record<string, string> {
  return RequestContext.current().applyTo(headers);
}