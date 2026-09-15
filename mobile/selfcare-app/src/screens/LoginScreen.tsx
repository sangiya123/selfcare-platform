/**
 * LoginScreen — captures the user's phone number / customer ID and asks for an
 * OTP through the CONFIGURED auth SDK (endpoints from manifest services.auth).
 *
 * The journey is admin-authored (manifest auth.login):
 *   - mode "inApp"  → identifier + OTP delivery channel chips (channels from
 *     manifest auth.methods.otp.options.channels), then navigate to OtpScreen;
 *   - mode "external" → launch the operator's web sign-in via deep link return.
 *
 * All copy and visual metrics are config-driven (i18n + theme tokens).
 */
import React, { useState, useCallback } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  Linking,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useTenant } from '../hooks/useTenant';
import { useLocalize } from '../manifest/Localization';
import { useTheme, fontSizePx, readableOn, ResolvedTheme } from '../manifest/ThemeEngine';
import { resolveLoginPlan } from '../config/loginFlow';
import type { SelfcareSDK } from '../config/ConfigSDK';
import type { RootStackParamList } from '../../App';

type Nav = NativeStackNavigationProp<RootStackParamList, 'Login'>;

function getSdk(): SelfcareSDK | undefined {
  return (globalThis as unknown as { __SELFCARE_SDK__?: SelfcareSDK }).__SELFCARE_SDK__;
}

