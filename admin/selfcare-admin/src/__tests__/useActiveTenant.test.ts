/**
 * useActiveTenant hook tests.
 */
import { renderHookWithProviders } from '@/test/renderWithProviders';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

// Mock the api module
vi.mock('@/lib/api', () => ({
  api: {
    tenants: {
      list: vi.fn(() => Promise.resolve([
        { tenantId: 'dialog-lk', name: 'Dialog Axiata', industry: 'TELCO' },
        { tenantId: 'aia-lk', name: 'AIA Insurance', industry: 'INSURANCE' },
      ])),
    },
    setActiveTenant: vi.fn(),
  },
}));

import { useActiveTenant } from '@/hooks/useActiveTenant';

describe('useActiveTenant', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('returns empty tenants on first render', () => {
    // Note: full render requires React Query setup; this is a unit test for the hook's state
    const { result } = renderHookWithProviders(() => useActiveTenant());
    expect(result.current.activeTenantId).toBe('');
  });

  it('uses stored tenant from localStorage', () => {
    localStorage.setItem('selfcare-active-tenant', 'aia-lk');
    const { result } = renderHookWithProviders(() => useActiveTenant());
    expect(result.current.activeTenantId).toBe('aia-lk');
  });
});
