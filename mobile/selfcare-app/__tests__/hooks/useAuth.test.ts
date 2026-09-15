/**
 * useAuth hook tests.
 *
 * Verifies the zustand auth store:
 * - Persists session tokens to MMKV
 * - Updates access token without re-persisting refresh token
 * - Clears session on signOutLocally
 * - Hydrates from MMKV on app start
 * - Treats expired sessions as unauthenticated
 */
import { act, renderHook } from '@testing-library/react-native';
import { useAuthStore } from '../../src/hooks/useAuth';

const mmkv = (global as any).__mmkvStorage as Map<string, string>;

describe('useAuth (zustand store)', () => {
  beforeEach(() => {
    mmkv.clear();
    useAuthStore.setState({
      accessToken: null,
      refreshToken: null,
      accessExpiresAt: null,
      isAuthenticated: false,
    });
  });

  it('starts with no session and not authenticated', () => {
    const { result } = renderHook(() => useAuthStore());
    expect(result.current.accessToken).toBeNull();
    expect(result.current.refreshToken).toBeNull();
    expect(result.current.accessExpiresAt).toBeNull();
    expect(result.current.isAuthenticated).toBe(false);
  });

  it('persistSession stores all three fields, sets authenticated, and persists to MMKV', () => {
    const { result } = renderHook(() => useAuthStore());

    act(() => {
      result.current.persistSession({
        accessToken: 'access-123',
        refreshToken: 'refresh-456',
        expiresIn: 3600,
      });
    });

    expect(result.current.accessToken).toBe('access-123');
    expect(result.current.refreshToken).toBe('refresh-456');
    expect(result.current.accessExpiresAt).toBeGreaterThan(Date.now());
    expect(result.current.isAuthenticated).toBe(true);
    expect(mmkv.get('accessToken')).toBe('access-123');
    expect(mmkv.get('refreshToken')).toBe('refresh-456');
    expect(mmkv.get('accessExpiresAt')).toBe(String(result.current.accessExpiresAt));
  });

  it('updateAccessToken replaces access token without touching refresh token', () => {
    const { result } = renderHook(() => useAuthStore());

    act(() => {
      result.current.persistSession({
        accessToken: 'access-1',
        refreshToken: 'refresh-1',
        expiresIn: 60,
      });
    });

    act(() => {
      result.current.updateAccessToken('access-2', 120);
    });

    expect(result.current.accessToken).toBe('access-2');
    expect(result.current.refreshToken).toBe('refresh-1');
    expect(result.current.accessExpiresAt).toBeGreaterThan(Date.now());
    expect(mmkv.get('accessToken')).toBe('access-2');
    expect(mmkv.get('refreshToken')).toBe('refresh-1');
  });

  it('signOutLocally clears all fields and wipes MMKV', () => {
    const { result } = renderHook(() => useAuthStore());

    act(() => {
      result.current.persistSession({
        accessToken: 'access',
        refreshToken: 'refresh',
        expiresIn: 60,
      });
    });

    act(() => {
      result.current.signOutLocally();
    });

    expect(result.current.accessToken).toBeNull();
    expect(result.current.refreshToken).toBeNull();
    expect(result.current.accessExpiresAt).toBeNull();
    expect(result.current.isAuthenticated).toBe(false);
    expect(mmkv.has('accessToken')).toBe(false);
    expect(mmkv.has('refreshToken')).toBe(false);
    expect(mmkv.has('accessExpiresAt')).toBe(false);
  });

  it('hydrate reads a valid session from MMKV and marks the store authenticated', () => {
    mmkv.set('accessToken', 'stored-access');
    mmkv.set('refreshToken', 'stored-refresh');
    mmkv.set('accessExpiresAt', String(Date.now() + 60_000));

    const { result } = renderHook(() => useAuthStore());

    act(() => {
      result.current.hydrate();
    });

    expect(result.current.accessToken).toBe('stored-access');
    expect(result.current.refreshToken).toBe('stored-refresh');
    expect(result.current.accessExpiresAt).toBeGreaterThan(Date.now());
    expect(result.current.isAuthenticated).toBe(true);
  });

  it('hydrate with no stored tokens leaves the store empty and unauthenticated', () => {
    const { result } = renderHook(() => useAuthStore());

    act(() => {
      result.current.hydrate();
    });

    expect(result.current.accessToken).toBeNull();
    expect(result.current.refreshToken).toBeNull();
    expect(result.current.isAuthenticated).toBe(false);
  });

  it('hydrate treats an expired stored session as unauthenticated', () => {
    mmkv.set('accessToken', 'stale-access');
    mmkv.set('refreshToken', 'stale-refresh');
    mmkv.set('accessExpiresAt', String(Date.now() - 60_000));

    const { result } = renderHook(() => useAuthStore());

    act(() => {
      result.current.hydrate();
    });

    expect(result.current.accessToken).toBe('stale-access');
    expect(result.current.isAuthenticated).toBe(false);
  });
});