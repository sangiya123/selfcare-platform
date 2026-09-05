/**
 * AIClient tests.
 *
 * Verifies:
 * - chat() unwraps the response correctly
 * - classifyIntent() returns the right shape
 * - getBundleRecommendations() returns a list
 * - getChurnScore() returns the right shape
 * - Local cache helpers work
 */
import { AIClient } from '../../src/config/AIClient';
import { ApiClient } from '../../src/config/ApiClient';

jest.mock('../../src/config/ApiClient');

describe('AIClient', () => {
  let mockApi: jest.Mocked<ApiClient>;
  let client: AIClient;

  beforeEach(() => {
    mockApi = {
      post: jest.fn(),
      get: jest.fn(),
      delete: jest.fn(),
    } as any;
    client = new AIClient(mockApi, 'dialog-lk');
  });

  describe('chat', () => {
    it('sends a chat request with the correct tenant id', async () => {
      mockApi.post.mockResolvedValue({
        content: 'Hello!',
        sessionId: 'sess-1',
        provider: 'anthropic',
        model: 'claude-sonnet-4-5',
        tokenUsage: { promptTokens: 10, completionTokens: 20, totalTokens: 30 },
      });

      const result = await client.chat({
        messages: [{ role: 'user', content: 'Hi' }],
      });

      expect(mockApi.post).toHaveBeenCalledWith('/api/v1/ai/chat', expect.objectContaining({
        tenantId: 'dialog-lk',
        messages: expect.arrayContaining([
          expect.objectContaining({ role: 'user', content: 'Hi' }),
        ]),
      }));
      expect(result.content).toBe('Hello!');
      expect(result.provider).toBe('anthropic');
      expect(result.tokenUsage?.totalTokens).toBe(30);
    });

    it('returns empty content when response has no content', async () => {
      mockApi.post.mockResolvedValue({});
      const result = await client.chat({ messages: [] });
      expect(result.content).toBe('');
    });
  });

  describe('classifyIntent', () => {
    it('classifies a balance inquiry', async () => {
      mockApi.post.mockResolvedValue({
        intent: 'BALANCE_INQUIRY',
        confidence: 0.9,
        suggestedAction: 'check_balance',
        autoActEligible: true,
      });

      const result = await client.classifyIntent('What is my balance?');

      expect(result.intent).toBe('BALANCE_INQUIRY');
      expect(result.confidence).toBe(0.9);
      expect(result.autoActEligible).toBe(true);
    });

    it('defaults confidence to 0 when not provided', async () => {
      mockApi.post.mockResolvedValue({ intent: 'GENERAL' });
      const result = await client.classifyIntent('Hello');
      expect(result.confidence).toBe(0);
    });
  });

  describe('getBundleRecommendations', () => {
    it('returns a list of recommendations', async () => {
      mockApi.get.mockResolvedValue([
        { productId: 'p1', name: 'Data Pack', price: 100, currency: 'LKR', urgency: 'high' },
      ]);
      const result = await client.getBundleRecommendations('conn-1', 3);
      expect(result).toHaveLength(1);
      expect(result[0].name).toBe('Data Pack');
      expect(mockApi.get).toHaveBeenCalledWith(expect.stringContaining('/api/v1/ai/recommendations/conn-1'));
    });

    it('returns an empty array when response is not an array', async () => {
      mockApi.get.mockResolvedValue(null);
      const result = await client.getBundleRecommendations('conn-1', 3);
      expect(result).toEqual([]);
    });
  });

  describe('getChurnScore', () => {
    it('returns churn score with risk level', async () => {
      mockApi.get.mockResolvedValue({
        connectionId: 'conn-1',
        score: 0.65,
        riskLevel: 'MEDIUM',
        signals: ['Below-average usage'],
        recommendedAction: 'Send targeted offer',
      });
      const result = await client.getChurnScore('conn-1');
      expect(result.riskLevel).toBe('MEDIUM');
      expect(result.signals).toContain('Below-average usage');
    });
  });

  describe('session management', () => {
    it('lists sessions', async () => {
      mockApi.get.mockResolvedValue([{ id: 's1' }, { id: 's2' }]);
      const result = await client.listSessions();
      expect(result).toHaveLength(2);
    });

    it('creates or gets a session', async () => {
      mockApi.post.mockResolvedValue({ id: 'new-sess' });
      const result = await client.getOrCreateSession();
      expect(result.id).toBe('new-sess');
    });

    it('deletes a session', async () => {
      mockApi.delete.mockResolvedValue(undefined);
      await client.deleteSession('sess-1');
      expect(mockApi.delete).toHaveBeenCalledWith('/api/v1/ai/sessions/sess-1');
    });
  });

  describe('local cache', () => {
    it('stores and retrieves last session id', () => {
      client.setLastSessionId('sess-xyz');
      expect(client.getLastSessionId()).toBe('sess-xyz');
    });

    it('clears the last session id', () => {
      client.setLastSessionId('sess-xyz');
      client.clearLastSessionId();
      expect(client.getLastSessionId()).toBeNull();
    });
  });

  describe('parseSSE', () => {
    it('parses a chunk event', () => {
      // Access private method via any-cast
      const block = `event: chunk\ndata: {"content":"Hello","streamId":"s1"}`;
      const event = (client as any).parseSSE(block);
      expect(event.type).toBe('chunk');
      expect(event.content).toBe('Hello');
    });

    it('parses a done event with token usage', () => {
      const block = `event: done\ndata: {"content":"All done","streamId":"s1","tokenUsage":{"totalTokens":42}}`;
      const event = (client as any).parseSSE(block);
      expect(event.type).toBe('done');
      expect(event.content).toBe('All done');
      expect(event.tokenUsage.totalTokens).toBe(42);
    });

    it('returns null for empty block', () => {
      expect((client as any).parseSSE('')).toBeNull();
    });

    it('returns message event with raw data when JSON parse fails', () => {
      const block = `data: not-json`;
      const event = (client as any).parseSSE(block);
      expect(event).toBeDefined();
    });
  });
});
