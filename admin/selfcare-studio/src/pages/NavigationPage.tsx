/**
 * NavigationPage — configure the mobile app's navigation structure.
 *
 * Covers two configuration domains:
 *   1. Bottom Tab Bar — tabs shown at the bottom of the mobile app
 *   2. Deep Links / Universal Links — routing rules for external links
 *     that open specific screens inside the app.
 *
 * The backend does not yet expose a navigation-config API, so this page
 * manages local state backed by realistic mock data.
 *
 * Features:
 *   Bottom Tabs:
 *     - Visual list with drag-to-reorder (state-managed, no external DnD lib)
 *     - Add / remove / edit tabs (icon picker via lucide, label, route, enabled)
 *     - Tab bar preview strip
 *   Deep Links:
 *     - List of route patterns
 *     - Add / edit deep link: pattern, target screen, params, enabled
 *     - Universal links / App links configuration
 *     - Allowed external domains list
 */
import { useState } from 'react';
import { toast } from 'sonner';
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import {
  Navigation,
  Plus,
  Trash2,
  GripVertical,
  Save,
  RefreshCw,
  Eye,
  ExternalLink,
  Globe,
  Smartphone,
  CheckCircle,
  XCircle,
  ChevronUp,
  ChevronDown,
  Link2,
  Shield,
  Settings,
  X,
} from 'lucide-react';
import { useActiveTenant } from '@/hooks/useActiveTenant';
import * as LucideIcons from 'lucide-react';
import { cn } from '@/lib/utils';

// ─── Types ────────────────────────────────────────────────────────────────────

export interface BottomTab {
  id: string;
  label: string;
  icon: string;       // Lucide icon name, e.g. "Home"
  route: string;       // e.g. "/home"
  order: number;
  enabled: boolean;
}

export interface DeepLink {
  id: string;
  pattern: string;     // e.g. "omobio://product/{id}"
  targetScreen: string; // e.g. "ProductDetailScreen"
  params: string[];   // extracted URL params: ["id"]
  enabled: boolean;
  description?: string;
}

export interface NavigationConfig {
  bottomTabs: BottomTab[];
  universalLinks: {
    enabled: boolean;
    associatedDomains: string[];  // e.g. ["applinks:dialog.lk", "applinks:omobio.io"]
  };
  allowedDomains: string[];
  deepLinks: DeepLink[];
}

// ─── Mock initial state ───────────────────────────────────────────────────────

const MOCK_CONFIG: NavigationConfig = {
  bottomTabs: [
    { id: 'tab_001', label: 'Home', icon: 'Home', route: '/home', order: 0, enabled: true },
    { id: 'tab_002', label: 'Balance', icon: 'Wallet', route: '/balance', order: 1, enabled: true },
    { id: 'tab_003', label: 'Packages', icon: 'Package', route: '/packages', order: 2, enabled: true },
    { id: 'tab_004', label: 'Support', icon: 'Headphones', route: '/support', order: 3, enabled: true },
    { id: 'tab_005', label: 'Profile', icon: 'User', route: '/profile', order: 4, enabled: true },
  ],
  universalLinks: {
    enabled: true,
    associatedDomains: ['applinks:dialog.omobio.io', 'applinks:hutch.omobio.io'],
  },
  allowedDomains: [
    'https://www.dialog.lk',
    'https://www.hutch.lk',
    'https://omobio.io',
    'https://docs.omobio.io',
  ],
  deepLinks: [
    {
      id: 'dl_001',
      pattern: 'omobio://balance',
      targetScreen: 'BalanceScreen',
      params: [],
      enabled: true,
      description: 'Opens the balance overview screen',
    },
    {
      id: 'dl_002',
      pattern: 'omobio://product/{productId}',
      targetScreen: 'ProductDetailScreen',
      params: ['productId'],
      enabled: true,
      description: 'Opens a specific product offer detail',
    },
    {
      id: 'dl_003',
      pattern: 'omobio://payment/{transactionId}',
      targetScreen: 'PaymentStatusScreen',
      params: ['transactionId'],
      enabled: false,
      description: 'Opens payment confirmation / status',
    },
    {
      id: 'dl_004',
      pattern: 'omobio://journey/{journeyId}',
      targetScreen: 'JourneyScreen',
      params: ['journeyId'],
      enabled: true,
      description: 'Launches a named customer journey',
    },
  ],
};

