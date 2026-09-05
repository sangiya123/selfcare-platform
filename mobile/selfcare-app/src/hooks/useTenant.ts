/**
 * useTenant — tenant context for the Selfcare App.
 *
 * A "tenant" (also called "client") is a business using the OMOBIO platform:
 *   - dialog-lk, hutch-lk, airtel-lk (telco)
 *   - aia-lk, aia-sg, ... (insurance)
 *
 * The tenant determines which industry pack is loaded (telco vs insurance)
 * and therefore which terminology, providers, and widget set apply.
 *
 * Tenant ID is persisted in MMKV so it survives app restarts.
 */
import { create } from 'zustand';

interface TenantState {
  /** e.g. "dialog-lk", "aia-lk" */
  tenantId: string | null;
  /** e.g. "telco", "insurance" */
  industryPack: string | null;
  setTenant: (tenantId: string) => void;
  clear: () => void;
}

/** Resolve industry pack from tenant ID. In production this comes from the
 *  /api/v1/admin/tenants/:id endpoint. For now we infer it. */
function inferIndustryPack(tenantId: string): string | null {
  if (!tenantId) return null;
  if (tenantId.startsWith('aia-')) return 'insurance';
  if (tenantId.startsWith('dialog-') || tenantId.startsWith('hutch-') || tenantId.startsWith('airtel-')) return 'telco';
  return null;
}

export const useTenant = create<TenantState>((set) => ({
  tenantId: null,
  industryPack: null,

  setTenant: (tenantId) => {
    set({
      tenantId,
      industryPack: inferIndustryPack(tenantId),
    });
  },

  clear: () => {
    set({ tenantId: null, industryPack: null });
  },
}));
