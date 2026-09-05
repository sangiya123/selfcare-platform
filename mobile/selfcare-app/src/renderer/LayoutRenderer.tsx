/**
 * LayoutRenderer — Recursively renders layout sections from the experience manifest.
 *
 * Renders each section by:
 * 1. Looking up the component in the registry
 * 2. Checking visibility conditions
 * 3. Loading data from the data source
 * 4. Rendering with loading/error/stale states
 * 5. Passing action handlers
 */

import React, { ReactElement, useState, useEffect } from 'react';
import { View, Text, StyleSheet, ActivityIndicator, RefreshControl, ScrollView } from 'react-native';
import { Section, Action } from '../config/ConfigSDK';
import { ComponentRegistry, WidgetProps } from '../components/ComponentRegistry';
import { useApi } from '../hooks/useApi';

export interface LayoutRendererProps {
  sections: Section[];
  registry: ComponentRegistry;
  dataSourceResolver: DataSourceResolver;
  onAction: (action: Action) => void;
  onWidgetRefresh?: (widgetId: string) => void;
}

export interface DataSourceResolver {
  resolve(dataSource: string, connectionId: string, tenantId: string): Promise<unknown>;
}

export function LayoutRenderer({
  sections,
  registry,
  dataSourceResolver,
  onAction,
  onWidgetRefresh,
}: LayoutRendererProps): ReactElement {
  // Sort sections by order
  const sortedSections = [...sections]
    .filter(s => !s.visibleWhen || isVisible(s.visibleWhen))
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
  onAction,
  onRefresh,
}: {
  section: Section;
  registry: ComponentRegistry;
  dataSourceResolver: DataSourceResolver;
  onAction: (action: Action) => void;
  onRefresh: () => void;
}): ReactElement | null {
  const Component = registry.get(section.component);

  if (!Component) {
    console.warn(`[LayoutRenderer] Unknown component: ${section.component} (section: ${section.id})`);
    return (
      <View style={styles.unknownComponent}>
        <Text style={styles.unknownText}>Unknown component: {section.component}</Text>
      </View>
    );
  }

  // Determine data loading strategy
  const [data, setData] = useState<unknown>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isStale, setIsStale] = useState(false);

  useEffect(() => {
    loadData();
  }, [section.dataSource, section.props]);

  async function loadData() {
    if (!section.dataSource) {
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      const result = await dataSourceResolver.resolve(section.dataSource, '', '');
      setData(result);
      setIsStale(false);
    } catch (e) {
      const message = e instanceof Error ? e.message : 'Failed to load data';
      setError(message);
      // Check if it's a timeout (retryable)
    } finally {
      setIsLoading(false);
    }
  }

  async function handleRetry() {
    setIsLoading(true);
    setError(null);
    await loadData();
    onRefresh();
  }

  function handleAction(action: Action) {
    onAction(action);
  }

  // Build widget props
  const widgetProps: WidgetProps = {
    id: section.id,
    componentId: section.component,
    variant: section.variant,
    data,
    dataSource: section.dataSource,
    props: section.props,
    onAction: handleAction,
    isLoading,
    isStale,
    error,
    retryable: error !== null,
    onRetry: handleRetry,
  };

  // Render states
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

function isVisible(condition: Section['visibleWhen']): boolean {
  if (!condition) return true;
  // Check feature flags, LOB, connection type, segment
  // Implemented by connecting to FeatureFlagClient
  return true;
}

// ============================================================
// STATE WIDGETS
// ============================================================

function SkeletonWidget({ sectionId }: { sectionId: string }): ReactElement {
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

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    paddingBottom: 100, // Bottom nav safe area
  },
  widgetContainer: {
    marginBottom: 16,
  },
  unknownComponent: {
    padding: 12,
    backgroundColor: '#FFF3CD',
    borderRadius: 8,
  },
  unknownText: {
    color: '#856404',
    fontSize: 13,
  },
  skeleton: {
    padding: 16,
    backgroundColor: '#F0F0F0',
    borderRadius: 12,
  },
  skeletonBlock: {
    height: 20,
    backgroundColor: '#E0E0E0',
    borderRadius: 4,
    marginBottom: 8,
  },
  skeletonShort: {
    width: '60%',
  },
  errorWidget: {
    padding: 16,
    backgroundColor: '#FFF5F5',
    borderRadius: 12,
    alignItems: 'center',
  },
  errorTitle: {
    fontSize: 15,
    fontWeight: '600',
    color: '#1A1A1A',
    marginBottom: 4,
  },
  errorMessage: {
    fontSize: 13,
    color: '#6B6475',
    textAlign: 'center',
    marginBottom: 12,
  },
  retryButton: {
    fontSize: 14,
    fontWeight: '600',
    color: '#6D28D9',
    paddingVertical: 8,
    paddingHorizontal: 16,
  },
});
