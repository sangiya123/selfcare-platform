/**
 * BiometricAuth — Biometric / device-credential authentication.
 *
 * Wraps `react-native-biometrics` for Face ID / Touch ID / fingerprint.
 * Used for step-up authentication on sensitive operations:
 *   - Viewing account numbers (full NIC, billing address)
 *   - Payment confirmation
 *   - Account deletion / GDPR data erasure
 *   - Changing security settings (PIN, password, recovery contact)
 *
 * Graceful degradation: when biometrics are unavailable or the user
 * cancels, falls back to device passcode (keyguard).
 *
 * Per-platform: on Android we use BIOMETRIC_STRONG; on iOS we use
 * Face ID / Touch ID depending on hardware.
 */

import { Alert, Platform } from 'react-native';
import ReactNativeBiometrics, { BiometryType } from 'react-native-biometrics';

const SERVICE = 'selfcare-biometric';

export type BiometricCapability =
  | 'AVAILABLE'
  | 'NOT_ENROLLED'
  | 'NOT_AVAILABLE'
  | 'HARDWARE_UNAVAILABLE';

export interface BiometricResult {
  success: boolean;
  cancelled?: boolean;
  reason?: string;
  /**
   * True when a stronger challenge was required (e.g. fallback to PIN)
   * but the user authenticated via the device credential.
   */
  deviceCredentialUsed?: boolean;
}

class BiometricAuthImpl {
  private rnBiometrics = new ReactNativeBiometrics({ allowDeviceCredentials: true });

  /**
   * Detect the biometric capability of the current device.
   */
  async getCapability(): Promise<BiometricCapability> {
    try {
      const { available, biometryType } = await this.rnBiometrics.isSensorAvailable();
      if (!available) return 'NOT_AVAILABLE';
      if (!biometryType || biometryType === undefined) return 'NOT_ENROLLED';
      return 'AVAILABLE';
    } catch (_) {
      return 'HARDWARE_UNAVAILABLE';
    }
  }

  /**
   * Identify the biometric type — Face ID, Touch ID, or Fingerprint.
   */
  async getBiometryType(): Promise<BiometryType | null> {
    try {
      const { available, biometryType } = await this.rnBiometrics.isSensorAvailable();
      if (!available) return null;
      return biometryType ?? null;
    } catch (_) {
      return null;
    }
  }

  /**
   * Prompt the user for biometric authentication.
   *
   * @param reason   User-facing reason shown in the system prompt
   * @param fallback Allow device passcode fallback if biometrics fail
   *                 (e.g. finger not recognised, Face ID in a mask)
   */
  async authenticate(
    reason: string,
    options: { fallback?: boolean; cancelButtonTitle?: string } = {},
  ): Promise<BiometricResult> {
    const { fallback = true, cancelButtonTitle = 'Cancel' } = options;
    try {
      const result = await this.rnBiometrics.simplePrompt({
        promptMessage: reason,
        cancelButtonText: cancelButtonTitle,
        fallbackPrompt: fallback ? 'Use device passcode' : undefined,
      });
      if (result.success) {
        return { success: true, deviceCredentialUsed: false };
      }
      const error = (result as { error?: string }).error;
      if (error === 'UserCancel' || error === 'USER_CANCEL') {
        return { success: false, cancelled: true, reason: 'User cancelled' };
      }
      if (error === 'Fallback' || error === 'BIOMETRIC_FALLBACK_TRIGGERED') {
        return { success: false, reason: 'Fallback to passcode triggered' };
      }
      return { success: false, reason: error ?? 'Unknown failure' };
    } catch (e) {
      return { success: false, reason: (e as Error).message };
    }
  }

  /**
   * High-level helper: prompt for biometric, falling back to passcode
   * after a couple of failures. Returns true on success.
   */
  async authenticateOrPasscode(reason: string): Promise<boolean> {
    const result = await this.authenticate(reason, { fallback: true });
    if (result.success) return true;

    if (result.cancelled) {
      return false;
    }

    // Show user-friendly alert
    return new Promise<boolean>((resolve) => {
      Alert.alert(
        'Authentication failed',
        'Please try again or use your device passcode.',
        [
          { text: 'Cancel', style: 'cancel', onPress: () => resolve(false) },
          { text: 'Try again', onPress: () => this.authenticateOrPasscode(reason).then(resolve) },
        ],
      );
    });
  }

  /**
   * Server-validated biometric: generate a key pair, sign a server-supplied
   * challenge, return the signature for backend verification.
   *
   * Used to ensure that a successful biometric is bound to *this* device —
   * preventing replay attacks via MITM.
   */
  async createSignature(challenge: string): Promise<{ signature: string; publicKey: string } | null> {
    try {
      // Generate a fresh key pair (key remains in Secure Enclave / StrongBox)
      const { publicKey } = await this.rnBiometrics.createKeys(SERVICE);
      if (!publicKey) return null;
      const { signature } = await this.rnBiometrics.createSignature({
        promptMessage: 'Confirm with biometrics',
        payload: challenge,
        privateKey: SERVICE,
      });
      if (!signature) return null;
      return { signature, publicKey };
    } catch (e) {
      if (__DEV__) {
        console.warn('[BiometricAuth] createSignature failed', e);
      }
      return null;
    }
  }

  /**
   * Wipe the biometric key — on sign-out or "Forget device".
   */
  async deleteKeys(): Promise<boolean> {
    try {
      const result = await this.rnBiometrics.deleteKeys(SERVICE);
      return result.success;
    } catch (e) {
      if (__DEV__) {
        console.warn('[BiometricAuth] deleteKeys failed', e);
      }
      return false;
    }
  }
}

export const BiometricAuth = new BiometricAuthImpl();
export default BiometricAuth;
