/**
 * ComponentRegistry Tests
 *
 * Tests for widget registration, override, and lookup.
 *
 * Covers:
 * - Basic registration
 * - Get / has / getEntry
 * - Override existing registration (with warning)
 * - Aliases (single and chained)
 * - Platform filtering
 * - Min app version filtering
 * - Registered IDs listing
 */

import React from 'react';
import { Text } from 'react-native';
import { ComponentRegistry } from '../../src/components/ComponentRegistry';
import type { WidgetProps } from '../../src/components/ComponentRegistry';

describe('ComponentRegistry', () => {
  let registry: ComponentRegistry;

  // Dummy component for testing
  const TestComponent = (props: WidgetProps) => <Text>{`Test: ${props.id}`}</Text>;
  const AnotherComponent = (props: WidgetProps) => <Text>{`Another: ${props.id}`}</Text>;
  const ThirdComponent = (props: WidgetProps) => <Text>{`Third: ${props.id}`}</Text>;

  beforeEach(() => {
    registry = new ComponentRegistry();
  });

  describe('basic registration', () => {
    it('registers a component', () => {
      registry.register('TestComponent', TestComponent);
      expect(registry.has('TestComponent')).toBe(true);
    });

    it('returns registered component on get', () => {
      registry.register('TestComponent', TestComponent);
      const Component = registry.get('TestComponent');
      expect(Component).toBe(TestComponent);
    });

    it('returns null for unregistered component', () => {
      const Component = registry.get('NotRegistered');
      expect(Component).toBeNull();
    });

    it('returns false from has for unregistered component', () => {
      expect(registry.has('NotRegistered')).toBe(false);
    });
  });

  describe('override', () => {
    it('overwrites existing component with warning', () => {
      const warnSpy = jest.spyOn(console, 'warn').mockImplementation();

      registry.register('TestComponent', TestComponent);
      registry.register('TestComponent', AnotherComponent);

      const Component = registry.get('TestComponent');
      expect(Component).toBe(AnotherComponent);
      expect(warnSpy).toHaveBeenCalledWith(
        expect.stringContaining('Overwriting existing registration: TestComponent')
      );

      warnSpy.mockRestore();
    });

    it('preserves previous registration in getEntry after override', () => {
      jest.spyOn(console, 'warn').mockImplementation();

      registry.register('TestComponent', TestComponent);
      registry.register('TestComponent', AnotherComponent);

      const entry = registry.getEntry('TestComponent');
      expect(entry?.component).toBe(AnotherComponent);
    });
  });

  describe('getEntry', () => {
    it('returns full entry with metadata', () => {
      registry.register('TestComponent', TestComponent, {
        security: 'read',
        description: 'Test widget',
        analyticsEvents: ['test_event'],
        platforms: ['ios', 'android'],
      });

      const entry = registry.getEntry('TestComponent');
      expect(entry).toBeTruthy();
      expect(entry?.component).toBe(TestComponent);
      expect(entry?.security).toBe('read');
      expect(entry?.description).toBe('Test widget');
      expect(entry?.analyticsEvents).toEqual(['test_event']);
      expect(entry?.platforms).toEqual(['ios', 'android']);
    });

    it('defaults security to "display"', () => {
      registry.register('TestComponent', TestComponent);
      const entry = registry.getEntry('TestComponent');
      expect(entry?.security).toBe('display');
    });

    it('returns null for unregistered component', () => {
      const entry = registry.getEntry('NotRegistered');
      expect(entry).toBeNull();
    });
  });

  describe('aliases', () => {
    it('resolves simple alias to canonical ID', () => {
      registry.register('BalanceCard', TestComponent);
      registry.registerAlias('Balance', 'BalanceCard');

      const Component = registry.get('Balance');
      expect(Component).toBe(TestComponent);
    });

    it('resolves chained aliases (A -> B -> C)', () => {
      registry.register('BalanceCard', TestComponent);
      registry.registerAlias('Balance', 'BalanceCard');
      registry.registerAlias('Bal', 'Balance');

      const Component = registry.get('Bal');
      expect(Component).toBe(TestComponent);
    });

    it('has() respects aliases', () => {
      registry.register('BalanceCard', TestComponent);
      registry.registerAlias('Balance', 'BalanceCard');

      expect(registry.has('Balance')).toBe(true);
      expect(registry.has('Bal')).toBe(false); // Alias chain only via get
    });

    it('getEntry() respects aliases', () => {
      registry.register('BalanceCard', TestComponent);
      registry.registerAlias('Balance', 'BalanceCard');

      const entry = registry.getEntry('Balance');
      expect(entry?.component).toBe(TestComponent);
    });

    it('handles alias cycles without infinite loop', () => {
      registry.register('A', TestComponent);
      registry.registerAlias('B', 'A');
      registry.registerAlias('A', 'B'); // Cycle: A -> B -> A

      // Resolution is capped after 10 iterations; the registered entry
      // under the requested id still resolves — the cycle must not hang.
      const Component = registry.get('A');
      expect(Component).toBe(TestComponent);
    });
  });

  describe('platform availability', () => {
    it('returns true when no platforms specified', () => {
      registry.register('TestComponent', TestComponent);
      expect(registry.isAvailable('TestComponent', 'ios', '1.0.0')).toBe(true);
    });

    it('returns true when platform matches', () => {
      registry.register('TestComponent', TestComponent, {
        platforms: ['ios', 'android'],
      });

      expect(registry.isAvailable('TestComponent', 'ios', '1.0.0')).toBe(true);
      expect(registry.isAvailable('TestComponent', 'android', '1.0.0')).toBe(true);
    });

    it('returns false when platform not in list', () => {
      registry.register('TestComponent', TestComponent, {
        platforms: ['ios'],
      });

      expect(registry.isAvailable('TestComponent', 'android', '1.0.0')).toBe(false);
    });

    it('returns false for unregistered component', () => {
      expect(registry.isAvailable('NotRegistered', 'ios', '1.0.0')).toBe(false);
    });
  });

  describe('min app version', () => {
    it('returns true when app version >= min version', () => {
      registry.register('TestComponent', TestComponent, {
        minAppVersion: '1.5.0',
      });

      expect(registry.isAvailable('TestComponent', 'ios', '1.5.0')).toBe(true);
      expect(registry.isAvailable('TestComponent', 'ios', '2.0.0')).toBe(true);
    });

    it('returns false when app version < min version', () => {
      registry.register('TestComponent', TestComponent, {
        minAppVersion: '2.0.0',
      });

      expect(registry.isAvailable('TestComponent', 'ios', '1.5.0')).toBe(false);
    });

    it('handles multi-digit version segments', () => {
      registry.register('TestComponent', TestComponent, {
        minAppVersion: '1.10.0',
      });

      expect(registry.isAvailable('TestComponent', 'ios', '1.9.0')).toBe(false);
      expect(registry.isAvailable('TestComponent', 'ios', '1.10.0')).toBe(true);
    });
  });

  describe('getRegisteredIds', () => {
    it('returns empty array when no registrations', () => {
      expect(registry.getRegisteredIds()).toEqual([]);
    });

    it('returns all registered component IDs', () => {
      registry.register('A', TestComponent);
      registry.register('B', AnotherComponent);
      registry.register('C', ThirdComponent);

      const ids = registry.getRegisteredIds();
      expect(ids).toHaveLength(3);
      expect(ids).toContain('A');
      expect(ids).toContain('B');
      expect(ids).toContain('C');
    });
  });

  describe('multiple registrations', () => {
    it('manages many components independently', () => {
      registry.register('A', TestComponent);
      registry.register('B', AnotherComponent);
      registry.register('C', ThirdComponent);

      expect(registry.get('A')).toBe(TestComponent);
      expect(registry.get('B')).toBe(AnotherComponent);
      expect(registry.get('C')).toBe(ThirdComponent);
    });
  });
});