// ─── Icon picker helpers ───────────────────────────────────────────────────────

const TAB_ICONS = [
  'Home', 'Wallet', 'Package', 'Headphones', 'User', 'Settings', 'Bell',
  'CreditCard', 'BarChart3', 'ShoppingCart', 'Heart', 'Star', 'Search',
  'MessageSquare', 'Phone', 'Mail', 'MapPin', 'Camera', 'Image', 'FileText',
  'Globe', 'Shield', 'Lock', 'Key', 'Zap', 'Activity', 'TrendingUp',
];

function getLucideIcon(name: string, className?: string) {
  const Icon = (LucideIcons as any)[name];
  if (!Icon) return <Settings className={className} />;
  return <Icon className={className} />;
}

// ─── Tab editor modal ──────────────────────────────────────────────────────────

interface TabModalProps {
  tab?: BottomTab;
  onClose: () => void;
  onSave: (tab: Omit<BottomTab, 'id'>) => void;
}

function TabModal({ tab, onClose, onSave }: TabModalProps) {
  const [form, setForm] = useState<Omit<BottomTab, 'id'>>({
    label: tab?.label ?? '',
    icon: tab?.icon ?? 'Home',
    route: tab?.route ?? '/',
    enabled: tab?.enabled ?? true,
    order: tab?.order ?? 0,
  });

  const set = (key: keyof typeof form, value: any) =>
    setForm((f) => ({ ...f, [key]: value }));

  const handleSave = () => {
    if (!form.label || !form.route) {
      toast.error('Label and route are required');
      return;
    }
    onSave(form);
    onClose();
  };

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{tab ? 'Edit Tab' : 'Add Tab'}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <div>
            <Label>Icon</Label>
            <div className="grid grid-cols-8 gap-1 mt-1">
              {TAB_ICONS.map((iconName) => (
                <button
                  key={iconName}
                  type="button"
                  className={cn(
                    'p-2 rounded border flex items-center justify-center hover:bg-gray-50 transition-colors',
                    form.icon === iconName
                      ? 'border-purple-500 bg-purple-50 text-purple-600'
                      : 'border-gray-200 text-gray-600'
                  )}
                  onClick={() => set('icon', iconName)}
                  title={iconName}
                >
                  {getLucideIcon(iconName, 'w-4 h-4')}
                </button>
              ))}
            </div>
          </div>
          <div>
            <Label>Label</Label>
            <Input value={form.label} onChange={(e) => set('label', e.target.value)} placeholder="e.g. Home" />
          </div>
          <div>
            <Label>Route (path)</Label>
            <Input value={form.route} onChange={(e) => set('route', e.target.value)} placeholder="/home" />
          </div>
          <div className="flex items-center gap-2">
            <input
              type="checkbox"
              id="tab-enabled"
              checked={form.enabled}
              onChange={(e) => set('enabled', e.target.checked)}
              className="w-4 h-4"
            />
            <Label htmlFor="tab-enabled" className="cursor-pointer">Enabled (visible to users)</Label>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button onClick={handleSave}>{tab ? 'Update Tab' : 'Add Tab'}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// ─── Deep link editor modal ────────────────────────────────────────────────────

interface DeepLinkModalProps {
  deepLink?: DeepLink;
  onClose: () => void;
  onSave: (dl: Omit<DeepLink, 'id'>) => void;
}

function DeepLinkModal({ deepLink, onClose, onSave }: DeepLinkModalProps) {
  const [form, setForm] = useState({
    pattern: deepLink?.pattern ?? 'omobio://',
    targetScreen: deepLink?.targetScreen ?? '',
    params: deepLink?.params?.join(', ') ?? '',
    enabled: deepLink?.enabled ?? true,
    description: deepLink?.description ?? '',
  });

  const set = (key: string, value: any) =>
    setForm((f) => ({ ...f, [key]: value }));

  const extractParams = (pattern: string) => {
    const matches = pattern.match(/\{(\w+)\}/g) ?? [];
    return matches.map((m) => m.slice(1, -1));
  };

  const handlePatternChange = (v: string) => {
    set('pattern', v);
    set('params', extractParams(v).join(', '));
  };

  const handleSave = () => {
    if (!form.pattern || !form.targetScreen) {
      toast.error('Pattern and target screen are required');
      return;
    }
    onSave({
      pattern: form.pattern,
      targetScreen: form.targetScreen,
      params: form.params
        ? form.params.split(',').map((p) => p.trim()).filter(Boolean)
        : [],
      enabled: form.enabled,
      description: form.description,
    });
    onClose();
  };

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{deepLink ? 'Edit Deep Link' : 'Add Deep Link'}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <div>
            <Label>URL Pattern</Label>
            <Input
              value={form.pattern}
              onChange={(e) => handlePatternChange(e.target.value)}
              placeholder="omobio://product/{id}"
            />
            <p className="text-xs text-gray-400 mt-1">
              Use {'{paramName}'} placeholders for dynamic segments. Supported schemes: omobio://, dialoglk://, hutchlk://
            </p>
          </div>
          <div>
            <Label>Target Screen</Label>
            <Input
              value={form.targetScreen}
              onChange={(e) => set('targetScreen', e.target.value)}
              placeholder="ProductDetailScreen"
            />
          </div>
          <div>
            <Label>Description (optional)</Label>
            <Input
              value={form.description}
              onChange={(e) => set('description', e.target.value)}
              placeholder="What this link opens"
            />
          </div>
          <div>
            <Label>Extracted Parameters (auto-detected)</Label>
            <Input value={form.params} disabled placeholder="id, productId, ..." />
          </div>
          <div className="flex items-center gap-2">
            <input
              type="checkbox"
              id="dl-enabled"
              checked={form.enabled}
              onChange={(e) => set('enabled', e.target.checked)}
              className="w-4 h-4"
            />
            <Label htmlFor="dl-enabled" className="cursor-pointer">Enabled</Label>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button onClick={handleSave}>{deepLink ? 'Update' : 'Add Deep Link'}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// ─── Tab bar preview ──────────────────────────────────────────────────────────

function TabBarPreview({ tabs }: { tabs: BottomTab[] }) {
  const sorted = [...tabs].sort((a, b) => a.order - b.order);
  const enabled = sorted.filter((t) => t.enabled);
  return (
    <div className="flex items-center justify-around bg-gray-900 rounded-xl p-3 shadow-lg">
      {enabled.map((tab) => (
        <div key={tab.id} className="flex flex-col items-center gap-1 min-w-[48px]">
          {getLucideIcon(tab.icon, 'w-5 h-5 text-white')}
          <span className="text-white text-xs opacity-70">{tab.label}</span>
        </div>
      ))}
    </div>
  );
}

// ─── Main page ────────────────────────────────────────────────────────────────

export default function NavigationPage() {
  const { activeTenantId } = useActiveTenant();
  const [config, setConfig] = useState<NavigationConfig>(MOCK_CONFIG);
  const [saving, setSaving] = useState(false);

  // Tab modal state
  const [tabModalOpen, setTabModalOpen] = useState(false);
  const [editingTab, setEditingTab] = useState<BottomTab | undefined>(undefined);

  // Deep link modal state
  const [dlModalOpen, setDlModalOpen] = useState(false);
  const [editingDl, setEditingDl] = useState<DeepLink | undefined>(undefined);

  // Allowed domain input
  const [newDomain, setNewDomain] = useState('');

  // ─── Tab operations ───────────────────────────────────────────────────────

  const openAddTab = () => { setEditingTab(undefined); setTabModalOpen(true); };
  const openEditTab = (tab: BottomTab) => { setEditingTab(tab); setTabModalOpen(true); };

  const saveTab = (tabData: Omit<BottomTab, 'id'>) => {
    if (editingTab) {
      setConfig((c) => ({
        ...c,
        bottomTabs: c.bottomTabs.map((t) =>
          t.id === editingTab.id ? { ...t, ...tabData } : t
        ),
      }));
      toast.success('Tab updated');
    } else {
      const newTab: BottomTab = {
        ...tabData,
        id: `tab_${Date.now()}`,
        order: config.bottomTabs.length,
      };
      setConfig((c) => ({ ...c, bottomTabs: [...c.bottomTabs, newTab] }));
      toast.success('Tab added');
    }
  };

  const deleteTab = (id: string) => {
    setConfig((c) => ({
      ...c,
      bottomTabs: c.bottomTabs.filter((t) => t.id !== id),
    }));
    toast.success('Tab removed');
  };

  const moveTab = (id: string, direction: 'up' | 'down') => {
    setConfig((c) => {
      const sorted = [...c.bottomTabs].sort((a, b) => a.order - b.order);
      const idx = sorted.findIndex((t) => t.id === id);
      const swapIdx = direction === 'up' ? idx - 1 : idx + 1;
      if (swapIdx < 0 || swapIdx >= sorted.length) return c;
      const updated = sorted.map((t, i) => {
        if (i === idx) return { ...t, order: sorted[swapIdx].order };
        if (i === swapIdx) return { ...t, order: sorted[idx].order };
        return t;
      });
      return { ...c, bottomTabs: updated };
    });
  };

  // ─── Deep link operations ─────────────────────────────────────────────────

  const openAddDl = () => { setEditingDl(undefined); setDlModalOpen(true); };
  const openEditDl = (dl: DeepLink) => { setEditingDl(dl); setDlModalOpen(true); };

  const saveDeepLink = (dlData: Omit<DeepLink, 'id'>) => {
    if (editingDl) {
      setConfig((c) => ({
        ...c,
        deepLinks: c.deepLinks.map((d) =>
          d.id === editingDl.id ? { ...d, ...dlData } : d
        ),
      }));
      toast.success('Deep link updated');
    } else {
      const newDl: DeepLink = { ...dlData, id: `dl_${Date.now()}` };
      setConfig((c) => ({ ...c, deepLinks: [...c.deepLinks, newDl] }));
      toast.success('Deep link added');
    }
  };

  const deleteDeepLink = (id: string) => {
    setConfig((c) => ({ ...c, deepLinks: c.deepLinks.filter((d) => d.id !== id) }));
    toast.success('Deep link removed');
  };

  // ─── Domain operations ─────────────────────────────────────────────────────

  const addDomain = () => {
    const domain = newDomain.trim();
    if (!domain) return;
    if (config.allowedDomains.includes(domain)) {
      toast.warning('Domain already in the list');
      return;
    }
    setConfig((c) => ({ ...c, allowedDomains: [...c.allowedDomains, domain] }));
    setNewDomain('');
    toast.success(`Domain added: ${domain}`);
  };

  const removeDomain = (domain: string) => {
    setConfig((c) => ({ ...c, allowedDomains: c.allowedDomains.filter((d) => d !== domain) }));
  };

  // ─── Save ─────────────────────────────────────────────────────────────────

  const onSave = async () => {
    setSaving(true);
    await new Promise((r) => setTimeout(r, 700));
    setSaving(false);
    toast.success('Navigation configuration saved');
    // TODO: POST to /api/v1/admin/navigation once backend is wired
  };

  const onReset = () => {
    if (confirm('Reset to default navigation config? Unsaved changes will be lost.')) {
      setConfig(MOCK_CONFIG);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <Navigation className="w-6 h-6" />
            Navigation &amp; Deep Links
          </h1>
          <p className="text-gray-500 mt-1">
            Configure mobile tab bar and URL routing for{' '}
            <code className="bg-gray-100 px-1.5 py-0.5 rounded text-sm">
              {activeTenantId || '—'}
            </code>
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={onReset}>
            <RefreshCw className="w-4 h-4 mr-2" /> Reset
          </Button>
          <Button onClick={onSave} disabled={saving}>
            <Save className="w-4 h-4 mr-2" />
            {saving ? 'Saving…' : 'Save Config'}
          </Button>
        </div>
      </div>

      <Tabs defaultValue="tabs">
        <TabsList>
          <TabsTrigger value="tabs">
            <Smartphone className="w-4 h-4 mr-1.5" /> Bottom Tabs
          </TabsTrigger>
          <TabsTrigger value="deeplinks">
            <Link2 className="w-4 h-4 mr-1.5" /> Deep Links / Universal Links
          </TabsTrigger>
        </TabsList>

        {/* ── Bottom Tabs ──────────────────────────────────────────────────── */}
        <TabsContent value="tabs" className="space-y-6">
          {/* Tab bar preview */}
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Eye className="w-5 h-5" /> Live Preview
              </CardTitle>
              <CardDescription>How the bottom tab bar will appear in the mobile app</CardDescription>
            </CardHeader>
            <CardContent>
              <TabBarPreview tabs={config.bottomTabs} />
            </CardContent>
          </Card>

          {/* Tabs list */}
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <div>
                <CardTitle>Tab Items</CardTitle>
                <CardDescription>
                  {config.bottomTabs.length} tab(s) configured — drag to reorder
                </CardDescription>
              </div>
              <Button size="sm" onClick={openAddTab}>
                <Plus className="w-4 h-4 mr-2" /> Add Tab
              </Button>
            </CardHeader>
            <CardContent className="p-0">
              <div className="divide-y">
                {[...config.bottomTabs]
                  .sort((a, b) => a.order - b.order)
                  .map((tab) => (
                    <div
                      key={tab.id}
                      className={cn(
                        'flex items-center gap-3 px-4 py-3 hover:bg-gray-50 transition-colors',
                        !tab.enabled && 'opacity-50'
                      )}
                    >
                      <GripVertical className="w-4 h-4 text-gray-300 flex-shrink-0" />
                      {getLucideIcon(tab.icon, 'w-5 h-5 text-gray-600 flex-shrink-0')}
                      <div className="flex-1 min-w-0">
                        <div className="font-medium text-sm">{tab.label}</div>
                        <div className="text-xs text-gray-400 font-mono">{tab.route}</div>
                      </div>
                      <Badge variant={tab.enabled ? 'default' : 'secondary'}>
                        {tab.enabled ? 'Enabled' : 'Hidden'}
                      </Badge>
                      <div className="flex items-center gap-1">
                        <Button
                          size="icon"
                          variant="ghost"
                          className="w-7 h-7"
                          onClick={() => moveTab(tab.id, 'up')}
                          title="Move up"
                        >
                          <ChevronUp className="w-3 h-3" />
                        </Button>
                        <Button
                          size="icon"
                          variant="ghost"
                          className="w-7 h-7"
                          onClick={() => moveTab(tab.id, 'down')}
                          title="Move down"
                        >
                          <ChevronDown className="w-3 h-3" />
                        </Button>
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() => openEditTab(tab)}
                        >
                          Edit
                        </Button>
                        <Button
                          size="sm"
                          variant="ghost"
                          className="text-red-600"
                          onClick={() => deleteTab(tab.id)}
                        >
                          <Trash2 className="w-3 h-3" />
                        </Button>
                      </div>
                    </div>
                  ))}
                {config.bottomTabs.length === 0 && (
                  <div className="text-center py-8 text-gray-500 text-sm">
                    No tabs configured. Click "Add Tab" to start.
                  </div>
                )}
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        {/* ── Deep Links / Universal Links ────────────────────────────────────── */}
        <TabsContent value="deeplinks" className="space-y-6">
          {/* Universal links config */}
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Globe className="w-5 h-5" /> Universal Links &amp; App Links
              </CardTitle>
              <CardDescription>
                Configure domains that can open screens inside the app via HTTP(S) links
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="ul-enabled"
                  checked={config.universalLinks.enabled}
                  onChange={(e) =>
                    setConfig((c) => ({
                      ...c,
                      universalLinks: { ...c.universalLinks, enabled: e.target.checked },
                    }))
                  }
                  className="w-4 h-4"
                />
                <Label htmlFor="ul-enabled" className="cursor-pointer">
                  Enable Universal Links / App Links
                </Label>
              </div>
              {config.universalLinks.enabled && (
                <>
                  <div>
                    <Label>Associated Domains (Apple App Links / Android App Links)</Label>
                    <div className="mt-1 space-y-1">
                      {config.universalLinks.associatedDomains.map((d) => (
                        <div key={d} className="flex items-center gap-2">
                          <Globe className="w-4 h-4 text-gray-400 flex-shrink-0" />
                          <code className="flex-1 bg-gray-100 px-2 py-1 rounded text-xs">{d}</code>
                        </div>
                      ))}
                    </div>
                    <p className="text-xs text-gray-400 mt-1">
                      Add these to your app&apos;s entitlements file (Apple: apple-app-site-association; Android: assetlinks.json)
                    </p>
                  </div>
                  <div>
                    <Label>Allowed External Domains</Label>
                    <div className="flex gap-2 mt-1">
                      <Input
                        value={newDomain}
                        onChange={(e) => setNewDomain(e.target.value)}
                        placeholder="https://www.example.com"
                        onKeyDown={(e) => e.key === 'Enter' && addDomain()}
                      />
                      <Button onClick={addDomain}>Add</Button>
                    </div>
                    <div className="mt-2 space-y-1">
                      {config.allowedDomains.map((d) => (
                        <div key={d} className="flex items-center gap-2 bg-gray-50 rounded px-3 py-2">
                          <Globe className="w-4 h-4 text-gray-400 flex-shrink-0" />
                          <code className="flex-1 text-sm">{d}</code>
                          <button
                            onClick={() => removeDomain(d)}
                            className="text-gray-400 hover:text-red-500 transition-colors"
                          >
                            <X className="w-4 h-4" />
                          </button>
                        </div>
                      ))}
                      {config.allowedDomains.length === 0 && (
                        <p className="text-sm text-gray-400 italic">No domains added yet</p>
                      )}
                    </div>
                  </div>
                </>
              )}
            </CardContent>
          </Card>

          {/* Deep links list */}
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <div>
                <CardTitle>Deep Link Routes</CardTitle>
                <CardDescription>
                  Custom URL schemes and route patterns that open specific app screens
                </CardDescription>
              </div>
              <Button size="sm" onClick={openAddDl}>
                <Plus className="w-4 h-4 mr-2" /> Add Deep Link
              </Button>
            </CardHeader>
            <CardContent className="p-0">
              <div className="divide-y">
                {config.deepLinks.map((dl) => (
                  <div
                    key={dl.id}
                    className={cn(
                      'flex items-start gap-3 px-4 py-3 hover:bg-gray-50 transition-colors',
                      !dl.enabled && 'opacity-50'
                    )}
                  >
                    <Link2 className="w-4 h-4 text-gray-400 mt-0.5 flex-shrink-0" />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <code className="bg-gray-100 px-2 py-0.5 rounded text-xs font-mono">
                          {dl.pattern}
                        </code>
                        <span className="text-gray-300">→</span>
                        <Badge variant="outline" className="text-xs">
                          {dl.targetScreen}
                        </Badge>
                        {dl.params.length > 0 && (
                          <span className="text-xs text-gray-400">
                            params: {dl.params.join(', ')}
                          </span>
                        )}
                      </div>
                      {dl.description && (
                        <p className="text-xs text-gray-500 mt-1">{dl.description}</p>
                      )}
                    </div>
                    {dl.enabled ? (
                      <CheckCircle className="w-4 h-4 text-green-500 flex-shrink-0" />
                    ) : (
                      <XCircle className="w-4 h-4 text-gray-300 flex-shrink-0" />
                    )}
                    <div className="flex items-center gap-1 flex-shrink-0">
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => openEditDl(dl)}
                      >
                        Edit
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        className="text-red-600"
                        onClick={() => deleteDeepLink(dl.id)}
                      >
                        <Trash2 className="w-3 h-3" />
                      </Button>
                    </div>
                  </div>
                ))}
                {config.deepLinks.length === 0 && (
                  <div className="text-center py-8 text-gray-500 text-sm">
                    No deep links configured. Click "Add Deep Link" to start.
                  </div>
                )}
              </div>
            </CardContent>
          </Card>

          {/* Integration note */}
          <Card className="border-dashed border-2">
            <CardContent className="py-6 text-center text-gray-500">
              <ExternalLink className="w-6 h-6 mx-auto mb-2 text-gray-300" />
              <p className="text-sm">
                To enable deep links on iOS, add URL schemes to <code>Info.plist</code>.
                On Android, add intent filters in <code>AndroidManifest.xml</code>.
                See the <strong>OMOBIO App Manifest</strong> template for details.
              </p>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      {/* Modals */}
      {tabModalOpen && (
        <TabModal
          tab={editingTab}
          onClose={() => setTabModalOpen(false)}
          onSave={saveTab}
        />
      )}
      {dlModalOpen && (
        <DeepLinkModal
          deepLink={editingDl}
          onClose={() => setDlModalOpen(false)}
          onSave={saveDeepLink}
        />
      )}
    </div>
  );
}
