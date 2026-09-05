/**
 * SettingsPage tests.
 *
 * Verifies the settings page renders all the expected sections
 * (profile, notifications, API tokens, workspace, audit log).
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom';
import { MemoryRouter } from 'react-router-dom';

import SettingsPage from '@/pages/SettingsPage';

function renderPage() {
  return render(
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
    expect(screen.getByText(/profile/i)).toBeInTheDocument();
  });

  it('renders the notifications section', () => {
    renderPage();
    expect(screen.getByText(/notification/i)).toBeInTheDocument();
  });

  it('renders the API tokens section', () => {
    renderPage();
    expect(screen.getByText(/api.*token/i)).toBeInTheDocument();
  });

  it('renders the workspace section', () => {
    renderPage();
    expect(screen.getByText(/workspace/i)).toBeInTheDocument();
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
