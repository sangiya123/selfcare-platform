/**
 * selfcare kernel (web) — Layout Renderer.
 *
 * Server-driven section renderer for the selfcare Studio preview:
 * - stable ordering by `order`
 * - visibility gates (feature flag / LOB / segment)
 * - component availability gates (platform / minAppVersion)
 * - per-widget data-source provisioning with loading/error states
 * - fallback rendering for unknown component IDs
 *
 * One slow/failed widget never fails the page (ADR-008 partial response).
 * Mirrors the mobile kernel (`mobile/selfcare-app/src/renderer/LayoutRenderer.tsx`).
 */

import React, { useEffect, useMemo, useState } from 'react';
import { ComponentRegistry, satisfiesMinVersion } from './ComponentRegistry';
import {
  DataSourceResolver,
  RenderContext,
  Section,
  WebAction,
  WebWidgetProps,
  VisibilityCondition,
} from './types';

export interface LayoutRendererProps {
  sections: Section[];
  registry: ComponentRegistry;
  dataSourceResolver?: DataSourceResolver;
  context?: RenderContext;
  platform?: string;
  appVersion?: string;
  onAction?: (action: WebAction) => void;
  className?: string;
}

interface SectionState {
  data?: unknown;
  error?: string;
  isLoading: boolean;
}

/** Sort sections by render order (stable; ties keep declaration order). */
export function sortSections(sections: Section[]): Section[] {
  return [...sections].sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
}

/** Visibility gate: feature flag, LOB, and segment must all pass when set. */
export function isSectionVisible(
  section: Section,
  context: RenderContext,
  features?: Record<string, boolean>
): boolean {
  const fixture = context.features ?? features ?? {};
  const when = section.visibleWhen as VisibilityCondition | undefined;
  if (!when) return true;

  if (when.feature && fixture[when.feature] === false) return false;
  if (when.lob && when.lob !== context.lob) return false;
  if (when.segment && when.segment !== context.segment) return false;
  return true;
}

/** Availability gate: explicitly-listed platform + minimum app version. */
export function isSectionAvailable(
  section: Section,
  registry: ComponentRegistry,
  platform: string,
  appVersion: string
): boolean {
  const availability = section.availability;
  if (availability?.platform?.length && !availability.platform.includes(platform)) return false;

  const minVersion = availability?.minAppVersion;
  if (minVersion && !satisfiesMinVersion(appVersion, minVersion)) return false;

  const entry = registry.getEntry(section.component);
  if (entry?.minAppVersion && !satisfiesMinVersion(appVersion, entry.minAppVersion)) return false;
  if (entry?.platforms?.length && !entry.platforms.includes(platform)) return false;

  return true;
}

/** Resolve the sections that should actually render for this context. */
export function resolveRenderableSections(
  sections: Section[],
  registry: ComponentRegistry,
  context: RenderContext,
  platform: string,
  appVersion: string
): Section[] {
  return sortSections(sections).filter(
    (section) =>
      isSectionVisible(section, context) && isSectionAvailable(section, registry, platform, appVersion)
  );
}

export function LayoutRenderer({
  sections,
  registry,
  dataSourceResolver,
  context,
  platform = 'web',
  appVersion = '1.0.0',
  onAction,
  className,
}: LayoutRendererProps): JSX.Element {
  const [states, setStates] = useState<Record<string, SectionState>>({});

  const renderable = useMemo(
    () => resolveRenderableSections(sections, registry, context ?? {}, platform, appVersion),
    [sections, registry, context, platform, appVersion]
  );

  useEffect(() => {
    const mounted = true;
    const next: Record<string, SectionState> = {};
    renderable.forEach((section) => {
      const existing = states[section.id];
      next[section.id] = existing ?? { isLoading: !!section.dataSource && !!dataSourceResolver };
    });
    setStates(next);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [renderable]);

  return (
    <div className={className} data-testid="web-layout">
      {renderable.map((section) => (
        <WebSection
          key={section.id}
          section={section}
          registry={registry}
          state={states[section.id] ?? { isLoading: !!section.dataSource && !!dataSourceResolver }}
          dataSourceResolver={dataSourceResolver}
          context={context ?? {}}
          onAction={onAction}
        />
      ))}
    </div>
  );
}

function WebSection({
  section,
  registry,
  state,
  dataSourceResolver,
  context,
  onAction,
}: {
  section: Section;
  registry: ComponentRegistry;
  state: SectionState;
  dataSourceResolver?: DataSourceResolver;
  context: RenderContext;
  onAction?: (action: WebAction) => void;
}): JSX.Element {
  const Component = registry.get(section.component);

  const { data, error, isLoading } = state;

  if (isLoading) {
    return (
      <div className="animate-pulse rounded-lg border border-gray-200 bg-white p-4" data-testid={`skeleton-${section.id}`}>
        <div className="h-4 w-32 rounded bg-gray-200" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700" data-testid={`error-${section.id}`}>
        {section.component}: {error}
      </div>
    );
  }

  if (!Component) {
    return (
      <div className="rounded-lg border border-dashed border-gray-300 bg-white p-4 text-sm text-gray-500" data-testid={`fallback-${section.id}`}>
        Unknown component: {section.component}
      </div>
    );
  }

  const widgetProps: WebWidgetProps = {
    id: section.id,
    componentId: section.component,
    variant: section.variant,
    data,
    dataSource: section.dataSource,
    props: section.props,
    onAction: onAction as WebWidgetProps['onAction'],
    isStale: false,
    error: error ?? undefined,
    retryable: false,
  };

  return (
    <div data-testid={`section-${section.id}`}>
      <Component {...widgetProps} />
    </div>
  );
}

/**
 * React hook that provisions data for a preview section. Used by the preview
 * shell to keep a section's data fresh on demand.
 */
export function useSectionData(
  dataSource: string | undefined,
  resolver: DataSourceResolver | undefined,
  context: RenderContext
): { data?: unknown; error?: string; isLoading: boolean; refresh: () => void } {
  const [result, setResult] = useState<{ data?: unknown; error?: string; isLoading: boolean }>(() => ({
    isLoading: !!dataSource && !!resolver,
  }));

  const load = useMemo(
    () => () => {
      if (!dataSource || !resolver) {
        setResult({ data: undefined, isLoading: false });
        return;
      }
      setResult({ isLoading: true });
      resolver
        .resolve(dataSource, { tenantId: context.tenantId, connectionId: context.connectionId })
        .then((data) => setResult({ data, isLoading: false }))
        .catch((e: unknown) => {
          const message = e instanceof Error ? e.message : 'Failed to load data';
          setResult({ error: message, isLoading: false });
        });
    },
    [dataSource, resolver, context.tenantId, context.connectionId]
  );

  useEffect(() => {
    load();
  }, [load]);

  return { ...result, refresh: load };
}

export default LayoutRenderer;