/**
 * OMOBIO Selfcare SDK — React Native
 *
 * Environment: reads from .env (loaded by ./load-env.sh before build).
 * Key env vars:
 *   OMOBIO_ENV          = dev | stg | reg | prod
 *   MOBILE_API_BASE_URL = API gateway URL
 *   MOBILE_TENANT_ID    = default tenant for this app
 */
import { LayoutRenderer } from './LayoutRenderer';
import { ComponentRegistry } from './ComponentRegistry';
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
  public readonly layoutRenderer: LayoutRenderer;
  public readonly componentRegistry: ComponentRegistry;
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
    // Env-driven defaults — single place to change per environment
    this.tenantId = options.tenantId ?? (process.env.MOBILE_TENANT_ID ?? 'dialog-lk');
    this.apiBaseUrl = options.apiBaseUrl ?? (process.env.MOBILE_API_BASE_URL ?? 'http://localhost:8080');
    this.env = options.env ?? ((process.env.MOBILE_ENV as any) ?? 'development');

    this.auth = new AuthSDK(this.tenantId, this.apiBaseUrl);
    this.api = new ApiClient(this.tenantId, this.apiBaseUrl, this.auth);
    this.ai = new AIClient(this.api, this.tenantId);
    this.componentRegistry = new ComponentRegistry();
    this.config = new ConfigSDK(this.tenantId, this.apiBaseUrl);
    this.actionEngine = new ActionEngine(this);
    this.layoutRenderer = new LayoutRenderer({
      registry: this.componentRegistry,
      actionEngine: this.actionEngine,
    });
  }

  async initialize(): Promise<void> {
    if (this.initialized) return;

    // Load manifest (uses ETag + MMKV cache)
    await this.config.refresh();

    // Register default widgets
    this.componentRegistry.registerDefaultWidgets();

    this.initialized = true;
  }

  getManifest(): Manifest | null {
    return this.config.getManifest();
  }

  getTheme(): ThemeTokens | null {
    const manifest = this.getManifest();
    return manifest?.theme ?? null;
  }

  isOnline(): boolean {
    return this.config.isOnline();
  }
}

// ============================================================
// ConfigSDK
// ============================================================

import { MMKV } from 'react-native-mmkv';
import axios, { AxiosInstance } from 'axios';

const storage = new MMKV({ id: 'omobio-config' });

export class ConfigSDK {
  private tenantId: string;
  private baseUrl: string;
  private client: AxiosInstance;
  private manifest: Manifest | null = null;
  private etag: string | null = null;
  private lastSync: string | null = null;

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

      const response = await this.client.get<Manifest>('/config/manifest', { headers });
      this.manifest = response.data;
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
export { LayoutRenderer } from './LayoutRenderer';
export { ComponentRegistry } from './ComponentRegistry';
export { ActionEngine } from './ActionEngine';
export type { Manifest, Layout, Section, Widget, ThemeTokens, Action } from './types';
export { OmobioError } from './errors';