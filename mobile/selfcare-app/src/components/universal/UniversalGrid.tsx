/**
 * UniversalGrid — Grid layout with child widget config. Replaces ActionGrid, QuickActionsGrid, etc.
 *
 * Component ID: UniversalGrid
 * All styling via ThemeEngine tokens.
 *
 * Manifest config:
 * {
 *   primitive: "UniversalGrid",
 *   config: {
 *     dataSource: "quickActions",
 *     item: { primitive: "UniversalBox", config: { renderType: "card", children: [...] }},
 *     columns: 4,
 *     gap: "md",
 *     itemAspectRatio?: number
 *   }
 * }
 */

import React from 'react';
import { View, StyleSheet, ScrollView } from 'react-native';
import type { PrimitiveProps } from '../ComponentRegistry';
import { useTheme, ResolvedTheme } from '../../manifest/ThemeEngine';
import { useLocalize } from '../../manifest/Localization';
import { UniversalBox } from './UniversalBox';
import { UniversalText } from './UniversalText';

interface UniversalGridItemConfig {
  primitive: string;
  config: Record<string, unknown>;
}

interface UniversalGridPropsConfig {
  dataSource?: string;
  item?: UniversalGridItemConfig;
  columns?: 2 | 3 | 4 | 5;
  gap?: 'none' | 'sm' | 'md' | 'lg' | 'xl' | number;
  itemAspectRatio?: number;
  scrollable?: boolean;
  maxItems?: number;
}

type UniversalGridData = Record<string, unknown>[];

export function UniversalGrid(props: PrimitiveProps): React.JSX.Element {
  const data = props.data as UniversalGridData | undefined;
  const config = (props.props || {}) as UniversalGridPropsConfig;
  const theme = useTheme();
  const { t } = useLocalize();
  const s = universalGridStyles(theme, config);

  if (props.isLoading && (!data || data.length === 0)) {
    return <UniversalGridSkeleton config={config} theme={theme} />;
  }

  if (props.error) {
    return (
      <View style={s.errorContainer}>
        <UniversalText data={null} props={{ variant: 'body', value: t('generic.loadError', { default: "Couldn't load grid" }), color: 'error' }} />
      </View>
    );
  }

  if (!data || data.length === 0) {
    return <View />;
  }

  const displayData = config.maxItems ? data.slice(0, config.maxItems) : data;
  const columns = config.columns || 4;
  const itemWidth = `${100 / columns}%`;

  const renderItem = (item: Record<string, unknown>, index: number) => (
    <View key={index} style={[s.item, { width: itemWidth }]}>
      <UniversalBox
        data={item}
        props={config.item?.config ?? {}}
        onAction={props.onAction}
        isLoading={false}
        error={undefined}
        retryable={false}
      />
    </View>
  );

  if (config.scrollable) {
    return (
      <ScrollView horizontal={true} showsHorizontalScrollIndicator={false} contentContainerStyle={s.scrollContent}>
        {displayData.map(renderItem)}
      </ScrollView>
    );
  }

  return (
    <View style={s.gridContainer}>
      {displayData.map(renderItem)}
    </View>
  );
}

function UniversalGridSkeleton({ config, theme }: { config: UniversalGridPropsConfig; theme: ResolvedTheme }) {
  const s = universalGridStyles(theme, config);
  return (
    <View style={config.scrollable ? s.scrollContent : s.gridContainer}>
      {[...Array(config.maxItems || 4)].map((_, i) => (
        <View key={i} style={[s.item, s.skeletonItem, { width: `${100 / (config.columns || 4)}%` }]}>
          <View style={s.skeletonCard} />
        </View>
      ))}
    </View>
  );
}

function universalGridStyles(theme: ResolvedTheme, config: UniversalGridPropsConfig) {
  const colors = theme.colors;
  const spacing = theme.spacing;
  const radii = theme.borderRadius;

  const gap = typeof config.gap === 'number' ? config.gap : spacing[config.gap || 'md'];

  return StyleSheet.create({
    gridContainer: {
      flexDirection: 'row',
      flexWrap: 'wrap',
      gap,
      paddingHorizontal: spacing[1],
    },
    scrollContent: {
      flexDirection: 'row',
      gap,
      paddingHorizontal: spacing[2],
      paddingVertical: spacing[1],
    },
    item: {
      backgroundColor: colors.surfaceContainerHighest ?? colors.surface,
      borderRadius: radii.md,
      padding: spacing[2],
      alignItems: 'center',
      justifyContent: 'center',
      ...theme.elevation.sm,
    },
    skeletonItem: { opacity: 0.5 },
    skeletonCard: {
      width: '100%',
      aspectRatio: config.itemAspectRatio || 1,
      backgroundColor: colors.onSurface + '1A',
      borderRadius: radii.md,
    },
    errorContainer: { padding: spacing[3], alignItems: 'center' },
  });
}