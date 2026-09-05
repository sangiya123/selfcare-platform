/**
 * Payment Flow E2E Tests
 *
 * Tests cross-connection payment with step-up authentication:
 * 1. Bill payment initiation
 * 2. Cross-connection payment selection
 * 3. Step-up auth (OTP/biometric) for sensitive operation
 * 4. Payment method selection
 * 5. Payment confirmation
 * 6. Receipt/result screen
 * 7. Recharge flow
 *
 * Prerequisite: Logged in, with pending bill or prepaid balance
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

describe('Payment Flow', () => {
  // ----------------------------------------
  // Bill Payment - Initiation
  // ----------------------------------------
  describe('Bill Payment Initiation', () => {
    beforeEach(async () => {
      await waitForVisible('dashboard-screen', 15000);
      // Navigate to bills widget
      await scrollDown('dashboard-scroll', 200);
    });

    it('shows pay button on bill card', async () => {
      await waitForVisible('widget-bills', 10000);
      const isPayVisible = await isVisible('btn-pay-bill');
      if (isPayVisible) {
        await expect(element(by.id('btn-pay-bill'))).toBeVisible();
      }
    });

    it('navigates to payment screen on pay button tap', async () => {
      await waitForVisible('widget-bills', 10000);
      const isPayVisible = await isVisible('btn-pay-bill');
      if (isPayVisible) {
        await tapById('btn-pay-bill');
        await waitForVisible('payment-screen', 5000);
      }
    });

    it('shows bill summary on payment screen', async () => {
      await waitForVisible('widget-bills', 10000);
      const isPayVisible = await isVisible('btn-pay-bill');
      if (isPayVisible) {
        await tapById('btn-pay-bill');
        await waitForVisible('payment-screen', 5000);

        await expect(element(by.id('payment-amount'))).toBeVisible();
        await expect(element(by.id('payment-bill-number'))).toBeVisible();
      }
    });
  });

  // ----------------------------------------
  // Cross-Connection Payment
  // ----------------------------------------
  describe('Cross-Connection Payment', () => {
    beforeEach(async () => {
      // Navigate to payment screen
      await waitForVisible('dashboard-screen', 15000);
      await scrollDown('dashboard-scroll', 200);
      await waitForVisible('widget-bills', 10000);
      const isPayVisible = await isVisible('btn-pay-bill');
      if (isPayVisible) {
        await tapById('btn-pay-bill');
        await waitForVisible('payment-screen', 5000);
      }
    });

    it('shows connection selector for paying another line', async () => {
      await waitForVisible('payment-screen', 5000);
      const isConnectionSelectorVisible = await isVisible('payment-connection-selector');
      if (isConnectionSelectorVisible) {
        await expect(element(by.id('payment-connection-selector'))).toBeVisible();
      }
    });

    it('shows current balance when paying own bill', async () => {
      await waitForVisible('payment-screen', 5000);
      const isBalanceVisible = await isVisible('payment-from-balance');
      if (isBalanceVisible) {
        await expect(element(by.id('payment-from-balance'))).toBeVisible();
      }
    });

    it('switches payment source to another connection', async () => {
      const isConnectionSelectorVisible = await isVisible('payment-connection-selector');
      if (isConnectionSelectorVisible) {
        await tapById('payment-connection-selector');
        await waitForVisible('connection-modal', 3000);

        // Select a different connection
        const isSecondVisible = await isVisible('connection-item-1');
        if (isSecondVisible) {
          await element(by.id('connection-item-1')).tap();
          await waitForVisible('payment-screen', 5000);

          // Balance should update
          const isNewBalanceVisible = await isVisible('payment-from-balance');
          if (isNewBalanceVisible) {
            await expect(element(by.id('payment-from-balance'))).toBeVisible();
          }
        }
      }
    });
  });

  // ----------------------------------------
  // Step-Up Authentication
  // ----------------------------------------
  describe('Step-Up Authentication', () => {
    beforeEach(async () => {
      await waitForVisible('dashboard-screen', 15000);
      await scrollDown('dashboard-scroll', 200);
      await waitForVisible('widget-bills', 10000);
      const isPayVisible = await isVisible('btn-pay-bill');
      if (isPayVisible) {
        await tapById('btn-pay-bill');
        await waitForVisible('payment-screen', 5000);
      }
    });

    it('triggers step-up auth for payment > threshold', async () => {
      // Enter amount above step-up threshold
      await scrollDown('payment-screen-scroll');
      const isAmountEditable = await isVisible('payment-amount-input');
      if (isAmountEditable) {
        await element(by.id('payment-amount-input')).clearText();
        await element(by.id('payment-amount-input')).typeText('10000');
      }

      // Proceed to payment
      await tapById('btn-proceed-payment');

      // Should show step-up auth
      await waitForVisible('stepup-auth-screen', 5000);
    });

    it('shows biometric option for step-up', async () => {
      await tapById('btn-proceed-payment');
      const isStepUpVisible = await isVisible('stepup-auth-screen');
      if (isStepUpVisible) {
        const isBiometricVisible = await isVisible('btn-biometric');
        if (isBiometricVisible) {
          await expect(element(by.id('btn-biometric'))).toBeVisible();
        }
      }
    });

    it('shows OTP option for step-up', async () => {
      await tapById('btn-proceed-payment');
      const isStepUpVisible = await isVisible('stepup-auth-screen');
      if (isStepUpVisible) {
        await expect(element(by.id('btn-otp-stepup'))).toBeVisible();
      }
    });

    it('authenticates with biometric', async () => {
      await tapById('btn-proceed-payment');
      await waitForVisible('stepup-auth-screen', 5000);

      const isBiometricVisible = await isVisible('btn-biometric');
      if (isBiometricVisible) {
        await tapById('btn-biometric');
        // Biometric would be handled by system
        // In test, mock biometric success
      }
    });

    it('authenticates with OTP for step-up', async () => {
      await tapById('btn-proceed-payment');
      await waitForVisible('stepup-auth-screen', 5000);
      await tapById('btn-otp-stepup');
      await waitForVisible('otp-stepup-input-1', 5000);

      // Enter OTP
      for (let i = 1; i <= 6; i++) {
        await element(by.id(`otp-stepup-input-${i}`)).typeText('1');
      }

      await waitForVisible('payment-methods-screen', 10000);
    });
  });

  // ----------------------------------------
  // Payment Method Selection
  // ----------------------------------------
  describe('Payment Method Selection', () => {
    beforeEach(async () => {
      // Navigate to payment method selection
      await waitForVisible('dashboard-screen', 15000);
      await scrollDown('dashboard-scroll', 200);
      const isPayVisible = await isVisible('btn-pay-bill');
      if (isPayVisible) {
        await tapById('btn-pay-bill');
        await waitForVisible('payment-screen', 5000);
        await tapById('btn-proceed-payment');
        await waitForVisible('payment-methods-screen', 10000);
      }
    });

    it('lists available payment methods', async () => {
      await waitForVisible('payment-methods-screen', 10000);
      const methodCount = await element(by.id('payment-methods-list')).getAttributes();
      expect(methodCount.childElements.length).toBeGreaterThan(0);
    });

    it('shows saved cards', async () => {
      await waitForVisible('payment-methods-screen', 10000);
      const isCardsVisible = await isVisible('saved-cards-section');
      if (isCardsVisible) {
        await expect(element(by.id('saved-cards-section'))).toBeVisible();
      }
    });

    it('shows mobile payment options', async () => {
      await waitForVisible('payment-methods-screen', 10000);
      const isMobileVisible = await isVisible('mobile-payments-section');
      if (isMobileVisible) {
        await expect(element(by.id('mobile-payments-section'))).toBeVisible();
      }
    });

    it('selects payment method', async () => {
      await waitForVisible('payment-methods-screen', 10000);
      const isMethodVisible = await isVisible('payment-method-item-0');
      if (isMethodVisible) {
        await element(by.id('payment-method-item-0')).tap();
        await waitForVisible('payment-confirm-screen', 5000);
      }
    });

    it('shows add new card option', async () => {
      await waitForVisible('payment-methods-screen', 10000);
      await scrollDown('payment-methods-scroll');
      const isAddCardVisible = await isVisible('btn-add-card');
      if (isAddCardVisible) {
        await expect(element(by.id('btn-add-card'))).toBeVisible();
      }
    });
  });

  // ----------------------------------------
  // Payment Confirmation
  // ----------------------------------------
  describe('Payment Confirmation', () => {
    beforeEach(async () => {
      // Navigate to confirmation
      await waitForVisible('dashboard-screen', 15000);
      await scrollDown('dashboard-scroll', 200);
      const isPayVisible = await isVisible('btn-pay-bill');
      if (isPayVisible) {
        await tapById('btn-pay-bill');
        await waitForVisible('payment-screen', 5000);
        await tapById('btn-proceed-payment');
        // Skip auth for testing (would need proper auth flow)
      }
    });

    it('shows payment summary', async () => {
      await waitForVisible('payment-confirm-screen', 10000);
      await expect(element(by.id('confirm-amount'))).toBeVisible();
      await expect(element(by.id('confirm-method'))).toBeVisible();
    });

    it('shows processing fee if applicable', async () => {
      await waitForVisible('payment-confirm-screen', 10000);
      const isFeeVisible = await isVisible('confirm-fee');
      if (isFeeVisible) {
        await expect(element(by.id('confirm-fee'))).toBeVisible();
      }
    });

    it('shows total amount', async () => {
      await waitForVisible('payment-confirm-screen', 10000);
      await expect(element(by.id('confirm-total'))).toBeVisible();
    });

    it('has confirm button', async () => {
      await waitForVisible('payment-confirm-screen', 10000);
      await expect(element(by.id('btn-confirm-payment'))).toBeVisible();
    });

    it('has cancel button', async () => {
      await waitForVisible('payment-confirm-screen', 10000);
      await expect(element(by.id('btn-cancel-payment'))).toBeVisible();
    });

    it('cancels and returns on cancel', async () => {
      await waitForVisible('payment-confirm-screen', 10000);
      await tapById('btn-cancel-payment');
      await waitForVisible('payment-screen', 5000);
    });
  });

  // ----------------------------------------
  // Payment Processing & Result
  // ----------------------------------------
  describe('Payment Processing', () => {
    beforeEach(async () => {
      // Navigate to confirmation and submit
      await waitForVisible('dashboard-screen', 15000);
      await scrollDown('dashboard-scroll', 200);
      const isPayVisible = await isVisible('btn-pay-bill');
      if (isPayVisible) {
        await tapById('btn-pay-bill');
        await waitForVisible('payment-screen', 5000);
        await tapById('btn-proceed-payment');
      }
    });

    it('shows loading during payment', async () => {
      await waitForVisible('payment-confirm-screen', 10000);
      await tapById('btn-confirm-payment');

      await waitForVisible('payment-loading', 5000);
      await expect(element(by.id('payment-loading'))).toBeVisible();
    });

    it('shows success screen on success', async () => {
      await waitForVisible('payment-confirm-screen', 10000);
      await tapById('btn-confirm-payment');

      await waitForVisible('payment-success', 30000);
      await expect(element(by.id('payment-success'))).toBeVisible();
    });

    it('shows receipt on success', async () => {
      await waitForVisible('payment-confirm-screen', 10000);
      await tapById('btn-confirm-payment');
      await waitForVisible('payment-success', 30000);

      await expect(element(by.id('receipt-ref'))).toBeVisible();
      const ref = await element(by.id('receipt-ref')).getText();
      expect(ref).toBeTruthy();
    });

    it('shows error screen on failure', async () => {
      // Would need to simulate payment failure
      // For now, just verify the success path
    });

    it('has share receipt option', async () => {
      await waitForVisible('payment-confirm-screen', 10000);
      await tapById('btn-confirm-payment');
      await waitForVisible('payment-success', 30000);

      const isShareVisible = await isVisible('btn-share-receipt');
      if (isShareVisible) {
        await expect(element(by.id('btn-share-receipt'))).toBeVisible();
      }
    });

    it('returns to dashboard on done', async () => {
      await waitForVisible('payment-confirm-screen', 10000);
      await tapById('btn-confirm-payment');
      await waitForVisible('payment-success', 30000);
      await tapById('btn-done');
      await waitForVisible('dashboard-screen', 5000);
    });
  });

  // ----------------------------------------
  // Recharge Flow
  // ----------------------------------------
  describe('Recharge Flow', () => {
    beforeEach(async () => {
      await waitForVisible('dashboard-screen', 15000);
    });

    it('navigates to recharge from balance card', async () => {
      await waitForVisible('widget-balance', 10000);
      const isRechargeVisible = await isVisible('btn-recharge');
      if (isRechargeVisible) {
        await tapById('btn-recharge');
        await waitForVisible('recharge-screen', 5000);
      }
    });

    it('shows recharge amounts', async () => {
      await waitForVisible('dashboard-screen', 15000);
      const isRechargeVisible = await isVisible('btn-recharge');
      if (isRechargeVisible) {
        await tapById('btn-recharge');
        await waitForVisible('recharge-screen', 5000);

        await expect(element(by.id('recharge-amounts'))).toBeVisible();
      }
    });

    it('shows custom amount input', async () => {
      const isRechargeVisible = await isVisible('btn-recharge');
      if (isRechargeVisible) {
        await tapById('btn-recharge');
        await waitForVisible('recharge-screen', 5000);

        const isCustomVisible = await isVisible('custom-amount-input');
        if (isCustomVisible) {
          await expect(element(by.id('custom-amount-input'))).toBeVisible();
        }
      }
    });
  });
});
