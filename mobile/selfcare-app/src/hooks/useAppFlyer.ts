/**
 * AppFlyer SDK wrapper for OMOBIO Selfcare App.
 *
 * Initialises the AppsFlyer SDK, tracks app install + events,
 * and exposes helpers for in-app event tracking and deep-link attribution.
 *
 * AppFlyer Dev Key should be set per environment via:
 *   - .env.MOBILE_APPSFLYER_DEV_KEY (Android)
 *   - .env.MOBILE_APPSFLYER_APP_ID   (iOS — Apple App ID)
 *
 * Usage:
 *   useEffect(() => { initAppFlyer(); }, []);
 *   trackEvent('user_login', { method: 'otp' });
 */
import { Platform } from 'react-native';
import appsFlyer from 'react-native-appsflyer';
import Config from 'react-native-config';

const DEV_KEY =
  (Config as any)?.MOBILE_APPSFLYER_DEV_KEY ?? 'omobio-dev-key';
const APP_ID =
  (Config as any)?.MOBILE_APPSFLYER_APP_ID ?? 'omobio.selfcare.app';

/** Initialise the AppsFlyer SDK. Call once at app start. */
export function initAppFlyer(): void {
  try {
    const options = {
      devKey: DEV_KEY,
      isDebug: __DEV__,
      appId: Platform.OS === 'ios' ? APP_ID : undefined,
      onInstallConversionDataListener: true,
      onDeepLinkListener: true,
      timeToWaitForATTUserAuthorization: 10,
    };

    appsFlyer.initSdk(
      options,
      (result) => {
        // eslint-disable-next-line no-console
        console.log('[AppFlyer] init success', result);
      },
      (error) => {
        // eslint-disable-next-line no-console
        console.warn('[AppFlyer] init error', error);
      }
    );
  } catch (e) {
    // eslint-disable-next-line no-console
    console.warn('[AppFlyer] init failed', e);
  }
}

/** Track an in-app event. */
export function trackEvent(eventName: string, values: Record<string, any> = {}): void {
  try {
    appsFlyer.trackEvent(
      eventName,
      { ...values, platform: Platform.OS },
      () => {
        // success
      },
      (err) => {
        // eslint-disable-next-line no-console
        console.warn('[AppFlyer] track error', err);
      }
    );
  } catch (e) {
    // eslint-disable-next-line no-console
    console.warn('[AppFlyer] track failed', e);
  }
}

/** Set the user ID (e.g. once authenticated). */
export function setAppFlyerUserId(userId: string): void {
  try {
    appsFlyer.setCustomerUserId(userId);
  } catch (e) {
    // eslint-disable-next-line no-console
    console.warn('[AppFlyer] setUserId failed', e);
  }
}

export default { initAppFlyer, trackEvent, setAppFlyerUserId };
