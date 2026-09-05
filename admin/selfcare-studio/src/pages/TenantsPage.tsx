import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Card, CardHeader, CardTitle, CardContent, CardDescription } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import {
  Tabs,
  TabsList,
  TabsTrigger,
  TabsContent,
} from '@/components/ui/tabs';
import {
  Building2,
  Globe,
  Users,
  Calendar,
  Plus,
  Briefcase,
  Phone,
  Shield,
  Plane,
  Banknote,
  Server,
  Activity,
  AlertTriangle,
  Smartphone,
  Rocket,
  Coins,
  Clock,
  Settings2,
  ChevronDown,
  ChevronRight,
  RefreshCw,
  Languages,
  Flag,
} from 'lucide-react';
import { toast } from 'sonner';
import { useActiveTenant } from '@/hooks/useActiveTenant';
import { api } from '@/lib/api';

/**
 * Tenants / Clients — manage the businesses using the OMOBIO platform.
 *
 * Each tenant has:
 *   - tenantId: stable ID (e.g. "dialog-lk", "aia-lk")
 *   - name / brand: display name
 *   - industry: TELCO | INSURANCE | TRAVEL | BANKING | ...
 *   - tenantType: OPERATOR | INSURER | TRAVEL_COMPANY | BANK | ...
 *   - country: ISO 3166-1 alpha-2
 *   - supportedLobs: lines of business (terminology varies by industry)
 *   - industryPack: which industry pack the tenant uses
 *
 * The active tenant dropdown in the header is the source of context for
 * most other admin pages (theme, integrations, journeys, etc.).
 */

// --- Types for the extended per-tenant configuration model ---

interface Environment {
  name: 'dev' | 'stg' | 'reg' | 'prod';
  cluster: string;
  region: string;
  apiBaseUrl: string;
  configServiceUrl: string;
  configVersion: string;
  status: 'healthy' | 'degraded' | 'down';
  lastChecked: string;
}

interface AppCompatibility {
  channel: 'stable' | 'beta' | 'preview';
  rolloutPct: number;
  iosMin: string;
  iosMax: string;
  androidMin: string;
  androidMax: string;
  huaweiMin: string;
  huaweiMax: string;
  features: string[]; // feature flag keys enabled on this channel
}

interface ReleaseChannel {
  name: 'stable' | 'beta' | 'preview';
  enabled: boolean;
  rolloutPct: number;
  abCohorts: { name: string; pct: number; variant: string }[];
}

interface LocaleConfig {
  defaultLocale: string;   // BCP-47 (e.g. en-LK, si-LK, ta-LK)
  supportedLocales: string[];
  defaultCurrency: string;  // ISO 4217 (e.g. LKR, USD)
  currencySymbol: string;
  timezone: string;         // IANA (e.g. Asia/Colombo)
  dateFormat: string;
}

interface TenantFeatureEntitlement {
  key: string;
  label: string;
  enabled: boolean;
  description: string;
}

// Demo data is shown when no API is configured so the screen is never blank.
const DEMO_ENVIRONMENTS: Environment[] = [
  {
    name: 'dev',
    cluster: 'k8s-selfcare-dev',
    region: 'ap-south-1',
    apiBaseUrl: 'https://api-dev.dialog.omobio.local',
    configServiceUrl: 'https://config-dev.dialog.omobio.local',
    configVersion: '2026.09.02-r4',
    status: 'healthy',
    lastChecked: '2 min ago',
  },
  {
    name: 'stg',
    cluster: 'k8s-selfcare-stg',
    region: 'ap-south-1',
    apiBaseUrl: 'https://api-stg.dialog.omobio.local',
    configServiceUrl: 'https://config-stg.dialog.omobio.local',
    configVersion: '2026.09.01-r2',
    status: 'healthy',
    lastChecked: '5 min ago',
  },
  {
    name: 'reg',
    cluster: 'k8s-selfcare-reg',
    region: 'ap-south-1',
    apiBaseUrl: 'https://api-reg.dialog.omobio.local',
    configServiceUrl: 'https://config-reg.dialog.omobio.local',
    configVersion: '2026.08.28-r7',
    status: 'degraded',
    lastChecked: '11 min ago',
  },
  {
    name: 'prod',
    cluster: 'k8s-selfcare-prod',
    region: 'ap-south-1',
    apiBaseUrl: 'https://api.dialog.omobio.com',
    configServiceUrl: 'https://config.dialog.omobio.com',
    configVersion: '2026.08.25-r12',
    status: 'healthy',
    lastChecked: '30 sec ago',
  },
];

