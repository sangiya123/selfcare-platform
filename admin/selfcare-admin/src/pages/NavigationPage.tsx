/**
 * NavigationPage — configure the mobile app's navigation structure.
 *
 * Covers two configuration domains:
 *   1. Bottom Tab Bar — tabs shown at the bottom of the mobile app
 *   2. Deep Links / Universal Links — routing rules for external links
 *     that open specific screens inside the app.
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
 *
 * API: /api/v1/admin/navigation (Config Tenant Service)
 */
import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
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
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
} from '@/components/ui/select';
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
  Menu,
  Zap,
  Layout,
} from 'lucide-react';
import { useActiveTenant } from '@/hooks/useActiveTenant';
import * as LucideIcons from 'lucide-react';
import { cn } from '@/lib/utils';
import { api } from '@/lib/api';

// ─── Types matching backend NavigationDocument ──────────────────────────────────

export interface BottomTab {
  id: string;
  label: string;
  icon: string;
  route: string;
  order: number;
  enabled: boolean;
  badge?: { source: string; max: number };
  visibleWhen?: { feature?: string; lob?: string; segment?: string };
}

export interface DrawerItem {
  id: string;
  label: string;
  icon: string;
  route: string;
  section?: string;
  requiresAuth?: boolean;
  visibleWhen?: Record<string, any>;
  order: number;
  enabled: boolean;
}

export interface QuickAction {
  id: string;
  label: string;
  icon: string;
  action: NavAction;
  visibleWhen?: Record<string, any>;
}

export interface NavAction {
  type: 'NAVIGATE' | 'OPEN_WEB' | 'OPEN_WEB_SSO' | 'START_JOURNEY' | 'DEEP_LINK' | 'MODAL' | 'EXTERNAL_BROWSER';
  route?: string;
  url?: string;
  journeyId?: string;
  params?: Record<string, any>;
  analyticsEvent?: string;
}

export interface Route {
  id: string;
  path: string;
  screen: string;
  params?: RouteParam[];
  requiresAuth?: boolean;
  guards?: string[];
}

export interface RouteParam {
  name: string;
  type: 'string' | 'number' | 'boolean';
  required: boolean;
}

export interface UniversalLink {
  id: string;
  host: string;
  pathPattern: string;
  route: string;
  paramMap?: Record<string, string>;
}

export interface UniversalLinksConfig {
  enabled?: boolean;
  associatedDomains?: string[];
  universalLinks?: UniversalLink[];
}

export interface DeepLink {
  id: string;
  scheme: string;
  pathPattern: string;
  route: string;
  paramMap?: Record<string, string>;
  fallbackUrl?: string;
  enabled: boolean;
  description?: string;
}

export interface NavigationConfig {
  tabs: BottomTab[];
  drawerItems?: DrawerItem[];
  quickActions?: QuickAction[];
  routes?: Route[];
  universalLinks?: UniversalLinksConfig;
  deepLinks?: DeepLink[];
  allowedDomains?: string[];
  metadata?: Record<string, any>;
  tenantId?: string;
  name?: string;
  version?: number;
  status?: 'DRAFT' | 'REVIEW' | 'APPROVED' | 'PUBLISHED' | 'ARCHIVED';
  createdAt?: string;
  updatedAt?: string;
}

// ─── Default initial state (used as fallback) ───────────────────────────────────

