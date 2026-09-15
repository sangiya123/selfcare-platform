/**
 * serviceEndpoints — resolve HTTP endpoint paths from the compiled manifest.
 *
 * Every hardcoded `/api/v1/...` path in the app was moved here as a fallback
 * DEFAULT only. The real values come from the manifest `services` section,
 * which is authored in the admin portal (MySQL/Mongo) and compiled + signed at
 * publish time. Endpoint changes are DB-level changes — never app releases
 * (v6 rule #1). `{param}` placeholders in configured paths are substituted
 * with the supplied values.
 */
import type { ServiceEndpoints } from '../manifest/types';

export type ServiceDomain = keyof ServiceEndpoints;

function manifestServices(): ServiceEndpoints | undefined {
  const sdk = (globalThis as { __SELFCARE_SDK__?: { getManifest?: () => unknown } })
    .__SELFCARE_SDK__;
  const manifest = sdk?.getManifest?.() as unknown as { services?: ServiceEndpoints } | null;
  return manifest?.services;
}

/**
 * Resolve a service endpoint path with `{param}` substitution.
 *
 * @param domain   services group (billing | usage | payments | auth | ai | ...)
 * @param key      endpoint key inside the group (e.g. `bills`)
 * @param fallback default v1 path used only when config is absent
 * @param params   values to substitute into `{name}` placeholders
 */
type ServiceMap = { [domain in ServiceDomain]: { [key: string]: string | undefined } };

export function servicePath(
  domain: ServiceDomain,
  key: string,
  fallback: string,
  params?: Record<string, string | number>
): string {
  const raw = (manifestServices() as ServiceMap | undefined)?.[domain]?.[key];
  const path = raw && raw.trim().length ? raw : fallback;
  if (!params) return path;
  return Object.entries(params).reduce(
    (acc, [name, value]) => acc.replaceAll(`{${name}}`, String(value)),
    path
  );
}