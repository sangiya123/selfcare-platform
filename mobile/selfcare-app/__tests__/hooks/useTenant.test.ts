/**
 * useTenant hook tests.
 *
 * Verifies:
 * - Default tenant is null until set
 * - industryPack is inferred from tenant id prefix (aia-* → insurance,
 *   dialog-* / hutch-* / airtel-* → telco)
 * - setTenant updates the active tenant
 * - clear resets the store
 */
import { act, renderHook } from '@testing-library/react-native';
import { useTenant } from '../../src/hooks/useTenant';

describe('useTenant', () => {
  beforeEach(() => {
    // Reset module-level store state for each test
    jest.resetModules();
  });

  it('defaults to null before a tenant is set', () => {
    const { result } = renderHook(() => useTenant());
    expect(result.current.tenantId).toBeNull();
    expect(result.current.industryPack).toBeNull();
  });

  it('infers telco industry pack for dialog-lk', () => {
    const { result } = renderHook(() => useTenant());
    act(() => {
      result.current.setTenant('dialog-lk');
    });
    expect(result.current.tenantId).toBe('dialog-lk');
    expect(result.current.industryPack).toBe('telco');
  });

  it('infers telco industry pack for hutch-lk', () => {
    const { result } = renderHook(() => useTenant());
    act(() => {
      result.current.setTenant('hutch-lk');
    });
    expect(result.current.industryPack).toBe('telco');
  });

  it('infers insurance industry pack for aia-lk', () => {
    const { result } = renderHook(() => useTenant());
    act(() => {
      result.current.setTenant('aia-lk');
    });
    expect(result.current.industryPack).toBe('insurance');
  });

  it('returns null industry pack for unknown tenant ids', () => {
    const { result } = renderHook(() => useTenant());
    act(() => {
      result.current.setTenant('unknown-tenant');
    });
    expect(result.current.industryPack).toBeNull();
  });

  it('clear resets the active tenant', () => {
    const { result } = renderHook(() => useTenant());
    act(() => {
      result.current.setTenant('aia-lk');
    });
    act(() => {
      result.current.clear();
    });
    expect(result.current.tenantId).toBeNull();
    expect(result.current.industryPack).toBeNull();
  });
});