const DEMO_COMPATIBILITY: AppCompatibility[] = [
  {
    channel: 'stable',
    rolloutPct: 95,
    iosMin: '14.0',
    iosMax: '17.6',
    androidMin: '8.0',
    androidMax: '14',
    huaweiMin: '10.0',
    huaweiMax: '12',
    features: ['billing.v2', 'support.chat', 'esim.activation'],
  },
  {
    channel: 'beta',
    rolloutPct: 4,
    iosMin: '15.0',
    iosMax: '17.6',
    androidMin: '9.0',
    androidMax: '14',
    huaweiMin: '11.0',
    huaweiMax: '12',
    features: ['billing.v2', 'support.chat', 'esim.activation', 'ai.assistant'],
  },
  {
    channel: 'preview',
    rolloutPct: 1,
    iosMin: '16.0',
    iosMax: '17.6',
    androidMin: '10.0',
    androidMax: '14',
    huaweiMin: '12.0',
    huaweiMax: '12',
    features: ['billing.v3-preview', 'ai.assistant', 'wearable.companion'],
  },
];

const DEMO_CHANNELS: ReleaseChannel[] = [
  {
    name: 'stable',
    enabled: true,
    rolloutPct: 95,
    abCohorts: [
      { name: 'control', pct: 50, variant: 'A' },
      { name: 'treatment', pct: 50, variant: 'B' },
    ],
  },
  {
    name: 'beta',
    enabled: true,
    rolloutPct: 4,
    abCohorts: [
      { name: 'beta-optin', pct: 100, variant: 'A' },
    ],
  },
  {
    name: 'preview',
    enabled: true,
    rolloutPct: 1,
    abCohorts: [
      { name: 'internal-employees', pct: 60, variant: 'A' },
      { name: 'qa-team', pct: 40, variant: 'A' },
    ],
  },
];

const DEMO_LOCALE: LocaleConfig = {
  defaultLocale: 'en-LK',
  supportedLocales: ['en-LK', 'si-LK', 'ta-LK'],
  defaultCurrency: 'LKR',
  currencySymbol: 'Rs.',
  timezone: 'Asia/Colombo',
  dateFormat: 'DD/MM/YYYY',
};

const DEMO_FEATURES: TenantFeatureEntitlement[] = [
  { key: 'billing.v2',         label: 'Billing v2 Engine',        enabled: true,  description: 'New bill rendering and PDF pipeline.' },
  { key: 'support.chat',       label: 'In-App Live Chat',         enabled: true,  description: 'Omobio support chat widget.' },
  { key: 'esim.activation',    label: 'eSIM Self-Activation',     enabled: true,  description: 'Allow customers to activate eSIMs without a store visit.' },
  { key: 'ai.assistant',       label: 'AI Assistant (Beta)',      enabled: false, description: 'Conversational AI helper for FAQs and journeys.' },
  { key: 'wearable.companion', label: 'Wearable Companion App',   enabled: false, description: 'Apple Watch / Wear OS companion view of usage.' },
  { key: 'family.sharing',     label: 'Family Plan Sharing',      enabled: false, description: 'Share allowances across linked accounts.' },
  { key: 'kyc.video',          label: 'Video KYC',                enabled: false, description: 'Video-based KYC flow during onboarding.' },
];

const DEMO_LOCALE_BY_INDUSTRY: Record<string, LocaleConfig> = {
  TELCO:     { defaultLocale: 'en-LK', supportedLocales: ['en-LK', 'si-LK', 'ta-LK'], defaultCurrency: 'LKR', currencySymbol: 'Rs.', timezone: 'Asia/Colombo', dateFormat: 'DD/MM/YYYY' },
  INSURANCE: { defaultLocale: 'en-LK', supportedLocales: ['en-LK', 'si-LK', 'ta-LK'], defaultCurrency: 'LKR', currencySymbol: 'Rs.', timezone: 'Asia/Colombo', dateFormat: 'DD/MM/YYYY' },
  TRAVEL:    { defaultLocale: 'en-US', supportedLocales: ['en-US', 'en-GB', 'fr-FR', 'ja-JP'], defaultCurrency: 'USD', currencySymbol: '$',  timezone: 'America/New_York', dateFormat: 'MM/DD/YYYY' },
  BANKING:   { defaultLocale: 'en-LK', supportedLocales: ['en-LK', 'si-LK', 'ta-LK'], defaultCurrency: 'LKR', currencySymbol: 'Rs.', timezone: 'Asia/Colombo', dateFormat: 'DD/MM/YYYY' },
};

// --- Demo tenants (used only when the API is unavailable) ---

