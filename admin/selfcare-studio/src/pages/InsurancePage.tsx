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
  Shield,
  FileText,
  Users,
  CreditCard,
  RefreshCw,
  Upload,
  Plus,
  CheckCircle2,
  Clock,
  XCircle,
  AlertCircle,
  Loader2,
  Eye,
} from 'lucide-react';
import { api } from '@/lib/api';
import { useActiveTenant } from '@/hooks/useActiveTenant';

/**
 * Insurance selfcare admin/operations page.
 *
 * Operators (AIA, etc.) configure their provider integration via
 * the Integrations page. Once configured, this page shows live data:
 *
 * Tabs:
 *   - Policies: list of customer policies, with renewal flow
 *   - Claims: list, file new claim, track status, upload docs
 *   - Beneficiaries: per-policy beneficiary management
 *   - Premiums: schedule, pay, auto-debit
 *
 * For the admin/customer-support view, customer ID is configurable.
 * In the production customer-facing app, customer ID comes from auth.
 */
export default function InsurancePage() {
  const { activeTenantId } = useActiveTenant();
  const [customerId, setCustomerId] = useState('demo-customer-001');
  const [selectedPolicyId, setSelectedPolicyId] = useState<string | null>(null);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <Shield className="w-6 h-6" />
            Insurance
          </h1>
          <p className="text-gray-500 mt-1">
            Manage policies, claims, beneficiaries, and premiums for{' '}
            <code className="text-sm bg-gray-100 px-1.5 py-0.5 rounded">{activeTenantId || '— no tenant —'}</code>
          </p>
        </div>
        <div className="flex gap-2">
          <Input
            value={customerId}
            onChange={(e) => setCustomerId(e.target.value)}
            placeholder="Customer ID"
            className="w-64"
          />
        </div>
      </div>

      <Tabs defaultValue="policies">
        <TabsList>
          <TabsTrigger value="policies">
            <Shield className="w-4 h-4 mr-1" /> Policies
          </TabsTrigger>
          <TabsTrigger value="claims">
            <FileText className="w-4 h-4 mr-1" /> Claims
          </TabsTrigger>
          <TabsTrigger value="beneficiaries">
            <Users className="w-4 h-4 mr-1" /> Beneficiaries
          </TabsTrigger>
          <TabsTrigger value="premiums">
            <CreditCard className="w-4 h-4 mr-1" /> Premiums
          </TabsTrigger>
        </TabsList>

        <TabsContent value="policies">
          <PoliciesTab customerId={customerId} onSelectPolicy={setSelectedPolicyId} />
        </TabsContent>

        <TabsContent value="claims">
          <ClaimsTab customerId={customerId} selectedPolicyId={selectedPolicyId} />
        </TabsContent>

        <TabsContent value="beneficiaries">
          <BeneficiariesTab selectedPolicyId={selectedPolicyId} />
        </TabsContent>

        <TabsContent value="premiums">
          <PremiumsTab selectedPolicyId={selectedPolicyId} />
        </TabsContent>
      </Tabs>
    </div>
  );
}

// ================================================================
// Policies Tab
// ================================================================

function PoliciesTab({ customerId, onSelectPolicy }: { customerId: string; onSelectPolicy: (id: string) => void }) {
  const { data: policies = [], isLoading } = useQuery({
    queryKey: ['policies', customerId],
    queryFn: () => api.insurance.policies.list(customerId),
    enabled: !!customerId,
  });

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {isLoading ? (
          <div className="col-span-full flex items-center justify-center py-12 text-gray-500">
            <Loader2 className="w-6 h-6 animate-spin mr-2" /> Loading policies...
          </div>
        ) : policies.length === 0 ? (
          <div className="col-span-full text-center py-12 text-gray-500">
            No insurance policies found for this customer.
          </div>
        ) : (
          policies.map((p: any) => (
            <Card key={p.id} className="hover:shadow-md">
              <CardHeader>
                <div className="flex items-start justify-between">
                  <div>
                    <CardTitle className="text-lg">
                      {p.planName || p.type}
                    </CardTitle>
                    <CardDescription>Policy #{p.policyNumber}</CardDescription>
                  </div>
                  <PolicyStatusBadge status={p.status} />
                </div>
              </CardHeader>
              <CardContent>
                <div className="space-y-1 text-sm text-gray-600 mb-3">
                  <div>
                    <span className="font-medium">Type:</span> {p.type}
                  </div>
                  <div>
                    <span className="font-medium">Sum assured:</span>{' '}
                    {p.currency} {p.sumAssured?.toLocaleString()}
                  </div>
                  <div>
                    <span className="font-medium">Premium:</span>{' '}
                    {p.currency} {p.premiumAmount?.toLocaleString()} / {p.premiumFrequency}
                  </div>
                  {p.nextPremiumDueDate && (
                    <div>
                      <span className="font-medium">Next due:</span> {p.nextPremiumDueDate}
                    </div>
                  )}
                  {p.maturityDate && (
                    <div>
                      <span className="font-medium">Maturity:</span> {p.maturityDate}
                    </div>
                  )}
                </div>
                <div className="flex gap-2">
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => onSelectPolicy(p.id)}
                  >
                    <Eye className="w-3 h-3 mr-1" /> Select
                  </Button>
                  <RenewalButton policyId={p.id} />
                </div>
              </CardContent>
            </Card>
          ))
        )}
      </div>
    </div>
  );
}

