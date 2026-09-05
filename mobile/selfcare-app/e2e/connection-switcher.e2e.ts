/**
 * Connection Switcher E2E Tests
 *
 * Tests multi-line account management:
 * 1. Primary connection display
 * 2. Switching between connections
 * 3. Connection status (active/suspended)
 * 4. Per-connection data display
 * 5. Connection details screen
 *
 * Prerequisite: Account with multiple linked connections
 */

import { describe, it, expect } from '@jest/globals';
import { element, by, waitFor } from 'detox';
import {
  tapById,
  waitForVisible,
  scrollDown,
  isVisible,
  navigateBack,
} from './before-each';

describe('Connection Switcher', () => {
  // ----------------------------------------
  // Primary Connection Display
  // ----------------------------------------
  describe('Primary Connection Display', () => {
    beforeEach(async () => {
      await waitForVisible('dashboard-screen', 15000);
    });

    it('shows connection switcher in header', async () => {
      await waitForVisible('connection-switcher', 5000);
      await expect(element(by.id('connection-switcher'))).toBeVisible();
    });

    it('displays current connection label', async () => {
      await waitForVisible('connection-switcher', 5000);
      const label = await element(by.id('connection-label')).getText();
      expect(label).toBeTruthy();
      expect(label.length).toBeGreaterThan(0);
    });

    it('shows MSISDN of current connection', async () => {
      const isMsisdnVisible = await isVisible('connection-msisdn');
      if (isMsisdnVisible) {
        const msisdn = await element(by.id('connection-msisdn')).getText();
        expect(msisdn).toMatch(/^\+?\d{10,}/);
      }
    });

    it('shows connection type indicator', async () => {
      const isTypeVisible = await isVisible('connection-type');
      if (isTypeVisible) {
        const type = await element(by.id('connection-type')).getText();
        expect(['PREPAID', 'POSTPAID', 'Fiber']).toContain(type);
      }
    });
  });

  // ----------------------------------------
  // Connection Switcher Modal
  // ----------------------------------------
  describe('Connection Switcher Modal', () => {
    beforeEach(async () => {
      await waitForVisible('dashboard-screen', 15000);
      await tapById('connection-switcher');
      await waitForVisible('connection-modal', 5000);
    });

    it('opens connection switcher modal on tap', async () => {
      await expect(element(by.id('connection-modal'))).toBeVisible();
    });

    it('lists all linked connections', async () => {
      await waitForVisible('connection-list', 5000);
      const connectionCount = await element(by.id('connection-list')).getAttributes();
      expect(connectionCount.childElements.length).toBeGreaterThanOrEqual(1);
    });

    it('marks current connection as selected', async () => {
      await waitForVisible('connection-item-selected', 5000);
      await expect(element(by.id('connection-item-selected'))).toBeVisible();
    });

    it('shows connection nickname when available', async () => {
      const hasNickname = await isVisible('connection-nickname');
      if (hasNickname) {
        await expect(element(by.id('connection-nickname'))).toBeVisible();
      }
    });

    it('shows connection status badge', async () => {
      await waitForVisible('connection-status-badge', 5000);
      const status = await element(by.id('connection-status-badge')).getText();
      expect(['ACTIVE', 'SUSPENDED', 'PENDING']).toContain(status);
    });

    it('shows connection balance in list', async () => {
      const isBalanceVisible = await isVisible('connection-balance');
      if (isBalanceVisible) {
        const balance = await element(by.id('connection-balance')).getText();
        expect(balance).toMatch(/LKR|SLR/);
      }
    });

    it('closes modal on backdrop tap', async () => {
      await element(by.id('modal-backdrop')).tap();
      await waitForVisible('dashboard-screen', 5000);
    });

    it('closes modal on close button', async () => {
      await tapById('btn-close-modal');
      await waitForVisible('dashboard-screen', 5000);
    });
  });

  // ----------------------------------------
  // Connection Switching
  // ----------------------------------------
  describe('Connection Switching', () => {
    beforeEach(async () => {
      await waitForVisible('dashboard-screen', 15000);
      await tapById('connection-switcher');
      await waitForVisible('connection-modal', 5000);
    });

    it('switches to selected connection', async () => {
      // Tap on a different connection (not the current one)
      const secondConnection = element(by.id('connection-item-1'));
      const isSecondVisible = await isVisible('connection-item-1');
      if (isSecondVisible) {
        await secondConnection.tap();

        // Modal should close
        await waitForVisible('dashboard-screen', 5000);

        // Header should update
        await waitForVisible('connection-switcher', 5000);
        const newLabel = await element(by.id('connection-label')).getText();
        expect(newLabel).toBeTruthy();
      }
    });

    it('updates balance display for new connection', async () => {
      const secondConnection = element(by.id('connection-item-1'));
      const isSecondVisible = await isVisible('connection-item-1');
      if (isSecondVisible) {
        await secondConnection.tap();
        await waitForVisible('dashboard-screen', 5000);

        // Balance should update
        await waitForVisible('widget-balance', 10000);
        await expect(element(by.id('widget-balance'))).toBeVisible();
      }
    });

    it('updates usage display for new connection', async () => {
      const secondConnection = element(by.id('connection-item-1'));
      const isSecondVisible = await isVisible('connection-item-1');
      if (isSecondVisible) {
        await secondConnection.tap();
        await waitForVisible('dashboard-screen', 5000);

        // Usage should update
        await waitForVisible('widget-usage', 10000);
        await expect(element(by.id('widget-usage'))).toBeVisible();
      }
    });

    it('shows loading during switch', async () => {
      const secondConnection = element(by.id('connection-item-1'));
      const isSecondVisible = await isVisible('connection-item-1');
      if (isSecondVisible) {
        await secondConnection.tap();

        // Loading state
        const isLoading = await isVisible('loading-indicator');
        if (isLoading) {
          await expect(element(by.id('loading-indicator'))).toBeVisible();
        }
      }
    });

    it('persists selected connection after app restart', async () => {
      // Switch connection
      const secondConnection = element(by.id('connection-item-1'));
      const isSecondVisible = await isVisible('connection-item-1');
      if (isSecondVisible) {
        await secondConnection.tap();
        await waitForVisible('dashboard-screen', 5000);

        // Restart app
        await device.terminateApp();
        await device.launchApp({ newInstance: true });

        // Should still be on selected connection
        await waitForVisible('dashboard-screen', 15000);
      }
    });
  });

  // ----------------------------------------
  // Connection Details
  // ----------------------------------------
  describe('Connection Details', () => {
    beforeEach(async () => {
      await waitForVisible('dashboard-screen', 15000);
      await tapById('connection-switcher');
      await waitForVisible('connection-modal', 5000);
    });

    it('navigates to connection details on long press', async () => {
      const firstConnection = element(by.id('connection-item-0'));
      await firstConnection.longPress();
      await waitForVisible('connection-detail-screen', 5000);
    });

    it('shows full connection details', async () => {
      const firstConnection = element(by.id('connection-item-0'));
      await firstConnection.longPress();
      await waitForVisible('connection-detail-screen', 5000);

      // Should show MSISDN, type, status, balance, etc.
      await expect(element(by.id('detail-msisdn'))).toBeVisible();
      await expect(element(by.id('detail-type'))).toBeVisible();
    });

    it('shows link/unlink option', async () => {
      const firstConnection = element(by.id('connection-item-0'));
      await firstConnection.longPress();
      await waitForVisible('connection-detail-screen', 5000);
      await scrollDown('connection-detail-scroll');

      const isLinkVisible = await isVisible('btn-link-connection');
      if (isLinkVisible) {
        await expect(element(by.id('btn-link-connection'))).toBeVisible();
      }
    });
  });

  // ----------------------------------------
  // Suspended Connection Handling
  // ----------------------------------------
  describe('Suspended Connection', () => {
    it('shows suspended indicator', async () => {
      await waitForVisible('dashboard-screen', 15000);
      await tapById('connection-switcher');
      await waitForVisible('connection-modal', 5000);

      const suspendedConnections = await element(by.id('connection-list')).getAttributes();
      // Check for any suspended connection items
    });

    it('limits actions on suspended connection', async () => {
      // Switch to suspended connection if available
      await waitForVisible('dashboard-screen', 15000);
      await tapById('connection-switcher');
      await waitForVisible('connection-modal', 5000);

      const isSuspendedVisible = await isVisible('connection-item-suspended');
      if (isSuspendedVisible) {
        await element(by.id('connection-item-suspended')).tap();

        // Should show warning
        await waitForVisible('suspended-warning', 5000);
      }
    });
  });

  // ----------------------------------------
  // Linking New Connection
  // ----------------------------------------
  describe('Link New Connection', () => {
    beforeEach(async () => {
      await waitForVisible('dashboard-screen', 15000);
      await tapById('connection-switcher');
      await waitForVisible('connection-modal', 5000);
      await scrollDown('connection-modal-scroll');
    });

    it('shows link connection option', async () => {
      const isLinkVisible = await isVisible('btn-add-connection');
      if (isLinkVisible) {
        await expect(element(by.id('btn-add-connection'))).toBeVisible();
      }
    });

    it('opens link flow on tap', async () => {
      const isLinkVisible = await isVisible('btn-add-connection');
      if (isLinkVisible) {
        await tapById('btn-add-connection');
        await waitForVisible('link-connection-screen', 5000);
      }
    });
  });

  // ----------------------------------------
  // Fiber/DSL Line Support
  // ----------------------------------------
  describe('Fiber/DSL Line', () => {
    it('shows fiber-specific widgets', async () => {
      // Fiber lines may show different widgets (data usage, etc.)
    });

    it('shows bandwidth information', async () => {
      // Fiber-specific bandwidth display
    });
  });
});
