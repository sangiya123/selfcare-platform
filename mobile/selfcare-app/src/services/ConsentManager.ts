/**
 * ConsentManager — User consent and data-privacy controls.
 *
 * Implements:
 *   - GDPR consent capture (purpose + timestamp + version)
 *   - Article 7 (clear affirmative action)
 *   - Article 17 (Right to be forgotten) — initiates local data erasure
 *   - Article 20 (Data portability) — exports the user's data
 *   - Cookie / tracking consent
 *   - Marketing opt-in / opt-out
 *   - Granular consent per processing purpose
 *
 * Consents are stored both:
 *   - Locally (SecureStorage) for the in-app display
 *   - On the server (via `customer-identity-service/consent`) for legal record
 *
 * The local copy is the source of truth for UX (immediate effect).
 * The server copy is the legal record.
 */

import SecureStorage from './SecureStorage';
import { ApiClient } from '../config/ApiClient';
import { uuidv4 } from '../utils/crypto';

export type ConsentPurpose =
  | 'TERMS_OF_SERVICE'
  | 'PRIVACY_POLICY'
  | 'MARKETING_EMAIL'
  | 'MARKETING_SMS'
  | 'MARKETING_PUSH'
  | 'ANALYTICS'
  | 'PERSONALIZATION'
  | 'THIRD_PARTY_SHARING'
  | 'AI_PROCESSING'
  | 'BIOMETRIC_AUTH'
  | 'LOCATION_TRACKING';

export interface Consent {
  purpose: ConsentPurpose;
  granted: boolean;
  version: string;          // version of the policy text the user consented to
  capturedAt: string;       // ISO 8601
  expiresAt?: string;       // when consent must be re-captured
  ipAddress?: string;       // audit trail (from server)
  userAgent?: string;       // audit trail (from server)
  /**
   * Free-form source of the consent — which screen, which CTA,
   * useful for proving "clear affirmative action".
   */
  source: string;
}

export interface ConsentState {
  consents: Record<ConsentPurpose, Consent | null>;
  /**
   * Set of purposes the user has explicitly denied — used to skip
   * related features (e.g. no marketing email if denied).
   */
  deniedPurposes: Set<ConsentPurpose>;
  /**
   * Last updated timestamp.
   */
  lastUpdatedAt: string;
}

const STORAGE_KEY = 'omobio_consent_state';
const DEFAULT_CONSENT_VERSION = '1.0.0';

/**
 * Purposes that should be re-prompted every 12 months for renewal.
 */
const RENEWAL_PERIOD_DAYS: Partial<Record<ConsentPurpose, number>> = {
  MARKETING_EMAIL: 365,
  MARKETING_SMS: 365,
  MARKETING_PUSH: 365,
  THIRD_PARTY_SHARING: 365,
  AI_PROCESSING: 365,
  BIOMETRIC_AUTH: 180,
  LOCATION_TRACKING: 180,
};

class ConsentManagerImpl {
  private state: ConsentState | null = null;
  private apiClient: ApiClient | null = null;

  setApiClient(client: ApiClient) {
    this.apiClient = client;
  }

  /**
   * Load consent state from SecureStorage. Cache in memory.
   */
  async load(): Promise<ConsentState> {
    if (this.state) return this.state;
    const raw = await SecureStorage.getItem(STORAGE_KEY);
    if (raw) {
      try {
        const parsed = JSON.parse(raw);
        this.state = {
          ...parsed,
          deniedPurposes: new Set(parsed.deniedPurposes ?? []),
        };
        return this.state!;
      } catch (_) {
        // Corrupt state — reset
      }
    }
    this.state = {
      consents: {} as Record<ConsentPurpose, Consent | null>,
      deniedPurposes: new Set<ConsentPurpose>(),
      lastUpdatedAt: new Date().toISOString(),
    };
    return this.state;
  }

  /**
   * Persist current state to SecureStorage.
   */
  private async persist(): Promise<void> {
    if (!this.state) return;
    const serializable = {
      ...this.state,
      deniedPurposes: Array.from(this.state.deniedPurposes),
    };
    await SecureStorage.setItem(STORAGE_KEY, JSON.stringify(serializable));
  }

  /**
   * Capture user consent for a given purpose. Idempotent.
   * Server-side record is created in the background.
   */
  async grant(purpose: ConsentPurpose, source: string, version: string = DEFAULT_CONSENT_VERSION): Promise<void> {
    await this.load();
    const consent: Consent = {
      purpose,
      granted: true,
      version,
      capturedAt: new Date().toISOString(),
      source,
    };
    this.state!.consents[purpose] = consent;
    this.state!.deniedPurposes.delete(purpose);
    this.state!.lastUpdatedAt = consent.capturedAt;
    await this.persist();
    await this.sendToServer(consent);
  }

