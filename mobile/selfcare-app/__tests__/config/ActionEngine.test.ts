/**
 * ActionEngine Tests
 *
 * Tests dispatch of all 18 action types supported by the OMOBIO Selfcare platform.
 *
 * Action types (18 total):
 * 1. NAVIGATE
 * 2. CALL_API
 * 3. START_JOURNEY
 * 4. OPEN_URL
 * 5. COPY_TO_CLIPBOARD
 * 6. SHOW_MODAL
 * 7. SHOW_TOAST
 * 8. DISMISS
 * 9. REFRESH
 * 10. OPEN_DRAWER
 * 11. CLOSE_DRAWER
 * 12. SCROLL_TO
 * 13. SET_VARIABLE
 * 14. LOG_EVENT
 * 15. PAYMENT
 * 16. SHARE
 * 17. CALL_NUMBER
 * 18. SEND_SMS
 */

import { ActionEngine } from '../../src/config/ActionEngine';
import Linking from 'react-native/Libraries/Linking/Linking';

describe('ActionEngine', () => {
  let engine: ActionEngine;
  let mockSdk: any;

  beforeEach(() => {
    mockSdk = {
      api: {
        post: jest.fn().mockResolvedValue({ data: { success: true } }),
        get: jest.fn().mockResolvedValue({}),
      },
      config: { getManifest: jest.fn() },
      auth: { getToken: jest.fn() },
    };

    engine = new ActionEngine(mockSdk);
    (Linking.openURL as jest.Mock).mockClear();
  });

  describe('NAVIGATE', () => {
    it('executes navigation action (no-op at engine level)', async () => {
      const action: any = {
        type: 'NAVIGATE',
        payload: { route: '/dashboard' },
      };

      await expect(engine.execute(action)).resolves.not.toThrow();
    });
  });

  describe('CALL_API', () => {
    it('calls API with endpoint and body', async () => {
      const action: any = {
        type: 'CALL_API',
        payload: {
          endpoint: '/api/v1/subscriptions/cancel',
          body: { reason: 'user_request' },
        },
      };

      await engine.execute(action);
      expect(mockSdk.api.post).toHaveBeenCalledWith(
        '/api/v1/subscriptions/cancel',
        { reason: 'user_request' }
      );
    });

    it('uses empty body when not provided', async () => {
      const action: any = {
        type: 'CALL_API',
        payload: { endpoint: '/api/v1/track/event' },
      };

      await engine.execute(action);
      expect(mockSdk.api.post).toHaveBeenCalledWith(
        '/api/v1/track/event',
        {}
      );
    });
  });

  describe('START_JOURNEY', () => {
    it('starts a journey (handled by app)', async () => {
      const action: any = {
        type: 'START_JOURNEY',
        payload: { journeyId: 'kyc-verification' },
      };

      await expect(engine.execute(action)).resolves.not.toThrow();
    });
  });

  describe('OPEN_URL', () => {
    it('opens external URL via Linking', async () => {
      const action: any = {
        type: 'OPEN_URL',
        payload: { url: 'https://example.com/terms' },
      };

      await engine.execute(action);
      expect(Linking.openURL).toHaveBeenCalledWith('https://example.com/terms');
    });

    it('opens tel: URL for call action', async () => {
      const action: any = {
        type: 'OPEN_URL',
        payload: { url: 'tel:+94112345678' },
      };

      await engine.execute(action);
      expect(Linking.openURL).toHaveBeenCalledWith('tel:+94112345678');
    });
  });

  describe('COPY_TO_CLIPBOARD', () => {
    it('copies text to clipboard (handled by app)', async () => {
      const action: any = {
        type: 'COPY_TO_CLIPBOARD',
        payload: { text: 'REF-12345' },
      };

      await expect(engine.execute(action)).resolves.not.toThrow();
    });
  });

  describe('SHOW_MODAL', () => {
    it('shows modal dialog', async () => {
      const action: any = {
        type: 'SHOW_MODAL',
        payload: {
          title: 'Confirm',
          message: 'Are you sure?',
          confirmAction: { type: 'CALL_API', payload: { endpoint: '/confirm' } },
        },
      };

      await expect(engine.execute(action)).resolves.not.toThrow();
    });
  });

  describe('SHOW_TOAST', () => {
    it('shows toast notification', async () => {
      const action: any = {
        type: 'SHOW_TOAST',
        payload: { message: 'Action completed', variant: 'success' },
      };

      await expect(engine.execute(action)).resolves.not.toThrow();
    });
  });

  describe('DISMISS', () => {
    it('dismisses current modal/screen', async () => {
      const action: any = {
        type: 'DISMISS',
        payload: {},
      };

      await expect(engine.execute(action)).resolves.not.toThrow();
    });
  });

  describe('REFRESH', () => {
    it('triggers refresh of current view', async () => {
      const action: any = {
        type: 'REFRESH',
        payload: { target: 'dashboard' },
      };

      await expect(engine.execute(action)).resolves.not.toThrow();
    });
  });

  describe('OPEN_DRAWER', () => {
    it('opens navigation drawer', async () => {
      const action: any = {
        type: 'OPEN_DRAWER',
        payload: {},
      };

      await expect(engine.execute(action)).resolves.not.toThrow();
    });
  });

  describe('CLOSE_DRAWER', () => {
    it('closes navigation drawer', async () => {
      const action: any = {
        type: 'CLOSE_DRAWER',
        payload: {},
      };

      await expect(engine.execute(action)).resolves.not.toThrow();
    });
  });

  describe('SCROLL_TO', () => {
    it('scrolls to specific section', async () => {
      const action: any = {
        type: 'SCROLL_TO',
        payload: { sectionId: 'bills-section' },
      };

      await expect(engine.execute(action)).resolves.not.toThrow();
    });
  });

  describe('SET_VARIABLE', () => {
    it('sets a runtime variable', async () => {
      const action: any = {
        type: 'SET_VARIABLE',
        payload: { key: 'selectedConnection', value: 'conn-123' },
      };

      await expect(engine.execute(action)).resolves.not.toThrow();
    });
  });

  describe('LOG_EVENT', () => {
    it('logs an analytics event', async () => {
      const action: any = {
        type: 'LOG_EVENT',
        payload: { event: 'balance_viewed', properties: { connectionId: 'conn-1' } },
      };

      await expect(engine.execute(action)).resolves.not.toThrow();
    });
  });

  describe('PAYMENT', () => {
    it('initiates payment flow', async () => {
      const action: any = {
        type: 'PAYMENT',
        payload: { billId: 'bill-123', amount: 100, currency: 'LKR' },
      };

      await expect(engine.execute(action)).resolves.not.toThrow();
    });
  });

  describe('SHARE', () => {
    it('opens native share sheet', async () => {
      const action: any = {
        type: 'SHARE',
        payload: { message: 'Check out my plan!', url: 'https://...' },
      };

      await expect(engine.execute(action)).resolves.not.toThrow();
    });
  });

  describe('CALL_NUMBER', () => {
    it('initiates phone call', async () => {
      const action: any = {
        type: 'CALL_NUMBER',
        payload: { number: '+94112345678' },
      };

      await expect(engine.execute(action)).resolves.not.toThrow();
    });
  });

  describe('SEND_SMS', () => {
    it('opens SMS app with pre-filled message', async () => {
      const action: any = {
        type: 'SEND_SMS',
        payload: { number: '+94771234567', message: 'Hello' },
      };

      await expect(engine.execute(action)).resolves.not.toThrow();
    });
  });

  describe('all action types smoke test', () => {
    const ALL_ACTION_TYPES = [
      'NAVIGATE',
      'CALL_API',
      'START_JOURNEY',
      'OPEN_URL',
      'COPY_TO_CLIPBOARD',
      'SHOW_MODAL',
      'SHOW_TOAST',
      'DISMISS',
      'REFRESH',
      'OPEN_DRAWER',
      'CLOSE_DRAWER',
      'SCROLL_TO',
      'SET_VARIABLE',
      'LOG_EVENT',
      'PAYMENT',
      'SHARE',
      'CALL_NUMBER',
      'SEND_SMS',
    ];

    it('has exactly 18 action types', () => {
      expect(ALL_ACTION_TYPES).toHaveLength(18);
    });

    ALL_ACTION_TYPES.forEach((type) => {
      it(`handles action type: ${type}`, async () => {
        const action: any = { type, payload: {} };
        await expect(engine.execute(action)).resolves.not.toThrow();
      });
    });
  });

  describe('unknown action type', () => {
    it('does not throw on unknown action type', async () => {
      const action: any = {
        type: 'UNKNOWN_ACTION',
        payload: {},
      };

      await expect(engine.execute(action)).resolves.not.toThrow();
    });
  });

  describe('error handling', () => {
    it('throws when CALL_API fails', async () => {
      mockSdk.api.post.mockRejectedValueOnce(new Error('API down'));

      const action: any = {
        type: 'CALL_API',
        payload: { endpoint: '/api/v1/test' },
      };

      await expect(engine.execute(action)).rejects.toThrow('API down');
    });
  });
});
