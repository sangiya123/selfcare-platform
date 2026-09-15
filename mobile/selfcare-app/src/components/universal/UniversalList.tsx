/**
 * UniversalList — Virtualized list with renderItem driven by child widget config.
 * Replaces DataList, BundlesList, NotificationsList, etc.
 *
 * Component ID: UniversalList
 * All styling via ThemeEngine tokens.
 *
 * Manifest config:
 * {
 *   primitive: "UniversalList",
 *   config: {
 *     dataSource: "product.recommended",
 *     item: { primitive: "UniversalBox", config: { renderType: "card", children: [...] }},
 *     layout: "vertical" | "horizontal" | "grid",
 *     columns?: 2 | 3 | 4,
 *     gap?: "sm" | "md" | "lg",
 *     separator?: boolean,
 *     emptyState?: { title: "...", message: "...", action: {...} },
 *     loadMore?: { enabled: true, action: {...} },
 *     pullToRefresh?: true
 *   }
 * }
 */

import React from 'react';
import { View, FlatList, StyleSheet, RefreshControl, ActivityIndicator } from 'react-native';
import type { PrimitiveProps } from '../ComponentRegistry';
import { useTheme, ResolvedTheme } from '../../manifest/ThemeEngine';
import { useLocalize } from '../../manifest/Localization';
import { UniversalBox } from './UniversalBox';
import { UniversalText } from './UniversalText';
import { UniversalButton } from './UniversalButton';

interface UniversalListItem {
  id: string;
  [key: string]: unknown;
}

interface UniversalListItemConfig {
  primitive: string;
  config: Record<string, unknown>;
  condition?: string;
}

interface UniversalListPropsConfig {
  dataSource?: string;
  item?: UniversalListItemConfig;
  layout?: 'vertical' | 'horizontal' | 'grid';
  columns?: 2 | 3 | 4;
  gap?: 'none' | 'sm' | 'md' | 'lg' | 'xl' | number;
  separator?: boolean;
  emptyState?: {
    title?: string;
    message?: string;
    icon?: string;
    action?: {
      label: string;
      action: { type: string; route?: string; params?: Record<string, unknown> };
      variant?: 'primary' | 'secondary' | 'outline';
    };
  };
  loadMore?: {
    enabled: boolean;
    action: { type: string; params?: Record<string, unknown> };
    threshold?: number;
  };
  pullToRefresh?: boolean;
  maxItems?: number;
  itemSpacing?: 'none' | 'sm' | 'md' | 'lg';
}

type UniversalListData = UniversalListItem[];

function getTokenValue(obj: Record<string, unknown> | undefined, path: string): unknown {
  if (!obj) return undefined;
  let current: unknown = obj;
  for (const key of path.split('.')) {
    if (current === null || current === undefined) return undefined;
    current = (current as Record<string, unknown>)[key];
  }
  return current;
}

