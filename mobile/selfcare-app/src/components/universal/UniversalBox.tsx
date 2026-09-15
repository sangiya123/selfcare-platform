/**
 * UniversalBox — The single container primitive. Replaces ALL card/container/surface wrappers.
 *
 * Component ID: UniversalBox
 * All styling via ThemeEngine tokens. Zero hardcoded values.
 *
 * Manifest config (via component catalog entry):
 * {
 *   componentId: "BalanceCard",
 *   primitive: "UniversalBox",
 *   config: {
 *     renderType: "card",
 *     variant: "primary",
 *     elevation: "md",
 *     borderRadius: "lg",
 *     padding: "md",
 *     children: [
 *       { primitive: "UniversalText", config: { variant: "label", valuePath: "balance.label" }},
 *       { primitive: "UniversalText", config: { variant: "value", valuePath: "balance.amount", format: "currency" }},
 *       { primitive: "UniversalButton", config: { label: "Recharge", action: {...}, variant: "primary" }}
 *     ]
 *   }
 * }
 */

import React from 'react';
import { View, StyleSheet, Pressable } from 'react-native';
import type { PrimitiveProps, Action } from '../ComponentRegistry';
import { useTheme, buttonToken, buttonGradient, ResolvedTheme } from '../../manifest/ThemeEngine';
import { useLocalize } from '../../manifest/Localization';
import { UniversalText } from './UniversalText';
import { UniversalImage } from './UniversalImage';
import { UniversalButton } from './UniversalButton';
import { UniversalInput } from './UniversalInput';
import { UniversalChart } from './UniversalChart';

interface UniversalBoxPropsConfig {
  renderType?: 'card' | 'surface' | 'overlay' | 'sheet' | 'divider' | 'spacer';
  variant?: 'primary' | 'secondary' | 'accent' | 'surface' | 'outline' | 'gradient' | 'transparent';
  elevation?: 'none' | 'sm' | 'md' | 'lg' | 'xl';
  borderRadius?: 'none' | 'sm' | 'md' | 'lg' | 'xl' | 'full' | number;
  padding?: 'none' | 'sm' | 'md' | 'lg' | 'xl' | number;
  margin?: 'none' | 'sm' | 'md' | 'lg' | 'xl' | number;
  width?: string | number;
  height?: string | number;
  minHeight?: number;
  maxHeight?: number;
  flex?: number;
  flexDirection?: 'row' | 'column' | 'row-reverse' | 'column-reverse';
  alignItems?: 'flex-start' | 'center' | 'flex-end' | 'stretch' | 'baseline';
  justifyContent?: 'flex-start' | 'center' | 'flex-end' | 'space-between' | 'space-around' | 'space-evenly';
  gap?: 'none' | 'sm' | 'md' | 'lg' | 'xl' | number;
  overflow?: 'visible' | 'hidden' | 'scroll';
  position?: 'relative' | 'absolute';
  top?: number;
  bottom?: number;
  left?: number;
  right?: number;
  zIndex?: number;
  opacity?: number;
  backgroundImage?: string;
  gradientColors?: string[];
  gradientDirection?: 'vertical' | 'horizontal' | 'diagonal';
  onPress?: {
    type: string;
    route?: string;
    journeyId?: string;
    params?: Record<string, unknown>;
    analyticsEvent?: string;
  };
  children?: UniversalChildConfig[];
}

interface UniversalChildConfig {
  primitive: 'UniversalBox' | 'UniversalText' | 'UniversalImage' | 'UniversalButton' | 'UniversalInput' | 'UniversalChart' | 'UniversalList' | 'UniversalGrid';
  config: Record<string, unknown>;
  condition?: string;
}

type UniversalBoxData = Record<string, unknown>;

function getTokenValue(obj: Record<string, unknown> | undefined, path: string): unknown {
  if (!obj) return undefined;
  let current: unknown = obj;
  for (const key of path.split('.')) {
    if (current === null || current === undefined) return undefined;
    current = (current as Record<string, unknown>)[key];
  }
  return current;
}

export function UniversalBox(props: PrimitiveProps): React.JSX.Element {
  const data = props.data as UniversalBoxData | undefined;
  const config = (props.props || {}) as UniversalBoxPropsConfig;
  const theme = useTheme();
  const { t } = useLocalize();
  const s = universalBoxStyles(theme, config);

  if (props.isLoading) {
    return <UniversalBoxSkeleton config={config} theme={theme} />;
  }

  if (props.error) {
    return (
      <View style={[s.container, s.errorContainer]}>
        <UniversalText data={null} props={{ variant: 'error', value: t('generic.loadError', { default: "Couldn't load" }) }} />
        {props.retryable && (
          <UniversalButton
            data={null}
            props={{ label: t('generic.retry', { default: 'Try again' }), variant: 'primary', onPress: props.onRetry }}
          />
        )}
      </View>
    );
  }

  if (!data && !config.children?.length) {
    return <View />;
  }

  const renderChildren = () => {
    if (!config.children?.length) return null;
    
    return config.children
      .filter(child => !child.condition || !!getTokenValue(data || {}, child.condition))
      .map((child, index) => renderPrimitive(child, index, data, theme, t, props.onAction));
  };

  const containerStyle = [
    s.container,
    config.renderType === 'card' && s.card,
    config.renderType === 'surface' && s.surface,
    config.renderType === 'overlay' && s.overlay,
    config.renderType === 'sheet' && s.sheet,
    config.renderType === 'divider' && s.divider,
    config.renderType === 'spacer' && s.spacer,
  ];

  const pressHandler = config.onPress
    ? () => props.onAction?.({ event: 'tap', ...config.onPress } as Action)
    : undefined;

  const Container = pressHandler ? Pressable : View;
  const containerProps = pressHandler ? { onPress: pressHandler, activeOpacity: 0.9 } : {};

  return (
    <Container style={containerStyle} {...containerProps} accessibilityRole={pressHandler ? 'button' : undefined}>
      {renderChildren()}
    </Container>
  );
}

