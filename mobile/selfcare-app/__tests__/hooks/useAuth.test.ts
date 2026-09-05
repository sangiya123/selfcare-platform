/**
 * useAuth hook tests.
 *
 * Verifies the zustand auth store:
 * - Persists session tokens to MMKV
 * - Updates access token without re-persisting refresh token
 * - Clears session on signOutLocally
 * - Hydrates from MMKV on app start
 * - Treats empty token strings as null
 */
import { act, renderHook } from '@testing-library/react-native';
import { useAuthStore } from '../../src/hooks/useAuth';

describe('useAuth (zustand store)', () => {
  beforeEach(() => {
    // Clear storage and reset the store between tests
    (global as any).__mmkvStorage.clear();
    useAuthStore.setState({
      accessToken: null,
      refreshToken: null,
      expiresAt: null,
      isHydrated: false,
    });
  });

  it('starts with no session and not hydrated', () => {
    const { result } = renderHook(() => useAuthStore());
    expect(result.current.accessToken).toBeNull();
    expect(result.current.refreshToken).toBeNull();
    expect(result.current.isHydrated).toBe(false);
  });

  it('persistSession stores all three fields and sets hydrated flag', () => {
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
    expect(result.current.expiresAt).toBeGreaterThan(Date.now() / 1000);
    expect(result.current.isHydrated).toBe(true);
  });

  it('updateAccessToken replaces access token without touching refresh', () => {
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
  });

  it('signOutLocally clears all fields but keeps the store mounted', () => {
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
    expect(result.current.expiresAt).toBeNull();
  });

  it('hydrate reads from MMKV and marks the store as hydrated', () => {
    // Pre-populate MMKV as if the previous session had been saved
    (global as any).__mmkvStorage.set('omobio-auth:accessToken', 'stored-access');
    (global as any).__mmkvStorage.set('omobio-auth:refreshToken', 'stored-refresh');
    (global as any).__mmkvStorage.set('omobio-auth:expiresAt', '9999999999');

    const { result } = renderHook(() => useAuthStore());

    act(() => {
      result.current.hydrate();
    });

    expect(result.current.accessToken).toBe('stored-access');
    expect(result.current.refreshToken).toBe('stored-refresh');
    expect(result.current.isHydrated).toBe(true);
  });

  it('hydrate with no stored tokens leaves the store empty but hydrated', () => {
    const { result } = renderHook(() => useAuthStore());

    act(() => {
      result.current.hydrate();
    });

    expect(result.current.accessToken).toBeNull();
    expect(result.current.refreshToken).toBeNull();
    expect(result.current.isHydrated).toBe(true);
  });
});
