/**
 * Selfcare App — Top-level application.
 *
 * - Initialises the Selfcare SDK (loads manifest, registers widgets).
 * - Wires React Navigation root.
 * - Bridges SDK auth state to the useAuthStore (zustand) so any component
 *   can react to auth changes.
 * - Renders a splash while loading, then the Login or Home stack.
 *
 * Environment variables are read via react-native-config from `.env.<env>`.
 */

import React, { useEffect, useState, useCallback } from 'react';
import { View, Text, StatusBar, StyleSheet, ActivityIndicator, LogBox, Linking, Image, Platform } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { NavigationContainer, DefaultTheme } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import Config from 'react-native-config';

import { SelfcareSDK } from './src/config/ConfigSDK';
import { resolveLoginPlan, parseExternalAuthReturn } from './src/config/loginFlow';
import { getWidgetRegistry } from './src/components/widgetLibrary';
import { resolveTheme, ResolvedTheme, useTheme, fontSizePx, ThemeProvider } from './src/manifest/ThemeEngine';
import { useLocalize } from './src/manifest/Localization';
import { ManifestTheme, NavItem, AiAssistantConfig, OverlayBannerConfig, ConsentConfig, ExperienceManifest } from './src/manifest/types';
import { navigationRef, navigateToRoute, appRouter, loadManifestNavigation, RootStackParamList } from './src/navigation/navigationRef';
import { useTenant } from './src/hooks/useTenant';
import { useAuthStore } from './src/hooks/useAuth';
import { initAppFlyer, trackEvent } from './src/hooks/useAppFlyer';
import { AppHeader } from './src/components/chrome/AppHeader';
import { FloatingAssistant } from './src/components/assistant/FloatingAssistant';
import { OverlayBanners } from './src/components/assistant/OverlayBanners';
import { ConsentPrompt } from './src/components/assistant/ConsentPrompt';
import { tokens } from './src/styles/design-tokens';

import { LoginScreen } from './src/screens/LoginScreen';
import { OtpScreen } from './src/screens/OtpScreen';
import { ExperienceScreen } from './src/renderer/ExperienceScreen';
import AIChatScreen from './src/screens/AIChatScreen';

// Augment global to hold the SDK singleton — accessible from any hook.
declare global {
  // eslint-disable-next-line no-var
  var __SELFCARE_SDK__: SelfcareSDK | undefined;
}

export type { RootStackParamList };

type TabParamList = {
  Home: undefined;
  Bills: undefined;
  Usage: undefined;
  Profile: undefined;
};

const RootStack = createNativeStackNavigator<RootStackParamList>();
const Tab: any = createBottomTabNavigator<TabParamList>();

/** Screens the platform core ships; tab routes resolve through this map. */
const CORE_SCREENS: Record<string, React.ComponentType> = {
  Home: () => <ExperienceScreen experienceId="home" />,
  Bills: () => <ExperienceScreen experienceId="bills" />,
  Usage: () => <ExperienceScreen experienceId="usage" />,
  Profile: () => <ExperienceScreen experienceId="profile" />,
  Support: () => <ExperienceScreen experienceId="support" />,
  AIChat: AIChatScreen,
};

function TabPlaceholder(): React.JSX.Element {
  const theme = useTheme();
  const { t } = useLocalize();
  return (
    <View
      style={{
        flex: 1,
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: theme.colors.surfaceSubtle,
        padding: theme.layout.pagePadding,
      }}
    >
      <Text style={{ color: theme.colors.textSecondary, fontSize: fontSizePx(theme, 'base') }}>
        {t('screens.unmapped', {
          default: 'This section is not configured yet for this operator.',
        })}
      </Text>
    </View>
  );
}

/**
 * Bottom tabs are BUILT from the manifest navigation (placement: 'tabBar'),
 * never hardcoded: which tabs exist, their labels (i18n), icons (CDN), order
 * and enablement are per-operator config. If the manifest declares no tab bar,
 * the four core screens render as a local-dev fallback only.
 */
