/**
 * OtpScreen — user enters the 6-digit code they received.
 *
 * Submits to auth.verifyOtp() and on success the SDK transitions to authenticated.
 * Includes a countdown resend button and a back button.
 */
import React, { useState, useEffect, useRef, useCallback } from 'react';
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
} from 'react-native';
import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useApi } from '../hooks/useApi';
import { useTenant } from '../hooks/useTenant';
import { tokens } from '../styles/design-tokens';
import type { RootStackParamList } from '../../App';

type Nav = NativeStackNavigationProp<RootStackParamList, 'Otp'>;
type R = RouteProp<RootStackParamList, 'Otp'>;

const RESEND_SECONDS = 30;
const CODE_LENGTH = 6;

export function OtpScreen(): React.JSX.Element {
  const navigation = useNavigation<Nav>();
  const route = useRoute<R>();
  const api = useApi();
  const { tenantId: activeTenant } = useTenant();

  const [code, setCode] = useState<string[]>(Array(CODE_LENGTH).fill(''));
  const [loading, setLoading] = useState(false);
  const [resendIn, setResendIn] = useState(RESEND_SECONDS);
  const [error, setError] = useState<string | null>(null);
  const inputRefs = useRef<(TextInput | null)[]>([]);

  useEffect(() => {
    if (resendIn <= 0) return;
    const t = setTimeout(() => setResendIn(resendIn - 1), 1000);
    return () => clearTimeout(t);
  }, [resendIn]);

  useEffect(() => {
    // Auto-focus first box
    inputRefs.current[0]?.focus();
  }, []);

  const handleCodeChange = useCallback(
    (text: string, index: number) => {
      const digit = text.replace(/\D/g, '').slice(-1);
      const next = [...code];
      next[index] = digit;
      setCode(next);
      setError(null);
      if (digit && index < CODE_LENGTH - 1) {
        inputRefs.current[index + 1]?.focus();
      }
      // Auto-submit when all filled
      if (index === CODE_LENGTH - 1 && digit) {
        const full = next.join('');
        if (full.length === CODE_LENGTH) {
          onVerify(full);
        }
      }
    },
    [code]
  );

  const onVerify = useCallback(
    async (fullCode?: string) => {
      const final = fullCode ?? code.join('');
      if (final.length !== CODE_LENGTH) {
        setError('Please enter the full 6-digit code.');
        return;
      }
      setLoading(true);
      try {
        const result = await api.auth.verifyOtp({
          identifier: route.params.msisdn,
          code: final,
          tenantId: activeTenant,
        });
        if (result.success) {
          // App.tsx will switch to Main when auth state becomes 'authenticated'
        } else {
          setError(result.errorMessage ?? 'Invalid code');
          setCode(Array(CODE_LENGTH).fill(''));
          inputRefs.current[0]?.focus();
        }
      } catch (err: any) {
        setError(err?.message ?? 'Network error');
      } finally {
        setLoading(false);
      }
    },
    [code, route.params.msisdn, activeTenant, api]
  );

  const onResend = useCallback(async () => {
    if (resendIn > 0) return;
    setResendIn(RESEND_SECONDS);
    setError(null);
    setCode(Array(CODE_LENGTH).fill(''));
    try {
      await api.auth.sendOtp({
        identifier: route.params.msisdn,
        channel: 'sms',
        tenantId: activeTenant,
      });
      Alert.alert('Code resent', 'A new 6-digit code has been sent.');
    } catch (err: any) {
      Alert.alert('Could not resend', err?.message ?? 'Try again later.');
    }
  }, [resendIn, route.params.msisdn, activeTenant, api]);

  return (
    <KeyboardAvoidingView
      style={styles.root}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <View style={styles.body}>
        <Text style={styles.title}>Enter verification code</Text>
        <Text style={styles.subtitle}>
          We sent a 6-digit code to{'\n'}
          <Text style={styles.identifier}>{route.params.msisdn}</Text>
        </Text>

        <View style={styles.codeRow}>
          {code.map((digit, i) => (
            <TextInput
              key={i}
              ref={(ref) => {
                inputRefs.current[i] = ref;
              }}
              style={[styles.codeBox, error && styles.codeBoxError]}
              value={digit}
              onChangeText={(t) => handleCodeChange(t, i)}
              keyboardType="number-pad"
              maxLength={1}
              selectTextOnFocus
              testID={`otp-input-${i}`}
            />
          ))}
        </View>

        {error && <Text style={styles.errorText}>{error}</Text>}

        <TouchableOpacity
          style={[styles.cta, loading && styles.ctaDisabled]}
          onPress={() => onVerify()}
          disabled={loading}
          testID="otp-submit"
        >
          {loading ? <ActivityIndicator color="#fff" /> : <Text style={styles.ctaText}>Verify</Text>}
        </TouchableOpacity>

        <View style={styles.resendRow}>
          <Text style={styles.resendLabel}>Didn't get a code?</Text>
          {resendIn > 0 ? (
            <Text style={styles.resendTimer}>Resend in {resendIn}s</Text>
          ) : (
            <TouchableOpacity onPress={onResend} testID="otp-resend">
              <Text style={styles.resendButton}>Resend</Text>
            </TouchableOpacity>
          )}
        </View>

        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.back}>
          <Text style={styles.backText}>← Change number</Text>
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: tokens.colors.surface },
  body: { flex: 1, padding: 24, justifyContent: 'center' },
  title: { fontSize: 22, fontWeight: '700', color: tokens.colors.textPrimary, textAlign: 'center' },
  subtitle: {
    fontSize: 14,
    color: tokens.colors.textSecondary,
    textAlign: 'center',
    marginTop: 8,
    marginBottom: 32,
  },
  identifier: { fontWeight: '600', color: tokens.colors.textPrimary },
  codeRow: { flexDirection: 'row', justifyContent: 'center', gap: 8 },
  codeBox: {
    width: 44,
    height: 56,
    borderWidth: 1,
    borderColor: tokens.colors.border,
    borderRadius: 8,
    textAlign: 'center',
    fontSize: 22,
    fontWeight: '600',
    color: tokens.colors.textPrimary,
    backgroundColor: '#fff',
  },
  codeBoxError: { borderColor: tokens.colors.error },
  errorText: {
    color: tokens.colors.error,
    textAlign: 'center',
    marginTop: 12,
    fontSize: 13,
  },
  cta: {
    marginTop: 32,
    backgroundColor: tokens.colors.primary500,
    borderRadius: 8,
    paddingVertical: 14,
    alignItems: 'center',
  },
  ctaDisabled: { opacity: 0.6 },
  ctaText: { color: '#fff', fontSize: 16, fontWeight: '600' },
  resendRow: { flexDirection: 'row', justifyContent: 'center', alignItems: 'center', marginTop: 20, gap: 6 },
  resendLabel: { color: tokens.colors.textSecondary, fontSize: 13 },
  resendTimer: { color: tokens.colors.textSecondary, fontSize: 13, fontWeight: '500' },
  resendButton: { color: tokens.colors.primary500, fontSize: 13, fontWeight: '600' },
  back: { marginTop: 24, alignSelf: 'center' },
  backText: { color: tokens.colors.textSecondary, fontSize: 13 },
});
