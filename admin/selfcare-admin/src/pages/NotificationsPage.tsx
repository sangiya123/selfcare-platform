/**
 * NotificationsPage — manage push notifications and in-app campaigns.
 *
 * Notifications are sent via the notification-service using pluggable
 * provider adapters (Firebase, Twilio, SendGrid). Campaign targeting
 * rules are tenant-scoped. Delivery metrics are aggregated from the
 * notification-service event stream.
 */
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Textarea } from '@/components/ui/textarea';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import {
  Bell,
  Plus,
  Send,
  Clock,
  Users,
  MousePointerClick,
  Eye,
  TrendingUp,
  Zap,
  Smartphone,
  Mail,
  MessageSquare,
  Calendar,
  Target,
  BarChart3,
  Loader2,
  ChevronDown,
  Copy,
  Trash2,
  Edit2,
} from 'lucide-react';
import { useActiveTenant } from '@/hooks/useActiveTenant';
import { api } from '@/lib/api';

// --- Types ---
interface Notification {
  id: string;
  title: string;
  body: string;
  sentAt: string;
  scheduledFor?: string;
  status: 'sent' | 'scheduled' | 'failed';
  reach: number;
  clickRate: number;
  channel: 'push' | 'sms' | 'email';
  priority: 'normal' | 'high';
  audience: string;
  deepLink?: string;
}

interface Campaign {
  id: string;
  title: string;
  description: string;
  bannerUrl: string;
  targetUrl: string;
  startDate: string;
  endDate: string;
  status: 'active' | 'inactive' | 'scheduled';
  audience: string;
  impressions: number;
  clicks: number;
  displayCondition: string;
}

interface Template {
  id: string;
  name: string;
  channel: 'push' | 'sms' | 'email';
  subject?: string;
  body: string;
  usageCount: number;
  lastUsed: string;
}

// --- Mock Data ---
const MOCK_NOTIFICATIONS: Notification[] = [
  { id: '1', title: 'Data Pack Expiry Reminder', body: 'Your 10GB data pack expires in 24 hours. Top up now to stay connected.', sentAt: '2025-08-28 09:00', status: 'sent', reach: 45230, clickRate: 12.4, channel: 'push', priority: 'high', audience: 'All users', deepLink: 'selfcare://data/topup' },
  { id: '2', title: 'Ramadan Offer 20% Off', body: 'Celebrate Ramadan with 20% off on all voice packs. Limited time only!', sentAt: '2025-08-30 10:00', status: 'sent', reach: 32100, clickRate: 18.7, channel: 'push', priority: 'normal', audience: 'Postpaid users' },
  { id: '3', title: 'Bill Payment Due', body: 'Your bill payment of LKR 1,250 is due on 5th. Pay now to avoid late fees.', sentAt: '2025-08-25 08:00', status: 'sent', reach: 12500, clickRate: 8.2, channel: 'sms', priority: 'high', audience: 'Postpaid users', deepLink: 'selfcare://billing/pay' },
  { id: '4', title: 'New Roaming Packs Available', body: 'Explore our new international roaming packs for your upcoming trips.', sentAt: '2025-09-02 11:00', status: 'scheduled', reach: 0, clickRate: 0, channel: 'push', priority: 'normal', audience: 'Frequent travelers' },
  { id: '5', title: 'Feedback Survey', body: 'We value your opinion! Complete our 2-minute survey and get 500MB free.', sentAt: '2025-08-20 14:00', status: 'sent', reach: 8900, clickRate: 22.1, channel: 'email', priority: 'normal', audience: 'All users' },
];

