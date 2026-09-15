/**
 * Selfcare SDK — React Native
 *
 * Environment: reads from .env (loaded by ./load-env.sh before build).
 * Key env vars:
 *   SELFCARE_ENV          = dev | stg | reg | prod
 *   MOBILE_API_BASE_URL = API gateway URL
 *   MOBILE_TENANT_ID    = default tenant for this app
 */
import { ActionEngine } from './ActionEngine';
import { AuthSDK } from './AuthSDK';
import { ApiClient } from './ApiClient';
import { AIClient } from './AIClient';
import type { Manifest, ThemeTokens } from './types';

// ============================================================
// SelfcareSDK — main entry point
// ============================================================

export class SelfcareSDK {
  public readonly config: ConfigSDK;
  public readonly auth: AuthSDK;
  public readonly api: ApiClient;
  public readonly ai: AIClient;
  public readonly actionEngine: ActionEngine;

  private tenantId: string;
  private apiBaseUrl: string;
  private env: 'development' | 'staging' | 'production';
  private initialized = false;

  constructor(options: {
    tenantId?: string;
    apiBaseUrl?: string;
    env?: 'development' | 'staging' | 'production';
  }) {
    // Env-driven defaults — MOBILE_TENANT_ID must be set for each environment
    // 'dialog-lk' is ONLY a local development fallback, never used in stg/reg/prod
    this.tenantId =
      options.tenantId ??
      process.env.MOBILE_TENANT_ID ??
      (this._isDev() ? 'dialog-lk' : '');
    this.apiBaseUrl = options.apiBaseUrl ?? (process.env.MOBILE_API_BASE_URL ?? 'http://localhost:8080');
    this.env = options.env ?? ((process.env.MOBILE_ENV as any) ?? 'development');

    this.auth = new AuthSDK(this.tenantId, this.apiBaseUrl);
    this.api = new ApiClient({
      tenantId: this.tenantId,
      baseUrl: this.apiBaseUrl,
      getAccessToken: () => useAuthStore.getState().accessToken,
    });
    this.ai = new AIClient(this.api, this.tenantId);
    this.config = new ConfigSDK(this.tenantId, this.apiBaseUrl);
    this.actionEngine = new ActionEngine(this);
  }

  async initialize(): Promise<void> {
    if (this.initialized) return;

    // Load manifest (uses ETag + MMKV cache)
    await this.config.refresh();

    // Inject configured service templates into the SDK clients — endpoint
    // paths and session policy are admin-authored (v6 rule #1). The SDK never
    // invents them; missing auth endpoints surface as explicit errors on use.
    const services = (
      this.getManifest() as unknown as { services?: ServiceEndpoints }
    )?.services;
    if (services?.auth) {
      this.auth.setEndpoints(services.auth);
    }
    if (services?.session?.refreshOffsetRatio) {
      this.auth.setSessionPolicy({
        refreshOffsetRatio: services.session.refreshOffsetRatio,
        accessTtlMs: services.session.accessTtlDays
          ? services.session.accessTtlDays * 24 * 60 * 60 * 1000
          : undefined,
      });
    }

    this.initialized = true;
  }

  getManifest(): Manifest | null {
    return this.config.getManifest();
  }

  /**
   * Resolve the compiled manifest for an experience (e.g. "home", "bills",
   * "support"). For the default experience this returns the boot manifest;
   * otherwise it lazily loads + caches that experience's manifest.
   */
  async getManifestForExperience(experience: string): Promise<Manifest | null> {
    if (!experience || experience === 'home') {
      return this.config.getManifest();
    }
    const loaded = await this.config.refreshExperience(experience);
    return loaded ?? this.config.getManifest();
  }

  getTheme(): ThemeTokens | null {
    const manifest = this.getManifest();
    return manifest?.theme ?? null;
  }

  isOnline(): boolean {
    return this.config.isOnline();
  }

  private _isDev(): boolean {
    return (
      this.env === 'development' ||
      !process.env.NODE_ENV ||
      process.env.NODE_ENV === 'development'
    );
  }
}

// ============================================================
// ConfigSDK
// ============================================================

import { MMKV } from 'react-native-mmkv';
import axios, { AxiosInstance } from 'axios';
import { localizer } from '../manifest/Localization';
import { ManifestI18n, ServiceEndpoints } from '../manifest/types';
import { useAuthStore } from '../hooks/useAuth';

const storage = new MMKV({ id: 'selfcare-config' });

