/**
 * AuthSDK — Authentication lifecycle for OMOBIO Selfcare App.
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

const ACCESS_TOKEN_KEY = 'omobio_access_token';
const REFRESH_TOKEN_KEY = 'omobio_refresh_token';
const SESSION_ID_KEY = 'omobio_session_id';
const DEVICE_ID_KEY = 'omobio_device_id';
const OTP_CORRELATION_KEY = 'omobio_otp_correlation';

const storage = new MMKV({ id: 'omobio-auth' });

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

type AuthEventType = 'authenticated' | 'token_refreshed' | 'signed_out' | 'session_expired';
type AuthListener = (event: AuthEventType, data?: unknown) => void;

export class AuthSDK {
  private readonly tenantId: string;
  private readonly baseUrl: string;
  private listeners = new Set<AuthListener>();
  private refreshTimer: ReturnType<typeof setTimeout> | null = null;
  private apiClient: ApiClient | null = null;

  constructor(tenantId: string, baseUrl: string) {
    this.tenantId = tenantId;
    this.baseUrl = baseUrl;
  }

  /** Inject ApiClient for use by token refresh */
  setApiClient(client: ApiClient): void {
    this.apiClient = client;
  }

  // ============================================================
  // OTP Auth
  // ============================================================

  async sendOtp(msisdn: string, channel: 'SMS' | 'WHATSAPP' = 'SMS'): Promise<OtpSendResult> {
    const res = await axios.post<{ data: OtpSendResult }>(
      `${this.baseUrl}/api/v1/auth/otp`,
      { msisdn, channel },
      { headers: { 'X-Tenant-Id': this.tenantId } }
    );
    const result = res.data.data;
    if (result.success && result.correlationId) {
      await storage.setAsync(OTP_CORRELATION_KEY, result.correlationId);
    }
    return result;
  }

  async verifyOtp(msisdn: string, code: string): Promise<OtpVerifyResult> {
    const correlationId = await storage.getStringAsync(OTP_CORRELATION_KEY);
    const res = await axios.post<{ data: OtpVerifyResult }>(
      `${this.baseUrl}/api/v1/auth/otp/verify`,
      { msisdn, otpCode: code, correlationId },
      { headers: { 'X-Tenant-Id': this.tenantId } }
    );
    const result = res.data.data;
    if (result.success) {
      await this.persistTokens(result);
      await storage.deleteAsync(OTP_CORRELATION_KEY);
      this.scheduleRefresh();
      this.emit('authenticated', { msisdn });
    }
    return result;
  }

  // ============================================================
  // Token Management
  // ============================================================

  async refreshTokens(): Promise<void> {
    const refreshToken = await this.getRefreshToken();
    if (!refreshToken) throw new Error('No refresh token available');

    try {
      const res = await axios.post<{ data: OtpVerifyResult }>(
        `${this.baseUrl}/api/v1/auth/refresh`,
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
          `${this.baseUrl}/api/v1/auth/signout`,
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
    this.listeners.forEach((l) => l(event, data));
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
    // Refresh at 80% of the 42-day window
    const ms = 42 * 24 * 60 * 60 * 1000 * 0.8;
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

const _storage = new MMKV({ id: 'omobio-auth' });
_storage.getStringAsync = (key: string) => Promise.resolve(_storage.getString(key) ?? null);
_storage.setAsync = (key: string, value: string) => Promise.resolve(_storage.set(key, value));
_storage.deleteAsync = (key: string) => Promise.resolve(_storage.delete(key));
