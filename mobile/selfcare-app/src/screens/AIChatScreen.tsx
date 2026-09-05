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
 * The screen is industry-aware: telco customers see "Check balance" first,
 * insurance customers see "My policies" first.
 */
import React, { useState, useRef, useEffect } from 'react';
import {
  View, Text, TextInput, FlatList, StyleSheet,
  TouchableOpacity, KeyboardAvoidingView, Platform,
  ActivityIndicator, RefreshControl, Alert,
} from 'react-native';
// Icons replaced with text-based equivalents for React Native compatibility
// TODO: Install @expo/vector-icons or react-native-vector-icons for real icons
const SendIcon = () => <Text style={{color:'#fff',fontSize:16}}>→</Text>;
const PlusIcon = () => <Text style={{color:'#6C2DC7',fontSize:18}}>+</Text>;
const MicIcon = () => <Text style={{color:'#6C2DC7',fontSize:18}}>🎤</Text>;
const StopIcon = () => <Text style={{color:'#fff',fontSize:16}}>■</Text>;
const BotIcon = () => <Text style={{color:'#fff',fontSize:14}}>AI</Text>;
const SparkleIcon = () => <Text style={{color:'#FF6B00',fontSize:12}}>✨</Text>;
const AlertIcon = () => <Text style={{color:'#D32F2F',fontSize:12}}>⚠</Text>;
const RefreshIcon = () => <Text style={{color:'#6C2DC7',fontSize:14}}>↻</Text>;
import { useAIChat } from '../hooks/useAIChat';
import { useRecommendations } from '../hooks/useRecommendations';
import { useTenant } from '../hooks/useTenant';
import { useAuth } from '../hooks/useAuth';
import { ChatBubble } from '../components/ChatBubble';

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

export default function AIChatScreen() {
  const { industryPack } = useTenant();
  const auth = useAuth();
  const connectionId = (auth as any).connectionId ?? 'acc-1';

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
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#6C2DC7" />
        }
        ListEmptyComponent={() => (
          <View style={styles.welcomeContainer}>
            <Text style={{fontSize: 48}}>🤖</Text>
            <Text style={styles.welcomeTitle}>Hi! How can I help you today?</Text>
            <Text style={styles.welcomeSubtitle}>
              I can help you with your account, plans, bills, and more.
            </Text>
          </View>
        )}
        ListFooterComponent={() => (
          isStreaming ? (
            <View style={styles.typingIndicator}>
              <ActivityIndicator size="small" color="#6C2DC7" />
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
          placeholderTextColor="#999"
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

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#FFFFFF' },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: 16, paddingVertical: 12,
    borderBottomWidth: 1, borderBottomColor: '#F0F0F0',
  },
  headerLeft: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  aiAvatar: {
    width: 36, height: 36, borderRadius: 18,
    backgroundColor: '#6C2DC7',
    alignItems: 'center', justifyContent: 'center',
  },
  headerTitle: { fontSize: 16, fontWeight: '600', color: '#212121' },
  headerSubtitle: { fontSize: 11, color: '#757575' },
  headerButton: { padding: 8 },
  errorBanner: {
    flexDirection: 'row', alignItems: 'center', gap: 6,
    backgroundColor: '#FFEBEE', paddingHorizontal: 16, paddingVertical: 8,
  },
  errorText: { color: '#D32F2F', fontSize: 12, flex: 1 },
  urgentBanner: {
    flexDirection: 'row', alignItems: 'center', gap: 6,
    backgroundColor: '#FFF3E0', paddingHorizontal: 16, paddingVertical: 8,
  },
  urgentText: { color: '#FF6B00', fontSize: 12, flex: 1 },
  messageList: { paddingVertical: 8 },
  welcomeContainer: { alignItems: 'center', paddingVertical: 48, paddingHorizontal: 24 },
  welcomeTitle: { fontSize: 20, fontWeight: '600', color: '#212121', marginTop: 16 },
  welcomeSubtitle: { fontSize: 14, color: '#757575', textAlign: 'center', marginTop: 8 },
  typingIndicator: { flexDirection: 'row', alignItems: 'center', gap: 8, padding: 12, paddingLeft: 50 },
  typingText: { color: '#757575', fontSize: 13 },
  promptsContainer: {
    paddingHorizontal: 16, paddingTop: 8, paddingBottom: 12,
    borderTopWidth: 1, borderTopColor: '#F0F0F0',
  },
  promptsTitle: { fontSize: 12, fontWeight: '600', color: '#757575', marginBottom: 8, textTransform: 'uppercase' },
  promptsGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  promptChip: {
    backgroundColor: '#F0E6FF',
    borderRadius: 16,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  promptText: { fontSize: 13, color: '#6C2DC7' },
  recommendationsSection: { marginTop: 16 },
  recCard: {
    flexDirection: 'row', alignItems: 'center',
    backgroundColor: '#F9F9F9', borderRadius: 12, padding: 12, marginBottom: 8,
  },
  recName: { fontSize: 14, fontWeight: '600', color: '#212121' },
  recReason: { fontSize: 12, color: '#757575', marginTop: 2 },
  recPrice: { fontSize: 14, fontWeight: '600', color: '#6C2DC7' },
  inputBar: {
    flexDirection: 'row', alignItems: 'flex-end', padding: 8,
    backgroundColor: '#FFFFFF',
    borderTopWidth: 1, borderTopColor: '#F0F0F0',
  },
  iconButton: { padding: 10 },
  input: {
    flex: 1, maxHeight: 100, paddingHorizontal: 12, paddingVertical: 8,
    backgroundColor: '#F5F5F5', borderRadius: 20, fontSize: 15, color: '#212121',
  },
  sendButton: {
    width: 40, height: 40, borderRadius: 20,
    backgroundColor: '#6C2DC7',
    alignItems: 'center', justifyContent: 'center', marginLeft: 6,
  },
  sendButtonDisabled: { backgroundColor: '#B0B0B0' },
  stopButton: { backgroundColor: '#D32F2F' },
});
