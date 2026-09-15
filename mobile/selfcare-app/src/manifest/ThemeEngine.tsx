/**
 * selfcare kernel — Theme Engine.
 *
 * Resolves the tenant's compiled manifest theme tokens (ManifestTheme) over the
 * neutral selfcare base token set and exposes them through React context so every
 * widget/screen becomes token-driven (colors, typography, layout metrics).
 *
 * v6 rule (nothing hardcoded): colours, font sizes, header/footer/tab-bar
 * metrics and the light/dark policy are authored in Selfcare Studio (admin
 * portal), stored in Mongo and delivered in the compiled manifest. The base
 * token map below is ONLY the neutral fallback for an unpublished theme — it can
 * never brand a tenant.
 */

import React, { createContext, useContext, useMemo, ReactNode } from 'react';
import { tokens } from '../styles/design-tokens';
import { ManifestTheme } from './types';

export type ColourMode = 'light' | 'dark';

export interface ElevationToken {
  shadowColor: string;
  shadowOffset: { width: number; height: number };
  shadowOpacity: number;
  shadowRadius: number;
  elevation: number;
}

export interface ResolvedTheme {
  mode: ColourMode;
  /** Flat color token map (primary500, surface, textPrimary, textOnPrimary, ...). */
  colors: Record<string, string>;
  typography: {
    fontFamily: string;
    sizes: Record<string, string>;
    weights: Record<string, number>;
  };
  layout: {
    headerHeight: number;
    footerHeight: number;
    tabBarHeight: number;
    sectionGap: number;
    pagePadding: number;
    screenMargin: number;
    cardPadding: number;
    minTouchTarget: number;
    radius: number;
    hairlinePx: number;
    bubbleMaxWidthPct: number;
  };
  radius: string;
  /** Spacing scale keyed by BOTH semantic name ("sm"|"md"|"lg"|...) AND 1-based index (1|2|3|...). */
  spacing: Record<string, number>;
  /** Border radius scale keyed by name ("sm"|"md"|"lg"|"xl"|"full"|...). */
  borderRadius: Record<string, number>;
  /** Elevation (shadow) scale keyed by name ("none"|"sm"|"md"|"lg"); partial keys allowed. */
  elevation: Record<string, Partial<ElevationToken>>;
  /** Button design tokens per variant ("primary"|"secondary"|"outline"|...) — from the manifest buttons block. */
  buttons?: Record<string, Record<string, unknown>>;
  logoUrl?: string;
}

const BASE_COLORS: Record<string, string> = { ...tokens.colors };

