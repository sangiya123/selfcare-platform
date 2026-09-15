/**
 * Report Builder — define, schedule, run, and export reports.
 *
 * Implements Admin Scope §13:
 *   Report library · Report builder with saved filters · Dashboards
 *   Scheduled exports/delivery · Semantic metrics · Permissions
 *   Data freshness and lineage
 *
 * Report categories:
 *   Operational · Financial · Customer Service · Usage Analytics
 *   Predictive · Custom SQL
 *
 * API:
 *   GET  /api/v1/admin/reports              → list reports
 *   POST /api/v1/admin/reports             → create report
 *   PUT  /api/v1/admin/reports/{id}        → update report
 *   DELETE /api/v1/admin/reports/{id}      → delete report
 *   POST /api/v1/admin/reports/{id}/run   → run now
 *   POST /api/v1/admin/reports/{id}/schedule → set schedule
 *   GET  /api/v1/admin/reports/executions  → recent runs
 */
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import {
  Card, CardHeader, CardTitle, CardDescription, CardContent,
} from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import {
  BarChart3, Plus, Play, Download, Calendar, Mail, FileText,
  Table, TrendingUp, Clock, CheckCircle2, XCircle, Loader2,
  Trash2, Save, Filter, Eye, RefreshCw, Database,
} from 'lucide-react';
import { api } from '@/lib/api';
import { useActiveTenant } from '@/hooks/useActiveTenant';

// ─────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────

interface Report {
  id: string;
  name: string;
  description: string;
  category: ReportCategory;
  columns: ReportColumn[];
  filters: SavedFilter[];
  schedule?: Schedule;
  lastRun?: string;
  lastRunStatus?: 'SUCCESS' | 'FAILED' | 'RUNNING' | 'NEVER';
  avgDurationSec: number;
  dataFreshnessMinutes: number;
  ownerId: string;
  permissions: string[];     // roles allowed to view
  delivery?: DeliveryConfig;
  lineage?: LineageInfo;
}

type ReportCategory = 'Operational' | 'Financial' | 'Customer Service' | 'Usage Analytics' | 'Predictive' | 'Custom SQL';

interface ReportColumn {
  key: string;
  label: string;
  type: 'string' | 'number' | 'date' | 'currency' | 'percent';
  aggregation?: 'sum' | 'avg' | 'count' | 'min' | 'max';
  semanticLabel?: string;  // e.g. "revenue_lkr", "msisdn_masked"
}

interface SavedFilter {
  id: string;
  name: string;
  column: string;
  operator: 'eq' | 'ne' | 'gt' | 'lt' | 'in' | 'contains' | 'between';
  value: string;
}

interface Schedule {
  cron: string;
  timezone: string;
  enabled: boolean;
  nextRun?: string;
  channels: ('email' | 'webhook' | 'storage')[];
  recipients?: string[];
}

interface DeliveryConfig {
  format: 'csv' | 'xlsx' | 'pdf' | 'json';
  emailTo: string[];
  webhookUrl?: string;
  storageBucket?: string;
}

interface LineageInfo {
  sourceTables: string[];
  lastRefreshAt: string;
  refreshIntervalMinutes: number;
  etlJobId?: string;
}

interface Execution {
  id: string;
  reportId: string;
  reportName: string;
  status: 'SUCCESS' | 'FAILED' | 'RUNNING' | 'TIMEOUT';
  durationSec: number;
  rowCount: number;
  startedAt: string;
  completedAt?: string;
  error?: string;
}

// ─────────────────────────────────────────────────────────
// Mock data (replace with api calls once backend is ready)
// ─────────────────────────────────────────────────────────

