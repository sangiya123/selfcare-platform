/**
 * BrandingPage — manage visual identity for a client/tenant.
 *
 * A client's brand is a bundle of:
 *   - Logo (URL or upload)
 *   - Primary color
 *   - Accent color
 *   - Font family
 *   - Design tokens (spacing, radius, elevation)
 *   - Dark/light theme support
 *   - Platform previews
 *   - Component variants
 *
 * Branding is stored as part of the tenant config and merged into the
 * compiled runtime manifest (ADR-004).
 */
import { useState, useEffect } from 'react';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Slider } from '@/components/ui/slider';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import {
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
} from '@/components/ui/select';
import { useActiveTenant } from '@/hooks/useActiveTenant';
import { toast } from 'sonner';
import { api } from '@/lib/api';
import {
  Briefcase,
  Upload,
  RefreshCw,
  Save,
  Eye,
  Sun,
  Moon,
  Smartphone,
  Monitor,
  Tablet,
  AppWindow,
  Box,
} from 'lucide-react';

// ─── Brand Config Types ───────────────────────────────────────────────────────

type ThemeMode = 'light' | 'dark' | 'both';

interface ThemeColors {
  primaryColor: string;
  accentColor: string;
  backgroundColor: string;
  textColor: string;
}

interface DesignTokens {
  baseSpacing: number;       // 4 | 8 | 12 | 16
  borderRadius: number;        // 0 | 4 | 8 | 12 | 16 | 9999
  elevation: 'none' | 'sm' | 'md' | 'lg' | 'xl';
}

interface BrandConfig {
  // Assets
  logoUrl: string;
  faviconUrl: string;
  splashImageUrl: string;
  // Typography
  fontFamily: string;
  // Light theme colors
  light: ThemeColors;
  // Dark theme colors
  dark: ThemeColors;
  // Theme mode
  themeMode: ThemeMode;
  // Design tokens
  tokens: DesignTokens;
}

// ─── Defaults ────────────────────────────────────────────────────────────────

const DEFAULT_LIGHT: ThemeColors = {
  primaryColor: '#6D28D9',
  accentColor: '#F59E0B',
  backgroundColor: '#FFFFFF',
  textColor: '#111827',
};

const DEFAULT_DARK: ThemeColors = {
  primaryColor: '#7C3AED',
  accentColor: '#FBBF24',
  backgroundColor: '#111827',
  textColor: '#F9FAFB',
};

const DEFAULT_TOKENS: DesignTokens = {
  baseSpacing: 8,
  borderRadius: 8,
  elevation: 'md',
};

const DEFAULT_BRAND: BrandConfig = {
  logoUrl: '',
  faviconUrl: '',
  splashImageUrl: '',
  fontFamily: 'Inter',
  light: DEFAULT_LIGHT,
  dark: DEFAULT_DARK,
  themeMode: 'both',
  tokens: DEFAULT_TOKENS,
};

// ─── Spacing & Radius scales ─────────────────────────────────────────────────

const SPACING_STEPS = [4, 8, 12, 16] as const;
const RADIUS_STEPS = [0, 4, 8, 12, 16, 9999] as const;
const ELEVATION_OPTIONS = ['none', 'sm', 'md', 'lg', 'xl'] as const;

// ─── Component ────────────────────────────────────────────────────────────────

