/**
 * Auth method gateway — resolves the tenant's auth strategy from the compiled
 * manifest ONLY. The app never invents policy values: every factor, order,
 * fallback, attempt limit and lockout window is authored in the admin portal
 * and delivered in the manifest (v6 rule #1).
 *
 * When a manifest without an `auth` block is present the strategy reports
 * `configured: false` and auth screens render a "config missing" state —
 * they never fall back to a fabricated factor policy.
 */
import { ExperienceManifest, ManifestAuthConfig, AuthMethodConfig } from '../manifest/types';

export interface ResolvedAuthStrategy {
  /** False when the manifest carries no auth strategy (tenant unconfigured). */
  configured: boolean;
  defaultMethod: string | null;
  /** Methods enabled+presented first (sorted by `order`, then declaration). */
  enabledMethods: AuthMethodConfig[];
  fallback: ManifestAuthConfig['fallback'] | null;
  sessionPolicy: ManifestAuthConfig['sessionPolicy'] | null;
  isEnabled(method: AuthMethodConfig['method']): boolean;
  get(method: AuthMethodConfig['method']): AuthMethodConfig | undefined;
  /** OTP delivery channels offered by this tenant, e.g. ["sms","email"]. */
  otpChannels(): string[];
  /** PIN length for the configured pin method (null when unconfigured). */
  pinLength(): number | null;
}

export function resolveAuthStrategy(
  manifest: ExperienceManifest | null | undefined
): ResolvedAuthStrategy {
  const cfg = (manifest as unknown as { auth?: ManifestAuthConfig })?.auth;
  const unconfigured: ResolvedAuthStrategy = {
    configured: false,
    defaultMethod: null,
    enabledMethods: [],
    fallback: null,
    sessionPolicy: null,
    isEnabled: () => false,
    get: () => undefined,
    otpChannels: () => [],
    pinLength: () => null,
  };

  if (!cfg || !Array.isArray(cfg.methods) || cfg.methods.length === 0) {
    return unconfigured;
  }

  const enabledMethods = cfg.methods
    .filter((m) => m.enabled)
    .sort((a, b) => (a.order ?? 99) - (b.order ?? 99));
  const byMethod = new Map(cfg.methods.map((m) => [m.method, m]));

  return {
    configured: true,
    defaultMethod: cfg.defaultMethod,
    enabledMethods,
    fallback: cfg.fallback,
    sessionPolicy: cfg.sessionPolicy,
    isEnabled(method) {
      return enabledMethods.some((m) => m.method === method);
    },
    get(method) {
      return byMethod.get(method);
    },
    otpChannels() {
      return byMethod.get('otp')?.options?.channels ?? [];
    },
    pinLength() {
      return byMethod.get('pin')?.options?.pinLength ?? null;
    },
  };
}