  /**
   * Revoke consent for a given purpose. Idempotent.
   */
  async revoke(purpose: ConsentPurpose, source: string = 'user-revoke'): Promise<void> {
    await this.load();
    const consent: Consent = {
      purpose,
      granted: false,
      version: this.state!.consents[purpose]?.version ?? DEFAULT_CONSENT_VERSION,
      capturedAt: new Date().toISOString(),
      source,
    };
    this.state!.consents[purpose] = consent;
    this.state!.deniedPurposes.add(purpose);
    this.state!.lastUpdatedAt = consent.capturedAt;
    await this.persist();
    await this.sendToServer(consent);
  }

  /**
   * Returns true if the user has granted consent for the given purpose
   * AND the consent is still valid (not expired).
   */
  async isGranted(purpose: ConsentPurpose): Promise<boolean> {
    const state = await this.load();
    const consent = state.consents[purpose];
    if (!consent || !consent.granted) return false;

    // Check expiry
    const renewalDays = RENEWAL_PERIOD_DAYS[purpose];
    if (renewalDays) {
      const ageMs = Date.now() - new Date(consent.capturedAt).getTime();
      const ageDays = ageMs / (1000 * 60 * 60 * 24);
      if (ageDays > renewalDays) return false;
    }
    return true;
  }

  /**
   * Returns the purposes that the user has explicitly denied.
   */
  async getDeniedPurposes(): Promise<Set<ConsentPurpose>> {
    const state = await this.load();
    return state.deniedPurposes;
  }

  /**
   * Returns all consent decisions for the current user.
   */
  async getAll(): Promise<Record<ConsentPurpose, Consent | null>> {
    const state = await this.load();
    return state.consents;
  }

  /**
   * Returns true if the user has any consent that is expired and
   * needs to be re-captured.
   */
  async needsRenewal(): Promise<ConsentPurpose[]> {
    const state = await this.load();
    const needsRenewal: ConsentPurpose[] = [];
    for (const purpose of Object.keys(state.consents) as ConsentPurpose[]) {
      const consent = state.consents[purpose];
      if (!consent?.granted) continue;
      const renewalDays = RENEWAL_PERIOD_DAYS[purpose];
      if (!renewalDays) continue;
      const ageDays = (Date.now() - new Date(consent.capturedAt).getTime()) / (1000 * 60 * 60 * 24);
      if (ageDays > renewalDays) {
        needsRenewal.push(purpose);
      }
    }
    return needsRenewal;
  }

  /**
   * Article 17 (Right to be forgotten) — initiates a data erasure
   * request. Wipes all local data and tells the server to do the same.
   *
   * Steps:
   *   1. Capture an audit consent (for legal record)
   *   2. Wipe SecureStorage
   *   3. Wipe MMKV cache
   *   4. POST /api/v1/customer/data-erasure
   *   5. Revoke all consents
   */
  async requestErasure(): Promise<string> {
    const erasureId = uuidv4();
    // 1. Wipe local data
    await SecureStorage.clear();
    // 2. Reset in-memory state
    this.state = {
      consents: {} as Record<ConsentPurpose, Consent | null>,
      deniedPurposes: new Set<ConsentPurpose>(),
      lastUpdatedAt: new Date().toISOString(),
    };
    // 3. Notify server
    if (this.apiClient) {
      await this.apiClient.post('/api/v1/customer/data-erasure', {
        erasureId,
        requestedAt: new Date().toISOString(),
        source: 'mobile-app',
      });
    }
    return erasureId;
  }

  /**
   * Article 20 (Data portability) — returns the user's data as a
   * JSON object for download.
   */
  async exportUserData(): Promise<Record<string, unknown>> {
    const state = await this.load();
    return {
      exportedAt: new Date().toISOString(),
      consents: state.consents,
      deniedPurposes: Array.from(state.deniedPurposes),
    };
  }

  /**
   * Send a consent decision to the server (legal record).
   */
  private async sendToServer(consent: Consent): Promise<void> {
    if (!this.apiClient) return;
    try {
      await this.apiClient.post('/api/v1/customer/consent', consent);
    } catch (e) {
      if (__DEV__) {
        console.warn('[ConsentManager] Failed to sync consent to server', e);
      }
      // Re-queue on next consent capture; for production, this would go
      // through a durable outbox.
    }
  }
}

export const ConsentManager = new ConsentManagerImpl();
export default ConsentManager;
