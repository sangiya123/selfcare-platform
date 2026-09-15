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
 *
 * Catalog resolution: catalog entries with a `primitive` (e.g. "UniversalBox")
 * resolve to the primitive wrapped with the immutable config recipe. Features
 * compose existing primitives — no app code per feature (ADR-009).
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

/**
 * Props accepted by universal primitives. Identical to WidgetProps but without
 * the id/componentId requirement — primitives are composable as nested children
 * (data + props + onAction only) AND as top-level registered widgets.
 */
export type PrimitiveProps = Omit<WidgetProps, 'id' | 'componentId'> &
  Partial<Pick<WidgetProps, 'id' | 'componentId'>>;

export interface RegisteredComponent {
  componentId: string;
  component: ComponentType<WidgetProps>;
  description?: string;
  platforms?: string[];
  minAppVersion?: string;
  security: 'display' | 'read' | 'write';
  analyticsEvents: string[];
  isPrimitive?: boolean;
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

/** Component catalog entry from manifest */
export interface CatalogComponent {
  componentId: string;
  /** Universal primitive (e.g. "UniversalBox"). Absent = availability-gate only, no resolve. */
  primitive?: string;
  config?: Record<string, unknown>;
  description?: string;
  platforms?: string[];
  minAppVersion?: string;
  security?: 'display' | 'read' | 'write';
  analyticsEvents?: string[];
}

export class ComponentRegistry {
  private registry: Map<string, RegisteredComponent> = new Map();
  private aliases: Map<string, string> = new Map();
  private catalog: Map<string, CatalogComponent> = new Map();
  private resolvedCache: Map<string, ComponentType<WidgetProps>> = new Map();

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
   * Checks direct registrations AND catalog-resolved components.
   */
  isAvailable(componentId: string, platform: string, appVersion: string): boolean {
    const resolved = this.resolve(componentId);
    const entry = resolved ? this.registry.get(resolved) : undefined;
    const catalogEntry = this.catalog.get(componentId);

    if (!entry && !catalogEntry) return false;

    const platforms = entry?.platforms ?? catalogEntry?.platforms;
    const minAppVersion = entry?.minAppVersion ?? catalogEntry?.minAppVersion;

    // Check platform
    if (platforms && platforms.length > 0 && !platforms.includes(platform)) {
      return false;
    }

    // Check minimum app version
    if (minAppVersion) {
      const satisfies = satisfiesMinVersion(appVersion, minAppVersion);
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

  /**
   * Load component catalog from manifest.
   * Called when manifest is refreshed.
   */
  loadCatalog(components: CatalogComponent[]): void {
    this.catalog.clear();
    this.resolvedCache.clear();
    for (const comp of components) {
      this.catalog.set(comp.componentId, comp);
    }
  }

  /**
   * Get a component, resolving from catalog if not directly registered.
   * This is the main entry point for LayoutRenderer.
   */
  getOrResolve(componentId: string): ComponentType<WidgetProps> | null {
    // First check direct registration
    const direct = this.get(componentId);
    if (direct) return direct;

    // Check cache
    if (this.resolvedCache.has(componentId)) {
      return this.resolvedCache.get(componentId)!;
    }

    // Resolve from catalog
    const catalogEntry = this.catalog.get(componentId);
    if (catalogEntry && catalogEntry.primitive) {
      const Primitive = this.getPrimitiveComponent(catalogEntry.primitive);
      if (Primitive) {
        const ResolvedComponent = (function ResolvedComponent(props: WidgetProps) {
          return React.createElement(Primitive, {
            ...props,
            props: { ...(catalogEntry.config || {}), ...props.props },
          });
        }) as ComponentType<WidgetProps>;
        this.resolvedCache.set(componentId, ResolvedComponent);
        return ResolvedComponent;
      }
    }

    return null;
  }

  /**
   * Get primitive component by name.
   */
  private getPrimitiveComponent(primitive: string): ComponentType<any> | null {
    // This will be populated by widgetLibrary
    return (globalThis as any).__SELFCARE_PRIMITIVES__?.[primitive] ?? null;
  }

  /**
   * Register a primitive component for catalog resolution. Universal
   * primitives register here from the widget library
   * (src/components/widgetLibrary.ts).
   */
  registerPrimitive(name: string, component: ComponentType<any>): void {
    if (!(globalThis as any).__SELFCARE_PRIMITIVES__) {
      (globalThis as any).__SELFCARE_PRIMITIVES__ = {};
    }
    (globalThis as any).__SELFCARE_PRIMITIVES__[name] = component;
  }
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

// Universal primitives are registered via registerPrimitive() from the widget
// library (src/components/widgetLibrary.ts).
