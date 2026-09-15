/**
 * Hook to get/set the active client/tenant for the admin session.
 *
 * A "client" (also called "tenant") is a business that uses the selfcare platform:
 *   - Dialog, Hutch, Airtel (telco clients)
 *   - AIA, Allianz (insurance clients)
 *   - Future travel / banking / etc. clients
 *
 * Clients are managed in MongoDB via /api/v1/admin/tenants.
 * The active client determines which integration configs are shown
 * in the Integration Builder, which feature flags apply, which
 * theme/layout is rendered, etc.
 *
 * The client ID is persisted in localStorage so it survives page reloads.
 */
import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';

const ACTIVE_TENANT_KEY = 'selfcare-active-tenant';

/**
 * Canonical client/tenant model.
 *
 * Generic across all industries — the `industry` field determines
 * which industry pack (telco, insurance, travel, ...) and therefore
 * which terminology/UI/feature set is shown.
 */
export interface Client {
  tenantId: string;
  name?: string;            // Display name ("Dialog Sri Lanka")
  industry?: string;        // TELCO | INSURANCE | TRAVEL | BANKING | ...
  tenantType?: string;      // OPERATOR | INSURER | TRAVEL_COMPANY | BANK | ...
  country?: string;         // ISO 3166-1 alpha-2
  status?: string;
  supportedLobs?: string[];
}

export function useActiveTenant() {
  const [activeTenantId, setActiveTenantIdState] = useState<string>(
    () => localStorage.getItem(ACTIVE_TENANT_KEY) || ''
  );

  const { data: tenants = [] } = useQuery<Client[]>({
    queryKey: ['tenants'],
    queryFn: () => api.tenants.list(),
  });

  // Auto-select first client if none selected
  useEffect(() => {
    if (!activeTenantId && tenants.length > 0) {
      const first = tenants[0];
      setActiveTenantIdState(first.tenantId);
      localStorage.setItem(ACTIVE_TENANT_KEY, first.tenantId);
      api.setActiveTenant(first.tenantId);
    }
  }, [activeTenantId, tenants]);

  const setActiveTenantId = (id: string) => {
    setActiveTenantIdState(id);
    localStorage.setItem(ACTIVE_TENANT_KEY, id);
    api.setActiveTenant(id);
  };

  return {
    activeTenantId,
    setActiveTenantId,
    tenants,
  };
}
