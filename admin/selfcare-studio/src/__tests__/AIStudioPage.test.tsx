/**
 * AIStudioPage tests.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import { MemoryRouter } from 'react-router-dom';

// Mock the useActiveTenant hook
vi.mock('@/hooks/useActiveTenant', () => ({
  useActiveTenant: () => ({
    activeTenantId: 'dialog-lk',
    tenants: [{ tenantId: 'dialog-lk', displayName: 'Dialog', industry: 'TELCO' }],
    setActiveTenant: vi.fn(),
  }),
}));

// Mock the API
vi.mock('@/lib/api', () => ({
  api: {
    get: vi.fn().mockImplementation((url: string) => {
      if (url.includes('/config')) {
        return Promise.resolve({
          data: {
            provider: 'anthropic',
            moderationEnabled: true,
            ragEnabled: true,
            toolsEnabled: true,
            usage: {},
          },
        });
      }
      if (url.includes('/tools')) {
        return Promise.resolve({
          data: [
            { name: 'get_balance', description: 'Check balance', permission: 'AUTO' },
            { name: 'pay_bill', description: 'Pay a bill', permission: 'APPROVAL' },
          ],
        });
      }
      if (url.includes('/usage')) {
        return Promise.resolve({
          data: {
            tenantId: 'dialog-lk',
            inputTokensToday: 1000,
            outputTokensToday: 500,
            requestCountToday: 10,
            estimatedCostUsd: 0.01,
          },
        });
      }
      if (url.includes('/prompts/')) {
        return Promise.resolve({ data: { id: 'telco', content: 'You are helpful', isCustomOverride: false } });
      }
      return Promise.resolve({ data: [] });
    }),
    put: vi.fn().mockResolvedValue({ data: {} }),
    post: vi.fn().mockResolvedValue({ data: {} }),
    delete: vi.fn().mockResolvedValue({ data: {} }),
  },
}));

// Mock sonner
vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

import AIStudioPage from '@/pages/AIStudioPage';

function renderPage() {
  return render(
    <MemoryRouter>
      <AIStudioPage />
    </MemoryRouter>
  );
}

describe('AIStudioPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders the AI Studio heading', async () => {
    renderPage();
    expect(screen.getByText('AI Studio')).toBeInTheDocument();
  });

  it('shows the active tenant in the header', async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText(/dialog-lk/i)).toBeInTheDocument();
    });
  });

  it('renders all 6 tabs', async () => {
    renderPage();
    expect(screen.getByText('Overview')).toBeInTheDocument();
    expect(screen.getByText('Model')).toBeInTheDocument();
    expect(screen.getByText('Tools')).toBeInTheDocument();
    expect(screen.getByText('Prompts')).toBeInTheDocument();
    expect(screen.getByText('Knowledge')).toBeInTheDocument();
    expect(screen.getByText('Safety')).toBeInTheDocument();
  });

  it('displays request count and token usage in overview', async () => {
    renderPage();
    await waitFor(() => {
      // The mock returns requestCountToday: 10
      expect(screen.getByText('10')).toBeInTheDocument();
    });
  });

  it('shows the active LLM provider', async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText('Anthropic')).toBeInTheDocument();
    });
  });
});
