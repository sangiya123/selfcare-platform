import { resolveTheme, readableOn, toCssVars, BASE_THEME, ThemeProvider, useThemeColors } from '../../src/manifest/ThemeEngine';
import { ManifestTheme } from '../../src/manifest/types';
import React from 'react';
import { Text } from 'react-native';
import { render } from '@testing-library/react-native';

const THEME: ManifestTheme = {
  name: 'dialog-lk',
  colors: { primary500: '#D40000', surface: '#FFFFFF' },
  typography: { fontFamily: 'Roboto', sizes: { base: '18px' } },
  radius: '20px',
  spacing: [4, 8, 16, 24],
  logoUrl: 'https://cdn.example.test/dialog-lk/logo.png',
  dark: { colors: { surface: '#121212', primary500: '#FF4D4D' } },
};

describe('ThemeEngine (mobile kernel)', () => {
  it('returns the base token set for an empty theme', () => {
    const resolved = resolveTheme(null);
    expect(resolved.mode).toBe('light');
    expect(resolved.colors.primary500).toBe(BASE_THEME.colors.primary500);
    expect(resolved.typography.fontFamily).toBe('System');
  });

  it('merges manifest tokens over the base set', () => {
    const resolved = resolveTheme(THEME);
    expect(resolved.colors.primary500).toBe('#D40000');
    expect(resolved.typography.fontFamily).toBe('Roboto');
    expect(resolved.typography.sizes.base).toBe('18px');
    expect(resolved.radius).toBe('20px');
    expect(resolved.spacing).toMatchObject({ sm: 8, md: 16, lg: 24, '1': 4, '3': 16, '4': 24 });
    expect(resolved.logoUrl).toContain('logo.png');
    // Base tokens that were not overridden survive.
    expect(resolved.colors.textPrimary).toBeDefined();
  });

  it('drops to the dark layer when mode is dark', () => {
    const resolved = resolveTheme(THEME, 'dark');
    expect(resolved.mode).toBe('dark');
    expect(resolved.colors.surface).toBe('#121212');
    expect(resolved.colors.primary500).toBe('#FF4D4D');
  });

  it('returns base theme for dark mode when theme has no dark layer', () => {
    const resolved = resolveTheme({ name: 'light-only', colors: { surface: '#FFF' } }, 'dark');
    expect(resolved.mode).toBe('dark');
    expect(resolved.colors.surface).toBe('#FFF');
  });

  it('computes readable foreground colours', () => {
    expect(readableOn('#000000')).toBe('white');
    expect(readableOn('#FFFFFF')).toBe('black');
    expect(readableOn('#D40000')).toBe('white');
    expect(readableOn('nonsense')).toBe('white');
  });

  it('emits CSS custom properties for the web bridge', () => {
    const css = toCssVars(resolveTheme(THEME));
    expect(css).toContain('--color-primary500: #D40000;');
    expect(css).toContain('--font-size-base: 18px;');
    expect(css).toContain('--radius: 20px;');
    expect(css).toContain(':root {');
  });

  it('exposes resolved tokens through React context', () => {
    function Consumer(): React.JSX.Element {
      const colors = useThemeColors();
      return <Text>{colors.primary500}</Text>;
    }
    const { getByText } = render(
      <ThemeProvider theme={THEME}>
        <Consumer />
      </ThemeProvider>
    );
    expect(getByText('#D40000')).toBeTruthy();
  });
});