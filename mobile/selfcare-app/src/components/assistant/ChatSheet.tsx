/**
 * ChatSheet — Token-driven AI chat panel used by the FloatingAssistant.
 *
 * Same engine as the full AI screen (useAIChat), rendered inside a rounded
 * bottom sheet. Every color, size, spacing, weight and radius comes from the
 * resolved manifest theme (admin-authored) and each string from i18n — no
 * hardcoded shape, palette or metrics (v6 rule #1).
 */

import React from 'react';
import {
  View,
  Text,
  TextInput,
  Pressable,
  ScrollView,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import { useLocalize } from '../../manifest/Localization';
import { fontSizePx, fontWeight, readableOn, ResolvedTheme } from '../../manifest/ThemeEngine';
import { useAIChat } from '../../hooks/useAIChat';
import { ChatMessage } from '../../config/AIClient';

export function ChatSheet({
  theme,
  suggestionKeys,
  onClose,
}: {
  theme: ResolvedTheme;
  suggestionKeys?: string[];
  onClose: () => void;
}): React.JSX.Element {
  const { t } = useLocalize();
  const { messages, input, setInput, send, isStreaming, error, recommendedPrompts } = useAIChat();

  const L = theme.layout;
  const C = theme.colors;
  const semibold = fontWeight(theme, 'semibold');
  const baseSize = fontSizePx(theme, 'base');
  const smSize = fontSizePx(theme, 'sm');
  const lgSize = fontSizePx(theme, 'lg');
  const onPrimary = C.textOnPrimary ?? (readableOn(C.primary) === 'white' ? '#FFFFFF' : '#111111');

  const sendText = async (text: string) => {
    const value = text.trim();
    if (!value || isStreaming) return;
    await send(value);
  };

  const title = t('chat.title', { default: 'AI Assistant' });
  const placeholder = t('chat.inputPlaceholder', { default: 'Ask about your account…' });
  const sendLabel = t('chat.send', { default: 'Send' });
  const closeLabel = t('chat.close', { default: 'Close' });
  const typingLabel = t('chat.typing', { default: '…' });

  const suggestions = (suggestionKeys && suggestionKeys.length > 0
    ? suggestionKeys.map((k) => t(k, { default: k }))
    : recommendedPrompts
  ).slice(0, 3);

  const renderBubble = (msg: ChatMessage, index: number) => {
    const user = msg.role === 'user';
    const bg = user ? C.primary : C.surfaceSubtle;
    const fg = user ? onPrimary : C.textPrimary;
    return (
      <View
        key={`${msg.role}-${index}-${msg.content.substring(0, 12)}`}
        style={{
          alignSelf: user ? 'flex-end' : 'flex-start',
          backgroundColor: bg,
          borderRadius: user ? L.radius * 0.8 : L.radius,
          maxWidth: `${L.bubbleMaxWidthPct}%`,
          padding: L.cardPadding,
          marginVertical: L.sectionGap / 2,
        }}
      >
        <Text style={{ color: fg, fontSize: baseSize }}>{msg.content}</Text>
      </View>
    );
  };

  return (
    <KeyboardAvoidingView
      style={styles.root}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: L.pagePadding,
          backgroundColor: C.surface,
          borderBottomWidth: L.hairlinePx,
          borderBottomColor: C.border,
        }}
      >
        <Text style={{ color: C.textPrimary, fontSize: lgSize, fontWeight: semibold }}>
          {title}
        </Text>
        <Pressable onPress={onClose}>
          <Text style={{ color: C.textSecondary, fontSize: smSize }}>{closeLabel}</Text>
        </Pressable>
      </View>

      <ScrollView
        style={styles.messages}
        contentContainerStyle={{ padding: L.pagePadding }}
      >
        {suggestions.map((s) => (
          <Pressable
            key={s}
            onPress={() => sendText(s)}
            style={{
              alignSelf: 'flex-start',
              maxWidth: `${L.bubbleMaxWidthPct}%`,
              borderWidth: L.hairlinePx,
              borderColor: C.border,
              borderRadius: L.radius,
              padding: L.screenMargin,
              marginBottom: L.sectionGap / 2,
            }}
          >
            <Text style={{ color: C.textSecondary, fontSize: smSize }}>{s}</Text>
          </Pressable>
        ))}
        {messages.map(renderBubble)}
        {isStreaming ? (
          <Text
            style={{
              color: C.textSecondary,
              fontSize: smSize,
              marginTop: L.sectionGap / 2,
            }}
          >
            {typingLabel}
          </Text>
        ) : null}
        {error ? (
          <Text
            style={{
              color: C.error,
              fontSize: smSize,
              marginTop: L.sectionGap / 2,
            }}
          >
            {error}
          </Text>
        ) : null}
      </ScrollView>

      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          padding: L.pagePadding,
          backgroundColor: C.surface,
          borderTopWidth: L.hairlinePx,
          borderTopColor: C.border,
        }}
      >
        <TextInput
          value={input}
          onChangeText={setInput}
          placeholder={placeholder}
          placeholderTextColor={C.textSecondary}
          style={{
            flex: 1,
            minHeight: L.minTouchTarget,
            borderRadius: L.radius,
            borderWidth: L.hairlinePx,
            borderColor: C.border,
            padding: L.screenMargin,
            color: C.textPrimary,
            fontSize: baseSize,
          }}
          onSubmitEditing={() => sendText(input)}
        />
        <Pressable
          style={{
            marginLeft: L.screenMargin,
            alignItems: 'center',
            justifyContent: 'center',
            backgroundColor: C.primary,
            borderRadius: L.radius,
            paddingVertical: L.screenMargin,
            paddingHorizontal: L.pagePadding,
          }}
          onPress={() => sendText(input)}
          disabled={isStreaming || !input.trim()}
        >
          <Text style={{ color: onPrimary, fontSize: smSize, fontWeight: semibold }}>
            {sendLabel}
          </Text>
        </Pressable>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = { root: { flex: 1 }, messages: { flex: 1 } };

export default ChatSheet;