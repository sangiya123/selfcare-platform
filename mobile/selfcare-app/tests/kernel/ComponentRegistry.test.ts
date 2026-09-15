import { ComponentRegistry } from '../../src/components/ComponentRegistry';

const Dummy = () => null;

describe('ComponentRegistry (v2 kernel)', () => {
  const registry = () => new ComponentRegistry();

  it('registers and resolves a component by id', () => {
    const r = registry();
    r.register('BalanceCard', Dummy, { platforms: ['android', 'ios'] });
    expect(r.has('BalanceCard')).toBe(true);
    expect(r.get('BalanceCard')).toBe(Dummy);
  });

  it('returns null for unknown components', () => {
    expect(registry().get('Nope')).toBeNull();
  });

  it('resolves aliases to the canonical id', () => {
    const r = registry();
    r.register('UsageSummary', Dummy);
    r.registerAlias('Usage', 'UsageSummary');
    expect(r.get('Usage')).toBe(Dummy);
    expect(r.getEntry('Usage')?.componentId).toBe('UsageSummary');
  });

  it('respects platform availability', () => {
    const r = registry();
    r.register('IosOnly', Dummy, { platforms: ['ios'] });
    expect(r.isAvailable('IosOnly', 'ios', '1.0.0')).toBe(true);
    expect(r.isAvailable('IosOnly', 'android', '1.0.0')).toBe(false);
  });

  it('respects min app version availability', () => {
    const r = registry();
    r.register('V4Widget', Dummy, { minAppVersion: '4.2.0' });
    expect(r.isAvailable('V4Widget', 'android', '5.0.0')).toBe(true);
    expect(r.isAvailable('V4Widget', 'android', '4.1.9')).toBe(false);
  });

  it('defaults unknown components to unavailable', () => {
    expect(registry().isAvailable('Missing', 'android', '1.0.0')).toBe(false);
  });
});