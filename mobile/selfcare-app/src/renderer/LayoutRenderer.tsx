/**
 * LayoutRenderer — Recursively renders layout sections from the experience manifest.
 *
 * Renders each section by:
 * 1. Looking up the component in the registry
 * 2. Applying visibility rules (feature flag / LOB / segment) + component
 *    availability (platform / min app version / customer type) per v6 §12
 * 3. Loading data through the DataSourceResolver
 * 4. Rendering with loading/error/stale states
 * 5. Passing action handlers (closed action set, ADR-009)
 *
 * Contract: ManifestSection / ManifestAction from `src/manifest/types`.
 */

import React, { ReactElement, useState, useEffect, useMemo } from 'react';
import { View, Text, StyleSheet, RefreshControl, ScrollView } from 'react-native';
import { ManifestSection, ManifestAction, DataSourceResolver } from '../manifest/types';
import { ComponentRegistry, WidgetProps } from '../components/ComponentRegistry';
import { useTheme, fontSizePx, ResolvedTheme } from '../manifest/ThemeEngine';
import { APP_VERSION } from '../utils/appVersion';

export interface RendererContext {
  tenantId?: string | null;
  connectionId?: string | null;
  platform: 'ios' | 'android' | 'web';
  appVersion: string;
  lob?: string | null;
  segment?: string | null;
  featureFlags?: Record<string, boolean>;
}

export interface LayoutRendererProps {
  sections: ManifestSection[];
  registry: ComponentRegistry;
  dataSourceResolver?: DataSourceResolver;
  onAction: (action: ManifestAction) => void;
  onWidgetRefresh?: (widgetId: string) => void;
  context?: Partial<RendererContext>;
}

const DEFAULT_CONTEXT: RendererContext = {
  platform: 'ios',
  appVersion: APP_VERSION,
};

export function LayoutRenderer({
  sections,
  registry,
  dataSourceResolver,
  onAction,
  onWidgetRefresh,
  context,
}: LayoutRendererProps): ReactElement {
  const ctx: RendererContext = { ...DEFAULT_CONTEXT, ...context };
  const styles = useChromeStyles();

  const sortedSections = [...sections]
    .filter((s) => isSectionVisible(s, ctx))
    .filter((s) => isSectionAvailable(s, registry, ctx))
    .sort((a, b) => (a.order ?? 99) - (b.order ?? 99));

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={styles.content}
      showsVerticalScrollIndicator={false}
      refreshControl={
        <RefreshControl
          refreshing={false}
          onRefresh={() => onWidgetRefresh?.('all')}
        />
      }
    >
      {sortedSections.map((section) => (
        <SectionRenderer
          key={section.id}
          section={section}
          registry={registry}
          dataSourceResolver={dataSourceResolver}
          context={ctx}
          onAction={onAction}
          onRefresh={() => onWidgetRefresh?.(section.id)}
        />
      ))}
    </ScrollView>
  );
}

export function SectionRenderer({
  section,
  registry,
  dataSourceResolver,
  context,
  onAction,
  onRefresh,
}: {
  section: ManifestSection;
  registry: ComponentRegistry;
  dataSourceResolver?: DataSourceResolver;
  context: RendererContext;
  onAction: (action: ManifestAction) => void;
  onRefresh: () => void;
}): ReactElement | null {
  const Component = registry.getOrResolve(section.component);
  const styles = useChromeStyles();

  if (!Component) {
    console.warn(`[LayoutRenderer] Unknown component: ${section.component} (section: ${section.id})`);
    return (
      <View style={styles.unknownComponent}>
        <Text style={styles.unknownText}>Unknown component: {section.component}</Text>
      </View>
    );
  }

  const [data, setData] = useState<unknown>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isStale, setIsStale] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function loadData() {
      if (!section.dataSource || !dataSourceResolver) {
        setIsLoading(false);
        return;
      }

      setIsLoading(true);
      setError(null);

      try {
        const result = await dataSourceResolver.resolve(section.dataSource, {
          tenantId: context.tenantId,
          connectionId: context.connectionId,
        });
        if (cancelled) return;
        setData(result);
        setIsStale(false);
      } catch (e) {
        if (cancelled) return;
        const message = e instanceof Error ? e.message : 'Failed to load data';
        setError(message);
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }

    loadData();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [section.dataSource, section.id, context.connectionId]);

  async function handleRetry() {
    setIsLoading(true);
    setError(null);
    if (section.dataSource && dataSourceResolver) {
      try {
        const result = await dataSourceResolver.resolve(section.dataSource, {
          tenantId: context.tenantId,
          connectionId: context.connectionId,
        });
        setData(result);
      } catch (e) {
        const message = e instanceof Error ? e.message : 'Failed to load data';
        setError(message);
      }
    }
    setIsLoading(false);
    onRefresh();
  }

  function handleAction(action: ManifestAction) {
    onAction(action);
  }

  const widgetProps: WidgetProps = {
    id: section.id,
    componentId: section.component,
    variant: section.variant,
    data,
    dataSource: section.dataSource,
    props: section.props,
    onAction: handleAction as WidgetProps['onAction'],
    isLoading,
    isStale,
    error: error ?? undefined,
    retryable: error !== null,
    onRetry: handleRetry,
  };

  if (isLoading && !data) {
    return (
      <View style={styles.widgetContainer}>
        <SkeletonWidget sectionId={section.id} />
      </View>
    );
  }

  if (error && !data) {
    return (
      <View style={styles.widgetContainer}>
        <ErrorWidget
          sectionId={section.id}
          message={error}
          onRetry={handleRetry}
        />
      </View>
    );
  }

  return (
    <View style={styles.widgetContainer}>
      <Component {...widgetProps} />
    </View>
  );
}

