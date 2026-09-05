/**
 * ProfileScreen — user account details and sign out.
 */
import React, { useCallback } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, Alert } from 'react-native';
import { useApi } from '../hooks/useApi';
import { useTenant } from '../hooks/useTenant';
import { tokens } from '../styles/design-tokens';
import { User, Mail, Phone, LogOut, Settings, Lock, HelpCircle } from 'lucide-react';

export function ProfileScreen(): React.JSX.Element {
  const api = useApi();
  const { tenantId: activeTenant } = useTenant();

  const onSignOut = useCallback(() => {
    Alert.alert('Sign out', 'Are you sure you want to sign out?', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Sign out',
        style: 'destructive',
        onPress: async () => {
          await api.auth.signOut();
        },
      },
    ]);
  }, [api]);

  return (
    <ScrollView style={styles.root} contentContainerStyle={styles.content}>
      <View style={styles.avatarCard}>
        <View style={styles.avatar}>
          <User size={32} color={tokens.colors.primary500} />
        </View>
        <Text style={styles.name}>Your account</Text>
        <Text style={styles.tenant}>{activeTenant}</Text>
      </View>

      <View style={styles.section}>
        <RowItem icon={User} label="Personal information" />
        <RowItem icon={Phone} label="Contact details" />
        <RowItem icon={Mail} label="Notification preferences" />
        <RowItem icon={Lock} label="Security & sign-in" />
        <RowItem icon={Settings} label="App settings" />
        <RowItem icon={HelpCircle} label="Help & support" />
      </View>

      <TouchableOpacity style={styles.signOut} onPress={onSignOut} testID="profile-signout">
        <LogOut size={18} color={tokens.colors.error} />
        <Text style={styles.signOutText}>Sign out</Text>
      </TouchableOpacity>

      <Text style={styles.version}>OMOBIO Selfcare · v1.0.0</Text>
    </ScrollView>
  );
}

function RowItem({ icon: Icon, label }: { icon: any; label: string }): React.JSX.Element {
  return (
    <TouchableOpacity style={styles.row}>
      <Icon size={20} color={tokens.colors.textSecondary} />
      <Text style={styles.rowText}>{label}</Text>
      <Text style={styles.chevron}>›</Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: tokens.colors.surfaceSubtle },
  content: { padding: 16 },
  avatarCard: { alignItems: 'center', padding: 20, backgroundColor: tokens.colors.surface, borderRadius: 12 },
  avatar: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: tokens.colors.surfaceSubtle,
    alignItems: 'center',
    justifyContent: 'center',
  },
  name: { fontSize: 16, fontWeight: '600', color: tokens.colors.textPrimary, marginTop: 12 },
  tenant: { fontSize: 12, color: tokens.colors.textSecondary, marginTop: 2 },
  section: {
    marginTop: 16,
    backgroundColor: tokens.colors.surface,
    borderRadius: 12,
    overflow: 'hidden',
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
  chevron: { fontSize: 22, color: tokens.colors.textSecondary },
  signOut: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    marginTop: 20,
    padding: 14,
    backgroundColor: tokens.colors.surface,
    borderRadius: 12,
  },
  signOutText: { color: tokens.colors.error, fontWeight: '600', fontSize: 14 },
  version: { textAlign: 'center', fontSize: 11, color: tokens.colors.textSecondary, marginTop: 16 },
});
