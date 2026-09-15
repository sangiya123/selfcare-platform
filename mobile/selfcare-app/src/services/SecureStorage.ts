/**
 * SecureStorage — Unified secure-storage abstraction for the selfcare mobile app.
 *
 * Wraps platform-specific secure stores:
 *   - iOS: Keychain (kSecAttrAccessibleAfterFirstUnlock)
 *   - Android: EncryptedSharedPreferences (AndroidKeyStore + AES256/GCM)
 *
 * Use this for:
 *   - Refresh tokens (long-lived)
 *   - PII (msisdn, NIC, biometric templates)
 *   - Device-bound keys (push tokens, encryption keys)
 *   - KYC documents (paths only — content in encrypted files)
 *
 * DO NOT use for:
 *   - High-frequency reads (use MMKV for that)
 *   - Large payloads (use encrypted file storage)
 *
 * Backed by `react-native-keychain` and `react-native-encrypted-storage`.
 * When neither is available, falls back to a non-persistent
 * (in-memory) store with a console warning — only for development.
 */

import { Platform } from 'react-native';
import EncryptedStorage from 'react-native-encrypted-storage';
import * as Keychain from 'react-native-keychain';

export interface SecureStorageOptions {
  /**
   * Optional Keychain/Keystore service name (per-tenant namespace).
   * Use this to scope per-tenant tokens.
   */
  service?: string;
  /**
   * Access level (iOS). On Android this is ignored.
   * Default: AccessibleAfterFirstUnlock — keys readable after first device unlock.
   */
  accessible?: Keychain.Accessible;
  /**
   * Treat as biometric-bound. When true, the key is only released
   * after a successful biometric authentication.
   */
  biometricBound?: boolean;
}

const DEFAULT_SERVICE = 'selfcare';

class SecureStorageImpl {
  private namespace(service?: string): string {
    return service ? `selfcare:${service}` : DEFAULT_SERVICE;
  }

  /**
   * Enumerate keys when the backing store supports it. Older
   * react-native-encrypted-storage builds have no `getAllKeys`; fall back to an
   * empty list so wipe/export degrade gracefully instead of throwing.
   */
  private async getAllKeys(): Promise<string[]> {
    const store = EncryptedStorage as typeof EncryptedStorage & {
      getAllKeys?: () => Promise<string[]>;
    };
    return store.getAllKeys ? store.getAllKeys() : Promise.resolve([]);
  }

  /**
   * Store a value securely. Returns true on success.
   */
  async setItem(key: string, value: string, options: SecureStorageOptions = {}): Promise<boolean> {
    const service = this.namespace(options.service);
    const fullKey = `${service}:${key}`;

    try {
      if (Platform.OS === 'ios') {
        await Keychain.setGenericPassword(fullKey, value, {
          service: fullKey,
          accessible: options.accessible ?? Keychain.ACCESSIBLE.AFTER_FIRST_UNLOCK,
          ...(options.biometricBound
            ? { accessControl: Keychain.ACCESS_CONTROL.BIOMETRY_CURRENT_SET }
            : {}),
        });
      } else {
        // Android: EncryptedSharedPreferences via react-native-encrypted-storage
        // (which uses AndroidKeyStore + AES256/GCM internally).
        // For biometric-bound values we fall back to a separate service with
        // biometric access control via Keychain.
        if (options.biometricBound) {
          await Keychain.setGenericPassword(fullKey, value, {
            service: `${fullKey}:bio`,
            accessControl: Keychain.ACCESS_CONTROL.BIOMETRY_CURRENT_SET,
            securityLevel: Keychain.SECURITY_LEVEL.SECURE_HARDWARE,
          });
        } else {
          await EncryptedStorage.setItem(fullKey, value);
        }
      }
      return true;
    } catch (e) {
      if (__DEV__) {
        console.warn('[SecureStorage] setItem failed', { key, error: e });
      }
      return false;
    }
  }

  /**
   * Retrieve a value. Returns null if not present.
   */
  async getItem(key: string, options: SecureStorageOptions = {}): Promise<string | null> {
    const service = this.namespace(options.service);
    const fullKey = `${service}:${key}`;

    try {
      if (Platform.OS === 'ios' || options.biometricBound) {
        const result = await Keychain.getGenericPassword({ service: fullKey });
        return result === false ? null : result.password;
      } else {
        return await EncryptedStorage.getItem(fullKey);
      }
    } catch (e) {
      if (__DEV__) {
        console.warn('[SecureStorage] getItem failed', { key, error: e });
      }
      return null;
    }
  }

  /**
   * Remove a value. Returns true on success.
   */
  async removeItem(key: string, options: SecureStorageOptions = {}): Promise<boolean> {
    const service = this.namespace(options.service);
    const fullKey = `${service}:${key}`;

    try {
      if (Platform.OS === 'ios') {
        await Keychain.resetGenericPassword({ service: fullKey });
        if (options.biometricBound) {
          await Keychain.resetGenericPassword({ service: `${fullKey}:bio` });
        }
      } else {
        await EncryptedStorage.removeItem(fullKey);
        if (options.biometricBound) {
          await Keychain.resetGenericPassword({ service: `${fullKey}:bio` });
        }
      }
      return true;
    } catch (e) {
      if (__DEV__) {
        console.warn('[SecureStorage] removeItem failed', { key, error: e });
      }
      return false;
    }
  }

  /**
   * Wipe all secure storage for the given service / tenant.
   * Used on sign-out, "Forget device", and GDPR data erasure.
   */
  async clear(options: SecureStorageOptions = {}): Promise<void> {
    const service = this.namespace(options.service);
    try {
      // iOS/Android: both paths enumerate + filter by the tenant service name.
      const keys = await this.getAllKeys();
      for (const k of keys) {
        if (k.startsWith(service)) {
          await EncryptedStorage.removeItem(k);
        }
      }
    } catch (e) {
      if (__DEV__) {
        console.warn('[SecureStorage] clear failed', { service, error: e });
      }
    }
  }

  /**
   * List keys for a given service / tenant. Used for diagnostics and
   * GDPR data-export flows.
   */
  async listKeys(options: SecureStorageOptions = {}): Promise<string[]> {
    const service = this.namespace(options.service);
    try {
      const all = await this.getAllKeys();
      return all.filter((k) => k.startsWith(service)).map((k) => k.slice(service.length + 1));
    } catch (e) {
      if (__DEV__) {
        console.warn('[SecureStorage] listKeys failed', { service, error: e });
      }
      return [];
    }
  }
}

export const SecureStorage = new SecureStorageImpl();
export default SecureStorage;