function PolicyStatusBadge({ status }: { status: string }) {
  const map: Record<string, { color: string; icon: any }> = {
    ACTIVE: { color: 'bg-green-100 text-green-700', icon: CheckCircle2 },
    LAPSE: { color: 'bg-yellow-100 text-yellow-700', icon: AlertCircle },
    SURRENDER: { color: 'bg-gray-100 text-gray-700', icon: XCircle },
    MATURED: { color: 'bg-blue-100 text-blue-700', icon: CheckCircle2 },
    PENDING: { color: 'bg-purple-100 text-purple-700', icon: Clock },
  };
  const cfg = map[status] || { color: 'bg-gray-100 text-gray-700', icon: AlertCircle };
  const Icon = cfg.icon;
  return (
    <span className={`text-xs px-2 py-1 rounded flex items-center gap-1 ${cfg.color}`}>
      <Icon className="w-3 h-3" /> {status}
    </span>
  );
}

function RenewalButton({ policyId }: { policyId: string }) {
  const queryClient = useQueryClient();
  const renewMutation = useMutation({
    mutationFn: () => api.insurance.policies.initiateRenewal(policyId),
    onSuccess: (data) => {
      if (data) {
        toast.success(`Renewal offer: ${data.newPremiumAmount} / ${data.newTermYears} years`);
        queryClient.invalidateQueries({ queryKey: ['policies'] });
      }
    },
    onError: () => toast.error('Failed to initiate renewal'),
  });

  return (
    <Button
      size="sm"
      variant="outline"
      onClick={() => renewMutation.mutate()}
      disabled={renewMutation.isPending}
    >
      <RefreshCw className={`w-3 h-3 mr-1 ${renewMutation.isPending ? 'animate-spin' : ''}`} />
      Renew
    </Button>
  );
}

// ================================================================
// Claims Tab
// ================================================================

function ClaimsTab({ customerId, selectedPolicyId }: { customerId: string; selectedPolicyId: string | null }) {
  const { data: claims = [], isLoading } = useQuery({
    queryKey: ['claims', customerId],
    queryFn: () => api.insurance.claims.list(customerId),
    enabled: !!customerId,
  });

  const [showFileClaim, setShowFileClaim] = useState(false);

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <Button onClick={() => setShowFileClaim(!showFileClaim)}>
          <Plus className="w-4 h-4 mr-1" /> File a Claim
        </Button>
      </div>

      {showFileClaim && (
        <FileClaimForm
          customerId={customerId}
          policyId={selectedPolicyId}
          onDone={() => setShowFileClaim(false)}
        />
      )}

      {isLoading ? (
        <div className="flex items-center justify-center py-12 text-gray-500">
          <Loader2 className="w-6 h-6 animate-spin mr-2" /> Loading claims...
        </div>
      ) : claims.length === 0 ? (
        <div className="text-center py-12 text-gray-500">No claims filed yet.</div>
      ) : (
        <div className="space-y-3">
          {claims.map((c: any) => (
            <ClaimCard key={c.id} claim={c} />
          ))}
        </div>
      )}
    </div>
  );
}

