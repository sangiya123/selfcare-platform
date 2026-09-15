/**
 * selfcare kernel — imperative navigation ref + manifest-route to screen mapping.
 *
 * The app registers `navigationRef` with NavigationContainer. The kernel
 * resolves manifest routes (from the compiled manifest `navigation` section)
 * to react-navigation screen names — including navigating into tab screens
 * from the root stack (nested navigation) and handling stack screens.
 */

import { createNavigationContainerRef } from '@react-navigation/native';
import { NavItem } from '../manifest/types';
import { NavigationRouter } from './NavigationRouter';

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

export type RootTabParamList = {
  Home: undefined;
  Bills: undefined;
  Usage: undefined;
  Profile: undefined;
};

export const navigationRef = createNavigationContainerRef<RootStackParamList>();

/**
 * Loose imperative navigator used for deep links: manifest routes map to
 * screen names at runtime, so we type the params loosely instead of forcing
 * every dynamic route through the react-navigation generic overloads.
 */
type LooseNavigator = {
  navigate: (name: string, params?: Record<string, unknown>) => void;
};

const looseNav = navigationRef as unknown as LooseNavigator;

/** Manifest routes that map to stack screens (outside the tab navigator). */
const STACK_SCREENS: Record<string, keyof RootStackParamList> = {
  '/support': 'Support',
  '/help': 'Support',
  '/ai': 'AIChat',
  '/chat': 'AIChat',
  '/login': 'Login',
};

/** Manifest routes that map to tabs nested inside the Main stack screen. */
const TAB_SCREENS: Record<string, keyof RootTabParamList> = {
  '/home': 'Home',
  '/dashboard': 'Home',
  '/bills': 'Bills',
  '/bill': 'Bills',
  '/usage': 'Usage',
  '/profile': 'Profile',
};

function normalize(route: string): string {
  let r = route.trim();
  if (!r.startsWith('/')) r = `/${r}`;
  // Prefer the longest exact key match (deep links like /usage/history resolve
  // to the goal screen, otherwise fall back to the parent tab).
  return r;
}

/**
 * Navigate to a manifest route. Resolves exact screen aliases first, then
 * longest-prefix tab/stack fallback so unknown deep paths still land on a
 * sensible screen.
 */
export function navigateToRoute(
  route: string,
  params?: Record<string, unknown>
): boolean {
  if (!navigationRef.isReady()) return false;

  const normalized = normalize(route);

  const stack = exactOrPrefix(STACK_SCREENS, normalized);
  if (stack) {
    looseNav.navigate(stack, params);
    return true;
  }

  const tab = exactOrPrefix(TAB_SCREENS, normalized);
  if (tab) {
    looseNav.navigate('Main', {
      screen: tab,
      params: params ?? {},
    });
    return true;
  }

  return false;
}

function exactOrPrefix(
  table: Record<string, string>,
  normalized: string
): string | null {
  if (table[normalized]) return table[normalized];

  let best: string | null = null;
  let bestLen = 0;
  for (const [key, screen] of Object.entries(table)) {
    if (normalized.startsWith(key) && key.length > bestLen) {
      best = screen;
      bestLen = key.length;
    }
  }
  return best;
}

/** App-wide navigation router singleton (route index from manifest config). */
export const appRouter = new NavigationRouter(navigateToRoute);

/** Load the manifest `navigation` section into the app router. */
export function loadManifestNavigation(navigation?: NavItem[]): void {
  appRouter.load(navigation);
}