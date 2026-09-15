/**
 * loginFlow — Config-driven login/onboarding journey resolution.
 *
 * Which auth journey an operator runs is an admin decision (manifest `auth`
 * + `auth.login`): in-app OTP steps, or an external web flow (language
 * selection → operator web site OTP → deep-link return → exchange the code
 * with the auth service). The app only executes what is configured; when auth
 * is unconfigured it reports that instead of inventing a journey (v6 #1).
 */

import {
  AuthMethodConfig,
  ManifestAuthConfig,
  ManifestLoginFlow,
} from '../manifest/types';

export interface LoginPlan {
  /** Whether the tenant auth strategy is present in the manifest. */
  configured: boolean;
  flow: ManifestLoginFlow | null;
  external?: ManifestLoginFlow['external'];
  enabledMethods: AuthMethodConfig[];
  defaultMethod: string | null;
}

export function resolveLoginPlan(manifest: unknown): LoginPlan {
  const auth = (manifest as { auth?: ManifestAuthConfig } | undefined)?.auth;
  const flow = auth?.login ?? null;
  return {
    configured: Boolean(auth),
    flow,
    external: flow?.external,
    enabledMethods: (auth?.methods ?? []).filter((m) => m.enabled),
    defaultMethod: auth?.defaultMethod ?? null,
  };
}

export type ExternalAuthReturn = { code: string } | { error: string };

/**
 * Parse a deep-link URL as the operator's external-auth return. Matches only
 * when the URL uses the configured return scheme (optionally path) and carries
 * the configured code/error params — anything else is a normal app route.
 */
export function parseExternalAuthReturn(
  url: string,
  flow: ManifestLoginFlow | null
): ExternalAuthReturn | null {
  if (!flow?.external) return null;
  const { returnScheme, returnPath, codeParam = 'code', errorParam = 'error' } =
    flow.external;

  if (!url.startsWith(`${returnScheme}://`)) return null;

  const withoutScheme = url.slice(`${returnScheme}://`.length);
  const [pathPart, queryPart] = withoutScheme.split('?');
  const normalizePath = (p: string): string => p.replace(/^\/+/, '').replace(/\/+$/, '');
  if (returnPath && normalizePath(pathPart) !== normalizePath(returnPath)) return null;

  const params = new Map<string, string>();
  if (queryPart) {
    queryPart.split('&').forEach((pair) => {
      const eq = pair.indexOf('=');
      if (eq === -1) return;
      const key = decodeURIComponent(pair.slice(0, eq));
      const value = decodeURIComponent(pair.slice(eq + 1));
      params.set(key, value);
    });
  }

  const error = params.get(errorParam);
  if (error) return { error };
  const code = params.get(codeParam);
  if (code) return { code };
  return null;
}