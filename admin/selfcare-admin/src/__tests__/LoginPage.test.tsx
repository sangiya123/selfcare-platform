/**
 * LoginPage tests.
 *
 * Verifies the two-step login flow (credentials → TOTP) and that
 * the navigation happens after both steps complete.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { renderWithProviders } from '@/test/renderWithProviders';

// Mock the api so we don't make real network calls
vi.mock('@/lib/api', () => ({
  api: {
    post: vi.fn((url: string) => {
      if (String(url).startsWith('/api/v1/admin/auth/login')) {
        return Promise.resolve({
          userId: 'u1',
          email: 'admin@selfcare.com',
          fullName: 'Platform Admin',
          tenantId: 'dialog-lk',
          role: 'admin',
          mfaPending: true,
        });
      }
      if (String(url) === '/api/v1/admin/mfa/verify') {
        return Promise.resolve({
          userId: 'u1',
          email: 'admin@selfcare.com',
          fullName: 'Platform Admin',
          tenantId: 'dialog-lk',
          role: 'admin',
          accessToken: 'access-token',
          refreshToken: 'refresh-token',
          sessionId: 's1',
        });
      }
      return Promise.resolve({});
    }),
    setToken: vi.fn(),
    clearToken: vi.fn(),
    setActiveTenant: vi.fn(),
  },
}));

// Mock sonner so toast calls don't print to test output
vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

import LoginPage from '@/pages/LoginPage';
import { api } from '@/lib/api';

function renderLogin() {
  return renderWithProviders(
    <MemoryRouter initialEntries={['/login']}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<div>Dashboard Home</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders the credentials step initially', () => {
    renderLogin();
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
  });

  it('shows the TOTP step after submitting valid credentials', async () => {
    renderLogin();
    fireEvent.change(screen.getByLabelText(/email/i), {
      target: { value: 'admin@selfcare.com' },
    });
    fireEvent.change(screen.getByLabelText(/password/i), {
      target: { value: 'Selfcare_Adm1n_T3st_Pa55w0rd!2026' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Continue' }));

    await waitFor(() => {
      expect(screen.getByLabelText(/totp|code/i)).toBeInTheDocument();
    });
  });

  it('rejects TOTP that is not 6 digits', async () => {
    renderLogin();
    // Move to TOTP step
    fireEvent.change(screen.getByLabelText(/email/i), {
      target: { value: 'admin@selfcare.com' },
    });
    fireEvent.change(screen.getByLabelText(/password/i), {
      target: { value: 'Selfcare_Adm1n_T3st_Pa55w0rd!2026' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Continue' }));

    await waitFor(() => {
      expect(screen.getByLabelText(/totp|code/i)).toBeInTheDocument();
    });

    fireEvent.change(screen.getByLabelText(/totp|code/i), { target: { value: '12345' } });
    fireEvent.click(screen.getByRole('button', { name: /verify|sign in|submit/i }));

    // Should still be on the TOTP step
    expect(screen.getByLabelText(/totp|code/i)).toBeInTheDocument();
  });

  it('accepts a valid 6-digit TOTP and navigates away', async () => {
    renderLogin();
    fireEvent.change(screen.getByLabelText(/email/i), {
      target: { value: 'admin@selfcare.com' },
    });
    fireEvent.change(screen.getByLabelText(/password/i), {
      target: { value: 'Selfcare_Adm1n_T3st_Pa55w0rd!2026' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Continue' }));

    await waitFor(() => {
      expect(screen.getByLabelText(/totp|code/i)).toBeInTheDocument();
    });

    fireEvent.change(screen.getByLabelText(/totp|code/i), { target: { value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: /verify|sign in|submit/i }));

    await waitFor(() => {
      expect(screen.getByText(/Dashboard Home/i)).toBeInTheDocument();
    });

    expect((api as any).setToken).toHaveBeenCalled();
  });
});
