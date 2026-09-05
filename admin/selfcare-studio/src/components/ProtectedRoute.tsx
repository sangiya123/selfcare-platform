/**
 * ProtectedRoute — gates authenticated pages.
 *
 * If no admin token in localStorage, redirects to /login.
 * (In production this would also verify the token's expiry and
 * refresh via the admin-identity-service.)
 */
import { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { api } from '@/lib/api';

export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const location = useLocation();
  const token = api.getToken();

  if (!token) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return <>{children}</>;
}