export default function BrandingPage() {
  const { activeTenantId, tenants } = useActiveTenant();
  const active = tenants.find((t: any) => t.tenantId === activeTenantId);
  const [brand, setBrand] = useState<BrandConfig>(DEFAULT_BRAND);
  const [saving, setSaving] = useState(false);
  const [previewPlatform, setPreviewPlatform] = useState<'ios' | 'android' | 'huawei' | 'web'>('ios');

  // Load existing theme config when tenant changes
  useEffect(() => {
    if (!activeTenantId) return;
    api.themes
      .list(activeTenantId)
      .then((themes) => {
        if (themes && themes.length > 0) {
          const existing = themes[0];
          setBrand({
            logoUrl: existing.logoUrl ?? '',
            faviconUrl: existing.faviconUrl ?? '',
            splashImageUrl: existing.splashImageUrl ?? '',
            fontFamily: existing.fontFamily ?? 'Inter',
            light: existing.light ?? DEFAULT_LIGHT,
            dark: existing.dark ?? DEFAULT_DARK,
            themeMode: existing.themeMode ?? 'both',
            tokens: existing.tokens ?? DEFAULT_TOKENS,
          });
        }
      })
      .catch(() => {
        // If load fails, keep defaults
      });
  }, [activeTenantId]);

  // ── Setters ────────────────────────────────────────────────────────────────

  const setLight = (key: keyof ThemeColors, value: string) =>
    setBrand((b) => ({ ...b, light: { ...b.light, [key]: value } }));

  const setDark = (key: keyof ThemeColors, value: string) =>
    setBrand((b) => ({ ...b, dark: { ...b.dark, [key]: value } }));

  const setTokens = <K extends keyof DesignTokens>(key: K, value: DesignTokens[K]) =>
    setBrand((b) => ({ ...b, tokens: { ...b.tokens, [key]: value } }));

  const onSave = async () => {
    if (!activeTenantId) return;
    setSaving(true);
    try {
      await api.themes.save({ ...brand, tenantId: activeTenantId });
      toast.success('Branding saved successfully');
    } catch (err: any) {
      toast.error('Failed to save branding: ' + (err.message ?? 'Unknown error'));
    } finally {
      setSaving(false);
    }
  };

  const onReset = () => setBrand(DEFAULT_BRAND);

  // ── Spacing preview ─────────────────────────────────────────────────────────

  const spacingLabels = ['xs', 'sm', 'md', 'lg', 'xl'];
  const spacingMultipliers = [0.5, 1, 1.5, 2, 3];

  // ── Elevation shadow map ────────────────────────────────────────────────────

  const elevationShadows: Record<DesignTokens['elevation'], string> = {
    none: 'none',
    sm: '0 1px 2px 0 rgba(0,0,0,0.05)',
    md: '0 4px 6px -1px rgba(0,0,0,0.1), 0 2px 4px -2px rgba(0,0,0,0.1)',
    lg: '0 10px 15px -3px rgba(0,0,0,0.1), 0 4px 6px -4px rgba(0,0,0,0.1)',
    xl: '0 20px 25px -5px rgba(0,0,0,0.1), 0 8px 10px -6px rgba(0,0,0,0.1)',
  };

  // ── Radius label ────────────────────────────────────────────────────────────

  const radiusLabel = (px: number) =>
    px === 9999 ? 'full (pill)' : `${px}px`;

  // ── Platform frame dimensions ───────────────────────────────────────────────

  const platformFrames: Record<string, { width: number; height: number; label: string; icon: React.ReactNode }> = {
    ios: { width: 260, height: 520, label: 'iOS', icon: <Smartphone className="w-4 h-4" /> },
    android: { width: 260, height: 520, label: 'Android', icon: <AppWindow className="w-4 h-4" /> },
    huawei: { width: 260, height: 520, label: 'Huawei', icon: <Tablet className="w-4 h-4" /> },
    web: { width: 380, height: 240, label: 'Web', icon: <Monitor className="w-4 h-4" /> },
  };

  // ── Platform notch/bar helper ──────────────────────────────────────────────

  const renderPlatformChrome = (p: string) => {
    if (p === 'ios') {
      return (
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-20 h-6 bg-black rounded-b-2xl z-10" />
      );
    }
    if (p === 'android') {
      return (
        <div className="absolute top-0 left-0 right-0 h-6 bg-black/80 z-10" />
      );
    }
    if (p === 'huawei') {
      return (
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-14 h-5 bg-black rounded-b-xl z-10" />
      );
    }
    return null; // web — no chrome
  };

  // ── Render preview card for a given theme ──────────────────────────────────

  const renderPreviewCard = (colors: ThemeColors, radius: number, shadow: string, title = 'Brand Preview') => (
    <div
      className="rounded-xl p-4 border transition-colors"
      style={{
        backgroundColor: colors.backgroundColor,
        color: colors.textColor,
        fontFamily: brand.fontFamily,
      }}
    >
      <div className="flex items-center gap-3 mb-3">
        <div
          className="w-10 h-10 flex items-center justify-center text-white font-bold text-sm rounded-lg"
          style={{
            backgroundColor: colors.primaryColor,
            borderRadius: Math.min(radius, 16),
          }}
        >
          {brand.logoUrl ? (
            <img src={brand.logoUrl} alt="logo" className="w-8 h-8 object-contain" />
          ) : (
            (active?.name || 'B').charAt(0)
          )}
        </div>
        <div>
          <div className="font-semibold text-sm">{title}</div>
          <div className="text-xs opacity-60">Powered by OMOBIO</div>
        </div>
      </div>
      <div
        className="inline-block px-3 py-1.5 rounded text-white text-xs font-medium mb-2 mr-1"
        style={{ backgroundColor: colors.primaryColor, borderRadius: Math.min(radius, 12) }}
      >
        Primary Action
      </div>
      <div
        className="inline-block px-3 py-1.5 rounded text-white text-xs font-medium mb-2"
        style={{ backgroundColor: colors.accentColor, borderRadius: Math.min(radius, 12) }}
      >
        Accent
      </div>
      <div
        className="p-2 border text-xs"
        style={{
          borderColor: colors.textColor + '30',
          borderRadius: Math.min(radius, 12),
          backgroundColor: colors.backgroundColor,
          boxShadow: shadow,
        }}
      >
        Card with shadow preview
      </div>
    </div>
  );

  // ── Render component variants ───────────────────────────────────────────────

  const renderComponentVariants = (colors: ThemeColors) => {
    const r = Math.min(brand.tokens.borderRadius, 16);
    return (
      <div className="space-y-4">
        {/* Buttons */}
        <div className="space-y-2">
          <Label className="text-xs text-muted-foreground uppercase tracking-wide">Buttons</Label>
          <div className="flex flex-wrap gap-2">
            <button
              className="px-4 py-2 text-white text-sm font-medium transition-opacity hover:opacity-90"
              style={{ backgroundColor: colors.primaryColor, borderRadius: Math.min(r, 12) }}
            >
              Default
            </button>
            <button
              className="px-4 py-2 text-white text-sm font-medium opacity-70"
              style={{ backgroundColor: colors.primaryColor, borderRadius: Math.min(r, 12) }}
            >
              Hover
            </button>
            <button
              className="px-4 py-2 text-white text-sm font-medium opacity-40 cursor-not-allowed"
              style={{ backgroundColor: colors.primaryColor, borderRadius: Math.min(r, 12) }}
              disabled
            >
              Disabled
            </button>
            <button
              className="px-4 py-2 text-white text-sm font-medium opacity-80 flex items-center gap-1"
              style={{ backgroundColor: colors.primaryColor, borderRadius: Math.min(r, 12) }}
            >
              <span className="w-3 h-3 rounded-full border-2 border-white border-t-transparent animate-spin inline-block" />
              Loading
            </button>
          </div>
        </div>

        {/* Inputs */}
        <div className="space-y-2">
          <Label className="text-xs text-muted-foreground uppercase tracking-wide">Inputs</Label>
          <div className="flex flex-col gap-2">
            <input
              type="text"
              placeholder="Default state"
              className="px-3 py-2 text-sm border outline-none transition-colors"
              style={{
                borderColor: colors.textColor + '40',
                backgroundColor: colors.backgroundColor,
                color: colors.textColor,
                borderRadius: Math.min(r, 12),
              }}
            />
            <input
              type="text"
              placeholder="Focused state"
              className="px-3 py-2 text-sm border outline-none"
              style={{
                borderColor: colors.primaryColor,
                backgroundColor: colors.backgroundColor,
                color: colors.textColor,
                borderRadius: Math.min(r, 12),
                boxShadow: `0 0 0 2px ${colors.primaryColor}30`,
              }}
              autoFocus
            />
            <input
              type="text"
              placeholder="Disabled state"
              disabled
              className="px-3 py-2 text-sm border opacity-50 cursor-not-allowed"
              style={{
                borderColor: colors.textColor + '20',
                backgroundColor: colors.backgroundColor + '80',
                color: colors.textColor,
                borderRadius: Math.min(r, 12),
              }}
            />
          </div>
        </div>

        {/* Cards */}
        <div className="space-y-2">
          <Label className="text-xs text-muted-foreground uppercase tracking-wide">Cards</Label>
          <div className="grid grid-cols-3 gap-2">
            {[0, 1, 2].map((i) => (
              <div
                key={i}
                className="p-3 border text-xs"
                style={{
                  borderColor: colors.textColor + '20',
                  backgroundColor: colors.backgroundColor,
                  color: colors.textColor,
                  borderRadius: Math.min(r, 12),
                  boxShadow: elevationShadows[brand.tokens.elevation],
                }}
              >
                <div
                  className="w-6 h-6 mb-1 rounded"
                  style={{ backgroundColor: colors.primaryColor + '30' }}
                />
                Card {i + 1}
              </div>
            ))}
          </div>
        </div>

        {/* Badges / Chips */}
        <div className="space-y-2">
          <Label className="text-xs text-muted-foreground uppercase tracking-wide">Badges &amp; Chips</Label>
          <div className="flex flex-wrap gap-2">
            <span
              className="px-2 py-0.5 text-xs font-medium text-white rounded-full"
              style={{ backgroundColor: colors.primaryColor, borderRadius: r >= 9999 ? 9999 : 12 }}
            >
              Primary
            </span>
            <span
              className="px-2 py-0.5 text-xs font-medium text-white rounded-full"
              style={{ backgroundColor: colors.accentColor, borderRadius: r >= 9999 ? 9999 : 12 }}
            >
              Accent
            </span>
            <span
              className="px-2 py-0.5 text-xs font-medium border rounded-full"
              style={{
                borderColor: colors.textColor + '40',
                color: colors.textColor,
                backgroundColor: 'transparent',
              }}
            >
              Outline
            </span>
          </div>
        </div>
      </div>
    );
  };

  // ── Render platform preview ─────────────────────────────────────────────────

  const frame = platformFrames[previewPlatform];
  const activeColors = brand.themeMode === 'dark' ? brand.dark : brand.light;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold flex items-center gap-2">
          <Briefcase className="w-6 h-6" />
          Branding
        </h1>
        <p className="text-gray-500 mt-1">
          Visual identity for{' '}
          <code className="bg-gray-100 px-1.5 py-0.5 rounded text-sm">
            {activeTenantId || '— no client selected —'}
          </code>
          {active?.name ? ` (${active.name})` : ''}
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">

        {/* ── Brand Assets ─────────────────────────────────────────────────── */}
        <Card>
          <CardHeader>
            <CardTitle>Brand Assets</CardTitle>
            <CardDescription>Logo, favicon, and splash image</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <Label>Logo URL</Label>
              <Input
                value={brand.logoUrl}
                onChange={(e) => setBrand((b) => ({ ...b, logoUrl: e.target.value }))}
                placeholder="https://cdn.example.com/logo.svg"
              />
            </div>
            <div>
              <Label>Favicon URL</Label>
              <Input
                value={brand.faviconUrl}
                onChange={(e) => setBrand((b) => ({ ...b, faviconUrl: e.target.value }))}
                placeholder="https://cdn.example.com/favicon.ico"
              />
            </div>
            <div>
              <Label>Splash image URL</Label>
              <Input
                value={brand.splashImageUrl}
                onChange={(e) => setBrand((b) => ({ ...b, splashImageUrl: e.target.value }))}
                placeholder="https://cdn.example.com/splash.png"
              />
            </div>
            <div className="border-2 border-dashed rounded-lg p-4 text-center text-sm text-gray-500">
              <Upload className="w-6 h-6 mx-auto mb-1" />
              Drag &amp; drop to upload (PNG, SVG, ICO — max 2MB)
            </div>
          </CardContent>
        </Card>

        {/* ── Color Palette (Light) ────────────────────────────────────────── */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Sun className="w-4 h-4" />
              Light Theme Colors
            </CardTitle>
            <CardDescription>Color tokens for light mode</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <ColorField
              label="Primary"
              value={brand.light.primaryColor}
              onChange={(v) => setLight('primaryColor', v)}
            />
            <ColorField
              label="Accent"
              value={brand.light.accentColor}
              onChange={(v) => setLight('accentColor', v)}
            />
            <ColorField
              label="Background"
              value={brand.light.backgroundColor}
              onChange={(v) => setLight('backgroundColor', v)}
            />
            <ColorField
              label="Text"
              value={brand.light.textColor}
              onChange={(v) => setLight('textColor', v)}
            />
            <div>
              <Label>Font family</Label>
              <select
                className="w-full border rounded px-3 py-2 text-sm"
                value={brand.fontFamily}
                onChange={(e) => setBrand((b) => ({ ...b, fontFamily: e.target.value }))}
              >
                <option>Inter</option>
                <option>Roboto</option>
                <option>Open Sans</option>
                <option>Lato</option>
                <option>Montserrat</option>
                <option>Poppins</option>
                <option>Noto Sans</option>
                <option>System UI</option>
              </select>
            </div>
          </CardContent>
        </Card>

        {/* ── Color Palette (Dark) ─────────────────────────────────────────── */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Moon className="w-4 h-4" />
              Dark Theme Colors
            </CardTitle>
            <CardDescription>Color tokens for dark mode</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <ColorField
              label="Primary"
              value={brand.dark.primaryColor}
              onChange={(v) => setDark('primaryColor', v)}
            />
            <ColorField
              label="Accent"
              value={brand.dark.accentColor}
              onChange={(v) => setDark('accentColor', v)}
            />
            <ColorField
              label="Background"
              value={brand.dark.backgroundColor}
              onChange={(v) => setDark('backgroundColor', v)}
            />
            <ColorField
              label="Text"
              value={brand.dark.textColor}
              onChange={(v) => setDark('textColor', v)}
            />
            <div>
              <Label>Theme mode</Label>
              <Select
                value={brand.themeMode}
                onValueChange={(v) => setBrand((b) => ({ ...b, themeMode: v as ThemeMode }))}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="light">
                    <span className="flex items-center gap-2">
                      <Sun className="w-4 h-4" /> Light only
                    </span>
                  </SelectItem>
                  <SelectItem value="dark">
                    <span className="flex items-center gap-2">
                      <Moon className="w-4 h-4" /> Dark only
                    </span>
                  </SelectItem>
                  <SelectItem value="both">
                    <span className="flex items-center gap-2">
                      <Sun className="w-4 h-4" /> / <Moon className="w-4 h-4" /> Both
                    </span>
                  </SelectItem>
                </SelectContent>
              </Select>
            </div>
          </CardContent>
        </Card>

        {/* ── Design Tokens ──────────────────────────────────────────────────── */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Box className="w-4 h-4" />
              Design Tokens
            </CardTitle>
            <CardDescription>Spacing scale, border radius, and elevation</CardDescription>
          </CardHeader>
          <CardContent className="space-y-6">

            {/* Spacing scale */}
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <Label>Base spacing unit</Label>
                <span className="text-sm font-mono bg-muted px-2 py-0.5 rounded">{brand.tokens.baseSpacing}px</span>
              </div>
              <Slider
                min={0}
                max={SPACING_STEPS.length - 1}
                step={1}
                value={[SPACING_STEPS.indexOf(brand.tokens.baseSpacing as typeof SPACING_STEPS[number])]}
                onValueChange={([i]) => setTokens('baseSpacing', SPACING_STEPS[i])}
              />
              <div className="flex gap-2">
                {spacingLabels.map((label, i) => (
                  <div key={label} className="flex flex-col items-center gap-1">
                    <div
                      className="bg-primary rounded"
                      style={{
                        width: Math.max(4, brand.tokens.baseSpacing * spacingMultipliers[i]),
                        height: Math.max(4, brand.tokens.baseSpacing * spacingMultipliers[i]),
                      }}
                    />
                    <span className="text-xs text-muted-foreground">{label}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Border radius */}
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <Label>Border radius</Label>
                <span className="text-sm font-mono bg-muted px-2 py-0.5 rounded">
                  {radiusLabel(brand.tokens.borderRadius)}
                </span>
              </div>
              <Slider
                min={0}
                max={RADIUS_STEPS.length - 1}
                step={1}
                value={[RADIUS_STEPS.indexOf(brand.tokens.borderRadius as typeof RADIUS_STEPS[number])]}
                onValueChange={([i]) => setTokens('borderRadius', RADIUS_STEPS[i])}
              />
              <div className="flex gap-3">
                {RADIUS_STEPS.map((px) => (
                  <div key={px} className="flex flex-col items-center gap-1">
                    <div
                      className="bg-primary w-8 h-8"
                      style={{ borderRadius: px === 9999 ? '9999px' : px }}
                    />
                    <span className="text-xs text-muted-foreground">{px === 9999 ? 'full' : px}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Elevation */}
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <Label>Elevation / Shadow</Label>
                <span className="text-sm font-mono bg-muted px-2 py-0.5 rounded capitalize">
                  {brand.tokens.elevation}
                </span>
              </div>
              <Select
                value={brand.tokens.elevation}
                onValueChange={(v) => setTokens('elevation', v as DesignTokens['elevation'])}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {ELEVATION_OPTIONS.map((opt) => (
                    <SelectItem key={opt} value={opt} className="capitalize">
                      {opt}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <div className="grid grid-cols-5 gap-2">
                {ELEVATION_OPTIONS.map((opt) => (
                  <div key={opt} className="text-center">
                    <div
                      className="h-8 w-full bg-white border rounded flex items-center justify-center"
                      style={{
                        boxShadow: elevationShadows[opt],
                        borderColor: opt === brand.tokens.elevation ? brand.light.primaryColor : undefined,
                        borderWidth: opt === brand.tokens.elevation ? 2 : 1,
                      }}
                    >
                      <span className="text-xs font-mono capitalize">{opt}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>

          </CardContent>
        </Card>

        {/* ── Platform Preview ──────────────────────────────────────────────── */}
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Eye className="w-5 h-5" /> Platform Preview
            </CardTitle>
            <CardDescription>See how the brand looks on different devices</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <Tabs
              value={previewPlatform}
              onValueChange={(v) => setPreviewPlatform(v as typeof previewPlatform)}
            >
              <TabsList className="mb-4">
                {(Object.keys(platformFrames) as Array<keyof typeof platformFrames>).map((p) => (
                  <TabsTrigger key={p} value={p} className="flex items-center gap-1.5">
                    {platformFrames[p].icon}
                    {platformFrames[p].label}
                  </TabsTrigger>
                ))}
              </TabsList>

              {/* Dual theme preview when "both" */}
              {brand.themeMode === 'both' && (
                <div className="flex gap-6 mb-4">
                  <div className="flex-1">
                    <div className="text-xs font-medium text-muted-foreground mb-2 flex items-center gap-1">
                      <Sun className="w-3 h-3" /> Light
                    </div>
                    <div
                      className="rounded-xl border overflow-hidden relative"
                      style={{ width: frame.width, height: frame.height }}
                    >
                      {renderPlatformChrome(previewPlatform)}
                      <div
                        className="h-full overflow-auto p-3"
                        style={{ paddingTop: previewPlatform !== 'web' ? 28 : 12 }}
                      >
                        {renderPreviewCard(brand.light, brand.tokens.borderRadius, elevationShadows[brand.tokens.elevation])}
                      </div>
                    </div>
                  </div>
                  <div className="flex-1">
                    <div className="text-xs font-medium text-muted-foreground mb-2 flex items-center gap-1">
                      <Moon className="w-3 h-3" /> Dark
                    </div>
                    <div
                      className="rounded-xl border overflow-hidden relative"
                      style={{ width: frame.width, height: frame.height }}
                    >
                      {renderPlatformChrome(previewPlatform)}
                      <div
                        className="h-full overflow-auto p-3"
                        style={{ paddingTop: previewPlatform !== 'web' ? 28 : 12 }}
                      >
                        {renderPreviewCard(brand.dark, brand.tokens.borderRadius, elevationShadows[brand.tokens.elevation], 'Dark Preview')}
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {/* Single theme preview */}
              {brand.themeMode !== 'both' && (
                <div className="flex justify-center">
                  <div
                    className="rounded-xl border overflow-hidden relative"
                    style={{ width: frame.width, height: frame.height }}
                  >
                    {renderPlatformChrome(previewPlatform)}
                    <div
                      className="h-full overflow-auto p-3"
                      style={{ paddingTop: previewPlatform !== 'web' ? 28 : 12 }}
                    >
                      {renderPreviewCard(
                        brand.themeMode === 'dark' ? brand.dark : brand.light,
                        brand.tokens.borderRadius,
                        elevationShadows[brand.tokens.elevation]
                      )}
                    </div>
                  </div>
                </div>
              )}
            </Tabs>
          </CardContent>
        </Card>

        {/* ── Component Variants Preview ─────────────────────────────────────── */}
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Component Variants</CardTitle>
            <CardDescription>
              See buttons, inputs, cards and badges in the current brand colors
            </CardDescription>
          </CardHeader>
          <CardContent>
            {brand.themeMode === 'both' ? (
              <Tabs defaultValue="light">
                <TabsList className="mb-4">
                  <TabsTrigger value="light" className="flex items-center gap-1.5">
                    <Sun className="w-4 h-4" /> Light
                  </TabsTrigger>
                  <TabsTrigger value="dark" className="flex items-center gap-1.5">
                    <Moon className="w-4 h-4" /> Dark
                  </TabsTrigger>
                </TabsList>
                <TabsContent value="light">
                  {renderComponentVariants(brand.light)}
                </TabsContent>
                <TabsContent value="dark">
                  <div
                    className="p-4 rounded-xl space-y-4"
                    style={{ backgroundColor: brand.dark.backgroundColor, color: brand.dark.textColor }}
                  >
                    {renderComponentVariants(brand.dark)}
                  </div>
                </TabsContent>
              </Tabs>
            ) : (
              renderComponentVariants(brand.themeMode === 'dark' ? brand.dark : brand.light)
            )}
          </CardContent>
        </Card>

      </div>

      {/* Footer actions */}
      <div className="flex justify-end gap-2">
        <Button variant="outline" onClick={onReset}>
          <RefreshCw className="w-4 h-4 mr-2" />
          Reset
        </Button>
        <Button onClick={onSave} disabled={saving || !activeTenantId}>
          <Save className="w-4 h-4 mr-2" />
          {saving ? 'Saving…' : 'Save Branding'}
        </Button>
      </div>
    </div>
  );
}

// ─── Color Field helper ───────────────────────────────────────────────────────

function ColorField({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
}) {
  return (
    <div className="flex items-center gap-2">
      <input
        type="color"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="w-10 h-10 border rounded cursor-pointer"
      />
      <div className="flex-1">
        <Label>{label}</Label>
        <Input
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder="#000000"
          className="font-mono"
        />
      </div>
    </div>
  );
}
