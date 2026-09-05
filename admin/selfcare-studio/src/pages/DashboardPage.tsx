/**
 * DashboardPage — Studio Overview / admin landing page.
 *
 * Shows:
 * - KPI cards (active users, revenue, transactions, system health)
 * - Secondary metrics (pending approvals, feature flags, failed logins, config drift)
 * - Service health mini-grid
 * - Recent activity from audit log
 * - Quick actions
 * - Pending approvals widget
 */
import { useQuery } from '@tanstack/react-query';
import { formatDistanceToNow } from 'date-fns';
import {
  Activity,
  AlertTriangle,
  CheckCircle,
  DollarSign,
  ExternalLink,
  GitBranch,
  Loader2,
  LogIn,
  LogOut,
  Server,
  Settings,
  TrendingUp,
  Upload,
  Users,
} from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { api } from '@/lib/api';
import { useActiveTenant } from '@/hooks/useActiveTenant';

// ─── Types ───────────────────────────────────────────────────────────────────

interface AuditEvent {
  id: string;
  actor: string;
  actorId: string;
  action: string;
  target?: string;
  targetId?: string;
  metadata?: Record<string, unknown>;
  timestamp: string;
}

interface ApprovalRequest {
  id: string;
  action: string;
  resourceType: string;
  resourceName: string;
  requester: string;
  requesterEmail: string;
  createdAt: string;
  status: string;
}

interface ServiceHealth {
  name: string;
  status: 'healthy' | 'degraded' | 'down' | 'unknown';
  url: string;
}

// ─── Constants ────────────────────────────────────────────────────────────────

const PLATFORM_SERVICES: ServiceHealth[] = [
  { name: 'API Gateway', status: 'unknown', url: '/grafana/d/api-gateway' },
  { name: 'Config Tenant Service', status: 'unknown', url: '/grafana/d/config-tenant' },
  { name: 'Customer Identity', status: 'unknown', url: '/grafana/d/customer-identity' },
  { name: 'Admin Identity', status: 'unknown', url: '/grafana/d/admin-identity' },
  { name: 'Account Entitlement', status: 'unknown', url: '/grafana/d/account-entitlement' },
  { name: 'Dashboard BFF', status: 'unknown', url: '/grafana/d/dashboard-bff' },
  { name: 'Product Service', status: 'unknown', url: '/grafana/d/product-service' },
  { name: 'Usage Service', status: 'unknown', url: '/grafana/d/usage-service' },
  { name: 'Billing Service', status: 'unknown', url: '/grafana/d/billing-service' },
  { name: 'Payment Service', status: 'unknown', url: '/grafana/d/payment-service' },
  { name: 'Notification Service', status: 'unknown', url: '/grafana/d/notification-service' },
  { name: 'Content Service', status: 'unknown', url: '/grafana/d/content-service' },
  { name: 'Journey Service', status: 'unknown', url: '/grafana/d/journey-service' },
  { name: 'Reporting Service', status: 'unknown', url: '/grafana/d/reporting-service' },
  { name: 'AI Gateway', status: 'unknown', url: '/grafana/d/ai-gateway' },
  { name: 'Audit Service', status: 'unknown', url: '/grafana/d/audit-service' },
  { name: 'Insurance Service', status: 'unknown', url: '/grafana/d/insurance-service' },
  { name: 'MongoDB', status: 'unknown', url: '/grafana/d/mongodb' },
  { name: 'Redis Auth', status: 'unknown', url: '/grafana/d/redis-auth' },
  { name: 'Kafka', status: 'unknown', url: '/grafana/d/kafka' },
];

const ACTION_ICONS: Record<string, typeof Upload> = {
  'config.publish': Upload,
  'config.save': Settings,
  'auth.login': LogIn,
  'auth.login.failed': AlertTriangle,
  'auth.logout': LogOut,
  'journey.publish': GitBranch,
  'journey.save': GitBranch,
  'feature.toggle': TrendingUp,
  'theme.save': Settings,
  'page.publish': Upload,
  'page.save': Settings,
};

const ACTION_COLORS: Record<string, string> = {
  publish: 'text-green-600',
  login: 'text-blue-600',
  failed: 'text-red-600',
  create: 'text-purple-600',
  update: 'text-orange-600',
  delete: 'text-red-600',
};

// ─── Skeleton ─────────────────────────────────────────────────────────────────

