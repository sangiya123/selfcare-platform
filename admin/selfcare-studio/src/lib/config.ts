/**
 * Centralized environment-driven configuration for Selfcare Studio.
 *
 * All values come from Vite env vars (set per mode: dev/stg/reg/prod).
 * NO hardcoded values anywhere in the app.
 *
 * Env vars (defined in .env.<mode>):
 *   VITE_APP_ENV            - dev | stg | reg | prod
 *   VITE_API_GATEWAY_URL    - API gateway base URL
 *   VITE_PUBLIC_API_BASE_URL - public-facing API URL
 *   VITE_ADMIN_PORTAL_URL   - this admin portal URL
 *   VITE_OBSERVABILITY_URL  - Grafana URL
 */

export type AppEnv = 'dev' | 'stg' | 'reg' | 'prod';

interface AppConfig {
  env: AppEnv;
  isProduction: boolean;
  apiGatewayUrl: string;
  publicApiBaseUrl: string;
  adminPortalUrl: string;
  observabilityUrl: string;
}

function getEnvVar(key: string, fallback?: string): string {
  const value = import.meta.env[key] as string | undefined;
  if (value === undefined || value === '') {
    if (fallback !== undefined) return fallback;
    throw new Error(`Missing required env var: ${key}`);
  }
  return value;
}

function resolveEnv(): AppEnv {
  const v = getEnvVar('VITE_APP_ENV', 'dev').toLowerCase();
  if (v === 'development') return 'dev';
  if (v === 'staging') return 'stg';
  if (v === 'production') return 'prod';
  if (['dev', 'stg', 'reg', 'prod'].includes(v)) return v as AppEnv;
  return 'dev';
}

export const config: AppConfig = {
  env: resolveEnv(),
  isProduction: resolveEnv() === 'prod',
  apiGatewayUrl: getEnvVar('VITE_API_GATEWAY_URL', 'http://localhost:8080'),
  publicApiBaseUrl: getEnvVar('VITE_PUBLIC_API_BASE_URL', 'http://localhost:8080'),
  adminPortalUrl: getEnvVar('VITE_ADMIN_PORTAL_URL', 'http://localhost:3000'),
  observabilityUrl: getEnvVar('VITE_OBSERVABILITY_URL', 'http://localhost:3001'),
};

// Friendly log in dev only
if (config.env === 'dev') {
  // eslint-disable-next-line no-console
  console.info(`[selfcare-studio] env=${config.env} api=${config.apiGatewayUrl}`);
}