export function LoginScreen(): React.JSX.Element {
  const navigation = useNavigation<Nav>();
  const { industryPack } = useTenant();
  const sdk = getSdk();
  const theme = useTheme();
  const { t } = useLocalize();

  // Config-driven login journey (manifest auth.login) — the app never decides
  // whether login is in-app OTP or the operator's external web flow.
  const loginPlan = resolveLoginPlan(sdk?.getManifest() ?? null);
  const externalMode =
    loginPlan.configured &&
    loginPlan.flow?.mode === 'external' &&
    Boolean(loginPlan.external?.url);

  // Delivery channels from the configured OTP method (manifest), never hardcoded.
  const otpMethod = loginPlan.enabledMethods.find((m) => m.method === 'otp');
  const configuredChannels = (otpMethod?.options?.channels ?? []).filter((c) =>
    ['sms', 'whatsapp', 'email'].includes(c)
  ) as Array<'sms' | 'whatsapp' | 'email'>;
  const channels = configuredChannels.length ? configuredChannels : (['sms', 'whatsapp'] as const);

  const [identifier, setIdentifier] = useState('');
  const [loading, setLoading] = useState(false);
  const [deliveryChannel, setDeliveryChannel] = useState<'sms' | 'whatsapp' | 'email'>(
    channels[0] ?? 'sms'
  );

  const isTelco = industryPack === 'telco' || industryPack === null;
  const idLabel = isTelco
    ? t('login.identifier.telco', { default: 'Mobile number' })
    : t('login.identifier.insurance', { default: 'Customer / policy number' });
  const idPlaceholder = isTelco ? '+94 77 123 4567' : 'e.g. AIA-12345678';

  const onSendOtp = useCallback(async () => {
    if (!identifier.trim()) {
      Alert.alert(
        t('login.requiredTitle', { default: 'Required' }),
        t('login.requiredBody', {
          default: isTelco ? 'Please enter your mobile number.' : 'Please enter your customer number.',
        })
      );
      return;
    }
    if (!sdk) {
      Alert.alert(t('login.notReady', { default: 'Sign-in is not ready yet.' }));
      return;
    }
    setLoading(true);
    try {
      const result = await sdk.auth.sendOtp(
        identifier.trim(),
        deliveryChannel.toUpperCase() as 'SMS' | 'WHATSAPP' | 'EMAIL'
      );
      if (result.success) {
        navigation.navigate('Otp', { msisdn: identifier.trim() });
      } else {
        Alert.alert(
          t('login.sendFailedTitle', { default: 'Could not send code' }),
          t('login.sendFailedBody', { default: 'Please try again.' })
        );
      }
    } catch (err: any) {
      Alert.alert(
        t('login.errorTitle', { default: 'Error' }),
        err?.message ?? t('login.networkError', { default: 'Network error' })
      );
    } finally {
      setLoading(false);
    }
  }, [identifier, deliveryChannel, isTelco, navigation, sdk, t]);

  const onExternalLogin = useCallback(() => {
    const url = loginPlan.external?.url;
    if (url) {
      Linking.openURL(url).catch(() => {
        Alert.alert(
          t('login.externalOpenFailed', { default: 'Could not open the sign-in page.' })
        );
      });
    }
  }, [loginPlan, t]);

  const s = loginStyles(theme);
  const onPrimary = readableOn(theme.colors.primary500 ?? theme.colors.primary);

  return (
    <KeyboardAvoidingView
      style={s.root}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView contentContainerStyle={s.scroll} keyboardShouldPersistTaps="handled">
        <View style={s.brand}>
          <Text style={s.brandName}>{t('brand.name', { default: 'selfcare' })}</Text>
          <Text style={s.brandTagline}>{t('brand.tagline', { default: 'Selfcare' })}</Text>
        </View>

        {externalMode ? (
          <View style={s.card}>
            <Text style={s.title}>{t('login.external.title', { default: 'Sign in' })}</Text>
            <Text style={s.subtitle}>
              {t('login.external.hint', {
                default: 'You will be taken to the operator portal to verify your number, then returned to this app.',
              })}
            </Text>
            <TouchableOpacity
              style={[s.cta, loading && s.ctaDisabled]}
              onPress={onExternalLogin}
              disabled={loading}
              testID="login-external-submit"
            >
              <Text style={s.ctaText}>
                {t('login.external.cta', { default: 'Continue with operator portal' })}
              </Text>
            </TouchableOpacity>
          </View>
        ) : (
          <View style={s.card}>
            <Text style={s.title}>
              {t('login.title', { default: 'Sign in' })}
            </Text>
            <Text style={s.subtitle}>
              {isTelco
                ? t('login.subtitle.telco', {
                    default: 'Enter your mobile number to receive a verification code.',
                  })
                : t('login.subtitle.insurance', {
                    default: 'Enter your customer ID to receive a verification code.',
                  })}
            </Text>

            <Text style={s.label}>{idLabel}</Text>
            <TextInput
              style={s.input}
              value={identifier}
              onChangeText={setIdentifier}
              placeholder={idPlaceholder}
              keyboardType={isTelco ? 'phone-pad' : 'default'}
              autoCorrect={false}
              autoCapitalize="none"
              testID="login-identifier"
            />

            <Text style={s.label}>{t('login.channelLabel', { default: 'Send code via' })}</Text>
            <View style={s.channelRow}>
              {channels.map((c) => (
                <TouchableOpacity
                  key={c}
                  style={[s.channelChip, deliveryChannel === c && s.channelChipActive]}
                  onPress={() => setDeliveryChannel(c)}
                  testID={`channel-${c}`}
                >
                  <Text
                    style={[s.channelChipText, deliveryChannel === c && s.channelChipTextActive]}
                  >
                    {c === 'sms'
                      ? t('login.channel.sms', { default: 'SMS' })
                      : c === 'whatsapp'
                        ? t('login.channel.whatsapp', { default: 'WhatsApp' })
                        : t('login.channel.email', { default: 'Email' })}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>

            <TouchableOpacity
              style={[s.cta, loading && s.ctaDisabled]}
              onPress={onSendOtp}
              disabled={loading}
              testID="login-submit"
            >
              {loading ? (
                <ActivityIndicator color={onPrimary} />
              ) : (
                <Text style={s.ctaText}>{t('login.sendCode', { default: 'Send code' })}</Text>
              )}
            </TouchableOpacity>
          </View>
        )}

        <Text style={s.footer}>
          {t('login.agreement', {
            default: 'By continuing you agree to our Terms of Service and Privacy Policy.',
          })}
        </Text>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

function loginStyles(theme: ResolvedTheme): ReturnType<typeof StyleSheet.create> {
  const colors = theme.colors;
  const layout = theme.layout;
  const primary = colors.primary500 ?? colors.primary;
  return StyleSheet.create({
    root: { flex: 1, backgroundColor: colors.surface },
    scroll: { flexGrow: 1, padding: layout.pagePadding, justifyContent: 'center' },
    brand: { alignItems: 'center', marginBottom: 32 },
    brandName: {
      fontSize: fontSizePx(theme, '4xl'),
      fontWeight: '700',
      color: colors.primary700 ?? primary,
      letterSpacing: 4,
    },
    brandTagline: {
      fontSize: fontSizePx(theme, 'lg'),
      fontWeight: '500',
      color: primary,
      marginTop: 4,
    },
    card: {
      backgroundColor: colors.surfaceStrong ?? colors.surfaceSubtle ?? '#ffffff',
      borderRadius: layout.radius,
      padding: layout.cardPadding,
      shadowColor: '#000000',
      shadowOpacity: 0.06,
      shadowRadius: 12,
      elevation: 2,
    },
    title: {
      fontSize: fontSizePx(theme, '2xl'),
      fontWeight: '700',
      color: colors.textPrimary,
    },
    subtitle: {
      fontSize: fontSizePx(theme, 'sm'),
      color: colors.textSecondary,
      marginTop: 4,
      marginBottom: 20,
    },
    label: {
      fontSize: fontSizePx(theme, 'sm'),
      fontWeight: '500',
      color: colors.textPrimary,
      marginTop: 16,
      marginBottom: 6,
    },
    input: {
      borderWidth: layout.hairlinePx,
      borderColor: colors.border,
      borderRadius: layout.radius,
      paddingHorizontal: 12,
      paddingVertical: 12,
      fontSize: fontSizePx(theme, 'base'),
      color: colors.textPrimary,
    },
    channelRow: { flexDirection: 'row', gap: 8, marginTop: 8 },
    channelChip: {
      paddingHorizontal: 14,
      paddingVertical: 8,
      borderWidth: layout.hairlinePx,
      borderColor: colors.border,
      borderRadius: layout.radius * 1.5,
    },
    channelChipActive: { backgroundColor: primary, borderColor: primary },
    channelChipText: { fontSize: fontSizePx(theme, 'sm'), color: colors.textPrimary },
    channelChipTextActive: { color: readableOn(primary), fontWeight: '600' },
    cta: {
      marginTop: 24,
      backgroundColor: primary,
      borderRadius: layout.radius,
      paddingVertical: 14,
      alignItems: 'center',
    },
    ctaDisabled: { opacity: 0.6 },
    ctaText: {
      color: readableOn(primary),
      fontSize: fontSizePx(theme, 'base'),
      fontWeight: '600',
    },
    footer: {
      textAlign: 'center',
      fontSize: fontSizePx(theme, 'xs'),
      color: colors.textSecondary,
      marginTop: 24,
    },
  });
}