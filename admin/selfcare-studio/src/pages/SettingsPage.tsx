/**
 * SettingsPage — global admin settings.
 *
 * Sections:
 *   - Profile: name, email, language
 *   - Notifications: which admin events email/Slack me
 *   - Auth & Security: login providers, OTP/SSO policy, session config, allowed origins
 *   - API tokens: personal access tokens for the admin API
 *   - Workspace: organization name, default time zone
 *   - Audit log: my recent admin actions
 *
 * Auth & Security covers the following from Admin Scope §9:
 *   - Enabled login providers (OTP, SSO/OIDC)
 *   - OTP policy (token length, expiry, attempts, lockout)
 *   - SSO/OIDC metadata (discovery URL, client ID, scopes)
 *   - Device/session policy (max sessions, device fingerprint)
 *   - Step-up thresholds (payment amount, config change)
 *   - Risk rules (new device, unusual location, velocity)
 *   - Session duration policy (access token TTL, refresh token TTL)
 *   - Allowed CORS origins
 *
 * API:
 *   GET/PUT /api/v1/admin/settings/auth
 *   GET/PUT /api/v1/admin/settings/auth/otp-policy
 *   GET/PUT /api/v1/admin/settings/auth/oidc
 *   GET/PUT /api/v1/admin/settings/auth/session-policy
 *   GET/PUT /api/v1/admin/settings/auth/risk-rules
 *   GET/PUT /api/v1/admin/settings/auth/allowed-origins
 */
import { useState } from 'react';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Switch } from '@/components/ui/switch';
import { Badge } from '@/components/ui/badge';
import {
  Settings as SettingsIcon,
  User,
  Bell,
  Key,
  Shield,
  Building,
  History,
  Copy,
  Trash2,
  Plus,
  Save,
  Globe,
  Smartphone,
  AlertTriangle,
} from 'lucide-react';

interface NotificationSetting {
  key: string;
  label: string;
  email: boolean;
  slack: boolean;
}

const DEFAULT_NOTIFICATIONS: NotificationSetting[] = [
  { key: 'config_published', label: 'Config published', email: true, slack: false },
  { key: 'config_rollback', label: 'Config rolled back', email: true, slack: true },
  { key: 'tenant_isolation_alert', label: 'Tenant isolation violation', email: true, slack: true },
  { key: 'payment_provider_down', label: 'Payment provider down', email: true, slack: true },
  { key: 'auth_outage', label: 'Auth service outage', email: true, slack: true },
  { key: 'widget_timeout_spike', label: 'Widget timeout spike', email: false, slack: true },
];

const MOCK_AUDIT = [
  { ts: '2026-09-03 14:22:18', actor: 'admin@omobio.io', action: 'published config', target: 'dialog-lk/home/v8' },
  { ts: '2026-09-03 13:11:02', actor: 'admin@omobio.io', action: 'updated integration', target: 'aia-lk/insurance-api' },
  { ts: '2026-09-03 11:05:33', actor: 'admin@omobio.io', action: 'created feature flag', target: 'AI_ASSISTANT_V2' },
  { ts: '2026-09-02 18:42:11', actor: 'admin@omobio.io', action: 'signed in', target: 'session from 10.0.4.21' },
  { ts: '2026-09-02 17:30:00', actor: 'admin@omobio.io', action: 'rolled back', target: 'dialog-lk/journey/onboarding/v4 → v3' },
];

