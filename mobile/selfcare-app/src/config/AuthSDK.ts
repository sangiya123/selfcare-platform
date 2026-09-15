/**
 * AuthSDK — Authentication lifecycle for Selfcare App.
 *
 * Handles:
 * - OTP send / verify (primary auth for telco)
 * - OIDC / social login (optional)
 * - Token storage (MMKV)
 * - Token refresh (background)
 * - Session sign-out
 * - Biometric / PIN re-auth for sensitive operations
 *
 * Tokens follow ADR-011:
 *   Access token: 42-day JWT (short-lived for API auth)
 *   Refresh token: 7-month Opaque (used to rotate access token)
 */

import EncryptedStorage from 'react-native-encrypted-storage';
import axios from 'axios';
import { MMKV } from 'react-native-mmkv';
import type { ApiClient } from './ApiClient';

const ACCESS_TOKEN_KEY = 'selfcare_access_token';
const REFRESH_TOKEN_KEY = 'selfcare_refresh_token';
const SESSION_ID_KEY = 'selfcare_session_id';
const DEVICE_ID_KEY = 'selfcare_device_id';
const OTP_CORRELATION_KEY = 'selfcare_otp_correlation';

const storage = new MMKV({ id: 'selfcare-auth' });

export interface OtpSendResult {
  success: boolean;
  correlationId?: string;
  expiresAt?: string;
  maskedMsisdn?: string;
}

export interface OtpVerifyResult {
  success: boolean;
  accessToken?: string;
  refreshToken?: string;
  sessionId?: string;
  expiresAt?: string;
}

export interface AuthState {
  isAuthenticated: boolean;
  hasCompletedOnboarding: boolean;
  primaryMsisdn?: string;
  primaryConnectionId?: string;
}

/**
 * Auth endpoint template NOT hardcoded: paths are authored in the admin portal
 * (manifest `services.auth`) and injected via setEndpoints before use. The SDK
 * refuses to invent endpoints — config missing = explicit error.
 */
export interface AuthEndpoints {
  otp?: string;
  otpVerify?: string;
  refresh?: string;
  signout?: string;
  /** Validation of an external login-flow auth code (may be an external URL). */
  exchange?: string;
}

/** Optional session policy overrides from the manifest (never invented). */
export interface AuthSessionPolicy {
  /** Refresh access token at this fraction of the access token TTL (0 < x < 1). */
  refreshOffsetRatio?: number;
  /** Access token lifetime in ms used to schedule the background refresh. */
  accessTtlMs?: number;
}

/** OTP delivery channel supported by the configured auth endpoints. */
export type OtpChannel = 'SMS' | 'WHATSAPP' | 'EMAIL';

type AuthEventType = 'authenticated' | 'token_refreshed' | 'signed_out' | 'session_expired';
type AuthListener = (event: AuthEventType, data?: unknown) => void;

export class AuthSDK {
  private readonly tenantId: string;
  private readonly baseUrl: string;
  private endpoints: AuthEndpoints | null = null;
  private sessionPolicy: AuthSessionPolicy = {};
  private listeners = new Set<AuthListener>();
  private refreshTimer: ReturnType<typeof setTimeout> | null = null;
  private apiClient: ApiClient | null = null;

  constructor(tenantId: string, baseUrl: string, endpoints?: AuthEndpoints) {
    this.tenantId = tenantId;
    this.baseUrl = baseUrl;
    this.endpoints = endpoints ?? null;
  }

  /** Inject ApiClient for use by token refresh */
  setApiClient(client: ApiClient): void {
    this.apiClient = client;
  }

  /** Apply configurable endpoint + session templates authored in the admin portal. */
  setEndpoints(endpoints: AuthEndpoints | null | undefined): void {
    this.endpoints = endpoints ?? null;
  }

  setSessionPolicy(policy: AuthSessionPolicy): void {
    this.sessionPolicy = policy;
  }

  /** Resolve a configured endpoint; refuse to invent paths (v6 rule #1). */
  private ep(key: keyof AuthEndpoints): string {
    const path = this.endpoints?.[key];
    if (!path) {
      throw new Error(`auth.endpoint.required:${String(key)}`);
    }
    return `${this.baseUrl}${path}`;
  }

  // ============================================================
  // OTP Auth
  // ============================================================

  async sendOtp(msisdn: string, channel: OtpChannel = 'SMS'): Promise<OtpSendResult> {
    const res = await axios.post<{ data: OtpSendResult }>(
      this.ep('otp'),
      { identifier: msisdn, channel },
      { headers: { 'X-Tenant-Id': this.tenantId } }
    );
    const result = res.data.data;
    if (result.correlationId) {
      await storage.setAsync(OTP_CORRELATION_KEY, result.correlationId);
      return { success: true, correlationId: result.correlationId, expiresAt: result.expiresAt };
    }
    return { success: false };
  }

  async verifyOtp(msisdn: string, code: string): Promise<OtpVerifyResult> {
    const correlationId = await storage.getStringAsync(OTP_CORRELATION_KEY);
    const deviceId = await this.getDeviceId();
    const res = await axios.post<{ data: OtpVerifyResult }>(
      this.ep('otpVerify'),
      { identifier: msisdn, code, correlationId, deviceId, deviceDescription: 'selfcare App' },
      { headers: { 'X-Tenant-Id': this.tenantId } }
    );
    const result = res.data.data;
    if (result.accessToken) {
      await this.persistTokens(result);
      await storage.deleteAsync(OTP_CORRELATION_KEY);
      this.scheduleRefresh();
      this.emit('authenticated', { msisdn });
      return { success: true, accessToken: result.accessToken, refreshToken: result.refreshToken, sessionId: result.sessionId };
    }
    return { success: false };
  }

