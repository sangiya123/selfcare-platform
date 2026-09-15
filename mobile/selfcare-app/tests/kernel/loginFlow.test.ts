import { resolveLoginPlan, parseExternalAuthReturn } from '../../src/config/loginFlow';

describe('loginFlow (mobile kernel)', () => {
  it('reports unconfigured auth without inventing a journey', () => {
    const plan = resolveLoginPlan(null);
    expect(plan.configured).toBe(false);
    expect(plan.flow).toBeNull();
    expect(plan.enabledMethods).toEqual([]);
    expect(plan.defaultMethod).toBeNull();
  });

  it('resolves enabled methods, default method and external flow from the manifest', () => {
    const plan = resolveLoginPlan({
      auth: {
        defaultMethod: 'otp',
        methods: [
          { method: 'otp', enabled: true, order: 1, options: { channels: ['sms', 'whatsapp'] } },
          { method: 'pin', enabled: false, order: 2 },
        ],
        sessionPolicy: { maxAttempts: 5, lockoutSeconds: 300 },
        fallback: 'pin',
        login: {
          mode: 'external',
          steps: ['languageSelection', 'externalAuth'],
          external: {
            url: 'https://selfcare.dialog.lk/login',
            returnScheme: 'selfcare',
            returnPath: '/login/callback',
            codeParam: 'code',
            errorParam: 'error',
          },
        },
      },
    });

    expect(plan.configured).toBe(true);
    expect(plan.enabledMethods.map((m) => m.method)).toEqual(['otp']);
    expect(plan.defaultMethod).toBe('otp');
    expect(plan.flow?.mode).toBe('external');
    expect(plan.external?.returnScheme).toBe('selfcare');
  });

  it('parses a valid external-auth deep-link return', () => {
    const plan = resolveLoginPlan({
      auth: {
        login: {
          mode: 'external',
          steps: ['externalAuth'],
          external: {
            url: 'https://x.example/login',
            returnScheme: 'selfcare',
            returnPath: '/login/callback',
          },
        },
      },
    });
    const result = parseExternalAuthReturn(
      'selfcare://login/callback?code=abc123',
      plan.flow
    );
    expect(result).toEqual({ code: 'abc123' });
  });

  it('parses an error return', () => {
    const plan = resolveLoginPlan({
      auth: {
        login: {
          mode: 'external',
          steps: ['externalAuth'],
          external: { url: 'https://x.example/login', returnScheme: 'selfcare' },
        },
      },
    });
    expect(
      parseExternalAuthReturn('selfcare://?error=access_denied', plan.flow)
    ).toEqual({ error: 'access_denied' });
  });

  it('ignores URLs that are not the configured return', () => {
    const plan = resolveLoginPlan(null);
    expect(parseExternalAuthReturn('selfcare://home', null)).toBeNull();
  });
});