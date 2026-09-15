/**
 * selfcare kernel — mobile action dispatcher (ADR-009 closed action set).
 *
 * Bridges the canonical ManifestAction (from compiled manifests) to runtime
 * behaviour:
 *   - NAVIGATE  -> NavigationRouter (react-navigation ref)
 *   - everything else -> the v1 ActionEngine (kept intact) with a normalized
 *     `{ type, payload }` action.
 *
 * Optional `confirmation` shows a native confirm dialog; optional
 * `analyticsEvent` is forwarded to the registered analytics hook.
 */

import { Alert } from 'react-native';
import { ManifestAction } from '../manifest/types';
import { NavigationRouter } from './NavigationRouter';
import { ActionEngine } from '../config/ActionEngine';
import { Action } from '../config/types';

export interface MobileActionDispatcherOptions {
  router: NavigationRouter;
  actionEngine?: ActionEngine;
  /** Optional analytics callback invoked for LOG_EVENT / analyticsEvent. */
  trackEvent?: (eventName: string, properties?: Record<string, unknown>) => void;
}

export function createMobileActionDispatcher(
  options: MobileActionDispatcherOptions
): (action: ManifestAction) => Promise<void> {
  const { router, actionEngine, trackEvent } = options;

  return async (action: ManifestAction): Promise<void> => {
    if (!action?.type) {
      // eslint-disable-next-line no-console
      console.warn('[MobileActionDispatcher] empty action', action);
      return;
    }

    if (action.analyticsEvent) {
      trackEvent?.(action.analyticsEvent, action.params);
    }

    if (action.type === 'NAVIGATE') {
      if (!action.route) return;
      const handled = router.navigate(action.route, action.params);
      if (!handled) {
        // eslint-disable-next-line no-console
        console.warn(`[MobileActionDispatcher] unknown route: ${action.route}`);
      }
      return;
    }

    if (action.confirmation) {
      const proceed = await confirm(action.confirmation);
      if (!proceed) return;
    }

    if (actionEngine) {
      const payload: Record<string, unknown> = { ...(action.params ?? {}) };
      if (action.route) payload.route = action.route;
      if (action.journeyId) payload.journeyId = action.journeyId;

      const legacy: Action = {
        type: action.type as Action['type'],
        payload,
      };
      await actionEngine.execute(legacy);
    }
  };
}

function confirm(message: string): Promise<boolean> {
  return new Promise<boolean>((resolve) => {
    Alert.alert(
      'Confirm',
      message,
      [
        { text: 'Cancel', style: 'cancel', onPress: () => resolve(false) },
        { text: 'OK', onPress: () => resolve(true) },
      ],
      { cancelable: true, onDismiss: () => resolve(false) }
    );
  });
}