/**
 * AppVersion — Single source of truth for the app version.
 * Reads from package.json at build time (via Metro/babel plugin or env var).
 * No hardcoded values in source.
 */

// Injected at build time by metro.config.js / babel plugin / env
// Falls back to '0.0.0-dev' in development if not injected
declare const __APP_VERSION__: string | undefined;
export const APP_VERSION =
  typeof __APP_VERSION__ === 'string' && __APP_VERSION__ ? __APP_VERSION__ : '0.0.0-dev';

// Also export parsed parts for semantic version comparisons
export const APP_VERSION_PARSED = APP_VERSION.split('.').map(Number);

// Minimum version check utility
export function satisfiesMinVersion(version: string, minVersion: string): boolean {
  const vParts = version.split('.').map(Number);
  const mParts = minVersion.split('.').map(Number);

  for (let i = 0; i < Math.max(vParts.length, mParts.length); i++) {
    const v = vParts[i] || 0;
    const m = mParts[i] || 0;
    if (v > m) return true;
    if (v < m) return false;
  }
  return true;
}

export function isAtLeast(major: number, minor: number, patch: number): boolean {
  const [vMajor, vMinor, vPatch] = APP_VERSION_PARSED;
  if (vMajor > major) return true;
  if (vMajor < major) return false;
  if (vMinor > minor) return true;
  if (vMinor < minor) return false;
  return vPatch >= patch;
}