/**
 * Linking mock for Jest tests.
 * Provides Linking API mirroring react-native Linking.
 */

const listeners = new Map<string, Set<Function>>();

export default {
  openURL: jest.fn(async (url: string): Promise<void> => {
    // Simulate successful URL opening
    return Promise.resolve();
  }),

  canOpenURL: jest.fn(async (url: string): Promise<boolean> => {
    // Default: assume all URLs can be opened
    return Promise.resolve(true);
  }),

  getInitialURL: jest.fn(async (): Promise<string | null> => {
    return Promise.resolve(null);
  }),

  addEventListener: jest.fn((event: string, handler: Function) => {
    if (!listeners.has(event)) {
      listeners.set(event, new Set());
    }
    listeners.get(event)!.add(handler);
    return {
      remove: () => {
        listeners.get(event)?.delete(handler);
      },
    };
  }),

  removeEventListener: jest.fn((event: string, handler: Function) => {
    listeners.get(event)?.delete(handler);
  }),

  // Test utilities
  __resetListeners: () => {
    listeners.clear();
  },

  __triggerEvent: (event: string, url: string) => {
    listeners.get(event)?.forEach(handler => handler({ url }));
  },
};
