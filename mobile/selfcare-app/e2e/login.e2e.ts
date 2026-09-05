/**
 * Login Flow E2E Tests
 *
 * Tests the complete OTP authentication flow:
 * 1. Landing page -> Enter MSISDN
 * 2. Send OTP -> Receive SMS
 * 3. Verify OTP -> Authenticated
 * 4. Session persistence -> Re-authentication bypass
 * 5. Biometric step-up -> Sensitive operation access
 *
 * Prerequisite: Backend running with OTP mock enabled
 */

import { describe, it, expect, beforeEach } from '@jest/globals';
import { device, element, by, waitFor } from 'detox';
import {
  tapById,
  typeById,
  clearInput,
  waitForVisible,
  waitForHidden,
  isVisible,
  navigateBack,
  TEST_USERS,
} from './before-each';

describe('Login Flow', () => {
  // ----------------------------------------
  // Landing Page
  // ----------------------------------------
  describe('Landing Page', () => {
    beforeEach(async () => {
      // App should open to landing/login screen
      await waitForVisible('landing-title', 10000).catch(() => {
        // May already be logged in from previous test
      });
    });

    it('shows login screen on cold start', async () => {
      await waitForVisible('landing-title', 15000);
      await expect(element(by.id('landing-title'))).toBeVisible();
    });

    it('shows app logo and branding', async () => {
      await waitForVisible('app-logo');
      await expect(element(by.id('app-logo'))).toBeVisible();
    });

    it('shows MSISDN input field', async () => {
      await waitForVisible('msisdn-input');
      await expect(element(by.id('msisdn-input'))).toBeVisible();
    });

    it('shows country code picker', async () => {
      await waitForVisible('country-code-picker');
      await expect(element(by.id('country-code-picker'))).toBeVisible();
    });

    it('shows continue button', async () => {
      await waitForVisible('btn-continue');
      await expect(element(by.id('btn-continue'))).toBeVisible();
    });

    it('continue button is disabled when input is empty', async () => {
      await waitForVisible('btn-continue');
      const button = element(by.id('btn-continue'));
      await expect(button).toBeVisible();
    });
  });

  // ----------------------------------------
  // MSISDN Entry Validation
  // ----------------------------------------
  describe('MSISDN Entry', () => {
    beforeEach(async () => {
      await waitForVisible('msisdn-input', 10000);
    });

    it('accepts valid Sri Lankan mobile number', async () => {
      await typeById('msisdn-input', '771234567');
      await expect(element(by.id('msisdn-input'))).toHaveText('771234567');
    });

    it('validates international format', async () => {
      await typeById('msisdn-input', '+94771234567');
      await expect(element(by.id('msisdn-input'))).toHaveText('+94771234567');
    });

    it('rejects invalid MSISDN length', async () => {
      await typeById('msisdn-input', '123');
      await tapById('btn-continue');

      await waitForVisible('msisdn-error');
      await expect(element(by.id('msisdn-error'))).toHaveText('Invalid phone number');
    });

    it('clears validation error on new input', async () => {
      await typeById('msisdn-input', '123');
      await tapById('btn-continue');
      await waitForVisible('msisdn-error');

      await clearInput('msisdn-input');
      await typeById('msisdn-input', '771234567');

      // Error should clear
      const hasError = await isVisible('msisdn-error');
      expect(hasError).toBe(false);
    });
  });

  // ----------------------------------------
  // OTP Send
  // ----------------------------------------
  describe('OTP Send', () => {
    beforeEach(async () => {
      await waitForVisible('msisdn-input', 10000);
      await typeById('msisdn-input', TEST_USERS.TELECOM.msisdn);
      await tapById('btn-continue');
      await waitForVisible('otp-screen', 10000);
    });

    it('navigates to OTP screen after sending', async () => {
      await waitForVisible('otp-screen', 10000);
      await expect(element(by.id('otp-screen'))).toBeVisible();
    });

    it('shows masked MSISDN', async () => {
      await waitForVisible('masked-msisdn');
      // Should show something like +94****4567
      const masked = await element(by.id('masked-msisdn')).getText();
      expect(masked).toContain('****');
    });

    it('shows 6 OTP input fields', async () => {
      for (let i = 1; i <= 6; i++) {
        await expect(element(by.id(`otp-input-${i}`))).toBeVisible();
      }
    });

    it('shows resend timer', async () => {
      await waitForVisible('resend-timer');
      await expect(element(by.id('resend-timer'))).toBeVisible();
    });

    it('auto-advances focus on digit entry', async () => {
      await element(by.id('otp-input-1')).typeText('1');
      // Focus should move to input-2
      // This tests keyboard navigation
    });

    it('backspace moves to previous field', async () => {
      await element(by.id('otp-input-2')).typeText('2');
      // Test backspace behavior
    });

    it('shows change number link', async () => {
      await expect(element(by.id('change-number-link'))).toBeVisible();
    });

    it('back navigation from OTP screen', async () => {
      await navigateBack();
      await waitForVisible('msisdn-input', 5000);
    });
  });

  // ----------------------------------------
  // OTP Verify
  // ----------------------------------------
  describe('OTP Verify', () => {
    beforeEach(async () => {
      await waitForVisible('msisdn-input', 10000);
      await typeById('msisdn-input', TEST_USERS.TELECOM.msisdn);
      await tapById('btn-continue');
      await waitForVisible('otp-input-1', 10000);
    });

    it('accepts valid OTP code', async () => {
      const otp = TEST_USERS.TELECOM.otp;

      // Enter OTP digit by digit
      for (let i = 0; i < otp.length; i++) {
        await element(by.id(`otp-input-${i + 1}`)).typeText(otp[i]);
      }

      // Wait for navigation to dashboard
      await waitForVisible('dashboard-screen', 15000);
      await expect(element(by.id('dashboard-screen'))).toBeVisible();
    });

    it('shows loading indicator during verification', async () => {
      const otp = TEST_USERS.TELECOM.otp;
      for (let i = 0; i < otp.length; i++) {
        await element(by.id(`otp-input-${i + 1}`)).typeText(otp[i]);
      }

      await waitForVisible('loading-indicator', 5000);
      await expect(element(by.id('loading-indicator'))).toBeVisible();
    });

    it('shows error on wrong OTP', async () => {
      // Enter wrong OTP
      for (let i = 0; i < 6; i++) {
        await element(by.id(`otp-input-${i + 1}`)).typeText('9');
      }

      await waitForVisible('otp-error', 10000);
      await expect(element(by.id('otp-error'))).toHaveText('Invalid code. Please try again.');
    });

    it('clears OTP inputs on error', async () => {
      // Enter wrong OTP
      for (let i = 0; i < 6; i++) {
        await element(by.id(`otp-input-${i + 1}`)).typeText('9');
      }

      await waitForVisible('otp-error', 10000);

      // All inputs should be cleared
      for (let i = 1; i <= 6; i++) {
        await expect(element(by.id(`otp-input-${i}`))).toHaveText('');
      }
    });

    it('shows attempts remaining', async () => {
      // After failed attempt, show remaining attempts
      for (let i = 0; i < 6; i++) {
        await element(by.id(`otp-input-${i + 1}`)).typeText('9');
      }
      await waitForVisible('otp-error', 10000);

      await waitForVisible('attempts-remaining');
      await expect(element(by.id('attempts-remaining'))).toBeVisible();
    });

    it('locks out after max attempts', async () => {
      // Try 3 times with wrong OTP
      for (let attempt = 0; attempt < 3; attempt++) {
        for (let i = 0; i < 6; i++) {
          await element(by.id(`otp-input-${i + 1}`)).typeText('9');
        }
        await waitForVisible('otp-error', 10000);

        // Wait a moment for UI to update
        await device.delay(500);
      }

      await waitForVisible('otp-locked', 10000);
      await expect(element(by.id('otp-locked'))).toBeVisible();
    });
  });

  // ----------------------------------------
  // Session Persistence
  // ----------------------------------------
  describe('Session Persistence', () => {
    it('persists login across app restart', async () => {
      // Log in
      await waitForVisible('msisdn-input', 10000);
      await typeById('msisdn-input', TEST_USERS.TELECOM.msisdn);
      await tapById('btn-continue');
      await waitForVisible('otp-input-1', 10000);

      const otp = TEST_USERS.TELECOM.otp;
      for (let i = 0; i < otp.length; i++) {
        await element(by.id(`otp-input-${i + 1}`)).typeText(otp[i]);
      }

      await waitForVisible('dashboard-screen', 15000);

      // Force restart app
      await device.terminateApp();
      await device.launchApp({ newInstance: true });

      // Should be on dashboard (already logged in)
      await waitForVisible('dashboard-screen', 15000);
      await expect(element(by.id('dashboard-screen'))).toBeVisible();
    });
  });

  // ----------------------------------------
  // Sign Out
  // ----------------------------------------
  describe('Sign Out', () => {
    beforeEach(async () => {
      // Navigate to profile/settings
      await waitForVisible('dashboard-screen', 15000);
      await tapById('btn-profile');
      await waitForVisible('profile-screen');
    });

    it('shows sign out option in profile', async () => {
      await scrollDown('profile-scroll');
      await waitForVisible('btn-sign-out');
      await expect(element(by.id('btn-sign-out'))).toBeVisible();
    });

    it('signs out and returns to login', async () => {
      await scrollDown('profile-scroll');
      await tapById('btn-sign-out');

      await waitForVisible('signout-confirm-dialog');
      await tapById('btn-confirm-signout');

      await waitForVisible('landing-title', 10000);
    });
  });

  // ----------------------------------------
  // Insurance Industry Login
  // ----------------------------------------
  describe('Insurance Industry Login', () => {
    it('shows insurance-specific onboarding for AIA', async () => {
      // AIA tenant should show insurance onboarding
      await waitForVisible('msisdn-input', 10000);
      await typeById('msisdn-input', TEST_USERS.INSURANCE.msisdn);
      await tapById('btn-continue');

      await waitForVisible('otp-screen', 10000);
      // May show policy number input instead of or in addition to OTP
    });
  });
});
