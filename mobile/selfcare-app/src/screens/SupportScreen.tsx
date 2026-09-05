/**
 * SupportScreen — FAQ, contact, and self-service support tools.
 */
import React from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, Linking } from 'react-native';
import { useTenant } from '../hooks/useTenant';
import { tokens } from '../styles/design-tokens';
import { MessageCircle, Phone, Mail, FileText, ChevronRight } from 'lucide-react';

export function SupportScreen(): React.JSX.Element {
  const { tenantId: activeTenant } = useTenant();

  const onCall = () => Linking.openURL('tel:+94112345678');
  const onEmail = () => Linking.openURL('mailto:support@omobio.io');
  const onChat = () => Linking.openURL('https://chat.omobio.io');

  return (
    <ScrollView style={styles.root} contentContainerStyle={styles.content}>
      <Text style={styles.h1}>How can we help?</Text>
      <Text style={styles.h2}>For {activeTenant}</Text>

      <View style={styles.contactRow}>
        <ContactCard icon={MessageCircle} label="Live chat" onPress={onChat} />
        <ContactCard icon={Phone} label="Call us" onPress={onCall} />
        <ContactCard icon={Mail} label="Email" onPress={onEmail} />
      </View>

      <Text style={styles.sectionTitle}>Common questions</Text>
      <View style={styles.faq}>
        <FaqRow q="How do I pay my bill?" />
        <FaqRow q="How do I recharge my account?" />
        <FaqRow q="How do I view my usage?" />
        <FaqRow q="How do I update my contact details?" />
        <FaqRow q="I lost my SIM card. What do I do?" />
      </View>

      <Text style={styles.sectionTitle}>Help articles</Text>
      <View style={styles.faq}>
        <ArticleRow title="Getting started" />
        <ArticleRow title="Payment methods" />
        <ArticleRow title="Travelling abroad" />
        <ArticleRow title="Account security" />
      </View>
    </ScrollView>
  );
}

function ContactCard({ icon: Icon, label, onPress }: any): React.JSX.Element {
  return (
    <TouchableOpacity style={styles.contactCard} onPress={onPress}>
      <Icon size={24} color={tokens.colors.primary500} />
      <Text style={styles.contactLabel}>{label}</Text>
    </TouchableOpacity>
  );
}

function FaqRow({ q }: { q: string }): React.JSX.Element {
  return (
    <TouchableOpacity style={styles.row}>
      <FileText size={18} color={tokens.colors.textSecondary} />
      <Text style={styles.rowText}>{q}</Text>
      <ChevronRight size={18} color={tokens.colors.textSecondary} />
    </TouchableOpacity>
  );
}

function ArticleRow({ title }: { title: string }): React.JSX.Element {
  return (
    <TouchableOpacity style={styles.row}>
      <Text style={styles.rowText}>{title}</Text>
      <ChevronRight size={18} color={tokens.colors.textSecondary} />
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: tokens.colors.surfaceSubtle },
  content: { padding: 16 },
  h1: { fontSize: 22, fontWeight: '700', color: tokens.colors.textPrimary },
  h2: { fontSize: 13, color: tokens.colors.textSecondary, marginBottom: 20 },
  contactRow: { flexDirection: 'row', gap: 8, marginBottom: 24 },
  contactCard: {
    flex: 1,
    backgroundColor: tokens.colors.surface,
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
  },
  contactLabel: { fontSize: 12, color: tokens.colors.textPrimary, marginTop: 8, fontWeight: '500' },
  sectionTitle: {
    fontSize: 13,
    fontWeight: '600',
    color: tokens.colors.textSecondary,
    textTransform: 'uppercase',
    marginBottom: 8,
    marginTop: 8,
  },
  faq: {
    backgroundColor: tokens.colors.surface,
    borderRadius: 12,
    overflow: 'hidden',
    marginBottom: 16,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 14,
    borderBottomWidth: 1,
    borderBottomColor: tokens.colors.surfaceSubtle,
    gap: 12,
  },
  rowText: { flex: 1, fontSize: 14, color: tokens.colors.textPrimary },
});
