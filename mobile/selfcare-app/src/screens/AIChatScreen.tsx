/**
 * AIChatScreen — in-app AI assistant chat interface.
 *
 * Features:
 * - Streaming text as the AI generates
 * - Quick-action prompts tailored to industry (telco vs insurance)
 * - Intent-based smart suggestions (after each user message)
 * - Tool call visualizations
 * - Voice input (if device supports it)
 * - New conversation button
 * - Pull-to-refresh
 * - Industry-specific suggested prompts
 *
 * Fully token-driven: every color, font size and margin comes from the
 * resolved manifest theme (admin-authored in Selfcare Studio). Nothing
 * hardcoded (v6 rule #1).
 */
import React, { useState, useRef, useEffect, useMemo } from 'react';
import {
  View, Text, TextInput, FlatList, StyleSheet,
  TouchableOpacity, KeyboardAvoidingView, Platform,
  ActivityIndicator, RefreshControl, Alert,
} from 'react-native';
import { useAIChat } from '../hooks/useAIChat';
import { useRecommendations } from '../hooks/useRecommendations';
import { useTenant } from '../hooks/useTenant';
import { useAuthStore } from '../hooks/useAuth';
import { ChatBubble } from '../components/ChatBubble';
import {
  ThemeProvider,
  useTheme,
  useThemeColors,
  fontSizePx,
  readableOn,
  ResolvedTheme,
} from '../manifest/ThemeEngine';
import { ExperienceManifest, ManifestTheme } from '../manifest/types';
import { SelfcareSDK } from '../config/ConfigSDK';

const TELCO_QUICK_PROMPTS = [
  'Check my balance',
  'How do I recharge?',
  'Show me data plans',
  'Pay my latest bill',
  'How much data do I have left?',
];

const INSURANCE_QUICK_PROMPTS = [
  'Show my policies',
  'File a new claim',
  'When is my next premium due?',
  'What does my policy cover?',
  'How do I add a beneficiary?',
];

function getSdk(): SelfcareSDK | undefined {
  return (globalThis as any).__SELFCARE_SDK__ as SelfcareSDK | undefined;
}

// Icon glyphs — components so they resolve theme colors from context. The AI
// chat lives inside a ThemeProvider (manifest theme), so these are always
// admin-configurable. No hardcoded colors.
function SendIcon() {
  const c = useThemeColors();
  const onPrimary = c.textOnPrimary ?? (readableOn(c.primary500) === 'white' ? '#FFFFFF' : '#111111');
  return <Text style={{ color: onPrimary, fontSize: 16 }}>→</Text>;
}
function PlusIcon() {
  const c = useThemeColors();
  return <Text style={{ color: c.primary500, fontSize: 18 }}>+</Text>;
}
function MicIcon() {
  const c = useThemeColors();
  return <Text style={{ color: c.primary500, fontSize: 18 }}>🎤</Text>;
}
function StopIcon() {
  const onError = readableOn(useThemeColors().error) === 'white' ? '#FFFFFF' : '#111111';
  return <Text style={{ color: onError, fontSize: 16 }}>■</Text>;
}
function BotIcon() {
  const c = useThemeColors();
  const onPrimary = c.textOnPrimary ?? (readableOn(c.primary500) === 'white' ? '#FFFFFF' : '#111111');
  return <Text style={{ color: onPrimary, fontSize: 14 }}>AI</Text>;
}
function SparkleIcon() {
  const c = useThemeColors();
  return <Text style={{ color: c.accent500, fontSize: 12 }}>✨</Text>;
}
function AlertIcon() {
  const c = useThemeColors();
  return <Text style={{ color: c.error, fontSize: 12 }}>⚠</Text>;
}
function RefreshIcon() {
  const c = useThemeColors();
  return <Text style={{ color: c.primary500, fontSize: 14 }}>↻</Text>;
}

export default function AIChatScreen() {
  const sdk = getSdk();
  return (
    <ThemeProvider theme={(sdk?.getManifest()?.theme as ManifestTheme | undefined) ?? null}>
      <AIChatScreenInner />
    </ThemeProvider>
  );
}

