/**
 * Design Tokens — Centralized design system.
 *
 * Tokens are loaded from the tenant theme config and applied via StyleSheet.
 * Default tokens are OMOBIO base palette (deep purple/violet).
 * Operators can override via theme config.
 */

export const tokens = {
  // Colors
  colors: {
    primary900: '#1B0B45',
    primary700: '#3D126E',
    primary500: '#6D28D9',
    primary300: '#A78BFA',
    accent500: '#C026D3',
    accentGlow: '#E879F9',
    surface: '#FFFFFF',
    surfaceSubtle: '#F7F5FB',
    textPrimary: '#17141F',
    textSecondary: '#6B6475',
    success: '#10B981',
    warning: '#F59E0B',
    error: '#EF4444',
    info: '#3B82F6',
    border: '#E5E7EB',
    overlay: 'rgba(0, 0, 0, 0.5)',
  },

  // Typography
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

  // Spacing
  spacing: {
    xs: 4,
    sm: 8,
    md: 12,
    lg: 16,
    xl: 24,
    '2xl': 32,
    '3xl': 48,
  },

  // Border radius
  borderRadius: {
    sm: 4,
    md: 8,
    lg: 12,
    xl: 16,
    '2xl': 24,
    full: 9999,
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