const MOCK_REPORTS: Report[] = [
  {
    id: 'r1', name: 'Daily Transaction Summary', category: 'Financial',
    description: 'Total transactions, success rate, revenue by operator and channel',
    columns: [
      { key: 'date', label: 'Date', type: 'date' },
      { key: 'channel', label: 'Channel', type: 'string' },
      { key: 'total', label: 'Total (LKR)', type: 'currency', aggregation: 'sum', semanticLabel: 'amount_lkr' },
      { key: 'count', label: 'Count', type: 'number', aggregation: 'count' },
      { key: 'success_rate', label: 'Success Rate', type: 'percent' },
    ],
    filters: [],
    schedule: { cron: '0 2 * * *', timezone: 'Asia/Colombo', enabled: true, nextRun: '2026-09-05 02:00', channels: ['email'], recipients: ['ops@selfcare.io'] },
    lastRun: '2026-09-04T02:00:00Z', lastRunStatus: 'SUCCESS', avgDurationSec: 45,
    dataFreshnessMinutes: 120, ownerId: 'admin', permissions: ['REPORT_ANALYST', 'TENANT_ADMIN'],
    delivery: { format: 'csv', emailTo: ['ops@selfcare.io'] },
    lineage: { sourceTables: ['payment_transactions', 'billing_events'], lastRefreshAt: '2026-09-04T02:00:00Z', refreshIntervalMinutes: 120, etlJobId: 'etl-daily-tx' },
  },
  {
    id: 'r2', name: 'Active Subscribers', category: 'Operational',
    description: 'DAU/MAU, by segment, by LOB',
    columns: [
      { key: 'date', label: 'Date', type: 'date' },
      { key: 'lob', label: 'Line of Business', type: 'string' },
      { key: 'dau', label: 'DAU', type: 'number', aggregation: 'count' },
      { key: 'mau', label: 'MAU', type: 'number', aggregation: 'count' },
      { key: 'arpu', label: 'ARPU (LKR)', type: 'currency', semanticLabel: 'arpu_lkr' },
    ],
    filters: [],
    schedule: { cron: '0 * * * *', timezone: 'UTC', enabled: true, nextRun: '2026-09-04T15:00:00Z', channels: ['email', 'storage'] },
    lastRun: '2026-09-04T14:00:00Z', lastRunStatus: 'SUCCESS', avgDurationSec: 12,
    dataFreshnessMinutes: 60, ownerId: 'admin', permissions: ['REPORT_ANALYST', 'TENANT_ADMIN', 'SUPER_ADMIN'],
    lineage: { sourceTables: ['sessions', 'connections'], lastRefreshAt: '2026-09-04T14:00:00Z', refreshIntervalMinutes: 60 },
  },
  {
    id: 'r3', name: 'Bill Payment Aging', category: 'Financial',
    description: 'Bills by age bucket, payment rate',
    columns: [
      { key: 'tenant', label: 'Tenant', type: 'string' },
      { key: 'bucket', label: 'Age Bucket', type: 'string' },
      { key: 'count', label: 'Bill Count', type: 'number', aggregation: 'count' },
      { key: 'total', label: 'Total (LKR)', type: 'currency', aggregation: 'sum' },
      { key: 'payment_rate', label: 'Payment Rate', type: 'percent' },
    ],
    filters: [],
    schedule: { cron: '0 3 * * *', timezone: 'Asia/Colombo', enabled: true, nextRun: '2026-09-05T03:00:00Z', channels: ['email'], recipients: ['finance@selfcare.io'] },
    lastRun: '2026-09-04T03:00:00Z', lastRunStatus: 'SUCCESS', avgDurationSec: 78,
    dataFreshnessMinutes: 120, ownerId: 'admin', permissions: ['REPORT_ANALYST', 'TENANT_ADMIN'],
    lineage: { sourceTables: ['billing_invoices', 'payment_transactions'], lastRefreshAt: '2026-09-04T03:00:00Z', refreshIntervalMinutes: 120 },
  },
  {
    id: 'r4', name: 'Customer Churn Risk', category: 'Predictive',
    description: 'ML-scored churn risk by segment',
    columns: [
      { key: 'segment', label: 'Segment', type: 'string' },
      { key: 'risk_high', label: 'High Risk (count)', type: 'number', aggregation: 'count' },
      { key: 'risk_medium', label: 'Medium Risk', type: 'number', aggregation: 'count' },
      { key: 'risk_low', label: 'Low Risk', type: 'number', aggregation: 'count' },
    ],
    filters: [],
    schedule: { cron: '0 4 * * 1', timezone: 'UTC', enabled: true, nextRun: '2026-09-08T04:00:00Z', channels: ['email', 'webhook'] },
    lastRun: '2026-09-01T04:00:00Z', lastRunStatus: 'SUCCESS', avgDurationSec: 230,
    dataFreshnessMinutes: 10080, ownerId: 'admin', permissions: ['REPORT_ANALYST', 'SUPER_ADMIN'],
    lineage: { sourceTables: ['connections', 'usage_summaries', 'ml_churn_scores'], lastRefreshAt: '2026-09-01T04:00:00Z', refreshIntervalMinutes: 10080 },
  },
  {
    id: 'r5', name: 'Recharge Channel Mix', category: 'Operational',
    description: 'Recharge volume by channel (app, USSD, retail)',
    columns: [
      { key: 'date', label: 'Date', type: 'date' },
      { key: 'channel', label: 'Channel', type: 'string' },
      { key: 'volume', label: 'Volume (LKR)', type: 'currency', aggregation: 'sum' },
      { key: 'count', label: 'Transactions', type: 'number', aggregation: 'count' },
    ],
    filters: [],
    lastRun: undefined, lastRunStatus: 'NEVER', avgDurationSec: 0,
    dataFreshnessMinutes: 0, ownerId: 'admin', permissions: ['REPORT_ANALYST'],
    lineage: { sourceTables: ['recharge_transactions'], lastRefreshAt: '', refreshIntervalMinutes: 0 },
  },
];

