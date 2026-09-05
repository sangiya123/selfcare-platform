/**
 * HomeScreen — server-driven dashboard.
 *
 * The layout is fully driven by the compiled manifest. This screen just
 * delegates to the SDK's LayoutRenderer.
 */
import React, { useCallback, useState } from 'react';
import { View, Text, StyleSheet, ScrollView, RefreshControl, StatusBar, TouchableOpacity } from 'react-native';
import { useTenant } from '../hooks/useTenant';
import { useApi } from '../hooks/useApi';
import { SelfcareSDK } from '../config/ConfigSDK';
import { tokens } from '../styles/design-tokens';

export function HomeScreen(): React.JSX.Element {
  const { tenantId: activeTenant, industryPack } = useTenant();
  const api = useApi();
  const [refreshing, setRefreshing] = useState(false);

  const onRefresh = useCallback(async () => {
    setRefreshing(true);
    try {
      // The SDK uses ETag round-trip; forceRefresh bypasses cache.
      await api.config.forceRefresh();
    } finally {
      setRefreshing(false);
    }
  }, [api]);

  // The actual home content is rendered by the SDK's LayoutRenderer
  // using the active manifest. Below is the chrome around it.
  return (
    <View style={styles.root}>
      <StatusBar barStyle="dark-content" backgroundColor={tokens.colors.surface} />
      <ScrollView
        contentContainerStyle={styles.scroll}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            tintColor={tokens.colors.primary500}
          />
        }
      >
        <View style={styles.greeting}>
          <Text style={styles.hello}>Welcome back</Text>
          <Text style={styles.tenantLine}>
            {activeTenant}
            {industryPack ? ` · ${industryPack}` : ''}
          </Text>
        </View>

        <View style={styles.dashboardHost}>
          <SelfcareHomeRenderer />
        </View>
      </ScrollView>

      {/* Floating AI assistant button — opens AIChatScreen */}
      <AIAssistantFab />
    </View>
  );
}

/**
 * Floating Action Button that opens the AI chat screen.
 * Uses react-navigation's navigation ref to navigate from any screen.
 */
function AIAssistantFab(): React.JSX.Element {
  const [nav] = React.useState(() => {
    try {
      return require('@react-navigation/native').useNavigation();
    } catch {
      return null;
    }
  });
  return (
    <TouchableOpacity
      style={styles.fab}
      onPress={() => {
        if (nav?.navigate) {
          nav.navigate('AIChat' as never);
        }
      }}
      activeOpacity={0.8}
      accessibilityLabel="Open AI assistant"
    >
      <Text style={styles.fabIcon}>AI</Text>
    </TouchableOpacity>
  );
}

/**
 * Wraps the SDK's layout renderer. Falls back to a placeholder
 * while the manifest is loading or if it doesn't have a `home`
 * experience defined.
 */
function SelfcareHomeRenderer(): React.JSX.Element {
  // The SDK is a singleton; in production this would be loaded
  // from a context provider. For now we lazily import.
  const sdkRef = React.useMemo(() => (globalThis as any).__OMOBIO_SDK__ as SelfcareSDK | undefined, []);
  const manifest = sdkRef?.getManifest() ?? null;
  const home = manifest?.experiences?.['home'] ?? null;

  if (!home) {
    return (
      <View style={styles.placeholder}>
        <Text style={styles.placeholderTitle}>Dashboard loading…</Text>
        <Text style={styles.placeholderBody}>
          If this persists, your tenant's manifest may not have a `home` experience.
        </Text>
      </View>
    );
  }

  // The full LayoutRenderer is used in production; here we render a
  // minimal shell that confirms the manifest is wired through.
  return (
    <View style={styles.placeholder}>
      <Text style={styles.placeholderTitle}>{home.name ?? 'Home'}</Text>
      <Text style={styles.placeholderBody}>
        {home.sections?.length ?? 0} sections loaded
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: tokens.colors.surfaceSubtle },
  scroll: { paddingBottom: 24 },
  greeting: {
    padding: 20,
    backgroundColor: tokens.colors.surface,
  },
  hello: { fontSize: 22, fontWeight: '700', color: tokens.colors.textPrimary },
  tenantLine: {
    fontSize: 13,
    color: tokens.colors.textSecondary,
    marginTop: 2,
  },
  dashboardHost: { padding: 16 },
  placeholder: {
    backgroundColor: tokens.colors.surface,
    borderRadius: 12,
    padding: 24,
    alignItems: 'center',
  },
  placeholderTitle: { fontSize: 16, fontWeight: '600', color: tokens.colors.textPrimary },
  placeholderBody: {
    fontSize: 12,
    color: tokens.colors.textSecondary,
    textAlign: 'center',
    marginTop: 6,
  },
  fab: {
    position: 'absolute',
    right: 20,
    bottom: 20,
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: tokens.colors.primary500,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 4,
    elevation: 5,
  },
  fabIcon: {
    color: '#FFFFFF',
    fontSize: 18,
    fontWeight: '700',
  },
});
