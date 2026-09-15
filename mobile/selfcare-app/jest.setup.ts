/**
 * Jest Setup — Global mocks and configuration.
 *
 * This file runs before each test file. It sets up:
 * - Global mocks for native modules (MMKV, EncryptedStorage, Linking, etc.)
 * - Global test utilities (fake timers, fetch, etc.)
 * - Test environment defaults
 */

// ============================================================
// Mock fetch / axios (used by SDK classes)
// ============================================================

const mockFetch = jest.fn();
global.fetch = mockFetch;

// Global fetch mock with json() helper
class MockFetchResponse {
  constructor(private data: any, private options: { status?: number; headers?: Record<string, string> } = {}) {}

  get status() { return this.options.status ?? 200; }
  get ok() { return this.status >= 200 && this.status < 300; }
  get headers() {
    const h = new Map<string, string>();
    Object.entries(this.options.headers ?? {}).forEach(([k, v]) => h.set(k, v));
    return { get: (key: string) => h.get(key) };
  }

  async json() { return this.data; }
  async text() { return JSON.stringify(this.data); }
}

global.fetch.mockResolvedValue = (data: any, options?: { status?: number; headers?: Record<string, string> }) =>
  Promise.resolve(new MockFetchResponse(data, options));

// ============================================================
// Mock axios (used by ConfigSDK, AuthSDK, ApiClient)
// ============================================================

jest.mock('axios', () => {
  // Default axios instance returned by create(). Tests override `create`
  // where they need a custom instance (see ApiClient.test.ts).
  const makeInstance = () => ({
    interceptors: {
      request: { use: jest.fn() },
      response: { use: jest.fn() },
    },
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
    patch: jest.fn(),
    delete: jest.fn(),
    request: jest.fn(),
    defaults: {},
    headers: {},
  });

  const mockAxios: Record<string, unknown> = {
    create: jest.fn(makeInstance),
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
    patch: jest.fn(),
    delete: jest.fn(),
    request: jest.fn(),
    defaults: {},
    interceptors: {
      request: { use: jest.fn() },
      response: { use: jest.fn() },
    },
  };

  return {
    __esModule: true,
    default: mockAxios,
  };
});

// ============================================================
// Mock react-native-mmkv
// ============================================================

const mockMmKVStorage = new Map<string, string>();

// NOTE: implemented as a plain class (not jest.fn().mockImplementation) so that
// `resetMocks: true` in jest.config.js cannot wipe the constructor behaviour.
jest.mock('react-native-mmkv', () => {
  return {
    MMKV: class {
      constructor(_opts?: { id?: string }) {}
      getString(key: string) { return mockMmKVStorage.get(key) ?? null; }
      set(key: string, value: string | number | boolean) { mockMmKVStorage.set(key, String(value)); }
      delete(key: string) { mockMmKVStorage.delete(key); }
      contains(key: string) { return mockMmKVStorage.has(key); }
      getAllKeys() { return Array.from(mockMmKVStorage.keys()); }
      clearAll() { mockMmKVStorage.clear(); }
      // Async methods added by AuthSDK augmentation
      getStringAsync(key: string) { return Promise.resolve(mockMmKVStorage.get(key) ?? null); }
      setAsync(key: string, value: string) { mockMmKVStorage.set(key, value); return Promise.resolve(); }
      deleteAsync(key: string) { mockMmKVStorage.delete(key); return Promise.resolve(); }
    },
  };
});

// Export for test cleanup
(global as any).__mmkvStorage = mockMmKVStorage;

// ============================================================
// Mock react-native-encrypted-storage
// ============================================================

const mockEncryptedStorage = new Map<string, string>();

// NOTE: plain functions (not jest.fn) so `resetMocks: true` cannot wipe them.
jest.mock('react-native-encrypted-storage', () => ({
  setItem: (key: string, value: string) => {
    mockEncryptedStorage.set(key, value);
    return Promise.resolve();
  },
  getItem: (key: string) => {
    return Promise.resolve(mockEncryptedStorage.get(key) ?? null);
  },
  removeItem: (key: string) => {
    mockEncryptedStorage.delete(key);
    return Promise.resolve();
  },
  clear: () => {
    mockEncryptedStorage.clear();
    return Promise.resolve();
  },
  getAllKeys: () => Promise.resolve(Array.from(mockEncryptedStorage.keys())),
}));

// react-native-keychain is not installed in this repo; provide a functional
// in-memory stand-in matching SecureStorage's usage (set/get/reset by service).
const mockKeychain = new Map<string, string>();
jest.mock('react-native-keychain', () => ({
  setGenericPassword: (username: string, password: string, opts?: { service?: string }) => {
    mockKeychain.set(opts?.service ?? 'default', password);
    return Promise.resolve(true);
  },
  getGenericPassword: (opts?: { service?: string }) => {
    const password = mockKeychain.get(opts?.service ?? 'default');
    return Promise.resolve(password === undefined ? false : { username: '', password });
  },
  resetGenericPassword: (opts?: { service?: string }) => {
    mockKeychain.delete(opts?.service ?? 'default');
    return Promise.resolve(true);
  },
  ACCESSIBLE: { AFTER_FIRST_UNLOCK: 'AccessibleAfterFirstUnlock' },
  ACCESS_CONTROL: { BIOMETRY_CURRENT_SET: 'BiometryCurrentSet' },
  SECURITY_LEVEL: { SECURE_HARDWARE: 'SECURE_HARDWARE' },
}), { virtual: true });

