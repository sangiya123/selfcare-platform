import { useMemo, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from '@/components/ui/toast';
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
import { Switch } from '@/components/ui/switch';
import { Slider } from '@/components/ui/slider';
import { Checkbox } from '@/components/ui/checkbox';
import { Textarea } from '@/components/ui/textarea';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { api } from '@/lib/api';
import { useActiveTenant } from '@/hooks/useActiveTenant';
import {
  Flag,
  Plus,
  ToggleLeft,
  ToggleRight,
  Trash2,
  History,
  Calendar,
  User,
  AlertOctagon,
  AlertTriangle,
  ShieldCheck,
  Link2,
  Filter,
  Smartphone,
  X,
  Save,
  Loader2,
} from 'lucide-react';

/**
 * Feature Flag Manager — controls feature toggles per tenant.
 *
 * Features:
 * - Enable/disable features per tenant
 * - Gradual rollout (percentage)
 * - Prerequisites (required flags)
 * - Targeting rules (tenant / LOB / segment, AND logic)
 * - Owner + expiry + kill switch
 * - App version compatibility (iOS / Android min)
 * - Per-flag audit history
 */

type RuleScope = 'TENANT' | 'LOB' | 'SEGMENT';
type RuleOperator = 'EQUALS' | 'IN' | 'NOT_IN';

interface TargetingRule {
  id: string;
  scope: RuleScope;
  operator: RuleOperator;
  value: string;
}

interface FeatureFlag {
  key: string;
  name: string;
  description?: string;
  enabled: boolean;
  rolloutPercent: number;
  tenantScope: string;
  segments: string[];
  variants?: { name: string; weight: number }[];
  createdAt: string;
  updatedAt: string;
  // ---- extended fields ----
  prerequisites?: string[];
  targetingRules?: TargetingRule[];
  ownerName?: string;
  ownerEmail?: string;
  expiryDate?: string | null;     // ISO date or null = never
  status?: 'ACTIVE' | 'KILLED';   // KILLED = force-kill switch engaged
  appVersion?: {
    iosMin?: string;
    iosMax?: string;
    androidMin?: string;
    androidMax?: string;
  };
}

interface AuditEntry {
  id: string;
  timestamp: string;
  actorId?: string;
  actorName?: string;
  action: string;
  previousValue?: any;
  newValue?: any;
  flagKey?: string;
}

export default function FeatureFlagPage() {
  const queryClient = useQueryClient();
  const { activeTenantId } = useActiveTenant();

  // --- API queries ---
  const {
    data: flags = [],
    isLoading,
  } = useQuery<FeatureFlag[]>({
    queryKey: ['feature-flags', activeTenantId],
    queryFn: () => api.features.list(activeTenantId),
    enabled: !!activeTenantId,
  });

  // --- mutations ---
  const toggleMutation = useMutation({
    mutationFn: ({ key, enabled }: { key: string; enabled: boolean }) =>
      api.features.toggle(key, enabled),
    onSuccess: (_data, vars) => {
      queryClient.invalidateQueries({ queryKey: ['feature-flags', activeTenantId] });
      toast.success(`Flag ${vars.enabled ? 'enabled' : 'disabled'}`);
    },
    onError: () => toast.error('Failed to toggle flag'),
  });

  const saveMutation = useMutation({
    mutationFn: (flag: Partial<FeatureFlag>) => api.features.save(flag),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['feature-flags', activeTenantId] });
      toast.success('Feature flag saved');
    },
    onError: () => toast.error('Failed to save flag'),
  });

  const killMutation = useMutation({
    mutationFn: async (key: string) => {
      // Kill switch = save flag with status=KILLED and enabled=false
      return api.features.save({
        key,
        status: 'KILLED',
        enabled: false,
      });
    },
    onSuccess: (_data, key) => {
      queryClient.invalidateQueries({ queryKey: ['feature-flags', activeTenantId] });
      queryClient.invalidateQueries({ queryKey: ['audit', 'feature_flag'] });
      toast.success(`Flag "${key}" force-killed (audit logged)`);
    },
    onError: () => toast.error('Failed to kill flag'),
  });

  const deleteMutation = useMutation({
    mutationFn: (key: string) => api.features.save({ key, _delete: true } as any),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['feature-flags', activeTenantId] });
      toast.success('Flag deleted');
    },
    onError: () => toast.error('Failed to delete flag'),
  });

  // --- local UI state ---
  const [editing, setEditing] = useState<Partial<FeatureFlag> | null>(null);
  const [historyFlagKey, setHistoryFlagKey] = useState<string | null>(null);

  const openNew = () => {
    setEditing({
      key: '',
      name: '',
      description: '',
      enabled: false,
      rolloutPercent: 0,
      tenantScope: 'all',
      segments: [],
      prerequisites: [],
      targetingRules: [],
      ownerName: '',
      ownerEmail: '',
      expiryDate: null,
      appVersion: {},
      status: 'ACTIVE',
    });
  };

  const openEdit = (flag: FeatureFlag) => {
    setEditing({ ...flag });
  };

  const saveEditing = () => {
    if (!editing) return;
    if (!editing.key || !editing.name) {
      toast.error('Key and name are required');
      return;
    }
    saveMutation.mutate(editing);
    setEditing(null);
  };

  const handleToggle = (flag: FeatureFlag) => {
    const desired = !flag.enabled;

    // Prerequisites check
    if (desired && flag.prerequisites && flag.prerequisites.length > 0) {
      const missing = flag.prerequisites.filter(
        (k) => !flags.find((f) => f.key === k)?.enabled
      );
      if (missing.length > 0) {
        toast.warning(
          `Cannot enable: required flags are disabled — ${missing.join(', ')}`
        );
        return;
      }
    }

    toggleMutation.mutate({ key: flag.key, enabled: desired });
  };

  const handleDelete = (flag: FeatureFlag) => {
    if (confirm(`Delete feature flag "${flag.key}"?`)) {
      deleteMutation.mutate(flag.key);
    }
  };

  const handleKill = (flag: FeatureFlag) => {
    if (
      confirm(
        `Force-kill flag "${flag.key}"? This disables it immediately and is logged in the audit trail.`
      )
    ) {
      killMutation.mutate(flag.key);
    }
  };

  // --- derived ---
  const expiredFlags = useMemo(
    () =>
      flags.filter(
        (f) => f.expiryDate && new Date(f.expiryDate) < new Date()
      ),
    [flags]
  );

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <Flag className="w-6 h-6" />
            Feature Flags
          </h1>
          <p className="text-gray-500 mt-1">
            Control feature rollout per tenant and segment
          </p>
        </div>
        <Button onClick={openNew} disabled={!activeTenantId}>
          <Plus className="w-4 h-4 mr-2" /> New Flag
        </Button>
      </div>

      {/* Expired cleanup banner */}
      {expiredFlags.length > 0 && (
        <Card className="border-amber-300 bg-amber-50">
          <CardContent className="pt-4 flex items-start gap-3">
            <AlertTriangle className="w-5 h-5 text-amber-600 mt-0.5" />
            <div className="flex-1 text-sm">
              <div className="font-medium text-amber-900">
                {expiredFlags.length} flag{expiredFlags.length > 1 ? 's' : ''}{' '}
                past expiry
              </div>
              <div className="text-amber-800">
                {expiredFlags.map((f) => f.key).join(', ')} — clean up or
                extend the expiry date.
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Loading / empty state */}
      {isLoading && (
        <div className="text-sm text-gray-500 flex items-center gap-2">
          <Loader2 className="w-4 h-4 animate-spin" /> Loading flags…
        </div>
      )}
      {!isLoading && flags.length === 0 && (
        <Card>
          <CardContent className="py-8 text-center text-sm text-gray-500">
            No feature flags yet. Click <strong>New Flag</strong> to create one.
          </CardContent>
        </Card>
      )}

      {/* Flag list */}
      <div className="space-y-3">
        {flags.map((flag) => {
          const isKilled = flag.status === 'KILLED';
          const isExpired =
            !!flag.expiryDate && new Date(flag.expiryDate) < new Date();
          return (
            <Card
              key={flag.key}
              className={
                isKilled
                  ? 'border-red-400 border-2'
                  : isExpired
                  ? 'border-amber-300 border-2'
                  : ''
              }
            >
              <CardContent className="pt-6">
                <div className="flex items-center justify-between">
                  <div className="flex-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <code className="text-xs bg-gray-100 px-2 py-1 rounded">
                        {flag.key}
                      </code>
                      <h3 className="font-medium">{flag.name}</h3>
                      {isKilled ? (
                        <Badge variant="destructive" className="gap-1">
                          <AlertOctagon className="w-3 h-3" /> KILLED
                        </Badge>
                      ) : (
                        <Badge variant={flag.enabled ? 'default' : 'secondary'}>
                          {flag.enabled ? 'ON' : 'OFF'}
                        </Badge>
                      )}
                      {isExpired && (
                        <Badge
                          variant="outline"
                          className="text-amber-700 border-amber-400 gap-1"
                        >
                          <Calendar className="w-3 h-3" /> EXPIRED
                        </Badge>
                      )}
                      <Badge variant="outline">{flag.tenantScope}</Badge>
                      {flag.prerequisites && flag.prerequisites.length > 0 && (
                        <Badge
                          variant="outline"
                          className="gap-1 text-blue-700 border-blue-300"
                        >
                          <Link2 className="w-3 h-3" />
                          {flag.prerequisites.length} prereq
                        </Badge>
                      )}
                      {flag.targetingRules && flag.targetingRules.length > 0 && (
                        <Badge
                          variant="outline"
                          className="gap-1 text-purple-700 border-purple-300"
                        >
                          <Filter className="w-3 h-3" />
                          {flag.targetingRules.length} rule
                          {flag.targetingRules.length > 1 ? 's' : ''}
                        </Badge>
                      )}
                    </div>
                    {flag.description && (
                      <p className="text-sm text-gray-500 mt-1">
                        {flag.description}
                      </p>
                    )}

                    {/* Owner / expiry / app version meta-row */}
                    <div className="flex flex-wrap items-center gap-x-4 gap-y-1 mt-2 text-xs text-gray-500">
                      {(flag.ownerName || flag.ownerEmail) && (
                        <span className="flex items-center gap-1">
                          <User className="w-3 h-3" />
                          {flag.ownerName || flag.ownerEmail}
                          {flag.ownerName && flag.ownerEmail
                            ? ` (${flag.ownerEmail})`
                            : ''}
                        </span>
                      )}
                      {flag.expiryDate && (
                        <span
                          className={`flex items-center gap-1 ${
                            isExpired ? 'text-amber-700 font-medium' : ''
                          }`}
                        >
                          <Calendar className="w-3 h-3" />
                          {isExpired ? 'Expired ' : 'Expires '}
                          {new Date(flag.expiryDate).toLocaleDateString()}
                        </span>
                      )}
                      {flag.appVersion &&
                        (flag.appVersion.iosMin ||
                          flag.appVersion.androidMin) && (
                          <span className="flex items-center gap-1">
                            <Smartphone className="w-3 h-3" />
                            {flag.appVersion.iosMin
                              ? `iOS ≥ ${flag.appVersion.iosMin}`
                              : ''}
                            {flag.appVersion.iosMin &&
                            flag.appVersion.androidMin
                              ? ' / '
                              : ''}
                            {flag.appVersion.androidMin
                              ? `Android ≥ ${flag.appVersion.androidMin}`
                              : ''}
                          </span>
                        )}
                    </div>
                  </div>

                  <div className="flex items-center gap-1">
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => handleToggle(flag)}
                      disabled={isKilled || toggleMutation.isPending}
                      title={
                        isKilled
                          ? 'Killed flags cannot be re-enabled from here'
                          : flag.enabled
                          ? 'Disable'
                          : 'Enable'
                      }
                    >
                      {flag.enabled ? (
                        <ToggleRight className="w-6 h-6 text-green-500" />
                      ) : (
                        <ToggleLeft className="w-6 h-6 text-gray-400" />
                      )}
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => setHistoryFlagKey(flag.key)}
                      title="Change history"
                    >
                      <History className="w-4 h-4" />
                    </Button>
                    {!isKilled && (
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => handleKill(flag)}
                        title="Force kill"
                        className="text-red-500 hover:text-red-700"
                      >
                        <AlertOctagon className="w-4 h-4" />
                      </Button>
                    )}
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => openEdit(flag)}
                      title="Edit flag"
                    >
                      <Flag className="w-4 h-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => handleDelete(flag)}
                      title="Delete flag"
                    >
                      <Trash2 className="w-4 h-4 text-red-500" />
                    </Button>
                  </div>
                </div>

                {/* Rollout slider */}
                {flag.enabled && flag.rolloutPercent < 100 && !isKilled && (
                  <div className="mt-4">
                    <div className="flex items-center justify-between text-xs mb-1">
                      <span>Rollout</span>
                      <span>{flag.rolloutPercent}%</span>
                    </div>
                    <Slider
                      min={0}
                      max={100}
                      step={5}
                      value={[flag.rolloutPercent]}
                      onValueChange={(v) => {
                        saveMutation.mutate({
                          ...flag,
                          rolloutPercent: v[0],
                        });
                      }}
                    />
                  </div>
                )}
              </CardContent>
            </Card>
          );
        })}
      </div>

      {/* Edit / Create dialog */}
      {editing && (
        <FlagEditDialog
          editing={editing}
          setEditing={setEditing}
          availableFlags={flags}
          onClose={() => setEditing(null)}
          onSave={saveEditing}
          saving={saveMutation.isPending}
        />
      )}

      {/* Audit history dialog */}
      {historyFlagKey && (
        <HistoryDialog
          flagKey={historyFlagKey}
          onClose={() => setHistoryFlagKey(null)}
        />
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Edit / Create dialog                                                */
/* ------------------------------------------------------------------ */

function FlagEditDialog({
  editing,
  setEditing,
  availableFlags,
  onClose,
  onSave,
  saving,
}: {
  editing: Partial<FeatureFlag>;
  setEditing: (e: Partial<FeatureFlag> | null) => void;
  availableFlags: FeatureFlag[];
  onClose: () => void;
  onSave: () => void;
  saving: boolean;
}) {
  const otherFlags = availableFlags.filter((f) => f.key !== editing.key);
  const prereqs = editing.prerequisites ?? [];
  const rules = editing.targetingRules ?? [];

  const togglePrereq = (key: string, checked: boolean) => {
    const next = checked
      ? [...prereqs, key]
      : prereqs.filter((k) => k !== key);
    setEditing({ ...editing, prerequisites: next });
  };

  const addRule = () => {
    const newRule: TargetingRule = {
      id: `rule_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
      scope: 'LOB',
      operator: 'EQUALS',
      value: '',
    };
    setEditing({ ...editing, targetingRules: [...rules, newRule] });
  };

  const updateRule = (id: string, patch: Partial<TargetingRule>) => {
    setEditing({
      ...editing,
      targetingRules: rules.map((r) => (r.id === id ? { ...r, ...patch } : r)),
    });
  };

  const removeRule = (id: string) => {
    setEditing({
      ...editing,
      targetingRules: rules.filter((r) => r.id !== id),
    });
  };

  const neverExpires = !editing.expiryDate;

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Flag className="w-5 h-5" />
            {editing.key ? `Edit ${editing.key}` : 'New Feature Flag'}
          </DialogTitle>
          <DialogDescription>
            Configure rollout, prerequisites, targeting, ownership and expiry.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* Key + name */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label>Key (unique identifier)</Label>
              <Input
                value={editing.key || ''}
                onChange={(e) =>
                  setEditing({ ...editing, key: e.target.value })
                }
                placeholder="e.g. NEW_DASHBOARD_V2"
                disabled={!!editing.createdAt /* can't rename after create */}
              />
            </div>
            <div>
              <Label>Display name</Label>
              <Input
                value={editing.name || ''}
                onChange={(e) =>
                  setEditing({ ...editing, name: e.target.value })
                }
                placeholder="e.g. New Dashboard V2"
              />
            </div>
          </div>

          <div>
            <Label>Description</Label>
            <Textarea
              value={editing.description || ''}
              onChange={(e) =>
                setEditing({ ...editing, description: e.target.value })
              }
              rows={2}
            />
          </div>

          {/* Enabled + tenant scope */}
          <div className="grid grid-cols-2 gap-3 items-end">
            <div className="flex items-center justify-between border rounded-md p-3">
              <Label className="m-0">Enabled</Label>
              <Switch
                checked={!!editing.enabled}
                onCheckedChange={(c) =>
                  setEditing({ ...editing, enabled: c })
                }
              />
            </div>
            <div>
              <Label>Tenant scope</Label>
              <Select
                value={editing.tenantScope || 'all'}
                onValueChange={(v) =>
                  setEditing({ ...editing, tenantScope: v })
                }
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All tenants</SelectItem>
                  <SelectItem value="dialog-lk">Dialog only</SelectItem>
                  <SelectItem value="hutch-lk">Hutch only</SelectItem>
                  <SelectItem value="airtel-lk">Airtel only</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          {/* Rollout */}
          <div>
            <div className="flex items-center justify-between text-xs mb-2">
              <Label className="m-0">Rollout percentage</Label>
              <span className="font-mono">{editing.rolloutPercent ?? 0}%</span>
            </div>
            <Slider
              min={0}
              max={100}
              step={5}
              value={[editing.rolloutPercent ?? 0]}
              onValueChange={(v) =>
                setEditing({ ...editing, rolloutPercent: v[0] })
              }
            />
          </div>

          {/* Prerequisites */}
          <div className="border rounded-md p-3 space-y-2">
            <div className="flex items-center gap-2">
              <Link2 className="w-4 h-4 text-blue-600" />
              <Label className="m-0">Required flags (prerequisites)</Label>
            </div>
            <p className="text-xs text-gray-500">
              When this flag is enabled, all required flags must also be
              enabled.
            </p>
            <div className="max-h-40 overflow-y-auto space-y-1">
              {otherFlags.length === 0 && (
                <div className="text-xs text-gray-400 italic">
                  No other flags available
                </div>
              )}
              {otherFlags.map((f) => (
                <label
                  key={f.key}
                  className="flex items-center gap-2 text-sm cursor-pointer"
                >
                  <Checkbox
                    checked={prereqs.includes(f.key)}
                    onCheckedChange={(c) => togglePrereq(f.key, !!c)}
                  />
                  <code className="text-xs bg-gray-100 px-1.5 py-0.5 rounded">
                    {f.key}
                  </code>
                  <span className="text-gray-600">{f.name}</span>
                </label>
              ))}
            </div>
          </div>

          {/* Targeting rules */}
          <div className="border rounded-md p-3 space-y-2">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Filter className="w-4 h-4 text-purple-600" />
                <Label className="m-0">Targeting rules (AND logic)</Label>
              </div>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={addRule}
              >
                <Plus className="w-3 h-3 mr-1" /> Add rule
              </Button>
            </div>
            <p className="text-xs text-gray-500">
              Rules narrow the audience. All rules must match (AND).
            </p>
            {rules.length === 0 && (
              <div className="text-xs text-gray-400 italic">
                No rules — flag applies to all (within tenant scope)
              </div>
            )}
            {rules.map((rule) => (
              <div
                key={rule.id}
                className="grid grid-cols-12 gap-2 items-center"
              >
                <Select
                  value={rule.scope}
                  onValueChange={(v) =>
                    updateRule(rule.id, { scope: v as RuleScope })
                  }
                >
                  <SelectTrigger className="col-span-3">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="TENANT">TENANT</SelectItem>
                    <SelectItem value="LOB">LOB</SelectItem>
                    <SelectItem value="SEGMENT">SEGMENT</SelectItem>
                  </SelectContent>
                </Select>

                <Select
                  value={rule.operator}
                  onValueChange={(v) =>
                    updateRule(rule.id, { operator: v as RuleOperator })
                  }
                >
                  <SelectTrigger className="col-span-3">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="EQUALS">EQUALS</SelectItem>
                    <SelectItem value="IN">IN</SelectItem>
                    <SelectItem value="NOT_IN">NOT_IN</SelectItem>
                  </SelectContent>
                </Select>

                <Input
                  className="col-span-5"
                  placeholder="value (e.g. POSTPAID, dialog-lk)"
                  value={rule.value}
                  onChange={(e) =>
                    updateRule(rule.id, { value: e.target.value })
                  }
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="col-span-1"
                  onClick={() => removeRule(rule.id)}
                >
                  <X className="w-4 h-4 text-red-500" />
                </Button>
              </div>
            ))}
          </div>

          {/* Owner */}
          <div className="border rounded-md p-3 space-y-2">
            <div className="flex items-center gap-2">
              <User className="w-4 h-4 text-gray-600" />
              <Label className="m-0">Owner</Label>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <Input
                placeholder="Team / person name"
                value={editing.ownerName || ''}
                onChange={(e) =>
                  setEditing({ ...editing, ownerName: e.target.value })
                }
              />
              <Input
                placeholder="contact@example.com"
                type="email"
                value={editing.ownerEmail || ''}
                onChange={(e) =>
                  setEditing({ ...editing, ownerEmail: e.target.value })
                }
              />
            </div>
          </div>

          {/* Expiry */}
          <div className="border rounded-md p-3 space-y-2">
            <div className="flex items-center gap-2">
              <Calendar className="w-4 h-4 text-gray-600" />
              <Label className="m-0">Expiry</Label>
            </div>
            <div className="flex items-center gap-2">
              <input
                type="checkbox"
                id="never-expires"
                checked={neverExpires}
                onChange={(e) =>
                  setEditing({
                    ...editing,
                    expiryDate: e.target.checked ? null : new Date().toISOString().slice(0, 10),
                  })
                }
              />
              <label htmlFor="never-expires" className="text-sm">
                Never expires
              </label>
            </div>
            {!neverExpires && (
              <Input
                type="date"
                value={
                  editing.expiryDate
                    ? String(editing.expiryDate).slice(0, 10)
                    : ''
                }
                onChange={(e) =>
                  setEditing({
                    ...editing,
                    expiryDate: e.target.value || null,
                  })
                }
              />
            )}
          </div>

          {/* App version compatibility */}
          <div className="border rounded-md p-3 space-y-2">
            <div className="flex items-center gap-2">
              <Smartphone className="w-4 h-4 text-gray-600" />
              <Label className="m-0">App version compatibility</Label>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <div>
                <Label className="text-xs">iOS min</Label>
                <Input
                  placeholder="e.g. 3.2.0"
                  value={editing.appVersion?.iosMin || ''}
                  onChange={(e) =>
                    setEditing({
                      ...editing,
                      appVersion: {
                        ...editing.appVersion,
                        iosMin: e.target.value,
                      },
                    })
                  }
                />
              </div>
              <div>
                <Label className="text-xs">Android min</Label>
                <Input
                  placeholder="e.g. 3.2.0"
                  value={editing.appVersion?.androidMin || ''}
                  onChange={(e) =>
                    setEditing({
                      ...editing,
                      appVersion: {
                        ...editing.appVersion,
                        androidMin: e.target.value,
                      },
                    })
                  }
                />
              </div>
              <div>
                <Label className="text-xs">iOS max (optional)</Label>
                <Input
                  placeholder="e.g. 4.0.0"
                  value={editing.appVersion?.iosMax || ''}
                  onChange={(e) =>
                    setEditing({
                      ...editing,
                      appVersion: {
                        ...editing.appVersion,
                        iosMax: e.target.value,
                      },
                    })
                  }
                />
              </div>
              <div>
                <Label className="text-xs">Android max (optional)</Label>
                <Input
                  placeholder="e.g. 4.0.0"
                  value={editing.appVersion?.androidMax || ''}
                  onChange={(e) =>
                    setEditing({
                      ...editing,
                      appVersion: {
                        ...editing.appVersion,
                        androidMax: e.target.value,
                      },
                    })
                  }
                />
              </div>
            </div>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={onSave} disabled={saving}>
            {saving ? (
              <Loader2 className="w-4 h-4 mr-2 animate-spin" />
            ) : (
              <Save className="w-4 h-4 mr-2" />
            )}
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/* ------------------------------------------------------------------ */
/* History dialog                                                      */
/* ------------------------------------------------------------------ */

function HistoryDialog({
  flagKey,
  onClose,
}: {
  flagKey: string;
  onClose: () => void;
}) {
  const { data: history = [], isLoading } = useQuery<AuditEntry[]>({
    queryKey: ['audit', 'feature_flag', flagKey],
    queryFn: () => api.audit.list({ action: 'feature_flag:*' }),
  });

  // Filter to entries for this flag (server may return broader set; we narrow client-side)
  const entries = useMemo(
    () =>
      history
        .filter(
          (e) =>
            !e.flagKey ||
            e.flagKey === flagKey ||
            (e.action || '').includes(flagKey)
        )
        .sort(
          (a, b) =>
            new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
        ),
    [history, flagKey]
  );

  const renderValue = (v: any) => {
    if (v === undefined || v === null) return '—';
    if (typeof v === 'object') return JSON.stringify(v);
    return String(v);
  };

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <History className="w-5 h-5" /> Change history — {flagKey}
          </DialogTitle>
          <DialogDescription>
            Audit trail of all changes to this feature flag.
          </DialogDescription>
        </DialogHeader>

        {isLoading && (
          <div className="text-sm text-gray-500 flex items-center gap-2 py-4">
            <Loader2 className="w-4 h-4 animate-spin" /> Loading history…
          </div>
        )}

        {!isLoading && entries.length === 0 && (
          <div className="text-sm text-gray-500 italic py-4">
            No history entries found for this flag.
          </div>
        )}

        <div className="space-y-2">
          {entries.map((entry) => (
            <div
              key={entry.id}
              className="border rounded-md p-3 text-sm space-y-1"
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <ShieldCheck className="w-4 h-4 text-gray-500" />
                  <code className="text-xs bg-gray-100 px-1.5 py-0.5 rounded">
                    {entry.action}
                  </code>
                </div>
                <span className="text-xs text-gray-500">
                  {new Date(entry.timestamp).toLocaleString()}
                </span>
              </div>
              <div className="text-xs text-gray-600">
                <User className="w-3 h-3 inline mr-1" />
                {entry.actorName || entry.actorId || 'system'}
              </div>
              {(entry.previousValue !== undefined ||
                entry.newValue !== undefined) && (
                <div className="text-xs grid grid-cols-2 gap-2 mt-1">
                  <div>
                    <div className="text-gray-500">Previous</div>
                    <div className="font-mono bg-gray-50 rounded px-2 py-1">
                      {renderValue(entry.previousValue)}
                    </div>
                  </div>
                  <div>
                    <div className="text-gray-500">New</div>
                    <div className="font-mono bg-gray-50 rounded px-2 py-1">
                      {renderValue(entry.newValue)}
                    </div>
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Close
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
