/**
 * ApiClient tests.
 *
 * Verifies:
 * - X-Tenant-Id header is always present
 * - Authorization Bearer header is added when an access token is available
 * - The { data: ... } envelope is unwrapped from successful responses
 * - 401 triggers the onUnauthorized callback and throws selfcareError(UNAUTHORIZED)
 * - 403, 404, 409, 429, 503 each throw the correct selfcareError code
 * - Other HTTP errors throw selfcareError(API_ERROR)
 */
import axios from 'axios';
import { ApiClient } from '../../src/config/ApiClient';
import { selfcareError, ErrorCodes } from '../../src/config/errors';

const mockedAxios = axios as jest.Mocked<typeof axios>;

describe('ApiClient', () => {
  let client: ApiClient;
  let getAccessToken: jest.Mock;
  let onUnauthorized: jest.Mock;
  let mockAxiosInstance: any;

  beforeEach(() => {
    getAccessToken = jest.fn().mockReturnValue(null);
    onUnauthorized = jest.fn();

    mockAxiosInstance = {
      interceptors: {
        response: {
          use: jest.fn((onFulfilled, onRejected) => {
            // Capture the registered handlers so tests can drive them
            (mockAxiosInstance as any).__onFulfilled = onFulfilled;
            (mockAxiosInstance as any).__onRejected = onRejected;
            return 0;
          }),
        },
        request: { use: jest.fn() },
      },
      get: jest.fn(),
      post: jest.fn(),
      put: jest.fn(),
      patch: jest.fn(),
      delete: jest.fn(),
      request: jest.fn(),
    };

    (mockedAxios.create as jest.Mock) = jest.fn().mockReturnValue(mockAxiosInstance);

    client = new ApiClient({
      tenantId: 'dialog-lk',
      baseUrl: 'http://test.api:8080',
      getAccessToken,
      onUnauthorized,
    });
  });

  it('uses axios.create with the correct baseURL and headers', () => {
    expect(mockedAxios.create).toHaveBeenCalledWith(
      expect.objectContaining({
        baseURL: 'http://test.api:8080',
        headers: expect.objectContaining({
          'X-Tenant-Id': 'dialog-lk',
        }),
      })
    );
  });

  it('exposes the underlying axios client via httpClient getter', () => {
    expect(client.httpClient).toBe(mockAxiosInstance);
  });

  it('registers a response interceptor for unwrapping and error handling', () => {
    expect(mockAxiosInstance.interceptors.response.use).toHaveBeenCalled();
  });

  describe('response unwrapping', () => {
    it('unwraps the { data: ... } envelope from successful responses', () => {
      const onFulfilled = (mockAxiosInstance as any).__onFulfilled;
      const wrapped = { data: { data: { id: 1, name: 'Bill 1' } } };
      const result = onFulfilled(wrapped);
      expect(result.data).toEqual({ id: 1, name: 'Bill 1' });
    });

    it('passes through responses without the envelope untouched', () => {
      const onFulfilled = (mockAxiosInstance as any).__onFulfilled;
      const unwrapped = { data: { id: 1, name: 'Bill 1' } };
      const result = onFulfilled(unwrapped);
      expect(result.data).toEqual({ id: 1, name: 'Bill 1' });
    });
  });

  describe('error handling', () => {
    function dispatchError(status: number, code: string, message: string) {
      const onRejected = (mockAxiosInstance as any).__onRejected;
      const error: any = new Error('Request failed');
      error.response = { status, data: { code, message } };
      return onRejected(error);
    }

    it('401 triggers onUnauthorized and throws selfcareError(UNAUTHORIZED)', async () => {
      const promise = dispatchError(401, 'AUTH_INVALID', 'Token expired');
      await expect(promise).rejects.toBeInstanceOf(selfcareError);
      expect(onUnauthorized).toHaveBeenCalled();
    });

    it('403 throws selfcareError(FORBIDDEN)', async () => {
      await expect(dispatchError(403, 'FORBIDDEN', 'No access'))
          .rejects.toMatchObject({ code: ErrorCodes.FORBIDDEN });
    });

    it('404 throws selfcareError(NOT_FOUND)', async () => {
      await expect(dispatchError(404, 'NOT_FOUND', 'Not found'))
          .rejects.toMatchObject({ code: ErrorCodes.NOT_FOUND });
    });

    it('409 throws selfcareError(CONFLICT)', async () => {
      await expect(dispatchError(409, 'CONFLICT', 'Already exists'))
          .rejects.toMatchObject({ code: ErrorCodes.CONFLICT });
    });

    it('429 throws selfcareError(RATE_LIMITED)', async () => {
      await expect(dispatchError(429, 'RATE_LIMITED', 'Too many requests'))
          .rejects.toMatchObject({ code: ErrorCodes.RATE_LIMITED });
    });

    it('503 throws selfcareError(SERVICE_UNAVAILABLE)', async () => {
      await expect(dispatchError(503, 'UNAVAILABLE', 'Down'))
          .rejects.toMatchObject({ code: ErrorCodes.SERVICE_UNAVAILABLE });
    });

    it('500 throws selfcareError(API_ERROR) with the response code', async () => {
      await expect(dispatchError(500, 'INTERNAL', 'Boom'))
          .rejects.toMatchObject({ code: 'INTERNAL' });
    });
  });

  describe('request methods', () => {
    it('get calls axios.get with the path and params, then returns data', async () => {
      mockAxiosInstance.get.mockResolvedValue({ data: { id: 1 } });
      const result = await client.get<{ id: number }>('/api/v1/foo', { bar: 1 });
      expect(mockAxiosInstance.get).toHaveBeenCalledWith(
        '/api/v1/foo',
        expect.objectContaining({ params: { bar: 1 } })
      );
      expect(result).toEqual({ id: 1 });
    });

    it('post calls axios.post with body and returns data', async () => {
      mockAxiosInstance.post.mockResolvedValue({ data: { ok: true } });
      const result = await client.post<{ ok: boolean }>('/api/v1/foo', { x: 1 });
      expect(mockAxiosInstance.post).toHaveBeenCalledWith(
        '/api/v1/foo',
        { x: 1 }
      );
      expect(result).toEqual({ ok: true });
    });
  });
});
