/**
 * BrandingPage tests.
 *
 * Verifies the brand editor renders the expected controls and that
 * color tokens are bound to form state.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';
import { MemoryRouter } from 'react-router-dom';

// Mock the active-tenant hook so the page renders without a real provider
vi.mock('@/hooks/useActiveTenant', () => ({
  useActiveTenant: () => ({
    activeTenantId: 'dialog-lk',
    tenants: [
      { tenantId: 'dialog-lk', displayName: 'Dialog', industry: 'TELCO' },
    ],
    setActiveTenant: vi.fn(),
  }),
}));

import BrandingPage from '@/pages/BrandingPage';

function renderPage() {
  return render(
    <MemoryRouter>
      <BrandingPage />
    </MemoryRouter>
  );
}

describe('BrandingPage', () => {
  it('renders the brand editor heading', () => {
    renderPage();
    expect(screen.getByText(/brand/i)).toBeInTheDocument();
  });

  it('shows a primary color input', () => {
    renderPage();
    const colorInputs = screen.getAllByDisplayValue(/#/);
    expect(colorInputs.length).toBeGreaterThan(0);
  });

  it('shows the active tenant in the header', () => {
    renderPage();
    // The mocked hook returns Dialog as the active tenant
    expect(screen.getByText(/dialog/i)).toBeInTheDocument();
  });

  it('renders a save button', () => {
    renderPage();
    const buttons = screen.getAllByRole('button');
    expect(buttons.length).toBeGreaterThan(0);
  });

  it('renders a font family field with default value', () => {
    renderPage();
    // The default font is Inter per the page's DEFAULT_BRAND constant
    expect(screen.getByDisplayValue('Inter')).toBeInTheDocument();
  });

  it('updates the color when the primary color input changes', () => {
    renderPage();
    const primaryInput = screen.getByDisplayValue('#6D28D9');
    expect(primaryInput).toBeInTheDocument();
    fireEvent.change(primaryInput, { target: { value: '#000000' } });
    expect(primaryInput).toHaveValue('#000000');
  });
});
