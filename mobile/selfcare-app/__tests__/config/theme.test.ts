/**
 * Theme Engine Tests
 *
 * Tests for theme application and utilities:
 * - applyTheme() with tenant overrides
 * - readableOn() — WCAG AA contrast checker
 * - toCSSVars() — CSS custom-property string generator
 * - resolveDarkTheme() — dark mode color inversion
 * - blendOnSurface() — alpha blending helper
 */

import {
  applyTheme,
  resolveDarkTheme,
  readableOn,
  blendOnSurface,
  toCSSVars,
  type ResolvedTheme,
} from '../../src/config/theme';
import { tokens } from '../../src/styles/design-tokens';
import type { ThemeTokens } from '../../src/config/types';

describe('Theme Engine', () => {
  describe('applyTheme()', () => {
    it('returns base theme when tenantTheme is null', () => {
      const result = applyTheme(null);
      expect(result.mode).toBe('light');
      expect(result.colors).toEqual(tokens.colors);
    });

    it('returns base theme when tenantTheme is undefined', () => {
      const result = applyTheme(undefined);
      expect(result.colors).toEqual(tokens.colors);
    });

    it('applies color overrides from tenant theme', () => {
      const tenantTheme: ThemeTokens = {
        name: 'custom',
        colors: {
          primary500: '#FF0000',
          accent500: '#00FF00',
        } as any,
        typography: {
          fontFamily: 'Roboto',
          sizes: { base: '14px' },
          weights: { normal: 400 },
        },
        radius: '8px',
        spacing: [4, 8, 12, 16],
      };

      const result = applyTheme(tenantTheme);
      expect(result.colors.primary500).toBe('#FF0000');
      expect(result.colors.accent500).toBe('#00FF00');
    });

    it('preserves non-overridden base colors', () => {
      const tenantTheme: ThemeTokens = {
        name: 'custom',
        colors: { primary500: '#FF0000' } as any,
        typography: {
          fontFamily: 'Roboto',
          sizes: { base: '14px' },
          weights: { normal: 400 },
        },
        radius: '8px',
        spacing: [4, 8, 12, 16],
      };

      const result = applyTheme(tenantTheme);
      expect(result.colors.success).toBe(tokens.colors.success);
      expect(result.colors.warning).toBe(tokens.colors.warning);
    });

    it('uses tenant typography when provided', () => {
      const tenantTheme: ThemeTokens = {
        name: 'custom',
        colors: {},
        typography: {
          fontFamily: 'Inter',
          sizes: { sm: '13px', base: '15px' },
          weights: { bold: 800 },
        },
        radius: '10px',
        spacing: [4, 8],
      };

      const result = applyTheme(tenantTheme);
      expect(result.typography.fontFamily).toBe('Inter');
    });

    it('uses tenant radius and spacing when provided', () => {
      const tenantTheme: ThemeTokens = {
        name: 'custom',
        colors: {},
        typography: {
          fontFamily: 'System',
          sizes: {},
          weights: {},
        },
        radius: '20px',
        spacing: [2, 4, 6, 8],
      };

      const result = applyTheme(tenantTheme);
      expect(result.radius).toBe('20px');
      expect(result.spacing).toEqual([2, 4, 6, 8]);
    });

    it('skips falsy color values (null/undefined)', () => {
      const tenantTheme: ThemeTokens = {
        name: 'custom',
        colors: { primary500: null, accent500: undefined, success: '#00FF00' } as any,
        typography: {
          fontFamily: 'System',
          sizes: {},
          weights: {},
        },
        radius: '8px',
        spacing: [4, 8],
      };

      const result = applyTheme(tenantTheme);
      expect(result.colors.primary500).toBe(tokens.colors.primary500);
      expect(result.colors.accent500).toBe(tokens.colors.accent500);
      expect(result.colors.success).toBe('#00FF00');
    });

    it('preserves logoUrl from tenant theme', () => {
      const tenantTheme: ThemeTokens = {
        name: 'custom',
        colors: {},
        typography: {
          fontFamily: 'System',
          sizes: {},
          weights: {},
        },
        radius: '8px',
        spacing: [4, 8],
        logoUrl: 'https://cdn.example.com/logo.png',
      };

      const result = applyTheme(tenantTheme);
      expect(result.logoUrl).toBe('https://cdn.example.com/logo.png');
    });
  });

  describe('readableOn()', () => {
    it('returns "white" for dark backgrounds', () => {
      expect(readableOn('#000000')).toBe('white');
      expect(readableOn('#1B0B45')).toBe('white');
      expect(readableOn('#3D126E')).toBe('white');
      expect(readableOn('#17141F')).toBe('white');
    });

    it('returns "black" for light backgrounds', () => {
      expect(readableOn('#FFFFFF')).toBe('black');
      expect(readableOn('#F7F5FB')).toBe('black');
      expect(readableOn('#E5E7EB')).toBe('black');
    });

    it('returns "black" for mid-light colors', () => {
      expect(readableOn('#CCCCCC')).toBe('black');
      expect(readableOn('#FFFF00')).toBe('black'); // Yellow
    });

    it('strips # prefix from hex', () => {
      expect(readableOn('000000')).toBe('white');
      expect(readableOn('FFFFFF')).toBe('black');
    });

    it('returns "white" for common brand colors', () => {
      expect(readableOn('#6D28D9')).toBe('white'); // Dialog purple
      expect(readableOn('#C026D3')).toBe('white'); // Accent
      expect(readableOn('#10B981')).toBe('white'); // Success green
    });

    it('uses luminance formula (0.299R + 0.587G + 0.114B)', () => {
      // Pure red (luminance ~0.299) -> should be 'black'
      expect(readableOn('#FF0000')).toBe('black');
      // Pure green (luminance ~0.587) -> should be 'white' (just above 0.5)
      // Wait: 0.587 > 0.5, so should be 'black' (per formula)
      // Actually re-check: luminance > 0.5 ? 'black' : 'white'
      // 0.587 > 0.5, so 'black' is correct
      expect(readableOn('#00FF00')).toBe('black');
      // Pure blue (luminance ~0.114) -> 'white'
      expect(readableOn('#0000FF')).toBe('white');
    });
  });

  describe('toCSSVars()', () => {
    it('generates CSS custom properties from theme', () => {
      const theme: ResolvedTheme = {
        mode: 'light',
        colors: {
          primary500: '#6D28D9',
          success: '#10B981',
        },
        typography: {
          fontFamily: 'System',
          sizes: { base: '16px', lg: '18px' },
          weights: { normal: 400 },
        },
        radius: '12px',
        spacing: [4, 8, 12, 16],
      };

      const css = toCSSVars(theme);
      expect(css).toContain(':root {');
      expect(css).toContain('--color-primary500: #6D28D9;');
      expect(css).toContain('--color-success: #10B981;');
      expect(css).toContain('--font-size-base: 16px;');
      expect(css).toContain('--font-size-lg: 18px;');
      expect(css).toContain('--radius: 12px;');
      expect(css).toContain('}');
    });

    it('handles empty colors map', () => {
      const theme: ResolvedTheme = {
        mode: 'light',
        colors: {},
        typography: {
          fontFamily: 'System',
          sizes: {},
          weights: {},
        },
        radius: '8px',
        spacing: [],
      };

      const css = toCSSVars(theme);
      expect(css).toContain(':root {');
      expect(css).toContain('--radius: 8px;');
      expect(css).toContain('}');
    });

    it('produces valid CSS syntax with semicolons', () => {
      const theme: ResolvedTheme = {
        mode: 'light',
        colors: { primary500: '#6D28D9' },
        typography: {
          fontFamily: 'System',
          sizes: { base: '16px' },
          weights: { normal: 400 },
        },
        radius: '12px',
        spacing: [4, 8],
      };

      const css = toCSSVars(theme);
      // Each property line should end with semicolon
      const lines = css.split('\n').filter(l => l.trim().startsWith('--'));
      lines.forEach(line => {
        expect(line).toMatch(/;$/);
      });
    });
  });

  describe('resolveDarkTheme()', () => {
    it('inverts white surfaces to dark', () => {
      const light: ResolvedTheme = {
        mode: 'light',
        colors: { surface: '#FFFFFF', surfaceSubtle: '#F7F5FB' },
        typography: {
          fontFamily: 'System',
          sizes: {},
          weights: {},
        },
        radius: '12px',
        spacing: [4, 8],
      };

      const dark = resolveDarkTheme(light);
      expect(dark.mode).toBe('dark');
      expect(dark.colors.surface).toBe('#17141F');
      expect(dark.colors.surfaceSubtle).toBe('#17141F');
    });

    it('preserves non-surface colors', () => {
      const light: ResolvedTheme = {
        mode: 'light',
        colors: {
          primary500: '#6D28D9',
          success: '#10B981',
          surface: '#FFFFFF',
        },
        typography: {
          fontFamily: 'System',
          sizes: {},
          weights: {},
        },
        radius: '12px',
        spacing: [4, 8],
      };

      const dark = resolveDarkTheme(light);
      expect(dark.colors.primary500).toBe('#6D28D9');
      expect(dark.colors.success).toBe('#10B981');
    });
  });

  describe('blendOnSurface()', () => {
    it('returns foreground color (placeholder implementation)', () => {
      const result = blendOnSurface('#000000', '#FFFFFF', 0.5);
      expect(result).toBe('#000000');
    });
  });

  describe('theme integration', () => {
    it('produces complete theme object for app use', () => {
      const tenantTheme: ThemeTokens = {
        name: 'dialog',
        colors: { primary500: '#FF5722' } as any,
        typography: {
          fontFamily: 'Roboto',
          sizes: { base: '16px' },
          weights: { normal: 400 },
        },
        radius: '12px',
        spacing: [4, 8, 12, 16, 24],
      };

      const result = applyTheme(tenantTheme);

      expect(result).toHaveProperty('mode');
      expect(result).toHaveProperty('colors');
      expect(result).toHaveProperty('typography');
      expect(result).toHaveProperty('radius');
      expect(result).toHaveProperty('spacing');
    });

    it('CSS output is web-bridge compatible', () => {
      const result = applyTheme(null);
      const css = toCSSVars(result);
      // Should be valid CSS when wrapped in <style>
      expect(css.startsWith(':root {')).toBe(true);
      expect(css.endsWith('}')).toBe(true);
    });
  });
});