function renderPrimitive(
  child: UniversalChildConfig,
  index: number,
  data: UniversalBoxData | undefined,
  theme: ResolvedTheme,
  t: ReturnType<typeof useLocalize>['t'],
  onAction: PrimitiveProps['onAction']
) {
  const childProps = { ...child.config, data } as PrimitiveProps;
  const common = { key: index, ...childProps, onAction };

  switch (child.primitive) {
    case 'UniversalText':
      return <UniversalText {...common} />;
    case 'UniversalImage':
      return <UniversalImage {...common} />;
    case 'UniversalButton':
      return <UniversalButton {...common} />;
    case 'UniversalInput':
      return <UniversalInput {...common} />;
    case 'UniversalBox':
      return <UniversalBox {...common} />;
    case 'UniversalChart':
      return <UniversalChart {...common} />;
    default:
      return null;
  }
}

function UniversalBoxSkeleton({ config, theme }: { config: UniversalBoxPropsConfig; theme: ResolvedTheme }) {
  const s = universalBoxStyles(theme, config);
  return (
    <View style={[s.container, s.skeleton]}>
      {config.children?.map((_, i) => (
        <View key={i} style={s.skeletonBlock} />
      ))}
    </View>
  );
}

function universalBoxStyles(theme: ResolvedTheme, config: UniversalBoxPropsConfig) {
  const colors = theme.colors;
  const spacing = theme.spacing;
  const radii = theme.borderRadius;

  const variant = config.variant || 'surface';
  const elevation = config.elevation || 'md';
  const borderRadius = typeof config.borderRadius === 'number' ? config.borderRadius : radii[config.borderRadius || 'md'];
  const padding = typeof config.padding === 'number' ? config.padding : spacing[config.padding || 'md'];
  const margin = typeof config.margin === 'number' ? config.margin : spacing[config.margin || 'none'];
  const gap = typeof config.gap === 'number' ? config.gap : spacing[config.gap || 'none'];
  const gradientStart =
    (Array.isArray(config.gradientColors) && config.gradientColors.length)
      ? config.gradientColors[0]
      : buttonGradient(theme, 'primary')?.[0];

  const variantColorMap: Record<string, { bg: string; border: string }> = {
    primary: { bg: buttonToken(theme, 'primary', 'bg') ?? colors.primary500 ?? colors.primary, border: 'transparent' },
    secondary: { bg: buttonToken(theme, 'secondary', 'bg') ?? colors.secondaryContainer ?? colors.surfaceVariant, border: buttonToken(theme, 'secondary', 'border') ?? colors.outlineVariant },
    accent: { bg: colors.tertiaryContainer ?? colors.surfaceVariant, border: colors.outlineVariant },
    surface: { bg: colors.surfaceContainerHighest ?? colors.surface, border: colors.outlineVariant },
    outline: { bg: 'transparent', border: buttonToken(theme, 'outline', 'border') ?? colors.outline },
    gradient: { bg: gradientStart ?? colors.primary500 ?? colors.primary, border: 'transparent' },
    transparent: { bg: 'transparent', border: 'transparent' },
  };
  const variantColors = variantColorMap[variant];

  const flexDirection = config.flexDirection || 'column';
  const alignItems = config.alignItems || 'stretch';
  const justifyContent = config.justifyContent || 'flex-start';

  return StyleSheet.create({
    container: {
      backgroundColor: variantColors.bg,
      borderWidth: variant === 'outline' ? 1 : 0,
      borderColor: variantColors.border,
      borderRadius,
      padding,
      margin,
      gap,
      flexDirection,
      alignItems,
      justifyContent,
      width: config.width,
      height: config.height,
      minHeight: config.minHeight,
      maxHeight: config.maxHeight,
      flex: config.flex,
      overflow: config.overflow || 'visible',
      position: config.position || 'relative',
      top: config.top,
      bottom: config.bottom,
      left: config.left,
      right: config.right,
      zIndex: config.zIndex,
      opacity: config.opacity ?? 1,
      ...theme.elevation[elevation],
    },
    card: {
      // card-specific overrides if needed
    },
    surface: {},
    overlay: {
      position: 'absolute',
      top: 0,
      left: 0,
      right: 0,
      bottom: 0,
      justifyContent: 'center',
      alignItems: 'center',
    },
    sheet: {
      position: 'absolute',
      left: 0,
      right: 0,
      bottom: 0,
      borderTopLeftRadius: radii.xl,
      borderTopRightRadius: radii.xl,
      maxHeight: '80%',
    },
    divider: {
      height: 1,
      backgroundColor: colors.outlineVariant + '4D',
      padding: 0,
      margin: 0,
    },
    spacer: {
      backgroundColor: 'transparent',
      height: padding,
    },
    errorContainer: {
      alignItems: 'center',
      justifyContent: 'center',
      padding: spacing[3],
    },
    skeleton: {
      opacity: 0.5,
    },
    skeletonBlock: {
      height: 16,
      backgroundColor: colors.onSurface + '1A',
      borderRadius: radii.sm,
      marginVertical: spacing[1],
    },
  });
}