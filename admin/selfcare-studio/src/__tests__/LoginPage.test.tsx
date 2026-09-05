/**
 * LoginPage tests.
 *
 * Verifies the two-step login flow (credentials → TOTP) and that
 * the navigation happens after both steps complete.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import { MemoryRouter, Routes, Route } from 'react-router-dom';

// Mock the api so we don't make real network calls
vi.mock('@/lib/api', () => ({
  api: {
    post: vi.fn().mockResolvedValue({}),
    setToken: vi.fn(),
    clearToken: vi.fn(),
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
  return render(
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
      target: { value: 'admin@omobio.com' },
    });
    fireEvent.change(screen.getByLabelText(/password/i), {
      target: { value: 'password123' },
    });
    fireEvent.click(screen.getByRole('button', { name: /sign in|continue|next/i }));

    await waitFor(() => {
      expect(screen.getByLabelText(/totp|code/i)).toBeInTheDocument();
    });
  });

  it('rejects TOTP that is not 6 digits', async () => {
    renderLogin();
    // Move to TOTP step
    fireEvent.change(screen.getByLabelText(/email/i), {
      target: { value: 'admin@omobio.com' },
    });
    fireEvent.change(screen.getByLabelText(/password/i), {
      target: { value: 'password123' },
    });
    fireEvent.click(screen.getByRole('button', { name: /sign in|continue|next/i }));

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
      target: { value: 'admin@omobio.com' },
    });
    fireEvent.change(screen.getByLabelText(/password/i), {
      target: { value: 'password123' },
    });
    fireEvent.click(screen.getByRole('button', { name: /sign in|continue|next/i }));

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
