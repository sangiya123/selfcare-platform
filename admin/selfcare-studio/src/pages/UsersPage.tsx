/**
 * UsersPage — manage admin users and roles for the active tenant.
 *
 * Replaces the need for AdminUserController UI. Provides a full CRUD
 * interface for admin users scoped to the active tenant, including
 * MFA status, last login tracking, role management, and account
 * lifecycle (disable / delete).
 *
 * Backend: GET  /api/v1/admin/users
 *          POST /api/v1/admin/users
 *          PUT  /api/v1/admin/users/{id}
 *          DELETE /api/v1/admin/users/{id}
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
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import {
  Users,
  Plus,
  Search,
  Shield,
  ShieldCheck,
  UserX,
  Trash2,
  Loader2,
  MoreHorizontal,
  CheckCircle,
  XCircle,
  Mail,
} from 'lucide-react';
import { api } from '@/lib/api';
import { useActiveTenant } from '@/hooks/useActiveTenant';

// ─── Types ────────────────────────────────────────────────────────────────────

export interface AdminUser {
  id: string;
  email: string;
  fullName: string;
  role: AdminRole;
  status: 'ACTIVE' | 'DISABLED' | 'PENDING';
  mfaEnabled: boolean;
  lastLogin?: string;
  createdAt: string;
  updatedAt?: string;
  createdBy?: string;
}

export type AdminRole = 'SUPER_ADMIN' | 'TENANT_ADMIN' | 'EDITOR' | 'VIEWER';

export type UserFormData = Partial<AdminUser> & { password?: string };

// ─── Mock fallback (backend not yet wired) ────────────────────────────────────

const MOCK_USERS: AdminUser[] = [
  {
    id: 'usr_001',
    email: 'samantha.fernando@omobio.io',
    fullName: 'Samantha Fernando',
    role: 'SUPER_ADMIN',
    status: 'ACTIVE',
    mfaEnabled: true,
    lastLogin: '2026-09-02T08:14:22Z',
    createdAt: '2024-01-15T10:00:00Z',
    createdBy: 'system',
  },
  {
    id: 'usr_002',
    email: 'roshan.de Silva@operator-x.com',
    fullName: 'Roshan de Silva',
    role: 'TENANT_ADMIN',
    status: 'ACTIVE',
    mfaEnabled: true,
    lastLogin: '2026-09-01T17:42:05Z',
    createdAt: '2024-03-22T09:30:00Z',
    createdBy: 'usr_001',
  },
  {
    id: 'usr_003',
    email: 'nimali.perera@operator-x.com',
    fullName: 'Nimali Perera',
    role: 'EDITOR',
    status: 'ACTIVE',
    mfaEnabled: false,
    lastLogin: '2026-08-28T11:05:33Z',
    createdAt: '2024-05-10T14:00:00Z',
    createdBy: 'usr_002',
  },
  {
    id: 'usr_004',
    email: 'kamal.weeraratne@operator-x.com',
    fullName: 'Kamal Weeraratne',
    role: 'VIEWER',
    status: 'DISABLED',
    mfaEnabled: false,
    lastLogin: '2026-07-15T09:00:00Z',
    createdAt: '2024-06-01T08:00:00Z',
    createdBy: 'usr_002',
  },
  {
    id: 'usr_005',
    email: 'dilini.rathnayake@operator-x.com',
    fullName: 'Dilini Rathnayake',
    role: 'EDITOR',
    status: 'PENDING',
    mfaEnabled: false,
    lastLogin: undefined,
    createdAt: '2026-09-01T16:20:00Z',
    createdBy: 'usr_002',
  },
];

// ─── API helpers (swap mock for real when backend lands) ─────────────────────

async function fetchUsers(): Promise<AdminUser[]> {
  try {
    return await api.get<AdminUser[]>('/api/v1/admin/users');
  } catch {
    // Backend not yet wired — fall back to mock data
    return MOCK_USERS;
  }
}

async function createUser(data: UserFormData): Promise<AdminUser> {
  try {
    return await api.post<AdminUser>('/api/v1/admin/users', data);
  } catch {
    // Mock: return a synthetic record
    const newUser: AdminUser = {
      id: `usr_${Date.now()}`,
      email: data.email || '',
      fullName: data.fullName || '',
      role: data.role || 'VIEWER',
      status: 'PENDING',
      mfaEnabled: false,
      createdAt: new Date().toISOString(),
    };
    MOCK_USERS.push(newUser);
    return newUser;
  }
}

async function updateUser(id: string, data: Partial<AdminUser>): Promise<AdminUser> {
  try {
    return await api.put<AdminUser>(`/api/v1/admin/users/${id}`, data);
  } catch {
    const idx = MOCK_USERS.findIndex((u) => u.id === id);
    if (idx !== -1) {
      MOCK_USERS[idx] = { ...MOCK_USERS[idx], ...data, updatedAt: new Date().toISOString() };
      return MOCK_USERS[idx];
    }
    throw new Error('User not found');
  }
}

async function removeUser(id: string): Promise<void> {
  try {
    await api.delete(`/api/v1/admin/users/${id}`);
  } catch {
    const idx = MOCK_USERS.findIndex((u) => u.id === id);
    if (idx !== -1) MOCK_USERS.splice(idx, 1);
  }
}

// ─── Role badge helper ────────────────────────────────────────────────────────

function RoleBadge({ role }: { role: AdminRole }) {
  const map: Record<AdminRole, { label: string; cls: string }> = {
    SUPER_ADMIN: { label: 'Super Admin', cls: 'bg-red-100 text-red-700' },
    TENANT_ADMIN: { label: 'Tenant Admin', cls: 'bg-purple-100 text-purple-700' },
    EDITOR: { label: 'Editor', cls: 'bg-blue-100 text-blue-700' },
    VIEWER: { label: 'Viewer', cls: 'bg-gray-100 text-gray-600' },
  };
  const { label, cls } = map[role];
  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium ${cls}`}>
      <Shield className="w-3 h-3" /> {label}
    </span>
  );
}

// ─── Create / Edit modal ──────────────────────────────────────────────────────

interface UserModalProps {
  user?: AdminUser;
  onClose: () => void;
  onSave: (data: UserFormData) => Promise<unknown>;
}

function UserModal({ user, onClose, onSave }: UserModalProps) {
  const isEdit = !!user;
  const [form, setForm] = useState({
    email: user?.email ?? '',
    fullName: user?.fullName ?? '',
    role: user?.role ?? 'VIEWER',
    status: user?.status ?? 'ACTIVE',
    password: '',
  });
  const [saving, setSaving] = useState(false);

  const set = (key: string, value: string) => setForm((f) => ({ ...f, [key]: value }));

  const handleSave = async () => {
    if (!form.email || (!isEdit && !form.password)) {
      toast.error('Email and password are required to create a user');
      return;
    }
    setSaving(true);
    try {
      await onSave({ ...form, password: isEdit ? undefined : form.password });
      toast.success(isEdit ? 'User updated' : 'User created');
      onClose();
    } catch (err: any) {
      toast.error(err?.message ?? 'Save failed');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{isEdit ? 'Edit User' : 'Create User'}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <div>
            <Label>Email</Label>
            <Input
              type="email"
              value={form.email}
              onChange={(e) => set('email', e.target.value)}
              disabled={isEdit}
              placeholder="user@example.com"
            />
          </div>
          <div>
            <Label>Full Name</Label>
            <Input
              value={form.fullName}
              onChange={(e) => set('fullName', e.target.value)}
              placeholder="Jane Doe"
            />
          </div>
          {!isEdit && (
            <div>
              <Label>Password</Label>
              <Input
                type="password"
                value={form.password}
                onChange={(e) => set('password', e.target.value)}
                placeholder="Min 8 characters"
              />
            </div>
          )}
          <div>
            <Label>Role</Label>
            <select
              className="w-full border rounded px-3 py-2 text-sm"
              value={form.role}
              onChange={(e) => set('role', e.target.value)}
            >
              <option value="SUPER_ADMIN">Super Admin</option>
              <option value="TENANT_ADMIN">Tenant Admin</option>
              <option value="EDITOR">Editor</option>
              <option value="VIEWER">Viewer</option>
            </select>
          </div>
          {isEdit && (
            <div>
              <Label>Status</Label>
              <select
                className="w-full border rounded px-3 py-2 text-sm"
                value={form.status}
                onChange={(e) => set('status', e.target.value)}
              >
                <option value="ACTIVE">Active</option>
                <option value="DISABLED">Disabled</option>
                <option value="PENDING">Pending</option>
              </select>
            </div>
          )}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button onClick={handleSave} disabled={saving}>
            {saving && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
            {isEdit ? 'Update' : 'Create User'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// ─── Delete confirmation modal ────────────────────────────────────────────────

interface DeleteModalProps {
  user: AdminUser;
  onClose: () => void;
  onConfirm: () => Promise<unknown>;
}

function DeleteModal({ user, onClose, onConfirm }: DeleteModalProps) {
  const [deleting, setDeleting] = useState(false);
  const handleDelete = async () => {
    setDeleting(true);
    try {
      await onConfirm();
      toast.success('User deleted');
      onClose();
    } catch (err: any) {
      toast.error(err?.message ?? 'Delete failed');
    } finally {
      setDeleting(false);
    }
  };
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>Delete User</DialogTitle>
        </DialogHeader>
        <p className="text-sm text-gray-600">
          Are you sure you want to delete <strong>{user.fullName}</strong> ({user.email})? This
          action cannot be undone.
        </p>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button variant="destructive" onClick={handleDelete} disabled={deleting}>
            {deleting && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
            Delete
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// ─── Main page ────────────────────────────────────────────────────────────────

export default function UsersPage() {
  const queryClient = useQueryClient();
  const { activeTenantId } = useActiveTenant();

  const [search, setSearch] = useState('');
  const [filterRole, setFilterRole] = useState<AdminRole | ''>('');
  const [filterStatus, setFilterStatus] = useState<AdminUser['status'] | ''>('');
  const [createOpen, setCreateOpen] = useState(false);
  const [editUser, setEditUser] = useState<AdminUser | null>(null);
  const [deleteUser, setDeleteUser] = useState<AdminUser | null>(null);

  const { data: users = [], isLoading } = useQuery({
    queryKey: ['admin-users', activeTenantId],
    queryFn: fetchUsers,
    enabled: !!activeTenantId,
  });

  const createMutation = useMutation({
    mutationFn: (data: UserFormData) => createUser(data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin-users', activeTenantId] }),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<AdminUser> }) =>
      updateUser(id, data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin-users', activeTenantId] }),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => removeUser(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin-users', activeTenantId] }),
  });

  const filtered = users.filter((u) => {
    const matchSearch =
      !search ||
      u.email.toLowerCase().includes(search.toLowerCase()) ||
      u.fullName.toLowerCase().includes(search.toLowerCase());
    const matchRole = !filterRole || u.role === filterRole;
    const matchStatus = !filterStatus || u.status === filterStatus;
    return matchSearch && matchRole && matchStatus;
  });

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <Users className="w-6 h-6" />
            Users &amp; Roles
          </h1>
          <p className="text-gray-500 mt-1">
            Manage admin users for{' '}
            <code className="bg-gray-100 px-1.5 py-0.5 rounded text-sm">{activeTenantId || '—'}</code>
          </p>
        </div>
        <Button onClick={() => setCreateOpen(true)}>
          <Plus className="w-4 h-4 mr-2" /> Add User
        </Button>
      </div>

      {/* Filters */}
      <Card>
        <CardContent className="pt-4">
          <div className="flex flex-wrap gap-3">
            <div className="flex-1 min-w-[200px]">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                <Input
                  placeholder="Search by email or name…"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  className="pl-9"
                />
              </div>
            </div>
            <select
              className="border rounded px-3 py-2 text-sm"
              value={filterRole}
              onChange={(e) => setFilterRole(e.target.value as AdminRole | '')}
            >
              <option value="">All Roles</option>
              <option value="SUPER_ADMIN">Super Admin</option>
              <option value="TENANT_ADMIN">Tenant Admin</option>
              <option value="EDITOR">Editor</option>
              <option value="VIEWER">Viewer</option>
            </select>
            <select
              className="border rounded px-3 py-2 text-sm"
              value={filterStatus}
              onChange={(e) => setFilterStatus(e.target.value as AdminUser['status'] | '')}
            >
              <option value="">All Statuses</option>
              <option value="ACTIVE">Active</option>
              <option value="DISABLED">Disabled</option>
              <option value="PENDING">Pending</option>
            </select>
          </div>
        </CardContent>
      </Card>

      {/* Table */}
      <Card>
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50 text-left">
                  <th className="px-4 py-3 font-medium text-gray-600">User</th>
                  <th className="px-4 py-3 font-medium text-gray-600">Role</th>
                  <th className="px-4 py-3 font-medium text-gray-600">Status</th>
                  <th className="px-4 py-3 font-medium text-gray-600">MFA</th>
                  <th className="px-4 py-3 font-medium text-gray-600">Last Login</th>
                  <th className="px-4 py-3 font-medium text-gray-600">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {isLoading ? (
                  <tr>
                    <td colSpan={6} className="text-center py-12 text-gray-500">
                      <Loader2 className="w-5 h-5 animate-spin mx-auto mb-2" />
                      Loading users…
                    </td>
                  </tr>
                ) : filtered.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="text-center py-12 text-gray-500">
                      No users match the current filters.
                    </td>
                  </tr>
                ) : (
                  filtered.map((user) => (
                    <tr key={user.id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3">
                        <div className="font-medium">{user.fullName}</div>
                        <div className="text-gray-500 flex items-center gap-1 text-xs">
                          <Mail className="w-3 h-3" /> {user.email}
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <RoleBadge role={user.role} />
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium ${
                            user.status === 'ACTIVE'
                              ? 'bg-green-100 text-green-700'
                              : user.status === 'DISABLED'
                              ? 'bg-red-100 text-red-700'
                              : 'bg-yellow-100 text-yellow-700'
                          }`}
                        >
                          {user.status === 'ACTIVE' ? (
                            <CheckCircle className="w-3 h-3" />
                          ) : user.status === 'DISABLED' ? (
                            <XCircle className="w-3 h-3" />
                          ) : (
                            <MoreHorizontal className="w-3 h-3" />
                          )}
                          {user.status.charAt(0) + user.status.slice(1).toLowerCase()}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        {user.mfaEnabled ? (
                          <ShieldCheck className="w-4 h-4 text-green-600" aria-label="MFA enabled" />
                        ) : (
                          <Shield className="w-4 h-4 text-gray-300" aria-label="MFA not enabled" />
                        )}
                      </td>
                      <td className="px-4 py-3 text-gray-500">
                        {user.lastLogin
                          ? new Date(user.lastLogin).toLocaleString(undefined, {
                              dateStyle: 'medium',
                              timeStyle: 'short',
                            })
                          : '—'}
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-1">
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => setEditUser(user)}
                            title="Edit user"
                          >
                            Edit
                          </Button>
                          <Button
                            size="sm"
                            variant="ghost"
                            className="text-red-600"
                            onClick={() => setDeleteUser(user)}
                            title="Delete user"
                          >
                            <Trash2 className="w-3 h-3" />
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>

      {/* Modals */}
      {createOpen && (
        <UserModal
          onClose={() => setCreateOpen(false)}
          onSave={(data) => createMutation.mutateAsync(data)}
        />
      )}
      {editUser && (
        <UserModal
          user={editUser}
          onClose={() => setEditUser(null)}
          onSave={(data) =>
            updateMutation.mutateAsync({ id: editUser.id, data })
          }
        />
      )}
      {deleteUser && (
        <DeleteModal
          user={deleteUser}
          onClose={() => setDeleteUser(null)}
          onConfirm={() => deleteMutation.mutateAsync(deleteUser.id)}
        />
      )}
    </div>
  );
}