function FileClaimForm({ customerId, policyId, onDone }: { customerId: string; policyId: string | null; onDone: () => void }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({
    type: 'HOSPITALISATION',
    incidentDate: new Date().toISOString().slice(0, 10),
    description: '',
    claimedAmount: '',
  });

  const submitMutation = useMutation({
    mutationFn: () => api.insurance.claims.submit(customerId, policyId!, {
      type: form.type,
      incidentDate: form.incidentDate,
      description: form.description,
      claimedAmount: form.claimedAmount ? Number(form.claimedAmount) : undefined,
    }),
    onSuccess: (data) => {
      if (data?.success) {
        toast.success(`Claim filed: ${data.claimNumber}`);
        queryClient.invalidateQueries({ queryKey: ['claims'] });
        onDone();
      } else {
        toast.error(data?.failureReason || 'Failed to file claim');
      }
    },
    onError: () => toast.error('Failed to file claim'),
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>File a New Claim</CardTitle>
        <CardDescription>For policy {policyId || '— select a policy first —'}</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <Label>Claim type</Label>
            <select
              className="w-full border rounded px-3 py-2"
              value={form.type}
              onChange={(e) => setForm({ ...form, type: e.target.value })}
            >
              <option value="HOSPITALISATION">Hospitalisation</option>
              <option value="CRITICAL_ILLNESS">Critical Illness</option>
              <option value="MOTOR_ACCIDENT">Motor Accident</option>
              <option value="DEATH">Death</option>
              <option value="PERSONAL_ACCIDENT">Personal Accident</option>
              <option value="TRAVEL_DELAY">Travel Delay</option>
              <option value="OTHER">Other</option>
            </select>
          </div>
          <div>
            <Label>Incident date</Label>
            <Input
              type="date"
              value={form.incidentDate}
              onChange={(e) => setForm({ ...form, incidentDate: e.target.value })}
            />
          </div>
          <div className="col-span-2">
            <Label>Description</Label>
            <Input
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              placeholder="Brief description of the incident"
            />
          </div>
          <div>
            <Label>Claimed amount</Label>
            <Input
              type="number"
              value={form.claimedAmount}
              onChange={(e) => setForm({ ...form, claimedAmount: e.target.value })}
              placeholder="0.00"
            />
          </div>
        </div>
        <div className="flex gap-2 mt-4 justify-end">
          <Button variant="outline" onClick={onDone}>Cancel</Button>
          <Button
            onClick={() => submitMutation.mutate()}
            disabled={!policyId || submitMutation.isPending}
          >
            {submitMutation.isPending && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
            Submit Claim
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

function ClaimCard({ claim }: { claim: any }) {
  const [showUpload, setShowUpload] = useState(false);
  const { data: activities = [] } = useQuery({
    queryKey: ['claim-activities', claim.id],
    queryFn: () => api.insurance.claims.activities(claim.id),
  });

  return (
    <Card>
      <CardHeader>
        <div className="flex items-start justify-between">
          <div>
            <CardTitle className="text-base">
              {claim.type} — {claim.claimNumber}
            </CardTitle>
            <CardDescription>Filed on {claim.reportedDate}</CardDescription>
          </div>
          <Badge>{claim.status}</Badge>
        </div>
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-3 gap-3 text-sm">
          <div>
            <span className="text-gray-500">Claimed:</span>{' '}
            {claim.currency} {claim.claimedAmount?.toLocaleString()}
          </div>
          <div>
            <span className="text-gray-500">Approved:</span>{' '}
            {claim.currency} {claim.approvedAmount?.toLocaleString() || '—'}
          </div>
          <div>
            <span className="text-gray-500">Paid:</span>{' '}
            {claim.currency} {claim.paidAmount?.toLocaleString() || '—'}
          </div>
        </div>

        {activities.length > 0 && (
          <div className="mt-3">
            <h4 className="text-sm font-medium mb-2">Activity</h4>
            <ol className="border-l-2 border-gray-200 ml-2 space-y-2 pl-4">
              {activities.map((a: any, i: number) => (
                <li key={i} className="text-sm">
                  <div className="font-medium">{a.action}</div>
                  {a.notes && <div className="text-gray-500">{a.notes}</div>}
                  <div className="text-xs text-gray-400">
                    {a.timestamp} · {a.actor}
                  </div>
                </li>
              ))}
            </ol>
          </div>
        )}

        <div className="flex gap-2 mt-3">
          <Button size="sm" variant="outline" onClick={() => setShowUpload(!showUpload)}>
            <Upload className="w-3 h-3 mr-1" /> Upload Document
          </Button>
        </div>

        {showUpload && (
          <DocumentUpload claimId={claim.id} onDone={() => setShowUpload(false)} />
        )}
      </CardContent>
    </Card>
  );
}

function DocumentUpload({ claimId, onDone }: { claimId: string; onDone: () => void }) {
  const [file, setFile] = useState<File | null>(null);
  const queryClient = useQueryClient();
  const uploadMutation = useMutation({
    mutationFn: () => api.insurance.claims.uploadDocument(claimId, file!),
    onSuccess: (data) => {
      if (data?.success) {
        toast.success('Document uploaded');
        onDone();
      } else {
        toast.error(data?.failureReason || 'Upload failed');
      }
    },
    onError: () => toast.error('Upload failed'),
  });

  return (
    <div className="mt-3 flex gap-2 items-center">
      <input
        type="file"
        onChange={(e) => setFile(e.target.files?.[0] || null)}
        className="text-sm"
      />
      <Button size="sm" onClick={() => uploadMutation.mutate()} disabled={!file || uploadMutation.isPending}>
        Upload
      </Button>
    </div>
  );
}

// ================================================================
// Beneficiaries Tab
// ================================================================

function BeneficiariesTab({ selectedPolicyId }: { selectedPolicyId: string | null }) {
  const { data: beneficiaries = [], isLoading } = useQuery({
    queryKey: ['beneficiaries', selectedPolicyId],
    queryFn: () => api.insurance.beneficiaries.list(selectedPolicyId!),
    enabled: !!selectedPolicyId,
  });

  const queryClient = useQueryClient();
  const [showAdd, setShowAdd] = useState(false);

  const addMutation = useMutation({
    mutationFn: (data: any) => api.insurance.beneficiaries.add(selectedPolicyId!, data),
    onSuccess: (data) => {
      if (data?.success) {
        toast.success(`Beneficiary added${data.endorsementStatus ? ' (pending endorsement)' : ''}`);
        queryClient.invalidateQueries({ queryKey: ['beneficiaries'] });
        setShowAdd(false);
      } else {
        toast.error(data?.failureReason || 'Failed to add');
      }
    },
  });

  const removeMutation = useMutation({
    mutationFn: (id: string) => api.insurance.beneficiaries.remove(id),
    onSuccess: () => {
      toast.success('Removed');
      queryClient.invalidateQueries({ queryKey: ['beneficiaries'] });
    },
  });

  if (!selectedPolicyId) {
    return (
      <div className="text-center py-12 text-gray-500">
        Select a policy from the Policies tab to view beneficiaries.
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <Button onClick={() => setShowAdd(!showAdd)}>
          <Plus className="w-4 h-4 mr-1" /> Add Beneficiary
        </Button>
      </div>

      {showAdd && <AddBeneficiaryForm onSubmit={(d) => addMutation.mutate(d)} onCancel={() => setShowAdd(false)} />}

      {isLoading ? (
        <div className="flex items-center justify-center py-12 text-gray-500">
          <Loader2 className="w-6 h-6 animate-spin mr-2" /> Loading...
        </div>
      ) : beneficiaries.length === 0 ? (
        <div className="text-center py-12 text-gray-500">No beneficiaries for this policy.</div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          {beneficiaries.map((b: any) => (
            <Card key={b.id}>
              <CardContent className="p-4">
                <div className="flex items-start justify-between">
                  <div>
                    <div className="font-medium">{b.fullName}</div>
                    <div className="text-sm text-gray-500">
                      {b.relationship} · {b.allocationPercent}% allocation
                    </div>
                    <div className="text-xs text-gray-400 mt-1">
                      DOB: {b.dateOfBirth} · {b.mobile}
                    </div>
                  </div>
                  <Button
                    size="sm"
                    variant="outline"
                    className="text-red-600"
                    onClick={() => removeMutation.mutate(b.id)}
                  >
                    Remove
                  </Button>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}

function AddBeneficiaryForm({ onSubmit, onCancel }: { onSubmit: (d: any) => void; onCancel: () => void }) {
  const [form, setForm] = useState({
    fullName: '',
    nic: '',
    relationship: 'SPOUSE',
    dateOfBirth: '',
    allocationPercent: '50',
    mobile: '',
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Add Beneficiary</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <Label>Full name</Label>
            <Input value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
          </div>
          <div>
            <Label>Relationship</Label>
            <select
              className="w-full border rounded px-3 py-2"
              value={form.relationship}
              onChange={(e) => setForm({ ...form, relationship: e.target.value })}
            >
              <option value="SPOUSE">Spouse</option>
              <option value="CHILD">Child</option>
              <option value="PARENT">Parent</option>
              <option value="SIBLING">Sibling</option>
              <option value="SELF">Self</option>
              <option value="OTHER">Other</option>
            </select>
          </div>
          <div>
            <Label>NIC</Label>
            <Input value={form.nic} onChange={(e) => setForm({ ...form, nic: e.target.value })} />
          </div>
          <div>
            <Label>Date of birth</Label>
            <Input type="date" value={form.dateOfBirth} onChange={(e) => setForm({ ...form, dateOfBirth: e.target.value })} />
          </div>
          <div>
            <Label>Allocation %</Label>
            <Input
              type="number"
              min="0"
              max="100"
              value={form.allocationPercent}
              onChange={(e) => setForm({ ...form, allocationPercent: e.target.value })}
            />
          </div>
          <div>
            <Label>Mobile</Label>
            <Input value={form.mobile} onChange={(e) => setForm({ ...form, mobile: e.target.value })} />
          </div>
        </div>
        <div className="flex gap-2 justify-end mt-4">
          <Button variant="outline" onClick={onCancel}>Cancel</Button>
          <Button onClick={() => onSubmit(form)}>Add</Button>
        </div>
      </CardContent>
    </Card>
  );
}

// ================================================================
// Premiums Tab
// ================================================================

function PremiumsTab({ selectedPolicyId }: { selectedPolicyId: string | null }) {
  const { data: premiums = [], isLoading } = useQuery({
    queryKey: ['premiums', selectedPolicyId],
    queryFn: () => api.insurance.premiums.schedule(selectedPolicyId!),
    enabled: !!selectedPolicyId,
  });
  const { data: next } = useQuery({
    queryKey: ['next-premium', selectedPolicyId],
    queryFn: () => api.insurance.premiums.next(selectedPolicyId!),
    enabled: !!selectedPolicyId,
  });

  const queryClient = useQueryClient();
  const payMutation = useMutation({
    mutationFn: (id: string) =>
      api.insurance.premiums.pay(id, {
        method: 'CREDIT_CARD',
        paymentToken: 'mock-token',
        amount: 0,
        currency: 'LKR',
      }),
    onSuccess: () => {
      toast.success('Premium paid');
      queryClient.invalidateQueries({ queryKey: ['premiums'] });
    },
  });
  const setupAutoDebitMutation = useMutation({
    mutationFn: () => api.insurance.premiums.setupAutoDebit(selectedPolicyId!, {
      accountType: 'BANK',
      accountNumber: 'mock-account',
      bankCode: 'BOC',
      holderName: 'demo',
      firstDebitDate: '2026-10-01',
    }),
    onSuccess: (data) => {
      if (data?.success) {
        toast.success(`Auto-debit set up (mandate: ${data.mandateReference})`);
      } else {
        toast.error(data?.failureReason || 'Setup failed');
      }
    },
  });

  if (!selectedPolicyId) {
    return (
      <div className="text-center py-12 text-gray-500">
        Select a policy from the Policies tab to view premium schedule.
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {next && (
        <Card>
          <CardHeader>
            <CardTitle>Next Due Premium</CardTitle>
            <CardDescription>Due on {next.dueDate}</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex items-center justify-between">
              <div>
                <div className="text-2xl font-bold">
                  {next.currency} {next.dueAmount?.toLocaleString()}
                </div>
                <div className="text-sm text-gray-500">Status: {next.status}</div>
              </div>
              <div className="flex gap-2">
                <Button onClick={() => payMutation.mutate(next.id)} disabled={payMutation.isPending}>
                  Pay Now
                </Button>
                <Button
                  variant="outline"
                  onClick={() => setupAutoDebitMutation.mutate()}
                  disabled={setupAutoDebitMutation.isPending}
                >
                  Setup Auto-Debit
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {isLoading ? (
        <div className="flex items-center justify-center py-12 text-gray-500">
          <Loader2 className="w-6 h-6 animate-spin mr-2" /> Loading schedule...
        </div>
      ) : (
        <Card>
          <CardHeader>
            <CardTitle>Premium Schedule</CardTitle>
          </CardHeader>
          <CardContent>
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b">
                  <th className="text-left py-2">Due Date</th>
                  <th className="text-left py-2">Amount</th>
                  <th className="text-left py-2">Status</th>
                  <th className="text-left py-2">Paid</th>
                </tr>
              </thead>
              <tbody>
                {premiums.map((p: any) => (
                  <tr key={p.id} className="border-b">
                    <td className="py-2">{p.dueDate}</td>
                    <td className="py-2">{p.currency} {p.dueAmount?.toLocaleString()}</td>
                    <td className="py-2">
                      <Badge>{p.status}</Badge>
                    </td>
                    <td className="py-2 text-gray-500">
                      {p.paidDate ? `${p.paidDate} · ${p.paymentReference}` : '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