const MOCK_CAMPAIGNS: Campaign[] = [
  { id: '1', title: 'Summer Data Bonanza', description: 'Get extra 5GB on every data pack purchase this summer', bannerUrl: 'https://picsum.photos/seed/summer/800/300', targetUrl: 'selfcare://offers/summer', startDate: '2025-06-01', endDate: '2025-08-31', status: 'active', audience: 'All users', impressions: 125000, clicks: 8500, displayCondition: 'App launch' },
  { id: '2', title: 'New App Features Tour', description: 'Discover the new features in our latest app update', bannerUrl: 'https://picsum.photos/seed/features/800/300', targetUrl: 'selfcare://onboarding/features', startDate: '2025-08-15', endDate: '2025-09-30', status: 'active', audience: 'App version < 3.0', impressions: 45000, clicks: 3200, displayCondition: 'After login' },
  { id: '3', title: 'Loyalty Rewards Week', description: 'Double points on all transactions for loyalty members', bannerUrl: 'https://picsum.photos/seed/loyalty/800/300', targetUrl: 'selfcare://rewards', startDate: '2025-09-01', endDate: '2025-09-07', status: 'scheduled', audience: 'Gold members', impressions: 0, clicks: 0, displayCondition: 'First launch of week' },
  { id: '4', title: 'Back to School Offer', description: 'Special data packs for students at 30% discount', bannerUrl: 'https://picsum.photos/seed/school/800/300', targetUrl: 'selfcare://offers/student', startDate: '2025-07-15', endDate: '2025-09-15', status: 'inactive', audience: 'Age 18-25', impressions: 89000, clicks: 5600, displayCondition: 'App launch' },
];

const MOCK_TEMPLATES: Template[] = [
  { id: '1', name: 'Data Expiry Reminder', channel: 'push', body: 'Your {data_pack} expires on {expiry_date}. Top up now at {link}', usageCount: 145, lastUsed: '2025-08-28' },
  { id: '2', name: 'Bill Payment Reminder', channel: 'sms', body: 'Your bill of {amount} is due on {due_date}. Pay via {link} to avoid late fees.', usageCount: 89, lastUsed: '2025-08-25' },
  { id: '3', name: 'Welcome SMS', channel: 'sms', body: 'Welcome to {operator}! Your new number is {msisdn}. Download our app at {link}', usageCount: 234, lastUsed: '2025-08-30' },
  { id: '4', name: 'Offer Announcement', channel: 'push', body: '{offer_title} — {offer_description}. T&C apply. {link}', usageCount: 67, lastUsed: '2025-08-20' },
  { id: '5', name: 'Payment Confirmation', channel: 'email', subject: 'Payment Received - {reference}', body: 'Dear {customer_name},\n\nWe have received your payment of {amount}.\n\nReference: {reference}\nDate: {date}\n\nThank you for choosing {operator}.', usageCount: 312, lastUsed: '2025-08-29' },
  { id: '6', name: 'Feedback Request', channel: 'push', body: 'We\'d love your feedback! Rate your experience: {link}', usageCount: 45, lastUsed: '2025-08-15' },
];

const AUDIENCE_OPTIONS = [
  'All users',
  'Postpaid users',
  'Prepaid users',
  'New users (< 30 days)',
  'Frequent travelers',
  'High-value customers',
  'Inactive users (> 30 days)',
];

const DISPLAY_CONDITIONS = [
  'App launch',
  'After login',
  'First launch of day',
  'First launch of week',
  'After payment',
  'Low balance detected',
  'Data threshold reached',
];