const MOCK_EXECUTIONS: Execution[] = [
  { id: 'e1', reportId: 'r1', reportName: 'Daily Transaction Summary', status: 'SUCCESS', durationSec: 47, rowCount: 12453, startedAt: '2026-09-04T02:00:00Z', completedAt: '2026-09-04T02:00:47Z' },
  { id: 'e2', reportId: 'r2', reportName: 'Active Subscribers', status: 'SUCCESS', durationSec: 11, rowCount: 893201, startedAt: '2026-09-04T14:00:00Z', completedAt: '2026-09-04T14:00:11Z' },
  { id: 'e3', reportId: 'r4', reportName: 'Customer Churn Risk', status: 'SUCCESS', durationSec: 234, rowCount: 45200, startedAt: '2026-09-01T04:00:00Z', completedAt: '2026-09-01T04:03:54Z' },
  { id: 'e4', reportId: 'r1', reportName: 'Daily Transaction Summary', status: 'FAILED', durationSec: 3, rowCount: 0, startedAt: '2026-09-03T02:00:00Z', error: 'DB connection timeout after 3s' },
  { id: 'e5', reportId: 'r2', reportName: 'Active Subscribers', status: 'RUNNING', durationSec: 8, rowCount: 0, startedAt: '2026-09-04T15:00:00Z' },
];

const REPORT_CATEGORIES: ReportCategory[] = ['Operational', 'Financial', 'Customer Service', 'Usage Analytics', 'Predictive', 'Custom SQL'];

// ─────────────────────────────────────────────────────────
// Component
// ─────────────────────────────────────────────────────────

