/**
 * AIClient — client for the AI Gateway.
 *
 * Handles:
 * - Standard chat (POST /api/v1/ai/chat)
 * - Streaming chat (POST /api/v1/ai/chat/stream → SSE consumer)
 * - Intent classification
 * - Bundle recommendations
 * - Churn risk scoring
 * - Session management
 *
 * For streaming, the client uses fetch + ReadableStream to consume Server-Sent
 * Events. The native EventSource API is not available in React Native, so we
 * parse the stream manually.
 */
import { MMKV } from 'react-native-mmkv';
import { ApiClient } from './ApiClient';
import { servicePath } from './serviceEndpoints';

const storage = new MMKV({ id: 'selfcare-ai' });

export type ChatRole = 'user' | 'assistant' | 'system' | 'tool';

export interface ChatMessage {
  role: ChatRole;
  content: string;
  toolCall?: { name: string; arguments: string };
  /** If true, this message is currently being streamed */
  streaming?: boolean;
  /** When the message was created */
  timestamp?: number;
}

export interface ChatRequest {
  messages: ChatMessage[];
  systemPrompt?: string;
  tools?: Array<{ name: string; description: string; inputSchema?: any }>;
  sessionId?: string;
  temperature?: number;
  maxTokens?: number;
}

export interface ChatResponse {
  content: string;
  sessionId?: string;
  provider?: string;
  model?: string;
  toolsUsed?: Array<{ toolName: string; arguments: string; result?: string; success: boolean }>;
  tokenUsage?: { promptTokens: number; completionTokens: number; totalTokens: number };
  latencyMs?: number;
}

export interface Intent {
  intent: string;
  confidence: number;
  suggestedAction: string;
  autoActEligible: boolean;
}

export interface BundleRec {
  productId: string;
  name: string;
  description: string;
  price: number;
  currency: string;
  reason: string;
  confidenceScore: number;
  urgency: 'low' | 'normal' | 'high' | 'urgent';
}

export interface ChurnScore {
  connectionId: string;
  score: number;
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH';
  signals: string[];
  recommendedAction: string;
}

export interface ChatSession {
  id: string;
  tenantId: string;
  userId?: string;
  title: string;
  channel: string;
  createdAt: string;
  lastActivityAt: string;
  active: boolean;
  messages: Array<{
    role: string;
    content: string;
    toolCalls?: any[];
    timestamp: string;
  }>;
}

export class AIClient {
  private api: ApiClient;
  private tenantId: string;

  constructor(api: ApiClient, tenantId: string) {
    this.api = api;
    this.tenantId = tenantId;
  }

  // -------------------------------------------------------------------------
  // Standard chat
  // -------------------------------------------------------------------------

  /**
   * Send a chat request and get a complete response.
   */
  async chat(request: ChatRequest): Promise<ChatResponse> {
    const resp = await this.api.post<any>(servicePath('ai', 'chat', '/api/v1/ai/chat'), {
      ...request,
      tenantId: this.tenantId,
    });
    return {
      content: resp.content ?? '',
      sessionId: resp.sessionId,
      provider: resp.provider,
      model: resp.model,
      toolsUsed: resp.toolsUsed,
      tokenUsage: resp.tokenUsage,
      latencyMs: resp.latencyMs,
    };
  }

  // -------------------------------------------------------------------------
  // Streaming chat
  // -------------------------------------------------------------------------

  /**
   * Start a streaming chat. Returns a streamId that can be used to listen for chunks.
   */
  async startStream(request: ChatRequest): Promise<string> {
    const resp = await this.api.post<{ streamId: string }>(servicePath('ai', 'chatStream', '/api/v1/ai/chat/stream'), {
      ...request,
      tenantId: this.tenantId,
    });
    return resp.streamId;
  }

