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
  const mockAxios = jest.fn().mockImplementation(() => ({
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
    patch: jest.fn(),
    delete: jest.fn(),
    create: jest.fn().mockReturnThis(),
  }));
  return {
    __esModule: true,
    default: mockAxios,
  };
});

// ============================================================
// Mock react-native-mmkv
// ============================================================

const mmkvStorage = new Map<string, string>();

jest.mock('react-native-mmkv', () => {
  return {
    MMKV: jest.fn().mockImplementation((_opts) => ({
      getString: (key: string) => mmkvStorage.get(key) ?? null,
      set: (key: string, value: string) => { mmkvStorage.set(key, value); },
      delete: (key: string) => { mmkvStorage.delete(key); },
      contains: (key: string) => mmkvStorage.has(key),
      getAllKeys: () => Array.from(mmkvStorage.keys()),
      clearAll: () => mmkvStorage.clear(),
      // Async methods added by AuthSDK augmentation
      getStringAsync: (key: string) => Promise.resolve(mmkvStorage.get(key) ?? null),
      setAsync: (key: string, value: string) => { mmkvStorage.set(key, value); return Promise.resolve(); },
      deleteAsync: (key: string) => { mmkvStorage.delete(key); return Promise.resolve(); },
    })),
  };
});

// Export for test cleanup
(global as any).__mmkvStorage = mmkvStorage;

// ============================================================
// Mock react-native-encrypted-storage
// ============================================================

const encryptedStorage = new Map<string, string>();

jest.mock('react-native-encrypted-storage', () => ({
  setItem: jest.fn((key: string, value: string) => {
    encryptedStorage.set(key, value);
    return Promise.resolve();
  }),
  getItem: jest.fn((key: string) => {
    return Promise.resolve(encryptedStorage.get(key) ?? null);
  }),
  removeItem: jest.fn((key: string) => {
    encryptedStorage.delete(key);
    return Promise.resolve();
  }),
  clear: jest.fn(() => {
    encryptedStorage.clear();
    return Promise.resolve();
  }),
}));

// Export for test cleanup
(global as any).__encryptedStorage = encryptedStorage;

// ============================================================
// Mock @react-native-async-storage/async-storage
// ============================================================

const asyncStorage = new Map<string, string>();

jest.mock('@react-native-async-storage/async-storage', () => ({
  setItem: jest.fn((key: string, value: string) => {
    asyncStorage.set(key, value);
    return Promise.resolve();
  }),
  getItem: jest.fn((key: string) => {
    return Promise.resolve(asyncStorage.get(key) ?? null);
  }),
  removeItem: jest.fn((key: string) => {
    asyncStorage.delete(key);
    return Promise.resolve();
  }),
  clear: jest.fn(() => {
    asyncStorage.clear();
    return Promise.resolve();
  }),
  getAllKeys: jest.fn(() => Promise.resolve(Array.from(asyncStorage.keys()))),
  multiGet: jest.fn((keys: string[]) => Promise.resolve(keys.map((k) => [k, asyncStorage.get(k) ?? null]))),
  multiSet: jest.fn((entries: [string, string][]) => {
    entries.forEach(([k, v]) => asyncStorage.set(k, v));
    return Promise.resolve();
  }),
}));

// Export for test cleanup
(global as any).__asyncStorage = asyncStorage;

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
// Mock react-native-clipboard
// ============================================================

jest.mock('@react-native-clipboard/clipboard', () => ({
  setString: jest.fn(),
  getString: jest.fn(() => Promise.resolve('')),
  addListener: jest.fn(),
  removeListeners: jest.fn(),
}));

// ============================================================
// Mock react-native-reanimated
// ============================================================

jest.mock('react-native-reanimated', () => {
  const Reanimated = require('react-native-reanimated/mock');
  Reanimated.default.call = () => {};
  return Reanimated;
});

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
  mmkvStorage.clear();
  encryptedStorage.clear();
  asyncStorage.clear();
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