/**
 * Visibility gate (feature flag / LOB / segment). Unknown feature flags are
 * treated as visible unless the feature is explicitly disabled in config.
 */
export function isSectionVisible(
  section: ManifestSection,
  context: RendererContext
): boolean {
  const rule = section.visibleWhen;
  if (!rule) return true;

  if (rule.feature && context.featureFlags) {
    if (context.featureFlags[rule.feature] === false) return false;
  }

  if (rule.lob && context.lob && rule.lob !== context.lob) return false;
  if (rule.segment && context.segment && rule.segment !== context.segment) return false;

  return true;
}

/**
 * Availability gate: section-level LOB/customerType/platform/max constraints
 * AND the registered component's platform/minAppVersion availability.
 */
export function isSectionAvailable(
  section: ManifestSection,
  registry: ComponentRegistry,
  context: RendererContext
): boolean {
  const availability = section.availability;
  if (availability) {
    if (availability.platform && !availability.platform.includes(context.platform)) return false;
    if (availability.lob && context.lob && !availability.lob.includes(context.lob)) return false;
    if (availability.customerType && context.segment && !availability.customerType.includes(context.segment)) return false;
    if (availability.minAppVersion && !satisfiesMinVersion(context.appVersion, availability.minAppVersion)) return false;
  }
  return registry.isAvailable(section.component, context.platform, context.appVersion);
}

/** Compare semver strings: true when version >= minVersion. */
export function satisfiesMinVersion(version: string, minVersion: string): boolean {
  const vParts = version.split('.').map(Number);
  const mParts = minVersion.split('.').map(Number);

  for (let i = 0; i < Math.max(vParts.length, mParts.length); i++) {
    const v = vParts[i] || 0;
    const m = mParts[i] || 0;
    if (v > m) return true;
    if (v < m) return false;
  }
  return true;
}

// ============================================================
// STATE WIDGETS
// ============================================================

function SkeletonWidget({ sectionId }: { sectionId: string }): ReactElement {
  const styles = useChromeStyles();
  return (
    <View style={styles.skeleton}>
      <View style={styles.skeletonBlock} />
      <View style={[styles.skeletonBlock, styles.skeletonShort]} />
    </View>
  );
}

function ErrorWidget({
  message,
  onRetry,
}: {
  sectionId: string;
  message: string;
  onRetry: () => void;
}): ReactElement {
  const styles = useChromeStyles();
  return (
    <View style={styles.errorWidget}>
      <Text style={styles.errorTitle}>Couldn't load</Text>
      <Text style={styles.errorMessage}>{message}</Text>
      <Text style={styles.retryButton} onPress={onRetry}>
        Try again
      </Text>
    </View>
  );
}

/** Chrome styles — fully resolved from the manifest theme (no hardcoded values). */
function createChromeStyles(t: ResolvedTheme): ReturnType<typeof StyleSheet.create> {
  const colors = t.colors;
  const layout = t.layout;
  return StyleSheet.create({
    container: {
      flex: 1,
    },
    content: {
      paddingHorizontal: layout.pagePadding,
      paddingVertical: Math.round(layout.sectionGap / 2),
      paddingBottom: layout.tabBarHeight + layout.sectionGap, // Bottom nav safe area
    },
    widgetContainer: {
      marginBottom: layout.sectionGap,
    },
    unknownComponent: {
      padding: layout.cardPadding,
      backgroundColor: colors.surfaceSubtle,
      borderRadius: t.layout.radius ?? (t.radius ? parseInt(t.radius.replace('px', ''), 10) : 8),
      borderWidth: StyleSheet.hairlineWidth,
      borderColor: colors.border,
    },
    unknownText: {
      color: colors.textSecondary,
      fontSize: fontSizePx(t, 'sm'),
    },
    skeleton: {
      padding: layout.cardPadding,
      backgroundColor: colors.surfaceSubtle,
      borderRadius: t.layout.radius ?? 12,
    },
    skeletonBlock: {
      height: 20,
      backgroundColor: colors.border,
      borderRadius: 4,
      marginBottom: 8,
    },
    skeletonShort: {
      width: '60%',
    },
    errorWidget: {
      padding: layout.cardPadding,
      backgroundColor: colors.surfaceSubtle,
      borderRadius: t.layout.radius ?? 12,
      alignItems: 'center',
    },
    errorTitle: {
      fontSize: fontSizePx(t, 'base'),
      fontWeight: '600',
      color: colors.textPrimary,
      marginBottom: 4,
    },
    errorMessage: {
      fontSize: fontSizePx(t, 'sm'),
      color: colors.textSecondary,
      textAlign: 'center',
      marginBottom: 12,
    },
    retryButton: {
      fontSize: fontSizePx(t, 'sm'),
      fontWeight: '600',
      color: colors.primary500,
      paddingVertical: 8,
      paddingHorizontal: 16,
    },
  });
}

function useChromeStyles() {
  const theme = useTheme();
  return useMemo(() => createChromeStyles(theme), [theme]);
}