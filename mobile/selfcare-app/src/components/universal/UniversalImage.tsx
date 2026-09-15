/**
 * UniversalImage — The single image primitive. Handles all image display needs.
 *
 * Component ID: UniversalImage
 * All styling via ThemeEngine tokens.
 *
 * Manifest config:
 * {
 *   primitive: "UniversalImage",
 *   config: {
 *     source: { uri: "https://..." } | { path: "data.imageUrl" } | number,
 *     variant?: "avatar" | "thumbnail" | "banner" | "icon" | "full" | "circle" | "rounded" | "square",
 *     aspectRatio?: number,
 *     width?: number | string,
 *     height?: number | string,
 *     resizeMode?: "cover" | "contain" | "stretch" | "repeat" | "center",
 *     placeholder?: "blur" | "color" | "skeleton",
 *     onPress?: { type: string; route?: string; ... },
 *     accessibilityLabel?: string
 *   }
 * }
 */

import React from 'react';
import { View, Image, ImageSourcePropType, StyleSheet, TouchableOpacity } from 'react-native';
import type { PrimitiveProps, Action } from '../ComponentRegistry';
import { useTheme, ResolvedTheme } from '../../manifest/ThemeEngine';
import { useLocalize } from '../../manifest/Localization';
import { UniversalText } from './UniversalText';

interface UniversalImagePropsConfig {
  source?: { uri: string } | { path: string } | number;
  variant?: 'avatar' | 'thumbnail' | 'banner' | 'icon' | 'full' | 'circle' | 'rounded' | 'square';
  aspectRatio?: number;
  width?: number | string;
  height?: number | string;
  resizeMode?: 'cover' | 'contain' | 'stretch' | 'repeat' | 'center';
  placeholder?: 'blur' | 'color' | 'skeleton';
  onPress?: {
    type: string;
    route?: string;
    journeyId?: string;
    params?: Record<string, unknown>;
    analyticsEvent?: string;
  };
  accessibilityLabel?: string;
  testID?: string;
}

type UniversalImageData = Record<string, unknown>;

function getTokenValue(obj: Record<string, unknown> | undefined, path: string): unknown {
  if (!obj) return undefined;
  let current: unknown = obj;
  for (const key of path.split('.')) {
    if (current === null || current === undefined) return undefined;
    current = (current as Record<string, unknown>)[key];
  }
  return current;
}

function resolveSource(config: UniversalImagePropsConfig, data: UniversalImageData | undefined): ImageSourcePropType | undefined {
  if (config.source === undefined) return undefined;
  if (typeof config.source === 'number') return config.source as ImageSourcePropType;
  if ('uri' in config.source) return { uri: config.source.uri };
  if (data) {
    const val = getTokenValue(data, config.source.path);
    if (val) return { uri: String(val) };
  }
  return undefined;
}

export function UniversalImage(props: PrimitiveProps): React.JSX.Element {
  const data = props.data as UniversalImageData | undefined;
  const config = (props.props || {}) as UniversalImagePropsConfig;
  const theme = useTheme();
  const { t } = useLocalize();
  const s = universalImageStyles(theme, config);

  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState(false);

  const source = resolveSource(config, data);

  if (props.isLoading || loading) {
    return <UniversalImageSkeleton config={config} theme={theme} />;
  }

  if (props.error || error || !source) {
    return (
      <View style={[s.container, s.error]}>
        <UniversalText
          data={null}
          props={{ variant: 'caption', value: t('image.loadError', { default: 'Image unavailable' }), color: 'tertiary' }}
        />
      </View>
    );
  }

  const isPressable = !!config.onPress;
  const containerStyle = [
    s.container,
    config.variant ? s[`variant_${config.variant}` as keyof typeof s] : null,
    config.aspectRatio ? { aspectRatio: config.aspectRatio } : null,
    config.width ? { width: config.width } : null,
    config.height ? { height: config.height } : null,
  ];

  const renderImage = () => (
    <Image
      source={source}
      style={s.image}
      resizeMode={config.resizeMode || 'cover'}
      onLoadEnd={() => setLoading(false)}
      onError={() => setError(true)}
    />
  );

  if (isPressable) {
    return (
      <TouchableOpacity
        style={containerStyle}
        onPress={() => props.onAction?.({ event: 'tap', ...config.onPress! } as Action)}
        activeOpacity={0.8}
        accessibilityLabel={config.accessibilityLabel}
        testID={config.testID}
      >
        {renderImage()}
      </TouchableOpacity>
    );
  }

  return (
    <View style={containerStyle} accessibilityLabel={config.accessibilityLabel} testID={config.testID}>
      {renderImage()}
    </View>
  );
}

function UniversalImageSkeleton({ config, theme }: { config: UniversalImagePropsConfig; theme: ResolvedTheme }) {
  const s = universalImageStyles(theme, config);
  return (
    <View
      style={[
        s.container,
        config.variant ? s[`variant_${config.variant}` as keyof typeof s] : null,
        config.aspectRatio ? { aspectRatio: config.aspectRatio } : null,
        s.skeleton,
      ]}
    />
  );
}

function universalImageStyles(theme: ResolvedTheme, config: UniversalImagePropsConfig) {
  const colors = theme.colors;
  const radii = theme.borderRadius;

  const variant = config.variant || 'square';
  const variantSizes = {
    avatar: { width: 40, height: 40, borderRadius: radii.full },
    thumbnail: { width: 80, height: 80, borderRadius: radii.md },
    banner: { width: '100%', height: 160, borderRadius: radii.lg },
    icon: { width: 24, height: 24, borderRadius: radii.sm },
    full: { width: '100%', height: 200, borderRadius: radii.lg },
    circle: { width: 48, height: 48, borderRadius: radii.full },
    rounded: { width: '100%', height: 120, borderRadius: radii.md },
    square: { width: '100%', aspectRatio: 1, borderRadius: radii.md },
  };

  const v = variantSizes[variant as keyof typeof variantSizes] || { width: '100%', aspectRatio: 1, borderRadius: radii.md };

  return StyleSheet.create({
    container: {
      overflow: 'hidden',
      backgroundColor: colors.surfaceContainerHighest ?? colors.surface,
      ...v,
    },
    image: {
      position: 'absolute',
      top: 0,
      left: 0,
      right: 0,
      bottom: 0,
    },
    error: {
      alignItems: 'center',
      justifyContent: 'center',
    },
    skeleton: {
      opacity: 0.5,
    },
    ...Object.fromEntries(
      Object.entries(variantSizes).map(([k, v]) => [`variant_${k}`, { ...v, backgroundColor: colors.surfaceContainerHighest ?? colors.surface }])
    ),
  });
}