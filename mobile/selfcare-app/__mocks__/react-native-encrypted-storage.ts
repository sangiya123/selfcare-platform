/**
 * EncryptedStorage mock for Jest tests.
 * Provides secure storage API mirroring react-native-encrypted-storage.
 */

const secureStorage = new Map<string, string>();

export default {
  setItem: jest.fn(async (key: string, value: string): Promise<void> => {
    secureStorage.set(key, value);
  }),

  getItem: jest.fn(async (key: string): Promise<string | null> => {
    return secureStorage.get(key) ?? null;
  }),

  removeItem: jest.fn(async (key: string): Promise<void> => {
    secureStorage.delete(key);
  }),

  clear: jest.fn(async (): Promise<void> => {
    secureStorage.clear();
  }),

  getAllKeys: jest.fn(async (): Promise<string[]> => {
    return Array.from(secureStorage.keys());
  }),
};

// Export storage for test utilities
export { secureStorage };