export function UniversalList(props: PrimitiveProps): React.JSX.Element {
  const data = props.data as UniversalListData | undefined;
  const config = (props.props || {}) as UniversalListPropsConfig;
  const theme = useTheme();
  const { t } = useLocalize();
  const s = universalListStyles(theme, config);

  const [refreshing, setRefreshing] = React.useState(false);
  const [loadMoreTriggered, setLoadMoreTriggered] = React.useState(false);

  const handleRefresh = () => {
    if (config.pullToRefresh) {
      setRefreshing(true);
      props.onAction?.({ event: 'pull', type: 'REFRESH', params: {}, analyticsEvent: 'list_refreshed' });
      setTimeout(() => setRefreshing(false), 1000);
    }
  };

  const handleLoadMore = () => {
    if (config.loadMore?.enabled && !loadMoreTriggered) {
      setLoadMoreTriggered(true);
      props.onAction?.({ event: 'reachEnd', type: config.loadMore.action.type, params: config.loadMore.action.params });
      setTimeout(() => setLoadMoreTriggered(false), 1000);
    }
  };

  if (props.isLoading && (!data || data.length === 0)) {
    return <UniversalListSkeleton theme={theme} config={config} />;
  }

  if (props.error) {
    return (
      <View style={s.errorContainer}>
        <UniversalText data={null} props={{ variant: 'body', value: t('generic.loadError', { default: "Couldn't load list" }), color: 'error' }} />
        {props.retryable && (
          <UniversalButton
            data={null}
            props={{ label: t('generic.retry', { default: 'Try again' }), variant: 'primary', onPress: props.onRetry }}
          />
        )}
      </View>
    );
  }

  if (!data || data.length === 0) {
    if (config.emptyState) {
      return (
        <View style={s.emptyContainer}>
          {config.emptyState.icon && <UniversalText data={null} props={{ variant: 'title', value: config.emptyState.icon }} />}
          {config.emptyState.title && <UniversalText data={null} props={{ variant: 'title', value: config.emptyState.title, color: 'onSurface' }} />}
          {config.emptyState.message && <UniversalText data={null} props={{ variant: 'body', value: config.emptyState.message, color: 'secondary' }} />}
          {config.emptyState.action && (
            <UniversalButton
              data={null}
              props={{
                label: config.emptyState.action.label,
                variant: config.emptyState.action.variant || 'primary',
                onPress: config.emptyState.action.action,
              }}
            />
          )}
        </View>
      );
    }
    return <View />;
  }

  const displayData = config.maxItems ? data.slice(0, config.maxItems) : data;
  const hasMore = config.loadMore?.enabled && data.length > (config.maxItems || data.length);

  const renderItem = ({ item, index }: { item: UniversalListItem; index: number }) => {
    if (!config.item) return null;
    return (
      <View style={config.separator ? s.itemWithSeparator : s.item}>
        <UniversalBox
          key={item.id ?? String(index)}
          data={item}
          props={config.item.config}
          onAction={props.onAction}
          isLoading={false}
          error={undefined}
          retryable={false}
        />
      </View>
    );
  };

  const keyExtractor = (item: UniversalListItem) => item.id ?? String(Math.random());

  const contentContainerStyle = config.layout === 'horizontal'
    ? s.horizontalContent
    : config.layout === 'grid'
    ? s.gridContent
    : s.verticalContent;

  return (
    <View style={s.container}>
      <FlatList
        data={displayData}
        renderItem={renderItem}
        keyExtractor={keyExtractor}
        contentContainerStyle={contentContainerStyle}
        refreshControl={
          config.pullToRefresh ? (
            <RefreshControl refreshing={refreshing} onRefresh={handleRefresh} colors={[theme.colors.primary500 ?? '#404040']} />
          ) : undefined
        }
        onEndReached={handleLoadMore}
        onEndReachedThreshold={config.loadMore?.threshold || 0.5}
        ListFooterComponent={hasMore && loadMoreTriggered ? (
          <View style={s.loadMoreContainer}>
            <ActivityIndicator color={theme.colors.primary500 ?? '#404040'} size="small" />
          </View>
        ) : undefined}
        showsVerticalScrollIndicator={false}
        showsHorizontalScrollIndicator={config.layout === 'horizontal'}
        horizontal={config.layout === 'horizontal'}
        numColumns={config.layout === 'grid' ? (config.columns || 2) : 1}
      />
    </View>
  );
}

function UniversalListSkeleton({ theme, config }: { theme: ResolvedTheme; config: UniversalListPropsConfig }) {
  const s = universalListStyles(theme, config);
  return (
    <View style={s.container}>
      {[...Array(5)].map((_, i) => (
        <View key={i} style={config.separator ? s.itemWithSeparator : s.item}>
          <View style={[s.skeletonCard, config.layout === 'grid' && s.skeletonGridCard]}>
            <View style={s.skeletonLine} />
            <View style={s.skeletonLine} />
          </View>
        </View>
      ))}
    </View>
  );
}

function universalListStyles(theme: ResolvedTheme, config: UniversalListPropsConfig) {
  const colors = theme.colors;
  const spacing = theme.spacing;
  const radii = theme.borderRadius;

  const gap = typeof config.gap === 'number' ? config.gap : spacing[config.gap || 'md'];
  const itemSpacing = spacing[config.itemSpacing || 'md'];

  return StyleSheet.create({
    container: { flex: 1 },
    verticalContent: { paddingVertical: itemSpacing, gap: gap },
    horizontalContent: { paddingVertical: itemSpacing, paddingHorizontal: spacing[2], gap: gap },
    gridContent: { padding: spacing[2], gap: gap },
    item: { width: '100%' },
    itemWithSeparator: { width: '100%', paddingBottom: itemSpacing },
    emptyContainer: {
      flex: 1,
      alignItems: 'center',
      justifyContent: 'center',
      padding: spacing[4],
      gap: spacing[2],
    },
    errorContainer: {
      flex: 1,
      alignItems: 'center',
      justifyContent: 'center',
      padding: spacing[4],
      gap: spacing[2],
    },
    loadMoreContainer: { padding: spacing[3], alignItems: 'center' },
    skeletonCard: {
      backgroundColor: colors.surfaceContainerHighest ?? colors.surface,
      borderRadius: radii.md,
      padding: spacing[3],
      ...theme.elevation.sm,
    },
    skeletonGridCard: {
      aspectRatio: 1,
    },
    skeletonLine: {
      height: 16,
      backgroundColor: colors.onSurface + '1A',
      borderRadius: radii.sm,
      marginBottom: spacing[1],
      width: '80%',
    },
  });
}