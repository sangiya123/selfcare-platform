import { useEffect, useState } from 'react';
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
  Plug,
  Plus,
  Trash2,
  TestTube2,
  CheckCircle2,
  XCircle,
  AlertCircle,
  Eye,
  EyeOff,
  Loader2,
} from 'lucide-react';
import { api } from '@/lib/api';
import { useActiveTenant } from '@/hooks/useActiveTenant';

/**
 * Integration Builder — configures client-specific API providers.
 *
 * All integration configs (base URLs, credentials, auth type, etc.)
 * are stored in MongoDB via /api/v1/admin/integrations and managed
 * through this UI. The provider beans fetch their config from the
 * TenantConfigurationService at runtime.
 *
 * Works across all industry packs. Examples:
 *   Telco industry pack:
 *     - Dialog MIFE / BSS / SMSC / Catalog
 *     - Hutch BSS / SMSC
 *     - Airtel Gateway
 *   Insurance industry pack:
 *     - AIA_INSURANCE (AIA policy / claims / beneficiaries / premiums)
 *   Cross-industry:
 *     - Stripe / Adyen (payments)
 *     - Twilio / MessageBird (SMS)
 *     - Firebase (push)
 *     - SendGrid (email)
 */
export default function IntegrationBuilderPage() {
  const queryClient = useQueryClient();
  const { activeTenantId } = useActiveTenant();
  const [editing, setEditing] = useState<Integration | null>(null);
  const [showSecrets, setShowSecrets] = useState<Record<string, boolean>>({});

  // Fetch integrations for the active tenant
  const { data: integrations = [], isLoading } = useQuery({
    queryKey: ['integrations', activeTenantId],
    queryFn: () => api.integrations.list(),
    enabled: !!activeTenantId,
  });

  const createMutation = useMutation({
    mutationFn: (newInt: Integration) => api.integrations.save(newInt),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['integrations', activeTenantId] });
      toast.success('Integration created');
    },
    onError: () => toast.error('Failed to create integration'),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Integration }) =>
      api.integrations.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['integrations', activeTenantId] });
      toast.success('Integration updated');
    },
    onError: () => toast.error('Failed to update integration'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.integrations.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['integrations', activeTenantId] });
      toast.success('Integration deleted');
    },
    onError: () => toast.error('Failed to delete integration'),
  });

  const testMutation = useMutation({
    mutationFn: (id: string) => api.integrations.test(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['integrations', activeTenantId] });
      toast.success('Connection test successful');
    },
    onError: () => toast.error('Connection test failed'),
  });

  const addIntegration = () => {
    setEditing({
      id: '',
      name: 'New Integration',
      industry: 'TELCO',
      type: 'CUSTOM',
      tenant: activeTenantId || '',
      status: 'DRAFT',
      baseUrl: '',
      authType: 'NONE',
      config: {},
      metadata: {},
      health: { status: 'UNKNOWN' },
    } as Integration);
  };

  const saveIntegration = async (integration: Integration) => {
    if (integration.id) {
      await updateMutation.mutateAsync({ id: integration.id, data: integration });
    } else {
      await createMutation.mutateAsync({ ...integration, tenantId: activeTenantId });
    }
    setEditing(null);
  };

  const deleteIntegration = async (id: string) => {
    if (confirm('Delete this integration? This cannot be undone.')) {
      await deleteMutation.mutateAsync(id);
    }
  };

  const testConnection = async (integration: Integration) => {
    if (integration.id) {
      await testMutation.mutateAsync(integration.id);
    } else {
      // Local test only — for new unsaved integrations
      toast.info('Save the integration first to test the connection');
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <Plug className="w-6 h-6" />
            Integration Builder
          </h1>
          <p className="text-gray-500 mt-1">Configure external API providers and adapters</p>
        </div>
        <Button onClick={addIntegration}>
          <Plus className="w-4 h-4 mr-2" /> Add Integration
        </Button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {isLoading ? (
          <div className="col-span-full flex items-center justify-center py-12 text-gray-500">
            <Loader2 className="w-6 h-6 animate-spin mr-2" /> Loading integrations...
          </div>
        ) : integrations.length === 0 ? (
          <div className="col-span-full text-center py-12 text-gray-500">
            No integrations configured for this tenant yet.
            <br />
            Click "Add Integration" to create one.
          </div>
        ) : (
          integrations.map((integration) => (
            <Card key={integration.id} className="hover:shadow-md transition-shadow">
              <CardHeader>
                <div className="flex items-start justify-between">
                  <div>
                    <CardTitle className="flex items-center gap-2">
                      {integration.name}
                      <HealthBadge status={integration.health?.status || 'UNKNOWN'} />
                    </CardTitle>
                    <CardDescription>{integration.integrationType || integration.type}</CardDescription>
                  </div>
                  <Badge variant={integration.status === 'ACTIVE' ? 'default' : 'secondary'}>
                    {integration.status}
                  </Badge>
                </div>
              </CardHeader>
              <CardContent>
                <div className="text-xs space-y-1 text-gray-600 mb-3">
                  <div><span className="font-medium">Base URL:</span> {integration.baseUrl || '—'}</div>
                  <div><span className="font-medium">Auth:</span> {integration.authType}</div>
                  {integration.health?.responseTimeMs && (
                    <div><span className="font-medium">Latency:</span> {integration.health.responseTimeMs}ms</div>
                  )}
                </div>
                <div className="flex gap-2">
                  <Button size="sm" variant="outline" onClick={() => testConnection(integration)}>
                    <TestTube2 className="w-3 h-3 mr-1" /> Test
                  </Button>
                  <Button size="sm" variant="outline" onClick={() => setEditing(integration)}>
                    Edit
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    className="text-red-600"
                    onClick={() => integration.id && deleteIntegration(integration.id)}
                  >
                    <Trash2 className="w-3 h-3 mr-1" /> Delete
                  </Button>
                </div>
              </CardContent>
            </Card>
          ))
        )}
      </div>

      {editing && (
        <Card>
          <CardHeader>
            <CardTitle>Edit: {editing.name}</CardTitle>
            <CardDescription>Configure connection settings and field mappings</CardDescription>
          </CardHeader>
          <CardContent>
            <Tabs defaultValue="connection">
              <TabsList>
                <TabsTrigger value="connection">Connection</TabsTrigger>
                <TabsTrigger value="auth">Authentication</TabsTrigger>
                <TabsTrigger value="mapping">Field Mapping</TabsTrigger>
                <TabsTrigger value="advanced">Advanced</TabsTrigger>
              </TabsList>

              <TabsContent value="connection" className="space-y-3">
                <div>
                  <Label>Name</Label>
                  <Input
                    value={editing.name}
                    onChange={(e) => setEditing({ ...editing, name: e.target.value })}
                  />
                </div>
                <div>
                  <Label>Industry pack</Label>
                  <select
                    className="w-full border rounded px-3 py-2"
                    value={editing.industry || 'TELCO'}
                    onChange={(e) => setEditing({ ...editing, industry: e.target.value })}
                  >
                    <option value="TELCO">Telco (operators, MVNOs)</option>
                    <option value="INSURANCE">Insurance (insurers, brokers)</option>
                    <option value="TRAVEL">Travel (airlines, hotels, agencies)</option>
                    <option value="BANKING">Banking &amp; Finance</option>
                    <option value="CROSS">Cross-industry (payments, notifications, AI)</option>
                  </select>
                </div>
                <div>
                  <Label>Integration type (e.g. DIALOG_MIFE, AIA_INSURANCE, STRIPE)</Label>
                  <Input
                    value={editing.integrationType || ''}
                    onChange={(e) => setEditing({ ...editing, integrationType: e.target.value })}
                    placeholder="AIA_INSURANCE"
                  />
                </div>
                <div>
                  <Label>Provider class (fully qualified)</Label>
                  <Input
                    value={editing.providerClass || ''}
                    onChange={(e) => setEditing({ ...editing, providerClass: e.target.value })}
                    placeholder="com.selfcare.dialog.provider.DialogAuthProvider"
                  />
                </div>
                <div>
                  <Label>Base URL</Label>
                  <Input
                    value={editing.baseUrl}
                    onChange={(e) => setEditing({ ...editing, baseUrl: e.target.value })}
                    placeholder="https://api.operator.com/v1"
                  />
                </div>
                <div>
                  <Label>Status</Label>
                  <select
                    className="w-full border rounded px-3 py-2"
                    value={editing.status}
                    onChange={(e) => setEditing({ ...editing, status: e.target.value as any })}
                  >
                    <option value="DRAFT">DRAFT</option>
                    <option value="ACTIVE">ACTIVE</option>
                    <option value="DISABLED">DISABLED</option>
                  </select>
                </div>
              </TabsContent>

              <TabsContent value="auth" className="space-y-3">
                <div>
                  <Label>Auth type</Label>
                  <select
                    className="w-full border rounded px-3 py-2"
                    value={editing.authType}
                    onChange={(e) => setEditing({ ...editing, authType: e.target.value as any })}
                  >
                    <option value="NONE">None</option>
                    <option value="API_KEY">API Key</option>
                    <option value="BASIC">Basic Auth</option>
                    <option value="OAUTH2_CLIENT_CREDENTIALS">OAuth2 Client Credentials</option>
                    <option value="MTLS">Mutual TLS</option>
                    <option value="JWT">JWT</option>
                  </select>
                </div>
                {editing.authType === 'API_KEY' && (
                  <div>
                    <Label>API Key (stored encrypted in DB)</Label>
                    <div className="flex gap-2">
                      <Input
                        type={showSecrets[editing.id || ''] ? 'text' : 'password'}
                        value={editing.credentials?.apiKey || ''}
                        onChange={(e) => setEditing({
                          ...editing,
                          credentials: { ...(editing.credentials || {}), apiKey: e.target.value }
                        })}
                        placeholder="••••••••"
                      />
                      <Button
                        variant="outline"
                        size="icon"
                        onClick={() => setShowSecrets((s) => ({ ...s, [editing.id || '']: !s[editing.id || ''] }))}
                      >
                        {showSecrets[editing.id || ''] ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                      </Button>
                    </div>
                  </div>
                )}
                {editing.authType === 'OAUTH2_CLIENT_CREDENTIALS' && (
                  <>
                    <div>
                      <Label>Client ID</Label>
                      <Input
                        value={editing.credentials?.clientId || ''}
                        onChange={(e) => setEditing({
                          ...editing,
                          credentials: { ...(editing.credentials || {}), clientId: e.target.value }
                        })}
                      />
                    </div>
                    <div>
                      <Label>Client Secret</Label>
                      <Input
                        type="password"
                        value={editing.credentials?.clientSecret || ''}
                        onChange={(e) => setEditing({
                          ...editing,
                          credentials: { ...(editing.credentials || {}), clientSecret: e.target.value }
                        })}
                      />
                    </div>
                    <div>
                      <Label>Token URL (optional, defaults to baseUrl + /oauth/token)</Label>
                      <Input
                        value={editing.credentials?.tokenUrl || ''}
                        onChange={(e) => setEditing({
                          ...editing,
                          credentials: { ...(editing.credentials || {}), tokenUrl: e.target.value }
                        })}
                        placeholder={editing.baseUrl ? `${editing.baseUrl.replace(/\/$/, '')}/oauth/token` : 'https://auth.operator.com/oauth/token'}
                      />
                    </div>
                  </>
                )}
              </TabsContent>

              <TabsContent value="mapping" className="space-y-3">
                <p className="text-sm text-gray-500 mb-3">
                  Map operator-specific fields to canonical platform fields.
                </p>
                <div className="grid grid-cols-2 gap-2">
                  <div className="font-medium text-sm">Canonical</div>
                  <div className="font-medium text-sm">Operator field</div>
                  {['connectionId', 'balance', 'currency', 'status'].map((canonical) => (
                    <div key={canonical} className="contents">
                      <Input value={canonical} disabled />
                      <Input placeholder="operator.field.path" />
                    </div>
                  ))}
                </div>
              </TabsContent>

              <TabsContent value="advanced" className="space-y-3">
                <div>
                  <Label>Retry policy</Label>
                  <select className="w-full border rounded px-3 py-2">
                    <option>Exponential backoff (3 retries)</option>
                    <option>Linear (2 retries)</option>
                    <option>None</option>
                  </select>
                </div>
                <div>
                  <Label>Circuit breaker threshold</Label>
                  <Input type="number" defaultValue={5} />
                </div>
                <div>
                  <Label>Rate limit (req/sec)</Label>
                  <Input type="number" defaultValue={100} />
                </div>
              </TabsContent>
            </Tabs>

            <div className="flex justify-end gap-2 mt-4">
              <Button variant="outline" onClick={() => setEditing(null)}>
                Cancel
              </Button>
              <Button
                onClick={() => editing && saveIntegration(editing)}
                disabled={createMutation.isPending}
              >
                {createMutation.isPending ? (
                  <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                ) : null}
                Save
              </Button>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

function HealthBadge({ status }: { status: string }) {
  if (status === 'HEALTHY') {
    return <CheckCircle2 className="w-4 h-4 text-green-500" />;
  }
  if (status === 'UNHEALTHY') {
    return <XCircle className="w-4 h-4 text-red-500" />;
  }
  return <AlertCircle className="w-4 h-4 text-gray-400" />;
}

interface Integration {
  id?: string;
  name: string;
  /** Industry pack: TELCO | INSURANCE | TRAVEL | BANKING | CROSS */
  industry?: string;
  integrationType?: string;
  type?: string;
  tenantId?: string;
  tenant?: string;
  status: 'ACTIVE' | 'DRAFT' | 'DISABLED';
  baseUrl: string;
  authType: 'NONE' | 'API_KEY' | 'BASIC' | 'OAUTH2_CLIENT_CREDENTIALS' | 'MTLS' | 'JWT';
  credentials?: Record<string, string>;
  fieldMapping?: Record<string, string>;
  config?: Record<string, any>;
  metadata?: Record<string, string>;
  health?: {
    status: 'HEALTHY' | 'UNHEALTHY' | 'UNKNOWN';
    lastChecked?: string;
    responseTimeMs?: number;
  };
  providerClass?: string;
}