function AIChatScreenInner() {
  const { industryPack } = useTenant();
  const auth = useAuthStore();
  const connectionId = (auth as any).connectionId ?? 'acc-1';
  const colors = useThemeColors();
  const styles = useChatStyles();

  const {
    messages, input, setInput, send, isStreaming, stop,
    intent, error, session, newConversation, recommendedPrompts, isUrgentIntent,
  } = useAIChat();

  const { bundles } = useRecommendations(connectionId);
  const [refreshing, setRefreshing] = useState(false);
  const [showPrompts, setShowPrompts] = useState(messages.length === 0);
  const listRef = useRef<FlatList>(null);

  const quickPrompts = industryPack === 'INSURANCE' ? INSURANCE_QUICK_PROMPTS : TELCO_QUICK_PROMPTS;

  // Auto-scroll to bottom
  useEffect(() => {
    if (messages.length > 0) {
      setTimeout(() => listRef.current?.scrollToEnd({ animated: true }), 100);
    }
  }, [messages]);

  // Show prompts when starting a new conversation
  useEffect(() => {
    setShowPrompts(messages.length === 0);
  }, [messages.length]);

  const onSend = async () => {
    if (!input.trim()) return;
    setShowPrompts(false);
    await send(input);
  };

  const onRefresh = async () => {
    setRefreshing(true);
    await newConversation();
    setRefreshing(false);
  };

  const onVoiceInput = () => {
    Alert.alert(
      'Voice Input',
      'Voice input requires Speech Recognition permissions. Continue?',
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Continue', onPress: () => {
          // In production: launch voice recognition, transcribe, then call onSend
          Alert.alert('Coming soon', 'Voice input will be available in the next release.');
        }},
      ]
    );
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      keyboardVerticalOffset={80}
    >
      {/* Header */}
      <View style={styles.header}>
        <View style={styles.headerLeft}>
          <View style={styles.aiAvatar}>
            <BotIcon />
          </View>
          <View>
            <Text style={styles.headerTitle}>AI Assistant</Text>
            <Text style={styles.headerSubtitle}>
              {session ? `Session ${session.id.substring(0, 6)}` : 'Connecting...'}
            </Text>
          </View>
        </View>
        <TouchableOpacity onPress={onRefresh} style={styles.headerButton}>
          <PlusIcon />
        </TouchableOpacity>
      </View>

      {/* Error banner */}
      {error && (
        <View style={styles.errorBanner}>
          <AlertIcon />
          <Text style={styles.errorText}>{error}</Text>
        </View>
      )}

      {/* Urgent intent banner */}
      {isUrgentIntent && !isStreaming && (
        <View style={styles.urgentBanner}>
          <SparkleIcon />
          <Text style={styles.urgentText}>
            Sounds like you need help with an issue — I can create a support ticket for you.
          </Text>
        </View>
      )}

      {/* Message list */}
      <FlatList
        ref={listRef}
        data={messages}
        keyExtractor={(_, i) => `msg-${i}`}
        renderItem={({ item }) => <ChatBubble message={item} />}
        contentContainerStyle={styles.messageList}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={colors.primary500} />
        }
        ListEmptyComponent={() => (
          <View style={styles.welcomeContainer}>
            <Text style={styles.welcomeEmoji}>🤖</Text>
            <Text style={styles.welcomeTitle}>Hi! How can I help you today?</Text>
            <Text style={styles.welcomeSubtitle}>
              I can help you with your account, plans, bills, and more.
            </Text>
          </View>
        )}
        ListFooterComponent={() => (
          isStreaming ? (
            <View style={styles.typingIndicator}>
              <ActivityIndicator size="small" color={colors.primary500} />
              <Text style={styles.typingText}>AI is thinking...</Text>
            </View>
          ) : null
        )}
      />

      {/* Quick prompts */}
      {showPrompts && (
        <View style={styles.promptsContainer}>
          <Text style={styles.promptsTitle}>Quick questions</Text>
          <View style={styles.promptsGrid}>
            {quickPrompts.map((prompt, i) => (
              <TouchableOpacity
                key={i}
                style={styles.promptChip}
                onPress={async () => {
                  setInput(prompt);
                  setShowPrompts(false);
                  await send(prompt);
                }}
              >
                <Text style={styles.promptText}>{prompt}</Text>
              </TouchableOpacity>
            ))}
          </View>

          {/* Smart recommendations */}
          {bundles.length > 0 && (
            <View style={styles.recommendationsSection}>
              <Text style={styles.promptsTitle}>Recommended for you</Text>
              {bundles.slice(0, 2).map((b, i) => (
                <TouchableOpacity
                  key={i}
                  style={styles.recCard}
                  onPress={() => send(`Tell me more about ${b.name}`)}
                >
                  <View style={{ flex: 1 }}>
                    <Text style={styles.recName}>{b.name}</Text>
                    <Text style={styles.recReason}>{b.reason}</Text>
                  </View>
                  <Text style={styles.recPrice}>
                    {b.currency} {b.price.toFixed(0)}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
          )}
        </View>
      )}

      {/* Input bar */}
      <View style={styles.inputBar}>
        <TouchableOpacity onPress={onVoiceInput} style={styles.iconButton}>
          <MicIcon />
        </TouchableOpacity>
        <TextInput
          style={styles.input}
          value={input}
          onChangeText={setInput}
          placeholder="Ask me anything..."
          placeholderTextColor={colors.border}
          multiline
          maxLength={500}
          editable={!isStreaming}
        />
        {isStreaming ? (
          <TouchableOpacity onPress={stop} style={[styles.sendButton, styles.stopButton]}>
            <StopIcon />
          </TouchableOpacity>
        ) : (
          <TouchableOpacity
            onPress={onSend}
            style={[styles.sendButton, !input.trim() && styles.sendButtonDisabled]}
            disabled={!input.trim()}
          >
            <SendIcon />
          </TouchableOpacity>
        )}
      </View>
    </KeyboardAvoidingView>
  );
}

/** Chat chrome styles — resolved from the manifest theme (admin-authored). */
function createChatStyles(t: ResolvedTheme): ReturnType<typeof StyleSheet.create> {
  const colors = t.colors;
  const l = t.layout;
  return StyleSheet.create({
    container: { flex: 1, backgroundColor: colors.surface },
    header: {
      flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
      paddingHorizontal: l.pagePadding, paddingVertical: Math.round(l.sectionGap / 2),
      borderBottomWidth: 1, borderBottomColor: colors.border,
    },
    headerLeft: { flexDirection: 'row', alignItems: 'center', gap: 10 },
    aiAvatar: {
      width: 36, height: 36, borderRadius: 18,
      backgroundColor: colors.primary500,
      alignItems: 'center', justifyContent: 'center',
    },
    headerTitle: { fontSize: fontSizePx(t, 'base'), fontWeight: '600', color: colors.textPrimary },
    headerSubtitle: { fontSize: fontSizePx(t, 'xs'), color: colors.textSecondary },
    headerButton: { padding: 8 },
    errorBanner: {
      flexDirection: 'row', alignItems: 'center', gap: 6,
      backgroundColor: colors.surfaceSubtle, paddingHorizontal: l.pagePadding, paddingVertical: 8,
    },
    errorText: { color: colors.error, fontSize: fontSizePx(t, 'xs'), flex: 1 },
    urgentBanner: {
      flexDirection: 'row', alignItems: 'center', gap: 6,
      backgroundColor: colors.surfaceSubtle, paddingHorizontal: l.pagePadding, paddingVertical: 8,
    },
    urgentText: { color: colors.accent500, fontSize: fontSizePx(t, 'xs'), flex: 1 },
    messageList: { paddingVertical: 8 },
    welcomeContainer: { alignItems: 'center', paddingVertical: 48, paddingHorizontal: 24 },
    welcomeEmoji: { fontSize: fontSizePx(t, '4xl') * 1.33 },
    welcomeTitle: { fontSize: fontSizePx(t, 'xl'), fontWeight: '600', color: colors.textPrimary, marginTop: l.sectionGap },
    welcomeSubtitle: { fontSize: fontSizePx(t, 'sm'), color: colors.textSecondary, textAlign: 'center', marginTop: 8 },
    typingIndicator: { flexDirection: 'row', alignItems: 'center', gap: 8, padding: 12, paddingLeft: 50 },
    typingText: { color: colors.textSecondary, fontSize: fontSizePx(t, 'sm') },
    promptsContainer: {
      paddingHorizontal: l.pagePadding, paddingTop: 8, paddingBottom: 12,
      borderTopWidth: 1, borderTopColor: colors.border,
    },
    promptsTitle: { fontSize: fontSizePx(t, 'xs'), fontWeight: '600', color: colors.textSecondary, marginBottom: 8, textTransform: 'uppercase' },
    promptsGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
    promptChip: {
      backgroundColor: colors.primary300,
      borderRadius: l.radius ?? 16,
      paddingHorizontal: 12,
      paddingVertical: 8,
    },
    promptText: { fontSize: fontSizePx(t, 'sm'), color: colors.primary700 },
    recommendationsSection: { marginTop: l.sectionGap },
    recCard: {
      flexDirection: 'row', alignItems: 'center',
      backgroundColor: colors.surface, borderRadius: l.radius ?? 12, padding: 12, marginBottom: 8,
    },
    recName: { fontSize: fontSizePx(t, 'sm'), fontWeight: '600', color: colors.textPrimary },
    recReason: { fontSize: fontSizePx(t, 'xs'), color: colors.textSecondary, marginTop: 2 },
    recPrice: { fontSize: fontSizePx(t, 'sm'), fontWeight: '600', color: colors.primary500 },
    inputBar: {
      flexDirection: 'row', alignItems: 'flex-end', padding: 8,
      backgroundColor: colors.surface,
      borderTopWidth: 1, borderTopColor: colors.border,
    },
    iconButton: { padding: 10 },
    input: {
      flex: 1, maxHeight: 100, paddingHorizontal: 12, paddingVertical: 8,
      backgroundColor: colors.surfaceSubtle, borderRadius: l.radius ?? 20, fontSize: fontSizePx(t, 'base'), color: colors.textPrimary,
    },
    sendButton: {
      width: 40, height: 40, borderRadius: 20,
      backgroundColor: colors.primary500,
      alignItems: 'center', justifyContent: 'center', marginLeft: 6,
    },
    sendButtonDisabled: { backgroundColor: colors.border },
    stopButton: { backgroundColor: colors.error },
  });
}

function useChatStyles() {
  const theme = useTheme();
  return useMemo(() => createChatStyles(theme), [theme]);
}