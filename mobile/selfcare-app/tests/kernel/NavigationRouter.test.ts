import { NavigationRouter } from '../../src/navigation/NavigationRouter';
import { NavItem } from '../../src/manifest/types';

const NAV: NavItem[] = [
  { id: 'home', label: 'Home', route: '/home' },
  {
    id: 'usage',
    label: 'Usage',
    route: '/usage',
    children: [{ id: 'usage-history', label: 'History', route: '/usage/history' }],
  },
  { id: 'bills', label: 'Bills', route: '/bills' },
];

describe('NavigationRouter (mobile kernel)', () => {
  it('indexes config navigation into a route map', () => {
    const router = new NavigationRouter(jest.fn());
    router.load(NAV);
    expect(router.getItems().length).toBe(3);
    expect(router.getTabs().length).toBe(3);
  });

  it('resolves exact routes and longest-prefix fallbacks', () => {
    const router = new NavigationRouter(jest.fn());
    router.load(NAV);
    expect(router.resolve('/usage')?.id).toBe('usage');
    expect(router.resolve('/usage/history')?.id).toBe('usage-history');
    // Longest-prefix: deep link into a child route still maps to the tab.
    const childOnly = new NavigationRouter(jest.fn());
    childOnly.load(NAV);
    expect(childOnly.resolve('/usage/recharge')).not.toBeNull();
  });

  it('navigates to known routes only', () => {
    const spy = jest.fn();
    const router = new NavigationRouter(spy);
    router.load(NAV);
    expect(router.navigate('/home')).toBe(true);
    expect(router.navigate('/does-not-exist')).toBe(false);
    expect(router.navigate('/usage', { tab: 'history' })).toBe(true);
    expect(spy).toHaveBeenCalledTimes(2);
    expect(spy.mock.calls[1]).toEqual(['/usage', { tab: 'history' }]);
  });

  it('consumes NAVIGATE actions through handleAction', () => {
    const router = new NavigationRouter(jest.fn());
    router.load(NAV);
    expect(
      router.handleAction({ event: 'tap', type: 'NAVIGATE', route: '/home' })
    ).toBe(true);
    expect(
      router.handleAction({ event: 'tap', type: 'NAVIGATE', route: '/missing' })
    ).toBe(false);
    expect(
      router.handleAction({ event: 'tap', type: 'SHOW_TOAST', params: { message: 'hi' } })
    ).toBe(false);
  });

  it('parses selfcare:// deep links into route + params', () => {
    const router = new NavigationRouter(jest.fn());
    const link = router.parseUrl('selfcare://usage/history?from=home&n=3&flag=true');
    expect(link?.route).toBe('/usage/history');
    expect(link?.params).toEqual({ from: 'home', n: 3, flag: true });
  });

  it('parses universal https links keeping the path as route', () => {
    const router = new NavigationRouter(jest.fn());
    const link = router.parseUrl('https://selfcare.dialog.lk/app/usage/history?from=home');
    expect(link?.route).toBe('/app/usage/history');
    expect(link?.params).toEqual({ from: 'home' });
  });

  it('rejects malformed deep links', () => {
    const router = new NavigationRouter(jest.fn());
    expect(router.parseUrl('garbage-no-scheme')).toBeNull();
    expect(router.parseUrl('')).toBeNull();
  });

  it('reports whether a deep link is handleable', () => {
    const router = new NavigationRouter(jest.fn());
    router.load(NAV);
    expect(router.canHandleDeepLink('selfcare://home')).toBe(true);
    expect(router.canHandleDeepLink('selfcare://unknown')).toBe(false);
    expect(router.canHandleDeepLink('garbage')).toBe(false);
  });
});