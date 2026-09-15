/**
 * UniversalButton — The single button primitive. Replaces ALL button variants.
 *
 * Component ID: UniversalButton
 * All styling via ThemeEngine tokens.
 *
 * Manifest config:
 * {
 *   primitive: "UniversalButton",
 *   config: {
 *     label: "Button text" | { path: "data.label" },
 *     variant?: "primary" | "secondary" | "outline" | "ghost" | "destructive" | "tonal",
 *     size?: "sm" | "md" | "lg" | "xl",
 *     fullWidth?: boolean,
 *     icon?: "leading" | "trailing",
 *     iconName?: string,
 *     loading?: boolean,
 *     disabled?: boolean,
 *     onPress?: { type: string; route?: string; ... }
 *   }
 * }
 */

import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet, ActivityIndicator } from 'react-native';
import type { PrimitiveProps } from '../ComponentRegistry';
import { useTheme, fontSizePx, buttonToken, ResolvedTheme } from '../../manifest/ThemeEngine';
import { useLocalize } from '../../manifest/Localization';

interface UniversalButtonPropsConfig {
  label?: string | { path: string };
  variant?: 'primary' | 'secondary' | 'outline' | 'ghost' | 'destructive' | 'tonal';
  size?: 'sm' | 'md' | 'lg' | 'xl';
  fullWidth?: boolean;
  icon?: 'leading' | 'trailing';
  iconName?: string;
  loading?: boolean;
  disabled?: boolean;
  onPress?:
    | {
        type: string;
        route?: string;
        journeyId?: string;
        params?: Record<string, unknown>;
        analyticsEvent?: string;
      }
    | (() => void);
  testID?: string;
}

type UniversalButtonData = Record<string, unknown>;

function getTokenValue(obj: Record<string, unknown> | undefined, path: string): unknown {
  if (!obj) return undefined;
  let current: unknown = obj;
  for (const key of path.split('.')) {
    if (current === null || current === undefined) return undefined;
    current = (current as Record<string, unknown>)[key];
  }
  return current;
}

export function UniversalButton(props: PrimitiveProps): React.JSX.Element {
  const data = props.data as UniversalButtonData | undefined;
  const config = (props.props || {}) as UniversalButtonPropsConfig;
  const theme = useTheme();
  const { t } = useLocalize();
  const s = universalButtonStyles(theme, config);

  const label = typeof config.label === 'string' 
    ? t(config.label, { default: config.label })
    : config.label && 'path' in config.label
    ? String(getTokenValue(data, config.label.path) ?? '')
    : '';

  const disabled = config.disabled || props.isLoading || config.loading;
  const variant = config.variant || 'primary';
  const size = config.size || 'md';

  if (!label && !config.iconName) {
    return <View style={s.hidden} />;
  }

  const handlePress = () => {
    if (typeof config.onPress === 'function') {
      config.onPress();
      return;
    }
    if (config.onPress) {
      props.onAction?.({ event: 'tap', ...config.onPress });
    }
  };

  return (
    <TouchableOpacity
      style={[
        s.container,
        s[`variant_${variant}` as keyof typeof s],
        s[`size_${size}` as keyof typeof s],
        config.fullWidth && s.fullWidth,
        disabled && s.disabled,
      ]}
      onPress={disabled ? undefined : handlePress}
      activeOpacity={0.8}
      disabled={disabled}
      accessibilityState={{ disabled }}
      testID={config.testID}
    >
      {config.icon === 'leading' && config.iconName && <Text style={s.icon}>{config.iconName}</Text>}
      {config.loading || props.isLoading ? (
        <ActivityIndicator
          color={variant === 'primary' || variant === 'destructive' ? (theme.colors.onPrimary ?? theme.colors.textOnPrimary ?? '#FFFFFF') : (theme.colors.primary500 ?? theme.colors.primary)}
          size="small"
          style={s.spinner}
        />
      ) : (
        <Text style={s.label} numberOfLines={1}>{label}</Text>
      )}
      {config.icon === 'trailing' && config.iconName && <Text style={s.icon}>{config.iconName}</Text>}
    </TouchableOpacity>
  );
}

function universalButtonStyles(theme: ResolvedTheme, config: UniversalButtonPropsConfig) {
  const colors = theme.colors;
  const spacing = theme.spacing;
  const radii = theme.borderRadius;

  const variant = config.variant || 'primary';
  const size = config.size || 'md';

  const sizeTokens = {
    sm: { paddingHorizontal: spacing[2], paddingVertical: spacing[1], fontSize: fontSizePx(theme, 'xs'), minHeight: 32, iconSize: 14 },
    md: { paddingHorizontal: spacing[3], paddingVertical: spacing[2], fontSize: fontSizePx(theme, 'sm'), minHeight: 40, iconSize: 16 },
    lg: { paddingHorizontal: spacing[4], paddingVertical: spacing[2], fontSize: fontSizePx(theme, 'base'), minHeight: 48, iconSize: 18 },
    xl: { paddingHorizontal: spacing[5], paddingVertical: spacing[3], fontSize: fontSizePx(theme, 'lg'), minHeight: 56, iconSize: 20 },
  }[size];

  const variantColors = {
    primary: { bg: buttonToken(theme, 'primary', 'bg') ?? colors.primary500 ?? colors.primary, text: buttonToken(theme, 'primary', 'text') ?? colors.onPrimary ?? colors.textOnPrimary ?? '#FFFFFF', border: 'transparent' },
    secondary: { bg: buttonToken(theme, 'secondary', 'bg') ?? colors.secondaryContainer ?? colors.surfaceVariant, text: buttonToken(theme, 'secondary', 'text') ?? colors.onSecondaryContainer ?? colors.onSurface ?? colors.textPrimary, border: 'transparent' },
    outline: { bg: buttonToken(theme, 'outline', 'bg') ?? 'transparent', text: buttonToken(theme, 'outline', 'text') ?? colors.primary500 ?? colors.primary, border: buttonToken(theme, 'outline', 'border') ?? colors.outline },
    ghost: { bg: 'transparent', text: colors.primary500 ?? colors.primary, border: 'transparent' },
    destructive: { bg: colors.errorContainer ?? '#FFF5F5', text: colors.error ?? '#EF4444', border: colors.error },
    tonal: { bg: colors.primaryContainer ?? (colors.primary500 ?? colors.primary) + '20', text: colors.onPrimaryContainer ?? colors.primary500 ?? colors.primary, border: 'transparent' },
  }[variant];

  return StyleSheet.create({
    container: {
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'center',
      gap: spacing[1],
      borderRadius: radii.md,
      borderWidth: variant === 'outline' ? 1 : 0,
      borderColor: variantColors.border,
      backgroundColor: variantColors.bg,
      minHeight: sizeTokens.minHeight,
      paddingHorizontal: sizeTokens.paddingHorizontal,
      paddingVertical: sizeTokens.paddingVertical,
      ...theme.elevation.sm,
    },
    fullWidth: { width: '100%' },
    disabled: { opacity: 0.4 },
    label: {
      color: variantColors.text,
      fontSize: sizeTokens.fontSize,
      fontWeight: '600',
      lineHeight: sizeTokens.fontSize * 1.3,
    },
    icon: {
      fontSize: sizeTokens.iconSize,
    },
    spinner: {
      marginHorizontal: spacing[1],
    },
    hidden: { display: 'none' },
    variant_primary: {},
    variant_secondary: {},
    variant_outline: {},
    variant_ghost: {},
    variant_destructive: {},
    variant_tonal: {},
    size_sm: {},
    size_md: {},
    size_lg: {},
    size_xl: {},
  });
}