/**
 * OtpScreen — user enters the verification code they received.
 *
 * Submits via the configured auth SDK (auth.verifyOtp(); on success the SDK
 * transitions to authenticated and App.tsx swaps to Main, and OTP resend uses
 * the configured delivery channel). OTP length is admin-authored (manifest
 * auth.methods.otp.options.length). All copy/colors/sizes are config-driven.
 */
import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
  KeyboardAvoidingView,
  Platform,
  StyleSheet,
} from 'react-native';
import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useLocalize } from '../manifest/Localization';
import { useTheme, fontSizePx, readableOn, ResolvedTheme } from '../manifest/ThemeEngine';
import { resolveLoginPlan } from '../config/loginFlow';
import type { SelfcareSDK } from '../config/ConfigSDK';
import type { RootStackParamList } from '../../App';

type Nav = NativeStackNavigationProp<RootStackParamList, 'Otp'>;
type R = RouteProp<RootStackParamList, 'Otp'>;

const RESEND_SECONDS = 30;
const DEFAULT_CODE_LENGTH = 6;

function getSdk(): SelfcareSDK | undefined {
  return (globalThis as unknown as { __SELFCARE_SDK__?: SelfcareSDK }).__SELFCARE_SDK__;
}

export function OtpScreen(): React.JSX.Element {
  const navigation = useNavigation<Nav>();
  const route = useRoute<R>();
  const sdk = getSdk();
  const theme = useTheme();
  const { t } = useLocalize();

  // OTP length + resend channel are admin-authored (manifest auth config),
  // never hardcoded in the app.
  const loginPlan = resolveLoginPlan(sdk?.getManifest() ?? null);
  const otpMethod = loginPlan.enabledMethods.find((m) => m.method === 'otp');
  const codeLength = otpMethod?.options?.length ?? DEFAULT_CODE_LENGTH;
  const resendChannel = ((otpMethod?.options?.channels ?? ['sms'])[0] ?? 'sms').toUpperCase() as
    | 'SMS'
    | 'WHATSAPP'
    | 'EMAIL';

  const [code, setCode] = useState<string[]>(() => Array(codeLength).fill(''));
  const [loading, setLoading] = useState(false);
  const [resendIn, setResendIn] = useState(RESEND_SECONDS);
  const [error, setError] = useState<string | null>(null);
  const inputRefs = useRef<Array<{ focus?: () => void } | null>>([]);

  useEffect(() => {
    if (resendIn <= 0) return;
    const timer = setTimeout(() => setResendIn(resendIn - 1), 1000);
    return () => clearTimeout(timer);
  }, [resendIn]);

  useEffect(() => {
    inputRefs.current[0]?.focus?.();
  }, []);

  const handleCodeChange = useCallback(
    (text: string, index: number) => {
      const digit = text.replace(/\D/g, '').slice(-1);
      const next = [...code];
      next[index] = digit;
      setCode(next);
      setError(null);
      if (digit && index < codeLength - 1) {
        inputRefs.current[index + 1]?.focus?.();
      }
      if (index === codeLength - 1 && digit) {
        const full = next.join('');
        if (full.length === codeLength) {
          onVerify(full);
        }
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [code, codeLength]
  );

  const onVerify = useCallback(
    async (fullCode?: string) => {
      const final = fullCode ?? code.join('');
      if (final.length !== codeLength) {
        setError(
          t('otp.incomplete', {
            default: 'Please enter the full {length}-digit code.',
            params: { length: String(codeLength) },
          })
        );
        return;
      }
      if (!sdk) {
        setError(t('otp.notReady', { default: 'Sign-in is not ready yet.' }));
        return;
      }
      setLoading(true);
      try {
        const result = await sdk.auth.verifyOtp(route.params.msisdn, final);
        if (result.success) {
          // App.tsx swaps to Main when the SDK emits 'authenticated'.
        } else {
          setError(t('otp.invalid', { default: 'Invalid code' }));
          setCode(Array(codeLength).fill(''));
          inputRefs.current[0]?.focus?.();
        }
      } catch (err: any) {
        setError(err?.message ?? t('otp.networkError', { default: 'Network error' }));
      } finally {
        setLoading(false);
      }
    },
    [code, codeLength, route.params.msisdn, sdk, t]
  );

  const onResend = useCallback(async () => {
    if (resendIn > 0 || !sdk) return;
    setResendIn(RESEND_SECONDS);
    setError(null);
    setCode(Array(codeLength).fill(''));
    try {
      await sdk.auth.sendOtp(route.params.msisdn, resendChannel);
      Alert.alert(
        t('otp.resentTitle', { default: 'Code resent' }),
        t('otp.resentBody', { default: 'A new code has been sent.' })
      );
    } catch (err: any) {
      Alert.alert(
        t('otp.resendFailedTitle', { default: 'Could not resend' }),
        err?.message ?? t('otp.resendFailedBody', { default: 'Try again later.' })
      );
    }
  }, [resendIn, route.params.msisdn, sdk, t, codeLength, resendChannel]);

  const s = otpStyles(theme);
  const onPrimary = readableOn(theme.colors.primary500 ?? theme.colors.primary);

  return (
    <KeyboardAvoidingView
      style={s.root}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <View style={s.body}>
        <Text style={s.title}>{t('otp.title', { default: 'Enter verification code' })}</Text>
        <Text style={s.subtitle}>
          {t('otp.subtitle', {
            default: 'We sent a code to the number you provided.',
          })}
          {'\n'}
          <Text style={s.identifier}>{route.params.msisdn}</Text>
        </Text>

        <View style={s.codeRow}>
          {code.map((digit, i) => (
            <TextInput
              key={i}
              ref={(ref) => {
                inputRefs.current[i] = ref;
              }}
              style={[s.codeBox, error && s.codeBoxError]}
              value={digit}
              onChangeText={(value) => handleCodeChange(value, i)}
              keyboardType="number-pad"
              maxLength={1}
              selectTextOnFocus
              testID={`otp-input-${i}`}
            />
          ))}
        </View>

        {error ? <Text style={s.errorText}>{error}</Text> : null}

        <TouchableOpacity
          style={[s.cta, loading && s.ctaDisabled]}
          onPress={() => onVerify()}
          disabled={loading}
          testID="otp-submit"
        >
          {loading ? (
            <ActivityIndicator color={onPrimary} />
          ) : (
            <Text style={s.ctaText}>{t('otp.verify', { default: 'Verify' })}</Text>
          )}
        </TouchableOpacity>

        <View style={s.resendRow}>
          <Text style={s.resendLabel}>{t('otp.resendHint', { default: "Didn't get a code?" })}</Text>
          {resendIn > 0 ? (
            <Text style={s.resendTimer}>
                {t('otp.resendIn', {
                  default: 'Resend in {seconds}s',
                  params: { seconds: String(resendIn) },
                })}
              </Text>
          ) : (
            <TouchableOpacity onPress={onResend} testID="otp-resend">
              <Text style={s.resendButton}>{t('otp.resend', { default: 'Resend' })}</Text>
            </TouchableOpacity>
          )}
        </View>

        <TouchableOpacity onPress={() => navigation.goBack()} style={s.back}>
          <Text style={s.backText}>{t('otp.changeNumber', { default: '← Change number' })}</Text>
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
  );
}

function otpStyles(theme: ResolvedTheme): ReturnType<typeof StyleSheet.create> {
  const colors = theme.colors;
  const layout = theme.layout;
  const base = colors.surface ?? '#f5f5f5';
  return StyleSheet.create({
    root: { flex: 1, backgroundColor: colors.surface },
    body: { flex: 1, padding: layout.pagePadding, justifyContent: 'center' },
    title: {
      fontSize: fontSizePx(theme, '2xl'),
      fontWeight: '700',
      color: colors.textPrimary,
      textAlign: 'center',
    },
    subtitle: {
      fontSize: fontSizePx(theme, 'sm'),
      color: colors.textSecondary,
      textAlign: 'center',
      marginTop: 8,
      marginBottom: 32,
    },
    identifier: { fontWeight: '600', color: colors.textPrimary },
    codeRow: { flexDirection: 'row', justifyContent: 'center', gap: 8 },
    codeBox: {
      width: layout.minTouchTarget,
      height: layout.minTouchTarget + 12,
      borderWidth: layout.hairlinePx,
      borderColor: colors.border,
      borderRadius: layout.radius,
      textAlign: 'center',
      fontSize: fontSizePx(theme, '2xl'),
      fontWeight: '600',
      color: colors.textPrimary,
      backgroundColor: colors.surfaceStrong ?? base,
    },
    codeBoxError: { borderColor: colors.error },
    errorText: {
      color: colors.error,
      textAlign: 'center',
      marginTop: 12,
      fontSize: fontSizePx(theme, 'sm'),
    },
    cta: {
      marginTop: 32,
      backgroundColor: colors.primary500 ?? colors.primary,
      borderRadius: layout.radius,
      paddingVertical: 14,
      alignItems: 'center',
    },
    ctaDisabled: { opacity: 0.6 },
    ctaText: {
      color: readableOn(colors.primary500 ?? colors.primary),
      fontSize: fontSizePx(theme, 'base'),
      fontWeight: '600',
    },
    resendRow: {
      flexDirection: 'row',
      justifyContent: 'center',
      alignItems: 'center',
      marginTop: 20,
    },
    resendLabel: { color: colors.textSecondary, fontSize: fontSizePx(theme, 'sm') },
    resendTimer: {
      color: colors.textSecondary,
      fontSize: fontSizePx(theme, 'sm'),
      fontWeight: '500',
    },
    resendButton: {
      color: colors.primary500 ?? colors.primary,
      fontSize: fontSizePx(theme, 'sm'),
      fontWeight: '600',
    },
    back: { marginTop: 24, alignSelf: 'center' },
    backText: { color: colors.textSecondary, fontSize: fontSizePx(theme, 'sm') },
  });
}