const BASE_TYPOGRAPHY: ResolvedTheme['typography'] = {
  fontFamily: 'System',
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

const BASE_SPACING: number[] = Object.values(tokens.spacing);

const BASE_SPACING_KEYS = ['xs', 'sm', 'md', 'lg', 'xl', '2xl', '3xl'] as const;

/** Build the spacing scale: semantic names + 1-based numeric aliases. */
function resolveSpacing(themeSpacing?: number[]): Record<string, number> {
  const values = themeSpacing?.length ? themeSpacing : BASE_SPACING;
  const rec: Record<string, number> = { 0: 0 };
  BASE_SPACING_KEYS.forEach((key, i) => {
    const v = values[i] ?? BASE_SPACING[i];
    rec[key] = v;
    rec[i + 1] = v;
  });
  return rec;
}

const BASE_BORDER_RADIUS: Record<string, number> = { ...tokens.borderRadius };

const BASE_ELEVATION: Record<string, ElevationToken> = {
  ...(tokens.elevation as unknown as Record<string, ElevationToken>),
};

const BASE_LAYOUT: ResolvedTheme['layout'] = { ...tokens.layout };

export const BASE_THEME: ResolvedTheme = {
  mode: 'light',
  colors: BASE_COLORS,
  typography: BASE_TYPOGRAPHY,
  layout: BASE_LAYOUT,
  radius: `${tokens.borderRadius.lg}px`,
  spacing: resolveSpacing(undefined),
  borderRadius: BASE_BORDER_RADIUS,
  elevation: BASE_ELEVATION,
};

/** Deep-merge style-shape override layers (manifest theme over base). */
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

function mergeLayout(
  base: ResolvedTheme['layout'],
  override?: Partial<ResolvedTheme['layout']>
): ResolvedTheme['layout'] {
  return { ...base, ...(override ?? {}) };
}

/**
 * Resolve the compiled manifest theme tokens for the requested colour mode.
 *
 * Mode policy (admin-authored): if the theme declares `mode: "light" | "dark"`
 * that policy wins; `"system"` (or absent) follows the runtime mode the app
 * passes in (typically the OS colour scheme). The manifest's optional `dark`
 * override layer is merged over the light tokens in dark mode.
 */
export function resolveTheme(
  theme: ManifestTheme | null | undefined,
  mode: ColourMode = 'light'
): ResolvedTheme {
  const effective: ColourMode =
    theme?.mode === 'light' || theme?.mode === 'dark' ? theme.mode : mode;

  if (!theme) {
    return effective === 'dark' ? { ...BASE_THEME, mode: 'dark' } : BASE_THEME;
  }

  const base: ResolvedTheme = {
    mode: effective,
    colors: mergeLayer(BASE_THEME.colors, theme.colors),
    typography: {
      fontFamily: theme.typography?.fontFamily ?? BASE_THEME.typography.fontFamily,
      sizes: { ...BASE_THEME.typography.sizes, ...(theme.typography?.sizes ?? {}) },
      weights: { ...BASE_THEME.typography.weights, ...(theme.typography?.weights ?? {}) },
    },
    layout: mergeLayout(BASE_LAYOUT, theme.layout),
    radius: theme.radius ?? BASE_THEME.radius,
    spacing: resolveSpacing(theme.spacing),
    borderRadius: { ...BASE_THEME.borderRadius, ...(theme.borderRadius ?? {}) },
    elevation: {
      ...BASE_THEME.elevation,
      ...(theme.elevation ?? {}),
    } as Record<string, Partial<ElevationToken>>,
    buttons: theme.buttons,
    logoUrl: theme.logoUrl,
  };

  if (effective === 'dark' && theme.dark) {
    return {
      ...base,
      mode: 'dark',
      colors: mergeLayer(base.colors, theme.dark.colors),
      typography: theme.dark.typography
        ? {
            fontFamily: theme.dark.typography.fontFamily ?? base.typography.fontFamily,
            sizes: { ...base.typography.sizes, ...(theme.dark.typography.sizes ?? {}) },
            weights: { ...base.typography.weights, ...(theme.dark.typography.weights ?? {}) },
          }
        : base.typography,
      layout: mergeLayout(base.layout, theme.dark.layout),
      radius: theme.dark.radius ?? base.radius,
      spacing: resolveSpacing(theme.dark.spacing),
      borderRadius: { ...base.borderRadius, ...(theme.dark.borderRadius ?? {}) },
      elevation: {
        ...base.elevation,
        ...(theme.dark.elevation ?? {}),
      } as Record<string, Partial<ElevationToken>>,
    };
  }

  return base;
}

/** Resolve a button design token (e.g. buttons.primary.bg) to its string value. */
export function buttonToken(theme: ResolvedTheme, variant: string, key: string): string | undefined {
  const value = theme.buttons?.[variant]?.[key];
  return typeof value === 'string' ? value : undefined;
}

/** Resolve a button gradient palette (buttons.primary.gradient) to string stops. */
export function buttonGradient(theme: ResolvedTheme, variant: string): string[] | undefined {
  const value = theme.buttons?.[variant]?.gradient;
  if (Array.isArray(value)) {
    const stops = value.filter((v): v is string => typeof v === 'string');
    return stops.length ? stops : undefined;
  }
  return undefined;
}

/** Resolve a typography size token to a numeric px value for React Native. */
export function fontSizePx(theme: ResolvedTheme, key: string): number {
  const raw = theme.typography.sizes[key] ?? theme.typography.sizes.base;
  const parsed = parseInt(raw.replace('px', ''), 10);
  return Number.isFinite(parsed) ? parsed : 16;
}

/** React Native font-weight literals (TextStyle.fontWeight). */
export type FontWeightLiteral =
  | '100' | '200' | '300' | '400' | '500' | '600' | '700' | '800' | '900'
  | 'normal' | 'bold';

/** Resolve a typography weight token to the font-weight literal. */
export function fontWeight(
  theme: ResolvedTheme,
  key: keyof ResolvedTheme['typography']['weights']
): FontWeightLiteral {
  const weight = theme.typography.weights[key] ?? theme.typography.weights.normal;
  return String(weight) as FontWeightLiteral;
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

/** CSS custom-property string generator for the web bridge / embedded web. */
export function toCssVars(theme: ResolvedTheme): string {
  const lines = [':root {'];
  Object.entries(theme.colors).forEach(([k, v]) => {
    lines.push(`  --color-${k}: ${v};`);
  });
  Object.entries(theme.typography.sizes).forEach(([k, v]) => {
    lines.push(`  --font-size-${k}: ${v};`);
  });
  Object.entries(theme.layout).forEach(([k, v]) => {
    lines.push(`  --layout-${k}: ${v}px;`);
  });
  lines.push(`  --radius: ${theme.radius};`);
  lines.push(`  --color-scheme: ${theme.mode};`);
  lines.push('}');
  return lines.join('\n');
}

// ============================================================
// React context
// ============================================================

const ThemeContext = createContext<ResolvedTheme>(BASE_THEME);

export interface ThemeProviderProps {
  theme?: ManifestTheme | null;
  mode?: ColourMode;
  children: ReactNode;
}

/** Provides resolved theme tokens to any component in the tree. */
export function ThemeProvider({ theme, mode = 'light', children }: ThemeProviderProps): React.JSX.Element {
  const resolved = useMemo(() => resolveTheme(theme, mode), [theme, mode]);
  return <ThemeContext.Provider value={resolved}>{children}</ThemeContext.Provider>;
}

/** Returns the resolved theme tokens for the current provider. */
export function useTheme(): ResolvedTheme {
  return useContext(ThemeContext);
}

/** Convenience accessor for the flat color map. */
export function useThemeColors(): Record<string, string> {
  return useContext(ThemeContext).colors;
}

/** Typography tokens (font family + sizes + weights). */
export function useThemeTypography(): ResolvedTheme['typography'] {
  return useContext(ThemeContext).typography;
}

/** Chrome layout metrics (header/footer/tab-bar heights, gaps, padding). */
export function useThemeLayout(): ResolvedTheme['layout'] {
  return useContext(ThemeContext).layout;
}