/**
 * OMOBIO Selfcare App — Top-level application.
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
import { View, Text, StatusBar, StyleSheet, ActivityIndicator, LogBox } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { NavigationContainer, DefaultTheme } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import Config from 'react-native-config';

import { SelfcareSDK } from './src/config/ConfigSDK';
import { registerAllWidgets } from './src/components/widgets';
import { applyTheme } from './src/config/theme';
import { useTenant } from './src/hooks/useTenant';
import { useAuthStore } from './src/hooks/useAuth';
import { initAppFlyer, trackEvent } from './src/hooks/useAppFlyer';
import { tokens } from './src/styles/design-tokens';

import { LoginScreen } from './src/screens/LoginScreen';
import { OtpScreen } from './src/screens/OtpScreen';
import { HomeScreen } from './src/screens/HomeScreen';
import { BillsScreen } from './src/screens/BillsScreen';
import { UsageScreen } from './src/screens/UsageScreen';
import { ProfileScreen } from './src/screens/ProfileScreen';
import { SupportScreen } from './src/screens/SupportScreen';
import AIChatScreen from './src/screens/AIChatScreen';

import type { ThemeTokens } from './src/config/types';

// Augment global to hold the SDK singleton — accessible from any hook.
declare global {
  // eslint-disable-next-line no-var
  var __OMOBIO_SDK__: SelfcareSDK | undefined;
}

export type RootStackParamList = {
  Login: undefined;
  Otp: { msisdn: string };
  Main: undefined;
  Bills: undefined;
  Usage: undefined;
  Support: undefined;
  Profile: undefined;
  AIChat: undefined;
};

type TabParamList = {
  Home: undefined;
  Bills: undefined;
  Usage: undefined;
  Profile: undefined;
};

const RootStack = createNativeStackNavigator<RootStackParamList>();
const Tab = createBottomTabNavigator<TabParamList>();

LogBox.ignoreLogs(['new NativeEventEmitter']);

// SDK is constructed once at module load.
const sdk = new SelfcareSDK({
  tenantId: (Config as any)?.MOBILE_TENANT_ID ?? 'dialog-lk',
  apiBaseUrl: (Config as any)?.MOBILE_API_BASE_URL ?? 'http://localhost:8080',
  env: ((Config as any)?.MOBILE_ENV as any) ?? 'development',
});
globalThis.__OMOBIO_SDK__ = sdk;

// Initialise AppFlyer once at module load
initAppFlyer();
trackEvent('app_open', { source: 'cold_start' });

function MainTabs(): React.JSX.Element {
  return (
    <Tab.Navigator
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: tokens.colors.primary500,
        tabBarInactiveTintColor: tokens.colors.textSecondary,
        tabBarStyle: { paddingTop: 4, height: 60 },
      }}
    >
      <Tab.Screen name="Home" component={HomeScreen} options={{ title: 'Home' }} />
      <Tab.Screen name="Bills" component={BillsScreen} options={{ title: 'Bills' }} />
      <Tab.Screen name="Usage" component={UsageScreen} options={{ title: 'Usage' }} />
      <Tab.Screen name="Profile" component={ProfileScreen} options={{ title: 'Profile' }} />
    </Tab.Navigator>
  );
}

function App(): React.JSX.Element {
  const [ready, setReady] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [theme, setTheme] = useState<ReturnType<typeof applyTheme>>(applyTheme(null));
  const { setTenant } = useTenant();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  const bootstrap = useCallback(async (): Promise<void> => {
    try {
      await sdk.initialize();
      registerAllWidgets(sdk.componentRegistry);
      setTenant(sdk.config['tenantId'] ?? 'dialog-lk');
      const manifestTheme = sdk.getTheme() as ThemeTokens | null;
      setTheme(applyTheme(manifestTheme));

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
        <StatusBar
          barStyle="dark-content"
          backgroundColor={theme.colors.surface ?? tokens.colors.surface}
        />
        <NavigationContainer theme={navTheme as any}>
          <RootStack.Navigator screenOptions={{ headerShown: false }}>
            {isAuthenticated ? (
              <RootStack.Screen name="Main" component={MainTabs} />
            ) : (
              <>
                <RootStack.Screen name="Login" component={LoginScreen} />
                <RootStack.Screen name="Otp" component={OtpScreen} />
              </>
            )}
            <RootStack.Screen name="Support" component={SupportScreen} />
            <RootStack.Screen name="AIChat" component={AIChatScreen} options={{ title: 'AI Assistant' }} />
          </RootStack.Navigator>
        </NavigationContainer>
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
      <Text style={styles.splashTitle}>OMOBIO</Text>
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
