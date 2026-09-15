/**
 * UniversalText — The single text primitive. Replaces ALL Text usages with theme-driven styling.
 *
 * Component ID: UniversalText
 * All styling via ThemeEngine tokens. Zero hardcoded values.
 *
 * Manifest config:
 * {
 *   primitive: "UniversalText",
 *   config: {
 *     variant: "label" | "value" | "title" | "subtitle" | "body" | "caption" | "overline" | "error" | "success" | "warning" | "link",
 *     value: "Static text" | { path: "data.field" } | { template: "Balance: {amount} {currency}" },
 *     format?: "currency" | "number" | "date" | "datetime" | "percentage" | "bytes" | "relative-time",
 *     formatOptions?: { currency?: string },
 *     weight?: "normal" | "medium" | "semibold" | "bold",
 *     align?: "left" | "center" | "right" | "justify",
 *     color?: "primary" | "secondary" | "tertiary" | "error" | "success" | "warning" | "onSurface" | "onBackground" | "onPrimary",
 *     maxLines?: number,
 *     ellipsizeMode?: "head" | "middle" | "tail" | "clip",
 *     selectable?: boolean,
 *     onPress?: { type: string; route?: string; ... }
 *   }
 * }
 */

import React from 'react';
import { Text, View, StyleSheet, TouchableOpacity } from 'react-native';
import type { PrimitiveProps } from '../ComponentRegistry';
import { useTheme, fontSizePx, ResolvedTheme } from '../../manifest/ThemeEngine';
import { useLocalize } from '../../manifest/Localization';
import { formatValue } from '../../utils/formatters';

interface UniversalTextPropsConfig {
  variant?: 'label' | 'value' | 'title' | 'subtitle' | 'body' | 'caption' | 'overline' | 'error' | 'success' | 'warning' | 'link' | 'code';
  value?: string | { path: string } | { template: string };
  format?: 'currency' | 'number' | 'date' | 'datetime' | 'percentage' | 'bytes' | 'relative-time';
  formatOptions?: { currency?: string };
  weight?: 'normal' | 'medium' | 'semibold' | 'bold';
  align?: 'left' | 'center' | 'right' | 'justify';
  color?: 'primary' | 'secondary' | 'tertiary' | 'error' | 'success' | 'warning' | 'onSurface' | 'onBackground' | 'onPrimary';
  maxLines?: number;
  ellipsizeMode?: 'head' | 'middle' | 'tail' | 'clip';
  selectable?: boolean;
  onPress?: {
    type: string;
    route?: string;
    journeyId?: string;
    params?: Record<string, unknown>;
    analyticsEvent?: string;
  };
  testID?: string;
}

type UniversalTextData = Record<string, unknown>;

function getTokenValue(obj: Record<string, unknown> | undefined, path: string): unknown {
  if (!obj) return undefined;
  let current: unknown = obj;
  for (const key of path.split('.')) {
    if (current === null || current === undefined) return undefined;
    current = (current as Record<string, unknown>)[key];
  }
  return current;
}

function resolveTemplate(template: string, data: UniversalTextData | undefined): string {
  if (!data) return template;
  return template.replace(/\{(\w+(?:\.\w+)*)\}/g, (_, key) => {
    const val = getTokenValue(data, key);
    return val !== undefined ? String(val) : '';
  });
}

export function UniversalText(props: PrimitiveProps): React.JSX.Element {
  const data = props.data as UniversalTextData | undefined;
  const config = (props.props || {}) as UniversalTextPropsConfig;
  const theme = useTheme();
  const { t } = useLocalize();
  const s = universalTextStyles(theme, config);

  if (props.isLoading) {
    return <UniversalTextSkeleton config={config} theme={theme} />;
  }

  let textContent: string | undefined;

  if (typeof config.value === 'string') {
    textContent = t(config.value, { default: config.value });
  } else if (config.value && 'path' in config.value) {
    const val = getTokenValue(data, config.value.path);
    if (val !== undefined && val !== null) {
      textContent = formatValue(val, config.format, config.formatOptions);
    }
  } else if (config.value && 'template' in config.value) {
    textContent = resolveTemplate(config.value.template, data);
  }

  if (textContent === undefined || textContent === null) {
    return <View style={s.hidden} />;
  }

  const isPressable = !!config.onPress;
  const style = [
    s.base,
    s[`variant_${config.variant || 'body'}` as keyof typeof s],
    s[`weight_${config.weight || 'normal'}` as keyof typeof s],
    s[`align_${config.align || 'left'}` as keyof typeof s],
    s[`color_${config.color || 'onSurface'}` as keyof typeof s],
    config.maxLines ? ({ maxLines: config.maxLines } as const) : null,
    config.testID ? ({ testID: config.testID } as const) : null,
  ];

  if (isPressable) {
    return (
      <TouchableOpacity
        onPress={() => props.onAction?.({ event: 'tap', ...config.onPress! })}
        activeOpacity={0.7}
        style={s.linkWrapper}
      >
        <Text style={style}>{textContent}</Text>
      </TouchableOpacity>
    );
  }

  return <Text style={style} numberOfLines={config.maxLines} ellipsizeMode={config.ellipsizeMode} selectable={config.selectable}>{textContent}</Text>;
}

