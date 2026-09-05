import { ReactNode } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import {
  LayoutDashboard,
  FileText,
  Palette,
  Workflow,
  Plug,
  Flag,
  BarChart3,
  Brain,
  Building2,
  Settings,
  LogOut,
  Building,
  Shield,
  Briefcase,
  Layers,
  GitBranch,
  BookOpen,
  Bell,
  Package,
  Users,
  Image as ImageIcon,
  Navigation,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { useActiveTenant } from '@/hooks/useActiveTenant';
import { api } from '@/lib/api';

const navItems = [
  { href: '/', label: 'Dashboard', icon: LayoutDashboard },
  { href: '/tenants', label: 'Tenants / Clients', icon: Building2 },
  { href: '/clients', label: 'Branding', icon: Briefcase },
  { href: '/pages', label: 'Page Builder', icon: FileText },
  { href: '/theme', label: 'Theme Designer', icon: Palette },
  { href: '/journeys', label: 'Journey Builder', icon: Workflow },
  { href: '/integrations', label: 'Integrations', icon: Plug },
  { href: '/flags', label: 'Feature Flags', icon: Flag },
  { href: '/insurance', label: 'Insurance', icon: Shield },
  { href: '/industry-packs', label: 'Industry Packs', icon: Layers },
  { href: '/changes', label: 'Change Governance', icon: GitBranch },
  { href: '/reports', label: 'Reports', icon: BarChart3 },
  { href: '/ai', label: 'AI Studio', icon: Brain },
  { href: '/content', label: 'Content', icon: BookOpen },
  { href: '/notifications', label: 'Notifications', icon: Bell },
  { href: '/products', label: 'Product Mapping', icon: Package },
  { href: '/users', label: 'Users & Roles', icon: Users },
  { href: '/assets', label: 'Asset Manager', icon: ImageIcon },
  { href: '/navigation', label: 'Navigation', icon: Navigation },
];

export default function Layout({ children }: { children: ReactNode }) {
  const location = useLocation();
  const navigate = useNavigate();
  const { activeTenantId, setActiveTenantId, tenants } = useActiveTenant();

  const onSignOut = () => {
    api.clearToken();
    localStorage.removeItem('omobio-active-tenant');
    navigate('/login');
  };

  return (
    <div className="flex h-screen bg-gray-50">
      <aside className="w-64 bg-white border-r flex flex-col">
        <div className="p-4 border-b">
          <h1 className="text-lg font-bold text-purple-600">OMOBIO Studio</h1>
          <p className="text-xs text-gray-500">Multi-industry selfcare platform</p>
        </div>
        <nav className="flex-1 p-2 space-y-1">
          {navItems.map((item) => {
            const isActive = location.pathname === item.href;
            const Icon = item.icon;
            return (
              <Link
                key={item.href}
                to={item.href}
                className={cn(
                  'flex items-center gap-2 px-3 py-2 rounded text-sm transition-colors',
                  isActive
                    ? 'bg-purple-50 text-purple-700 font-medium'
                    : 'text-gray-700 hover:bg-gray-100'
                )}
              >
                <Icon className="w-4 h-4" />
                {item.label}
              </Link>
            );
          })}
        </nav>
        <div className="p-2 border-t space-y-1">
          <Link
            to="/settings"
            className="flex items-center gap-2 px-3 py-2 rounded text-sm text-gray-700 hover:bg-gray-100"
          >
            <Settings className="w-4 h-4" /> Settings
          </Link>
          <button
            onClick={onSignOut}
            className="w-full flex items-center gap-2 px-3 py-2 rounded text-sm text-gray-700 hover:bg-gray-100"
          >
            <LogOut className="w-4 h-4" /> Sign Out
          </button>
        </div>
      </aside>
      <main className="flex-1 overflow-auto">
        <div className="bg-white border-b px-6 py-2 flex items-center justify-end">
          <label className="flex items-center gap-2 text-sm text-gray-600">
            <Building className="w-4 h-4" />
            <span>Active client:</span>
            <select
              className="border rounded px-2 py-1 text-sm"
              value={activeTenantId}
              onChange={(e) => setActiveTenantId(e.target.value)}
            >
              {tenants.length === 0 ? (
                <option value="">— none —</option>
              ) : (
                tenants.map((t: any) => (
                  <option key={t.tenantId} value={t.tenantId}>
                    {t.tenantId}
                    {t.name ? ` · ${t.name}` : t.displayName ? ` · ${t.displayName}` : ''}
                    {t.industry ? ` · ${t.industry}` : ''}
                  </option>
                ))
              )}
            </select>
          </label>
        </div>
        <div className="p-6">{children}</div>
      </main>
    </div>
  );
}