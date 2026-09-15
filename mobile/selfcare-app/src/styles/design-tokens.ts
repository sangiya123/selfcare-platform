/**
 * Design Tokens — Neutral fallback scale used only when no tenant theme has
 * been published.
 *
 * v6 rule: colors, typography, layout metrics and light/dark are NEVER
 * hardcoded for a tenant. The full theme (brand colors, font sizes, header /
 * footer / tab-bar metrics, dark mode) is authored in Selfcare Studio (admin
 * portal), stored in Mongo and delivered to the app inside the compiled,
 * signed Experience Manifest (ManifestTheme). Widgets read resolved tokens
 * through `useTheme()` / `useThemeColors()` — never these raw constants.
 *
 * The palette below is intentionally neutral (near-black / grey / white) so a
 * missing theme can never silently brand the product.
 */

export const tokens = {
  // Colors — neutral fallback scale (no brand decision).
  colors: {
    primary900: '#171717',
    primary700: '#262626',
    primary500: '#404040',
    primary300: '#A3A3A3',
    accent500: '#525252',
    accentGlow: '#A3A3A3',
    surface: '#FFFFFF',
    surfaceSubtle: '#F5F5F5',
    textPrimary: '#171717',
    textSecondary: '#737373',
    textOnPrimary: '#FFFFFF',
    success: '#16A34A',
    warning: '#D97706',
    error: '#DC2626',
    info: '#2563EB',
    border: '#E5E5E5',
    shadow: '#000000',
    overlay: 'rgba(0, 0, 0, 0.5)',
  },

  // Typography — neutral fallback scale (theme overrides from manifest).
  fontSize: {
    xs: 12,
    sm: 14,
    base: 16,
    lg: 18,
    xl: 20,
    '2xl': 24,
    '3xl': 30,
    '4xl': 36,
  },

  fontWeight: {
    normal: '400' as const,
    medium: '500' as const,
    semibold: '600' as const,
    bold: '700' as const,
  },

  // Spacing — neutral fallback scale.
  spacing: {
    xs: 4,
    sm: 8,
    md: 12,
    lg: 16,
    xl: 24,
    '2xl': 32,
    '3xl': 48,
  },

  // Border radius — neutral fallback scale.
  borderRadius: {
    sm: 4,
    md: 8,
    lg: 12,
    xl: 16,
    '2xl': 24,
    full: 9999,
  },

  // Chrome layout metrics — neutral fallback (theme.layout from manifest wins).
  layout: {
    headerHeight: 56,
    footerHeight: 60,
    tabBarHeight: 60,
    sectionGap: 16,
    pagePadding: 16,
    screenMargin: 12,
    cardPadding: 16,
    minTouchTarget: 44,
    radius: 12,
    hairlinePx: 1,
    bubbleMaxWidthPct: 85,
  },

  // Elevation
  elevation: {
    none: {
      shadowColor: 'transparent',
      shadowOffset: { width: 0, height: 0 },
      shadowOpacity: 0,
      shadowRadius: 0,
      elevation: 0,
    },
    sm: {
      shadowColor: '#000',
      shadowOffset: { width: 0, height: 1 },
      shadowOpacity: 0.05,
      shadowRadius: 2,
      elevation: 1,
    },
    md: {
      shadowColor: '#000',
      shadowOffset: { width: 0, height: 4 },
      shadowOpacity: 0.07,
      shadowRadius: 6,
      elevation: 3,
    },
    lg: {
      shadowColor: '#000',
      shadowOffset: { width: 0, height: 10 },
      shadowOpacity: 0.1,
      shadowRadius: 15,
      elevation: 6,
    },
  },

  // Touch target (per WCAG 2.2 AA)
  minTouchTarget: 44,

  // Motion
  motion: {
    fast: 150,
    normal: 200,
    slow: 300,
  },
} as const;

export type DesignTokens = typeof tokens;