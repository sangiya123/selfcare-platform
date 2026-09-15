/**
 * ActionEngine — Dispatches actions emitted by the server-driven UI.
 *
 * Implements the closed action set defined in ADR-009. Config cannot
 * execute arbitrary code — only registered actions are dispatched.
 *
 * The 18 supported action types:
 *   1. NAVIGATE       — in-app navigation
 *   2. CALL_API       — fire-and-forget API call (e.g. analytics)
 *   3. START_JOURNEY  — start a multi-step journey
 *   4. OPEN_URL       — open external URL (browser or in-app)
 *   5. COPY_TO_CLIPBOARD
 *   6. SHOW_MODAL     — open a modal dialog
 *   7. SHOW_TOAST     — show a transient notification
 *   8. DISMISS        — close a modal/toast
 *   9. REFRESH        — refresh current data
 *  10. OPEN_DRAWER    — open side drawer
 *  11. CLOSE_DRAWER   — close drawer
 *  12. SCROLL_TO      — scroll to a section/anchor
 *  13. SET_VARIABLE   — set a local variable (e.g. form state)
 *  14. LOG_EVENT      — log an analytics event
 *  15. PAYMENT        — start a payment flow
 *  16. SHARE          — share content (OS share sheet)
 *  17. CALL_NUMBER    — dial a phone number
 *  18. SEND_SMS       — open SMS composer
 */
import { Clipboard, Share, Alert } from 'react-native';
import LinkingDefault from 'react-native/Libraries/Linking/Linking';
import type { Linking as LinkingStatic } from 'react-native';
import type { Action } from './types';
import type { SelfcareSDK } from './ConfigSDK';

const Linking = LinkingDefault as unknown as LinkingStatic;

export interface ActionContext {
  navigate?: (route: string, params?: Record<string, unknown>) => void;
  showToast?: (message: string, variant?: 'info' | 'success' | 'error') => void;
  showModal?: (modalId: string, props?: Record<string, unknown>) => void;
  dismissModal?: () => void;
  openDrawer?: () => void;
  closeDrawer?: () => void;
  scrollTo?: (anchor: string) => void;
  setVariable?: (name: string, value: unknown) => void;
  startJourney?: (journeyId: string, entryState?: Record<string, unknown>) => void;
  startPayment?: (paymentId: string) => void;
  refresh?: () => void;
  logEvent?: (eventName: string, properties?: Record<string, unknown>) => void;
}

export class ActionEngine {
  private sdk: SelfcareSDK;
  private context: ActionContext = {};

  constructor(sdk: SelfcareSDK) {
    this.sdk = sdk;
  }

  /** Register runtime callbacks. The app wires this once at startup. */
  setContext(ctx: ActionContext): void {
    this.context = { ...this.context, ...ctx };
  }

  /** Dispatch an action. */
  async execute(action: Action): Promise<void> {
    if (!action || !action.type) {
      // eslint-disable-next-line no-console
      console.warn('[ActionEngine] empty action', action);
      return;
    }

    try {
      switch (action.type) {
        case 'NAVIGATE':
          this.context.navigate?.(action.payload?.route as string, action.payload?.params);
          break;

        case 'CALL_API':
          await this.sdk.api.post(action.payload?.endpoint as string, action.payload?.body ?? {});
          break;

        case 'START_JOURNEY':
          this.context.startJourney?.(
            action.payload?.journeyId as string,
            action.payload?.state as Record<string, unknown>
          );
          break;

        case 'OPEN_URL':
          if (action.payload?.url) {
            await Linking.openURL(action.payload.url as string);
          }
          break;

        case 'COPY_TO_CLIPBOARD':
          if (action.payload?.text != null) {
            Clipboard.setString(String(action.payload.text));
            this.context.showToast?.('Copied to clipboard', 'success');
          }
          break;

        case 'SHOW_MODAL':
          this.context.showModal?.(
            action.payload?.modalId as string,
            action.payload?.props as Record<string, unknown>
          );
          break;

        case 'SHOW_TOAST':
          this.context.showToast?.(
            (action.payload?.message as string) ?? '',
            (action.payload?.variant as 'info' | 'success' | 'error') ?? 'info'
          );
          break;

        case 'DISMISS':
          this.context.dismissModal?.();
          break;

        case 'REFRESH':
          this.context.refresh?.();
          break;

        case 'OPEN_DRAWER':
          this.context.openDrawer?.();
          break;

        case 'CLOSE_DRAWER':
          this.context.closeDrawer?.();
          break;

        case 'SCROLL_TO':
          this.context.scrollTo?.(action.payload?.anchor as string);
          break;

        case 'SET_VARIABLE':
          if (action.payload?.name) {
            this.context.setVariable?.(
              action.payload.name as string,
              action.payload.value
            );
          }
          break;

        case 'LOG_EVENT':
          this.context.logEvent?.(
            (action.payload?.event as string) ?? 'unknown',
            action.payload?.properties as Record<string, unknown>
          );
          break;

        case 'PAYMENT':
          this.context.startPayment?.(action.payload?.paymentId as string);
          break;

        case 'SHARE':
          if (action.payload?.message || action.payload?.url) {
            await Share.share({
              message: (action.payload?.message as string) ?? '',
              url: action.payload?.url as string | undefined,
              title: action.payload?.title as string | undefined,
            });
          }
          break;

        case 'CALL_NUMBER':
          if (action.payload?.phoneNumber) {
            const url = `tel:${String(action.payload.phoneNumber).replace(/[^\d+]/g, '')}`;
            const supported = await Linking.canOpenURL(url);
            if (supported) {
              await Linking.openURL(url);
            } else {
              Alert.alert('Cannot place call', 'This device cannot place phone calls.');
            }
          }
          break;

        case 'SEND_SMS':
          if (action.payload?.phoneNumber) {
            const body = action.payload?.body ? `&body=${encodeURIComponent(String(action.payload.body))}` : '';
            await Linking.openURL(`sms:${String(action.payload.phoneNumber)}${body}`);
          }
          break;

        default:
          // eslint-disable-next-line no-console
          console.warn(`[ActionEngine] unhandled action type: ${(action as any).type}`);
      }
    } catch (err) {
      // eslint-disable-next-line no-console
      console.error(`[ActionEngine] action ${action.type} failed`, err);
    }
  }
}

// Silence unused import warning for ActionSheetIOS (used by some platform branches)
// (none currently unused)
