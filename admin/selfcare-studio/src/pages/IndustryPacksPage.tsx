/**
 * IndustryPacksPage — view and manage industry pack definitions.
 *
 * An "industry pack" is the per-vertical provider implementation:
 *   - Telco pack: connection-centric, BalanceProvider, UsageProvider, RechargeProvider
 *   - Insurance pack: policy-centric, InsuranceProvider (policies, claims, premiums)
 *   - Travel pack: itinerary-centric (future)
 *   - Banking pack: account-centric (future)
 *
 * Each pack exposes a canonical adapter interface and a set of terminology
 * mappings. New clients are assigned a pack at tenant creation.
 *
 * Industry pack boundaries are HARD: cross-industry logic goes in
 * platform-common, not in a pack.
 */
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Layers, Phone, Shield, Plane, Banknote, Code, ArrowRight, Globe } from 'lucide-react';

interface IndustryPack {
  key: string;
  name: string;
  description: string;
  industries: string[];
  version: string;
  adapters: string[];
  terminology: { industry: string; canonical: string; example: string }[];
  status: 'STABLE' | 'BETA' | 'EXPERIMENTAL';
  clients: string[];
}

const PACKS: IndustryPack[] = [
  {
    key: 'telco',
    name: 'Telco Pack',
    description: 'Connection-centric mobile/broadband selfcare. Subscribers, MSISDNs, balance, usage, recharge, bill payment.',
    industries: ['TELCO'],
    version: '2.4.0',
    adapters: [
      'BalanceProvider',
      'UsageProvider',
      'RechargeProvider',
      'BillPaymentProvider',
      'ProductCatalogProvider',
      'AccountEntitlementProvider',
    ],
    terminology: [
      { industry: 'telco', canonical: 'connection', example: 'MSISDN / connection ID' },
      { industry: 'telco', canonical: 'subscriber', example: 'end customer' },
      { industry: 'telco', canonical: 'recharge', example: 'top-up via voucher / card' },
    ],
    status: 'STABLE',
    clients: ['dialog-lk', 'hutch-lk', 'airtel-lk'],
  },
  {
    key: 'insurance',
    name: 'Insurance Pack',
    description: 'Policy-centric insurance selfcare. Policies, claims, beneficiaries, premium payments.',
    industries: ['INSURANCE'],
    version: '1.8.0',
    adapters: [
      'InsuranceProvider',
      'ClaimProvider',
      'BeneficiaryProvider',
      'PremiumProvider',
      'DocumentProvider',
    ],
    terminology: [
      { industry: 'insurance', canonical: 'policy', example: 'life/health/motor policy' },
      { industry: 'insurance', canonical: 'policyholder', example: 'end customer' },
      { industry: 'insurance', canonical: 'premium', example: 'recurring payment' },
      { industry: 'insurance', canonical: 'claim', example: 'insurance claim' },
    ],
    status: 'STABLE',
    clients: ['aia-lk', 'aia-sg', 'aia-th', 'aia-my', 'aia-hk', 'aia-in'],
  },
  {
    key: 'travel',
    name: 'Travel Pack',
    description: 'Itinerary-centric selfcare for airlines and OTAs. Bookings, check-in, loyalty.',
    industries: ['TRAVEL'],
    version: '0.3.0',
    adapters: [
      'BookingProvider',
      'CheckInProvider',
      'LoyaltyProvider',
    ],
    terminology: [
      { industry: 'travel', canonical: 'booking', example: 'flight / hotel reservation' },
      { industry: 'travel', canonical: 'passenger', example: 'end customer' },
    ],
    status: 'BETA',
    clients: [],
  },
  {
    key: 'banking',
    name: 'Banking Pack',
    description: 'Account-centric selfcare for retail banks. Accounts, cards, transfers, statements.',
    industries: ['BANKING'],
    version: '0.1.0',
    adapters: [
      'AccountProvider',
      'CardProvider',
      'TransferProvider',
      'StatementProvider',
    ],
    terminology: [
      { industry: 'banking', canonical: 'account', example: 'savings / current account' },
      { industry: 'banking', canonical: 'account holder', example: 'end customer' },
    ],
    status: 'EXPERIMENTAL',
    clients: [],
  },
];

