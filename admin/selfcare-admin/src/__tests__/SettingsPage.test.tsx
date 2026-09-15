/**
 * SettingsPage tests.
 *
 * Verifies the settings page renders all the expected sections
 * (profile, notifications, API tokens, workspace, audit log).
 */
import { describe, it, expect, vi } from 'vitest';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import { MemoryRouter } from 'react-router-dom';
import { renderWithProviders } from '@/test/renderWithProviders';

import SettingsPage from '@/pages/SettingsPage';

function renderPage() {
  return renderWithProviders(
    <MemoryRouter>
      <SettingsPage />
    </MemoryRouter>
  );
}

describe('SettingsPage', () => {
  it('renders the settings page', () => {
    renderPage();
    expect(screen.getByText(/settings/i)).toBeInTheDocument();
  });

  it('renders the profile section', () => {
    renderPage();
    expect(screen.getByText('Your personal admin account')).toBeInTheDocument();
  });

  it('renders the notifications section', () => {
    renderPage();
    expect(screen.getByText(/notification/i)).toBeInTheDocument();
  });

  it('renders the API tokens section', () => {
    renderPage();
    expect(screen.getByText(/api.*token/i)).toBeInTheDocument();
  });

  it('renders the workspace section', async () => {
    renderPage();
    const tab = screen.getByRole('tab', { name: /workspace/i });
    tab.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, button: 0 }));
    tab.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, button: 0 }));
    tab.dispatchEvent(new MouseEvent('pointerup', { bubbles: true, button: 0 }));
    tab.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, button: 0 }));
    tab.dispatchEvent(new MouseEvent('click', { bubbles: true, button: 0 }));
    await waitFor(() => {
      expect(screen.getByText('Organization-level settings')).toBeInTheDocument();
    });
  });

  it('renders the audit log section', () => {
    renderPage();
    expect(screen.getByText(/audit log/i)).toBeInTheDocument();
  });

  it('does not crash when no settings data is loaded', () => {
    // The page should render its skeleton/default state without errors
    const { container } = renderPage();
    expect(container).toBeInTheDocument();
  });
});
