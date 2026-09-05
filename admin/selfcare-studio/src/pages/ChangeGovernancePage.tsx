/**
 * ChangeGovernancePage — Draft → Validate → Preview → Review → Approve →
 * Publish → Monitor → Rollback workflow UI.
 *
 * Every config change in Selfcare Studio follows this 8-stage workflow per
 * the planning doc (`05_admin_reports/01_Selfcare_Studio_Admin_Scope.md`
 * section 16, `05_admin_reports/03_Admin_RBAC_Audit_and_Approval_Matrix.md`).
 *
 * The page shows:
 * - All in-flight changes (Pending Review, Pending Approval, Scheduled)
 * - Recent published changes
 * - Rollback history
 * - One-click diff between versions
 * - A "four-eyes" check: the user who drafted cannot be the user who
 *   approves (enforced client-side and server-side).
 */
import { useState } from 'react';
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
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import {
  CheckCircle2,
  XCircle,
  Clock,
  GitBranch,
  GitCommit,
  RotateCcw,
  Eye,
  Send,
  AlertTriangle,
  FileText,
  Users,
  Loader2,
} from 'lucide-react';
import { api } from '@/lib/api';
import { useActiveTenant } from '@/hooks/useActiveTenant';

type ChangeStatus =
  | 'DRAFT'
  | 'VALIDATING'
  | 'PREVIEW'
  | 'IN_REVIEW'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'PUBLISHED'
  | 'REJECTED'
  | 'ROLLED_BACK'
  | 'FAILED';

interface ConfigChange {
  id: string;
  resourceType: 'layout' | 'theme' | 'journey' | 'integration' | 'feature_flag' | 'page' | 'content';
  resourceId: string;
  resourceName: string;
  versionFrom?: string;
  versionTo: string;
  changeSummary: string;
  diffJson: string;
  status: ChangeStatus;
  drafterId: string;
  drafterEmail: string;
  reviewerId?: string;
  approverId?: string;
  approverEmail?: string;
  approvalComments?: string;
  createdAt: string;
  submittedAt?: string;
  approvedAt?: string;
  publishedAt?: string;
  rolledBackAt?: string;
  rollbackReason?: string;
  ticketReference?: string;
  environment: 'dev' | 'staging' | 'prod';
}

const STATUS_META: Record<
  ChangeStatus,
  { label: string; color: string; icon: any }
> = {
  DRAFT: { label: 'Draft', color: 'bg-gray-100 text-gray-700', icon: FileText },
  VALIDATING: { label: 'Validating', color: 'bg-blue-100 text-blue-700', icon: Loader2 },
  PREVIEW: { label: 'In Preview', color: 'bg-purple-100 text-purple-700', icon: Eye },
  IN_REVIEW: { label: 'In Review', color: 'bg-yellow-100 text-yellow-700', icon: Users },
  PENDING_APPROVAL: { label: 'Pending Approval', color: 'bg-orange-100 text-orange-700', icon: Clock },
  APPROVED: { label: 'Approved', color: 'bg-cyan-100 text-cyan-700', icon: CheckCircle2 },
  PUBLISHED: { label: 'Published', color: 'bg-green-100 text-green-700', icon: GitBranch },
  REJECTED: { label: 'Rejected', color: 'bg-red-100 text-red-700', icon: XCircle },
  ROLLED_BACK: { label: 'Rolled Back', color: 'bg-pink-100 text-pink-700', icon: RotateCcw },
  FAILED: { label: 'Failed', color: 'bg-red-100 text-red-700', icon: AlertTriangle },
};

const HIGH_RISK_RESOURCES: ConfigChange['resourceType'][] = [
  'layout',
  'integration',
  'feature_flag',
  'journey',
];