const DEFAULT_CONFIG: NavigationConfig = {
  tabs: [
    { id: 'tab_001', label: 'Home', icon: 'Home', route: '/home', order: 0, enabled: true },
    { id: 'tab_002', label: 'Balance', icon: 'Wallet', route: '/balance', order: 1, enabled: true },
    { id: 'tab_003', label: 'Packages', icon: 'Package', route: '/packages', order: 2, enabled: true },
    { id: 'tab_004', label: 'Support', icon: 'Headphones', route: '/support', order: 3, enabled: true },
    { id: 'tab_005', label: 'Profile', icon: 'User', route: '/profile', order: 4, enabled: true },
  ],
  drawerItems: [
    { id: 'drawer_001', label: 'My Account', icon: 'User', route: '/account', section: 'account', order: 0, requiresAuth: true, enabled: true },
    { id: 'drawer_002', label: 'Billing History', icon: 'FileText', route: '/bills', section: 'billing', order: 1, requiresAuth: true, enabled: true },
    { id: 'drawer_003', label: 'Usage', icon: 'Activity', route: '/usage', section: 'usage', order: 2, requiresAuth: true, enabled: true },
    { id: 'drawer_004', label: 'Packages', icon: 'Package', route: '/packages', section: 'packages', order: 3, requiresAuth: true, enabled: true },
    { id: 'drawer_005', label: 'Support', icon: 'Headphones', route: '/support', section: 'support', order: 4, requiresAuth: true, enabled: true },
    { id: 'drawer_006', label: 'Settings', icon: 'Settings', route: '/settings', section: 'settings', order: 5, requiresAuth: true, enabled: true },
  ],
  quickActions: [
    { id: 'qa_001', label: 'Recharge', icon: 'CreditCard', action: { type: 'NAVIGATE', route: '/recharge' }, visibleWhen: {} },
    { id: 'qa_002', label: 'Pay Bill', icon: 'CreditCard', action: { type: 'NAVIGATE', route: '/pay-bill' }, visibleWhen: {} },
    { id: 'qa_003', label: 'Data Loan', icon: 'Zap', action: { type: 'NAVIGATE', route: '/data-loan' }, visibleWhen: {} },
    { id: 'qa_004', label: 'Share Credit', icon: 'Users', action: { type: 'NAVIGATE', route: '/share-credit' }, visibleWhen: {} },
  ],
  routes: [
    { id: 'route_001', path: '/home', screen: 'HomeScreen' },
    { id: 'route_002', path: '/balance', screen: 'BalanceScreen' },
    { id: 'route_003', path: '/packages', screen: 'PackagesScreen' },
    { id: 'route_004', path: '/recharge', screen: 'RechargeScreen' },
    { id: 'route_005', path: '/pay-bill', screen: 'PayBillScreen' },
    { id: 'route_006', path: '/data-loan', screen: 'DataLoanScreen' },
    { id: 'route_007', path: '/share-credit', screen: 'ShareCreditScreen' },
    { id: 'route_008', path: '/account', screen: 'AccountScreen' },
    { id: 'route_009', path: '/bills', screen: 'BillsScreen' },
    { id: 'route_010', path: '/usage', screen: 'UsageScreen' },
    { id: 'route_011', path: '/support', screen: 'SupportScreen' },
    { id: 'route_012', path: '/settings', screen: 'SettingsScreen' },
  ],
  universalLinks: {
    enabled: true,
    associatedDomains: ['applinks:dialog.selfcare.io', 'applinks:hutch.selfcare.io'],
    universalLinks: [
      { id: 'ul_001', host: 'dialog.selfcare.io', pathPattern: '/app/*', route: '/deep-link', paramMap: {} },
      { id: 'ul_002', host: 'hutch.selfcare.io', pathPattern: '/app/*', route: '/deep-link', paramMap: {} },
    ],
  },
  allowedDomains: [
    'https://www.dialog.lk',
    'https://www.hutch.lk',
    'https://selfcare.io',
    'https://docs.selfcare.io',
  ],
  deepLinks: [
    {
      id: 'dl_001',
      scheme: 'selfcare',
      pathPattern: 'balance',
      route: '/balance',
      paramMap: {},
      fallbackUrl: 'https://selfcare.io/balance',
      enabled: true,
      description: 'Opens the balance overview screen',
    },
    {
      id: 'dl_002',
      scheme: 'selfcare',
      pathPattern: 'product/{productId}',
      route: '/product/{productId}',
      paramMap: { productId: 'productId' },
      fallbackUrl: 'https://selfcare.io/product',
      enabled: true,
      description: 'Opens a specific product offer detail',
    },
    {
      id: 'dl_003',
      scheme: 'selfcare',
      pathPattern: 'payment/{transactionId}',
      route: '/payment/{transactionId}',
      paramMap: { transactionId: 'transactionId' },
      fallbackUrl: 'https://selfcare.io/payment',
      enabled: false,
      description: 'Opens payment confirmation / status',
    },
    {
      id: 'dl_004',
      scheme: 'selfcare',
      pathPattern: 'journey/{journeyId}',
      route: '/journey/{journeyId}',
      paramMap: { journeyId: 'journeyId' },
      fallbackUrl: 'https://selfcare.io/journey',
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
  const [form, setForm] = useState<Omit<DeepLink, 'id'>>({
    scheme: deepLink?.scheme ?? 'selfcare',
    pathPattern: deepLink?.pathPattern ?? '',
    route: deepLink?.route ?? '',
    paramMap: deepLink?.paramMap ?? {},
    fallbackUrl: deepLink?.fallbackUrl ?? '',
    enabled: deepLink?.enabled ?? true,
    description: deepLink?.description ?? '',
  });

  const set = (key: keyof typeof form, value: any) =>
    setForm((f) => ({ ...f, [key]: value }));

  const extractParams = (pattern: string) => {
    const matches = pattern.match(/\{(\w+)\}/g) ?? [];
    return matches.map((m) => m.slice(1, -1));
  };

  const handlePatternChange = (v: string) => {
    set('pathPattern', v);
    set('paramMap', Object.fromEntries(extractParams(v).map((p) => [p, p])));
  };

  const handleSave = () => {
    if (!form.scheme || !form.pathPattern || !form.route) {
      toast.error('Scheme, pattern, and route are required');
      return;
    }
    onSave({
      scheme: form.scheme,
      pathPattern: form.pathPattern,
      route: form.route,
      paramMap: form.paramMap,
      fallbackUrl: form.fallbackUrl,
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
            <Label>Scheme</Label>
            <Input value={form.scheme} onChange={(e) => set('scheme', e.target.value)} placeholder="selfcare" />
          </div>
          <div>
            <Label>URL Pattern</Label>
            <Input
              value={form.pathPattern}
              onChange={(e) => handlePatternChange(e.target.value)}
              placeholder="selfcare://product/{id}"
            />
            <p className="text-xs text-gray-400 mt-1">
              Use {'{paramName}'} placeholders for dynamic segments. Supported schemes: selfcare://, dialoglk://, hutchlk://
            </p>
          </div>
          <div>
            <Label>Target Route</Label>
            <Input
              value={form.route}
              onChange={(e) => set('route', e.target.value)}
              placeholder="/product/{productId}"
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
            <Input value={Object.keys(form.paramMap ?? {}).join(', ')} disabled placeholder="id, productId, ..." />
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

// ─── Drawer item editor modal ───────────────────────────────────────────────────

interface DrawerModalProps {
  item?: DrawerItem;
  onClose: () => void;
  onSave: (data: Omit<DrawerItem, 'id'>) => void;
}

function DrawerModal({ item, onClose, onSave }: DrawerModalProps) {
  const [form, setForm] = useState<Omit<DrawerItem, 'id'>>({
    label: item?.label ?? '',
    icon: item?.icon ?? 'User',
    route: item?.route ?? '/',
    section: item?.section ?? 'general',
    requiresAuth: item?.requiresAuth ?? true,
    visibleWhen: item?.visibleWhen ?? {},
    order: item?.order ?? 0,
    enabled: item?.enabled ?? true,
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
          <DialogTitle>{item ? 'Edit Drawer Item' : 'Add Drawer Item'}</DialogTitle>
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
            <Input value={form.label} onChange={(e) => set('label', e.target.value)} placeholder="e.g. My Account" />
          </div>
          <div>
            <Label>Route (path)</Label>
            <Input value={form.route} onChange={(e) => set('route', e.target.value)} placeholder="/account" />
          </div>
          <div>
            <Label>Section</Label>
            <Input value={form.section} onChange={(e) => set('section', e.target.value)} placeholder="account" />
          </div>
          <div className="flex items-center gap-2">
            <input
              type="checkbox"
              id="drawer-auth"
              checked={form.requiresAuth}
              onChange={(e) => set('requiresAuth', e.target.checked)}
              className="w-4 h-4"
            />
            <Label htmlFor="drawer-auth" className="cursor-pointer">Requires authentication</Label>
          </div>
          <div className="flex items-center gap-2">
            <input
              type="checkbox"
              id="drawer-enabled"
              checked={form.enabled}
              onChange={(e) => set('enabled', e.target.checked)}
              className="w-4 h-4"
            />
            <Label htmlFor="drawer-enabled" className="cursor-pointer">Enabled (visible in menu)</Label>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button onClick={handleSave}>{item ? 'Update' : 'Add Drawer Item'}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// ─── Quick Action editor modal ──────────────────────────────────────────────────

interface QuickActionModalProps {
  item?: QuickAction;
  onClose: () => void;
  onSave: (data: Omit<QuickAction, 'id'>) => void;
}

function QuickActionModal({ item, onClose, onSave }: QuickActionModalProps) {
  const [form, setForm] = useState<Omit<QuickAction, 'id'>>({
    label: item?.label ?? '',
    icon: item?.icon ?? 'Zap',
    action: item?.action ?? { type: 'NAVIGATE', route: '/' },
    visibleWhen: item?.visibleWhen ?? {},
  });

  const set = (key: keyof typeof form, value: any) =>
    setForm((f) => ({ ...f, [key]: value }));

  const handleSave = () => {
    if (!form.label) {
      toast.error('Label is required');
      return;
    }
    onSave(form);
    onClose();
  };

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{item ? 'Edit Quick Action' : 'Add Quick Action'}</DialogTitle>
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
            <Input value={form.label} onChange={(e) => set('label', e.target.value)} placeholder="e.g. Recharge" />
          </div>
          <div>
            <Label>Action Type</Label>
            <Select value={form.action.type} onValueChange={(v) => set('action', { ...form.action, type: v as NavAction['type'] })}>
              <SelectTrigger className="w-full">
                <SelectValue placeholder="Select action type" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="NAVIGATE">Navigate</SelectItem>
                <SelectItem value="OPEN_WEB">Open Web</SelectItem>
                <SelectItem value="OPEN_WEB_SSO">Open Web (SSO)</SelectItem>
                <SelectItem value="START_JOURNEY">Start Journey</SelectItem>
                <SelectItem value="DEEP_LINK">Deep Link</SelectItem>
                <SelectItem value="MODAL">Open Modal</SelectItem>
                <SelectItem value="EXTERNAL_BROWSER">External Browser</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div>
            <Label>Route (for NAVIGATE/DEEP_LINK)</Label>
            <Input value={form.action.route ?? ''} onChange={(e) => set('action', { ...form.action, route: e.target.value })} placeholder="/recharge" />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button onClick={handleSave}>{item ? 'Update' : 'Add Quick Action'}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// ─── Route editor modal ──────────────────────────────────────────────────────────

interface RouteModalProps {
  item?: Route;
  onClose: () => void;
  onSave: (data: Omit<Route, 'id'>) => void;
}

function RouteModal({ item, onClose, onSave }: RouteModalProps) {
  const [form, setForm] = useState<Omit<Route, 'id'>>({
    path: item?.path ?? '/',
    screen: item?.screen ?? '',
    params: item?.params ?? [],
    requiresAuth: item?.requiresAuth ?? false,
    guards: item?.guards ?? [],
  });

  const set = (key: keyof typeof form, value: any) =>
    setForm((f) => ({ ...f, [key]: value }));

  const handleSave = () => {
    if (!form.path || !form.screen) {
      toast.error('Path and screen are required');
      return;
    }
    onSave(form);
    onClose();
  };

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{item ? 'Edit Route' : 'Add Route'}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <div>
            <Label>Path</Label>
            <Input value={form.path} onChange={(e) => set('path', e.target.value)} placeholder="/recharge" />
          </div>
          <div>
            <Label>Screen Component</Label>
            <Input value={form.screen} onChange={(e) => set('screen', e.target.value)} placeholder="RechargeScreen" />
          </div>
          <div>
            <Label>Requires Auth</Label>
            <Select value={(form.requiresAuth ?? false).toString()} onValueChange={(v) => set('requiresAuth', v === 'true')}>
              <SelectTrigger className="w-full"><SelectValue placeholder="Select" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="true">Yes</SelectItem>
                <SelectItem value="false">No</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button onClick={handleSave}>{item ? 'Update' : 'Add Route'}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// ─── Universal Link editor modal ──────────────────────────────────────────────────

interface UniversalLinkModalProps {
  item?: UniversalLink;
  onClose: () => void;
  onSave: (data: Omit<UniversalLink, 'id'>) => void;
}

function UniversalLinkModal({ item, onClose, onSave }: UniversalLinkModalProps) {
  const [form, setForm] = useState<Omit<UniversalLink, 'id'>>({
    host: item?.host ?? '',
    pathPattern: item?.pathPattern ?? '',
    route: item?.route ?? '/',
    paramMap: item?.paramMap ?? {},
  });

  const set = (key: keyof typeof form, value: any) =>
    setForm((f) => ({ ...f, [key]: value }));

  const handleSave = () => {
    if (!form.host || !form.pathPattern || !form.route) {
      toast.error('Host, path pattern, and route are required');
      return;
    }
    onSave(form);
    onClose();
  };

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{item ? 'Edit Universal Link' : 'Add Universal Link'}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <div>
            <Label>Host (e.g., dialog.selfcare.io)</Label>
            <Input value={form.host} onChange={(e) => set('host', e.target.value)} placeholder="dialog.selfcare.io" />
          </div>
          <div>
            <Label>Path Pattern (e.g., /app/* or {'/product/{id}'})</Label>
            <Input value={form.pathPattern} onChange={(e) => set('pathPattern', e.target.value)} placeholder="/app/*" />
          </div>
          <div>
            <Label>Target Route (e.g., /deep-link or {'/product/{id}'})</Label>
            <Input value={form.route} onChange={(e) => set('route', e.target.value)} placeholder="/deep-link" />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button onClick={handleSave}>{item ? 'Update' : 'Add Universal Link'}</Button>
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
  const queryClient = useQueryClient();
  const [saving, setSaving] = useState(false);

  // Fetch navigation config from API
  const { data: navConfig, isLoading, error } = useQuery({
    queryKey: ['navigation', activeTenantId],
    queryFn: () => api.navigation.get(activeTenantId!),
    enabled: !!activeTenantId,
    retry: 1,
  });

  // Initialize local state with API data or defaults
  const [config, setConfig] = useState<NavigationConfig>(() => {
    if (navConfig) return navConfig as NavigationConfig;
    return DEFAULT_CONFIG;
  });

  // Update local state when API data loads
  useEffect(() => {
    if (navConfig && !isLoading) {
      setConfig(navConfig as NavigationConfig);
    }
  }, [navConfig, isLoading]);

  // Mutation for saving navigation config
  const saveMutation = useMutation({
    mutationFn: (data: NavigationConfig) => api.navigation.save(data),
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ['navigation', activeTenantId] });
      setConfig(saved as NavigationConfig);
      toast.success('Navigation configuration saved');
      setSaving(false);
    },
    onError: (err: any) => {
      toast.error(err.message || 'Failed to save navigation config');
      setSaving(false);
    },
  });

  // Mutation for publishing
  const publishMutation = useMutation({
    mutationFn: (id: string) => api.navigation.publish(id),
    onSuccess: (published) => {
      queryClient.invalidateQueries({ queryKey: ['navigation', activeTenantId] });
      setConfig(published as NavigationConfig);
      toast.success('Navigation published');
    },
    onError: (err: any) => toast.error(err.message || 'Failed to publish'),
  });

  // Tab modal state
  const [tabModalOpen, setTabModalOpen] = useState(false);
  const [editingTab, setEditingTab] = useState<BottomTab | undefined>(undefined);

  // Deep link modal state
  const [dlModalOpen, setDlModalOpen] = useState(false);
  const [editingDl, setEditingDl] = useState<DeepLink | undefined>(undefined);

  // Allowed domain input
  const [newDomain, setNewDomain] = useState('');

  // Drawer items modal state
  const [drawerModalOpen, setDrawerModalOpen] = useState(false);
  const [editingDrawer, setEditingDrawer] = useState<DrawerItem | undefined>(undefined);

  // Quick actions modal state
  const [qaModalOpen, setQaModalOpen] = useState(false);
  const [editingQa, setEditingQa] = useState<QuickAction | undefined>(undefined);

  // Routes modal state
  const [routeModalOpen, setRouteModalOpen] = useState(false);
  const [editingRoute, setEditingRoute] = useState<Route | undefined>(undefined);

  // Universal links modal state
  const [ulModalOpen, setUlModalOpen] = useState(false);
  const [editingUl, setEditingUl] = useState<UniversalLink | undefined>(undefined);

  // ─── Save handler ─────────────────────────────────────────────────────────────

  const onSave = async () => {
    setSaving(true);
    try {
      await saveMutation.mutateAsync(config);
    } catch {
      // Error handled by mutation
    }
  };

  const onReset = () => {
    if (confirm('Reset to default navigation config? Unsaved changes will be lost.')) {
      setConfig(DEFAULT_CONFIG);
    }
  };

  // ─── Tab operations ───────────────────────────────────────────────────────

  const openAddTab = () => { setEditingTab(undefined); setTabModalOpen(true); };
  const openEditTab = (tab: BottomTab) => { setEditingTab(tab); setTabModalOpen(true); };

  const saveTab = (tabData: Omit<BottomTab, 'id'>) => {
    setConfig((c) => {
      const newTabs = editingTab
        ? c.tabs.map((t) => (t.id === editingTab.id ? { ...t, ...tabData } : t))
        : [...c.tabs, { ...tabData, id: `tab_${Date.now()}`, order: c.tabs.length }];
      return { ...c, tabs: newTabs };
    });
    toast.success(editingTab ? 'Tab updated' : 'Tab added');
    setTabModalOpen(false);
    setEditingTab(undefined);
  };

  const deleteTab = (id: string) => {
    setConfig((c) => ({ ...c, tabs: c.tabs.filter((t) => t.id !== id) }));
    toast.success('Tab removed');
  };

  const moveTab = (id: string, direction: 'up' | 'down') => {
    setConfig((c) => {
      const sorted = [...c.tabs].sort((a, b) => a.order - b.order);
      const idx = sorted.findIndex((t) => t.id === id);
      const swapIdx = direction === 'up' ? idx - 1 : idx + 1;
      if (swapIdx < 0 || swapIdx >= sorted.length) return c;
      const updated = sorted.map((t, i) => {
        if (i === idx) return { ...t, order: sorted[swapIdx].order };
        if (i === swapIdx) return { ...t, order: sorted[idx].order };
        return t;
      });
      return { ...c, tabs: updated };
    });
  };

  // ─── Drawer operations ──────────────────────────────────────────────────────
  const openAddDrawer = () => { setEditingDrawer(undefined); setDrawerModalOpen(true); };
  const openEditDrawer = (item: DrawerItem) => { setEditingDrawer(item); setDrawerModalOpen(true); };

  const saveDrawer = (data: Omit<DrawerItem, 'id'>) => {
    setConfig((c) => {
      const items = c.drawerItems ?? [];
      const newItems = editingDrawer
        ? items.map((t) => (t.id === editingDrawer.id ? { ...t, ...data } : t))
        : [...items, { ...data, id: `drawer_${Date.now()}`, order: items.length }];
      return { ...c, drawerItems: newItems };
    });
    toast.success(editingDrawer ? 'Drawer item updated' : 'Drawer item added');
    setDrawerModalOpen(false);
    setEditingDrawer(undefined);
  };

  const deleteDrawer = (id: string) => {
    setConfig((c) => ({ ...c, drawerItems: (c.drawerItems ?? []).filter((t) => t.id !== id) }));
    toast.success('Drawer item removed');
  };

  // ─── Quick Action operations ────────────────────────────────────────────────
  const openAddQa = () => { setEditingQa(undefined); setQaModalOpen(true); };
  const openEditQa = (item: QuickAction) => { setEditingQa(item); setQaModalOpen(true); };

  const saveQa = (data: Omit<QuickAction, 'id'>) => {
    setConfig((c) => {
      const items = c.quickActions ?? [];
      const newItems = editingQa
        ? items.map((t) => (t.id === editingQa.id ? { ...t, ...data } : t))
        : [...items, { ...data, id: `qa_${Date.now()}` }];
      return { ...c, quickActions: newItems };
    });
    toast.success(editingQa ? 'Quick action updated' : 'Quick action added');
    setQaModalOpen(false);
    setEditingQa(undefined);
  };

  const deleteQa = (id: string) => {
    setConfig((c) => ({ ...c, quickActions: (c.quickActions ?? []).filter((t) => t.id !== id) }));
    toast.success('Quick action removed');
  };

  // ─── Route operations ──────────────────────────────────────────────────────
  const openAddRoute = () => { setEditingRoute(undefined); setRouteModalOpen(true); };
  const openEditRoute = (item: Route) => { setEditingRoute(item); setRouteModalOpen(true); };

  const saveRoute = (data: Omit<Route, 'id'>) => {
    setConfig((c) => {
      const items = c.routes ?? [];
      const newItems = editingRoute
        ? items.map((t) => (t.id === editingRoute.id ? { ...t, ...data } : t))
        : [...items, { ...data, id: `route_${Date.now()}` }];
      return { ...c, routes: newItems };
    });
    toast.success(editingRoute ? 'Route updated' : 'Route added');
    setRouteModalOpen(false);
    setEditingRoute(undefined);
  };

  const deleteRoute = (id: string) => {
    setConfig((c) => ({ ...c, routes: (c.routes ?? []).filter((t) => t.id !== id) }));
    toast.success('Route removed');
  };

  // ─── Universal Link operations ──────────────────────────────────────────────
  const openAddUl = () => { setEditingUl(undefined); setUlModalOpen(true); };
  const openEditUl = (item: UniversalLink) => { setEditingUl(item); setUlModalOpen(true); };

  const saveUl = (data: Omit<UniversalLink, 'id'>) => {
    setConfig((c) => {
      const items = c.universalLinks?.universalLinks ?? [];
      const newItems = editingUl
        ? items.map((t) => (t.id === editingUl.id ? { ...t, ...data } : t))
        : [...items, { ...data, id: `ul_${Date.now()}` }];
      return { ...c, universalLinks: { ...c.universalLinks, universalLinks: newItems, enabled: c.universalLinks?.enabled ?? true } };
    });
    toast.success(editingUl ? 'Universal link updated' : 'Universal link added');
    setUlModalOpen(false);
    setEditingUl(undefined);
  };

  const deleteUl = (id: string) => {
    setConfig((c) => ({
      ...c,
      universalLinks: { ...c.universalLinks, universalLinks: (c.universalLinks?.universalLinks ?? []).filter((t) => t.id !== id), enabled: c.universalLinks?.enabled ?? true },
    }));
    toast.success('Universal link removed');
  };

  // ─── Deep link operations ─────────────────────────────────────────────────

  const openAddDl = () => { setEditingDl(undefined); setDlModalOpen(true); };
  const openEditDl = (dl: DeepLink) => { setEditingDl(dl); setDlModalOpen(true); };

  const saveDeepLink = (dlData: Omit<DeepLink, 'id'>) => {
    setConfig((c) => {
      const newDls = editingDl
        ? (c.deepLinks ?? []).map((d) => (d.id === editingDl.id ? { ...d, ...dlData } : d))
        : [...(c.deepLinks ?? []), { ...dlData, id: `dl_${Date.now()}` }];
      return { ...c, deepLinks: newDls };
    });
    toast.success(editingDl ? 'Deep link updated' : 'Deep link added');
    setDlModalOpen(false);
    setEditingDl(undefined);
  };

  const deleteDeepLink = (id: string) => {
    setConfig((c) => ({ ...c, deepLinks: (c.deepLinks ?? []).filter((d) => d.id !== id) }));
    toast.success('Deep link removed');
  };

  // ─── Domain operations ─────────────────────────────────────────────────────

  const addDomain = () => {
    const domain = newDomain.trim();
    if (!domain) return;
    if ((config.allowedDomains ?? []).includes(domain)) {
      toast.warning('Domain already in the list');
      return;
    }
    setConfig((c) => ({ ...c, allowedDomains: [...(c.allowedDomains ?? []), domain] }));
    setNewDomain('');
    toast.success(`Domain added: ${domain}`);
  };

const removeDomain = (domain: string) => {
    setConfig((c) => ({ ...c, allowedDomains: (c.allowedDomains ?? []).filter((d) => d !== domain) }));
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <Navigation className="w-6 h-6" />
            Navigation & Deep Links
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
          <Button onClick={onSave} disabled={saving || saveMutation.isPending}>
            <Save className="w-4 h-4 mr-2" />
            {saveMutation.isPending ? 'Saving…' : 'Save Config'}
          </Button>
        </div>
      </div>

      <Tabs defaultValue="tabs">
        <TabsList>
          <TabsTrigger value="tabs">
            <Smartphone className="w-4 h-4 mr-1.5" /> Bottom Tabs
          </TabsTrigger>
          <TabsTrigger value="drawer">
            <Menu className="w-4 h-4 mr-1.5" /> Drawer Menu
          </TabsTrigger>
          <TabsTrigger value="quickactions">
            <Zap className="w-4 h-4 mr-1.5" /> Quick Actions
          </TabsTrigger>
          <TabsTrigger value="routes">
            <Layout className="w-4 h-4 mr-1.5" /> Routes
          </TabsTrigger>
          <TabsTrigger value="deeplinks">
            <Link2 className="w-4 h-4 mr-1.5" /> Deep Links
          </TabsTrigger>
          <TabsTrigger value="universal">
            <Globe className="w-4 h-4 mr-1.5" /> Universal Links
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
              <TabBarPreview tabs={config.tabs} />
            </CardContent>
          </Card>

          {/* Tabs list */}
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <div>
                <CardTitle>Tab Items</CardTitle>
                <CardDescription>
                  {config.tabs.length} tab(s) configured — drag to reorder
                </CardDescription>
              </div>
              <Button size="sm" onClick={openAddTab}>
                <Plus className="w-4 h-4 mr-2" /> Add Tab
              </Button>
            </CardHeader>
            <CardContent className="p-0">
              <div className="divide-y">
                {[...config.tabs]
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
                {config.tabs.length === 0 && (
                  <div className="text-center py-8 text-gray-500 text-sm">
                    No tabs configured. Click "Add Tab" to start.
                  </div>
                )}
              </div>
            </CardContent>
          </Card>
</TabsContent>

        {/* ── Drawer Menu ───────────────────────────────────────────────────── */}
        <TabsContent value="drawer" className="space-y-6">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <div>
                <CardTitle>Drawer Menu Items</CardTitle>
                <CardDescription>Side navigation / hamburger menu items</CardDescription>
              </div>
              <Button size="sm" onClick={openAddDrawer}>
                <Plus className="w-4 h-4 mr-2" /> Add Item
              </Button>
            </CardHeader>
            <CardContent className="p-0">
              <div className="divide-y">
                {(config.drawerItems ?? []).map((item) => (
                  <div
                    key={item.id}
                    className={cn(
                      'flex items-center gap-3 px-4 py-3 hover:bg-gray-50 transition-colors',
                      !item.enabled && 'opacity-50'
                    )}
                  >
                    <GripVertical className="w-4 h-4 text-gray-300 flex-shrink-0" />
                    {getLucideIcon(item.icon, 'w-5 h-5 text-gray-600 flex-shrink-0')}
                    <div className="flex-1 min-w-0">
                      <div className="font-medium text-sm">{item.label}</div>
                      <div className="text-xs text-gray-400 font-mono">{item.route}</div>
                    </div>
                    <span className="text-xs text-gray-400">Section: {item.section}</span>
                    <Badge variant={item.requiresAuth ? 'default' : 'secondary'}>
                      {item.requiresAuth ? 'Auth Required' : 'Public'}
                    </Badge>
                    <div className="flex items-center gap-1">
                      <Button size="sm" variant="ghost" onClick={() => openEditDrawer(item)}>Edit</Button>
                      <Button size="sm" variant="ghost" className="text-red-600" onClick={() => deleteDrawer(item.id)}>
                        <Trash2 className="w-3 h-3" />
                      </Button>
                    </div>
                  </div>
                ))}
                {(config.drawerItems ?? []).length === 0 && (
                  <div className="text-center py-8 text-gray-500 text-sm">
                    No drawer items configured. Click "Add Item" to start.
                  </div>
                )}
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        {/* ── Quick Actions ───────────────────────────────────────────────────── */}
        <TabsContent value="quickactions" className="space-y-6">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <div>
                <CardTitle>Quick Actions</CardTitle>
                <CardDescription>Floating action buttons and shortcuts</CardDescription>
              </div>
              <Button size="sm" onClick={openAddQa}>
                <Plus className="w-4 h-4 mr-2" /> Add Action
              </Button>
            </CardHeader>
            <CardContent className="p-0">
              <div className="divide-y">
                {(config.quickActions ?? []).map((qa) => (
                  <div
                    key={qa.id}
                    className={cn('flex items-center gap-3 px-4 py-3 hover:bg-gray-50 transition-colors')}
                  >
                    <Zap className="w-4 h-4 text-gray-400 flex-shrink-0" />
                    {getLucideIcon(qa.icon, 'w-5 h-5 text-gray-600 flex-shrink-0')}
                    <div className="flex-1 min-w-0">
                      <div className="font-medium text-sm">{qa.label}</div>
                      <div className="text-xs text-gray-400">Action: {qa.action.type}</div>
                    </div>
                    <div className="flex items-center gap-1">
                      <Button size="sm" variant="ghost" onClick={() => openEditQa(qa)}>Edit</Button>
                      <Button size="sm" variant="ghost" className="text-red-600" onClick={() => deleteQa(qa.id)}>
                        <Trash2 className="w-3 h-3" />
                      </Button>
                    </div>
                  </div>
                ))}
                {(config.quickActions ?? []).length === 0 && (
                  <div className="text-center py-8 text-gray-500 text-sm">
                    No quick actions configured. Click "Add Action" to start.
                  </div>
                )}
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        {/* ── Routes ───────────────────────────────────────────────────── */}
        <TabsContent value="routes" className="space-y-6">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <div>
                <CardTitle>Canonical Routes</CardTitle>
                <CardDescription>In-app route table (path → screen/component)</CardDescription>
              </div>
              <Button size="sm" onClick={openAddRoute}>
                <Plus className="w-4 h-4 mr-2" /> Add Route
              </Button>
            </CardHeader>
            <CardContent className="p-0">
              <div className="divide-y">
                {(config.routes ?? []).map((route) => (
                  <div
                    key={route.id}
                    className={cn('flex items-center gap-3 px-4 py-3 hover:bg-gray-50 transition-colors')}
                  >
                    <Layout className="w-4 h-4 text-gray-400 flex-shrink-0" />
                    <div className="flex-1 min-w-0">
                      <div className="font-medium text-sm">{route.path}</div>
                      <div className="text-xs text-gray-400 font-mono">{route.screen}</div>
                    </div>
                    <Badge variant="outline">{route.requiresAuth ? 'Auth' : 'Public'}</Badge>
                    <div className="flex items-center gap-1">
                      <Button size="sm" variant="ghost" onClick={() => openEditRoute(route)}>Edit</Button>
                      <Button size="sm" variant="ghost" className="text-red-600" onClick={() => deleteRoute(route.id)}>
                        <Trash2 className="w-3 h-3" />
                      </Button>
                    </div>
                  </div>
                ))}
                {(config.routes ?? []).length === 0 && (
                  <div className="text-center py-8 text-gray-500 text-sm">
                    No routes configured. Click "Add Route" to start.
                  </div>
                )}
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        {/* ── Deep Links ───────────────────────────────────────────────────── */}
        <TabsContent value="deeplinks" className="space-y-6">
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
                {(config.deepLinks ?? []).map((dl) => (
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
                          {dl.scheme}://{dl.pathPattern}
                        </code>
                        <span className="text-gray-300">→</span>
                        <Badge variant="outline" className="text-xs">
                          {dl.route}
                        </Badge>
                        {dl.paramMap && Object.keys(dl.paramMap).length > 0 && (
                          <span className="text-xs text-gray-400">
                            params: {Object.entries(dl.paramMap).map(([k,v]) => `${k}={${v}}`).join(', ')}
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
                      <Button size="sm" variant="ghost" onClick={() => openEditDl(dl)}>Edit</Button>
                      <Button size="sm" variant="ghost" className="text-red-600" onClick={() => deleteDeepLink(dl.id)}>
                        <Trash2 className="w-3 h-3" />
                      </Button>
                    </div>
                  </div>
                ))}
                {(config.deepLinks ?? []).length === 0 && (
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
                See the <strong>selfcare App Manifest</strong> template for details.
              </p>
            </CardContent>
          </Card>
        </TabsContent>

        {/* ── Universal Links ───────────────────────────────────────────────────── */}
        <TabsContent value="universal" className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Globe className="w-5 h-5" /> Universal Links & App Links
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
                  checked={config.universalLinks?.enabled ?? false}
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
              {(config.universalLinks?.enabled ?? false) && (
                <>
                  <div>
                    <Label>Associated Domains (Apple App Links / Android App Links)</Label>
                    <div className="mt-1 space-y-1">
                      {(config.universalLinks?.associatedDomains ?? []).map((d) => (
                        <div key={d} className="flex items-center gap-2">
                          <Globe className="w-4 h-4 text-gray-400 flex-shrink-0" />
                          <code className="flex-1 bg-gray-100 px-2 py-1 rounded text-xs">{d}</code>
                          <Button size="icon" variant="ghost" className="text-red-600" onClick={() => {
                            setConfig((c) => ({
                              ...c,
                              universalLinks: {
                                ...c.universalLinks,
                                associatedDomains: (c.universalLinks?.associatedDomains ?? []).filter(x => x !== d)
                              }
                            }));
                          }}>
                            <X className="w-3 h-3" />
                          </Button>
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
                      {(config.allowedDomains ?? []).map((d) => (
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
                      {(config.allowedDomains ?? []).length === 0 && (
                        <p className="text-sm text-gray-400 italic">No domains added yet</p>
                      )}
                    </div>
                  </div>
                </>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <div>
                <CardTitle>Universal Link Routes</CardTitle>
                <CardDescription>HTTPS universal link mappings to in-app routes</CardDescription>
              </div>
              <Button size="sm" onClick={openAddUl}>
                <Plus className="w-4 h-4 mr-2" /> Add Universal Link
              </Button>
            </CardHeader>
            <CardContent className="p-0">
              <div className="divide-y">
                {(config.universalLinks?.universalLinks ?? []).map((ul) => (
                  <div
                    key={ul.id}
                    className={cn('flex items-center gap-3 px-4 py-3 hover:bg-gray-50 transition-colors')}
                  >
                    <Globe className="w-4 h-4 text-gray-400 flex-shrink-0" />
                    <div className="flex-1 min-w-0">
                      <div className="font-medium text-sm">{ul.host}</div>
                      <div className="text-xs text-gray-400">{ul.pathPattern} → {ul.route}</div>
                    </div>
                    <div className="flex items-center gap-1">
                      <Button size="sm" variant="ghost" onClick={() => openEditUl(ul)}>Edit</Button>
                      <Button size="sm" variant="ghost" className="text-red-600" onClick={() => {
                        setConfig((c) => ({
                          ...c,
                          universalLinks: {
                            ...c.universalLinks,
                            universalLinks: (c.universalLinks?.universalLinks ?? []).filter(x => x.id !== ul.id)
                          }
                        }));
                      }}>
                        <Trash2 className="w-3 h-3" />
                      </Button>
                    </div>
                  </div>
                ))}
                {(config.universalLinks?.universalLinks ?? []).length === 0 && (
                  <div className="text-center py-8 text-gray-500 text-sm">
                    No universal links configured. Click "Add Universal Link" to start.
                  </div>
                )}
              </div>
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
      {drawerModalOpen && (
        <DrawerModal
          item={editingDrawer}
          onClose={() => setDrawerModalOpen(false)}
          onSave={saveDrawer}
        />
      )}
      {qaModalOpen && (
        <QuickActionModal
          item={editingQa}
          onClose={() => setQaModalOpen(false)}
          onSave={saveQa}
        />
      )}
      {routeModalOpen && (
        <RouteModal
          item={editingRoute}
          onClose={() => setRouteModalOpen(false)}
          onSave={saveRoute}
        />
      )}
      {ulModalOpen && (
        <UniversalLinkModal
          item={editingUl}
          onClose={() => setUlModalOpen(false)}
          onSave={saveUl}
        />
      )}
    </div>
  );
}
