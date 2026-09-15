/**
 * useAuth — Authentication state store.
 *
 * Holds:
 * - accessToken / refreshToken
 * - isAuthenticated
 * - userInfo (when available)
 *
 * Persists tokens to MMKV (secure storage). On sign-out, tokens are
 * wiped from both MMKV and the in-memory store.
 */
import { create } from 'zustand';
import { MMKV } from 'react-native-mmkv';

const secureStorage = new MMKV({ id: 'selfcare-auth-secure' });
const ACCESS_KEY = 'accessToken';
const REFRESH_KEY = 'refreshToken';
const EXPIRES_KEY = 'accessExpiresAt';

export interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  accessExpiresAt: number | null;
  isAuthenticated: boolean;

  persistSession: (session: {
    accessToken: string;
    refreshToken: string;
    expiresIn: number;
  }) => void;
  updateAccessToken: (token: string, expiresIn: number) => void;
  signOutLocally: () => void;
  hydrate: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  refreshToken: null,
  accessExpiresAt: null,
  isAuthenticated: false,

  persistSession: ({ accessToken, refreshToken, expiresIn }) => {
    const expiresAt = Date.now() + expiresIn * 1000;
    secureStorage.set(ACCESS_KEY, accessToken);
    secureStorage.set(REFRESH_KEY, refreshToken);
    secureStorage.set(EXPIRES_KEY, String(expiresAt));
    set({ accessToken, refreshToken, accessExpiresAt: expiresAt, isAuthenticated: true });
  },

  updateAccessToken: (accessToken, expiresIn) => {
    const expiresAt = Date.now() + expiresIn * 1000;
    secureStorage.set(ACCESS_KEY, accessToken);
    secureStorage.set(EXPIRES_KEY, String(expiresAt));
    set({ accessToken, accessExpiresAt: expiresAt });
  },

  signOutLocally: () => {
    secureStorage.delete(ACCESS_KEY);
    secureStorage.delete(REFRESH_KEY);
    secureStorage.delete(EXPIRES_KEY);
    set({ accessToken: null, refreshToken: null, accessExpiresAt: null, isAuthenticated: false });
  },

  hydrate: () => {
    const accessToken = secureStorage.getString(ACCESS_KEY) ?? null;
    const refreshToken = secureStorage.getString(REFRESH_KEY) ?? null;
    const expiresAtRaw = secureStorage.getString(EXPIRES_KEY);
    const expiresAt = expiresAtRaw ? Number(expiresAtRaw) : null;
    const valid = accessToken && expiresAt && expiresAt > Date.now();
    set({
      accessToken,
      refreshToken,
      accessExpiresAt: expiresAt,
      isAuthenticated: Boolean(valid),
    });
  },
}));

// Hydrate on module load
useAuthStore.getState().hydrate();
