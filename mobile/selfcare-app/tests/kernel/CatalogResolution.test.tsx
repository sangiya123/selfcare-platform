import React from 'react';
import { Text } from 'react-native';
import { render } from '@testing-library/react-native';
import { ComponentRegistry, CatalogComponent, PrimitiveProps } from '../../src/components/ComponentRegistry';

/**
 * Catalog-resolution regression tests for the midend-parity home layout
 * components (PackageDetailCard / QuickAddonList / CreditLimitCard).
 *
 * These components ship as `component_catalog` recipes in the Dialog seed
 * (`deploy/local/mongo-init/01-tenant-dialog.js`) mapped to universal
 * primitives (ADR-009 — zero feature-specific code in the app). They must
 * resolve through ComponentRegistry.getOrResolve at runtime: catalog config
 * merged over the section props, catalog platforms/minAppVersion honoured,
 * and unresolvable recipes falling back to null without crashing.
 */
describe('ComponentRegistry catalog resolution (getOrResolve)', () => {
  let registry: ComponentRegistry;

  // Stub primitive that mirrors UniversalBox/UniversalText's config merge:
  // it exposes the merged `props` so tests can assert catalog + section props.
  const ReceivingPrimitive = ({ props, id }: PrimitiveProps): React.JSX.Element => (
    <Text testID="resolved">{JSON.stringify({ merge: props || {}, id: id ?? null })}</Text>
  );

  const catalog: CatalogComponent[] = [
    {
      componentId: 'PackageDetailCard',
      primitive: 'UniversalBox',
      config: {
        renderType: 'card',
        padding: 'md',
        children: [
          { primitive: 'UniversalText', config: { variant: 'title', value: { path: 'packageName' } } },
          { primitive: 'UniversalText', config: { variant: 'caption', value: { path: 'activatedAt' }, format: 'relative-time' } },
          { primitive: 'UniversalText', config: { variant: 'caption', value: { path: 'autoRenew' } } },
        ],
      },
      platforms: ['android', 'ios', 'huawei', 'web'],
      minAppVersion: '1.0.0',
      security: 'read',
    },
    {
      componentId: 'QuickAddonList',
      primitive: 'UniversalList',
      config: {
        layout: 'horizontal',
        item: {
          primitive: 'UniversalBox',
          config: {
            renderType: 'card',
            padding: 'sm',
            children: [
              { primitive: 'UniversalText', config: { variant: 'subtitle', value: { path: 'name' } } },
              { primitive: 'UniversalText', config: { variant: 'caption', value: { path: 'price' }, format: 'currency' } },
            ],
          },
        },
      },
      platforms: ['android', 'ios', 'huawei', 'web'],
      minAppVersion: '1.0.0',
      security: 'display',
    },
    {
      componentId: 'CreditLimitCard',
      primitive: 'UniversalBox',
      config: {
        renderType: 'card',
        padding: 'md',
        children: [
          { primitive: 'UniversalText', config: { variant: 'value', value: { path: 'creditLimit.amount' }, format: 'currency' } },
          { primitive: 'UniversalText', config: { variant: 'caption', value: { path: 'creditLimit.usagePercent' } } },
        ],
      },
      platforms: ['android', 'ios', 'huawei', 'web'],
      minAppVersion: '1.0.0',
      security: 'read',
    },
    { componentId: 'Unresolvable', primitive: 'NotAPrimitive', platforms: ['ios'] },
  ];

  beforeEach(() => {
    // Reset the primitive registry shared through globalThis so tests are isolated.
    delete (globalThis as { __SELFCARE_PRIMITIVES__?: unknown }).__SELFCARE_PRIMITIVES__;
    registry = new ComponentRegistry();
    registry.registerPrimitive('UniversalBox', ReceivingPrimitive as never);
    registry.registerPrimitive('UniversalList', ReceivingPrimitive as never);
    registry.loadCatalog(catalog);
  });

  it('resolves all three midend-parity recipes to renderable components', () => {
    for (const componentId of ['PackageDetailCard', 'QuickAddonList', 'CreditLimitCard']) {
      const Component = registry.getOrResolve(componentId);
      expect(Component).not.toBeNull();
      expect(typeof Component).toBe('function');
    }
  });

  it('returns null when the recipe primitive is not registered', () => {
    expect(registry.getOrResolve('Unresolvable')).toBeNull();
  });

  it('returns null before the catalog is loaded', () => {
    const fresh = new ComponentRegistry();
    fresh.registerPrimitive('UniversalBox', ReceivingPrimitive as never);
    expect(fresh.getOrResolve('PackageDetailCard')).toBeNull();
  });

  it('merges catalog config over section props (section props win)', () => {
    const Component = registry.getOrResolve('PackageDetailCard')!;
    const { getByTestId } = render(
      React.createElement(Component, {
        id: 'package-details',
        componentId: 'PackageDetailCard',
        data: { packageName: 'Data 1GB', activatedAt: '2026-09-01T00:00:00Z', autoRenew: true },
        props: { showAutoRenew: true },
      })
    );

    const merged = JSON.parse(getByTestId('resolved').props.children as string) as {
      merge: Record<string, unknown>;
      id: string | null;
    };
    expect(merged.id).toBe('package-details');
    expect(merged.merge).toEqual(
      expect.objectContaining({ renderType: 'card', padding: 'md', showAutoRenew: true })
    );
  });

  it('honours catalog platforms and minAppVersion in isAvailable', () => {
    expect(registry.isAvailable('PackageDetailCard', 'android', '1.0.0')).toBe(true);
    expect(registry.isAvailable('PackageDetailCard', 'android', '0.9.0')).toBe(false);
    expect(registry.isAvailable('PackageDetailCard', 'wearos', '9.0.0')).toBe(false);

    // Direct registration (BalanceCard-style) still takes precedence for custom ids.
    registry.register('OpCodeOverride', ReceivingPrimitive as never, {
      platforms: ['ios'],
      minAppVersion: '2.0.0',
    });
    expect(registry.getOrResolve('OpCodeOverride')).toBe(ReceivingPrimitive);
  });

  it('caches resolved catalog components by id', () => {
    const first = registry.getOrResolve('CreditLimitCard');
    const second = registry.getOrResolve('CreditLimitCard');
    expect(first).toBe(second);
  });
});