function MainTabs({ theme }: { theme: ResolvedTheme }): React.JSX.Element {
  const { t } = useLocalize();
  const manifest = sdk.getManifest() as unknown as { navigation?: NavItem[] } | null;
  const tabItems = (manifest?.navigation ?? [])
    .filter((i) => i.placement === 'tabBar' && i.enabled !== false)
    .filter(isPlatformActive);

  const effective: { route: string; label: string; screen: React.ComponentType; iconUrl?: string }[] = tabItems.length
    ? tabItems.map((it) => ({
        route: it.route.replace(/^\//, ''),
        label: it.labelKey ? t(it.labelKey, { default: it.label ?? it.route }) : (it.label ?? it.route),
        screen: CORE_SCREENS[it.route.replace(/^\//, '')] ?? TabPlaceholder,
        iconUrl: it.iconUrl,
      }))
    : [
        { route: 'Home', label: t('tabs.home', { default: 'Home' }), screen: CORE_SCREENS.Home },
        { route: 'Bills', label: t('tabs.bills', { default: 'Bills' }), screen: CORE_SCREENS.Bills },
        { route: 'Usage', label: t('tabs.usage', { default: 'Usage' }), screen: CORE_SCREENS.Usage },
        { route: 'Profile', label: t('tabs.profile', { default: 'Profile' }), screen: CORE_SCREENS.Profile },
      ];

  const activeColor = theme.colors.primary ?? theme.colors.textPrimary ?? '#171717';
  const inactiveColor = theme.colors.textSecondary ?? '#737373';

  return (
    <Tab.Navigator
      screenOptions={{
        headerShown: true,
        header: () => <AppHeader />,
        tabBarActiveTintColor: activeColor,
        tabBarInactiveTintColor: inactiveColor,
        tabBarStyle: {
          height: theme.layout.tabBarHeight,
          backgroundColor: theme.colors.surface,
          borderTopColor: theme.colors.border,
        },
      }}
    >
      {effective.map(({ route, label, screen, iconUrl }) => (
        <Tab.Screen
          key={route}
          name={route}
          component={screen}
          options={{
            title: label,
            tabBarLabel: label,
            header: () => <AppHeader />,
            tabBarIcon: ({ color, size }: { color: string; size: number }) => iconFor(color, size, iconUrl),
          }}
        />
      ))}
    </Tab.Navigator>
  );

  function iconFor(color: string, size: number, url?: string): React.JSX.Element {
    if (url) {
      return (
        <Image
          source={{ uri: url }}
          style={{ width: size, height: size, tintColor: color }}
          resizeMode="contain"
        />
      );
    }
    return (
      <View
        style={{
          width: size * 0.5,
          height: size * 0.5,
          borderRadius: size * 0.25,
          backgroundColor: color,
        }}
      />
    );
  }

  function isPlatformActive(item: NavItem): boolean {
    const availability = item.availability;
    if (!availability?.length) return true;
    const platformKey = Platform.OS === 'ios' ? 'ios' : 'android';
    return availability.some((a) => a.platform === platformKey && a.active !== false);
  }
}

LogBox.ignoreLogs(['new NativeEventEmitter']);

// SDK is constructed once at module load.
// MOBILE_TENANT_ID is REQUIRED for non-development environments. The
// 'dialog-lk' default is a LOCAL DEV FALLBACK only and should never
// reach stg/reg/prod builds.
const sdk = new SelfcareSDK({
  tenantId: (Config as any)?.MOBILE_TENANT_ID,
  apiBaseUrl: (Config as any)?.MOBILE_API_BASE_URL ?? 'http://localhost:8080',
  env: ((Config as any)?.MOBILE_ENV as any) ?? 'development',
});
globalThis.__SELFCARE_SDK__ = sdk;

// Initialise AppFlyer once at module load
initAppFlyer();
trackEvent('app_open', { source: 'cold_start' });

function App(): React.JSX.Element {
  const [ready, setReady] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [theme, setTheme] = useState<ResolvedTheme>(() => resolveTheme(null));
  const [manifestTheme, setManifestTheme] = useState<ManifestTheme | null>(null);
  const [runtime, setRuntime] = useState<{
    ai?: AiAssistantConfig;
    banners?: OverlayBannerConfig[];
    consents?: ConsentConfig[];
  }>({});
  const { setTenant } = useTenant();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  // Resolve deep links (universal links + selfcare://) through the manifest
  // navigation router once the app is bootstrapped.
  const handleDeepLink = useCallback((url: string | null | undefined): void => {
    if (!url) return;

    // External-auth return (operator web OTP → deep link): exchange the code
    // with the configured auth service instead of routing. The flow is
    // admin-authored via manifest auth.login.
    const plan = resolveLoginPlan(sdk.getManifest());
    const externalReturn = parseExternalAuthReturn(url, plan.flow);
    if (externalReturn) {
      if ('code' in externalReturn && externalReturn.code) {
        sdk.auth
          .completeExternalAuth(externalReturn.code)
          .then(() => useAuthStore.setState({ isAuthenticated: true }))
          .catch((err) => {
            // eslint-disable-next-line no-console
            console.error('[Login] external auth exchange failed', err);
          });
      } else {
        // eslint-disable-next-line no-console
        console.error('[Login] external auth returned error', (externalReturn as { error: string }).error);
      }
      return;
    }

    const link = appRouter.parseUrl(url);
    if (!link) return;
    const routed = appRouter.navigate(link.route, link.params);
    if (!routed) navigateToRoute(link.route, link.params);
  }, []);

  useEffect(() => {
    const sub = Linking.addEventListener('url', (event) => handleDeepLink(event.url));
    Linking.getInitialURL()
      .then((url) => handleDeepLink(url))
      .catch(() => {
        // No initial deep link; that's fine.
      });
    return () => sub.remove();
  }, [handleDeepLink]);

  const bootstrap = useCallback(async (): Promise<void> => {
    try {
      await sdk.initialize();
      getWidgetRegistry();
      setTenant(sdk.config['tenantId'] ?? '');
      const manifestTheme = sdk.getTheme() as ManifestTheme | null;
      setTheme(resolveTheme(manifestTheme));
      setManifestTheme(manifestTheme);
      loadManifestNavigation(sdk.getManifest()?.navigation as unknown as NavItem[] | undefined);

      // Load component catalog into registry for lazy resolution
      const registry = getWidgetRegistry();
      const manifest = sdk.getManifest() as unknown as ExperienceManifest | null | undefined;
      if (manifest?.components?.length) {
        registry.loadCatalog(manifest.components);
      }
      setRuntime({
        ai: manifest?.aiAssistant,
        banners: manifest?.overlayBanners,
        consents: manifest?.consents,
      });

      // Bridge SDK auth events to zustand so any component can observe
      sdk.auth.addAuthListener((event) => {
        const auth = useAuthStore.getState();
        if (event === 'authenticated') {
          useAuthStore.setState({ isAuthenticated: true });
        }
        if (event === 'signed_out' || event === 'session_expired') {
          auth.signOutLocally();
        }
      });

      // Reconcile on boot: if the SDK has tokens but zustand doesn't know,
      // mark the session as authenticated. (isAuthenticated is sync, getAccessToken is async.)
      if (sdk.auth.isAuthenticated()) {
        sdk.auth.getAccessToken()
          .then((token) => {
            if (token) {
              useAuthStore.setState({ isAuthenticated: true, accessToken: token });
            }
          })
          .catch(() => {
            // Token retrieval failed; stay unauthenticated.
          });
      }

      setReady(true);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to initialise app';
      setError(message);
      // eslint-disable-next-line no-console
      console.error('[App] Bootstrap failed', err);
    }
  }, [setTenant]);

  useEffect(() => {
    bootstrap();
  }, [bootstrap]);

  if (!ready) {
    return <SplashScreen error={error} onRetry={bootstrap} />;
  }

  const navTheme = {
    ...DefaultTheme,
    dark: false,
    colors: {
      ...DefaultTheme.colors,
      primary: theme.colors.primary500 ?? tokens.colors.primary500,
      background: theme.colors.surfaceSubtle ?? tokens.colors.surfaceSubtle,
      card: theme.colors.surface ?? tokens.colors.surface,
      text: theme.colors.textPrimary ?? tokens.colors.textPrimary,
    },
  };

  return (
    <GestureHandlerRootView style={styles.root}>
      <SafeAreaProvider>
        <StatusBar barStyle={theme.mode === 'dark' ? 'light-content' : 'dark-content'} />
        <ThemeProvider theme={manifestTheme} mode={theme.mode}>
          <NavigationContainer ref={navigationRef} theme={navTheme as any}>
            <RootStack.Navigator screenOptions={{ headerShown: false }}>
              {isAuthenticated ? (
                <RootStack.Screen name="Main">
                  {() => <MainTabs theme={theme} />}
                </RootStack.Screen>
              ) : (
                <>
                  <RootStack.Screen name="Login" component={LoginScreen} />
                  <RootStack.Screen name="Otp" component={OtpScreen} />
                </>
              )}
              <RootStack.Screen name="Support" component={() => <ExperienceScreen experienceId="support" />} />
              <RootStack.Screen name="AIChat" component={AIChatScreen} options={{ title: 'AI Assistant' }} />
            </RootStack.Navigator>
          </NavigationContainer>
          <FloatingAssistant config={runtime.ai} theme={theme} />
          <OverlayBanners configs={runtime.banners} theme={theme} />
          <ConsentPrompt configs={runtime.consents} theme={theme} />
        </ThemeProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}

function SplashScreen({
  error,
  onRetry,
}: {
  error: string | null;
  onRetry: () => void;
}): React.JSX.Element {
  return (
    <View style={styles.splash}>
      <Text style={styles.splashTitle}>selfcare</Text>
      <Text style={styles.splashSubtitle}>Selfcare</Text>
      {error ? (
        <>
          <Text style={styles.splashError}>{error}</Text>
          <Text style={styles.splashRetry} onPress={onRetry}>
            Tap to retry
          </Text>
        </>
      ) : (
        <ActivityIndicator
          size="large"
          color={tokens.colors.primary500}
          style={{ marginTop: 32 }}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  splash: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: tokens.colors.surface,
  },
  splashTitle: {
    fontSize: 36,
    fontWeight: '700',
    color: tokens.colors.primary700,
    letterSpacing: 4,
  },
  splashSubtitle: {
    fontSize: 18,
    fontWeight: '500',
    color: tokens.colors.primary500,
    marginTop: 4,
  },
  splashError: {
    marginTop: 24,
    color: tokens.colors.error,
    paddingHorizontal: 32,
    textAlign: 'center',
  },
  splashRetry: { marginTop: 12, color: tokens.colors.primary500, fontWeight: '600' },
});

export default App;
