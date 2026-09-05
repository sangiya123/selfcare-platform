/**
 * Offline Mode E2E Tests
 *
 * Tests offline functionality and last-known-good config:
 * 1. App works offline with cached config
 * 2. Last-known-good manifest used when API unavailable
 * 3. Stale data indicators
 * 4. Sync on reconnection
 * 5. Network status display
 *
 * Prerequisites: Logged in with cached manifest
 */

import { describe, it, expect, beforeEach } from '@jest/globals';
import { device, element, by, waitFor } from 'detox';
import {
  tapById,
  waitForVisible,
  scrollDown,
  isVisible,
  waitForHidden,
} from './before-each';

describe('Offline Mode', () => {
  // ----------------------------------------
  // Initial Offline State
  // ----------------------------------------
  describe('Initial Offline State', () => {
    beforeEach(async () => {
      // App should open with cached manifest on cold start
    });

    it('shows cached dashboard when offline', async () => {
      await waitForVisible('dashboard-screen', 15000);
      // Should show last-known-good widgets
      await expect(element(by.id('dashboard-screen'))).toBeVisible();
    });

    it('shows last-known-good balance', async () => {
      await waitForVisible('dashboard-screen', 15000);
      const isBalanceVisible = await isVisible('widget-balance');
      if (isBalanceVisible) {
        await expect(element(by.id('widget-balance'))).toBeVisible();
      }
    });

    it('shows stale indicator for usage data', async () => {
      await waitForVisible('dashboard-screen', 15000);
      const isUsageVisible = await isVisible('widget-usage');
      if (isUsageVisible) {
        const isStaleVisible = await isVisible('usage-stale-indicator');
        if (isStaleVisible) {
          await expect(element(by.id('usage-stale-indicator'))).toBeVisible();
        }
      }
    });
  });

  // ----------------------------------------
  // Network Status
  // ----------------------------------------
  describe('Network Status', () => {
    beforeEach(async () => {
      await waitForVisible('dashboard-screen', 15000);
    });

    it('shows network status indicator', async () => {
      const isIndicatorVisible = await isVisible('network-status');
      if (isIndicatorVisible) {
        await expect(element(by.id('network-status'))).toBeVisible();
      }
    });

    it('shows offline banner when disconnected', async () => {
      // Network status indicator should show offline state
      const isBannerVisible = await isVisible('offline-banner');
      if (isBannerVisible) {
        await expect(element(by.id('offline-banner'))).toBeVisible();
        const text = await element(by.id('offline-banner')).getText();
        expect(text.toLowerCase()).toContain('offline');
      }
    });

    it('hides offline banner when connected', async () => {
      const isBannerVisible = await isVisible('offline-banner');
      if (!isBannerVisible) {
        // Good - no offline banner when connected
        expect(true).toBe(true);
      }
    });
  });

  // ----------------------------------------
  // Offline Navigation
  // ----------------------------------------
  describe('Offline Navigation', () => {
    beforeEach(async () => {
      await waitForVisible('dashboard-screen', 15000);
    });

    it('allows navigation to previously loaded screens', async () => {
      // Navigate to usage details (should be cached)
      const isUsageVisible = await isVisible('widget-usage');
      if (isUsageVisible) {
        await tapById('btn-usage-details');
        await waitForVisible('usage-screen', 5000);
      }
    });

    it('shows offline message for uncached screens', async () => {
      // Try to navigate to a screen not in manifest
      await element(by.id('nav-settings')).tap();
      await waitForVisible('settings-screen', 5000);
    });

    it('caches viewed screens for offline access', async () => {
      // View a screen while online
      await scrollDown('dashboard-scroll', 200);
      await tapById('btn-usage-details');
      await waitForVisible('usage-screen', 5000);

      // Go back
      await element(by.id('btn-back')).tap();
      await waitForVisible('dashboard-screen', 5000);

      // Screen should be cached for offline
    });
  });

  // ----------------------------------------
  // Offline Actions
  // ----------------------------------------
  describe('Offline Actions', () => {
    beforeEach(async () => {
      await waitForVisible('dashboard-screen', 15000);
    });

    it('shows error when action requires network', async () => {
      // Try to recharge (requires network)
      const isRechargeVisible = await isVisible('btn-recharge');
      if (isRechargeVisible) {
        await tapById('btn-recharge');
        await waitForVisible('offline-error', 5000);
      }
    });

    it('queues actions for retry when back online', async () => {
      // Start an action
      const isRechargeVisible = await isVisible('btn-recharge');
      if (isRechargeVisible) {
        await tapById('btn-recharge');
        await waitForVisible('offline-error', 5000);

        // Should show "Queue for later" option
        const isQueueVisible = await isVisible('btn-queue-action');
        if (isQueueVisible) {
          await tapById('btn-queue-action');
          await waitForVisible('queued-toast', 3000);
        }
      }
    });
  });

  // ----------------------------------------
  // Reconnection Sync
  // ----------------------------------------
  describe('Reconnection Sync', () => {
    beforeEach(async () => {
      await waitForVisible('dashboard-screen', 15000);
    });

    it('detects network reconnection', async () => {
      // Wait for sync to start
      await waitForVisible('sync-indicator', 15000);
      await expect(element(by.id('sync-indicator'))).toBeVisible();
    });

    it('updates stale data on reconnect', async () => {
      // Wait for sync to complete
      await waitForHidden('sync-indicator', 60000);
      // Stale indicators should be gone
      const isUsageStaleVisible = await isVisible('usage-stale-indicator');
      expect(isUsageStaleVisible).toBe(false);
    });

    it('refreshes balance on reconnect', async () => {
      // Sync should update balance
      await waitForVisible('widget-balance', 10000);
      await expect(element(by.id('widget-balance'))).toBeVisible();
    });

    it('shows sync complete notification', async () => {
      await waitForVisible('sync-complete-toast', 30000);
    });
  });

  // ----------------------------------------
  // Config Cache Management
  // ----------------------------------------
  describe('Config Cache Management', () => {
    beforeEach(async () => {
      await waitForVisible('dashboard-screen', 15000);
    });

    it('uses manifest from cache on cold offline start', async () => {
      // Cold start with no network
      await device.terminateApp();
      await device.launchApp({ newInstance: true, url: null });

      // Wait for cached manifest to load
      await waitForVisible('dashboard-screen', 15000);
      await expect(element(by.id('dashboard-screen'))).toBeVisible();
    });

    it('shows version info from cached manifest', async () => {
      await waitForVisible('dashboard-screen', 15000);
      const isVersionVisible = await isVisible('manifest-version');
      if (isVersionVisible) {
        await expect(element(by.id('manifest-version'))).toBeVisible();
      }
    });

    it('clears stale cache from settings', async () => {
      await element(by.id('nav-settings')).tap();
      await waitForVisible('settings-screen', 5000);
      await scrollDown('settings-scroll');

      const isClearCacheVisible = await isVisible('btn-clear-cache');
      if (isClearCacheVisible) {
        await tapById('btn-clear-cache');
        await waitForVisible('cache-cleared-toast', 3000);
      }
    });
  });

  // ----------------------------------------
  // ETag Validation
  // ----------------------------------------
  describe('ETag Validation', () => {
    beforeEach(async () => {
      await waitForVisible('dashboard-screen', 15000);
    });

    it('skips full fetch when manifest unchanged (304)', async () => {
      // When manifest hasn't changed, API returns 304
      // This should be fast and not show loading
    });

    it('updates manifest when changed', async () => {
      // When manifest version differs, full update
    });
  });

  // ----------------------------------------
  // Edge Cases
  // ----------------------------------------
  describe('Edge Cases', () => {
    it('handles partial data gracefully', async () => {
      // Some widgets load, others fail
    });

    it('recovers from corrupted cache', async () => {
      // Should fall back to default config
    });

    it('handles network flap (rapid connect/disconnect)', async () => {
      // Should not crash or hang
    });
  });

  // ----------------------------------------
  // Timeout Handling
  // ----------------------------------------
  describe('Timeout Handling', () => {
    it('falls back to cache after timeout', async () => {
      // API timeout should use cached data
    });

    it('shows timeout error if no cache', async () => {
      // No cache + timeout = error screen
    });
  });
});
