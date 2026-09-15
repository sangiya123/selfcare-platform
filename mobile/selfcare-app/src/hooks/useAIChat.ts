/**
 * useAIChat — React hook for the AI chat experience.
 *
 * Manages:
 * - Active session (auto-resumes last session)
 * - Message list (optimistic updates)
 * - Streaming text rendering
 * - Quick actions based on intent classification
 *
 * Usage:
 *   const { messages, send, isStreaming, intent } = useAIChat();
 *
 *   await send("What's my balance?");
 *   → intent is classified → optimistic message added → stream consumed
 *   → final AI response appended with streaming text
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { AIClient, ChatMessage, Intent, ChatSession } from '../config/AIClient';
import { ApiClient } from '../config/ApiClient';
import { useAuthStore } from './useAuth';
import { useTenant } from './useTenant';

const RECOMMENDED_PROMPTS = [
  'What is my current balance?',
  'How do I recharge?',
  'Show me available data plans',
  'Pay my latest bill',
  'I have an issue with my account',
  'What are my most used services?',
];

const URGENT_INTENTS = ['COMPLAINT', 'BILL_INQUIRY'];

export function useAIChat() {
  const { tenantId } = useTenant();
  const auth = useAuthStore();
  const [client, setClient] = useState<AIClient | null>(null);
  const [session, setSession] = useState<ChatSession | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [isStreaming, setIsStreaming] = useState(false);
  const [input, setInput] = useState('');
  const [intent, setIntent] = useState<Intent | null>(null);
  const [error, setError] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  // Initialize AI client
  useEffect(() => {
    const api = new ApiClient({
      tenantId: tenantId ?? 'unknown',
      baseUrl: (typeof process !== 'undefined' &&
        (process as any)?.env?.MOBILE_API_BASE_URL) ?? 'http://localhost:8080',
      getAccessToken: () => auth.accessToken,
    });
    const ai = new AIClient(api, tenantId ?? 'unknown');
    setClient(ai);
  }, [tenantId, auth.accessToken]);

  // Initialize or resume a session
  useEffect(() => {
    if (!client) return;

    (async () => {
      try {
        const lastId = client.getLastSessionId();
        const session = await client.getOrCreateSession(lastId ?? undefined);
        setSession(session);
        client.setLastSessionId(session.id);
        if (session.messages) {
          setMessages(
            session.messages.map(m => ({
              role: m.role as any,
              content: m.content,
              timestamp: new Date(m.timestamp).getTime(),
            }))
          );
        }
      } catch (e: any) {
        setError(e.message);
      }
    })();
  }, [client]);

  // Auto-classify intent on user messages
  useEffect(() => {
    if (!client || messages.length === 0) return;
    const last = messages[messages.length - 1];
    if (last.role !== 'user' || last.streaming) return;

    client
      .classifyIntent(last.content)
      .then(setIntent)
      .catch(() => setIntent(null));
  }, [messages, client]);

  /**
   * Send a user message. Streams the response if streaming is supported.
   */
  const send = useCallback(
    async (text: string) => {
      if (!client || !text.trim() || isStreaming) return;

      const userMessage: ChatMessage = {
        role: 'user',
        content: text.trim(),
        timestamp: Date.now(),
      };

      // Add user message optimistically
      setMessages(prev => [...prev, userMessage]);
      setInput('');
      setError(null);

      // Add a placeholder for the assistant's response
      const assistantMessage: ChatMessage = {
        role: 'assistant',
        content: '',
        timestamp: Date.now(),
        streaming: true,
      };
      setMessages(prev => [...prev, assistantMessage]);
      setIsStreaming(true);

      try {
        // Try streaming first
        const streamId = await client.startStream({
          messages: [...messages, userMessage],
          sessionId: session?.id,
        });

        const baseUrl = (typeof process !== 'undefined' &&
          (process as any)?.env?.MOBILE_API_BASE_URL) ?? 'http://localhost:8080';
        const fullContent = await consumeStreamToText(
          client,
          streamId,
          baseUrl,
          auth.accessToken,
          tenantId ?? 'unknown',
          (chunk) => {
            setMessages(prev => {
              const next = [...prev];
              const lastIdx = next.length - 1;
              if (next[lastIdx]?.role === 'assistant') {
                next[lastIdx] = {
                  ...next[lastIdx],
                  content: next[lastIdx].content + chunk,
                };
              }
              return next;
            });
          }
        );

        // Mark as done
        setMessages(prev => {
          const next = [...prev];
          const lastIdx = next.length - 1;
          if (next[lastIdx]?.role === 'assistant') {
            next[lastIdx] = { ...next[lastIdx], streaming: false, content: fullContent };
          }
          return next;
        });
      } catch (err: any) {
        // Fall back to non-streaming
        try {
          const response = await client.chat({
            messages: [...messages, userMessage],
            sessionId: session?.id,
          });
          setMessages(prev => {
            const next = [...prev];
            const lastIdx = next.length - 1;
            if (next[lastIdx]?.role === 'assistant') {
              next[lastIdx] = { ...next[lastIdx], streaming: false, content: response.content };
            }
            return next;
          });
        } catch (fallbackErr: any) {
          setError(fallbackErr.message);
          setMessages(prev => {
            const next = [...prev];
            const lastIdx = next.length - 1;
            if (next[lastIdx]?.role === 'assistant') {
              next[lastIdx] = {
                ...next[lastIdx],
                streaming: false,
                content: "I'm having trouble responding right now. Please try again.",
              };
            }
            return next;
          });
        }
      } finally {
        setIsStreaming(false);
      }
    },
    [client, messages, session, isStreaming, auth.accessToken, tenantId]
  );

  /**
   * Start a new conversation (creates a fresh session).
   */
  const newConversation = useCallback(async () => {
    if (!client) return;
    try {
      const newSession = await client.getOrCreateSession();
      setSession(newSession);
      client.setLastSessionId(newSession.id);
      setMessages([]);
      setIntent(null);
    } catch (e: any) {
      setError(e.message);
    }
  }, [client]);

  /**
   * Stop the current stream.
   */
  const stop = useCallback(() => {
    abortRef.current?.abort();
    setIsStreaming(false);
  }, []);

  return {
    messages,
    input,
    setInput,
    send,
    isStreaming,
    stop,
    intent,
    error,
    session,
    newConversation,
    recommendedPrompts: RECOMMENDED_PROMPTS,
    isUrgentIntent: intent ? URGENT_INTENTS.includes(intent.intent) : false,
  };
}

/**
 * Consume a stream to completion and return the full text.
 */
async function consumeStreamToText(
  client: AIClient,
  streamId: string,
  baseUrl: string,
  accessToken: string | null,
  _tenantId: string,
  onChunk: (chunk: string) => void
): Promise<string> {
  let fullText = '';

  for await (const event of client.consumeStream(streamId, baseUrl, accessToken)) {
    if (event.type === 'chunk') {
      fullText += (event as any).content ?? '';
      onChunk((event as any).content ?? '');
    } else if (event.type === 'done') {
      return (event as any).content ?? fullText;
    } else if (event.type === 'error') {
      throw new Error((event as any).error ?? 'Stream error');
    }
  }

  return fullText;
}
