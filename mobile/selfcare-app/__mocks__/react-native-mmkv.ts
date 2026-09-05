/**
 * MMKV mock for Jest tests.
 * Provides synchronous and async storage methods.
 */

const storage = new Map<string, string>();

export class MMKV {
  private id: string;

  constructor(opts?: { id?: string }) {
    this.id = opts?.id ?? 'default';
  }

  getString(key: string): string | undefined {
    return storage.get(`${this.id}:${key}`);
  }

  set(key: string, value: string | number | boolean): void {
    storage.set(`${this.id}:${key}`, String(value));
  }

  delete(key: string): void {
    storage.delete(`${this.id}:${key}`);
  }

  contains(key: string): boolean {
    return storage.has(`${this.id}:${key}`);
  }

  getAllKeys(): string[] {
    return Array.from(storage.keys()).filter(k => k.startsWith(`${this.id}:`));
  }

  clearAll(): void {
    Array.from(storage.keys())
      .filter(k => k.startsWith(`${this.id}:`))
      .forEach(k => storage.delete(k));
  }

  // Async methods (augmented by AuthSDK)
  getStringAsync(key: string): Promise<string | null> {
    return Promise.resolve(this.getString(key) ?? null);
  }

  setAsync(key: string, value: string): Promise<void> {
    this.set(key, value);
    return Promise.resolve();
  }

  deleteAsync(key: string): Promise<void> {
    this.delete(key);
    return Promise.resolve();
  }
}

// Export storage for test utilities
export { storage };