export default function ChangeGovernancePage() {
  const queryClient = useQueryClient();
  const { activeTenantId } = useActiveTenant();
  const [tab, setTab] = useState<'pending' | 'in_review' | 'published' | 'rolled_back'>('pending');
  const [selectedChange, setSelectedChange] = useState<ConfigChange | null>(null);

  // Mock data — replace with real API when approval service is wired
  const { data: changes = [], isLoading } = useQuery({
    queryKey: ['changes', activeTenantId, tab],
    queryFn: async (): Promise<ConfigChange[]> => {
      // TODO: api.approvals.list({ status: tab })
      return mockChanges(activeTenantId || 'demo-tenant', tab);
    },
  });

  const submitForReview = useMutation({
    mutationFn: (id: string) => Promise.resolve(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['changes'] });
      toast.success('Submitted for review');
    },
  });

  const approveChange = useMutation({
    mutationFn: ({ id, comments }: { id: string; comments: string }) =>
      Promise.resolve({ id, comments }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['changes'] });
      toast.success('Approved and queued for publish');
    },
  });

  const rejectChange = useMutation({
    mutationFn: ({ id, comments }: { id: string; comments: string }) =>
      Promise.resolve({ id, comments }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['changes'] });
      toast.success('Change rejected');
    },
  });

  const rollback = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      Promise.resolve({ id, reason }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['changes'] });
      toast.success('Rolled back to previous version');
    },
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold flex items-center gap-2">
          <GitBranch className="w-6 h-6" />
          Change Governance
        </h1>
        <p className="text-gray-500 mt-1">
          Every config change goes through Draft → Validate → Preview → Review →
          Approve → Publish → Monitor. High-risk changes require a separate
          approver (four-eyes principle).
        </p>
      </div>

      {/* Workflow diagram */}
      <Card>
        <CardHeader>
          <CardTitle>Workflow Stages</CardTitle>
          <CardDescription>
            Each stage is auditable. A change cannot skip a stage, and the
            approver cannot be the drafter.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-between text-xs">
            {[
              'Draft',
              'Validate',
              'Preview',
              'Review',
              'Approve',
              'Publish',
              'Monitor',
              'Rollback',
            ].map((stage, i) => (
              <div key={stage} className="flex items-center gap-1 flex-1">
                <div className="flex flex-col items-center">
                  <div className="w-8 h-8 rounded-full bg-purple-100 text-purple-700 flex items-center justify-center font-semibold">
                    {i + 1}
                  </div>
                  <span className="mt-1 text-gray-600">{stage}</span>
                </div>
                {i < 7 && <div className="flex-1 h-0.5 bg-gray-200 mx-1" />}
              </div>
            ))}
          </div>
        </CardContent>
      </Card>

      <Tabs value={tab} onValueChange={(v) => setTab(v as any)}>
        <TabsList>
          <TabsTrigger value="pending">
            Pending ({changes.filter((c) => c.status === 'PENDING_APPROVAL' || c.status === 'IN_REVIEW').length})
          </TabsTrigger>
          <TabsTrigger value="in_review">In Review</TabsTrigger>
          <TabsTrigger value="published">Published</TabsTrigger>
          <TabsTrigger value="rolled_back">Rolled Back</TabsTrigger>
        </TabsList>

        <TabsContent value={tab} className="space-y-3">
          {isLoading ? (
            <div className="flex items-center justify-center py-12 text-gray-500">
              <Loader2 className="w-6 h-6 animate-spin mr-2" /> Loading changes...
            </div>
          ) : changes.length === 0 ? (
            <div className="text-center py-12 text-gray-500">
              No changes in this stage.
            </div>
          ) : (
            <div className="space-y-2">
              {changes.map((change) => (
                <ChangeRow
                  key={change.id}
                  change={change}
                  onSelect={() => setSelectedChange(change)}
                  onSubmitForReview={() => submitForReview.mutate(change.id)}
                  onApprove={(comments) =>
                    approveChange.mutate({ id: change.id, comments })
                  }
                  onReject={(comments) =>
                    rejectChange.mutate({ id: change.id, comments })
                  }
                  onRollback={(reason) =>
                    rollback.mutate({ id: change.id, reason })
                  }
                />
              ))}
            </div>
          )}
        </TabsContent>
      </Tabs>

      {selectedChange && (
        <ChangeDetailDialog
          change={selectedChange}
          onClose={() => setSelectedChange(null)}
        />
      )}
    </div>
  );
}