// --- Component ---
export default function NotificationsPage() {
  const { activeTenantId } = useActiveTenant();
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<'push' | 'campaigns' | 'templates'>('push');
  const [showCreateNotification, setShowCreateNotification] = useState(false);
  const [showCreateCampaign, setShowCreateCampaign] = useState(false);
  const [showCreateTemplate, setShowCreateTemplate] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState<Template | null>(null);
  const [saving, setSaving] = useState(false);

  // Load templates from the notification-service admin API
  const { data: templates = [] } = useQuery({
    queryKey: ['notification-templates', activeTenantId],
    queryFn: async () => {
      const res = await api.notifications.templates.list(activeTenantId ?? '');
      const list = (Array.isArray(res) ? res : (res as any)?.data ?? []) as Template[];
      if (Array.isArray((res as any)?.content)) {
        return (res as any).content as Template[];
      }
      return list;
    },
    enabled: !!activeTenantId,
  });

  const saveTemplateMutation = useMutation({
    mutationFn: (data: any) =>
      editingTemplate?.id
        ? api.notifications.templates.update(editingTemplate.id, data)
        : api.notifications.templates.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notification-templates'] });
      toast.success(editingTemplate ? 'Template updated' : 'Template created');
      setShowCreateTemplate(false);
      setEditingTemplate(null);
    },
    onError: (err: any) => toast.error(err.message || 'Failed to save template'),
  });

  const deleteTemplateMutation = useMutation({
    mutationFn: (id: string) => api.notifications.templates.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notification-templates'] });
      toast.success('Template deleted');
    },
    onError: (err: any) => toast.error(err.message || 'Failed to delete template'),
  });

  // Notification form
  const [notificationForm, setNotificationForm] = useState({
    title: '',
    body: '',
    channel: 'push' as 'push' | 'sms' | 'email',
    audience: 'All users',
    priority: 'normal' as 'normal' | 'high',
    sendNow: true,
    scheduledDate: '',
    deepLink: '',
    enableAbTest: false,
    variantBTitle: '',
    variantBBody: '',
  });

  // Campaign form
  const [campaignForm, setCampaignForm] = useState({
    title: '',
    description: '',
    bannerUrl: '',
    targetUrl: '',
    startDate: '',
    endDate: '',
    audience: 'All users',
    displayCondition: 'App launch',
  });

  // Template form
  const [templateForm, setTemplateForm] = useState({
    name: '',
    channel: 'push' as 'push' | 'sms' | 'email',
    subject: '',
    body: '',
  });

  const handleSaveNotification = async () => {
    setSaving(true);
    await new Promise((r) => setTimeout(r, 800));
    setSaving(false);
    toast.success('Notification created');
    setShowCreateNotification(false);
  };

  const handleSaveCampaign = async () => {
    setSaving(true);
    await new Promise((r) => setTimeout(r, 800));
    setSaving(false);
    toast.success('Campaign created');
    setShowCreateCampaign(false);
  };

  const handleSaveTemplate = async () => {
    if (!templateForm.name || !templateForm.body) {
      toast.error('Template name and body are required');
      return;
    }
    setSaving(true);
    try {
      await saveTemplateMutation.mutateAsync({
        name: templateForm.name,
        channel: templateForm.channel,
        subject: templateForm.channel === 'email' ? templateForm.subject : undefined,
        body: templateForm.body,
      });
    } catch {
      // handled by mutation
    } finally {
      setSaving(false);
    }
  };

  const handleDeleteTemplate = (template: Template) => {
    if (confirm('Delete this template?')) {
      deleteTemplateMutation.mutate(template.id);
    }
  };

  const openEditTemplate = (template: Template) => {
    setEditingTemplate(template);
    setTemplateForm({
      name: template.name,
      channel: template.channel,
      subject: template.subject || '',
      body: template.body,
    });
    setShowCreateTemplate(true);
  };

  const totalSent = MOCK_NOTIFICATIONS.reduce((sum, n) => sum + n.reach, 0);
  const avgClickRate = (
    MOCK_NOTIFICATIONS.reduce((sum, n) => sum + n.clickRate * n.reach, 0) /
    totalSent
  ).toFixed(1);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold flex items-center gap-2">
          <Bell className="w-6 h-6" />
          Notifications & Campaigns
        </h1>
        <p className="text-gray-500 mt-1">
          Manage push notifications, in-app campaigns, and message templates for{' '}
          <code className="bg-gray-100 px-1.5 py-0.5 rounded text-sm">
            {activeTenantId || '— no client selected —'}
          </code>
        </p>
      </div>

      {/* Delivery metrics summary */}
      <div className="grid grid-cols-4 gap-4">
        <Card>
          <CardContent className="pt-4">
            <div className="text-sm text-gray-500">Total Sent</div>
            <div className="text-2xl font-bold">{totalSent.toLocaleString()}</div>
            <div className="text-xs text-gray-400 mt-1">Last 30 days</div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4">
            <div className="text-sm text-gray-500">Avg. Click Rate</div>
            <div className="text-2xl font-bold">{avgClickRate}%</div>
            <div className="text-xs text-gray-400 mt-1">Across all channels</div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4">
            <div className="text-sm text-gray-500">Active Campaigns</div>
            <div className="text-2xl font-bold">
              {MOCK_CAMPAIGNS.filter((c) => c.status === 'active').length}
            </div>
            <div className="text-xs text-gray-400 mt-1">Currently running</div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4">
            <div className="text-sm text-gray-500">Templates</div>
            <div className="text-2xl font-bold">{templates.length}</div>
            <div className="text-xs text-gray-400 mt-1">Available to use</div>
          </CardContent>
        </Card>
      </div>

      {/* Tabs */}
      <Tabs value={activeTab} onValueChange={(v) => setActiveTab(v as any)}>
        <TabsList>
          <TabsTrigger value="push">Push Notifications</TabsTrigger>
          <TabsTrigger value="campaigns">In-App Campaigns</TabsTrigger>
          <TabsTrigger value="templates">Templates</TabsTrigger>
        </TabsList>

        {/* Push Notifications Tab */}
        <TabsContent value="push" className="space-y-4">
          <div className="flex justify-end">
            <Button onClick={() => setShowCreateNotification(true)}>
              <Plus className="w-4 h-4 mr-2" />
              New Notification
            </Button>
          </div>

          <Card>
            <CardContent className="p-0">
              <table className="w-full">
                <thead>
                  <tr className="border-b bg-gray-50">
                    <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 uppercase">
                      Notification
                    </th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 uppercase">
                      Channel
                    </th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 uppercase">
                      Audience
                    </th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 uppercase">
                      Sent / Scheduled
                    </th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 uppercase">
                      Reach
                    </th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 uppercase">
                      Click Rate
                    </th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 uppercase">
                      Status
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {MOCK_NOTIFICATIONS.map((notification) => (
                    <tr key={notification.id} className="border-b hover:bg-gray-50">
                      <td className="px-4 py-3">
                        <div className="font-medium text-sm">{notification.title}</div>
                        <div className="text-xs text-gray-500 truncate max-w-xs">
                          {notification.body}
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <ChannelBadge channel={notification.channel} />
                      </td>
                      <td className="px-4 py-3 text-xs text-gray-600">
                        {notification.audience}
                      </td>
                      <td className="px-4 py-3 text-xs text-gray-500">
                        <span className="flex items-center gap-1">
                          {notification.status === 'scheduled' ? (
                            <Clock className="w-3 h-3" />
                          ) : (
                            <Send className="w-3 h-3" />
                          )}
                          {notification.scheduledFor || notification.sentAt}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-sm font-medium">
                        {notification.reach > 0 ? notification.reach.toLocaleString() : '—'}
                      </td>
                      <td className="px-4 py-3">
                        {notification.clickRate > 0 ? (
                          <span className="text-sm font-medium text-green-600">
                            {notification.clickRate}%
                          </span>
                        ) : (
                          '—'
                        )}
                      </td>
                      <td className="px-4 py-3">
                        <Badge variant={notification.status === 'sent' ? 'default' : 'secondary'}>
                          {notification.status}
                        </Badge>
                        {notification.priority === 'high' && (
                          <Zap className="w-3 h-3 text-orange-500 ml-1 inline" />
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </CardContent>
          </Card>
        </TabsContent>

        {/* In-App Campaigns Tab */}
        <TabsContent value="campaigns" className="space-y-4">
          <div className="flex justify-end">
            <Button onClick={() => setShowCreateCampaign(true)}>
              <Plus className="w-4 h-4 mr-2" />
              New Campaign
            </Button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {MOCK_CAMPAIGNS.map((campaign) => (
              <Card key={campaign.id} className="overflow-hidden">
                <div className="h-32 bg-gray-100">
                  <img
                    src={campaign.bannerUrl}
                    alt={campaign.title}
                    className="w-full h-full object-cover"
                    onError={(e) => {
                      (e.target as HTMLImageElement).src = 'https://picsum.photos/800/300?grayscale';
                    }}
                  />
                </div>
                <CardHeader className="pb-2">
                  <div className="flex items-start justify-between">
                    <div>
                      <CardTitle className="text-base">{campaign.title}</CardTitle>
                      <CardDescription className="text-xs mt-1">
                        {campaign.description}
                      </CardDescription>
                    </div>
                    <Badge
                      variant={
                        campaign.status === 'active'
                          ? 'default'
                          : campaign.status === 'scheduled'
                          ? 'secondary'
                          : 'outline'
                      }
                    >
                      {campaign.status}
                    </Badge>
                  </div>
                </CardHeader>
                <CardContent className="space-y-2">
                  <div className="flex items-center gap-4 text-xs text-gray-500">
                    <span className="flex items-center gap-1">
                      <Calendar className="w-3 h-3" />
                      {campaign.startDate} – {campaign.endDate}
                    </span>
                    <span className="flex items-center gap-1">
                      <Users className="w-3 h-3" />
                      {campaign.audience}
                    </span>
                  </div>
                  <div className="flex items-center gap-1 text-xs text-gray-500">
                    <Target className="w-3 h-3" />
                    Trigger: {campaign.displayCondition}
                  </div>
                  {campaign.impressions > 0 && (
                    <div className="flex items-center gap-4 pt-2 border-t">
                      <div>
                        <div className="text-lg font-bold">{campaign.impressions.toLocaleString()}</div>
                        <div className="text-xs text-gray-500">Impressions</div>
                      </div>
                      <div>
                        <div className="text-lg font-bold">{campaign.clicks.toLocaleString()}</div>
                        <div className="text-xs text-gray-500">Clicks</div>
                      </div>
                      <div>
                        <div className="text-lg font-bold text-green-600">
                          {((campaign.clicks / campaign.impressions) * 100).toFixed(1)}%
                        </div>
                        <div className="text-xs text-gray-500">CTR</div>
                      </div>
                    </div>
                  )}
                </CardContent>
              </Card>
            ))}
          </div>
        </TabsContent>

        {/* Templates Tab */}
        <TabsContent value="templates" className="space-y-4">
          <div className="flex justify-end">
            <Button onClick={() => setShowCreateTemplate(true)}>
              <Plus className="w-4 h-4 mr-2" />
              New Template
            </Button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {templates.map((template) => (
              <Card key={template.id} className="hover:shadow-md transition-shadow">
                <CardHeader className="pb-2">
                  <div className="flex items-start justify-between">
                    <div className="flex-1">
                      <CardTitle className="text-sm">{template.name}</CardTitle>
                      <div className="flex items-center gap-2 mt-1">
                        <ChannelBadge channel={template.channel} />
                        <span className="text-xs text-gray-400">
                          Used {template.usageCount} times
                        </span>
                      </div>
                    </div>
                  </div>
                </CardHeader>
                <CardContent>
                  <div className="bg-gray-50 rounded p-2 text-xs text-gray-600 font-mono whitespace-pre-wrap line-clamp-4">
                    {template.body}
                  </div>
                  <div className="flex items-center justify-between mt-3">
                    <span className="text-xs text-gray-400">
                      Last used: {template.lastUsed}
                    </span>
                    <div className="flex gap-1">
                      <Button size="sm" variant="ghost" onClick={() => openEditTemplate(template)}>
                        <Edit2 className="w-3 h-3" />
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        className="text-red-600"
                        onClick={() => handleDeleteTemplate(template)}
                      >
                        <Trash2 className="w-3 h-3" />
                      </Button>
                    </div>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </TabsContent>
      </Tabs>

      {/* Create Notification Modal */}
      <Dialog open={showCreateNotification} onOpenChange={setShowCreateNotification}>
        <DialogContent className="max-w-xl">
          <DialogHeader>
            <DialogTitle>Create Push Notification</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 max-h-[60vh] overflow-y-auto pr-2">
            <div>
              <Label>Title</Label>
              <Input
                value={notificationForm.title}
                onChange={(e) => setNotificationForm({ ...notificationForm, title: e.target.value })}
                placeholder="Enter notification title"
              />
            </div>
            <div>
              <Label>
                Body
                <span className="ml-2 text-xs text-gray-400 font-normal">
                  {notificationForm.body.length}/160
                </span>
              </Label>
              <Textarea
                value={notificationForm.body}
                onChange={(e) => setNotificationForm({ ...notificationForm, body: e.target.value })}
                placeholder="Enter notification body"
                rows={3}
                maxLength={160}
              />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <Label>Channel</Label>
                <select
                  className="w-full border rounded px-3 py-2"
                  value={notificationForm.channel}
                  onChange={(e) => setNotificationForm({ ...notificationForm, channel: e.target.value as any })}
                >
                  <option value="push">Push</option>
                  <option value="sms">SMS</option>
                  <option value="email">Email</option>
                </select>
              </div>
              <div>
                <Label>Priority</Label>
                <select
                  className="w-full border rounded px-3 py-2"
                  value={notificationForm.priority}
                  onChange={(e) => setNotificationForm({ ...notificationForm, priority: e.target.value as any })}
                >
                  <option value="normal">Normal</option>
                  <option value="high">High</option>
                </select>
              </div>
            </div>
            <div>
              <Label>Target Audience</Label>
              <select
                className="w-full border rounded px-3 py-2"
                value={notificationForm.audience}
                onChange={(e) => setNotificationForm({ ...notificationForm, audience: e.target.value })}
              >
                {AUDIENCE_OPTIONS.map((opt) => (
                  <option key={opt} value={opt}>{opt}</option>
                ))}
              </select>
            </div>
            <div>
              <Label>Deep Link / Action URL</Label>
              <Input
                value={notificationForm.deepLink}
                onChange={(e) => setNotificationForm({ ...notificationForm, deepLink: e.target.value })}
                placeholder="selfcare://data/topup"
              />
            </div>
            <div className="border rounded p-3 space-y-3">
              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="sendNow"
                  checked={notificationForm.sendNow}
                  onChange={(e) => setNotificationForm({ ...notificationForm, sendNow: e.target.checked })}
                />
                <Label htmlFor="sendNow" className="cursor-pointer">Send immediately</Label>
              </div>
              {!notificationForm.sendNow && (
                <Input
                  type="datetime-local"
                  value={notificationForm.scheduledDate}
                  onChange={(e) => setNotificationForm({ ...notificationForm, scheduledDate: e.target.value })}
                />
              )}
            </div>
            <div className="border rounded p-3">
              <div className="flex items-center gap-2 mb-3">
                <input
                  type="checkbox"
                  id="abTest"
                  checked={notificationForm.enableAbTest}
                  onChange={(e) => setNotificationForm({ ...notificationForm, enableAbTest: e.target.checked })}
                />
                <Label htmlFor="abTest" className="cursor-pointer font-medium">Enable A/B Testing</Label>
              </div>
              {notificationForm.enableAbTest && (
                <div className="space-y-3 pl-6">
                  <div>
                    <Label className="text-xs">Variant B - Title</Label>
                    <Input
                      value={notificationForm.variantBTitle}
                      onChange={(e) => setNotificationForm({ ...notificationForm, variantBTitle: e.target.value })}
                      placeholder="Alternate title"
                    />
                  </div>
                  <div>
                    <Label className="text-xs">Variant B - Body</Label>
                    <Textarea
                      value={notificationForm.variantBBody}
                      onChange={(e) => setNotificationForm({ ...notificationForm, variantBBody: e.target.value })}
                      placeholder="Alternate body"
                      rows={2}
                    />
                  </div>
                </div>
              )}
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowCreateNotification(false)}>
              Cancel
            </Button>
            <Button onClick={handleSaveNotification} disabled={saving}>
              {saving ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <Send className="w-4 h-4 mr-2" />}
              {saving ? 'Creating...' : 'Create Notification'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Create Campaign Modal */}
      <Dialog open={showCreateCampaign} onOpenChange={setShowCreateCampaign}>
        <DialogContent className="max-w-xl">
          <DialogHeader>
            <DialogTitle>Create In-App Campaign</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 max-h-[60vh] overflow-y-auto pr-2">
            <div>
              <Label>Campaign Title</Label>
              <Input
                value={campaignForm.title}
                onChange={(e) => setCampaignForm({ ...campaignForm, title: e.target.value })}
                placeholder="Enter campaign title"
              />
            </div>
            <div>
              <Label>Description</Label>
              <Textarea
                value={campaignForm.description}
                onChange={(e) => setCampaignForm({ ...campaignForm, description: e.target.value })}
                placeholder="Brief description of the campaign"
                rows={2}
              />
            </div>
            <div>
              <Label>Banner Image URL</Label>
              <Input
                value={campaignForm.bannerUrl}
                onChange={(e) => setCampaignForm({ ...campaignForm, bannerUrl: e.target.value })}
                placeholder="https://cdn.example.com/banner.jpg"
              />
              <p className="text-xs text-gray-400 mt-1">Recommended size: 800x300px</p>
            </div>
            <div>
              <Label>Target URL / Deep Link</Label>
              <Input
                value={campaignForm.targetUrl}
                onChange={(e) => setCampaignForm({ ...campaignForm, targetUrl: e.target.value })}
                placeholder="selfcare://offers/summer"
              />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <Label>Start Date</Label>
                <Input
                  type="date"
                  value={campaignForm.startDate}
                  onChange={(e) => setCampaignForm({ ...campaignForm, startDate: e.target.value })}
                />
              </div>
              <div>
                <Label>End Date</Label>
                <Input
                  type="date"
                  value={campaignForm.endDate}
                  onChange={(e) => setCampaignForm({ ...campaignForm, endDate: e.target.value })}
                />
              </div>
            </div>
            <div>
              <Label>Target Audience</Label>
              <select
                className="w-full border rounded px-3 py-2"
                value={campaignForm.audience}
                onChange={(e) => setCampaignForm({ ...campaignForm, audience: e.target.value })}
              >
                {AUDIENCE_OPTIONS.map((opt) => (
                  <option key={opt} value={opt}>{opt}</option>
                ))}
              </select>
            </div>
            <div>
              <Label>Display Condition</Label>
              <select
                className="w-full border rounded px-3 py-2"
                value={campaignForm.displayCondition}
                onChange={(e) => setCampaignForm({ ...campaignForm, displayCondition: e.target.value })}
              >
                {DISPLAY_CONDITIONS.map((cond) => (
                  <option key={cond} value={cond}>{cond}</option>
                ))}
              </select>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowCreateCampaign(false)}>
              Cancel
            </Button>
            <Button onClick={handleSaveCampaign} disabled={saving}>
              {saving ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : null}
              {saving ? 'Creating...' : 'Create Campaign'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Create / Edit Template Modal */}
      <Dialog open={showCreateTemplate} onOpenChange={(open) => { setShowCreateTemplate(open); if (!open) setEditingTemplate(null); }}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>
              {editingTemplate ? 'Edit Template' : 'Create Template'}
            </DialogTitle>
          </DialogHeader>
          <div className="space-y-4 max-h-[60vh] overflow-y-auto pr-2">
            <div>
              <Label>Template Name</Label>
              <Input
                value={templateForm.name}
                onChange={(e) => setTemplateForm({ ...templateForm, name: e.target.value })}
                placeholder="Enter template name"
              />
            </div>
            <div>
              <Label>Channel</Label>
              <select
                className="w-full border rounded px-3 py-2"
                value={templateForm.channel}
                onChange={(e) => setTemplateForm({ ...templateForm, channel: e.target.value as any })}
              >
                <option value="push">Push</option>
                <option value="sms">SMS</option>
                <option value="email">Email</option>
              </select>
            </div>
            {templateForm.channel === 'email' && (
              <div>
                <Label>Subject Line</Label>
                <Input
                  value={templateForm.subject}
                  onChange={(e) => setTemplateForm({ ...templateForm, subject: e.target.value })}
                  placeholder="Email subject line (use {variables})"
                />
              </div>
            )}
            <div>
              <Label>Body</Label>
              <Textarea
                value={templateForm.body}
                onChange={(e) => setTemplateForm({ ...templateForm, body: e.target.value })}
                placeholder="Message body. Use {variable_name} for dynamic content."
                rows={6}
              />
              <p className="text-xs text-gray-400 mt-1">
                Variables: {'{customer_name}'}, {'{amount}'}, {'{date}'}, {'{link}'}, etc.
              </p>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => { setShowCreateTemplate(false); setEditingTemplate(null); }}>
              Cancel
            </Button>
            <Button onClick={handleSaveTemplate} disabled={saving}>
              {saving ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : null}
              {saving ? 'Saving...' : 'Save Template'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

// --- Sub-components ---
function ChannelBadge({ channel }: { channel: string }) {
  const configs = {
    push: { icon: Smartphone, label: 'Push', className: 'bg-purple-100 text-purple-700' },
    sms: { icon: MessageSquare, label: 'SMS', className: 'bg-blue-100 text-blue-700' },
    email: { icon: Mail, label: 'Email', className: 'bg-green-100 text-green-700' },
  };
  const cfg = configs[channel as keyof typeof configs] || configs.push;
  const Icon = cfg.icon;
  return (
    <span className={`inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded ${cfg.className}`}>
      <Icon className="w-3 h-3" />
      {cfg.label}
    </span>
  );
}
