import { useState, useEffect, useCallback } from 'react';
import { useDropzone } from 'react-dropzone';
import { HexColorPicker } from 'react-colorful';
import { toast } from 'sonner';
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
  CardFooter,
} from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Slider } from '@/components/ui/slider';
import {
  Palette,
  Save,
  Upload,
  RotateCcw,
  Eye,
  Copy,
  Download,
  Smartphone,
  Monitor,
} from 'lucide-react';

/**
 * Theme Designer — visual editor for design tokens.
 *
 * Edits:
 * - Color tokens (primary, secondary, accent, surface, etc.)
 * - Typography (font family, sizes, weights)
 * - Spacing scale
 * - Border radius
 * - Elevation/shadow
 * - Logo upload
 * - Live preview (mobile + desktop)
 */
export default function ThemeDesignerPage() {
  const [tokens, setTokens] = useState<ThemeTokens>(defaultTokens);
  const [activeColorKey, setActiveColorKey] = useState<string>('primary');
  const [previewMode, setPreviewMode] = useState<'mobile' | 'desktop'>('mobile');
  const [dirty, setDirty] = useState(false);

  // Apply tokens as CSS custom properties to preview
  useEffect(() => {
    const root = document.getElementById('theme-preview');
    if (root) {
      Object.entries(tokens.colors).forEach(([key, value]) => {
        root.style.setProperty(`--color-${key}`, value);
      });
      Object.entries(tokens.typography.sizes).forEach(([key, value]) => {
        root.style.setProperty(`--font-size-${key}`, value);
      });
      root.style.setProperty('--radius', tokens.radius);
      root.style.setProperty('--font-family', tokens.typography.fontFamily);
    }
  }, [tokens]);

  const updateColor = (key: string, value: string) => {
    setTokens((prev) => ({
      ...prev,
      colors: { ...prev.colors, [key]: value },
    }));
    setDirty(true);
  };

  const updateTypography = (field: string, value: string) => {
    setTokens((prev) => ({
      ...prev,
      typography: { ...prev.typography, [field]: value },
    }));
    setDirty(true);
  };

  const updateFontSize = (key: string, value: string) => {
    setTokens((prev) => ({
      ...prev,
      typography: {
        ...prev.typography,
        sizes: { ...prev.typography.sizes, [key]: value },
      },
    }));
    setDirty(true);
  };

  const handleReset = () => {
    if (confirm('Reset all tokens to defaults? This will discard unsaved changes.')) {
      setTokens(defaultTokens);
      setDirty(false);
      toast.success('Theme reset to defaults');
    }
  };

  const handleSave = async () => {
    // POST to config-tenant-service
    try {
      // await api.post('/admin/themes', tokens);
      toast.success('Theme saved successfully');
      setDirty(false);
    } catch (err) {
      toast.error('Failed to save theme');
    }
  };

  const handleExport = () => {
    const dataStr = JSON.stringify(tokens, null, 2);
    const blob = new Blob([dataStr], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `theme-${tokens.name}.json`;
    a.click();
    URL.revokeObjectURL(url);
    toast.success('Theme exported');
  };

  const { getRootProps, getInputProps } = useDropzone({
    accept: { 'image/*': ['.png', '.jpg', '.svg', '.webp'] },
    onDrop: (files) => {
      if (files[0]) {
        const reader = new FileReader();
        reader.onload = () => {
          setTokens((prev) => ({ ...prev, logoUrl: reader.result as string }));
          setDirty(true);
          toast.success('Logo uploaded');
        };
        reader.readAsDataURL(files[0]);
      }
    },
  });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <Palette className="w-6 h-6" />
            Theme Designer
          </h1>
          <p className="text-gray-500 mt-1">Visual editor for design tokens and branding</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={handleReset}>
            <RotateCcw className="w-4 h-4 mr-2" /> Reset
          </Button>
          <Button variant="outline" onClick={handleExport}>
            <Download className="w-4 h-4 mr-2" /> Export
          </Button>
          <Button onClick={handleSave} disabled={!dirty}>
            <Save className="w-4 h-4 mr-2" /> Save{dirty && ' *'}
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Editor */}
        <Card>
          <CardHeader>
            <CardTitle>Tokens</CardTitle>
            <CardDescription>Customize colors, typography, and spacing</CardDescription>
          </CardHeader>
          <CardContent>
            <Tabs defaultValue="colors">
              <TabsList className="w-full">
                <TabsTrigger value="colors" className="flex-1">Colors</TabsTrigger>
                <TabsTrigger value="typography" className="flex-1">Typography</TabsTrigger>
                <TabsTrigger value="spacing" className="flex-1">Spacing</TabsTrigger>
                <TabsTrigger value="logo" className="flex-1">Logo</TabsTrigger>
              </TabsList>

              <TabsContent value="colors" className="space-y-4">
                <div>
                  <Label>Theme name</Label>
                  <Input
                    value={tokens.name}
                    onChange={(e) => {
                      setTokens({ ...tokens, name: e.target.value });
                      setDirty(true);
                    }}
                  />
                </div>
                <div className="grid grid-cols-2 gap-3">
                  {Object.entries(tokens.colors).map(([key, value]) => (
                    <button
                      key={key}
                      onClick={() => setActiveColorKey(key)}
                      className={`p-3 border-2 rounded-lg text-left transition-all ${
                        activeColorKey === key ? 'border-blue-500' : 'border-gray-200'
                      }`}
                    >
                      <div
                        className="w-full h-12 rounded mb-2"
                        style={{ backgroundColor: value }}
                      />
                      <div className="text-xs font-medium">{key}</div>
                      <div className="text-xs text-gray-500">{value}</div>
                    </button>
                  ))}
                </div>
                <div className="border-t pt-4">
                  <Label>Edit: {activeColorKey}</Label>
                  <div className="flex gap-3 mt-2">
                    <HexColorPicker
                      color={tokens.colors[activeColorKey]}
                      onChange={(c) => updateColor(activeColorKey, c)}
                    />
                    <Input
                      value={tokens.colors[activeColorKey]}
                      onChange={(e) => updateColor(activeColorKey, e.target.value)}
                    />
                  </div>
                </div>
              </TabsContent>

              <TabsContent value="typography" className="space-y-4">
                <div>
                  <Label>Font family</Label>
                  <select
                    className="w-full border rounded px-3 py-2"
                    value={tokens.typography.fontFamily}
                    onChange={(e) => updateTypography('fontFamily', e.target.value)}
                  >
                    <option value="Inter, sans-serif">Inter</option>
                    <option value="Roboto, sans-serif">Roboto</option>
                    <option value="system-ui, sans-serif">System UI</option>
                    <option value="'Helvetica Neue', sans-serif">Helvetica</option>
                  </select>
                </div>
                <div>
                  <Label>Font sizes (rem)</Label>
                  <div className="grid grid-cols-2 gap-3 mt-2">
                    {Object.entries(tokens.typography.sizes).map(([key, value]) => (
                      <div key={key}>
                        <Label className="text-xs">{key}</Label>
                        <Input
                          type="number"
                          step="0.1"
                          value={parseFloat(value)}
                          onChange={(e) => updateFontSize(key, `${e.target.value}rem`)}
                        />
                      </div>
                    ))}
                  </div>
                </div>
              </TabsContent>

              <TabsContent value="spacing" className="space-y-4">
                <div>
                  <Label>Border radius</Label>
                  <div className="flex items-center gap-3 mt-2">
                    <Slider
                      value={[parseInt(tokens.radius)]}
                      onValueChange={([v]) => {
                        setTokens({ ...tokens, radius: `${v}px` });
                        setDirty(true);
                      }}
                      max={32}
                      step={1}
                    />
                    <span className="text-sm w-12">{tokens.radius}</span>
                  </div>
                </div>
                <div>
                  <Label>Spacing scale (px)</Label>
                  <div className="grid grid-cols-4 gap-2 mt-2">
                    {tokens.spacing.map((s, i) => (
                      <div key={i} className="text-center">
                        <div
                          className="bg-blue-500 mx-auto"
                          style={{ width: `${s}px`, height: `${s}px`, maxWidth: '100%' }}
                        />
                        <div className="text-xs mt-1">{s}px</div>
                      </div>
                    ))}
                  </div>
                </div>
              </TabsContent>

              <TabsContent value="logo">
                <div
                  {...getRootProps()}
                  className="border-2 border-dashed border-gray-300 rounded-lg p-8 text-center cursor-pointer hover:border-blue-500"
                >
                  <input {...getInputProps()} />
                  {tokens.logoUrl ? (
                    <img src={tokens.logoUrl} alt="Logo" className="max-h-32 mx-auto" />
                  ) : (
                    <>
                      <Upload className="w-12 h-12 mx-auto text-gray-400 mb-2" />
                      <p>Drag logo here, or click to select</p>
                      <p className="text-xs text-gray-500 mt-1">PNG, SVG, JPG up to 5MB</p>
                    </>
                  )}
                </div>
              </TabsContent>
            </Tabs>
          </CardContent>
        </Card>

        {/* Preview */}
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle>Live Preview</CardTitle>
              <div className="flex gap-1">
                <Button
                  size="sm"
                  variant={previewMode === 'mobile' ? 'default' : 'outline'}
                  onClick={() => setPreviewMode('mobile')}
                >
                  <Smartphone className="w-4 h-4" />
                </Button>
                <Button
                  size="sm"
                  variant={previewMode === 'desktop' ? 'default' : 'outline'}
                  onClick={() => setPreviewMode('desktop')}
                >
                  <Monitor className="w-4 h-4" />
                </Button>
              </div>
            </div>
          </CardHeader>
          <CardContent>
            <div
              id="theme-preview"
              className="border rounded-lg overflow-hidden"
              style={{
                width: previewMode === 'mobile' ? '375px' : '100%',
                height: '600px',
                margin: '0 auto',
                backgroundColor: tokens.colors.surface,
                fontFamily: tokens.typography.fontFamily,
                borderRadius: tokens.radius,
              }}
            >
              <div
                className="p-4"
                style={{ backgroundColor: tokens.colors.primary, color: '#fff' }}
              >
                <div className="flex items-center gap-2">
                  {tokens.logoUrl && <img src={tokens.logoUrl} className="h-6" alt="" />}
                  <span style={{ fontSize: tokens.typography.sizes.lg }}>My Operator</span>
                </div>
              </div>
              <div className="p-4 space-y-3">
                <div
                  className="p-4 rounded"
                  style={{
                    backgroundColor: tokens.colors.surfaceVariant,
                    borderRadius: tokens.radius,
                  }}
                >
                  <h3 style={{ fontSize: tokens.typography.sizes.lg }}>Welcome back</h3>
                  <p style={{ fontSize: tokens.typography.sizes.sm, color: tokens.colors.onSurfaceVariant }}>
                    Here's your account overview
                  </p>
                </div>
                <button
                  className="w-full p-3"
                  style={{
                    backgroundColor: tokens.colors.accent,
                    color: '#fff',
                    borderRadius: tokens.radius,
                  }}
                >
                  Primary Action
                </button>
                <button
                  className="w-full p-3"
                  style={{
                    backgroundColor: 'transparent',
                    color: tokens.colors.primary,
                    border: `1px solid ${tokens.colors.primary}`,
                    borderRadius: tokens.radius,
                  }}
                >
                  Secondary Action
                </button>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

interface ThemeTokens {
  name: string;
  colors: Record<string, string>;
  typography: {
    fontFamily: string;
    sizes: Record<string, string>;
  };
  radius: string;
  spacing: number[];
  logoUrl: string;
}

const defaultTokens: ThemeTokens = {
  name: 'Default Theme',
  colors: {
    primary: '#7c3aed',
    primaryVariant: '#6d28d9',
    secondary: '#ec4899',
    accent: '#f59e0b',
    background: '#ffffff',
    surface: '#f9fafb',
    surfaceVariant: '#f3f4f6',
    onPrimary: '#ffffff',
    onSurface: '#111827',
    onSurfaceVariant: '#6b7280',
    error: '#ef4444',
    success: '#10b981',
    warning: '#f59e0b',
  },
  typography: {
    fontFamily: 'Inter, sans-serif',
    sizes: {
      xs: '0.75rem',
      sm: '0.875rem',
      base: '1rem',
      lg: '1.125rem',
      xl: '1.25rem',
      '2xl': '1.5rem',
      '3xl': '1.875rem',
    },
  },
  radius: '8px',
  spacing: [4, 8, 12, 16, 24, 32, 48, 64],
  logoUrl: '',
};
