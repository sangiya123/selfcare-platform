/**
 * AppsFlyer SDK wrapper for Selfcare App.
 *
 * Targets the installed v7 SDK (react-native-appsflyer 7): init({devKey, appId})
 * → registerSessionReadyListener → start() inside the ready callback; events
 * via logEvent({eventName, eventValues}); user id via setCustomerUserId({customerId}).
 *
 * AppFlyer Dev Key / App ID come from environment config only:
 *   - Android: MOBILE_APPSFLYER_DEV_KEY
 *   - iOS:     MOBILE_APPSFLYER_APP_ID (Apple App ID)
 *
 * Usage:
 *   useEffect(() => { initAppFlyer(); }, []);
 *   trackEvent('user_login', { method: 'otp' });
 */
import { Platform } from 'react-native';
import { AppsFlyer } from 'react-native-appsflyer';
import Config from 'react-native-config';

const DEV_KEY = (Config as any)?.MOBILE_APPSFLYER_DEV_KEY;
const APP_ID = (Config as any)?.MOBILE_APPSFLYER_APP_ID;

/** Initialise the AppsFlyer SDK. Call once at app start. */
export function initAppFlyer(): void {
  if (!DEV_KEY || !APP_ID) {
    // Analytics is optional — no dev key = no tracking (nothing hardcoded).
    return;
  }
  try {
    AppsFlyer.init({ devKey: DEV_KEY, appId: APP_ID })
      .then(() => AppsFlyer.registerSessionReadyListener(() => {
        AppsFlyer.start().catch((e) => {
          if (__DEV__) console.warn('[AppFlyer] start error', e);
        });
      }))
      .catch((e) => {
        if (__DEV__) console.warn('[AppFlyer] init error', e);
      });
  } catch (e) {
    if (__DEV__) console.warn('[AppFlyer] init failed', e);
  }
}

/** Track an in-app event. */
export async function trackEvent(eventName: string, values: Record<string, unknown> = {}): Promise<void> {
  if (!DEV_KEY || !APP_ID) return;
  try {
    await AppsFlyer.logEvent({
      eventName,
      eventValues: { ...values, platform: Platform.OS },
    });
  } catch (e) {
    if (__DEV__) console.warn('[AppFlyer] track error', e);
  }
}

/** Set the user ID (e.g. once authenticated). */
export async function setAppFlyerUserId(userId: string): Promise<void> {
  if (!DEV_KEY || !APP_ID) return;
  try {
    await AppsFlyer.setCustomerUserId({ customerId: userId });
  } catch (e) {
    if (__DEV__) console.warn('[AppFlyer] setUserId failed', e);
  }
}

export default { initAppFlyer, trackEvent, setAppFlyerUserId };