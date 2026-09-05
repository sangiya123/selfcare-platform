/**
 * SupportTile — Customer-support contact tile.
 *
 * Component ID: SupportTile
 * Provides: live chat entry, FAQs, call support, email support.
 */

import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity, Linking } from 'react-native';
import type { WidgetProps } from '../ComponentRegistry';
import { tokens } from '../../styles/design-tokens';

interface SupportConfig {
  supportPhone?: string;
  supportEmail?: string;
  liveChatRoute?: string;
  faqRoute?: string;
  showCallSupport?: boolean;
  showEmailSupport?: boolean;
  showLiveChat?: boolean;
  showFAQ?: boolean;
  layout?: 'list' | 'grid';
}

interface SupportData {
  supportPhone?: string;
  supportEmail?: string;
  available24x7?: boolean;
  waitTimeMin?: number;
}

export function SupportTile(props: WidgetProps): React.JSX.Element {
  const config = (props.props ?? {}) as SupportConfig;
  const data = (props.data as SupportData | undefined) ?? {};

  const phone = data.supportPhone ?? config.supportPhone;
  const email = data.supportEmail ?? config.supportEmail;

  const handleCall = () => {
    if (phone) Linking.openURL(`tel:${phone}`);
  };
  const handleEmail = () => {
    if (email) Linking.openURL(`mailto:${email}`);
  };
  const handleChat = () => {
    props.onAction?.({ event: 'start_chat', type: 'START_JOURNEY', route: config.liveChatRoute ?? '/support/chat' });
  };
  const handleFAQ = () => {
    props.onAction?.({ event: 'view_faq', type: 'NAVIGATE', route: config.faqRoute ?? '/support/faq' });
  };

  const showCall = (config.showCallSupport !== false) && !!phone;
  const showEmail = (config.showEmailSupport !== false) && !!email;
  const showChat = config.showLiveChat !== false;
  const showFAQ = config.showFAQ !== false;

  return (
    <View style={[styles.container, config.layout === 'grid' && styles.gridLayout]}>
      <View style={styles.header}>
        <Text style={styles.title}>Need help?</Text>
        {data.available24x7 && (
          <View style={styles.badge}>
            <Text style={styles.badgeText}>24/7</Text>
          </View>
        )}
      </View>
      {data.waitTimeMin !== undefined && (
        <Text style={styles.subtitle}>Avg wait: {data.waitTimeMin} min</Text>
      )}

      <View style={styles.optionsContainer}>
        {showChat && <SupportOption icon="chat" label="Live Chat" onPress={handleChat} />}
        {showCall && <SupportOption icon="phone" label="Call Us" onPress={handleCall} />}
        {showEmail && <SupportOption icon="email" label="Email" onPress={handleEmail} />}
        {showFAQ && <SupportOption icon="faq" label="FAQs" onPress={handleFAQ} />}
      </View>
    </View>
  );
}

function SupportOption({ icon, label, onPress }: { icon: string; label: string; onPress: () => void }): React.JSX.Element {
  return (
    <TouchableOpacity style={styles.option} onPress={onPress} activeOpacity={0.7}>
      <View style={styles.optionIcon}>
        <Text style={styles.optionIconText}>{icon.charAt(0).toUpperCase()}</Text>
      </View>
      <Text style={styles.optionLabel}>{label}</Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  container: { backgroundColor: tokens.colors.surface, borderRadius: tokens.borderRadius.xl, padding: tokens.spacing.lg, ...tokens.elevation.sm },
  gridLayout: { padding: tokens.spacing.md },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 },
  title: { fontSize: tokens.fontSize.lg, fontWeight: tokens.fontWeight.bold, color: tokens.colors.textPrimary },
  subtitle: { fontSize: tokens.fontSize.xs, color: tokens.colors.textSecondary, marginBottom: tokens.spacing.md },
  badge: { backgroundColor: tokens.colors.success + '20', paddingHorizontal: 8, paddingVertical: 2, borderRadius: tokens.borderRadius.full },
  badgeText: { color: tokens.colors.success, fontSize: 10, fontWeight: tokens.fontWeight.bold },
  optionsContainer: { flexDirection: 'row', flexWrap: 'wrap', gap: tokens.spacing.md, marginTop: tokens.spacing.md },
  option: { alignItems: 'center', minWidth: 72 },
  optionIcon: { width: 48, height: 48, borderRadius: 24, backgroundColor: tokens.colors.primary500, justifyContent: 'center', alignItems: 'center', marginBottom: 4 },
  optionIconText: { color: tokens.colors.surface, fontSize: tokens.fontSize.base, fontWeight: tokens.fontWeight.bold },
  optionLabel: { fontSize: tokens.fontSize.xs, color: tokens.colors.textPrimary, textAlign: 'center' },
});
