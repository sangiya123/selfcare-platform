/**
 * ConfigSDK Tests
 *
 * Tests config fetching, ETag caching, and last-known-good behavior.
 *
 * Covers:
 * - Initial manifest fetch (200 OK)
 * - ETag round-trip (If-None-Match → 304 Not Modified)
 * - Last-known-good fallback on network error
 * - Force refresh
 * - Cache persistence via MMKV
 */

import axios from 'axios';
import { ConfigSDK } from '../../src/config/ConfigSDK';
import { MMKV } from 'react-native-mmkv';

const mockedAxios = axios as jest.Mocked<typeof axios>;

describe('ConfigSDK', () => {
  let sdk: ConfigSDK;
  const TENANT_ID = 'dialog-lk';
  const BASE_URL = 'http://test.api:8080';

  const MOCK_MANIFEST = {
    version: '1.0.0',
    layouts: [{ id: 'home', name: 'Home', sections: [] }],
    theme: {
      name: 'dialog',
      colors: { primary500: '#6D28D9' },
      typography: {
        fontFamily: 'System',
        sizes: { base: '16px' },
        weights: { normal: 400 },
      },
      radius: '12px',
      spacing: [4, 8, 12, 16, 24],
    },
    navigation: [],
    journeys: [],
    featureFlags: { showNewDashboard: true },
  };

  beforeEach(() => {
    // Clear MMKV storage between tests
    const mmkv = new MMKV({ id: 'omobio-config' });
    mmkv.clearAll();

    // Reset axios mock
    mockedAxios.create.mockReturnValue({
      get: jest.fn(),
      post: jest.fn(),
    } as any);

    sdk = new ConfigSDK(TENANT_ID, BASE_URL);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('refresh()', () => {
    it('fetches manifest from API with X-Tenant-Id header', async () => {
      const getMock = jest.fn().mockResolvedValue({
        data: MOCK_MANIFEST,
        headers: { etag: 'W/"abc123"' },
      });
      mockedAxios.create.mockReturnValue({ get: getMock } as any);

      await sdk.refresh();

      expect(getMock).toHaveBeenCalledTimes(1);
      const [path, options] = getMock.mock.calls[0];
      expect(path).toBe('/config/manifest');
      expect(options.headers['X-Tenant-Id']).toBe(TENANT_ID);
    });

    it('stores manifest in memory after successful fetch', async () => {
      const getMock = jest.fn().mockResolvedValue({
        data: MOCK_MANIFEST,
        headers: { etag: 'W/"abc123"' },
      });
      mockedAxios.create.mockReturnValue({ get: getMock } as any);

      await sdk.refresh();

      const manifest = sdk.getManifest();
      expect(manifest).toEqual(MOCK_MANIFEST);
      expect(sdk.isOnline()).toBe(true);
    });

    it('persists manifest to MMKV cache', async () => {
      const getMock = jest.fn().mockResolvedValue({
        data: MOCK_MANIFEST,
        headers: { etag: 'W/"abc123"' },
      });
      mockedAxios.create.mockReturnValue({ get: getMock } as any);

      await sdk.refresh();

      const mmkv = new MMKV({ id: 'omobio-config' });
      const cached = mmkv.getString(`manifest:${TENANT_ID}`);
      expect(cached).toBeTruthy();
      const parsed = JSON.parse(cached!);
      expect(parsed.version).toBe('1.0.0');
    });

    it('updates lastSync timestamp after successful fetch', async () => {
      const getMock = jest.fn().mockResolvedValue({
        data: MOCK_MANIFEST,
        headers: { etag: 'W/"abc123"' },
      });
      mockedAxios.create.mockReturnValue({ get: getMock } as any);

      const before = Date.now();
      await sdk.refresh();
      const after = Date.now();

      const lastSync = sdk.getLastSyncTime();
      expect(lastSync).toBeTruthy();
      const lastSyncTime = new Date(lastSync!).getTime();
      expect(lastSyncTime).toBeGreaterThanOrEqual(before);
      expect(lastSyncTime).toBeLessThanOrEqual(after);
    });
  });

  describe('ETag caching', () => {
    it('sends If-None-Match header when ETag exists', async () => {
      const getMock = jest.fn().mockResolvedValue({
        data: MOCK_MANIFEST,
        headers: { etag: 'W/"abc123"' },
      });
      mockedAxios.create.mockReturnValue({ get: getMock } as any);

      // First fetch stores ETag
      await sdk.refresh();

      // Second fetch should send If-None-Match
      const sdk2 = new ConfigSDK(TENANT_ID, BASE_URL);
      await sdk2.refresh();

      const secondCallHeaders = getMock.mock.calls[1][1].headers;
      expect(secondCallHeaders['If-None-Match']).toBe('W/"abc123"');
    });

    it('persists ETag across SDK instances', async () => {
      const getMock = jest.fn().mockResolvedValue({
        data: MOCK_MANIFEST,
        headers: { etag: 'W/"persisted-etag"' },
      });
      mockedAxios.create.mockReturnValue({ get: getMock } as any);

      // First instance fetches and stores ETag
      await sdk.refresh();

      // New instance loads ETag from storage
      const sdk2 = new ConfigSDK(TENANT_ID, BASE_URL);
      const headers2: Record<string, string> = {};
      const getMock2 = jest.fn().mockImplementation((_path, opts) => {
        Object.assign(headers2, opts.headers);
        return Promise.resolve({ data: MOCK_MANIFEST, headers: { etag: 'W/"new-etag"' } });
      });
      mockedAxios.create.mockReturnValue({ get: getMock2 } as any);

      await sdk2.refresh();

      expect(headers2['If-None-Match']).toBe('W/"persisted-etag"');
    });

    it('handles 304 Not Modified by keeping cached manifest', async () => {
      // First fetch
      const getMock = jest.fn()
        .mockResolvedValueOnce({
          data: MOCK_MANIFEST,
          headers: { etag: 'W/"abc123"' },
        })
        .mockResolvedValueOnce({
          status: 304,
          data: null,
          headers: {},
        });

      mockedAxios.create.mockReturnValue({ get: getMock } as any);

      await sdk.refresh();
      const manifestBefore = sdk.getManifest();

      const sdk2 = new ConfigSDK(TENANT_ID, BASE_URL);
      await sdk2.refresh();

      // Should still have the original manifest
      expect(sdk2.getManifest()).toEqual(manifestBefore);
    });
  });

  describe('last-known-good fallback', () => {
    it('uses cached manifest when network fails', async () => {
      // First fetch succeeds
      const getMock = jest.fn()
        .mockResolvedValueOnce({
          data: MOCK_MANIFEST,
          headers: { etag: 'W/"abc123"' },
        })
        .mockRejectedValueOnce(new Error('Network error'));

      mockedAxios.create.mockReturnValue({ get: getMock } as any);

      await sdk.refresh();
      const cachedManifest = sdk.getManifest();

      // Second fetch fails but cached manifest should still be available
      const sdk2 = new ConfigSDK(TENANT_ID, BASE_URL);
      await sdk2.refresh();

      expect(sdk2.getManifest()).toEqual(cachedManifest);
      expect(sdk2.isOnline()).toBe(true); // Still online due to cached manifest
    });

    it('throws error when no cached manifest and network fails', async () => {
      const getMock = jest.fn().mockRejectedValue(new Error('Network error'));
      mockedAxios.create.mockReturnValue({ get: getMock } as any);

      const sdkNoCache = new ConfigSDK('unknown-tenant', BASE_URL);
      await expect(sdkNoCache.refresh()).rejects.toThrow('Network error');
      expect(sdkNoCache.isOnline()).toBe(false);
    });

    it('reports online=false when no manifest has ever been loaded', () => {
      const freshSdk = new ConfigSDK('never-loaded', BASE_URL);
      expect(freshSdk.isOnline()).toBe(false);
    });
  });

  describe('forceRefresh()', () => {
    it('clears ETag before fetching', async () => {
      const getMock = jest.fn()
        .mockResolvedValueOnce({
          data: MOCK_MANIFEST,
          headers: { etag: 'W/"abc123"' },
        })
        .mockResolvedValueOnce({
          data: { ...MOCK_MANIFEST, version: '1.0.1' },
          headers: { etag: 'W/"def456"' },
        });

      mockedAxios.create.mockReturnValue({ get: getMock } as any);

      await sdk.refresh();

      // Force refresh should NOT include If-None-Match
      const secondCallHeaders = getMock.mock.calls[1][1].headers;
      expect(secondCallHeaders['If-None-Match']).toBe('W/"abc123"');

      await sdk.forceRefresh();

      // After forceRefresh, the latest fetch should not have If-None-Match
      const thirdCallHeaders = getMock.mock.calls[2][1].headers;
      expect(thirdCallHeaders['If-None-Match']).toBeUndefined();

      expect(sdk.getManifest()?.version).toBe('1.0.1');
    });
  });

  describe('offline mode', () => {
    it('initializes with cached manifest on cold start', async () => {
      // Pre-populate cache
      const getMock = jest.fn().mockResolvedValue({
        data: MOCK_MANIFEST,
        headers: { etag: 'W/"cached"' },
      });
      mockedAxios.create.mockReturnValue({ get: getMock } as any);

      const sdk1 = new ConfigSDK(TENANT_ID, BASE_URL);
      await sdk1.refresh();

      // New instance should load from cache
      const sdk2 = new ConfigSDK(TENANT_ID, BASE_URL);
      expect(sdk2.getManifest()).toEqual(MOCK_MANIFEST);
      expect(sdk2.isOnline()).toBe(true);
    });

    it('handles corrupted cache gracefully', () => {
      const mmkv = new MMKV({ id: 'omobio-config' });
      mmkv.set(`manifest:${TENANT_ID}`, 'invalid{json');

      // Should not throw, should return null
      const sdk2 = new ConfigSDK(TENANT_ID, BASE_URL);
      expect(sdk2.getManifest()).toBeNull();
    });
  });
});
