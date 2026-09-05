import { SecureStorage } from '../src/services/SecureStorage';

/**
 * Tests for SecureStorage and related security helpers.
 *
 * Run with: npx jest tests/security/
 */
describe('SecureStorage', () => {
  beforeEach(async () => {
    // Wipe before each test
    await SecureStorage.clear();
  });

  afterAll(async () => {
    await SecureStorage.clear();
  });

  it('round-trips a value', async () => {
    const ok = await SecureStorage.setItem('refresh_token', 'abc-123', { service: 'test' });
    expect(ok).toBe(true);
    const v = await SecureStorage.getItem('refresh_token', { service: 'test' });
    expect(v).toBe('abc-123');
  });

  it('returns null for missing key', async () => {
    const v = await SecureStorage.getItem('does-not-exist', { service: 'test' });
    expect(v).toBeNull();
  });

  it('removes a value', async () => {
    await SecureStorage.setItem('x', 'y', { service: 'test' });
    await SecureStorage.removeItem('x', { service: 'test' });
    const v = await SecureStorage.getItem('x', { service: 'test' });
    expect(v).toBeNull();
  });

  it('isolates by service / tenant', async () => {
    await SecureStorage.setItem('k', 'v1', { service: 'tenant-a' });
    await SecureStorage.setItem('k', 'v2', { service: 'tenant-b' });
    expect(await SecureStorage.getItem('k', { service: 'tenant-a' })).toBe('v1');
    expect(await SecureStorage.getItem('k', { service: 'tenant-b' })).toBe('v2');
  });

  it('clears all keys for a service', async () => {
    await SecureStorage.setItem('a', '1', { service: 'tenant' });
    await SecureStorage.setItem('b', '2', { service: 'tenant' });
    await SecureStorage.setItem('a', '1', { service: 'other' });
    await SecureStorage.clear({ service: 'tenant' });
    expect(await SecureStorage.getItem('a', { service: 'tenant' })).toBeNull();
    expect(await SecureStorage.getItem('b', { service: 'tenant' })).toBeNull();
    expect(await SecureStorage.getItem('a', { service: 'other' })).toBe('1');
  });

  it('lists keys for a service', async () => {
    await SecureStorage.setItem('a', '1', { service: 'tenant' });
    await SecureStorage.setItem('b', '2', { service: 'tenant' });
    const keys = await SecureStorage.listKeys({ service: 'tenant' });
    expect(keys.sort()).toEqual(['a', 'b']);
  });
});

import { Crypto } from '../src/utils/crypto';

describe('Crypto helpers', () => {
  it('sha256 produces 64 hex chars', async () => {
    const h = await Crypto.sha256('hello world');
    expect(h).toMatch(/^[0-9a-f]{64}$/);
    // Known vector
    expect(h).toBe('b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9');
  });

  it('hmacSha256 is deterministic for same input', async () => {
    const h1 = await Crypto.hmacSha256('payload', 'secret');
    const h2 = await Crypto.hmacSha256('payload', 'secret');
    expect(h1).toBe(h2);
  });

  it('randomString is cryptographically random', () => {
    const set = new Set<string>();
    for (let i = 0; i < 100; i++) {
      set.add(Crypto.randomString(32));
    }
    expect(set.size).toBe(100); // all unique
  });

  it('uuidv4 is well-formed', () => {
    const id = Crypto.uuidv4();
    expect(id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  });

  it('encrypt/decrypt is reversible', async () => {
    const key = new Uint8Array(32);
    for (let i = 0; i < 32; i++) key[i] = i;
    const ct = await Crypto.encryptAesGcm('hello world', key);
    const pt = await Crypto.decryptAesGcm(ct, key);
    expect(pt).toBe('hello world');
  });

  it('AES-GCM rejects tampered ciphertext', async () => {
    const key = new Uint8Array(32);
    for (let i = 0; i < 32; i++) key[i] = i;
    const ct = await Crypto.encryptAesGcm('hello world', key);
    // Tamper
    const tampered = ct.slice(0, -1) + (ct.endsWith('A') ? 'B' : 'A');
    await expect(Crypto.decryptAesGcm(tampered, key)).rejects.toThrow();
  });

  it('constantTimeEqual is true for identical and false for different', () => {
    expect(Crypto.constantTimeEqual('abc', 'abc')).toBe(true);
    expect(Crypto.constantTimeEqual('abc', 'abd')).toBe(false);
    expect(Crypto.constantTimeEqual('abc', 'abcd')).toBe(false);
  });
});

import ConsentManager from '../src/services/ConsentManager';

describe('ConsentManager', () => {
  beforeEach(async () => {
    await SecureStorage.clear();
  });

  it('grant stores a granted consent', async () => {
    await ConsentManager.grant('TERMS_OF_SERVICE', 'signup-screen');
    expect(await ConsentManager.isGranted('TERMS_OF_SERVICE')).toBe(true);
  });

  it('revoke stores a denied consent and adds to denied set', async () => {
    await ConsentManager.revoke('MARKETING_EMAIL', 'settings-screen');
    expect(await ConsentManager.isGranted('MARKETING_EMAIL')).toBe(false);
    const denied = await ConsentManager.getDeniedPurposes();
    expect(denied.has('MARKETING_EMAIL')).toBe(true);
  });

  it('returns false for ungranted purpose', async () => {
    expect(await ConsentManager.isGranted('MARKETING_SMS')).toBe(false);
  });

  it('grant then revoke toggles the consent', async () => {
    await ConsentManager.grant('PRIVACY_POLICY', 'first-launch');
    expect(await ConsentManager.isGranted('PRIVACY_POLICY')).toBe(true);
    await ConsentManager.revoke('PRIVACY_POLICY', 'settings-screen');
    expect(await ConsentManager.isGranted('PRIVACY_POLICY')).toBe(false);
  });

  it('exportUserData returns a serializable object', async () => {
    await ConsentManager.grant('TERMS_OF_SERVICE', 'signup');
    const data = await ConsentManager.exportUserData();
    expect(data.exportedAt).toBeDefined();
    expect(data.consents.TERMS_OF_SERVICE?.granted).toBe(true);
  });
});
