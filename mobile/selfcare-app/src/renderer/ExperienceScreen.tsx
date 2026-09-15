/**
 * ExperienceScreen — single generic screen that renders ANY manifest experience.
 *
 * Replaces: HomeScreen, BillsScreen, UsageScreen, ProfileScreen, SupportScreen.
 * The manifest's `experience` field selects which sections to render.
 * All chrome (header, refresh, placeholder) is theme-driven — zero hardcoded values.
 */

import React, { useEffect, useMemo, useState } from 'react';
import { View, Text, StyleSheet, RefreshControl, ScrollView, Platform } from 'react-native';
import { useTenant } from '../hooks/useTenant';
import { useApi } from '../hooks/useApi';
import { useLocalize } from '../manifest/Localization';
import { SelfcareSDK } from '../config/ConfigSDK';

import { ComponentRegistry } from '../components/ComponentRegistry';
import { getWidgetRegistry } from '../components/widgetLibrary';
import { LayoutRenderer, RendererContext } from '../renderer/LayoutRenderer';
import { ThemeProvider, useTheme, useThemeColors, ResolvedTheme } from '../manifest/ThemeEngine';
import { ExperienceManifest, NavItem, DataSourceResolver, ManifestTheme } from '../manifest/types';
import { createMobileActionDispatcher } from '../navigation/MobileActionDispatcher';
import { createManifestDataSourceResolver } from '../renderer/dataSourceResolver';
import { APP_VERSION } from '../utils/appVersion';
import { appRouter, loadManifestNavigation } from '../navigation/navigationRef';

interface ExperienceScreenProps {
  /** Experience ID from the manifest (e.g., "home", "bills", "usage", "profile", "support"). */
  experienceId: string;
  /** Optional explicit title key for i18n (falls back to experienceId). */
  titleKey?: string;
  /** Optional explicit title string (overrides titleKey). */
  title?: string;
}

function getSdk(): SelfcareSDK | undefined {
  return (globalThis as any).__SELFCARE_SDK__ as SelfcareSDK | undefined;
}

export function ExperienceScreen({ experienceId, titleKey, title }: ExperienceScreenProps): React.JSX.Element {
  const sdk = getSdk();
  const manifestTheme = (sdk?.getManifest()?.theme as ManifestTheme | undefined) ?? null;

  return (
    <ThemeProvider theme={manifestTheme}>
      <ExperienceScreenInner experienceId={experienceId} titleKey={titleKey} title={title} />
    </ThemeProvider>
  );
}

function ExperienceScreenInner({ experienceId, titleKey, title }: ExperienceScreenProps): React.JSX.Element {
  const { tenantId: activeTenant, industryPack } = useTenant();
  const api = useApi();
  const { t } = useLocalize();
  const colors = useThemeColors();
  const theme = useTheme();
  const styles = useExperienceStyles();
  const currentSdk = getSdk();

  const registry = useMemo(() => getWidgetRegistry(), []);
  const bootManifest = (currentSdk?.getManifest() ?? null) as unknown as ExperienceManifest | null;
  const [experienceManifest, setExperienceManifest] = useState<ExperienceManifest | null>(null);

  // Load the manifest for THIS experience (each experience is its own
  // compiled manifest). "home" resolves to the boot manifest.
  useEffect(() => {
    let cancelled = false;

    async function loadExperience() {
      if (!currentSdk) {
        setExperienceManifest((experienceId === 'home' ? bootManifest : null) as ExperienceManifest | null);
        return;
      }
      if (experienceId === 'home') {
        setExperienceManifest(bootManifest as ExperienceManifest | null);
        return;
      }
      const manifest = await currentSdk.getManifestForExperience(experienceId);
      if (!cancelled) {
        setExperienceManifest(manifest as unknown as ExperienceManifest | null);
      }
    }

    loadExperience();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [experienceId, currentSdk]);

  const manifest = experienceManifest ?? (experienceId === 'home' ? bootManifest : null);

  // Load manifest navigation + catalog on first mount
  useEffect(() => {
    if (!manifest) return;
    loadManifestNavigation(manifest.navigation as NavItem[] | undefined);
    if (manifest.components?.length) {
      registry.loadCatalog(manifest.components);
    }
  }, [manifest, registry]);

  // Resolve sections for this experience — every experience manifest carries
  // its sections at the top level.
  const sections = useMemo(() => {
    if (!manifest) return null;
    if (manifest.sections?.length) return manifest.sections;
    return null;
  }, [manifest]);

  // Build action dispatcher
  const dispatch = useMemo(
    () =>
      createMobileActionDispatcher({
        router: appRouter,
        actionEngine: currentSdk?.actionEngine,
        trackEvent: (event, props) => console.log('[Analytics]', event, props),
      }),
    [currentSdk]
  );

  // Build data source resolver from manifest dataSources config
  const dataSourceResolver = useMemo(
    () => createManifestDataSourceResolver(api, manifest),
    [api, manifest]
  );

  // Renderer context
  const context: RendererContext = useMemo(
    () => ({
      tenantId: activeTenant,
      platform: Platform.OS === 'ios' ? 'ios' : 'android',
      appVersion: APP_VERSION,
      lob: industryPack,
    }),
    [activeTenant, industryPack]
  );

  // Screen title: explicit > titleKey > manifest nav label > experienceId
  const screenTitle = useMemo(() => {
    if (title) return title;
    if (titleKey) return t(titleKey, { default: titleKey });
    if (manifest?.navigation) {
      const navItem = manifest.navigation.find((n) => n.route === `/${experienceId}` || n.route === experienceId);
      if (navItem?.labelKey) return t(navItem.labelKey, { default: navItem.label ?? experienceId });
      if (navItem?.label) return navItem.label;
    }
    return t(`experience.${experienceId}.title`, { default: experienceId });
  }, [title, titleKey, manifest, experienceId, t]);

  if (!sections || sections.length === 0) {
    return (
      <View style={styles.placeholder}>
        <Text style={styles.placeholderTitle}>{screenTitle}</Text>
        <Text style={styles.placeholderBody}>
          {t('experience.empty', {
            default: `No sections configured for "${experienceId}". Publish a layout in Selfcare Studio.`,
          })}
        </Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <ScrollView
        contentContainerStyle={styles.scroll}
        refreshControl={
          <RefreshControl
            refreshing={false}
            onRefresh={() => {}}
            tintColor={colors.primary500}
          />
        }
      >
        <LayoutRenderer
          sections={sections}
          registry={registry}
          dataSourceResolver={dataSourceResolver}
          onAction={dispatch}
          context={context}
        />
      </ScrollView>
    </View>
  );
}

/** Experience chrome styles — resolved from the manifest theme. */
function createExperienceStyles(t: ResolvedTheme): ReturnType<typeof StyleSheet.create> {
  const colors = t.colors;
  const l = t.layout;
  return StyleSheet.create({
    container: { flex: 1, backgroundColor: colors.surfaceSubtle },
    scroll: { paddingBottom: l.sectionGap * 2 },
    placeholder: {
      flex: 1,
      backgroundColor: colors.surface,
      alignItems: 'center',
      justifyContent: 'center',
      padding: l.cardPadding,
    },
    placeholderTitle: {
      fontSize: 20,
      fontWeight: '600',
      color: colors.textPrimary,
      marginBottom: 8,
    },
    placeholderBody: {
      fontSize: 14,
      color: colors.textSecondary,
      textAlign: 'center',
    },
  });
}

function useExperienceStyles() {
  const theme = useTheme();
  return useMemo(() => createExperienceStyles(theme), [theme]);
}