/**
 * UniversalInput — The single input primitive. Handles text, password, email, phone, number, OTP, search.
 *
 * Component ID: UniversalInput
 * All styling via ThemeEngine tokens.
 *
 * Manifest config:
 * {
 *   primitive: "UniversalInput",
 *   config: {
 *     type?: "text" | "password" | "email" | "phone" | "number" | "otp" | "search" | "multiline",
 *     label?: string,
 *     placeholder?: string,
 *     valuePath?: "data.field",
 *     onChangePath?: "data.field",
 *     helperText?: string,
 *     errorText?: string,
 *     prefix?: string | { path: "data.prefix" },
 *     suffix?: string | { path: "data.suffix" },
 *     maxLength?: number,
 *     autoCapitalize?: "none" | "sentences" | "words" | "characters",
 *     autoCorrect?: boolean,
 *     keyboardType?: "default" | "email-address" | "numeric" | "phone-pad" | "decimal-pad",
 *     returnKeyType?: "done" | "next" | "search" | "send" | "go",
 *     secureTextEntry?: boolean,
 *     onSubmit?: { type: string; ... },
 *     onChange?: { type: string; ... }
 *   }
 * }
 */

import React from 'react';
import { View, Text, TextInput, StyleSheet, TouchableOpacity } from 'react-native';
import type { PrimitiveProps } from '../ComponentRegistry';
import { useTheme, fontSizePx, ResolvedTheme } from '../../manifest/ThemeEngine';
import { useLocalize } from '../../manifest/Localization';

interface UniversalInputPropsConfig {
  type?: 'text' | 'password' | 'email' | 'phone' | 'number' | 'otp' | 'search' | 'multiline';
  label?: string;
  placeholder?: string;
  valuePath?: string;
  onChangePath?: string;
  helperText?: string;
  errorText?: string;
  prefix?: string | { path: string };
  suffix?: string | { path: string };
  maxLength?: number;
  autoCapitalize?: 'none' | 'sentences' | 'words' | 'characters';
  autoCorrect?: boolean;
  keyboardType?: 'default' | 'email-address' | 'numeric' | 'phone-pad' | 'decimal-pad';
  returnKeyType?: 'done' | 'next' | 'search' | 'send' | 'go';
  secureTextEntry?: boolean;
  onSubmit?: {
    type: string;
    route?: string;
    journeyId?: string;
    params?: Record<string, unknown>;
    analyticsEvent?: string;
  };
  onChange?: {
    type: string;
    route?: string;
    journeyId?: string;
    params?: Record<string, unknown>;
    analyticsEvent?: string;
  };
  testID?: string;
}

type UniversalInputData = Record<string, unknown>;

function getTokenValue(obj: Record<string, unknown> | undefined, path: string): unknown {
  if (!obj) return undefined;
  let current: unknown = obj;
  for (const key of path.split('.')) {
    if (current === null || current === undefined) return undefined;
    current = (current as Record<string, unknown>)[key];
  }
  return current;
}

