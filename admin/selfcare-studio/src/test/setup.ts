/**
 * Vitest global setup for Selfcare Studio.
 *
 * Provides:
 * - Global mocks for external dependencies
 * - Test utilities (mockApi, mockTenant)
 * - Global teardown
 */
import { beforeEach, afterEach, vi } from 'vitest';

// Reset modules between tests to avoid state leakage
beforeEach(() => {
  vi.resetModules();
});

// Mock import.meta.env for all tests
vi.stubEnv('VITE_APP_ENV', 'test');
vi.stubEnv('VITE_API_GATEWAY_URL', 'http://localhost:8080');
vi.stubEnv('VITE_PUBLIC_API_BASE_URL', 'http://localhost:8080');
vi.stubEnv('VITE_ADMIN_PORTAL_URL', 'http://localhost:3000');
vi.stubEnv('VITE_OBSERVABILITY_URL', 'http://localhost:3001');

afterEach(() => vi.unstubAllEnvs());

// Suppress console errors in tests (only for expected error-path tests)
// Uncomment when needed:
// global.console.error = vi.fn();
// global.console.warn = vi.fn();
