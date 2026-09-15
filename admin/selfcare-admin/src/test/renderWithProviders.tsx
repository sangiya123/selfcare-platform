/**
 * Test render helpers for selfcare Studio.
 *
 * Pages use TanStack Query hooks (useQuery/useMutation), so test renders must
 * be wrapped in a QueryClientProvider. `retry:false` keeps failing queries from
 * hanging the suite.
 */
import { render, renderHook } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { type ReactElement } from 'react';

export function createTestQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
}

export function renderWithProviders(ui: ReactElement) {
  const queryClient = createTestQueryClient();
  return {
    queryClient,
    ...render(
      <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
    ),
  };
}

export function renderHookWithProviders<Result, Props = void>(
  hook: (initialProps: Props) => Result
) {
  const queryClient = createTestQueryClient();
  return renderHook(hook, {
    wrapper: ({ children }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    ),
  });
}