/**
 * errors.test.ts — verifies the selfcareError and ErrorCodes exports.
 */
import { selfcareError, ErrorCodes } from '../../src/config/errors';

describe('selfcareError', () => {
  it('carries code, message, and optional details', () => {
    const err = new selfcareError('SOME_CODE', 'something failed', { foo: 'bar' });
    expect(err.code).toBe('SOME_CODE');
    expect(err.message).toBe('something failed');
    expect(err.details).toEqual({ foo: 'bar' });
    expect(err).toBeInstanceOf(Error);
  });

  it('omits details when not provided', () => {
    const err = new selfcareError('CODE', 'msg');
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