export default function ReportBuilderPage() {
  const { activeTenantId } = useActiveTenant();
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<'library' | 'builder' | 'executions' | 'lineage'>('library');
  const [selectedReport, setSelectedReport] = useState<Report | null>(null);
  const [runningId, setRunningId] = useState<string | null>(null);
  const [filterCategory, setFilterCategory] = useState<string>('All');

  // Fetch reports from API
  const { data: reports = [], isLoading: loadingReports } = useQuery({
    queryKey: ['reports', activeTenantId],
    queryFn: () => api.reports.list(),
    enabled: !!activeTenantId,
  });

  // Fetch executions from API
  const { data: executions = [], isLoading: loadingExecutions } = useQuery({
    queryKey: ['report-executions', activeTenantId],
    queryFn: () => api.reports.executions(activeTenantId),
    enabled: !!activeTenantId,
  });

  const isLoading = loadingReports || loadingExecutions;

  const filteredReports = filterCategory === 'All'
    ? reports
    : reports.filter((r) => r.category === filterCategory);

  const runReport = async (report: Report) => {
    setRunningId(report.id);
    toast.info(`Running "${report.name}"…`);
    try {
      await api.reports.execute(report.id, {});
      toast.success(`"${report.name}" execution started`);
      queryClient.invalidateQueries({ queryKey: ['report-executions', activeTenantId] });
    } catch (err: any) {
      toast.error(err.message || 'Failed to run report');
    }
    setRunningId(null);
  };

  const stats = {
    total: reports.length,
    scheduled: reports.filter((r) => r.schedule?.enabled).length,
    last24h: executions.filter((e: Execution) => e.startedAt && new Date(e.startedAt) > new Date(Date.now() - 86400000)).length,
    failed24h: executions.filter((e: Execution) => e.status === 'FAILED' && e.startedAt && new Date(e.startedAt) > new Date(Date.now() - 86400000)).length,
  };

  return (
    <div className="space-y-6">
      {/* ── Header ── */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <BarChart3 className="w-6 h-6" /> Report Builder
          </h1>
          <p className="text-gray-500 mt-1">Define, schedule, run, and export reports</p>
        </div>
        <Button onClick={() => { setSelectedReport(null); setActiveTab('builder'); }}>
          <Plus className="w-4 h-4 mr-2" /> New Report
        </Button>
      </div>

      {/* ── Stats row ── */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <StatCard label="Total reports" value={stats.total} icon={<FileText className="w-4 h-4" />} />
        <StatCard label="Scheduled" value={stats.scheduled} icon={<Calendar className="w-4 h-4" />} />
        <StatCard label="Runs (24h)" value={stats.last24h} icon={<Clock className="w-4 h-4" />} />
        <StatCard label="Failed (24h)" value={stats.failed24h} icon={<XCircle className="w-4 h-4" />} warning={stats.failed24h > 0} />
      </div>

      {/* ── Tabs ── */}
      <Tabs value={activeTab} onValueChange={(v) => setActiveTab(v as any)}>
        <TabsList>
          <TabsTrigger value="library"><Table className="w-4 h-4 mr-1" />Library</TabsTrigger>
          <TabsTrigger value="builder"><Filter className="w-4 h-4 mr-1" />Builder</TabsTrigger>
          <TabsTrigger value="executions"><Clock className="w-4 h-4 mr-1" />Executions</TabsTrigger>
          <TabsTrigger value="lineage"><Database className="w-4 h-4 mr-1" />Data Lineage</TabsTrigger>
        </TabsList>

        {/* ── Library tab ── */}
        <TabsContent value="library" className="space-y-4">
          {/* Category filter */}
          <div className="flex gap-2 flex-wrap">
            <Button size="sm" variant={filterCategory === 'All' ? 'default' : 'outline'} onClick={() => setFilterCategory('All')}>All</Button>
            {REPORT_CATEGORIES.map((cat) => (
              <Button key={cat} size="sm" variant={filterCategory === cat ? 'default' : 'outline'} onClick={() => setFilterCategory(cat)}>{cat}</Button>
            ))}
          </div>

          {/* Report list */}
          <div className="space-y-3">
            {filteredReports.map((report) => (
              <Card key={report.id} className="hover:border-purple-300 transition-colors">
                <CardContent className="pt-6">
                  <div className="flex items-start justify-between gap-4">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <h3 className="font-semibold">{report.name}</h3>
                        <Badge>{report.category}</Badge>
                        {report.schedule?.enabled && (
                          <Badge variant="outline" className="flex items-center gap-1">
                            <Clock className="w-3 h-3" />
                            {report.schedule.cron}
                          </Badge>
                        )}
                        {report.lastRunStatus === 'FAILED' && <Badge variant="destructive">FAILED</Badge>}
                        {report.lastRunStatus === 'NEVER' && <Badge variant="secondary">Never run</Badge>}
                      </div>
                      <p className="text-sm text-gray-500 mt-1">{report.description}</p>

                      {/* Metadata row */}
                      <div className="flex items-center gap-4 mt-2 text-xs text-gray-400 flex-wrap">
                        {report.lastRun && (
                          <span className="flex items-center gap-1">
                            <Clock className="w-3 h-3" /> Last run: {relativeTime(report.lastRun)}
                          </span>
                        )}
                        <span>Avg: {report.avgDurationSec}s</span>
                        <span>Freshness: {report.dataFreshnessMinutes >= 60 ? `${report.dataFreshnessMinutes / 60}h` : `${report.dataFreshnessMinutes}m`}</span>
                        {report.delivery && (
                          <Badge variant="outline" className="text-xs">
                            <Mail className="w-3 h-3 mr-1" />
                            {report.delivery.format.toUpperCase()}
                          </Badge>
                        )}
                        <div className="flex items-center gap-1">
                          {report.permissions.map((p: string) => (
                            <Badge key={p} variant="secondary" className="text-xs">{p}</Badge>
                          ))}
                        </div>
                      </div>

                      {/* Lineage mini-bar */}
                      {report.lineage && (
                        <div className="flex items-center gap-2 mt-2 text-xs text-gray-400">
                          <Database className="w-3 h-3" />
                          <span>{report.lineage.sourceTables.join(' → ')}</span>
                          <span className="text-gray-300">·</span>
                          <span>refresh {report.lineage.refreshIntervalMinutes >= 60 ? `${report.lineage.refreshIntervalMinutes / 60}h` : `${report.lineage.refreshIntervalMinutes}m`}</span>
                        </div>
                      )}
                    </div>

                    {/* Actions */}
                    <div className="flex items-center gap-2 shrink-0">
                      <Button size="sm" variant="outline" onClick={() => { setSelectedReport(report); setActiveTab('builder'); }}>
                        <Eye className="w-3 h-3 mr-1" /> View
                      </Button>
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => runReport(report)}
                        disabled={runningId === report.id || report.lastRunStatus === 'RUNNING'}
                      >
                        {runningId === report.id || report.lastRunStatus === 'RUNNING'
                          ? <Loader2 className="w-3 h-3 animate-spin" />
                          : <Play className="w-3 h-3 mr-1" />}
                        {report.lastRunStatus === 'RUNNING' ? 'Running…' : 'Run'}
                      </Button>
                      <Button size="sm" variant="outline">
                        <Download className="w-3 h-3 mr-1" /> Export
                      </Button>
                    </div>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </TabsContent>

        {/* ── Builder tab ── */}
        <TabsContent value="builder">
          <ReportBuilder
            report={selectedReport}
            onSave={() => { toast.success('Report saved'); setActiveTab('library'); }}
            onCancel={() => setActiveTab('library')}
          />
        </TabsContent>

        {/* ── Executions tab ── */}
        <TabsContent value="executions">
          <Card>
            <CardHeader>
              <CardTitle>Recent Report Executions</CardTitle>
              <CardDescription>Last 100 runs across all reports</CardDescription>
            </CardHeader>
            <CardContent>
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-gray-500 border-b">
                    <th className="py-2 font-normal">Report</th>
                    <th className="py-2 font-normal">Status</th>
                    <th className="py-2 font-normal">Duration</th>
                    <th className="py-2 font-normal">Rows</th>
                    <th className="py-2 font-normal">Started</th>
                    <th className="py-2 font-normal">Error</th>
                    <th className="py-2 font-normal"></th>
                  </tr>
                </thead>
                <tbody>
                  {executions.map((e: Execution) => (
                    <tr key={e.id} className="border-b last:border-0">
                      <td className="py-2 font-medium">{e.reportName}</td>
                      <td className="py-2">
                        <StatusBadge status={e.status} />
                      </td>
                      <td className="py-2 text-gray-500">{e.durationSec}s</td>
                      <td className="py-2 text-gray-500">{e.rowCount > 0 ? e.rowCount.toLocaleString() : '—'}</td>
                      <td className="py-2 text-gray-500 font-mono text-xs">{relativeTime(e.startedAt)}</td>
                      <td className="py-2 text-red-500 text-xs max-w-xs truncate">{e.error || ''}</td>
                      <td className="py-2 text-right">
                        {e.status === 'SUCCESS' && (
                          <Button size="sm" variant="ghost"><Download className="w-3 h-3" /></Button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </CardContent>
          </Card>
        </TabsContent>

        {/* ── Lineage tab ── */}
        <TabsContent value="lineage">
          <div className="space-y-4">
            <p className="text-sm text-gray-500">Data lineage shows which source tables feed each report and how fresh the data is.</p>
            {reports.filter((r) => r.lineage).map((report) => (
              <Card key={report.id}>
                <CardHeader>
                  <div className="flex items-center justify-between">
                    <div>
                      <CardTitle className="text-base">{report.name}</CardTitle>
                      <CardDescription>{report.lineage!.sourceTables.join(' → ')}</CardDescription>
                    </div>
                    <div className="text-right text-sm">
                      <div className="text-gray-500">Last refresh</div>
                      <div className="font-medium">{report.lineage!.lastRefreshAt ? relativeTime(report.lineage!.lastRefreshAt) : 'Never'}</div>
                      <div className="text-gray-400 text-xs">every {report.lineage!.refreshIntervalMinutes >= 60 ? `${report.lineage!.refreshIntervalMinutes / 60}h` : `${report.lineage!.refreshIntervalMinutes}m`}</div>
                    </div>
                  </div>
                </CardHeader>
                <CardContent>
                  <div className="flex items-center gap-2">
                    {report.lineage!.sourceTables.map((table: string, i: number) => (
                      <span key={table}>
                        <span className="inline-flex items-center gap-1 bg-gray-100 text-gray-700 text-xs px-2 py-1 rounded font-mono">
                          <Database className="w-3 h-3" /> {table}
                        </span>
                        {i < report.lineage!.sourceTables.length - 1 && (
                          <span className="text-gray-400 mx-1">→</span>
                        )}
                      </span>
                    ))}
                    {report.lineage!.etlJobId && (
                      <>
                        <span className="text-gray-400 mx-1">→</span>
                        <span className="inline-flex items-center gap-1 bg-purple-50 text-purple-700 text-xs px-2 py-1 rounded">
                          <RefreshCw className="w-3 h-3" /> ETL #{report.lineage!.etlJobId}
                        </span>
                      </>
                    )}
                  </div>

                  {/* Freshness bar */}
                  <div className="mt-3">
                    <div className="flex justify-between text-xs text-gray-500 mb-1">
                      <span>Data freshness</span>
                      <span>{report.dataFreshnessMinutes >= 60 ? `${report.dataFreshnessMinutes / 60}h old` : `${report.dataFreshnessMinutes}m old`}</span>
                    </div>
                    <div className="w-full bg-gray-100 rounded-full h-1.5">
                      <div
                        className="bg-green-400 h-1.5 rounded-full"
                        style={{ width: `${Math.max(5, 100 - (report.dataFreshnessMinutes / (24 * 60)) * 100)}%` }}
                      />
                    </div>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </TabsContent>
      </Tabs>
    </div>
  );
}

// ─────────────────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────────────────

function StatCard({ label, value, icon, warning }: { label: string; value: number; icon: React.ReactNode; warning?: boolean }) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between pb-2">
        <CardDescription>{label}</CardDescription>
        <span className="text-gray-400">{icon}</span>
      </CardHeader>
      <CardContent>
        <p className={`text-3xl font-bold ${warning ? 'text-red-500' : ''}`}>{value}</p>
      </CardContent>
    </Card>
  );
}

function StatusBadge({ status }: { status: Execution['status'] }) {
  const map: Record<string, { cls: string; icon: React.ReactNode; label: string }> = {
    SUCCESS: { cls: 'bg-green-100 text-green-700', icon: <CheckCircle2 className="w-3 h-3" />, label: 'Success' },
    FAILED: { cls: 'bg-red-100 text-red-700', icon: <XCircle className="w-3 h-3" />, label: 'Failed' },
    RUNNING: { cls: 'bg-blue-100 text-blue-700', icon: <Loader2 className="w-3 h-3 animate-spin" />, label: 'Running' },
    TIMEOUT: { cls: 'bg-yellow-100 text-yellow-700', icon: <Clock className="w-3 h-3" />, label: 'Timeout' },
  };
  const m = map[status] ?? map.FAILED;
  return (
    <span className={`inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded ${m.cls}`}>
      {m.icon} {m.label}
    </span>
  );
}

function relativeTime(isoString: string): string {
  if (!isoString) return '—';
  const diff = Date.now() - new Date(isoString).getTime();
  if (diff < 60000) return 'just now';
  if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`;
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}h ago`;
  return `${Math.floor(diff / 86400000)}d ago`;
}

// ─────────────────────────────────────────────────────────
// Report Builder form
// ─────────────────────────────────────────────────────────

function ReportBuilder({
  report,
  onSave,
  onCancel,
}: {
  report: Report | null;
  onSave: () => void;
  onCancel: () => void;
}) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<Partial<Report>>(
    report ?? {
      name: '',
      description: '',
      category: 'Operational',
      columns: [],
      filters: [],
      permissions: ['REPORT_ANALYST'],
    }
  );
  const [filters, setFilters] = useState<SavedFilter[]>(report?.filters ?? []);
  const [schedule, setSchedule] = useState<Partial<Schedule>>(report?.schedule ?? {});
  const [delivery, setDelivery] = useState<Partial<DeliveryConfig>>(report?.delivery ?? { format: 'csv', emailTo: [] });
  const [saving, setSaving] = useState(false);

  const updateForm = (key: keyof Report, value: any) =>
    setForm((f) => ({ ...f, [key]: value }));

  const createMutation = useMutation({
    mutationFn: (data: any) => api.reports.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reports'] });
      toast.success('Report created');
      onSave();
    },
    onError: (err: any) => toast.error(err.message || 'Failed to create report'),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: any }) => api.reports.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reports'] });
      toast.success('Report updated');
      onSave();
    },
    onError: (err: any) => toast.error(err.message || 'Failed to update report'),
  });

  const save = async () => {
    if (!form.name) { toast.error('Report name is required'); return; }
    setSaving(true);
    const payload = { ...form, filters, schedule, delivery };
    if (report?.id) {
      updateMutation.mutate({ id: report.id, data: payload });
    } else {
      createMutation.mutate(payload);
    }
    setSaving(false);
  };

  const addFilter = () => {
    setFilters((f) => [...f, { id: `f${Date.now()}`, name: '', column: '', operator: 'eq', value: '' }]);
  };

  return (
    <div className="space-y-6">
      {/* Basic info */}
      <Card>
        <CardHeader>
          <CardTitle>{report ? `Edit: ${report.name}` : 'New Report'}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label>Report name *</Label>
              <Input value={form.name} onChange={(e) => updateForm('name', e.target.value)} placeholder="Daily Transaction Summary" />
            </div>
            <div>
              <Label>Category</Label>
              <select className="w-full border rounded px-3 py-2" value={form.category} onChange={(e) => updateForm('category', e.target.value)}>
                {REPORT_CATEGORIES.map((c) => <option key={c}>{c}</option>)}
              </select>
            </div>
          </div>
          <div>
            <Label>Description</Label>
            <Input value={form.description} onChange={(e) => updateForm('description', e.target.value)} placeholder="What this report shows and who it's for" />
          </div>
          <div>
            <Label>Permissions (roles that can view)</Label>
            <div className="flex flex-wrap gap-2 mt-1">
              {['VIEWER', 'CONTENT_EDITOR', 'REPORT_ANALYST', 'TENANT_ADMIN', 'SUPER_ADMIN'].map((role) => (
                <label key={role} className="flex items-center gap-1.5 text-sm cursor-pointer">
                  <input
                    type="checkbox"
                    checked={(form.permissions ?? []).includes(role)}
                    onChange={(e) => {
                      const perms = new Set(form.permissions ?? []);
                      e.target.checked ? perms.add(role) : perms.delete(role);
                      updateForm('permissions', [...perms]);
                    }}
                    className="rounded"
                  />
                  {role}
                </label>
              ))}
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Saved filters */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle>Saved Filters</CardTitle>
            <Button size="sm" variant="outline" onClick={addFilter}><Plus className="w-3 h-3 mr-1" />Add Filter</Button>
          </div>
          <CardDescription>Reusable filter presets that analysts can apply when running this report</CardDescription>
        </CardHeader>
        <CardContent>
          {filters.length === 0 ? (
            <p className="text-sm text-gray-400 italic">No filters yet. Add a filter to let analysts narrow results.</p>
          ) : (
            <div className="space-y-3">
              {filters.map((f, i) => (
                <div key={f.id} className="flex items-center gap-2">
                  <select className="border rounded px-2 py-1.5 text-sm" value={f.column} onChange={(e) => setFilters((prev) => prev.map((x, j) => j === i ? { ...x, column: e.target.value } : x))}>
                    <option value="">Column…</option>
                    <option value="tenant">Tenant</option>
                    <option value="date">Date</option>
                    <option value="channel">Channel</option>
                    <option value="status">Status</option>
                  </select>
                  <select className="border rounded px-2 py-1.5 text-sm" value={f.operator} onChange={(e) => setFilters((prev) => prev.map((x, j) => j === i ? { ...x, operator: e.target.value as SavedFilter['operator'] } : x))}>
                    <option value="eq">equals</option>
                    <option value="ne">not equals</option>
                    <option value="gt">greater than</option>
                    <option value="lt">less than</option>
                    <option value="contains">contains</option>
                    <option value="in">in list</option>
                  </select>
                  <Input className="flex-1" value={f.value} onChange={(e) => setFilters((prev) => prev.map((x, j) => j === i ? { ...x, value: e.target.value } : x))} placeholder="Value" />
                  <Input className="w-32" value={f.name} onChange={(e) => setFilters((prev) => prev.map((x, j) => j === i ? { ...x, name: e.target.value } : x))} placeholder="Preset name" />
                  <Button size="sm" variant="ghost" onClick={() => setFilters((prev) => prev.filter((_, j) => j !== i))}><Trash2 className="w-3 h-3 text-red-500" /></Button>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Schedule */}
      <Card>
        <CardHeader>
          <CardTitle>Schedule</CardTitle>
          <CardDescription>Automatically run this report on a cron schedule</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-center gap-3">
            <label className="flex items-center gap-2 cursor-pointer">
              <input type="checkbox" checked={schedule.enabled} onChange={(e) => setSchedule((s) => ({ ...s, enabled: e.target.checked }))} className="rounded" />
              <span className="text-sm font-medium">Enable schedule</span>
            </label>
          </div>
          {schedule.enabled && (
            <div className="grid grid-cols-3 gap-4">
              <div>
                <Label>Cron expression</Label>
                <Input value={schedule.cron} onChange={(e) => setSchedule((s) => ({ ...s, cron: e.target.value }))} placeholder="0 2 * * *" />
                <p className="text-xs text-gray-400 mt-1">minute hour day month weekday</p>
              </div>
              <div>
                <Label>Timezone</Label>
                <select className="w-full border rounded px-3 py-2" value={schedule.timezone} onChange={(e) => setSchedule((s) => ({ ...s, timezone: e.target.value }))}>
                  <option>UTC</option><option>Asia/Colombo</option><option>Asia/Singapore</option>
                </select>
              </div>
              <div>
                <Label>Delivery channels</Label>
                <div className="flex gap-3 mt-1">
                  {(['email', 'webhook', 'storage'] as const).map((ch) => (
                    <label key={ch} className="flex items-center gap-1 text-sm">
                      <input type="checkbox" checked={(schedule.channels ?? []).includes(ch)} onChange={(e) => {
                        const channels = new Set(schedule.channels ?? []);
                        e.target.checked ? channels.add(ch) : channels.delete(ch);
                        setSchedule((s) => ({ ...s, channels: [...channels] }));
                      }} className="rounded" />
                      {ch}
                    </label>
                  ))}
                </div>
              </div>
            </div>
          )}
          {schedule.nextRun && schedule.enabled && (
            <p className="text-xs text-gray-500">Next run: <span className="font-mono">{schedule.nextRun}</span></p>
          )}
        </CardContent>
      </Card>

      {/* Export delivery */}
      <Card>
        <CardHeader>
          <CardTitle>Export &amp; Delivery</CardTitle>
          <CardDescription>Automatically deliver the report output via email, webhook, or object storage</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label>Export format</Label>
              <select className="w-full border rounded px-3 py-2" value={delivery.format} onChange={(e) => setDelivery((d) => ({ ...d, format: e.target.value as DeliveryConfig['format'] }))}>
                <option value="csv">CSV</option><option value="xlsx">XLSX</option><option value="pdf">PDF</option><option value="json">JSON</option>
              </select>
            </div>
            <div>
              <Label>Recipients (comma-separated emails)</Label>
              <Input value={(delivery.emailTo ?? []).join(', ')} onChange={(e) => setDelivery((d) => ({ ...d, emailTo: e.target.value.split(',').map((s) => s.trim()).filter(Boolean) }))} placeholder="ops@selfcare.io, team@selfcare.io" />
            </div>
          </div>
          <div>
            <Label>Webhook URL (optional)</Label>
            <Input value={delivery.webhookUrl ?? ''} onChange={(e) => setDelivery((d) => ({ ...d, webhookUrl: e.target.value }))} placeholder="https://hooks.example.com/reports" />
          </div>
          <div>
            <Label>Storage bucket (optional)</Label>
            <Input value={delivery.storageBucket ?? ''} onChange={(e) => setDelivery((d) => ({ ...d, storageBucket: e.target.value }))} placeholder="s3://selfcare-reports/tenant/" />
          </div>
        </CardContent>
      </Card>

      {/* Actions */}
      <div className="flex justify-end gap-3">
        <Button variant="outline" onClick={onCancel}>Cancel</Button>
        <Button onClick={save} disabled={saving}>
          {saving ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : <Save className="w-4 h-4 mr-2" />}
          {saving ? 'Saving…' : 'Save Report'}
        </Button>
      </div>
    </div>
  );
}
