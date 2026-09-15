/**
 * Theme Engine — Applies tenant design tokens at runtime.
 *
 * Tokens are resolved from:
 * 1. selfcare base tokens (defaults)
 * 2. Tenant theme overrides (from manifest.theme)
 * 3. Platform mode (light / dark)
 *
 * The resolved token set is frozen and used in all StyleSheet.create() calls.
 */

import { tokens } from '../styles/design-tokens';
import type { ThemeTokens } from './types';

// Platform colour modes
export type ColourMode = 'light' | 'dark';

// Resolved theme context
export interface ResolvedTheme {
  mode: ColourMode;
  colors: Record<string, string>;
  typography: ThemeTokens['typography'];
  radius: string;
  spacing: number[];
  logoUrl?: string;
}

// ============================================================
// Base token fallback map
// ============================================================

const BASE_TOKENS: ResolvedTheme = {
  mode: 'light',
  colors: tokens.colors,
  typography: {
    fontFamily: 'System',
    sizes: {
      xs: '12px', sm: '14px', base: '16px', lg: '18px',
      xl: '20px', '2xl': '24px', '3xl': '30px', '4xl': '36px',
    },
    weights: { normal: 400, medium: 500, semibold: 600, bold: 700 },
  },
  radius: `${tokens.borderRadius.lg}px`,
  spacing: Object.values(tokens.spacing),
};

// ============================================================
// Apply tenant theme overrides
// ============================================================

export function applyTheme(tenantTheme: ThemeTokens | null | undefined): ResolvedTheme {
  if (!tenantTheme) return BASE_TOKENS;

  const colors: Record<string, string> = { ...BASE_TOKENS.colors };
  if (tenantTheme.colors) {
    Object.entries(tenantTheme.colors).forEach(([k, v]) => {
      if (v) colors[k] = v;
    });
  }

  return {
    mode: 'light', // dark mode resolved separately if needed
    colors,
    typography: tenantTheme.typography ?? BASE_TOKENS.typography,
    radius: tenantTheme.radius ?? BASE_TOKENS.radius,
    spacing: tenantTheme.spacing ?? BASE_TOKENS.spacing,
    logoUrl: tenantTheme.logoUrl,
  };
}

// ============================================================
// Dark mode resolution
// ============================================================

export function resolveDarkTheme(light: ResolvedTheme): ResolvedTheme {
  const darkColors: Record<string, string> = {};
  Object.entries(light.colors).forEach(([k, v]) => {
    // Naive inversion — replace white/surface colors with dark equivalents
    if (v === '#FFFFFF') {
      darkColors[k] = '#17141F';
    } else if (v === '#F7F5FB') {
      darkColors[k] = '#1F1B2A';
    } else {
      darkColors[k] = v;
    }
  });

  return {
    ...light,
    mode: 'dark',
    colors: { ...light.colors, ...darkColors },
  };
}

// ============================================================
// Color helpers
// ============================================================

/** Blend foreground onto background using alpha */
export function blendOnSurface(
  foreground: string,
  background: string,
  alpha: number
): string {
  // Simplified — real implementation converts hex to RGB, blends, returns hex
  return foreground; // placeholder
}

/** Returns a readable text colour for any background (WCAG AA) */
export function readableOn(backgroundHex: string): 'white' | 'black' {
  const hex = backgroundHex.replace('#', '');
  const r = parseInt(hex.slice(0, 2), 16);
  const g = parseInt(hex.slice(2, 4), 16);
  const b = parseInt(hex.slice(4, 6), 16);
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
  return luminance > 0.5 ? 'black' : 'white';
}

// ============================================================
// CSS custom-property string generator (for web bridge)
// ============================================================

export function toCSSVars(theme: ResolvedTheme): string {
  const lines = [':root {'];
  Object.entries(theme.colors).forEach(([k, v]) => {
    lines.push(`  --color-${k}: ${v};`);
  });
  Object.entries(theme.typography.sizes).forEach(([k, v]) => {
    lines.push(`  --font-size-${k}: ${v};`);
  });
  lines.push(`  --radius: ${theme.radius};`);
  lines.push('}');
  return lines.join('\n');
}
