/**
 * IndustryPacksPage tests.
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom';
import IndustryPacksPage from '@/pages/IndustryPacksPage';

describe('IndustryPacksPage', () => {
  it('renders all 4 industry packs', () => {
    render(<IndustryPacksPage />);
    expect(screen.getByText('Telco Pack')).toBeInTheDocument();
    expect(screen.getByText('Insurance Pack')).toBeInTheDocument();
    expect(screen.getByText('Travel Pack')).toBeInTheDocument();
    expect(screen.getByText('Banking Pack')).toBeInTheDocument();
  });

  it('shows Telco Pack has 3 active clients (dialog-lk, hutch-lk, airtel-lk)', () => {
    render(<IndustryPacksPage />);
    expect(screen.getByText('dialog-lk')).toBeInTheDocument();
    expect(screen.getByText('hutch-lk')).toBeInTheDocument();
    expect(screen.getByText('airtel-lk')).toBeInTheDocument();
  });

  it('shows Insurance Pack has 6 AIA clients', () => {
    render(<IndustryPacksPage />);
    expect(screen.getByText('aia-lk')).toBeInTheDocument();
    expect(screen.getByText('aia-sg')).toBeInTheDocument();
  });

  it('shows Telco Pack adapters', () => {
    render(<IndustryPacksPage />);
    expect(screen.getByText('BalanceProvider')).toBeInTheDocument();
    expect(screen.getByText('RechargeProvider')).toBeInTheDocument();
  });

  it('shows Insurance Pack adapters', () => {
    render(<IndustryPacksPage />);
    expect(screen.getByText('InsuranceProvider')).toBeInTheDocument();
    expect(screen.getByText('ClaimProvider')).toBeInTheDocument();
  });

  it('shows status badges', () => {
    render(<IndustryPacksPage />);
    expect(screen.getByText('Stable')).toBeInTheDocument();
    expect(screen.getByText('Beta')).toBeInTheDocument();
    expect(screen.getByText('Experimental')).toBeInTheDocument();
  });

  it('shows terminology mappings', () => {
    render(<IndustryPacksPage />);
    // Telco terminology
    expect(screen.getByText('MSISDN / connection ID')).toBeInTheDocument();
    // Insurance terminology
    expect(screen.getByText('life/health/motor policy')).toBeInTheDocument();
  });
});
