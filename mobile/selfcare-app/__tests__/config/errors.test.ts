/**
 * errors.test.ts — verifies the OmobioError and ErrorCodes exports.
 */
import { OmobioError, ErrorCodes } from '../../src/config/errors';

describe('OmobioError', () => {
  it('carries code, message, and optional details', () => {
    const err = new OmobioError('SOME_CODE', 'something failed', { foo: 'bar' });
    expect(err.code).toBe('SOME_CODE');
    expect(err.message).toBe('something failed');
    expect(err.details).toEqual({ foo: 'bar' });
    expect(err).toBeInstanceOf(Error);
  });

  it('omits details when not provided', () => {
    const err = new OmobioError('CODE', 'msg');
    expect(err.details).toBeUndefined();
  });
});

describe('ErrorCodes', () => {
  it('exposes the canonical error code constants', () => {
    expect(ErrorCodes.UNAUTHORIZED).toBeDefined();
    expect(ErrorCodes.FORBIDDEN).toBeDefined();
    expect(ErrorCodes.NOT_FOUND).toBeDefined();
    expect(ErrorCodes.CONFLICT).toBeDefined();
    expect(ErrorCodes.RATE_LIMITED).toBeDefined();
    expect(ErrorCodes.SERVICE_UNAVAILABLE).toBeDefined();
  });
});
