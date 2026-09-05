/**
 * AsyncStorage mock for Jest tests.
 * Provides AsyncStorage API mirroring @react-native-async-storage/async-storage.
 */

const storage = new Map<string, string>();

export default {
  setItem: jest.fn(async (key: string, value: string): Promise<void> => {
    storage.set(key, value);
  }),

  getItem: jest.fn(async (key: string): Promise<string | null> => {
    return storage.get(key) ?? null;
  }),

  removeItem: jest.fn(async (key: string): Promise<void> => {
    storage.delete(key);
  }),

  clear: jest.fn(async (): Promise<void> => {
    storage.clear();
  }),

  getAllKeys: jest.fn(async (): Promise<string[]> => {
    return Array.from(storage.keys());
  }),

  multiGet: jest.fn(async (keys: string[]): Promise<[string, string | null][]> => {
    return keys.map(k => [k, storage.get(k) ?? null]);
  }),

  multiSet: jest.fn(async (entries: [string, string][]): Promise<void> => {
    entries.forEach(([k, v]) => storage.set(k, v));
  }),

  multiRemove: jest.fn(async (keys: string[]): Promise<void> => {
    keys.forEach(k => storage.delete(k));
  }),

  getMultiple: jest.fn(async (keys: string[]): Promise<Map<string, string>> => {
    const result = new Map<string, string>();
    keys.forEach(k => {
      const v = storage.get(k);
      if (v) result.set(k, v);
    });
    return result;
  }),
};

// Export storage for test utilities
export { storage };
