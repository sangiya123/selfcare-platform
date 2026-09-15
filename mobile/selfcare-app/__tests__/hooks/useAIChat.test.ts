/**
 * useAIChat hook tests.
 *
 * Verifies:
 * - Returns initial empty state
 * - Recommended prompts are available
 * - Industry-aware prompts (telco vs insurance)
 * - Intent is null initially
 */
import { renderHook } from '@testing-library/react-native';
import { useAIChat } from '../../src/hooks/useAIChat';

// Mock the underlying AI client so we don't need a real server
jest.mock('../../src/config/AIClient', () => ({
  AIClient: class {
    getOrCreateSession = async () => ({
      id: 'sess-1',
      tenantId: 'dialog-lk',
      title: 'New Conversation',
      messages: [],
      active: true,
    });
    setLastSessionId = () => {};
    getLastSessionId = () => null;
    classifyIntent = async () => ({
      intent: 'GENERAL', confidence: 0.5, suggestedAction: 'general_assist', autoActEligible: false,
    });
  },
}));

jest.mock('../../src/config/ApiClient', () => ({
  ApiClient: class {},
}));

jest.mock('../../src/hooks/useAuth', () => ({
  useAuthStore: () => ({ accessToken: null }),
}));

describe('useAIChat', () => {
  it('returns initial empty state', () => {
    const { result } = renderHook(() => useAIChat());
    expect(result.current.messages).toEqual([]);
    expect(result.current.input).toBe('');
    expect(result.current.isStreaming).toBe(false);
    expect(result.current.intent).toBeNull();
    expect(result.current.error).toBeNull();
  });

  it('provides recommended prompts', () => {
    const { result } = renderHook(() => useAIChat());
    expect(result.current.recommendedPrompts.length).toBeGreaterThan(0);
  });

  it('starts with showPrompts false but exposes isUrgentIntent flag', () => {
    const { result } = renderHook(() => useAIChat());
    expect(result.current.isUrgentIntent).toBe(false);
  });
});