  /**
   * Consume Server-Sent Events from a stream. Yields parsed events.
   */
  async *consumeStream(
    streamId: string,
    baseUrl: string,
    accessToken: string | null
  ): AsyncGenerator<StreamEvent> {
    const eventsPath = servicePath('ai', 'chatStreamEvents', '/api/v1/ai/chat/stream/{streamId}/events', {
      streamId,
    });
    const url = eventsPath.startsWith('http')
      ? eventsPath
      : `${baseUrl}${eventsPath}`;
    const headers: Record<string, string> = {
      Accept: 'text/event-stream',
      'X-Tenant-Id': this.tenantId,
    };
    if (accessToken) {
      headers.Authorization = `Bearer ${accessToken}`;
    }

    const response = await fetch(url, { method: 'GET', headers });

    if (!response.ok || !response.body) {
      yield { type: 'error', error: `Stream error: ${response.status}` };
      return;
    }

    const reader = (response.body as any).getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });

        // SSE events are separated by \n\n
        const events = buffer.split('\n\n');
        buffer = events.pop() ?? '';

        for (const eventBlock of events) {
          const event = this.parseSSE(eventBlock);
          if (event) yield event;
        }
      }
    } finally {
      try { reader.releaseLock(); } catch {}
    }
  }

  private parseSSE(block: string): StreamEvent | null {
    if (!block.trim()) return null;

    let event = 'message';
    let data = '';

    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) {
        event = line.slice(6).trim();
      } else if (line.startsWith('data:')) {
        data += line.slice(5).trim();
      }
    }

    let payload: any = {};
    if (data) {
      try {
        payload = JSON.parse(data);
      } catch {
        payload = { raw: data };
      }
    }

    return { type: event, ...payload } as StreamEvent;
  }

  // -------------------------------------------------------------------------
  // Intent classification
  // -------------------------------------------------------------------------

  async classifyIntent(message: string): Promise<Intent> {
    const resp = await this.api.post<any>(servicePath('ai', 'classify', '/api/v1/ai/classify'), { message });
    return {
      intent: resp.intent ?? 'GENERAL',
      confidence: resp.confidence ?? 0,
      suggestedAction: resp.suggestedAction ?? 'general_assist',
      autoActEligible: resp.autoActEligible ?? false,
    };
  }

  // -------------------------------------------------------------------------
  // Recommendations
  // -------------------------------------------------------------------------

  async getBundleRecommendations(connectionId: string, limit = 3): Promise<BundleRec[]> {
    const resp = await this.api.get<any[]>(
      servicePath('ai', 'recommendations', '/api/v1/ai/recommendations/{connectionId}?limit={limit}', {
        connectionId,
        limit,
      })
    );
    return (Array.isArray(resp) ? resp : []).map(r => ({
      productId: r.productId ?? '',
      name: r.name ?? '',
      description: r.description ?? '',
      price: r.price ?? 0,
      currency: r.currency ?? 'USD',
      reason: r.reason ?? '',
      confidenceScore: r.confidenceScore ?? 0,
      urgency: r.urgency ?? 'normal',
    }));
  }

  async getChurnScore(connectionId: string): Promise<ChurnScore> {
    const resp = await this.api.get<ChurnScore>(
      servicePath('ai', 'churn', '/api/v1/ai/churn/{connectionId}', { connectionId })
    );
    return {
      connectionId: resp.connectionId ?? connectionId,
      score: resp.score ?? 0,
      riskLevel: resp.riskLevel ?? 'LOW',
      signals: resp.signals ?? [],
      recommendedAction: resp.recommendedAction ?? '',
    };
  }

  // -------------------------------------------------------------------------
  // Session management
  // -------------------------------------------------------------------------

  async listSessions(): Promise<ChatSession[]> {
    const resp = await this.api.get<any[]>(servicePath('ai', 'sessions', '/api/v1/ai/sessions'));
    return Array.isArray(resp) ? resp : [];
  }

  async getOrCreateSession(sessionId?: string): Promise<ChatSession> {
    const resp = await this.api.post<any>(servicePath('ai', 'sessions', '/api/v1/ai/sessions'), { sessionId });
    return resp;
  }

  async getSessionHistory(sessionId: string): Promise<ChatSession> {
    return this.api.get<any>(
      servicePath('ai', 'sessionHistory', '/api/v1/ai/sessions/{sessionId}/history', { sessionId })
    );
  }

  async deleteSession(sessionId: string): Promise<void> {
    await this.api.delete(
      servicePath('ai', 'sessionHistory', '/api/v1/ai/sessions/{sessionId}', { sessionId })
    );
  }

  // -------------------------------------------------------------------------
  // Sentiment analysis
  // -------------------------------------------------------------------------

  /**
   * Analyze sentiment of a single message.
   */
  async analyzeSentiment(text: string): Promise<{
    score: number;
    label: string;
    confidence: number;
    triggers: string[];
  }> {
    const resp = await this.api.post<any>(servicePath('ai', 'sentiment', '/api/v1/ai/sentiment'), { text });
    return {
      score: resp.score ?? 0,
      label: resp.label ?? 'NEUTRAL',
      confidence: resp.confidence ?? 0,
      triggers: resp.triggers ?? [],
    };
  }

  // -------------------------------------------------------------------------
  // Conversation summarization
  // -------------------------------------------------------------------------

  /**
   * Generate a structured summary of a conversation.
   */
  async summarize(messages: ChatMessage[]): Promise<{
    headline: string;
    dominantIntent: string;
    topics: string[];
    sentiment: string;
    resolutionStatus: string;
    suggestedFollowUps: string[];
    userMessageCount: number;
    assistantMessageCount: number;
  }> {
    const resp = await this.api.post<any>(servicePath('ai', 'summarize', '/api/v1/ai/summarize'), {
      messages: messages.map(m => ({ role: m.role, content: m.content })),
    });
    return {
      headline: resp.headline ?? '',
      dominantIntent: resp.dominantIntent ?? 'GENERAL',
      topics: resp.topics ?? [],
      sentiment: resp.sentiment ?? 'NEUTRAL',
      resolutionStatus: resp.resolutionStatus ?? 'PENDING',
      suggestedFollowUps: resp.suggestedFollowUps ?? [],
      userMessageCount: resp.userMessageCount ?? 0,
      assistantMessageCount: resp.assistantMessageCount ?? 0,
    };
  }

  // -------------------------------------------------------------------------
  // Semantic search
  // -------------------------------------------------------------------------

  /**
   * Search the knowledge base for relevant chunks.
   */
  async search(query: string, topK = 5): Promise<{
    query: string;
    results: Array<{ chunkId: string; text: string; similarity: number }>;
    total: number;
  }> {
    const resp = await this.api.get<any>(servicePath('ai', 'search', '/api/v1/ai/search'), { query, topK });
    return {
      query: resp.query ?? query,
      results: (resp.results ?? []).map((r: any) => ({
        chunkId: r.chunkId ?? '',
        text: r.text ?? '',
        similarity: r.similarity ?? 0,
      })),
      total: resp.total ?? 0,
    };
  }

  // -------------------------------------------------------------------------
  // Fallback (no-LLM) chat
  // -------------------------------------------------------------------------

  /**
   * Get a response without using the LLM (used when AI is unavailable).
   */
  async fallbackChat(request: ChatRequest): Promise<ChatResponse> {
    const resp = await this.api.post<any>(servicePath('ai', 'chatFallback', '/api/v1/ai/chat/fallback'), {
      ...request,
      tenantId: this.tenantId,
    });
    return {
      content: resp.content ?? '',
      sessionId: resp.sessionId,
      provider: resp.provider ?? 'fallback',
      model: resp.model,
    };
  }

  // -------------------------------------------------------------------------
  // Health check
  // -------------------------------------------------------------------------

  /**
   * Health check for AI subsystems.
   */
  async health(): Promise<Record<string, any>> {
    return this.api.get<any>(servicePath('ai', 'health', '/api/v1/ai/health'));
  }

  // -------------------------------------------------------------------------
  // Local cache helpers
  // -------------------------------------------------------------------------

  /** Get last session id from local cache. */
  getLastSessionId(): string | null {
    return storage.getString('lastSessionId') ?? null;
  }

  setLastSessionId(id: string) {
    storage.set('lastSessionId', id);
  }

  clearLastSessionId() {
    storage.delete('lastSessionId');
  }
}

// ─────────────────────────────────────────────────────────
// Stream event types
// ─────────────────────────────────────────────────────────

export type StreamEvent =
  | { type: 'chunk'; content: string; streamId: string }
  | { type: 'tool_call'; toolName: string; arguments: string; streamId: string }
  | { type: 'done'; content: string; streamId: string; provider?: string; model?: string; tokenUsage?: any }
  | { type: 'error'; error: string; streamId?: string }
  | { type: 'close'; streamId: string }
  | { type: 'message'; [k: string]: any };
