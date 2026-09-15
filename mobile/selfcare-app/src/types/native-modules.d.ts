/**
 * Ambient declarations for native modules that are imported by v1 security
 * services but are NOT yet added to package.json (v6 backlog — wire the real
 * pods/packages + shipped types when the security step lands).
 *
 * These declarations only describe the exact surface the code uses today so
 * the kernel typechecks clean; they are NOT promises that the native code
 * exists at runtime. Trying to call these on a build without the native
 * dependency will throw at runtime, which is the same behaviour as before.
 */

declare module 'react-native-keychain' {
  export type AccessControl = 'BIOMETRY_CURRENT_SET' | 'DEVICE_PASSCODE';
  export type SecurityLevel = 'SECURE_HARDWARE' | 'SOFTWARE';
  export type Accessible =
    | 'ACCESSIBLE_WHEN_UNLOCKED'
    | 'ACCESSIBLE_AFTER_FIRST_UNLOCK'
    | 'ACCESSIBLE_ALWAYS'
    | 'ACCESSIBLE_WHEN_PASSCODE_SET_THIS_DEVICE_ONLY';

  export interface SetGenericPasswordOptions {
    service: string;
    accessible?: Accessible;
    accessControl?: AccessControl;
    securityLevel?: SecurityLevel;
  }

  export interface GetGenericPasswordOptions {
    service: string;
  }

  export interface UserCredentials {
    username: string;
    password: string;
    service: string;
  }

  export const ACCESSIBLE: Record<'AFTER_FIRST_UNLOCK' | 'WHEN_UNLOCKED' | 'ALWAYS', Accessible>;
  export const ACCESS_CONTROL: Record<'BIOMETRY_CURRENT_SET' | 'DEVICE_PASSCODE', AccessControl>;
  export const SECURITY_LEVEL: Record<'SECURE_HARDWARE' | 'SOFTWARE', SecurityLevel>;
  export type AccessibleType = Accessible;

  export function setGenericPassword(
    username: string,
    password: string,
    options: SetGenericPasswordOptions
  ): Promise<boolean>;
  export function getGenericPassword(
    options: GetGenericPasswordOptions
  ): Promise<UserCredentials | false>;
  export function resetGenericPassword(options: { service: string }): Promise<boolean>;
}

declare module 'react-native-biometrics' {
  export type BiometryType =
    | 'TouchID'
    | 'FaceID'
    | 'Biometrics'
    | 'Face'
    | 'Fingerprint'
    | 'None';

  export interface SensorAvailability {
    available: boolean;
    biometryType: BiometryType | undefined;
  }

  export interface SimplePromptResult {
    success: boolean;
    error?: string;
  }

  export interface CreateSignatureResult {
    signature?: string;
  }

  export interface DeleteKeyResult {
    success: boolean;
  }

  export class ReactNativeBiometrics {
    constructor(options?: { allowDeviceCredentials?: boolean });
    isSensorAvailable(): Promise<SensorAvailability>;
    simplePrompt(options: {
      promptMessage: string;
      cancelButtonText?: string;
      fallbackPrompt?: string;
    }): Promise<SimplePromptResult>;
    createKeys(promptMessage?: string): Promise<{ publicKey?: string | null }>;
    createSignature(options: {
      promptMessage: string;
      payload: string;
      privateKey: string;
    }): Promise<CreateSignatureResult>;
    deleteKeys(promptMessage?: string): Promise<DeleteKeyResult>;
  }

  export default ReactNativeBiometrics;
}

declare module 'react-native-quick-crypto' {
  import type { Buffer } from 'buffer';

  export interface QuickUpdateable {
    update(data: string | Uint8Array): this;
  }

  export interface QuickHash extends QuickUpdateable {
    digest(encoding: 'hex' | 'base64'): string;
  }

  export interface QuickCipher {
    update(data: Uint8Array): Buffer;
    final(): Buffer;
    getAuthTag(): Buffer;
    setAuthTag(tag: Uint8Array): void;
  }

  export function createHash(algorithm: string): QuickHash;
  export function createHmac(algorithm: string, secret: string | Uint8Array): QuickHash;
  export function randomBytes(size: number): Buffer;
  export function hkdfSync(
    digest: string,
    secret: Uint8Array,
    salt: Uint8Array,
    info: Uint8Array,
    keylen: number
  ): Buffer;
  export function createCipheriv(
    algorithm: string,
    key: Uint8Array,
    iv: Uint8Array
  ): QuickCipher;
  export function createDecipheriv(
    algorithm: string,
    key: Uint8Array,
    iv: Uint8Array
  ): QuickCipher;

  const QuickCrypto: {
    createHash: typeof createHash;
    createHmac: typeof createHmac;
    randomBytes: typeof randomBytes;
    hkdfSync: typeof hkdfSync;
    createCipheriv: typeof createCipheriv;
    createDecipheriv: typeof createDecipheriv;
  };
  export default QuickCrypto;
}