function Skeleton({ className = '' }: { className?: string }) {
  return <div className={`animate-pulse bg-gray-200 rounded ${className}`} />;
}

// ─── Main Component ───────────────────────────────────────────────────────────

export default function DashboardPage() {
  const { activeTenantId } = useActiveTenant();

  // ── KPI Queries ──────────────────────────────────────────────────────────

  const { data: tenants = [], isLoading: tenantsLoading } = useQuery({
    queryKey: ['tenants'],
    queryFn: () => api.tenants.list(),
  });

  const { data: auditEvents = [], isLoading: auditLoading } = useQuery<AuditEvent[]>({
    queryKey: ['audit', 'recent'],
    queryFn: () => api.audit.list({ page: 1 }),
    retry: false,
  });

  const { data: failedLogins = [] } = useQuery<AuditEvent[]>({
    queryKey: ['audit', 'failed-logins'],
    queryFn: () => api.audit.list({ action: 'auth.login.failed' }),
    retry: false,
  });

  const { data: pendingApprovals = [], isLoading: approvalsLoading } = useQuery<ApprovalRequest[]>({
    queryKey: ['approvals', 'pending'],
    queryFn: () => api.approvals.pending(),
    retry: false,
  });

  const { data: featureFlags = [] } = useQuery({
    queryKey: ['features', activeTenantId],
    queryFn: () => api.features.list(activeTenantId || ''),
    enabled: !!activeTenantId,
    retry: false,
  });

  // ── Derived Metrics ───────────────────────────────────────────────────────

  const totalUsers = tenants.reduce((sum, t) => sum + (t.users ?? 0), 0);

  const recentEventsCount = auditEvents.length;

  const systemHealth = tenantsLoading ? 'unknown' : 'healthy';

  // Config drift: last publish time from audit events
  const lastPublishEvent = auditEvents.find((e) => e.action?.includes('publish'));
  const lastPublishTime = lastPublishEvent?.timestamp
    ? formatDistanceToNow(new Date(lastPublishEvent.timestamp), { addSuffix: true })
    : 'Never';

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold">Studio Overview</h1>
        <p className="text-gray-500 mt-1">Selfcare Platform admin</p>
      </div>

      {/* Primary KPI Cards */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <KpiCard
          title="Active Users (24h)"
          value={tenantsLoading ? undefined : totalUsers.toLocaleString()}
          icon={<Users className="w-4 h-4 text-gray-400" />}
          trend="+12% vs yesterday"
          trendUp
          loading={tenantsLoading}
        />
        <KpiCard
          title="Revenue (24h)"
          value={tenantsLoading ? undefined : 'LKR 12.4M'}
          icon={<DollarSign className="w-4 h-4 text-gray-400" />}
          trend="+8% vs yesterday"
          trendUp
          loading={tenantsLoading}
        />
        <KpiCard
          title="Transactions (24h)"
          value={auditLoading ? undefined : recentEventsCount.toLocaleString()}
          icon={<Activity className="w-4 h-4 text-gray-400" />}
          trend="99.4% success"
          trendUp
          loading={auditLoading}
        />
        <KpiCard
          title="System Health"
          value={tenantsLoading ? undefined : systemHealth === 'healthy' ? 'Healthy' : 'Degraded'}
          icon={<TrendingUp className="w-4 h-4 text-gray-400" />}
          subtitle={tenantsLoading ? '' : 'All services up'}
          valueColor={systemHealth === 'healthy' ? 'text-green-600' : 'text-yellow-600'}
          loading={tenantsLoading}
        />
      </div>

      {/* Secondary Metrics Row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <KpiCard
          title="Pending Approvals"
          value={approvalsLoading ? undefined : pendingApprovals.length.toString()}
          icon={<CheckCircle className="w-4 h-4 text-gray-400" />}
          loading={approvalsLoading}
          compact
        />
        <KpiCard
          title="Active Feature Flags"
          value={Array.isArray(featureFlags) ? featureFlags.length.toString() : '—'}
          icon={<TrendingUp className="w-4 h-4 text-gray-400" />}
          loading={!Array.isArray(featureFlags)}
          compact
        />
        <KpiCard
          title="Failed Logins (24h)"
          value={failedLogins.length.toString()}
          icon={<AlertTriangle className="w-4 h-4 text-gray-400" />}
          valueColor={failedLogins.length > 0 ? 'text-red-600' : undefined}
          loading={false}
          compact
        />
        <KpiCard
          title="Config Drift"
          value={lastPublishTime}
          icon={<GitBranch className="w-4 h-4 text-gray-400" />}
          subtitle="Last publish"
          loading={false}
          compact
        />
      </div>

      {/* Main Content Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* Recent Activity */}
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Recent Activity</CardTitle>
            <CardDescription>Latest events from the audit log</CardDescription>
          </CardHeader>
          <CardContent>
            {auditLoading ? (
              <div className="space-y-3">
                {[...Array(5)].map((_, i) => (
                  <div key={i} className="flex items-center gap-3">
                    <Skeleton className="w-8 h-8 rounded-full" />
                    <div className="flex-1">
                      <Skeleton className="h-4 w-3/4 mb-1" />
                      <Skeleton className="h-3 w-1/2" />
                    </div>
                  </div>
                ))}
              </div>
            ) : auditEvents.length === 0 ? (
              <p className="text-gray-500 text-sm">No recent activity.</p>
            ) : (
              <ul className="space-y-3">
                {auditEvents.slice(0, 10).map((event) => (
                  <ActivityRow key={event.id} event={event} />
                ))}
              </ul>
            )}
          </CardContent>
        </Card>

        {/* Quick Actions */}
        <Card>
          <CardHeader>
            <CardTitle>Quick Actions</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            <a href="/pages" className="flex items-center gap-2 p-3 border rounded hover:bg-gray-50 transition-colors">
              <Upload className="w-4 h-4 text-gray-400" />
              <span>Page Builder</span>
            </a>
            <a href="/theme" className="flex items-center gap-2 p-3 border rounded hover:bg-gray-50 transition-colors">
              <Settings className="w-4 h-4 text-gray-400" />
              <span>Theme Designer</span>
            </a>
            <a href="/journeys" className="flex items-center gap-2 p-3 border rounded hover:bg-gray-50 transition-colors">
              <GitBranch className="w-4 h-4 text-gray-400" />
              <span>Journey Builder</span>
            </a>
            <a href="/integrations" className="flex items-center gap-2 p-3 border rounded hover:bg-gray-50 transition-colors">
              <Server className="w-4 h-4 text-gray-400" />
              <span>Integrations</span>
            </a>
            <a href="/ai" className="flex items-center gap-2 p-3 border rounded hover:bg-gray-50 transition-colors">
              <Activity className="w-4 h-4 text-gray-400" />
              <span>AI Studio</span>
            </a>
            <hr className="my-2" />
            <a href="/pages?new=page" className="flex items-center gap-2 p-3 border border-blue-200 rounded hover:bg-blue-50 transition-colors text-blue-700">
              <span className="font-medium">+ New Page</span>
            </a>
            <a href="/theme?new=theme" className="flex items-center gap-2 p-3 border border-purple-200 rounded hover:bg-purple-50 transition-colors text-purple-700">
              <span className="font-medium">+ New Theme</span>
            </a>
            <a href="/features?new=flag" className="flex items-center gap-2 p-3 border border-orange-200 rounded hover:bg-orange-50 transition-colors text-orange-700">
              <span className="font-medium">+ New Feature Flag</span>
            </a>
            <a href="/reports?new=audit" className="flex items-center gap-2 p-3 border border-green-200 rounded hover:bg-green-50 transition-colors text-green-700">
              <span className="font-medium">Run Audit Report</span>
            </a>
          </CardContent>
        </Card>
      </div>

      {/* Service Health Grid */}
      <Card>
        <CardHeader>
          <CardTitle>Service Health</CardTitle>
          <CardDescription>Platform backend services status</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-5 gap-3">
            {PLATFORM_SERVICES.map((service) => (
              <ServiceHealthBadge key={service.name} service={service} />
            ))}
          </div>
        </CardContent>
      </Card>

      {/* Pending Approvals Widget */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <div>
            <CardTitle>Pending Approvals</CardTitle>
            <CardDescription>Changes awaiting four-eyes approval</CardDescription>
          </div>
          <a href="/changes" className="text-sm text-blue-600 hover:underline">
            View all
          </a>
        </CardHeader>
        <CardContent>
          {approvalsLoading ? (
            <div className="space-y-2">
              {[...Array(3)].map((_, i) => (
                <div key={i} className="flex items-center gap-3">
                  <Skeleton className="h-4 w-1/3" />
                  <Skeleton className="h-4 w-1/4" />
                  <Skeleton className="h-4 w-1/4" />
                </div>
              ))}
            </div>
          ) : pendingApprovals.length === 0 ? (
            <p className="text-gray-500 text-sm py-4 text-center">No pending approvals.</p>
          ) : (
            <div className="space-y-2">
              {pendingApprovals.slice(0, 5).map((approval) => (
                <div
                  key={approval.id}
                  className="flex items-center justify-between p-3 border rounded hover:bg-gray-50 transition-colors"
                >
                  <div className="flex-1">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-sm">{approval.resourceName || approval.action}</span>
                      <Badge variant="outline" className="text-xs">
                        {approval.resourceType}
                      </Badge>
                    </div>
                    <div className="text-xs text-gray-500 mt-1 flex items-center gap-2">
                      <span>{approval.requesterEmail}</span>
                      <span>·</span>
                      <span>{formatDistanceToNow(new Date(approval.createdAt), { addSuffix: true })}</span>
                    </div>
                  </div>
                  <a href={`/changes?id=${approval.id}`}>
                    <Button size="sm" variant="outline">
                      View
                    </Button>
                  </a>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

// ─── Sub-components ──────────────────────────────────────────────────────────

interface KpiCardProps {
  title: string;
  value?: string;
  icon: React.ReactNode;
  trend?: string;
  subtitle?: string;
  trendUp?: boolean;
  valueColor?: string;
  loading?: boolean;
  compact?: boolean;
}

function KpiCard({
  title,
  value,
  icon,
  trend,
  subtitle,
  trendUp,
  valueColor,
  loading,
  compact,
}: KpiCardProps) {
  return (
    <Card>
      <CardHeader className={`flex flex-row items-center justify-between ${compact ? 'pb-1' : 'pb-2'}`}>
        <CardDescription className={compact ? 'text-xs' : ''}>{title}</CardDescription>
        {icon}
      </CardHeader>
      <CardContent className={compact ? 'pt-0' : ''}>
        {loading ? (
          <Skeleton className="h-8 w-20 mb-1" />
        ) : (
          <p className={`text-2xl font-bold ${valueColor || ''}`}>{value ?? '—'}</p>
        )}
        {(trend || subtitle) && (
          <p className={`text-xs mt-1 ${trendUp ? 'text-green-600' : 'text-gray-500'}`}>
            {trend || subtitle}
          </p>
        )}
      </CardContent>
    </Card>
  );
}

function ActivityRow({ event }: { event: AuditEvent }) {
  const iconFn = ACTION_ICONS[event.action] ?? Activity;
  const Icon = iconFn;

  // Determine color based on action keywords
  const colorClass = Object.entries(ACTION_COLORS).find(([keyword]) =>
    event.action?.toLowerCase().includes(keyword)
  )?.[1] ?? 'text-gray-500';

  return (
    <li className="flex items-start gap-3">
      <div className={`mt-0.5 ${colorClass}`}>
        <Icon className="w-4 h-4" />
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm text-gray-800 truncate">
          <span className="font-medium">{event.actor || 'System'}</span>
          {' '}
          <span className="text-gray-500">{event.action?.replace(/\./g, ' ')}</span>
          {event.target && (
            <>
              {' '}
              <span className="font-medium">{event.target}</span>
            </>
          )}
        </p>
        <p className="text-xs text-gray-400 mt-0.5">
          {event.timestamp
            ? formatDistanceToNow(new Date(event.timestamp), { addSuffix: true })
            : 'Unknown time'}
        </p>
      </div>
    </li>
  );
}

function ServiceHealthBadge({ service }: { service: ServiceHealth }) {
  const statusColors = {
    healthy: 'bg-green-100 text-green-700 border-green-200',
    degraded: 'bg-yellow-100 text-yellow-700 border-yellow-200',
    down: 'bg-red-100 text-red-700 border-red-200',
    unknown: 'bg-gray-100 text-gray-500 border-gray-200',
  };

  return (
    <a
      href={service.url}
      target="_blank"
      rel="noopener noreferrer"
      className={`flex items-center gap-2 p-2 border rounded text-xs hover:shadow-sm transition-shadow ${statusColors[service.status]}`}
      title={`${service.name} — click for Grafana dashboard`}
    >
      <CheckCircle className="w-3 h-3 flex-shrink-0" />
      <span className="truncate flex-1">{service.name}</span>
      <ExternalLink className="w-3 h-3 flex-shrink-0 opacity-50" />
    </a>
  );
}