const DEMO_TENANTS = [
  {
    tenantId: 'dialog-lk',
    name: 'Dialog Axiata',
    industry: 'TELCO',
    tenantType: 'OPERATOR',
    country: 'Sri Lanka',
    status: 'ACTIVE',
    supportedLobs: ['mobile', 'broadband', 'tv'],
    users: 1240000,
    plan: 'Enterprise',
    created: '2024-01-15',
    environments: DEMO_ENVIRONMENTS,
    compatibility: DEMO_COMPATIBILITY,
    channels: DEMO_CHANNELS,
    locale: DEMO_LOCALE_BY_INDUSTRY.TELCO,
    features: DEMO_FEATURES,
  },
  {
    tenantId: 'aia-lk',
    name: 'AIA Insurance',
    industry: 'INSURANCE',
    tenantType: 'INSURER',
    country: 'Sri Lanka',
    status: 'ACTIVE',
    supportedLobs: ['life', 'health', 'motor', 'education'],
    users: 380000,
    plan: 'Enterprise',
    created: '2024-06-10',
    environments: DEMO_ENVIRONMENTS,
    compatibility: DEMO_COMPATIBILITY,
    channels: DEMO_CHANNELS,
    locale: DEMO_LOCALE_BY_INDUSTRY.INSURANCE,
    features: DEMO_FEATURES,
  },
  {
    tenantId: 'hutch-lk',
    name: 'Hutch Sri Lanka',
    industry: 'TELCO',
    tenantType: 'OPERATOR',
    country: 'Sri Lanka',
    status: 'ACTIVE',
    supportedLobs: ['mobile'],
    users: 580000,
    plan: 'Enterprise',
    created: '2024-03-22',
    environments: DEMO_ENVIRONMENTS,
    compatibility: DEMO_COMPATIBILITY,
    channels: DEMO_CHANNELS,
    locale: DEMO_LOCALE_BY_INDUSTRY.TELCO,
    features: DEMO_FEATURES,
  },
  {
    tenantId: 'airtel-lk',
    name: 'Airtel Lanka',
    industry: 'TELCO',
    tenantType: 'OPERATOR',
    country: 'Sri Lanka',
    status: 'TRIAL',
    supportedLobs: ['mobile'],
    users: 12000,
    plan: 'Trial',
    created: '2026-08-01',
    environments: DEMO_ENVIRONMENTS,
    compatibility: DEMO_COMPATIBILITY,
    channels: DEMO_CHANNELS,
    locale: DEMO_LOCALE_BY_INDUSTRY.TELCO,
    features: DEMO_FEATURES,
  },
] as any[];