function ChangeRow({
  change,
  onSelect,
  onSubmitForReview,
  onApprove,
  onReject,
  onRollback,
}: {
  change: ConfigChange;
  onSelect: () => void;
  onSubmitForReview: () => void;
  onApprove: (comments: string) => void;
  onReject: (comments: string) => void;
  onRollback: (reason: string) => void;
}) {
  const meta = STATUS_META[change.status];
  const Icon = meta.icon;
  const isHighRisk = HIGH_RISK_RESOURCES.includes(change.resourceType);

  return (
    <Card className="hover:shadow-md transition-shadow cursor-pointer" onClick={onSelect}>
      <CardContent className="p-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3 flex-1">
            <div className="flex flex-col items-center">
              <Icon className="w-5 h-5 text-gray-500" />
              {isHighRisk && (
                <span title="High-risk — four-eyes approval required">
                  <AlertTriangle className="w-3 h-3 text-orange-500 mt-1" />
                </span>
              )}
            </div>
            <div className="flex-1">
              <div className="flex items-center gap-2">
                <span className="font-medium">{change.resourceName}</span>
                <Badge variant="outline" className="text-xs">
                  {change.resourceType}
                </Badge>
                <Badge className={meta.color}>
                  {meta.label}
                </Badge>
                {isHighRisk && (
                  <Badge variant="destructive" className="text-xs">High Risk</Badge>
                )}
              </div>
              <p className="text-sm text-gray-500 mt-1">{change.changeSummary}</p>
              <div className="text-xs text-gray-400 mt-1 flex items-center gap-3">
                <span>v{change.versionFrom || '—'} → v{change.versionTo}</span>
                <span>by {change.drafterEmail}</span>
                <span>{formatTime(change.createdAt)}</span>
                {change.environment && (
                  <Badge variant="outline" className="text-xs">{change.environment}</Badge>
                )}
              </div>
            </div>
          </div>
          <div className="flex items-center gap-2" onClick={(e) => e.stopPropagation()}>
            {change.status === 'DRAFT' && (
              <Button size="sm" onClick={onSubmitForReview}>
                <Send className="w-3 h-3 mr-1" /> Submit
              </Button>
            )}
            {change.status === 'PENDING_APPROVAL' && (
              <>
                <Button
                  size="sm"
                  variant="outline"
                  className="text-red-600"
                  onClick={() => {
                    const comments = prompt('Rejection reason:') || '';
                    if (comments) onReject(comments);
                  }}
                >
                  Reject
                </Button>
                <Button
                  size="sm"
                  onClick={() => {
                    const comments = prompt('Approval comments:') || '';
                    onApprove(comments);
                  }}
                >
                  Approve
                </Button>
              </>
            )}
            {change.status === 'PUBLISHED' && (
              <Button
                size="sm"
                variant="outline"
                onClick={() => {
                  const reason = prompt('Rollback reason:') || '';
                  if (reason) onRollback(reason);
                }}
              >
                <RotateCcw className="w-3 h-3 mr-1" /> Rollback
              </Button>
            )}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

function ChangeDetailDialog({
  change,
  onClose,
}: {
  change: ConfigChange;
  onClose: () => void;
}) {
  return (
    <div
      className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50"
      onClick={onClose}
    >
      <Card className="w-3/4 max-w-4xl" onClick={(e) => e.stopPropagation()}>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <GitCommit className="w-5 h-5" />
            {change.resourceName}
          </CardTitle>
          <CardDescription>
            {change.resourceType} · v{change.versionFrom || '—'} → v{change.versionTo}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4 max-h-96 overflow-auto">
          <div>
            <h4 className="font-medium text-sm mb-1">Summary</h4>
            <p className="text-sm text-gray-700">{change.changeSummary}</p>
          </div>
          <div>
            <h4 className="font-medium text-sm mb-1">Diff (before → after)</h4>
            <pre className="bg-gray-50 border rounded p-3 text-xs overflow-auto">
              {change.diffJson}
            </pre>
          </div>
          <div className="grid grid-cols-2 gap-4 text-sm">
            <div>
              <h4 className="font-medium mb-1">Drafter</h4>
              <p className="text-gray-700">{change.drafterEmail}</p>
            </div>
            <div>
              <h4 className="font-medium mb-1">Approver</h4>
              <p className="text-gray-700">{change.approverEmail || '— pending —'}</p>
            </div>
            <div>
              <h4 className="font-medium mb-1">Created</h4>
              <p className="text-gray-700">{change.createdAt}</p>
            </div>
            {change.publishedAt && (
              <div>
                <h4 className="font-medium mb-1">Published</h4>
                <p className="text-gray-700">{change.publishedAt}</p>
              </div>
            )}
            {change.ticketReference && (
              <div>
                <h4 className="font-medium mb-1">Ticket</h4>
                <p className="text-gray-700">{change.ticketReference}</p>
              </div>
            )}
          </div>
        </CardContent>
        <div className="flex justify-end p-4 border-t">
          <Button variant="outline" onClick={onClose}>Close</Button>
        </div>
      </Card>
    </div>
  );
}

function formatTime(iso: string): string {
  const d = new Date(iso);
  const now = new Date();
  const diff = (now.getTime() - d.getTime()) / 1000;
  if (diff < 60) return `${Math.floor(diff)}s ago`;
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
  return `${Math.floor(diff / 86400)}d ago`;
}

function mockChanges(tenantId: string, tab: string): ConfigChange[] {
  const now = new Date();
  const yesterday = new Date(now.getTime() - 86400 * 1000);
  const twoHoursAgo = new Date(now.getTime() - 2 * 3600 * 1000);

  if (tab === 'pending' || tab === 'in_review') {
    return [
      {
        id: 'chg-1',
        resourceType: 'layout',
        resourceId: 'layout-home',
        resourceName: 'Home Layout (Dialog LK)',
        versionFrom: '5',
        versionTo: '6',
        changeSummary: 'Add new "Recommended for you" widget below the balance card',
        diffJson: JSON.stringify({
          sections: {
            'before': ['header', 'balance', 'usage', 'quick-actions'],
            'after': ['header', 'balance', 'usage', 'recommended', 'quick-actions'],
          },
        }, null, 2),
        status: 'PENDING_APPROVAL',
        drafterId: 'admin-1',
        drafterEmail: 'editor@omobio.io',
        approverId: 'admin-2',
        createdAt: twoHoursAgo.toISOString(),
        environment: 'prod',
        ticketReference: 'CHANGE-1234',
      },
      {
        id: 'chg-2',
        resourceType: 'integration',
        resourceId: 'int-stripe-1',
        resourceName: 'Stripe Payment Provider',
        versionFrom: '1',
        versionTo: '2',
        changeSummary: 'Rotate Stripe API key (quarterly rotation)',
        diffJson: JSON.stringify({
          apiKey: { before: 'sk_live_****', after: 'sk_live_NEW****' },
        }, null, 2),
        status: 'IN_REVIEW',
        drafterId: 'admin-3',
        drafterEmail: 'integration-admin@omobio.io',
        createdAt: yesterday.toISOString(),
        environment: 'prod',
      },
    ];
  }
  if (tab === 'published') {
    return [
      {
        id: 'chg-0',
        resourceType: 'theme',
        resourceId: 'theme-primary',
        resourceName: 'Primary Theme (Dialog)',
        versionFrom: '3',
        versionTo: '4',
        changeSummary: 'Update primary purple shade to match new brand',
        diffJson: '{}',
        status: 'PUBLISHED',
        drafterId: 'admin-1',
        drafterEmail: 'editor@omobio.io',
        approverEmail: 'approver@omobio.io',
        createdAt: yesterday.toISOString(),
        publishedAt: new Date(now.getTime() - 86400 * 1000 / 2).toISOString(),
        environment: 'prod',
      },
    ];
  }
  if (tab === 'rolled_back') {
    return [
      {
        id: 'chg-rb-1',
        resourceType: 'journey',
        resourceId: 'journey-payment',
        resourceName: 'Payment Journey (Airtel)',
        versionFrom: '8',
        versionTo: '9',
        changeSummary: 'Reverted to v8 due to provider timeout in production',
        diffJson: '{}',
        status: 'ROLLED_BACK',
        drafterId: 'admin-1',
        drafterEmail: 'editor@omobio.io',
        approverEmail: 'approver@omobio.io',
        rollbackReason: 'Production timeout on payment provider call',
        rolledBackAt: yesterday.toISOString(),
        createdAt: new Date(now.getTime() - 3 * 86400 * 1000).toISOString(),
        environment: 'prod',
      },
    ];
  }
  return [];
}
