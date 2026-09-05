/**
 * E2E Before-Each Hooks
 *
 * Shared setup/teardown for all E2E tests.
 */

import { device, element, by, expect } from 'detox';
import { reloadApp, launchApp } from 'detox';

// ============================================================
// Global Test Data
// ============================================================

export const TEST_USERS = {
  TELECOM: {
    msisdn: '+94771234567',
    otp: '123456',
    tenantId: 'dialog-lk',
  },
  INSURANCE: {
    msisdn: '+94771234599',
    otp: '654321',
    tenantId: 'aia-lk',
  },
};

export const TEST_CONNECTIONS = {
  PRIMARY: { connectionId: 'conn-primary-1', label: 'Primary Line' },
  SECONDARY: { connectionId: 'conn-secondary-2', label: 'Secondary Line' },
};

// ============================================================
// Global Before-Each
// ============================================================

beforeEach(async () => {
  // Reload app between tests for isolation
  await reloadApp();
});

// ============================================================
// Global After-Each
// ============================================================

afterEach(async () => {
  // Screenshot on failure
  if (expect.getState().currentError) {
    await device.takeScreenshot(`failure-${Date.now()}`);
  }
});

// ============================================================
// Test Helpers
// ============================================================

/** Navigate back on both platforms */
export async function navigateBack(): Promise<void> {
  if (device.getPlatform() === 'ios') {
    await device.pressBack();
  } else {
    await device.pressBack();
  }
}

/** Tap element by testID */
export async function tapById(testId: string): Promise<void> {
  await element(by.id(testId)).tap();
}

/** Type text into element by testID */
export async function typeById(testId: string, text: string): Promise<void> {
  await element(by.id(testId)).typeText(text);
}

/** Wait for element to be visible */
export async function waitForVisible(testId: string, timeout = 10000): Promise<void> {
  await waitFor(element(by.id(testId)))
    .toBeVisible()
    .withTimeout(timeout);
}

/** Wait for element to NOT be visible */
export async function waitForHidden(testId: string, timeout = 5000): Promise<void> {
  await waitFor(element(by.id(testId)))
    .toBeNotVisible()
    .withTimeout(timeout);
}

/** Check if element exists */
export async function isVisible(testId: string): Promise<boolean> {
  try {
    await expect(element(by.id(testId))).toBeVisible();
    return true;
  } catch {
    return false;
  }
}

/** Clear text from input */
export async function clearInput(testId: string): Promise<void> {
  await element(by.id(testId)).clearText();
}

/** Scroll down on scrollable element */
export async function scrollDown(scrollableId: string, amount = 200): Promise<void> {
  await element(by.id(scrollableId)).scroll(amount, 'down');
}

/** Pull to refresh */
export async function pullToRefresh(scrollableId: string): Promise<void> {
  await element(by.id(scrollableId)).scroll(300, 'down', [], 0.8, 0.2);
}

/** Disable animations for faster tests */
export async function disableAnimations(): Promise<void> {
  await device.setBiometricEnrollment(false);
  await device.setStatusBar({ animation: 'none' });
}
