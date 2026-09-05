/**
 * ChatBubble — single message bubble in the AI chat.
 *
 * Renders:
 * - Role avatar (text-based: "U" for user, "AI" for assistant)
 * - Message content with streaming cursor
 * - Timestamp
 * - Tool call indicators
 */
import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { ChatMessage } from '../config/AIClient';

interface Props {
  message: ChatMessage;
  theme?: {
    primary?: string;
    primaryLight?: string;
    textPrimary?: string;
    textSecondary?: string;
    surface?: string;
  };
}

export function ChatBubble({ message, theme = {} }: Props) {
  const isUser = message.role === 'user';
  const isTool = message.role === 'tool';

  const palette = {
    primary: theme.primary ?? '#6C2DC7',
    primaryLight: theme.primaryLight ?? '#A78BFA',
    textPrimary: theme.textPrimary ?? '#212121',
    textSecondary: theme.textSecondary ?? '#757575',
    surface: theme.surface ?? '#F5F5F5',
  };

  if (isTool) {
    return (
      <View style={styles.toolBubble}>
        <Text style={[styles.toolLabel, { color: palette.textSecondary }]}>
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
        { backgroundColor: isUser ? palette.primary : palette.primaryLight },
      ]}>
        <Text style={styles.avatarText}>{isUser ? 'U' : 'AI'}</Text>
      </View>

      {/* Bubble */}
      <View style={[
        styles.bubble,
        isUser
          ? { backgroundColor: palette.primary }
          : { backgroundColor: palette.surface, borderWidth: 1, borderColor: palette.primaryLight },
      ]}>
        <Text style={[
          styles.content,
          { color: isUser ? '#FFFFFF' : palette.textPrimary },
        ]}>
          {message.content}
          {message.streaming && <Text style={styles.cursor}>▍</Text>}
        </Text>
        {message.timestamp && (
          <Text style={[
            styles.timestamp,
            { color: isUser ? 'rgba(255,255,255,0.65)' : palette.textSecondary },
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
    color: '#FFFFFF',
    fontSize: 11,
    fontWeight: '700',
  },
  bubble: {
    maxWidth: '75%',
    padding: 10,
    borderRadius: 16,
  },
  content: {
    fontSize: 15,
    lineHeight: 21,
  },
  cursor: {
    color: '#999',
    fontWeight: '700',
  },
  timestamp: {
    fontSize: 10,
    marginTop: 4,
  },
  toolBubble: {
    flexDirection: 'row',
    alignItems: 'center',
    marginVertical: 2,
    paddingHorizontal: 52,
  },
  toolLabel: {
    fontSize: 11,
    fontStyle: 'italic',
  },
});