export default function IndustryPacksPage() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold flex items-center gap-2">
          <Layers className="w-6 h-6" />
          Industry Packs
        </h1>
        <p className="text-gray-500 mt-1">
          Per-vertical provider implementations. Cross-industry logic lives in
          <code className="bg-gray-100 px-1.5 py-0.5 rounded text-sm mx-1">platform-common</code>;
          industry-specific logic lives in the corresponding pack.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {PACKS.map((pack) => (
          <PackCard key={pack.key} pack={pack} />
        ))}
      </div>
    </div>
  );
}

function PackCard({ pack }: { pack: IndustryPack }) {
  return (
    <Card>
      <CardHeader>
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-2">
            <PackIcon packKey={pack.key} />
            <div>
              <CardTitle>{pack.name}</CardTitle>
              <CardDescription className="mt-1">
                v{pack.version} · {pack.adapters.length} adapters
              </CardDescription>
            </div>
          </div>
          <PackStatusBadge status={pack.status} />
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm text-gray-600">{pack.description}</p>

        <div>
          <div className="text-xs font-medium text-gray-500 mb-1">Industries</div>
          <div className="flex flex-wrap gap-1">
            {pack.industries.map((i) => (
              <Badge key={i} variant="secondary">
                {i}
              </Badge>
            ))}
          </div>
        </div>

        <div>
          <div className="text-xs font-medium text-gray-500 mb-1">Adapters</div>
          <div className="flex flex-wrap gap-1">
            {pack.adapters.map((a) => (
              <code key={a} className="text-xs bg-gray-100 px-2 py-0.5 rounded">
                {a}
              </code>
            ))}
          </div>
        </div>

        <div>
          <div className="text-xs font-medium text-gray-500 mb-1">Terminology</div>
          <table className="w-full text-xs">
            <thead>
              <tr className="text-left text-gray-500">
                <th className="font-normal">Canonical</th>
                <th className="font-normal">Example</th>
              </tr>
            </thead>
            <tbody>
              {pack.terminology.map((t) => (
                <tr key={t.canonical} className="border-t">
                  <td className="py-1 font-mono">{t.canonical}</td>
                  <td className="py-1 text-gray-600">{t.example}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div>
          <div className="text-xs font-medium text-gray-500 mb-1">
            Active clients ({pack.clients.length})
          </div>
          {pack.clients.length === 0 ? (
            <div className="text-xs text-gray-400 italic">none yet</div>
          ) : (
            <div className="flex flex-wrap gap-1">
              {pack.clients.map((c) => (
                <Badge key={c} variant="outline" className="font-mono text-xs">
                  {c}
                </Badge>
              ))}
            </div>
          )}
        </div>

        <div className="flex gap-2 pt-2 border-t">
          <Button size="sm" variant="outline">
            <Code className="w-3.5 h-3.5 mr-1" /> View Adapters
          </Button>
          <Button size="sm" variant="outline">
            <Globe className="w-3.5 h-3.5 mr-1" /> View Clients
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

function PackIcon({ packKey }: { packKey: string }) {
  switch (packKey) {
    case 'telco':
      return <Phone className="w-5 h-5 text-blue-500" />;
    case 'insurance':
      return <Shield className="w-5 h-5 text-green-500" />;
    case 'travel':
      return <Plane className="w-5 h-5 text-purple-500" />;
    case 'banking':
      return <Banknote className="w-5 h-5 text-yellow-500" />;
    default:
      return <Layers className="w-5 h-5 text-gray-500" />;
  }
}

function PackStatusBadge({ status }: { status: IndustryPack['status'] }) {
  const map: Record<IndustryPack['status'], { cls: string; label: string }> = {
    STABLE: { cls: 'bg-green-100 text-green-700', label: 'Stable' },
    BETA: { cls: 'bg-yellow-100 text-yellow-700', label: 'Beta' },
    EXPERIMENTAL: { cls: 'bg-gray-100 text-gray-600', label: 'Experimental' },
  };
  const m = map[status];
  return (
    <span className={`text-xs px-2 py-0.5 rounded ${m.cls}`}>{m.label}</span>
  );
}