/** Initialize the localization engine from a manifest (when present). */
function initLocalization(manifest: Manifest | null): void {
  if (!manifest) return;
  localizer.init((manifest as unknown as { i18n?: ManifestI18n }).i18n);
}

export class ConfigSDK {
  private tenantId: string;
  private baseUrl: string;
  private client: AxiosInstance;
  private manifest: Manifest | null = null;
  private etag: string | null = null;
  private lastSync: string | null = null;
  private experienceManifests: Map<string, Manifest> = new Map();
  private experienceEtags: Map<string, string> = new Map();

  constructor(tenantId: string, baseUrl: string) {
    this.tenantId = tenantId;
    this.baseUrl = baseUrl ?? (process.env.MOBILE_API_BASE_URL ?? 'http://localhost:8080');
    this.client = axios.create({ baseURL: this.baseUrl });
    this.etag = storage.getString(`etag:${tenantId}`) ?? null;

    // Load cached manifest
    const cached = storage.getString(`manifest:${tenantId}`);
    if (cached) {
      try {
        this.manifest = JSON.parse(cached) as Manifest;
        initLocalization(this.manifest);
      } catch {}
    }
  }

  async refresh(): Promise<void> {
    try {
      const headers: Record<string, string> = {
        'X-Tenant-Id': this.tenantId,
      };
      if (this.etag) {
        headers['If-None-Match'] = this.etag;
      }

      const response = await this.client.get<Manifest>('/api/v1/config/manifest', { headers });
      if (response.status === 304 || !response.data) {
        this.lastSync = new Date().toISOString();
        return;
      }
      this.manifest = response.data;
      initLocalization(this.manifest);
      this.etag = response.headers['etag'] as string;
      this.lastSync = new Date().toISOString();

      storage.set(`manifest:${this.tenantId}`, JSON.stringify(this.manifest));
      if (this.etag) {
        storage.set(`etag:${this.tenantId}`, this.etag);
      }
    } catch (error: any) {
      // Use cached on network error
      if (!this.manifest) {
        throw error;
      }
    }
  }

  /**
   * Load the compiled manifest for a specific experience (e.g. "home",
   * "bills", "support"). Each experience is its own compiled manifest
   * (`GET /api/v1/config/manifest?experience=X`). Results are cached in
   * memory + MMKV per experience and revalidated with a per-experience ETag.
   */
  async refreshExperience(experience: string): Promise<Manifest | null> {
    const cacheKey = `${this.tenantId}:${experience}`;
    const storageKey = `manifest:${cacheKey}`;
    const etagKey = `etag:${cacheKey}`;

    // Serve cached (MMKV) before any network round-trip.
    if (!this.experienceManifests.has(experience)) {
      const cached = storage.getString(storageKey);
      if (cached) {
        try {
          const parsed = JSON.parse(cached) as Manifest;
          this.experienceManifests.set(experience, parsed);
        } catch {}
      }
    }

    try {
      const headers: Record<string, string> = {
        'X-Tenant-Id': this.tenantId,
      };
      const cachedEtag = this.experienceEtags.get(experience) ?? storage.getString(etagKey);
      if (cachedEtag) {
        headers['If-None-Match'] = cachedEtag;
      }

      const response = await this.client.get<Manifest>('/api/v1/config/manifest', {
        headers,
        params: experience && experience !== 'home' ? { experience } : undefined,
      });
      if (response.status === 304 || !response.data) {
        return this.experienceManifests.get(experience) ?? null;
      }
      const manifest = response.data;
      this.experienceManifests.set(experience, manifest);
      this.experienceEtags.set(experience, response.headers['etag'] as string);
      storage.set(storageKey, JSON.stringify(manifest));
      if (response.headers['etag']) {
        storage.set(etagKey, response.headers['etag'] as string);
      }
      return manifest;
    } catch (error: any) {
      // Use cached on network error
      return this.experienceManifests.get(experience) ?? null;
    }
  }

  async forceRefresh(): Promise<void> {
    this.etag = null;
    await this.refresh();
  }

  getManifest(): Manifest | null {
    return this.manifest;
  }

  getLastSyncTime(): string | null {
    return this.lastSync;
  }

  isOnline(): boolean {
    return !!this.manifest;
  }
}

// ============================================================
// Re-export types and components
// ============================================================
export { ActionEngine } from './ActionEngine';
export type { Manifest, ThemeTokens, Action } from './types';
export { selfcareError } from './errors';