export default function SettingsPage() {
  const [notifSettings, setNotifSettings] = useState(DEFAULT_NOTIFICATIONS);

  const toggle = (key: string, channel: 'email' | 'slack') => {
    setNotifSettings((prev) =>
      prev.map((n) => (n.key === key ? { ...n, [channel]: !n[channel] } : n))
    );
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold flex items-center gap-2">
          <SettingsIcon className="w-6 h-6" /> Settings
        </h1>
        <p className="text-gray-500 mt-1">Manage your admin profile and workspace.</p>
      </div>

      <Tabs defaultValue="profile">
        <TabsList>
          <TabsTrigger value="profile">
            <User className="w-4 h-4 mr-1" /> Profile
          </TabsTrigger>
          <TabsTrigger value="notifications">
            <Bell className="w-4 h-4 mr-1" /> Notifications
          </TabsTrigger>
          <TabsTrigger value="api-tokens">
            <Key className="w-4 h-4 mr-1" /> API Tokens
          </TabsTrigger>
          <TabsTrigger value="workspace">
            <Building className="w-4 h-4 mr-1" /> Workspace
          </TabsTrigger>
          <TabsTrigger value="auth-security">
            <Shield className="w-4 h-4 mr-1" /> Auth &amp; Security
          </TabsTrigger>
          <TabsTrigger value="audit">
            <History className="w-4 h-4 mr-1" /> My Audit Log
          </TabsTrigger>
        </TabsList>

        <TabsContent value="profile">
          <Card>
            <CardHeader>
              <CardTitle>Profile</CardTitle>
              <CardDescription>Your personal admin account</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4 max-w-md">
              <div>
                <Label>Display name</Label>
                <Input defaultValue="Platform Admin" />
              </div>
              <div>
                <Label>Email</Label>
                <Input type="email" defaultValue="admin@omobio.io" />
              </div>
              <div>
                <Label>Language</Label>
                <select className="w-full border rounded px-3 py-2 text-sm">
                  <option>English</option>
                  <option>Sinhala</option>
                  <option>Tamil</option>
                </select>
              </div>
              <div>
                <Label>Time zone</Label>
                <select className="w-full border rounded px-3 py-2 text-sm">
                  <option>Asia/Colombo (UTC+5:30)</option>
                  <option>Asia/Singapore (UTC+8)</option>
                  <option>UTC</option>
                </select>
              </div>
              <Button>Save Profile</Button>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="notifications">
          <Card>
            <CardHeader>
              <CardTitle>Admin Notifications</CardTitle>
              <CardDescription>
                Which events should email or Slack me?
              </CardDescription>
            </CardHeader>
            <CardContent>
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-gray-500 border-b">
                    <th className="font-normal py-2">Event</th>
                    <th className="font-normal py-2 text-center w-24">Email</th>
                    <th className="font-normal py-2 text-center w-24">Slack</th>
                  </tr>
                </thead>
                <tbody>
                  {notifSettings.map((n) => (
                    <tr key={n.key} className="border-b last:border-0">
                      <td className="py-3">{n.label}</td>
                      <td className="text-center">
                        <Switch
                          checked={n.email}
                          onCheckedChange={() => toggle(n.key, 'email')}
                        />
                      </td>
                      <td className="text-center">
                        <Switch
                          checked={n.slack}
                          onCheckedChange={() => toggle(n.key, 'slack')}
                        />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="api-tokens">
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle>API Tokens</CardTitle>
                  <CardDescription>Personal access tokens for the admin API</CardDescription>
                </div>
                <Button size="sm">
                  <Plus className="w-4 h-4 mr-1" /> New Token
                </Button>
              </div>
            </CardHeader>
            <CardContent>
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-gray-500 border-b">
                    <th className="font-normal py-2">Name</th>
                    <th className="font-normal py-2">Created</th>
                    <th className="font-normal py-2">Last used</th>
                    <th className="font-normal py-2">Scopes</th>
                    <th className="font-normal py-2 w-20"></th>
                  </tr>
                </thead>
                <tbody>
                  <tr className="border-b last:border-0">
                    <td className="py-3 font-mono text-xs">ci-deploy</td>
                    <td className="py-3 text-gray-600">2026-08-15</td>
                    <td className="py-3 text-gray-600">2 min ago</td>
                    <td className="py-3">
                      <Badge variant="outline" className="text-xs">read:config</Badge>{' '}
                      <Badge variant="outline" className="text-xs">write:config</Badge>
                    </td>
                    <td className="py-3 text-right">
                      <Button size="sm" variant="ghost">
                        <Trash2 className="w-3.5 h-3.5" />
                      </Button>
                    </td>
                  </tr>
                  <tr>
                    <td className="py-3 font-mono text-xs">local-dev</td>
                    <td className="py-3 text-gray-600">2026-07-02</td>
                    <td className="py-3 text-gray-600">3 days ago</td>
                    <td className="py-3">
                      <Badge variant="outline" className="text-xs">read:*</Badge>
                    </td>
                    <td className="py-3 text-right">
                      <Button size="sm" variant="ghost">
                        <Trash2 className="w-3.5 h-3.5" />
                      </Button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="workspace">
          <Card>
            <CardHeader>
              <CardTitle>Workspace</CardTitle>
              <CardDescription>Organization-level settings</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4 max-w-md">
              <div>
                <Label>Organization name</Label>
                <Input defaultValue="OMOBIO" />
              </div>
              <div>
                <Label>Default client</Label>
                <select className="w-full border rounded px-3 py-2 text-sm">
                  <option value="">— none —</option>
                  <option value="dialog-lk">dialog-lk</option>
                  <option value="aia-lk">aia-lk</option>
                </select>
              </div>
              <div>
                <Label>Session timeout (minutes)</Label>
                <Input type="number" defaultValue={30} />
              </div>
              <Button>Save Workspace</Button>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="auth-security">
          {/*
           * Auth & Security tab — covers Admin Scope §9:
           *   Login providers · OTP policy · SSO/OIDC · Device & session policy
           *   Step-up thresholds · Risk rules · CORS origins
           */}
          <div className="space-y-6">

            {/* ── Login providers ── */}
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Smartphone className="w-4 h-4" /> Login Providers
                </CardTitle>
                <CardDescription>Authentication methods available for admin sign-in</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="font-medium">Password + OTP</p>
                    <p className="text-sm text-gray-500">Email magic link or TOTP</p>
                  </div>
                  <Switch defaultChecked />
                </div>
                <div className="flex items-center justify-between">
                  <div>
                    <p className="font-medium">SSO / OIDC</p>
                    <p className="text-sm text-gray-500">SAML 2.0 or OpenID Connect</p>
                  </div>
                  <Switch defaultChecked />
                </div>
                <div className="flex items-center justify-between">
                  <div>
                    <p className="font-medium">Password only</p>
                    <p className="text-sm text-gray-500">Not recommended for production</p>
                  </div>
                  <Switch defaultChecked={false} />
                </div>
              </CardContent>
            </Card>

            {/* ── OTP policy ── */}
            <Card>
              <CardHeader>
                <CardTitle>OTP Policy</CardTitle>
                <CardDescription>One-time password settings for multi-factor auth</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4 max-w-lg">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <Label>Code length</Label>
                    <select className="w-full border rounded px-3 py-2 text-sm">
                      <option>6 digits</option>
                      <option>8 digits</option>
                    </select>
                  </div>
                  <div>
                    <Label>Algorithm</Label>
                    <select className="w-full border rounded px-3 py-2 text-sm">
                      <option>SHA1 (Google Authenticator compatible)</option>
                      <option>SHA256</option>
                      <option>SHA512</option>
                    </select>
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <Label>Code expiry (seconds)</Label>
                    <Input type="number" defaultValue={30} />
                  </div>
                  <div>
                    <Label>Max attempts before lockout</Label>
                    <Input type="number" defaultValue={5} />
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <Label>Backup codes per user</Label>
                    <Input type="number" defaultValue={10} />
                  </div>
                  <div>
                    <Label>Lockout duration (minutes)</Label>
                    <Input type="number" defaultValue={15} />
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  <Switch defaultChecked />
                  <div>
                    <p className="font-medium">Allow QR code scan</p>
                    <p className="text-sm text-gray-500">Show TOTP QR code during MFA setup</p>
                  </div>
                </div>
                <Button size="sm"><Save className="w-4 h-4 mr-1" /> Save OTP Policy</Button>
              </CardContent>
            </Card>

            {/* ── SSO / OIDC config ── */}
            <Card>
              <CardHeader>
                <CardTitle>SSO / OIDC Configuration</CardTitle>
                <CardDescription>Identity provider metadata for federated sign-in</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4 max-w-lg">
                <div>
                  <Label>Provider type</Label>
                  <select className="w-full border rounded px-3 py-2 text-sm">
                    <option>OpenID Connect</option>
                    <option>SAML 2.0</option>
                    <option>Disabled</option>
                  </select>
                </div>
                <div>
                  <Label>Discovery URL (OIDC)</Label>
                  <Input placeholder="https://idp.example.com/.well-known/openid-configuration" />
                </div>
                <div>
                  <Label>Client ID</Label>
                  <Input placeholder="omobio-admin-portal" />
                </div>
                <div>
                  <Label>Client Secret</Label>
                  <Input type="password" placeholder="••••••••••••" />
                </div>
                <div>
                  <Label>Scopes (space-separated)</Label>
                  <Input defaultValue="openid profile email" />
                </div>
                <div>
                  <Label>Sign-in button label</Label>
                  <Input defaultValue="Sign in with SSO" />
                </div>
                <Button size="sm"><Save className="w-4 h-4 mr-1" /> Save SSO Config</Button>
              </CardContent>
            </Card>

            {/* ── Session policy ── */}
            <Card>
              <CardHeader>
                <CardTitle>Session &amp; Device Policy</CardTitle>
                <CardDescription>Session lifetime and concurrent device limits</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4 max-w-lg">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <Label>Access token TTL (minutes)</Label>
                    <Input type="number" defaultValue={60} />
                  </div>
                  <div>
                    <Label>Refresh token TTL (days)</Label>
                    <Input type="number" defaultValue={30} />
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <Label>Max concurrent sessions per user</Label>
                    <Input type="number" defaultValue={3} />
                  </div>
                  <div>
                    <Label>Device fingerprint policy</Label>
                    <select className="w-full border rounded px-3 py-2 text-sm">
                      <option>Warn on new device</option>
                      <option>Block new device</option>
                      <option>Permissive (log only)</option>
                    </select>
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  <Switch defaultChecked />
                  <div>
                    <p className="font-medium">Enforce same-device re-auth</p>
                    <p className="text-sm text-gray-500">Require password for sensitive actions on new devices</p>
                  </div>
                </div>
                <Button size="sm"><Save className="w-4 h-4 mr-1" /> Save Session Policy</Button>
              </CardContent>
            </Card>

            {/* ── Step-up thresholds ── */}
            <Card>
              <CardHeader>
                <CardTitle>Step-Up Authentication Thresholds</CardTitle>
                <CardDescription>
                  Actions that always require re-verification (MFA or re-auth) regardless of session
                </CardDescription>
              </CardHeader>
              <CardContent>
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-left text-gray-500 border-b">
                      <th className="font-normal py-2">Action</th>
                      <th className="font-normal py-2 text-center">Enabled</th>
                      <th className="font-normal py-2 text-center">Threshold</th>
                    </tr>
                  </thead>
                  <tbody>
                    {[
                      { action: 'Payment above amount', enabled: true, defaultVal: '100.00' },
                      { action: 'Config publish (high-risk)', enabled: true, defaultVal: '—' },
                      { action: 'Role/permission change', enabled: true, defaultVal: '—' },
                      { action: 'Integration credential change', enabled: true, defaultVal: '—' },
                      { action: 'Bulk user action', enabled: true, defaultVal: '10 users' },
                      { action: 'API token creation', enabled: false, defaultVal: '—' },
                    ].map((s, i) => (
                      <tr key={i} className="border-b last:border-0">
                        <td className="py-3 font-medium">{s.action}</td>
                        <td className="text-center">
                          <Switch defaultChecked={s.enabled} />
                        </td>
                        <td className="text-center">
                          <Input
                            className="max-w-32 mx-auto text-center text-sm"
                            defaultValue={s.defaultVal}
                            disabled={s.defaultVal === '—'}
                          />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <Button size="sm" className="mt-4"><Save className="w-4 h-4 mr-1" /> Save Thresholds</Button>
              </CardContent>
            </Card>

            {/* ── Risk rules ── */}
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <AlertTriangle className="w-4 h-4" /> Risk Rules
                </CardTitle>
                <CardDescription>
                  Automated triggers for additional verification or blocking
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                {[
                  {
                    rule: 'New device login',
                    desc: 'Block or require step-up for logins from unseen devices',
                    enabled: true,
                    action: 'Block',
                  },
                  {
                    rule: 'Unusual location',
                    desc: 'Flag logins from IP geolocations outside usual countries',
                    enabled: true,
                    action: 'Warn',
                  },
                  {
                    rule: 'Login velocity',
                    desc: 'Trigger on >5 failed logins in 10 minutes per account',
                    enabled: true,
                    action: 'Block + Notify',
                  },
                  {
                    rule: 'Suspicious user agent',
                    desc: 'Block known automation tools and headless browsers',
                    enabled: false,
                    action: 'Block',
                  },
                ].map((r, i) => (
                  <div key={i} className="flex items-start gap-4 p-3 border rounded-lg">
                    <Switch defaultChecked={r.enabled} className="mt-1" />
                    <div className="flex-1">
                      <p className="font-medium">{r.rule}</p>
                      <p className="text-sm text-gray-500">{r.desc}</p>
                    </div>
                    <select className="border rounded px-2 py-1 text-sm">
                      <option>Block</option>
                      <option selected={r.action === 'Warn'}>Warn</option>
                      <option>Block + Notify</option>
                      <option>Allow</option>
                    </select>
                  </div>
                ))}
                <Button size="sm"><Save className="w-4 h-4 mr-1" /> Save Risk Rules</Button>
              </CardContent>
            </Card>

            {/* ── CORS / allowed origins ── */}
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Globe className="w-4 h-4" /> Allowed CORS Origins
                </CardTitle>
                <CardDescription>
                  Domains permitted to make cross-origin requests to the admin API
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="flex gap-2">
                  <Input placeholder="https://admin.omobio.example.com" className="flex-1" />
                  <Button size="sm"><Plus className="w-4 h-4" /></Button>
                </div>
                <div className="space-y-2">
                  {[
                    'https://admin.omobio.example.com',
                    'https://studio.omobio.example.com',
                    'https://selfcare.omobio.example.com',
                  ].map((origin, i) => (
                    <div key={i} className="flex items-center justify-between p-2 border rounded text-sm">
                      <span className="font-mono">{origin}</span>
                      <Button size="sm" variant="ghost">
                        <Trash2 className="w-3.5 h-3.5 text-red-500" />
                      </Button>
                    </div>
                  ))}
                </div>
                <Button size="sm"><Save className="w-4 h-4 mr-1" /> Save Origins</Button>
              </CardContent>
            </Card>

          </div>
        </TabsContent>

        <TabsContent value="audit">
          <Card>
            <CardHeader>
              <CardTitle>My Recent Activity</CardTitle>
              <CardDescription>Last 50 admin actions by you</CardDescription>
            </CardHeader>
            <CardContent>
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-gray-500 border-b">
                    <th className="font-normal py-2 w-44">Timestamp</th>
                    <th className="font-normal py-2 w-40">Actor</th>
                    <th className="font-normal py-2 w-44">Action</th>
                    <th className="font-normal py-2">Target</th>
                  </tr>
                </thead>
                <tbody>
                  {MOCK_AUDIT.map((a, i) => (
                    <tr key={i} className="border-b last:border-0">
                      <td className="py-2 font-mono text-xs text-gray-600">{a.ts}</td>
                      <td className="py-2 font-mono text-xs">{a.actor}</td>
                      <td className="py-2">
                        <Badge variant="outline">{a.action}</Badge>
                      </td>
                      <td className="py-2 font-mono text-xs text-gray-700">{a.target}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}
