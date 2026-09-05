/**
 * AuthSDK Tests
 *
 * Tests OTP send/verify, token refresh, and secure storage.
 *
 * Covers:
 * - OTP send (SMS and WhatsApp channels)
 * - OTP verify with correlation ID persistence
 * - Token refresh with 401 handling
 * - Secure token storage (MMKV)
 * - Sign out with session invalidation
 * - Device ID generation
 * - Auth event listeners
 */

import axios from 'axios';
import { AuthSDK } from '../../src/config/AuthSDK';
import { MMKV } from 'react-native-mmkv';

const mockedAxios = axios as jest.Mocked<typeof axios>;

describe('AuthSDK', () => {
  let auth: AuthSDK;
  const TENANT_ID = 'dialog-lk';
  const BASE_URL = 'http://test.api:8080';
  const MSISDN = '+94771234567';

  beforeEach(() => {
    const mmkv = new MMKV({ id: 'omobio-auth' });
    mmkv.clearAll();

    mockedAxios.create.mockReturnValue({
      get: jest.fn(),
      post: jest.fn(),
    } as any);

    auth = new AuthSDK(TENANT_ID, BASE_URL);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('OTP send', () => {
    it('sends OTP via SMS by default', async () => {
      const postMock = jest.fn().mockResolvedValue({
        data: {
          data: {
            success: true,
            correlationId: 'corr-123',
            expiresAt: '2026-09-03T12:00:00Z',
            maskedMsisdn: '+94****4567',
          },
        },
      });
      mockedAxios.post = postMock as any;

      const result = await auth.sendOtp(MSISDN);

      expect(postMock).toHaveBeenCalledWith(
        `${BASE_URL}/api/v1/auth/otp`,
        { msisdn: MSISDN, channel: 'SMS' },
        { headers: { 'X-Tenant-Id': TENANT_ID } }
      );
      expect(result.success).toBe(true);
      expect(result.correlationId).toBe('corr-123');
    });

    it('sends OTP via WhatsApp channel when specified', async () => {
      const postMock = jest.fn().mockResolvedValue({
        data: {
          data: { success: true, correlationId: 'corr-whatsapp' },
        },
      });
      mockedAxios.post = postMock as any;

      const result = await auth.sendOtp(MSISDN, 'WHATSAPP');

      expect(postMock.mock.calls[0][1].channel).toBe('WHATSAPP');
      expect(result.correlationId).toBe('corr-whatsapp');
    });

    it('persists correlation ID to secure storage on success', async () => {
      const postMock = jest.fn().mockResolvedValue({
        data: {
          data: { success: true, correlationId: 'corr-persist-123' },
        },
      });
      mockedAxios.post = postMock as any;

      await auth.sendOtp(MSISDN);

      const mmkv = new MMKV({ id: 'omobio-auth' });
      const stored = mmkv.getString('omobio_otp_correlation');
      expect(stored).toBe('corr-persist-123');
    });

    it('does not persist correlation ID on failure', async () => {
      const postMock = jest.fn().mockResolvedValue({
        data: {
          data: { success: false },
        },
      });
      mockedAxios.post = postMock as any;

      await auth.sendOtp(MSISDN);

      const mmkv = new MMKV({ id: 'omobio-auth' });
      expect(mmkv.contains('omobio_otp_correlation')).toBe(false);
    });
  });

  describe('OTP verify', () => {
    it('verifies OTP with correlation ID from storage', async () => {
      const mmkv = new MMKV({ id: 'omobio-auth' });
      mmkv.set('omobio_otp_correlation', 'corr-from-storage');

      const postMock = jest.fn().mockResolvedValue({
        data: {
          data: {
            success: true,
            accessToken: 'access-token-123',
            refreshToken: 'refresh-token-456',
            sessionId: 'session-789',
            expiresAt: '2026-10-15T12:00:00Z',
          },
        },
      });
      mockedAxios.post = postMock as any;

      const result = await auth.verifyOtp(MSISDN, '123456');

      expect(postMock).toHaveBeenCalledWith(
        `${BASE_URL}/api/v1/auth/otp/verify`,
        { msisdn: MSISDN, otpCode: '123456', correlationId: 'corr-from-storage' },
        { headers: { 'X-Tenant-Id': TENANT_ID } }
      );
      expect(result.success).toBe(true);
      expect(result.accessToken).toBe('access-token-123');
    });

    it('persists tokens to secure storage on successful verify', async () => {
      const mmkv = new MMKV({ id: 'omobio-auth' });
      mmkv.set('omobio_otp_correlation', 'corr-1');

      mockedAxios.post = jest.fn().mockResolvedValue({
        data: {
          data: {
            success: true,
            accessToken: 'new-access',
            refreshToken: 'new-refresh',
            sessionId: 'new-session',
          },
        },
      }) as any;

      await auth.verifyOtp(MSISDN, '123456');

      expect(mmkv.getString('omobio_access_token')).toBe('new-access');
      expect(mmkv.getString('omobio_refresh_token')).toBe('new-refresh');
      expect(mmkv.getString('omobio_session_id')).toBe('new-session');
    });

    it('clears correlation ID after successful verify', async () => {
      const mmkv = new MMKV({ id: 'omobio-auth' });
      mmkv.set('omobio_otp_correlation', 'corr-to-clear');

      mockedAxios.post = jest.fn().mockResolvedValue({
        data: {
          data: {
            success: true,
            accessToken: 'a',
            refreshToken: 'r',
            sessionId: 's',
          },
        },
      }) as any;

      await auth.verifyOtp(MSISDN, '123456');

      expect(mmkv.contains('omobio_otp_correlation')).toBe(false);
    });

    it('emits authenticated event on successful verify', async () => {
      const mmkv = new MMKV({ id: 'omobio-auth' });
      mmkv.set('omobio_otp_correlation', 'corr-evt');

      mockedAxios.post = jest.fn().mockResolvedValue({
        data: {
          data: { success: true, accessToken: 'a', refreshToken: 'r', sessionId: 's' },
        },
      }) as any;

      const listener = jest.fn();
      auth.addAuthListener(listener);

      await auth.verifyOtp(MSISDN, '123456');

      expect(listener).toHaveBeenCalledWith('authenticated', { msisdn: MSISDN });
    });

    it('does not persist tokens on failed verify', async () => {
      const mmkv = new MMKV({ id: 'omobio-auth' });
      mmkv.set('omobio_otp_correlation', 'corr-fail');

      mockedAxios.post = jest.fn().mockResolvedValue({
        data: {
          data: { success: false },
        },
      }) as any;

      await auth.verifyOtp(MSISDN, 'wrong');

      expect(mmkv.contains('omobio_access_token')).toBe(false);
      expect(mmkv.contains('omobio_refresh_token')).toBe(false);
    });
  });

  describe('Token refresh', () => {
    it('refreshes tokens using stored refresh token', async () => {
      const mmkv = new MMKV({ id: 'omobio-auth' });
      mmkv.set('omobio_refresh_token', 'old-refresh-token');
      mmkv.set('omobio_access_token', 'old-access-token');

      mockedAxios.post = jest.fn().mockResolvedValue({
        data: {
          data: {
            accessToken: 'new-access',
            refreshToken: 'new-refresh',
            sessionId: 's',
          },
        },
      }) as any;

      await auth.refreshTokens();

      expect(mockedAxios.post).toHaveBeenCalledWith(
        `${BASE_URL}/api/v1/auth/refresh`,
        { refreshToken: 'old-refresh-token' },
        { headers: { 'X-Tenant-Id': TENANT_ID } }
      );
      expect(mmkv.getString('omobio_access_token')).toBe('new-access');
      expect(mmkv.getString('omobio_refresh_token')).toBe('new-refresh');
    });

    it('throws error when no refresh token available', async () => {
      await expect(auth.refreshTokens()).rejects.toThrow('No refresh token available');
    });

    it('emits token_refreshed event on successful refresh', async () => {
      const mmkv = new MMKV({ id: 'omobio-auth' });
      mmkv.set('omobio_refresh_token', 'rt');

      mockedAxios.post = jest.fn().mockResolvedValue({
        data: {
          data: { accessToken: 'a', refreshToken: 'r', sessionId: 's' },
        },
      }) as any;

      const listener = jest.fn();
      auth.addAuthListener(listener);

      await auth.refreshTokens();

      expect(listener).toHaveBeenCalledWith('token_refreshed');
    });

    it('clears session and emits session_expired on 401', async () => {
      const mmkv = new MMKV({ id: 'omobio-auth' });
      mmkv.set('omobio_refresh_token', 'expired-rt');
      mmkv.set('omobio_access_token', 'expired-at');
      mmkv.set('omobio_session_id', 'expired-s');

      const error = new Error('Unauthorized') as any;
      error.response = { status: 401 };
      mockedAxios.post = jest.fn().mockRejectedValue(error) as any;

      const listener = jest.fn();
      auth.addAuthListener(listener);

      await expect(auth.refreshTokens()).rejects.toThrow();

      expect(listener).toHaveBeenCalledWith('session_expired');
      expect(mmkv.contains('omobio_access_token')).toBe(false);
      expect(mmkv.contains('omobio_refresh_token')).toBe(false);
    });
  });

  describe('isAuthenticated()', () => {
    it('returns false when no access token stored', () => {
      expect(auth.isAuthenticated()).toBe(false);
    });

    it('returns true when access token exists in storage', () => {
      const mmkv = new MMKV({ id: 'omobio-auth' });
      mmkv.set('omobio_access_token', 'some-token');
      expect(auth.isAuthenticated()).toBe(true);
    });
  });

  describe('signOut()', () => {
    it('invalidates session on server', async () => {
      const mmkv = new MMKV({ id: 'omobio-auth' });
      mmkv.set('omobio_access_token', 'at');
      mmkv.set('omobio_refresh_token', 'rt');
      mmkv.set('omobio_session_id', 'sess-id');

      const postMock = jest.fn().mockResolvedValue({ data: {} });
      mockedAxios.post = postMock as any;

      await auth.signOut();

      expect(postMock).toHaveBeenCalledWith(
        `${BASE_URL}/api/v1/auth/signout`,
        { sessionId: 'sess-id' },
        expect.objectContaining({
          headers: expect.objectContaining({
            'X-Tenant-Id': TENANT_ID,
            Authorization: 'Bearer at',
          }),
        })
      );
    });

    it('clears local session after sign out', async () => {
      const mmkv = new MMKV({ id: 'omobio-auth' });
      mmkv.set('omobio_access_token', 'at');
      mmkv.set('omobio_refresh_token', 'rt');
      mmkv.set('omobio_session_id', 's');

      mockedAxios.post = jest.fn().mockResolvedValue({}) as any;

      await auth.signOut();

      expect(mmkv.contains('omobio_access_token')).toBe(false);
      expect(mmkv.contains('omobio_refresh_token')).toBe(false);
      expect(mmkv.contains('omobio_session_id')).toBe(false);
    });

    it('emits signed_out event', async () => {
      const mmkv = new MMKV({ id: 'omobio-auth' });
      mmkv.set('omobio_session_id', 's');

      mockedAxios.post = jest.fn().mockResolvedValue({}) as any;

      const listener = jest.fn();
      auth.addAuthListener(listener);

      await auth.signOut();

      expect(listener).toHaveBeenCalledWith('signed_out');
    });

    it('clears local session even if server call fails', async () => {
      const mmkv = new MMKV({ id: 'omobio-auth' });
      mmkv.set('omobio_access_token', 'at');
      mmkv.set('omobio_session_id', 's');

      mockedAxios.post = jest.fn().mockRejectedValue(new Error('Network')) as any;

      await auth.signOut();

      // Best-effort: local cleanup should still happen
      expect(mmkv.contains('omobio_access_token')).toBe(false);
    });
  });

  describe('getDeviceId()', () => {
    it('generates a new device ID on first call', async () => {
      const id = await auth.getDeviceId();
      expect(id).toMatch(/^dev-\d+-[a-z0-9]+$/);
    });

    it('returns the same device ID on subsequent calls', async () => {
      const id1 = await auth.getDeviceId();
      const id2 = await auth.getDeviceId();
      expect(id1).toBe(id2);
    });
  });

  describe('auth listeners', () => {
    it('supports multiple listeners', async () => {
      const mmkv = new MMKV({ id: 'omobio-auth' });
      mmkv.set('omobio_otp_correlation', 'c');

      mockedAxios.post = jest.fn().mockResolvedValue({
        data: { data: { success: true, accessToken: 'a', refreshToken: 'r', sessionId: 's' } },
      }) as any;

      const listener1 = jest.fn();
      const listener2 = jest.fn();
      auth.addAuthListener(listener1);
      auth.addAuthListener(listener2);

      await auth.verifyOtp(MSISDN, '123');

      expect(listener1).toHaveBeenCalledWith('authenticated', expect.any(Object));
      expect(listener2).toHaveBeenCalledWith('authenticated', expect.any(Object));
    });

    it('supports unsubscribing via returned function', async () => {
      const mmkv = new MMKV({ id: 'omobio-auth' });
      mmkv.set('omobio_otp_correlation', 'c');
      mmkv.set('omobio_session_id', 's');

      mockedAxios.post = jest.fn().mockResolvedValue({}) as any;

      const listener = jest.fn();
      const unsubscribe = auth.addAuthListener(listener);
      unsubscribe();

      await auth.signOut();

      expect(listener).not.toHaveBeenCalled();
    });
  });
});