function UniversalTextSkeleton({ config, theme }: { config: UniversalTextPropsConfig; theme: ResolvedTheme }) {
  const s = universalTextStyles(theme, config);
  const variant = config.variant || 'body';
  const heights = { label: 16, value: 24, title: 28, subtitle: 18, body: 16, caption: 12, overline: 10, error: 16, success: 16, warning: 16, link: 16, code: 14 };
  return (
    <View style={[s.base, s[`variant_${variant}` as keyof typeof s], s.skeleton, { height: heights[variant as keyof typeof heights] }]} />
  );
}

function universalTextStyles(theme: ResolvedTheme, config: UniversalTextPropsConfig) {
  const colors = theme.colors;

  const variantStyles = {
    label: { fontSize: fontSizePx(theme, 'xs'), fontWeight: '600', letterSpacing: 0.5, textTransform: 'uppercase' },
    value: { fontSize: fontSizePx(theme, '2xl'), fontWeight: '700', lineHeight: fontSizePx(theme, '2xl') * 1.2 },
    title: { fontSize: fontSizePx(theme, 'xl'), fontWeight: '700', lineHeight: fontSizePx(theme, 'xl') * 1.3 },
    subtitle: { fontSize: fontSizePx(theme, 'base'), fontWeight: '500', lineHeight: fontSizePx(theme, 'base') * 1.4 },
    body: { fontSize: fontSizePx(theme, 'base'), fontWeight: '400', lineHeight: fontSizePx(theme, 'base') * 1.5 },
    caption: { fontSize: fontSizePx(theme, 'sm'), fontWeight: '400', lineHeight: fontSizePx(theme, 'sm') * 1.4 },
    overline: { fontSize: fontSizePx(theme, 'xs'), fontWeight: '500', letterSpacing: 1, textTransform: 'uppercase' },
    error: { fontSize: fontSizePx(theme, 'sm'), fontWeight: '500' },
    success: { fontSize: fontSizePx(theme, 'sm'), fontWeight: '500' },
    warning: { fontSize: fontSizePx(theme, 'sm'), fontWeight: '500' },
    link: { fontSize: fontSizePx(theme, 'sm'), fontWeight: '600', textDecorationLine: 'underline' },
    code: { fontSize: fontSizePx(theme, 'sm'), fontFamily: 'monospace', fontWeight: '400' },
  };

  const weightStyles = {
    normal: { fontWeight: '400' },
    medium: { fontWeight: '500' },
    semibold: { fontWeight: '600' },
    bold: { fontWeight: '700' },
  };

  const alignStyles = {
    left: { textAlign: 'left' },
    center: { textAlign: 'center' },
    right: { textAlign: 'right' },
    justify: { textAlign: 'justify' },
  };

  const colorStyles = {
    primary: { color: colors.primary500 ?? colors.primary ?? colors.textPrimary },
    secondary: { color: colors.onSurfaceVariant ?? colors.textSecondary },
    tertiary: { color: colors.onSurfaceVariant ?? colors.textSecondary },
    error: { color: colors.error ?? '#EF4444' },
    success: { color: colors.success ?? '#10B981' },
    warning: { color: colors.warning ?? '#F59E0B' },
    onSurface: { color: colors.onSurface ?? colors.textPrimary },
    onBackground: { color: colors.onBackground ?? colors.textPrimary },
    onPrimary: { color: colors.onPrimary ?? colors.textOnPrimary ?? '#FFFFFF' },
  };

  return StyleSheet.create({
    base: {},
    ...Object.fromEntries(Object.entries(variantStyles).map(([k, v]) => [`variant_${k}`, v])),
    ...Object.fromEntries(Object.entries(weightStyles).map(([k, v]) => [`weight_${k}`, v])),
    ...Object.fromEntries(Object.entries(alignStyles).map(([k, v]) => [`align_${k}`, v])),
    ...Object.fromEntries(Object.entries(colorStyles).map(([k, v]) => [`color_${k}`, v])),
    linkWrapper: {},
    hidden: { display: 'none' },
    skeleton: {
      opacity: 0.5,
      backgroundColor: colors.onSurface + '1A',
      borderRadius: theme.borderRadius.sm,
    },
  });
}