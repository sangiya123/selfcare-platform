/**
 * ProtectedRoute tests.
 */
import { describe, it, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import '@testing-library/jest-dom';
import ProtectedRoute from '@/components/ProtectedRoute';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/lib/api', () => ({
  api: {
    getToken: vi.fn(() => null),
  },
}));

describe('ProtectedRoute', () => {
  it('redirects unauthenticated user to /login', () => {
    renderWithProviders(
      <MemoryRouter initialEntries={['/protected']}>
        <Routes>
          <Route path="/login" element={<div>Login page</div>} />
          <Route
            path="/protected"
            element={
              <ProtectedRoute>
                <div>Protected content</div>
              </ProtectedRoute>
            }
          />
        </Routes>
      </MemoryRouter>
    );
    expect(screen.getByText('Login page')).toBeInTheDocument();
    expect(screen.queryByText('Protected content')).not.toBeInTheDocument();
  });
});
