/**
 * ComponentRegistry — Maps server-driven component IDs to React Native components.
 *
 * The registry is the bridge between server configuration (layout manifests) and
 * the actual mobile UI. Each component declares:
 * - The componentId it handles
 * - Its props schema
 * - Loading, error, and empty states
 * - Accessibility semantics
 *
 * Components are registered at app startup from the widget library.
 *
 * Usage:
 *   const registry = new ComponentRegistry();
 *   registry.register('BalanceCard', BalanceCardWidget);
 *   registry.register('UsageSummary', UsageSummaryWidget);
 *
 *   const Component = registry.get('BalanceCard');
 *   if (Component) {
 *     <Component {...props} />
 *   }
 */

import React, { ComponentType, ReactElement } from 'react';

export interface WidgetProps {
  id: string;
  componentId: string;
  variant?: string;
  data?: unknown;
  dataSource?: string;
  props?: Record<string, unknown>;
  onAction?: (action: Action) => void;
  isLoading?: boolean;
  isStale?: boolean;
  error?: string;
  retryable?: boolean;
  onRetry?: () => void;
}

export interface RegisteredComponent {
  componentId: string;
  component: ComponentType<WidgetProps>;
  description?: string;
  platforms?: string[];
  minAppVersion?: string;
  security: 'display' | 'read' | 'write';
  analyticsEvents: string[];
}

export interface Action {
  event: string;
  type: string;
  route?: string;
  journeyId?: string;
  params?: Record<string, unknown>;
  confirmation?: string;
  analyticsEvent?: string;
}

export class ComponentRegistry {
  private registry: Map<string, RegisteredComponent> = new Map();
  private aliases: Map<string, string> = new Map();

  /**
   * Register a widget component.
   */
  register(
    componentId: string,
    component: ComponentType<WidgetProps>,
    options?: Partial<Omit<RegisteredComponent, 'componentId' | 'component'>>
  ): void {
    if (this.registry.has(componentId)) {
      console.warn(`[ComponentRegistry] Overwriting existing registration: ${componentId}`);
    }
    this.registry.set(componentId, {
      componentId,
      component,
      platforms: options?.platforms,
      minAppVersion: options?.minAppVersion,
      security: options?.security || 'display',
      analyticsEvents: options?.analyticsEvents || [],
      description: options?.description,
    });
  }

  /**
   * Register an alias (e.g., "Balance" -> "BalanceCard").
   */
  registerAlias(alias: string, canonicalId: string): void {
    this.aliases.set(alias, canonicalId);
  }

  /**
   * Get a registered component.
   */
  get(componentId: string): ComponentType<WidgetProps> | null {
    const resolved = this.resolve(componentId);
    if (!resolved) return null;
    const entry = this.registry.get(resolved);
    return entry?.component ?? null;
  }

  /**
   * Get the full registration entry for a component.
   */
  getEntry(componentId: string): RegisteredComponent | null {
    const resolved = this.resolve(componentId);
    if (!resolved) return null;
    return this.registry.get(resolved) ?? null;
  }

  /**
   * Check if a component is registered.
   */
  has(componentId: string): boolean {
    return this.registry.has(this.resolve(componentId) ?? componentId);
  }

  /**
   * Check if a component is available for a given platform/version.
   */
  isAvailable(componentId: string, platform: string, appVersion: string): boolean {
    const entry = this.getEntry(componentId);
    if (!entry) return false;

    // Check platform
    if (entry.platforms && entry.platforms.length > 0 && !entry.platforms.includes(platform)) {
      return false;
    }

    // Check minimum app version
    if (entry.minAppVersion) {
      const satisfies = satisfiesMinVersion(appVersion, entry.minAppVersion);
      if (!satisfies) return false;
    }

    return true;
  }

  /**
   * Get all registered component IDs.
   */
  getRegisteredIds(): string[] {
    return Array.from(this.registry.keys());
  }

  /**
   * Resolve an alias to its canonical component ID.
   */
  private resolve(componentId: string): string | undefined {
    let resolved = componentId;
    let iterations = 0;
    while (this.aliases.has(resolved) && iterations < 10) {
      resolved = this.aliases.get(resolved)!;
      iterations++;
    }
    return resolved;
  }
}

/**
 * Compare semantic versions.
 * Returns true if version >= minVersion.
 */
function satisfiesMinVersion(version: string, minVersion: string): boolean {
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
// BUILT-IN WIDGET COMPONENTS
// ============================================================

// Import actual widgets in the app entry point
// This file exports the registry class and the widget interface

export function createWidgetLibrary(): Map<string, RegisteredComponent> {
  const widgets = new Map<string, RegisteredComponent>();

  // Telco Selfcare Widgets (registered at app startup)
  // Balance, Usage, Bills, Packages, Offers, Notifications, Support, etc.
  // See src/components/widgets/ for implementations

  return widgets;
}