export default function TenantsPage() {
  const { tenants, activeTenantId, setActiveTenantId } = useActiveTenant();
  const queryClient = useQueryClient();
  const [showAdd, setShowAdd] = useState(false);
  const [expandedTenantId, setExpandedTenantId] = useState<string | null>(null);

  const isUsingDemo = !tenants || tenants.length === 0;
  const all = isUsingDemo ? DEMO_TENANTS : tenants;

  const handleReload = () => {
    queryClient.invalidateQueries({ queryKey: ['tenants'] });
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <Building2 className="w-6 h-6" />
            Tenants / Clients
          </h1>
          <p className="text-gray-500 mt-1">
            Businesses using the OMOBIO platform across industries and countries.
            Configure environments, release channels, app compatibility and feature
            entitlements per tenant.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="icon" onClick={handleReload} title="Reload tenants">
            <RefreshCw className="w-4 h-4" />
          </Button>
          <Button onClick={() => setShowAdd(!showAdd)}>
            <Plus className="w-4 h-4 mr-2" /> Add Client
          </Button>
        </div>
      </div>

      {isUsingDemo && (
        <div className="rounded border border-amber-300 bg-amber-50 text-amber-900 text-sm p-3 flex items-center gap-2">
          <AlertTriangle className="w-4 h-4" />
          <span>
            <strong>Demo data.</strong> The tenant API is not reachable, so this view is
            showing built-in example tenants. The data below will be replaced with live
            data once <code>api.tenants.list()</code> returns a result.
          </span>
        </div>
      )}

      {showAdd && (
        <AddClientForm
          onDone={() => setShowAdd(false)}
          onCreated={() => {
            handleReload();
            setShowAdd(false);
          }}
        />
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {all.map((t) => {
          const isExpanded = expandedTenantId === t.tenantId;
          const isActive = activeTenantId === t.tenantId;
          return (
            <Card key={t.tenantId} className={isActive ? 'ring-2 ring-blue-400' : ''}>
              <CardHeader>
                <div className="flex items-start justify-between">
                  <div>
                    <CardTitle className="flex items-center gap-2">
                      <IndustryIcon industry={t.industry} />
                      {t.name || t.tenantId}
                    </CardTitle>
                    <CardDescription className="flex items-center gap-1 mt-1">
                      <Globe className="w-3 h-3" /> {t.country}
                      <span className="mx-1">·</span>
                      <code className="text-xs">{t.tenantId}</code>
                    </CardDescription>
                  </div>
                  <div className="flex flex-col items-end gap-1">
                    <Badge variant={t.status === 'ACTIVE' ? 'default' : 'secondary'}>
                      {t.status}
                    </Badge>
                    {isActive && <Badge variant="outline" className="text-blue-700 border-blue-300">Active</Badge>}
                  </div>
                </div>
              </CardHeader>
              <CardContent className="space-y-2 text-sm">
                <div className="flex flex-wrap gap-1">
                  <IndustryBadge industry={t.industry} />
                  <TenantTypeBadge type={t.tenantType} />
                </div>
                {t.supportedLobs && t.supportedLobs.length > 0 && (
                  <div>
                    <span className="text-gray-500">Lines of business:</span>{' '}
                    {t.supportedLobs.join(', ')}
                  </div>
                )}
                {t.users && (
                  <div className="flex items-center gap-2 text-gray-600">
                    <Users className="w-4 h-4" /> {t.users.toLocaleString()} users
                  </div>
                )}
                {t.created && (
                  <div className="flex items-center gap-2 text-gray-600">
                    <Calendar className="w-4 h-4" /> Since {t.created}
                  </div>
                )}

                <div className="flex gap-2 mt-2">
                  {!isActive && (
                    <Button
                      variant="outline"
                      size="sm"
                      className="flex-1"
                      onClick={() => setActiveTenantId(t.tenantId)}
                    >
                      Set Active
                    </Button>
                  )}
                  <Button
                    variant="outline"
                    size="sm"
                    className="flex-1"
                    onClick={() =>
                      setExpandedTenantId(isExpanded ? null : t.tenantId)
                    }
                  >
                    {isExpanded ? <ChevronDown className="w-4 h-4 mr-1" /> : <ChevronRight className="w-4 h-4 mr-1" />}
                    Configure
                  </Button>
                </div>

                {isExpanded && (
                  <TenantConfigPanel tenant={t} isDemo={isUsingDemo} />
                )}
              </CardContent>
            </Card>
          );
        })}
      </div>
    </div>
  );
}

// -------------------------------------------------------------------
// Configuration panel (drawer-style, rendered inline when expanded)
// -------------------------------------------------------------------

function TenantConfigPanel({ tenant, isDemo }: { tenant: any; isDemo: boolean }) {
  // Read extended sub-resources from the API. Falls back to demo data
  // when running in demo mode OR when the API doesn't expose them.
  const environments = useTenantEnvironments(tenant.tenantId, isDemo);
  const compatibility = useTenantCompatibility(tenant.tenantId, isDemo);
  const channels = useTenantChannels(tenant.tenantId, isDemo);
  const locale = useTenantLocale(tenant.tenantId, tenant.industry, isDemo);
  const features = useTenantFeatures(tenant.tenantId, isDemo);

  return (
    <div className="mt-3 border-t pt-3">
      <Tabs defaultValue="environments" className="w-full">
        <TabsList className="grid grid-cols-5 w-full">
          <TabsTrigger value="environments" className="text-xs">
            <Server className="w-3 h-3 mr-1" /> Envs
          </TabsTrigger>
          <TabsTrigger value="compatibility" className="text-xs">
            <Smartphone className="w-3 h-3 mr-1" /> Apps
          </TabsTrigger>
          <TabsTrigger value="channels" className="text-xs">
            <Rocket className="w-3 h-3 mr-1" /> Releases
          </TabsTrigger>
          <TabsTrigger value="locale" className="text-xs">
            <Languages className="w-3 h-3 mr-1" /> Locale
          </TabsTrigger>
          <TabsTrigger value="features" className="text-xs">
            <Flag className="w-3 h-3 mr-1" /> Features
          </TabsTrigger>
        </TabsList>

        <TabsContent value="environments" className="mt-3">
          <EnvironmentsList data={environments} isDemo={isDemo} />
        </TabsContent>
        <TabsContent value="compatibility" className="mt-3">
          <CompatibilityMatrix data={compatibility} isDemo={isDemo} />
        </TabsContent>
        <TabsContent value="channels" className="mt-3">
          <ReleaseChannelsList data={channels} isDemo={isDemo} />
        </TabsContent>
        <TabsContent value="locale" className="mt-3">
          <LocaleCurrency data={locale} isDemo={isDemo} />
        </TabsContent>
        <TabsContent value="features" className="mt-3">
          <FeatureEntitlement data={features} isDemo={isDemo} />
        </TabsContent>
      </Tabs>
    </div>
  );
}

// --- 2. Environments & Endpoints ---

function EnvironmentsList({ data, isDemo }: { data: Environment[]; isDemo: boolean }) {
  if (!data || data.length === 0) {
    return <EmptyBlock label="No environments configured for this tenant." />;
  }
  return (
    <div className="space-y-2">
      {isDemo && <DemoBadge />}
      {data.map((env) => (
        <div key={env.name} className="rounded border p-2 text-xs">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 font-semibold">
              <EnvStatusIndicator status={env.status} />
              <span className="uppercase tracking-wide">{env.name}</span>
              <span className="text-gray-500">· {env.region}</span>
            </div>
            <EnvStatusBadge status={env.status} />
          </div>
          <div className="grid grid-cols-2 gap-x-3 gap-y-1 mt-2 text-gray-600">
            <div>
              <span className="text-gray-400">Cluster: </span>
              <code className="text-[10px]">{env.cluster}</code>
            </div>
            <div>
              <span className="text-gray-400">Config ver: </span>
              <code className="text-[10px]">{env.configVersion}</code>
            </div>
            <div className="col-span-2 truncate">
              <span className="text-gray-400">API: </span>
              <code className="text-[10px]">{env.apiBaseUrl}</code>
            </div>
            <div className="col-span-2 truncate">
              <span className="text-gray-400">Config svc: </span>
              <code className="text-[10px]">{env.configServiceUrl}</code>
            </div>
            <div className="col-span-2 text-gray-400">
              Last health check: {env.lastChecked}
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

function EnvStatusIndicator({ status }: { status: Environment['status'] }) {
  const color =
    status === 'healthy'   ? 'text-green-500'  :
    status === 'degraded'  ? 'text-amber-500'  :
                              'text-red-500';
  return <Activity className={`w-3 h-3 ${color}`} />;
}

function EnvStatusBadge({ status }: { status: Environment['status'] }) {
  if (status === 'healthy')  return <Badge variant="default" className="bg-green-600">Healthy</Badge>;
  if (status === 'degraded') return <Badge variant="secondary" className="bg-amber-100 text-amber-800">Degraded</Badge>;
  return <Badge variant="destructive">Down</Badge>;
}

// --- 3. App Compatibility Matrix ---

function CompatibilityMatrix({ data, isDemo }: { data: AppCompatibility[]; isDemo: boolean }) {
  if (!data || data.length === 0) {
    return <EmptyBlock label="No app compatibility rows defined." />;
  }
  return (
    <div className="space-y-2">
      {isDemo && <DemoBadge />}
      <div className="overflow-x-auto">
        <table className="w-full text-[11px] border-collapse">
          <thead>
            <tr className="text-gray-500 border-b">
              <th className="text-left py-1 pr-1">Channel</th>
              <th className="text-left py-1 px-1">% Rollout</th>
              <th className="text-left py-1 px-1">iOS</th>
              <th className="text-left py-1 px-1">Android</th>
              <th className="text-left py-1 px-1">Huawei</th>
            </tr>
          </thead>
          <tbody>
            {data.map((row) => (
              <tr key={row.channel} className="border-b last:border-b-0">
                <td className="py-1 pr-1">
                  <ChannelBadge channel={row.channel} />
                </td>
                <td className="py-1 px-1 font-mono">{row.rolloutPct}%</td>
                <td className="py-1 px-1 font-mono">{row.iosMin} – {row.iosMax}</td>
                <td className="py-1 px-1 font-mono">{row.androidMin} – {row.androidMax}</td>
                <td className="py-1 px-1 font-mono">{row.huaweiMin} – {row.huaweiMax}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="space-y-1">
        {data.map((row) => (
          <div key={row.channel} className="text-[11px] text-gray-600">
            <span className="font-semibold uppercase">{row.channel}:</span>{' '}
            {row.features.length > 0 ? row.features.join(', ') : <em className="text-gray-400">no extra features</em>}
          </div>
        ))}
      </div>
    </div>
  );
}

function ChannelBadge({ channel }: { channel: 'stable' | 'beta' | 'preview' }) {
  const cls =
    channel === 'stable'  ? 'bg-green-100 text-green-700'  :
    channel === 'beta'    ? 'bg-amber-100 text-amber-800'  :
                            'bg-purple-100 text-purple-700';
  return <span className={`text-[10px] px-1.5 py-0.5 rounded uppercase font-semibold ${cls}`}>{channel}</span>;
}

// --- 4. Release Channels / Cohorts ---

function ReleaseChannelsList({ data, isDemo }: { data: ReleaseChannel[]; isDemo: boolean }) {
  if (!data || data.length === 0) {
    return <EmptyBlock label="No release channels configured." />;
  }
  return (
    <div className="space-y-2">
      {isDemo && <DemoBadge />}
      {data.map((ch) => (
        <div key={ch.name} className="rounded border p-2 text-xs space-y-1">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 font-semibold">
              <ChannelBadge channel={ch.name} />
              <span className="uppercase">{ch.name}</span>
            </div>
            <span className="text-gray-500">Rollout: {ch.rolloutPct}%</span>
          </div>
          <div className="w-full bg-gray-200 rounded h-1.5 overflow-hidden">
            <div
              className={`h-full ${
                ch.name === 'stable'  ? 'bg-green-500'  :
                ch.name === 'beta'    ? 'bg-amber-500'  :
                                        'bg-purple-500'
              }`}
              style={{ width: `${Math.min(100, Math.max(0, ch.rolloutPct))}%` }}
            />
          </div>
          {ch.abCohorts.length > 0 && (
            <div className="text-[11px] text-gray-600">
              <span className="text-gray-400">A/B cohorts: </span>
              {ch.abCohorts.map((c, i) => (
                <span key={c.name} className="mr-2">
                  <code className="text-[10px]">{c.name}</code>
                  <span className="text-gray-400"> ({c.pct}% · var {c.variant})</span>
                  {i < ch.abCohorts.length - 1 ? ',' : ''}
                </span>
              ))}
            </div>
          )}
        </div>
      ))}
    </div>
  );
}

// --- 5. Locale / Currency / Timezone ---

function LocaleCurrency({ data, isDemo }: { data: LocaleConfig | null; isDemo: boolean }) {
  if (!data) return <EmptyBlock label="No locale configuration." />;
  return (
    <div className="space-y-2 text-xs">
      {isDemo && <DemoBadge />}
      <div className="grid grid-cols-2 gap-2">
        <Field icon={<Languages className="w-3 h-3" />} label="Default locale"     value={data.defaultLocale} />
        <Field icon={<Coins className="w-3 h-3" />}       label="Default currency"   value={`${data.defaultCurrency} (${data.currencySymbol})`} />
        <Field icon={<Clock className="w-3 h-3" />}       label="Timezone (IANA)"    value={data.timezone} />
        <Field icon={<Calendar className="w-3 h-3" />}    label="Date format"        value={data.dateFormat} />
      </div>
      <div className="rounded border p-2">
        <div className="text-[11px] text-gray-500 mb-1">Supported locales</div>
        <div className="flex flex-wrap gap-1">
          {data.supportedLocales.map((l) => (
            <span key={l} className="text-[10px] px-1.5 py-0.5 rounded bg-gray-100 text-gray-700">
              {l}
            </span>
          ))}
        </div>
      </div>
    </div>
  );
}

function Field({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="rounded border p-2">
      <div className="flex items-center gap-1 text-[10px] text-gray-500 uppercase">
        {icon} {label}
      </div>
      <div className="text-xs font-mono mt-0.5">{value}</div>
    </div>
  );
}

// --- 6. Tenant Feature Entitlement ---

function FeatureEntitlement({ data, isDemo }: { data: TenantFeatureEntitlement[]; isDemo: boolean }) {
  const [local, setLocal] = useState<TenantFeatureEntitlement[]>(data || []);
  const [saving, setSaving] = useState<string | null>(null);

  const handleToggle = async (key: string, enabled: boolean) => {
    const previous = local.find((f) => f.key === key)?.enabled;
    setLocal((prev) => prev.map((f) => (f.key === key ? { ...f, enabled } : f)));

    if (isDemo) {
      toast.success(`${key} ${enabled ? 'enabled' : 'disabled'} (demo only)`);
      return;
    }

    setSaving(key);
    try {
      await api.features.toggle(key, enabled);
      toast.success(`Feature "${key}" ${enabled ? 'enabled' : 'disabled'}`);
    } catch (e: any) {
      // Rollback on failure
      setLocal((prev) => prev.map((f) => (f.key === key ? { ...f, enabled: previous ?? false } : f)));
      toast.error(`Failed to update ${key}: ${e?.message || 'unknown error'}`);
    } finally {
      setSaving(null);
    }
  };

  if (!local || local.length === 0) {
    return <EmptyBlock label="No feature flags defined for this tenant." />;
  }

  return (
    <div className="space-y-1.5">
      {isDemo && <DemoBadge />}
      {local.map((f) => (
        <div key={f.key} className="flex items-start justify-between rounded border p-2">
          <div className="pr-2">
            <div className="text-xs font-semibold">{f.label}</div>
            <div className="text-[11px] text-gray-500">{f.description}</div>
            <code className="text-[10px] text-gray-400">{f.key}</code>
          </div>
          <Switch
            checked={f.enabled}
            disabled={saving === f.key}
            onCheckedChange={(v) => handleToggle(f.key, v)}
            aria-label={`Toggle ${f.key}`}
          />
        </div>
      ))}
    </div>
  );
}

// --- Shared small bits ---

function EmptyBlock({ label }: { label: string }) {
  return (
    <div className="rounded border border-dashed p-3 text-xs text-gray-500 text-center">
      {label}
    </div>
  );
}

function DemoBadge() {
  return (
    <div className="text-[10px] text-amber-700 bg-amber-50 border border-amber-200 rounded px-2 py-0.5 inline-block">
      Demo data — connect the API to load real values
    </div>
  );
}

// --- Data hooks (API + demo fallback) ---

function useTenantEnvironments(tenantId: string, isDemo: boolean) {
  const { data } = useQuery<Environment[]>({
    queryKey: ['tenant-environments', tenantId],
    queryFn: async () => {
      const t = await api.tenants.get(tenantId);
      return (t?.environments as Environment[]) || [];
    },
    enabled: !isDemo,
  });
  return isDemo ? DEMO_ENVIRONMENTS : (data || []);
}

function useTenantCompatibility(tenantId: string, isDemo: boolean) {
  const { data } = useQuery<AppCompatibility[]>({
    queryKey: ['tenant-compatibility', tenantId],
    queryFn: async () => {
      const t = await api.tenants.get(tenantId);
      return (t?.compatibility as AppCompatibility[]) || [];
    },
    enabled: !isDemo,
  });
  return isDemo ? DEMO_COMPATIBILITY : (data || []);
}

function useTenantChannels(tenantId: string, isDemo: boolean) {
  const { data } = useQuery<ReleaseChannel[]>({
    queryKey: ['tenant-channels', tenantId],
    queryFn: async () => {
      const t = await api.tenants.get(tenantId);
      return (t?.channels as ReleaseChannel[]) || [];
    },
    enabled: !isDemo,
  });
  return isDemo ? DEMO_CHANNELS : (data || []);
}

function useTenantLocale(tenantId: string, industry: string | undefined, isDemo: boolean) {
  const { data } = useQuery<LocaleConfig | null>({
    queryKey: ['tenant-locale', tenantId],
    queryFn: async () => {
      const t = await api.tenants.get(tenantId);
      return (t?.locale as LocaleConfig) || null;
    },
    enabled: !isDemo,
  });
  if (isDemo) return DEMO_LOCALE_BY_INDUSTRY[industry || 'TELCO'] || DEMO_LOCALE;
  return data || null;
}

function useTenantFeatures(tenantId: string, isDemo: boolean) {
  const { data } = useQuery<TenantFeatureEntitlement[]>({
    queryKey: ['tenant-features', tenantId],
    queryFn: async () => {
      const flags = await api.features.list(tenantId);
      return (flags as TenantFeatureEntitlement[]) || [];
    },
    enabled: !isDemo,
  });
  return isDemo ? DEMO_FEATURES : (data || []);
}

// -------------------------------------------------------------------
// Add Client form — wired to api.tenants.create
// -------------------------------------------------------------------

function AddClientForm({ onDone, onCreated }: { onDone: () => void; onCreated: () => void }) {
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState({
    tenantId: '',
    name: '',
    industry: 'TELCO',
    tenantType: 'OPERATOR',
    country: 'LK',
    defaultLocale: 'en-LK',
    defaultCurrency: 'LKR',
    currencySymbol: 'Rs.',
    timezone: 'Asia/Colombo',
  });

  const onIndustryChange = (ind: string) => {
    const suggestedType =
      ind === 'TELCO' ? 'OPERATOR' :
      ind === 'INSURANCE' ? 'INSURER' :
      ind === 'TRAVEL' ? 'TRAVEL_COMPANY' :
      ind === 'BANKING' ? 'BANK' : 'OPERATOR';
    const localeDefaults = DEMO_LOCALE_BY_INDUSTRY[ind] || DEMO_LOCALE_BY_INDUSTRY.TELCO;
    setForm((prev) => ({
      ...prev,
      industry: ind,
      tenantType: suggestedType,
      defaultLocale: localeDefaults.defaultLocale,
      defaultCurrency: localeDefaults.defaultCurrency,
      currencySymbol: localeDefaults.currencySymbol,
      timezone: localeDefaults.timezone,
    }));
  };

  const handleSubmit = async () => {
    if (!form.tenantId.trim() || !form.name.trim()) {
      toast.error('Tenant ID and display name are required.');
      return;
    }
    if (!/^[a-z0-9-]+$/.test(form.tenantId)) {
      toast.error('Tenant ID must be lowercase letters, digits, and dashes only.');
      return;
    }
    if (form.country.length !== 2) {
      toast.error('Country must be a 2-letter ISO 3166-1 code.');
      return;
    }

    setSubmitting(true);
    try {
      const payload = {
        tenantId: form.tenantId.trim(),
        name: form.name.trim(),
        industry: form.industry,
        tenantType: form.tenantType,
        country: form.country.toUpperCase(),
        status: 'TRIAL',
        plan: 'Trial',
        locale: {
          defaultLocale: form.defaultLocale,
          defaultCurrency: form.defaultCurrency,
          currencySymbol: form.currencySymbol,
          timezone: form.timezone,
        },
      };

      const created = await api.tenants.create(payload);
      toast.success(`Tenant "${created?.name || form.tenantId}" created`);
      onCreated();
    } catch (e: any) {
      toast.error(`Failed to create tenant: ${e?.message || 'unknown error'}`);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Settings2 className="w-4 h-4" /> Add New Client
        </CardTitle>
        <CardDescription>
          Onboard a new business onto the OMOBIO platform. Calls{' '}
          <code>POST /api/v1/admin/tenants</code>.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <Label>Tenant ID</Label>
            <Input
              value={form.tenantId}
              onChange={(e) => setForm({ ...form, tenantId: e.target.value })}
              placeholder="dialog-lk"
            />
          </div>
          <div>
            <Label>Display name</Label>
            <Input
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              placeholder="Dialog Sri Lanka"
            />
          </div>
          <div>
            <Label>Industry</Label>
            <select
              className="w-full border rounded px-3 py-2"
              value={form.industry}
              onChange={(e) => onIndustryChange(e.target.value)}
              disabled={submitting}
            >
              <option value="TELCO">Telecommunications</option>
              <option value="INSURANCE">Insurance</option>
              <option value="TRAVEL">Travel &amp; Hospitality</option>
              <option value="BANKING">Banking &amp; Finance</option>
            </select>
          </div>
          <div>
            <Label>Tenant type</Label>
            <select
              className="w-full border rounded px-3 py-2"
              value={form.tenantType}
              onChange={(e) => setForm({ ...form, tenantType: e.target.value })}
              disabled={submitting}
            >
              <option value="OPERATOR">Telecom Operator</option>
              <option value="MVNO">MVNO</option>
              <option value="INSURER">Insurer</option>
              <option value="BROKER">Insurance Broker</option>
              <option value="TRAVEL_COMPANY">Travel Company</option>
              <option value="AIRLINE">Airline</option>
              <option value="BANK">Bank</option>
            </select>
          </div>
          <div>
            <Label>Country (ISO 3166-1 alpha-2)</Label>
            <Input
              value={form.country}
              onChange={(e) => setForm({ ...form, country: e.target.value.toUpperCase() })}
              maxLength={2}
              placeholder="LK"
              disabled={submitting}
            />
          </div>
          <div>
            <Label>Timezone (IANA)</Label>
            <Input
              value={form.timezone}
              onChange={(e) => setForm({ ...form, timezone: e.target.value })}
              placeholder="Asia/Colombo"
              disabled={submitting}
            />
          </div>
          <div>
            <Label>Default locale (BCP-47)</Label>
            <Input
              value={form.defaultLocale}
              onChange={(e) => setForm({ ...form, defaultLocale: e.target.value })}
              placeholder="en-LK"
              disabled={submitting}
            />
          </div>
          <div>
            <Label>Default currency (ISO 4217)</Label>
            <div className="flex gap-2">
              <Input
                className="flex-1"
                value={form.defaultCurrency}
                onChange={(e) => setForm({ ...form, defaultCurrency: e.target.value.toUpperCase() })}
                maxLength={3}
                placeholder="LKR"
                disabled={submitting}
              />
              <Input
                className="w-20"
                value={form.currencySymbol}
                onChange={(e) => setForm({ ...form, currencySymbol: e.target.value })}
                placeholder="Rs."
                disabled={submitting}
              />
            </div>
          </div>
        </div>
        <div className="flex gap-2 justify-end mt-4">
          <Button variant="outline" onClick={onDone} disabled={submitting}>
            Cancel
          </Button>
          <Button onClick={handleSubmit} disabled={submitting}>
            {submitting ? (
              <>
                <RefreshCw className="w-4 h-4 mr-2 animate-spin" /> Creating…
              </>
            ) : (
              <>
                <Plus className="w-4 h-4 mr-2" /> Create
              </>
            )}
          </Button>
        </div>
        <p className="text-xs text-gray-500 mt-3">
          The industry pack chosen determines which provider beans and
          terminology are available for this client. The tenant is
          created via <code>POST /api/v1/admin/tenants</code>.
        </p>
      </CardContent>
    </Card>
  );
}

// --- Industry / type visual helpers (unchanged) ---

function IndustryIcon({ industry }: { industry?: string }) {
  switch (industry) {
    case 'TELCO': return <Phone className="w-4 h-4 text-blue-500" />;
    case 'INSURANCE': return <Shield className="w-4 h-4 text-green-500" />;
    case 'TRAVEL': return <Plane className="w-4 h-4 text-purple-500" />;
    case 'BANKING': return <Banknote className="w-4 h-4 text-yellow-500" />;
    default: return <Briefcase className="w-4 h-4 text-gray-500" />;
  }
}

function IndustryBadge({ industry }: { industry?: string }) {
  const colors: Record<string, string> = {
    TELCO: 'bg-blue-100 text-blue-700',
    INSURANCE: 'bg-green-100 text-green-700',
    TRAVEL: 'bg-purple-100 text-purple-700',
    BANKING: 'bg-yellow-100 text-yellow-700',
  };
  const cls = colors[industry || ''] || 'bg-gray-100 text-gray-700';
  return (
    <span className={`text-xs px-2 py-0.5 rounded ${cls}`}>
      {industry || 'UNKNOWN'}
    </span>
  );
}

function TenantTypeBadge({ type }: { type?: string }) {
  const labels: Record<string, string> = {
    OPERATOR: 'Operator',
    INSURER: 'Insurer',
    TRAVEL_COMPANY: 'Travel Co.',
    BANK: 'Bank',
  };
  return (
    <span className="text-xs px-2 py-0.5 rounded bg-gray-100 text-gray-600">
      {labels[type || ''] || type || 'Unknown'}
    </span>
  );
}
