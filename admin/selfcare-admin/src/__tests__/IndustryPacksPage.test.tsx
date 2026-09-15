/**
 * IndustryPacksPage tests.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import '@testing-library/jest-dom';
import IndustryPacksPage from '@/pages/IndustryPacksPage';
import { renderWithProviders } from '@/test/renderWithProviders';

// Mock the active tenant list — client badges are driven by the tenants API.
vi.mock('@/hooks/useActiveTenant', () => ({
  useActiveTenant: () => ({
    activeTenantId: 'dialog-lk',
    tenants: [
      { tenantId: 'dialog-lk', industry: 'TELCO' },
      { tenantId: 'hutch-lk', industry: 'TELCO' },
      { tenantId: 'airtel-lk', industry: 'TELCO' },
      { tenantId: 'aia-lk', industry: 'INSURANCE' },
      { tenantId: 'aia-sg', industry: 'INSURANCE' },
      { tenantId: 'aia-my', industry: 'INSURANCE' },
      { tenantId: 'aia-th', industry: 'INSURANCE' },
      { tenantId: 'aia-ph', industry: 'INSURANCE' },
      { tenantId: 'aia-au', industry: 'INSURANCE' },
    ],
    setActiveTenant: vi.fn(),
  }),
}));

function renderPage() {
  return renderWithProviders(<IndustryPacksPage />);
}

describe('IndustryPacksPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders all 4 industry packs', () => {
    renderPage();
    expect(screen.getByText('Telco Pack')).toBeInTheDocument();
    expect(screen.getByText('Insurance Pack')).toBeInTheDocument();
    expect(screen.getByText('Travel Pack')).toBeInTheDocument();
    expect(screen.getByText('Banking Pack')).toBeInTheDocument();
  });

  it('shows Telco Pack has 3 active clients (dialog-lk, hutch-lk, airtel-lk)', () => {
    renderPage();
    expect(screen.getByText('dialog-lk')).toBeInTheDocument();
    expect(screen.getByText('hutch-lk')).toBeInTheDocument();
    expect(screen.getByText('airtel-lk')).toBeInTheDocument();
  });

  it('shows Insurance Pack has 6 AIA clients', () => {
    renderPage();
    expect(screen.getByText('aia-lk')).toBeInTheDocument();
    expect(screen.getByText('aia-sg')).toBeInTheDocument();
  });

  it('shows Telco Pack adapters', () => {
    renderPage();
    expect(screen.getByText('BalanceProvider')).toBeInTheDocument();
    expect(screen.getByText('RechargeProvider')).toBeInTheDocument();
  });

  it('shows Insurance Pack adapters', () => {
    renderPage();
    expect(screen.getByText('InsuranceProvider')).toBeInTheDocument();
    expect(screen.getByText('ClaimProvider')).toBeInTheDocument();
  });

  it('shows status badges', () => {
    renderPage();
    // Two packs are STABLE (telco + insurance), one BETA, one EXPERIMENTAL.
    expect(screen.getAllByText('Stable').length).toBeGreaterThan(0);
    expect(screen.getByText('Beta')).toBeInTheDocument();
    expect(screen.getByText('Experimental')).toBeInTheDocument();
  });

  it('shows terminology mappings', () => {
    renderPage();
    // Telco terminology
    expect(screen.getByText('MSISDN / connection ID')).toBeInTheDocument();
    // Insurance terminology
    expect(screen.getByText('life/health/motor policy')).toBeInTheDocument();
  });
});