// react-native-quick-crypto is a native module not available in Jest; back it
// with Node's built-in crypto (same API surface used by src/utils/crypto.ts).
jest.mock('react-native-quick-crypto', () => {
  const nodeCrypto = jest.requireActual('crypto') as typeof import('crypto');
  return {
    __esModule: true,
    default: {
      createHash: (algo: string) => nodeCrypto.createHash(algo),
      createHmac: (algo: string, key: string | Buffer) => nodeCrypto.createHmac(algo, key),
      randomBytes: (size: number) => nodeCrypto.randomBytes(size),
      createCipheriv: (algo: string, key: Buffer, iv: Buffer) =>
        nodeCrypto.createCipheriv(algo, key, iv),
      createDecipheriv: (algo: string, key: Buffer, iv: Buffer) =>
        nodeCrypto.createDecipheriv(algo, key, iv),
      hkdfSync: (algo: string, secret: Uint8Array, salt: Uint8Array, info: Buffer, len: number) =>
        nodeCrypto.hkdfSync(algo, secret, salt, info, len),
    },
  };
}, { virtual: true });

// Export for test cleanup
(global as any).__encryptedStorage = mockEncryptedStorage;

// ============================================================
// Mock @react-native-async-storage/async-storage
// ============================================================

const mockAsyncStorage = new Map<string, string>();

jest.mock('@react-native-async-storage/async-storage', () => ({
  setItem: jest.fn((key: string, value: string) => {
    mockAsyncStorage.set(key, value);
    return Promise.resolve();
  }),
  getItem: jest.fn((key: string) => {
    return Promise.resolve(mockAsyncStorage.get(key) ?? null);
  }),
  removeItem: jest.fn((key: string) => {
    mockAsyncStorage.delete(key);
    return Promise.resolve();
  }),
  clear: jest.fn(() => {
    mockAsyncStorage.clear();
    return Promise.resolve();
  }),
  getAllKeys: jest.fn(() => Promise.resolve(Array.from(mockAsyncStorage.keys()))),
  multiGet: jest.fn((keys: string[]) => Promise.resolve(keys.map((k) => [k, mockAsyncStorage.get(k) ?? null]))),
  multiSet: jest.fn((entries: [string, string][]) => {
    entries.forEach(([k, v]) => mockAsyncStorage.set(k, v));
    return Promise.resolve();
  }),
}));

// Export for test cleanup
(global as any).__asyncStorage = mockAsyncStorage;

// ============================================================
// Mock react-native Linking
// ============================================================

jest.mock('react-native/Libraries/Linking/Linking', () => ({
  openURL: jest.fn((url: string) => Promise.resolve()),
  canOpenURL: jest.fn(() => Promise.resolve(true)),
  getInitialURL: jest.fn(() => Promise.resolve(null)),
  addEventListener: jest.fn(() => ({ remove: jest.fn() })),
  removeEventListener: jest.fn(),
}));

// ============================================================
// Mock @react-native-community/netinfo
// ============================================================

jest.mock('@react-native-community/netinfo', () => ({
  fetch: jest.fn(() => Promise.resolve({ isConnected: true })),
  addEventListener: jest.fn(() => ({ remove: jest.fn() })),
  useNetInfo: jest.fn(() => ({ isConnected: true, isInternetReachable: true })),
}));

// ============================================================
// Mock react-native-device-info
// ============================================================

jest.mock('react-native-device-info', () => ({
  getVersion: jest.fn(() => Promise.resolve('1.0.0')),
  getBuildNumber: jest.fn(() => Promise.resolve('1')),
  getDeviceId: jest.fn(() => Promise.resolve('test-device')),
  getSystemName: jest.fn(() => Promise.resolve('Android')),
  getSystemVersion: jest.fn(() => Promise.resolve('14')),
  isTablet: jest.fn(() => false),
}));

// ============================================================
// Global test utilities
// ============================================================

/** Advance fake timers by ms milliseconds */
export function advanceTimersByTime(ms: number): void {
  jest.advanceTimersByTime(ms);
}

/** Run all pending promises */
export function flushPromises(): Promise<void> {
  return new Promise(resolve => setImmediate(resolve));
}

/** Clear all storage mocks */
export function clearStorage(): void {
  mockMmKVStorage.clear();
  mockEncryptedStorage.clear();
  mockAsyncStorage.clear();
}

/** Reset all mocks */
export function resetAllMocks(): void {
  mockFetch.mockReset();
  jest.clearAllMocks();
}

/** Mock console.error to fail tests on unexpected errors */
const originalError = console.error;
console.error = (...args: any[]) => {
  // Ignore known benign warnings
  const message = args[0]?.toString?.() ?? '';
  if (
    message.includes('Warning: ReactDOM.render') ||
    message.includes('Warning: An update to') ||
    message.includes('act(...)')
  ) {
    return;
  }
  originalError.apply(console, args);
};

// ============================================================
// Test timeout helpers
// ============================================================

export const DEFAULT_TIMEOUT = 5000;

/** Create a promise that rejects after timeout */
export function withTimeout<T>(promise: Promise<T>, timeoutMs: number = DEFAULT_TIMEOUT): Promise<T> {
  return Promise.race([
    promise,
    new Promise<T>((_, reject) =>
      setTimeout(() => reject(new Error(`Timeout after ${timeoutMs}ms`)), timeoutMs)
    ),
  ]);
}

// ============================================================
// TypeScript augmentation for globals
// ============================================================

declare global {
  const flushPromises: () => Promise<void>;
  const clearStorage: () => void;
  const advanceTimersByTime: (ms: number) => void;
}