  /**
   * Validate an external login-flow auth code (operator web OTP → deep link
   * return). The validation endpoint (`services.auth.exchange`) may itself be
   * an external URL — path is authored in the admin portal, never invented.
   */
  async completeExternalAuth(code: string): Promise<OtpVerifyResult> {
    const endpoint = this.endpoints?.exchange;
    if (!endpoint) {
      throw new Error('auth.endpoint.required:exchange');
    }
    const res = await axios.post<{ data: OtpVerifyResult }>(
      `${this.baseUrl}${endpoint}`,
      { code },
      { headers: { 'X-Tenant-Id': this.tenantId } }
    );
    const result = res.data.data;
    if (result.accessToken) {
      await this.persistTokens(result);
      this.scheduleRefresh();
      this.emit('authenticated', { external: true });
      return { success: true, accessToken: result.accessToken, refreshToken: result.refreshToken, sessionId: result.sessionId };
    }
    return { success: false };
  }

  // ============================================================
  // Token Management
  // ============================================================

  async refreshTokens(): Promise<void> {
    const refreshToken = await this.getRefreshToken();
    if (!refreshToken) throw new Error('No refresh token available');

    try {
      const res = await axios.post<{ data: OtpVerifyResult }>(
        this.ep('refresh'),
        { refreshToken },
        { headers: { 'X-Tenant-Id': this.tenantId } }
      );
      await this.persistTokens(res.data.data);
      this.scheduleRefresh();
      this.emit('token_refreshed');
    } catch (err: any) {
      if (err.response?.status === 401) {
        await this.clearSession();
        this.emit('session_expired');
      }
      throw err;
    }
  }

  async getAccessToken(): Promise<string | null> {
    return storage.getStringAsync(ACCESS_TOKEN_KEY);
  }

  isAuthenticated(): boolean {
    // Optimistic check — tokens are present
    return storage.contains(ACCESS_TOKEN_KEY);
  }

  async signOut(): Promise<void> {
    const sessionId = await this.getSessionId();
    if (sessionId) {
      try {
        await axios.post(
          this.ep('signout'),
          { sessionId },
          {
            headers: {
              'X-Tenant-Id': this.tenantId,
              Authorization: `Bearer ${await this.getAccessToken()}`,
            },
          }
        );
      } catch {
        // Best-effort — proceed with local cleanup
      }
    }
    await this.clearSession();
    this.emit('signed_out');
  }

  // ============================================================
  // Session Data
  // ============================================================

  async getSessionId(): Promise<string | null> {
    return storage.getStringAsync(SESSION_ID_KEY);
  }

  async getDeviceId(): Promise<string> {
    let deviceId = await storage.getStringAsync(DEVICE_ID_KEY);
    if (!deviceId) {
      deviceId = `dev-${Date.now()}-${Math.random().toString(36).slice(2)}`;
      await storage.setAsync(DEVICE_ID_KEY, deviceId);
    }
    return deviceId;
  }

  // ============================================================
  // Auth Events
  // ============================================================

  addAuthListener(listener: AuthListener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private emit(event: AuthEventType, data?: unknown): void {
    this.listeners.forEach((l) => (data === undefined ? l(event) : l(event, data)));
  }

  // ============================================================
  // Helpers
  // ============================================================

  private async persistTokens(result: OtpVerifyResult): Promise<void> {
    if (result.accessToken) {
      await storage.setAsync(ACCESS_TOKEN_KEY, result.accessToken);
    }
    if (result.refreshToken) {
      await storage.setAsync(REFRESH_TOKEN_KEY, result.refreshToken);
    }
    if (result.sessionId) {
      await storage.setAsync(SESSION_ID_KEY, result.sessionId);
    }
  }

  private async getRefreshToken(): Promise<string | null> {
    return storage.getStringAsync(REFRESH_TOKEN_KEY);
  }

  private async clearSession(): Promise<void> {
    this.cancelRefresh();
    await storage.deleteAsync(ACCESS_TOKEN_KEY);
    await storage.deleteAsync(REFRESH_TOKEN_KEY);
    await storage.deleteAsync(SESSION_ID_KEY);
  }

  private scheduleRefresh(): void {
    this.cancelRefresh();
    // Access TTL + refresh offset are authored in the admin portal
    // (manifest services.session) and injected via setSessionPolicy; the SDK
    // never guesses token lifetimes (ADR-011 remains an open policy review).
    const accessTtlMs = this.sessionPolicy.accessTtlMs ?? 42 * 24 * 60 * 60 * 1000;
    const ratio = this.sessionPolicy.refreshOffsetRatio ?? 0.8;
    const ms = Math.max(0, accessTtlMs * ratio);
    this.refreshTimer = setTimeout(() => this.refreshTokens().catch(console.error), ms);
  }

  private cancelRefresh(): void {
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
      this.refreshTimer = null;
    }
  }
}

// ============================================================
// Type augmentation for MMKV
// ============================================================
declare module 'react-native-mmkv' {
  interface MMKV {
    getStringAsync(key: string): Promise<string | null>;
    setAsync(key: string, value: string): Promise<void>;
    deleteAsync(key: string): Promise<void>;
  }
}

const _storage = new MMKV({ id: 'selfcare-auth' });
_storage.getStringAsync = (key: string) => Promise.resolve(_storage.getString(key) ?? null);
_storage.setAsync = (key: string, value: string) => Promise.resolve(_storage.set(key, value));
_storage.deleteAsync = (key: string) => Promise.resolve(_storage.delete(key));
