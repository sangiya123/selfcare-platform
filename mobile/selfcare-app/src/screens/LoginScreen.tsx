/**
 * LoginScreen — captures the user's phone number and sends an OTP.
 *
 * Flow:
 *   1. User enters MSISDN (telco clients) or customer ID (insurance clients)
 *   2. SDK asks for OTP via auth.sendOtp()
 *   3. On success → navigate to OtpScreen
 *
 * Industry-aware: the input label and validation adapt to the active
 * industry pack (telco vs insurance).
 */
import React, { useState, useCallback } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  Alert,
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useTenant } from '../hooks/useTenant';
import { useApi } from '../hooks/useApi';
import { tokens } from '../styles/design-tokens';
import type { RootStackParamList } from '../../App';

type Nav = NativeStackNavigationProp<RootStackParamList, 'Login'>;

export function LoginScreen(): React.JSX.Element {
  const navigation = useNavigation<Nav>();
  const { tenantId: activeTenant, industryPack } = useTenant();
  const api = useApi();

  // Telco: MSISDN. Insurance: customer/policy number.
  const [identifier, setIdentifier] = useState('');
  const [loading, setLoading] = useState(false);
  const [deliveryChannel, setDeliveryChannel] = useState<'sms' | 'whatsapp' | 'email'>('sms');

  const isTelco = industryPack === 'telco' || industryPack === null;
  const idLabel = isTelco ? 'Mobile number' : 'Customer / policy number';
  const idPlaceholder = isTelco ? '+94 77 123 4567' : 'e.g. AIA-12345678';
  const idHelp = isTelco
    ? 'We will send a 6-digit code via SMS or WhatsApp.'
    : 'We will send a 6-digit code via your registered email.';

  const onSendOtp = useCallback(async () => {
    if (!identifier.trim()) {
      Alert.alert('Required', `Please enter your ${isTelco ? 'mobile number' : 'customer number'}.`);
      return;
    }
    setLoading(true);
    try {
      const result = await api.auth.sendOtp({
        identifier: identifier.trim(),
        channel: deliveryChannel,
        tenantId: activeTenant,
      });
      if (result.success) {
        navigation.navigate('Otp', { msisdn: identifier.trim() });
      } else {
        Alert.alert('Could not send code', result.errorMessage ?? 'Please try again.');
      }
    } catch (err: any) {
      Alert.alert('Error', err?.message ?? 'Network error');
    } finally {
      setLoading(false);
    }
  }, [identifier, deliveryChannel, activeTenant, isTelco, api, navigation]);

  return (
    <KeyboardAvoidingView
      style={styles.root}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled">
        <View style={styles.brand}>
          <Text style={styles.brandName}>OMOBIO</Text>
          <Text style={styles.brandTagline}>Selfcare</Text>
          {activeTenant ? (
            <Text style={styles.tenantLabel}>{activeTenant}</Text>
          ) : null}
        </View>

        <View style={styles.card}>
          <Text style={styles.title}>Sign in</Text>
          <Text style={styles.subtitle}>
            {isTelco ? 'Enter your mobile number to receive a verification code.' : 'Enter your customer ID to receive a verification code.'}
          </Text>

          <Text style={styles.label}>{idLabel}</Text>
          <TextInput
            style={styles.input}
            value={identifier}
            onChangeText={setIdentifier}
            placeholder={idPlaceholder}
            keyboardType={isTelco ? 'phone-pad' : 'default'}
            autoCorrect={false}
            autoCapitalize="none"
            testID="login-identifier"
          />
          <Text style={styles.help}>{idHelp}</Text>

          {isTelco && (
            <>
              <Text style={styles.label}>Send code via</Text>
              <View style={styles.channelRow}>
                {(['sms', 'whatsapp'] as const).map((c) => (
                  <TouchableOpacity
                    key={c}
                    style={[
                      styles.channelChip,
                      deliveryChannel === c && styles.channelChipActive,
                    ]}
                    onPress={() => setDeliveryChannel(c)}
                    testID={`channel-${c}`}
                  >
                    <Text
                      style={[
                        styles.channelChipText,
                        deliveryChannel === c && styles.channelChipTextActive,
                      ]}
                    >
                      {c === 'sms' ? 'SMS' : 'WhatsApp'}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </>
          )}

          <TouchableOpacity
            style={[styles.cta, loading && styles.ctaDisabled]}
            onPress={onSendOtp}
            disabled={loading}
            testID="login-submit"
          >
            {loading ? (
              <ActivityIndicator color="#fff" />
            ) : (
              <Text style={styles.ctaText}>Send code</Text>
            )}
          </TouchableOpacity>
        </View>

        <Text style={styles.footer}>
          By continuing you agree to our Terms of Service and Privacy Policy.
        </Text>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: tokens.colors.surface },
  scroll: { flexGrow: 1, padding: 24, justifyContent: 'center' },
  brand: { alignItems: 'center', marginBottom: 32 },
  brandName: {
    fontSize: 36,
    fontWeight: '700',
    color: tokens.colors.primary700,
    letterSpacing: 4,
  },
  brandTagline: {
    fontSize: 18,
    fontWeight: '500',
    color: tokens.colors.primary500,
    marginTop: 4,
  },
  tenantLabel: {
    marginTop: 12,
    fontSize: 12,
    color: tokens.colors.textSecondary,
    paddingHorizontal: 8,
    paddingVertical: 2,
    backgroundColor: tokens.colors.surfaceSubtle,
    borderRadius: 4,
  },
  card: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 20,
    shadowColor: '#000',
    shadowOpacity: 0.06,
    shadowRadius: 12,
    elevation: 2,
  },
  title: { fontSize: 22, fontWeight: '700', color: tokens.colors.textPrimary },
  subtitle: { fontSize: 14, color: tokens.colors.textSecondary, marginTop: 4, marginBottom: 20 },
  label: { fontSize: 13, fontWeight: '500', color: tokens.colors.textPrimary, marginTop: 16, marginBottom: 6 },
  input: {
    borderWidth: 1,
    borderColor: tokens.colors.border,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 12,
    fontSize: 16,
    color: tokens.colors.textPrimary,
  },
  help: { fontSize: 12, color: tokens.colors.textSecondary, marginTop: 6 },
  channelRow: { flexDirection: 'row', gap: 8, marginTop: 8 },
  channelChip: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderWidth: 1,
    borderColor: tokens.colors.border,
    borderRadius: 18,
  },
  channelChipActive: { backgroundColor: tokens.colors.primary500, borderColor: tokens.colors.primary500 },
  channelChipText: { fontSize: 13, color: tokens.colors.textPrimary },
  channelChipTextActive: { color: '#fff', fontWeight: '600' },
  cta: {
    marginTop: 24,
    backgroundColor: tokens.colors.primary500,
    borderRadius: 8,
    paddingVertical: 14,
    alignItems: 'center',
  },
  ctaDisabled: { opacity: 0.6 },
  ctaText: { color: '#fff', fontSize: 16, fontWeight: '600' },
  footer: { textAlign: 'center', fontSize: 11, color: tokens.colors.textSecondary, marginTop: 24 },
});
