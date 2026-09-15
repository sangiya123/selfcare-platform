/**
 * ChatBubble — single message bubble in the AI chat.
 *
 * Fully token-driven: every colour and size comes from the resolved manifest
 * theme (admin-authored in Selfcare Studio) via useTheme. No hardcoded brand
 * values.
 */
import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { ChatMessage } from '../config/AIClient';
import {
  useTheme,
  useThemeColors,
  fontSizePx,
  readableOn,
} from '../manifest/ThemeEngine';

interface Props {
  message: ChatMessage;
  /** Optional per-instance hint layer (still resolved over the manifest theme). */
  theme?: {
    primary?: string;
    primaryLight?: string;
    textPrimary?: string;
    textSecondary?: string;
    surface?: string;
  };
}

export function ChatBubble({ message, theme = {} }: Props) {
  const resolved = useTheme();
  const baseColors = useThemeColors();
  const isUser = message.role === 'user';
  const isTool = message.role === 'tool';

  // Manifest theme first; optional instance hints on top (never hardcoded).
  const colors = {
    primary: theme.primary ?? baseColors.primary500,
    primaryLight: theme.primaryLight ?? baseColors.primary300,
    textPrimary: theme.textPrimary ?? baseColors.textPrimary,
    textSecondary: theme.textSecondary ?? baseColors.textSecondary,
    surface: theme.surface ?? baseColors.surface,
  };

  const onPrimary = readableOn(colors.primary) === 'white' ? '#FFFFFF' : '#111111';
  const onPrimaryMuted = readableOn(colors.primary) === 'white'
    ? 'rgba(255,255,255,0.65)'
    : 'rgba(0,0,0,0.5)';

  if (isTool) {
    return (
      <View style={styles.toolBubble}>
        <Text style={[styles.toolLabel, { color: colors.textSecondary, fontSize: fontSizePx(resolved, 'xs') }]}>
          [Tool] {message.content}
        </Text>
      </View>
    );
  }

  return (
    <View style={[styles.row, isUser ? styles.rowUser : styles.rowAssistant]}>
      {/* Avatar */}
      <View style={[
        styles.avatar,
        { backgroundColor: isUser ? colors.primary : colors.primaryLight },
      ]}>
        <Text style={[styles.avatarText, { color: isUser ? onPrimary : colors.textPrimary, fontSize: fontSizePx(resolved, 'xs') }]}>
          {isUser ? 'U' : 'AI'}
        </Text>
      </View>

      {/* Bubble */}
      <View style={[
        styles.bubble,
        isUser
          ? { backgroundColor: colors.primary }
          : { backgroundColor: colors.surface, borderWidth: StyleSheet.hairlineWidth, borderColor: colors.primaryLight },
      ]}>
        <Text style={[
          styles.content,
          { color: isUser ? onPrimary : colors.textPrimary, fontSize: fontSizePx(resolved, 'base') },
        ]}>
          {message.content}
          {message.streaming && <Text style={[styles.cursor, { color: colors.textSecondary }]}>▍</Text>}
        </Text>
        {message.timestamp && (
          <Text style={[
            styles.timestamp,
            { color: isUser ? onPrimaryMuted : colors.textSecondary, fontSize: fontSizePx(resolved, 'xs') },
          ]}>
            {new Date(message.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
          </Text>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    marginVertical: 4,
    paddingHorizontal: 12,
    alignItems: 'flex-end',
  },
  rowUser: { justifyContent: 'flex-end' },
  rowAssistant: { justifyContent: 'flex-start' },
  avatar: {
    width: 28,
    height: 28,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    marginHorizontal: 4,
  },
  avatarText: {
    fontWeight: '700',
  },
  bubble: {
    maxWidth: '75%',
    padding: 10,
    borderRadius: 16,
  },
  content: {
    lineHeight: 21,
  },
  cursor: {
    fontWeight: '700',
  },
  timestamp: {
    marginTop: 4,
  },
  toolBubble: {
    flexDirection: 'row',
    alignItems: 'center',
    marginVertical: 2,
    paddingHorizontal: 52,
  },
  toolLabel: {
    fontStyle: 'italic',
  },
});