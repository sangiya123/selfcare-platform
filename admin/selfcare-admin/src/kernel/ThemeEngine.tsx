/**
 * selfcare kernel (web) — Theme Engine.
 *
 * Resolves the compiled manifest theme tokens over the selfcare base token set and
 * exposes them as CSS custom properties on the preview container so widgets can
 * be token-driven. Mirrors the mobile kernel (`mobile/selfcare-app/src/manifest/ThemeEngine.tsx`).
 *
 * Rule (v6): no brand decision lives in the app. All colors/typography/radius
 * are authored in the admin portal (selfcare Studio theme/branding config) and
 * arrive here through the compiled manifest `theme` section. The fallback token
 * map below is intentionally NEUTRAL (monochrome) and is only used as a
 * bootstrap default until the manifest theme is available.
 */

import React, { createContext, useContext, useMemo, ReactNode, CSSProperties } from 'react';
import { ManifestTheme } from './types';

export type ColourMode = 'light' | 'dark';

export interface ResolvedTheme {
  mode: ColourMode;
  colors: Record<string, string>;
  typography: {
    fontFamily: string;
    sizes: Record<string, string>;
    weights: Record<string, number>;
  };
  radius: string;
  spacing: number[];
  logoUrl?: string;
}

const BASE_COLORS: Record<string, string> = {
  primary: '#3F3F46',
  primary500: '#3F3F46',
  primary600: '#27272A',
  surface: '#FFFFFF',
  surfaceSubtle: '#F4F4F5',
  surfaceHover: '#E4E4E7',
  textPrimary: '#18181B',
  textSecondary: '#71717A',
  textOnPrimary: '#FFFFFF',
  border: '#E4E4E7',
  error: '#B91C1C',
  warning: '#B45309',
  success: '#15803D',
};

const BASE_TYPOGRAPHY: ResolvedTheme['typography'] = {
  fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
  sizes: {
    xs: '12px',
    sm: '14px',
    base: '16px',
    lg: '18px',
    xl: '20px',
    '2xl': '24px',
    '3xl': '30px',
    '4xl': '36px',
  },
  weights: { normal: 400, medium: 500, semibold: 600, bold: 700 },
};

const BASE_SPACING: number[] = [4, 8, 12, 16, 24, 32, 48];

export const BASE_THEME: ResolvedTheme = {
  mode: 'light',
  colors: BASE_COLORS,
  typography: BASE_TYPOGRAPHY,
  radius: '12px',
  spacing: BASE_SPACING,
};

function mergeLayer(
  base: Record<string, string>,
  override?: Record<string, string>
): Record<string, string> {
  const merged: Record<string, string> = { ...base };
  if (override) {
    Object.entries(override).forEach(([key, value]) => {
      if (value) merged[key] = value;
    });
  }
  return merged;
}

/** Resolve the compiled manifest theme tokens for the requested colour mode. */
export function resolveTheme(
  theme: ManifestTheme | null | undefined,
  mode: ColourMode = 'light'
): ResolvedTheme {
  if (!theme) {
    return mode === 'dark' ? { ...BASE_THEME, mode } : BASE_THEME;
  }

  const base: ResolvedTheme = {
    mode,
    colors: mergeLayer(BASE_THEME.colors, theme.colors),
    typography: {
      fontFamily: theme.typography?.fontFamily ?? BASE_THEME.typography.fontFamily,
      sizes: { ...BASE_THEME.typography.sizes, ...(theme.typography?.sizes ?? {}) },
      weights: { ...BASE_THEME.typography.weights, ...(theme.typography?.weights ?? {}) },
    },
    radius: theme.radius ?? BASE_THEME.radius,
    spacing: theme.spacing?.length ? theme.spacing : BASE_THEME.spacing,
    logoUrl: theme.logoUrl,
  };

  if (mode === 'dark' && theme.dark) {
    return {
      ...base,
      mode,
      colors: mergeLayer(base.colors, theme.dark.colors),
      typography: {
        ...base.typography,
        ...(theme.dark.typography ? { ...base.typography, ...theme.dark.typography } : {}),
      },
      radius: theme.dark.radius ?? base.radius,
      spacing: theme.dark.spacing?.length ? theme.dark.spacing : base.spacing,
    };
  }

  return base;
}

/** Emit a readable foreground ('white' | 'black') for any background hex. */
export function readableOn(backgroundHex: string): 'white' | 'black' {
  const hex = backgroundHex.replace('#', '');
  if (!/^[0-9a-fA-F]{6}$/.test(hex)) return 'white';
  const r = parseInt(hex.slice(0, 2), 16);
  const g = parseInt(hex.slice(2, 4), 16);
  const b = parseInt(hex.slice(4, 6), 16);
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
  return luminance > 0.5 ? 'black' : 'white';
}

/** CSS custom-property string for direct stylesheet injection (mobile parity). */
export function toCssVars(theme: ResolvedTheme): string {
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

/** CSS custom-property map for inline style application on the preview host. */
export function toCssVarsMap(theme: ResolvedTheme): Record<string, string> {
  const map: Record<string, string> = {};
  Object.entries(theme.colors).forEach(([k, v]) => {
    map[`--color-${k}`] = v;
  });
  Object.entries(theme.typography.sizes).forEach(([k, v]) => {
    map[`--font-size-${k}`] = v;
  });
  map['--radius'] = theme.radius;
  return map;
}

// ============================================================
// React context
// ============================================================

const ThemeContext = createContext<ResolvedTheme>(BASE_THEME);

export interface ThemeProviderProps {
  theme?: ManifestTheme | null;
  mode?: ColourMode;
  /** When set, CSS variables are applied to this container element. */
  containerId?: string;
  children: ReactNode;
}

/** Provides resolved theme tokens and CSS custom properties to the tree. */
export function ThemeProvider({
  theme,
  mode = 'light',
  containerId = 'selfcare-preview-root',
  children,
}: ThemeProviderProps): JSX.Element {
  const resolved = useMemo(() => resolveTheme(theme, mode), [theme, mode]);
  const vars = useMemo(() => toCssVarsMap(resolved), [resolved]);

  return (
    <ThemeContext.Provider value={resolved}>
      <div
        id={containerId}
        style={
          {
            color: resolved.colors.textPrimary,
            backgroundColor: resolved.colors.surface,
            fontFamily: resolved.typography.fontFamily,
            ...vars,
          } as CSSProperties
        }
      >
        {children}
      </div>
    </ThemeContext.Provider>
  );
}

/** Returns the resolved theme tokens for the current provider. */
export function useTheme(): ResolvedTheme {
  return useContext(ThemeContext);
}

/** Convenience accessor for the flat color map. */
export function useThemeColors(): Record<string, string> {
  return useContext(ThemeContext).colors;
}