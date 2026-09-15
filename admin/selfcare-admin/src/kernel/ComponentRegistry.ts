/**
 * selfcare kernel (web) — Component Registry.
 *
 * Maps server-driven component IDs to React components for the selfcare Studio
 * preview. Mirrors the mobile kernel (`mobile/selfcare-app/src/components/ComponentRegistry.ts`).
 */

import React, { ComponentType } from 'react';
import { WebWidgetProps } from './types';

export interface RegisteredComponent {
  componentId: string;
  component: ComponentType<WebWidgetProps>;
  description?: string;
  platforms?: string[];
  minAppVersion?: string;
  security: 'display' | 'read' | 'write';
  analyticsEvents: string[];
}

export class ComponentRegistry {
  private registry: Map<string, RegisteredComponent> = new Map();
  private aliases: Map<string, string> = new Map();

  register(
    componentId: string,
    component: ComponentType<WebWidgetProps>,
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

  registerAlias(alias: string, canonicalId: string): void {
    this.aliases.set(alias, canonicalId);
  }

  get(componentId: string): ComponentType<WebWidgetProps> | null {
    const resolved = this.resolve(componentId);
    if (!resolved) return null;
    const entry = this.registry.get(resolved);
    return entry?.component ?? null;
  }

  getEntry(componentId: string): RegisteredComponent | null {
    const resolved = this.resolve(componentId);
    if (!resolved) return null;
    return this.registry.get(resolved) ?? null;
  }

  has(componentId: string): boolean {
    return this.registry.has(this.resolve(componentId) ?? componentId);
  }

  isAvailable(componentId: string, platform: string, appVersion: string): boolean {
    const entry = this.getEntry(componentId);
    if (!entry) return false;

    if (entry.platforms && entry.platforms.length > 0 && !entry.platforms.includes(platform)) {
      return false;
    }

    if (entry.minAppVersion) {
      if (!satisfiesMinVersion(appVersion, entry.minAppVersion)) return false;
    }

    return true;
  }

  getRegisteredIds(): string[] {
    return Array.from(this.registry.keys());
  }

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

/** True when `version` >= `minVersion` (semver compare). */
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