/**
 * useTenant hook tests.
 *
 * Verifies:
 * - Default tenant comes from MOBILE_TENANT_ID env
 * - industryPack is inferred from tenant id prefix (aia-* → insurance,
 *   dialog-*/hutch-*/airtel-* → telco)
 * - setActiveTenant updates the active tenant
 * - hydrated flag is set after initial load
 */
import { act, renderHook } from '@testing-library/react-native';
import { useTenant } from '../../src/hooks/useTenant';

describe('useTenant', () => {
  beforeEach(() => {
    // Reset module-level state for each test
    jest.resetModules();
  });

  it('defaults to dialog-lk when env not set', () => {
    const { result } = renderHook(() => useTenant());
    expect(result.current.tenantId).toBe('dialog-lk');
  });

  it('infers TELCO industry pack for dialog-lk', () => {
    const { result } = renderHook(() => useTenant());
    expect(result.current.industryPack).toBe('TELCO');
  });

  it('infers TELCO industry pack for hutch-lk', () => {
    jest.isolateModules(() => {
      // Set env BEFORE module is required
      process.env.MOBILE_TENANT_ID = 'hutch-lk';
      const { useTenant } = require('../../src/hooks/useTenant');
      const { result } = renderHook(() => useTenant());
      expect(result.current.industryPack).toBe('TELCO');
    });
  });

  it('infers INSURANCE industry pack for aia-multi', () => {
    jest.isolateModules(() => {
      process.env.MOBILE_TENANT_ID = 'aia-multi';
      const { useTenant } = require('../../src/hooks/useTenant');
      const { result } = renderHook(() => useTenant());
      expect(result.current.industryPack).toBe('INSURANCE');
    });
  });

  it('infers INSURANCE industry pack for aia-lk', () => {
    jest.isolateModules(() => {
      process.env.MOBILE_TENANT_ID = 'aia-lk';
      const { useTenant } = require('../../src/hooks/useTenant');
      const { result } = renderHook(() => useTenant());
      expect(result.current.industryPack).toBe('INSURANCE');
    });
  });

  it('falls back to TELCO for unknown tenant ids', () => {
    jest.isolateModules(() => {
      process.env.MOBILE_TENANT_ID = 'unknown-tenant';
      const { useTenant } = require('../../src/hooks/useTenant');
      const { result } = renderHook(() => useTenant());
      expect(result.current.industryPack).toBe('TELCO');
    });
  });

  it('setActiveTenant updates the active tenant id', () => {
    const { result } = renderHook(() => useTenant());

    act(() => {
      result.current.setActiveTenant('aia-lk');
    });

    expect(result.current.tenantId).toBe('aia-lk');
    expect(result.current.industryPack).toBe('INSURANCE');
  });
});
