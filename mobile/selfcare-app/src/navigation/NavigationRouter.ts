/**
 * selfcare kernel — Navigation & Deep-Link Router (mobile).
 *
 * Indexes the manifest's `navigation` config into a route table and resolves:
 * - NAVIGATE actions (server-driven) to registered screens
 * - incoming deep links (universal links / `selfcare://`) into { route, params }
 *
 * The router is renderer-agnostic: the app injects a `navigate` function
 * (e.g. bound to a react-navigation ref). Config never hardcodes screens —
 * all routes come from the compiled manifest (v6 rule).
 */

import { ManifestAction, NavItem } from '../manifest/types';

/** Injected imperative navigation (react-navigation ref or history). */
export type RouteNavigator = (
  route: string,
  params?: Record<string, unknown>
) => void;

/** High-level deep-link result. */
export interface DeepLink {
  route: string;
  params: Record<string, unknown>;
  raw: string;
}

export const DEFAULT_DEEP_LINK_SCHEME = 'selfcare';

export class NavigationRouter {
  private navItems: NavItem[] = [];
  private routeMap = new Map<string, NavItem>();
  private readonly navigateImpl: RouteNavigator;

  constructor(navigator: RouteNavigator) {
    this.navigateImpl = navigator;
  }

  /** (Re)index navigation config from the compiled manifest. */
  load(navigation?: NavItem[]): this {
    this.navItems = navigation ?? [];
    this.routeMap.clear();

    const walk = (items: NavItem[], parent?: NavItem) => {
      for (const item of items) {
        const routeKey = this.normalizeRoute(item.route);
        if (routeKey) {
          this.routeMap.set(routeKey, item);
          this.routeMap.set(routeKey + '/', item);
        }
        if (item.children?.length) walk(item.children, item);
      }
    };

    walk(this.navItems);
    return this;
  }

  /** Flat list of registered navigation items. */
  getItems(): NavItem[] {
    return this.navItems;
  }

  /** Top-level items suitable for a bottom tab bar. */
  getTabs(): NavItem[] {
    return this.navItems;
  }

  /** Resolve a route path to its navigation entry (longest-prefix match). */
  resolve(route: string): NavItem | null {
    const normalized = this.normalizeRoute(route);
    if (!normalized) return null;

    if (this.routeMap.has(normalized)) {
      return this.routeMap.get(normalized) ?? null;
    }

    // Longest-prefix fallback so "/usage/history" resolves to "/usage".
    let best: NavItem | null = null;
    let bestLen = 0;
    for (const [key, item] of this.routeMap.entries()) {
      const cleanKey = key.replace(/\/$/, '');
      if (normalized.startsWith(cleanKey) && cleanKey.length > bestLen) {
        best = item;
        bestLen = cleanKey.length;
      }
    }
    return best;
  }

  /** Imperatively navigate to a manifest route. Returns false if unknown. */
  navigate(route: string, params?: Record<string, unknown>): boolean {
    if (!this.resolve(route)) {
      return false;
    }
    this.navigateImpl(route, params);
    return true;
  }

  /**
   * Dispatch a manifest action. Returns true when the action was consumed by
   * the router (currently only NAVIGATE).
   */
  handleAction(action: ManifestAction): boolean {
    if (action?.type !== 'NAVIGATE') return false;
    const route = action.route;
    if (!route) return false;
    return this.navigate(route, action.params);
  }

  /**
   * Parse a deep link URL into { route, params }.
   *
   * Supports:
   *   selfcare://usage/history?from=home
   *   https://selfcare.dialog.lk/app/usage/history?from=home
   */
  parseUrl(url: string): DeepLink | null {
    if (!url) return null;

    let parsed: URL;
    try {
      parsed = new URL(url);
    } catch {
      // Fall back to scheme-without-:// (selfcare://... is a valid URL scheme, so
      // this is only a safety net for malformed input).
      const schemeIdx = url.indexOf('://');
      if (schemeIdx < 0) return null;
      try {
        parsed = new URL(url.replace(/^[^:]+:\/\//, 'https://'));
      } catch {
        return null;
      }
    }

    const pathname = parsed.pathname === '/' ? '' : parsed.pathname;
    let route = pathname;
    // Prefer a real HTTP domain path; otherwise the scheme host is part of the
    // route (selfcare://usage -> "/usage").
    if (/^https?:$/.test(parsed.protocol) && parsed.hostname) {
      route = `/${pathname}`;
    } else {
      const host = parsed.hostname ?? '';
      if (host) route = `/${host}${pathname}`;
    }

    const params: Record<string, unknown> = {};
    parsed.searchParams.forEach((value, key) => {
      params[key] = coerceParam(value);
    });

    const normalized = this.normalizeRoute(route);
    return { raw: url, route: normalized, params };
  }

  /** True when a deep link resolves to a known route. */
  canHandleDeepLink(url: string): boolean {
    const link = this.parseUrl(url);
    return !!link && !!this.routeMap.has(link.route);
  }

  /** Normalize a route: ensure at least "/" and no double slashes. */
  normalizeRoute(route: string): string {
    if (!route) return '/';
    let r = route.trim();
    if (!r.startsWith('/')) r = `/${r}`;
    return r.replace(/\/{2,}/g, '/');
  }
}

function coerceParam(value: string): unknown {
  if (value === 'true') return true;
  if (value === 'false') return false;
  if (value !== '' && !Number.isNaN(Number(value))) return Number(value);
  return value;
}