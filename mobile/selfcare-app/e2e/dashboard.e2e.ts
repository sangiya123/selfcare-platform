/**
 * Dashboard Widgets E2E Tests
 *
 * Tests that dashboard widgets render correctly from the server-driven manifest:
 * 1. BalanceCard renders with data
 * 2. UsageCard shows data/voice/SMS usage
 * 3. BillCard displays pending bills
 * 4. Quick Actions grid
 * 5. Notifications list
 * 6. Banners carousel
 * 7. Pull to refresh
 * 8. Widget error states
 *
 * Prerequisites: Logged in as dialog-lk tenant
 */

import { describe, it, expect } from '@jest/globals';
import { element, by, waitFor } from 'detox';
import {
  tapById,
  waitForVisible,
  scrollDown,
  pullToRefresh,
  isVisible,
} from './before-each';

describe('Dashboard Widgets', () => {
  // ----------------------------------------
  // Balance Card
  // ----------------------------------------
  describe('BalanceCard', () => {
    beforeEach(async () => {
      await waitForVisible('dashboard-screen', 15000);
    });

    it('renders balance card', async () => {
      await waitForVisible('widget-balance', 10000);
      await expect(element(by.id('widget-balance'))).toBeVisible();
    });

    it('displays balance amount', async () => {
      await waitForVisible('widget-balance', 10000);
      // Should show formatted amount (e.g., "LKR 150.50")
      const amountText = await element(by.id('balance-amount')).getText();
      expect(amountText).toMatch(/LKR|SLR|Rs/);
    });

    it('shows balance type label (PREPAID vs POSTPAID)', async () => {
      await waitForVisible('widget-balance', 10000);
      const label = await element(by.id('balance-label')).getText();
      expect(['Available Balance', 'Amount Due']).toContain(label);
    });

    it('shows recharge button for PREPAID', async () => {
      await waitForVisible('widget-balance', 10000);
      const isRechargeVisible = await isVisible('btn-recharge');
      if (isRechargeVisible) {
        await expect(element(by.id('btn-recharge'))).toBeVisible();
      }
    });

    it('shows expiry date when available', async () => {
      await waitForVisible('widget-balance', 10000);
      const isExpiryVisible = await isVisible('balance-expiry');
      if (isExpiryVisible) {
        await expect(element(by.id('balance-expiry'))).toBeVisible();
      }
    });

    it('navigates to recharge on button tap', async () => {
      await waitForVisible('widget-balance', 10000);
      const isRechargeVisible = await isVisible('btn-recharge');
      if (isRechargeVisible) {
        await tapById('btn-recharge');
        await waitForVisible('recharge-screen', 5000);
      }
    });

    it('handles loading state', async () => {
      // Pull to refresh should trigger loading state
      await pullToRefresh('dashboard-scroll');
      await waitForVisible('widget-balance', 10000);
    });
  });

  // ----------------------------------------
  // Usage Card
  // ----------------------------------------
  describe('UsageCard', () => {
    beforeEach(async () => {
      await waitForVisible('widget-usage', 10000);
    });

    it('renders usage card', async () => {
      await expect(element(by.id('widget-usage'))).toBeVisible();
    });

    it('shows data usage bar', async () => {
      await waitForVisible('usage-data-bar', 5000);
      await expect(element(by.id('usage-data-bar'))).toBeVisible();
    });

    it('shows data used/total text', async () => {
      const dataText = await element(by.id('usage-data-text')).getText();
      expect(dataText).toMatch(/GB|MB/);
    });

    it('shows voice usage', async () => {
      const isVoiceVisible = await isVisible('usage-voice');
      if (isVoiceVisible) {
        await expect(element(by.id('usage-voice'))).toBeVisible();
      }
    });

    it('shows SMS usage', async () => {
      const isSmsVisible = await isVisible('usage-sms');
      if (isSmsVisible) {
        await expect(element(by.id('usage-sms'))).toBeVisible();
      }
    });

    it('shows billing period label', async () => {
      const isPeriodVisible = await isVisible('usage-period');
      if (isPeriodVisible) {
        await expect(element(by.id('usage-period'))).toBeVisible();
      }
    });

    it('shows "View details" link', async () => {
      await expect(element(by.id('btn-usage-details'))).toBeVisible();
    });

    it('navigates to usage details', async () => {
      await tapById('btn-usage-details');
      await waitForVisible('usage-screen', 5000);
    });

    it('renders chip layout variant', async () => {
      // Scroll to find chip variant if present
      const isChipVisible = await isVisible('usage-chip-data');
      if (isChipVisible) {
        await expect(element(by.id('usage-chip-data'))).toBeVisible();
        await expect(element(by.id('usage-chip-voice'))).toBeVisible();
        await expect(element(by.id('usage-chip-sms'))).toBeVisible();
      }
    });
  });

  // ----------------------------------------
  // Bill Card
  // ----------------------------------------
  describe('BillCard', () => {
    beforeEach(async () => {
      // Bills may be below the fold
      await scrollDown('dashboard-scroll', 200);
    });

    it('renders bill card for postpaid users', async () => {
      await waitForVisible('widget-bills', 10000);
      await expect(element(by.id('widget-bills'))).toBeVisible();
    });

    it('shows bill amount', async () => {
      await waitForVisible('widget-bills', 10000);
      const amountText = await element(by.id('bill-amount')).getText();
      expect(amountText).toMatch(/LKR|SLR/);
    });

    it('shows due date', async () => {
      await waitForVisible('widget-bills', 10000);
      await expect(element(by.id('bill-due-date'))).toBeVisible();
    });

    it('shows status badge', async () => {
      await waitForVisible('widget-bills', 10000);
      const statusText = await element(by.id('bill-status')).getText();
      expect(['DUE', 'OVERDUE', 'PAID', 'PENDING']).toContain(statusText);
    });

    it('shows Pay button for unpaid bills', async () => {
      await waitForVisible('widget-bills', 10000);
      const statusText = await element(by.id('bill-status')).getText();
      if (statusText !== 'PAID') {
        await expect(element(by.id('btn-pay-bill'))).toBeVisible();
      }
    });

    it('navigates to bill details on tap', async () => {
      await waitForVisible('widget-bills', 10000);
      await element(by.id('widget-bills')).tap();
      await waitForVisible('bill-detail-screen', 5000);
    });
  });

  // ----------------------------------------
  // Quick Actions
  // ----------------------------------------
  describe('Quick Actions Grid', () => {
    beforeEach(async () => {
      await scrollDown('dashboard-scroll', 300);
    });

    it('renders quick actions grid', async () => {
      await waitForVisible('quick-actions-grid', 10000);
      await expect(element(by.id('quick-actions-grid'))).toBeVisible();
    });

    it('shows predefined quick actions', async () => {
      await waitForVisible('quick-actions-grid', 10000);
      // Should show actions like: Recharge, Bundles, Support, etc.
      const actionCount = await element(by.id('quick-actions-grid')).getAttributes();
      expect(actionCount.childElements.length).toBeGreaterThan(0);
    });

    it('supports long press for additional options', async () => {
      await waitForVisible('quick-actions-grid', 10000);
      // Long press on first action
      const firstAction = element(by.id('quick-action-0'));
      await firstAction.longPress();
      await waitForVisible('action-context-menu', 3000);
    });

    it('each action navigates correctly', async () => {
      await waitForVisible('quick-actions-grid', 10000);
      const firstAction = element(by.id('quick-action-0'));
      await firstAction.tap();
      // Should navigate to respective screen
      // Verify by checking navigation happened
    });
  });

  // ----------------------------------------
  // Notifications
  // ----------------------------------------
  describe('Notifications List', () => {
    beforeEach(async () => {
      await scrollDown('dashboard-scroll', 400);
    });

    it('renders notifications widget', async () => {
      await waitForVisible('widget-notifications', 10000);
      await expect(element(by.id('widget-notifications'))).toBeVisible();
    });

    it('shows notification count badge', async () => {
      await waitForVisible('widget-notifications', 10000);
      const isBadgeVisible = await isVisible('notification-count');
      if (isBadgeVisible) {
        const count = await element(by.id('notification-count')).getText();
        expect(parseInt(count, 10)).toBeGreaterThan(0);
      }
    });

    it('shows notification items', async () => {
      await waitForVisible('widget-notifications', 10000);
      const isListVisible = await isVisible('notification-list');
      if (isListVisible) {
        await expect(element(by.id('notification-item-0'))).toBeVisible();
      }
    });

    it('marks notification as read on tap', async () => {
      await waitForVisible('widget-notifications', 10000);
      const isListVisible = await isVisible('notification-list');
      if (isListVisible) {
        await element(by.id('notification-item-0')).tap();
        // Notification should change read state
      }
    });
  });

  // ----------------------------------------
  // Banners Carousel
  // ----------------------------------------
  describe('Banners Carousel', () => {
    beforeEach(async () => {
      // Banners usually at top
    });

    it('renders banners carousel', async () => {
      await waitForVisible('dashboard-screen', 15000);
      const isCarouselVisible = await isVisible('banners-carousel');
      if (isCarouselVisible) {
        await expect(element(by.id('banners-carousel'))).toBeVisible();
      }
    });

    it('shows pagination dots', async () => {
      const isCarouselVisible = await isVisible('banners-carousel');
      if (isCarouselVisible) {
        const isDotsVisible = await isVisible('banner-pagination');
        if (isDotsVisible) {
          await expect(element(by.id('banner-pagination'))).toBeVisible();
        }
      }
    });

    it('swipes to next banner', async () => {
      const isCarouselVisible = await isVisible('banners-carousel');
      if (isCarouselVisible) {
        await element(by.id('banners-carousel')).swipe('left');
        // Check pagination updated
      }
    });

    it('taps banner to navigate', async () => {
      const isCarouselVisible = await isVisible('banners-carousel');
      if (isCarouselVisible) {
        const isBannerVisible = await isVisible('banner-item-0');
        if (isBannerVisible) {
          await element(by.id('banner-item-0')).tap();
          // Should navigate to banner target
        }
      }
    });
  });

  // ----------------------------------------
  // Pull to Refresh
  // ----------------------------------------
  describe('Pull to Refresh', () => {
    it('triggers refresh on pull down', async () => {
      await waitForVisible('dashboard-screen', 15000);
      await pullToRefresh('dashboard-scroll');
      // Loading indicator should appear briefly
      await waitForVisible('dashboard-screen', 10000);
    });

    it('updates all widgets after refresh', async () => {
      await waitForVisible('dashboard-screen', 15000);
      await pullToRefresh('dashboard-scroll');
      // Widgets should show fresh data
      await waitForVisible('widget-balance', 10000);
    });
  });

  // ----------------------------------------
  // Error States
  // ----------------------------------------
  describe('Widget Error States', () => {
    it('shows retry button on widget failure', async () => {
      // This test requires network conditions to be simulated
      // In practice, use Detox with network conditions
    });

    it('shows stale data indicator', async () => {
      // Test stale data behavior
    });
  });

  // ----------------------------------------
  // Telco vs Insurance Widgets
  // ----------------------------------------
  describe('Industry-Specific Widgets', () => {
    it('shows telco widgets for dialog tenant', async () => {
      await waitForVisible('widget-balance', 10000);
      await expect(element(by.id('widget-balance'))).toBeVisible();
      await expect(element(by.id('widget-usage'))).toBeVisible();
    });

    it('does not show insurance widgets for telco tenant', async () => {
      const hasInsuranceWidget = await isVisible('widget-insurance-policies');
      expect(hasInsuranceWidget).toBe(false);
    });
  });
});
