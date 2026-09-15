/**
 * LoginPage — admin sign-in.
 *
 * Admin identity uses SAML SSO for production (admin-identity-service).
 * For local dev, password fallback is allowed.
 *
 * Two-factor (TOTP) is required for users with the admin role.
 */
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { toast } from 'sonner';
import { Lock, Mail, Shield, KeyRound } from 'lucide-react';
import { api } from '@/lib/api';

interface LoginResult {
  userId: string;
  email: string;
  fullName: string;
  tenantId: string;
  role: string;
  sessionId: string;
  accessToken: string;
  refreshToken: string;
  mfaPending: boolean;
}

export default function LoginPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState<'credentials' | 'totp'>('credentials');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [totp, setTotp] = useState('');
  const [loading, setLoading] = useState(false);
  const [loginResult, setLoginResult] = useState<LoginResult | null>(null);

  const onSubmitCredentials = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.set('email', email);
      params.set('password', password);
      const result = await api.post<LoginResult>(`/api/v1/admin/auth/login?${params.toString()}`);
      setLoginResult(result);
      if (result.mfaPending) {
        setStep('totp');
      } else {
        api.setToken(result.accessToken);
        if (result.tenantId) {
          api.setActiveTenant(result.tenantId);
        }
        toast.success('Signed in');
        navigate('/');
      }
    } catch (err: any) {
      toast.error(err.message || 'Invalid credentials');
    } finally {
      setLoading(false);
    }
  };

  const onSubmitTotp = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      if (totp.length !== 6 || !/^\d{6}$/.test(totp)) {
        throw new Error('TOTP must be 6 digits');
      }
      if (!loginResult) {
        throw new Error('Session expired, please try again');
      }
      const result = await api.post<LoginResult>('/api/v1/admin/mfa/verify', {
        userId: loginResult.userId,
        totpCode: totp,
      });
      if (result.accessToken) {
        api.setToken(result.accessToken);
        if (result.tenantId) {
          api.setActiveTenant(result.tenantId);
        }
        toast.success('Signed in');
        navigate('/');
      } else {
        toast.error('Invalid verification code');
      }
    } catch (err: any) {
      toast.error(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-purple-50 via-white to-blue-50 p-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-6">
          <div className="inline-flex items-center justify-center w-14 h-14 rounded-xl bg-purple-600 text-white text-2xl font-bold mb-2">
            O
          </div>
          <h1 className="text-2xl font-bold text-gray-900">selfcare Studio</h1>
          <p className="text-sm text-gray-500">selfcare Platform admin</p>
        </div>

        <Card>
          {step === 'credentials' ? (
            <form onSubmit={onSubmitCredentials}>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Lock className="w-5 h-5" /> Sign in
                </CardTitle>
                <CardDescription>Use your corporate admin account</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div>
                  <Label htmlFor="login-email">Email</Label>
                  <div className="relative">
                    <Mail className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
                    <Input
                      id="login-email"
                      type="email"
                      required
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      placeholder="you@selfcare.io"
                      className="pl-9"
                    />
                  </div>
                </div>
                <div>
                  <Label htmlFor="login-password">Password</Label>
                  <div className="relative">
                    <Lock className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
                    <Input
                      id="login-password"
                      type="password"
                      required
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      className="pl-9"
                    />
                  </div>
                </div>
                <Button type="submit" className="w-full" disabled={loading}>
                  {loading ? 'Verifying…' : 'Continue'}
                </Button>
                <div className="relative">
                  <div className="absolute inset-0 flex items-center">
                    <div className="w-full border-t" />
                  </div>
                  <div className="relative flex justify-center text-xs">
                    <span className="bg-white px-2 text-gray-500">or</span>
                  </div>
                </div>
                <Button type="button" variant="outline" className="w-full">
                  <Shield className="w-4 h-4 mr-2" /> Continue with SAML SSO
                </Button>
              </CardContent>
            </form>
          ) : (
            <form onSubmit={onSubmitTotp}>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <KeyRound className="w-5 h-5" /> Two-factor authentication
                </CardTitle>
                <CardDescription>
                  Enter the 6-digit code from your authenticator app
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div>
                  <Label htmlFor="login-totp">Verification code</Label>
                  <Input
                    id="login-totp"
                    value={totp}
                    onChange={(e) => setTotp(e.target.value.replace(/\D/g, '').slice(0, 6))}
                    placeholder="000000"
                    inputMode="numeric"
                    className="text-center text-2xl tracking-widest font-mono"
                    autoFocus
                  />
                </div>
                <Button type="submit" className="w-full" disabled={loading || totp.length !== 6}>
                  {loading ? 'Verifying…' : 'Verify & sign in'}
                </Button>
                <Button
                  type="button"
                  variant="link"
                  className="w-full"
                  onClick={() => setStep('credentials')}
                >
                  ← Back
                </Button>
              </CardContent>
            </form>
          )}
        </Card>

        <p className="text-xs text-center text-gray-400 mt-4">
          Protected by selfcare admin identity. All access is logged.
        </p>
      </div>
    </div>
  );
}