export function UniversalInput(props: PrimitiveProps): React.JSX.Element {
  const data = props.data as UniversalInputData | undefined;
  const config = (props.props || {}) as UniversalInputPropsConfig;
  const theme = useTheme();
  const { t } = useLocalize();
  const s = universalInputStyles(theme, config);

  const [value, setValue] = React.useState(() => {
    if (config.valuePath && data) {
      return String(getTokenValue(data, config.valuePath) ?? '');
    }
    return '';
  });
  const [showPassword, setShowPassword] = React.useState(false);
  const [focused, setFocused] = React.useState(false);
  const [hasError, setHasError] = React.useState(false);

  const inputType = config.type || 'text';
  const isSecure = (inputType === 'password' || config.secureTextEntry) && !showPassword;

  const handleChangeText = (text: string) => {
    setValue(text);
    if (config.onChange) {
      props.onAction?.({
        event: 'change',
        ...config.onChange,
        params: { ...config.onChange.params, [config.onChangePath ?? 'value']: text },
      });
    }
  };

  const handleSubmit = () => {
    if (config.onSubmit) {
      props.onAction?.({
        event: 'submit',
        ...config.onSubmit,
        params: { ...config.onSubmit.params, value },
      });
    }
  };

  const handleBlur = () => {
    setFocused(false);
    if (config.errorText && value && config.maxLength && value.length > config.maxLength) {
      setHasError(true);
    }
  };

  const prefix = typeof config.prefix === 'string' 
    ? t(config.prefix, { default: config.prefix })
    : config.prefix && 'path' in config.prefix && data
    ? String(getTokenValue(data, config.prefix.path) ?? '')
    : undefined;

  const suffix = typeof config.suffix === 'string'
    ? t(config.suffix, { default: config.suffix })
    : config.suffix && 'path' in config.suffix && data
    ? String(getTokenValue(data, config.suffix.path) ?? '')
    : undefined;

  const label = config.label ? t(config.label, { default: config.label }) : undefined;
  const placeholder = config.placeholder ? t(config.placeholder, { default: config.placeholder }) : undefined;
  const helperText = config.helperText ? t(config.helperText, { default: config.helperText }) : undefined;
  const errorText = config.errorText ? t(config.errorText, { default: config.errorText }) : undefined;

  const showError = hasError || !!errorText || props.error;

  return (
    <View style={[s.container, showError && s.containerError, focused && s.containerFocused]}>
      {label && <Text style={s.label}>{label}</Text>}
      <View style={[s.inputWrapper, showError && s.inputWrapperError, focused && s.inputWrapperFocused]}>
        {prefix && <Text style={s.affix}>{prefix}</Text>}
        <TextInput
          style={[s.input, isSecure && s.inputSecure]}
          value={value}
          onChangeText={handleChangeText}
          onFocus={() => setFocused(true)}
          onBlur={handleBlur}
          onSubmitEditing={handleSubmit}
          placeholder={placeholder}
          placeholderTextColor={theme.colors.onSurfaceVariant ?? theme.colors.textSecondary}
          secureTextEntry={isSecure}
          autoCapitalize={config.autoCapitalize || 'none'}
          autoCorrect={config.autoCorrect ?? false}
          keyboardType={getKeyboardType(inputType, config.keyboardType)}
          returnKeyType={config.returnKeyType || 'done'}
          maxLength={config.maxLength}
          multiline={inputType === 'multiline'}
          textAlign="left"
          testID={config.testID}
        />
        {suffix && <Text style={s.affix}>{suffix}</Text>}
        {(inputType === 'password' || config.secureTextEntry) && (
          <TouchableOpacity onPress={() => setShowPassword(!showPassword)} style={s.eyeButton} accessibilityLabel={showPassword ? 'Hide password' : 'Show password'}>
            <Text style={s.eyeIcon}>{showPassword ? '🙈' : '👁'}</Text>
          </TouchableOpacity>
        )}
        {inputType === 'search' && (
          <TouchableOpacity onPress={() => setValue('')} style={s.clearButton} accessibilityLabel="Clear">
            <Text style={s.clearIcon}>✕</Text>
          </TouchableOpacity>
        )}
      </View>
      {(helperText || showError) && (
        <Text style={[s.helper, showError && s.helperError]}>{errorText || helperText}</Text>
      )}
    </View>
  );
}

function getKeyboardType(inputType: string, override?: string): any {
  if (override) return override;
  switch (inputType) {
    case 'email': return 'email-address';
    case 'phone': return 'phone-pad';
    case 'number':
    case 'otp': return 'numeric';
    case 'search': return 'default';
    default: return 'default';
  }
}

function universalInputStyles(theme: ResolvedTheme, config: UniversalInputPropsConfig) {
  const colors = theme.colors;
  const spacing = theme.spacing;
  const radii = theme.borderRadius;

  const inputType = config.type || 'text';
  const isOtp = inputType === 'otp';

  return StyleSheet.create({
    container: {
      gap: spacing[1],
    },
    containerError: {},
    containerFocused: {},
    label: {
      fontSize: fontSizePx(theme, 'sm'),
      fontWeight: '500',
      color: colors.onSurface ?? colors.textPrimary,
    },
    inputWrapper: {
      flexDirection: 'row',
      alignItems: 'center',
      backgroundColor: colors.surfaceContainerHighest ?? colors.surface,
      borderWidth: 1,
      borderColor: colors.outlineVariant ?? colors.border,
      borderRadius: radii.md,
      paddingHorizontal: spacing[3],
      paddingVertical: spacing[1],
    },
    inputWrapperError: {
      borderColor: colors.error ?? '#EF4444',
      borderWidth: 2,
    },
    inputWrapperFocused: {
      borderColor: colors.primary500 ?? colors.primary,
      borderWidth: 2,
    },
    input: {
      flex: 1,
      fontSize: fontSizePx(theme, 'base'),
      color: colors.onSurface ?? colors.textPrimary,
      paddingVertical: isOtp ? spacing[2] : 0,
      letterSpacing: isOtp ? 8 : 0,
    },
    inputSecure: {},
    affix: {
      fontSize: fontSizePx(theme, 'base'),
      color: colors.onSurfaceVariant ?? colors.textSecondary,
      paddingHorizontal: spacing[1],
    },
    eyeButton: {
      padding: spacing[1],
    },
    eyeIcon: {
      fontSize: fontSizePx(theme, 'base'),
    },
    clearButton: {
      padding: spacing[1],
    },
    clearIcon: {
      fontSize: fontSizePx(theme, 'base'),
      color: colors.onSurfaceVariant ?? colors.textSecondary,
    },
    helper: {
      fontSize: fontSizePx(theme, 'xs'),
      color: colors.onSurfaceVariant ?? colors.textSecondary,
    },
    helperError: {
      color: colors.error ?? '#EF4444',
    },
  });
}