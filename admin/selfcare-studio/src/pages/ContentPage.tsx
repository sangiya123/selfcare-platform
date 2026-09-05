/**
 * ContentPage — CMS for FAQ articles, banners, legal text, help articles, and templates.
 *
 * Content is stored in MongoDB and served by the content-service.
 * All content is tenant-scoped and supports multi-language.
 * The Config Service compiles this into the runtime manifest (ADR-004).
 */
import { useState } from 'react';
import { toast } from 'sonner';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Textarea } from '@/components/ui/textarea';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import {
  BookOpen,
  Plus,
  Search,
  Eye,
  Edit2,
  Trash2,
  ChevronRight,
  FileText,
  Image,
  Scale,
  HelpCircle,
  FileCode,
  Globe,
  Clock,
  CheckCircle2,
  Archive,
  ChevronDown,
  ChevronUp,
  Loader2,
} from 'lucide-react';
import { useActiveTenant } from '@/hooks/useActiveTenant';

// --- Types ---
interface ContentItem {
  id: string;
  title: string;
  type: 'faq' | 'banner' | 'legal' | 'help' | 'template';
  status: 'draft' | 'published' | 'archived';
  language: string;
  lastUpdated: string;
  metaTitle?: string;
  metaDescription?: string;
  tags: string[];
  /**
   * Translation group: a logical "content item" may have one entry per
   * language. Same translationGroupId = same content, different languages.
   * This is what powers the translation workflow: see which languages are
   * missing, which are out of date, etc.
   */
  translationGroupId?: string;
  /** Expiry / scheduled unpublish for time-bound content. */
  expiresAt?: string;
  /** Scheduled publish datetime (in addition to the immediate publish). */
  scheduledAt?: string;
}

interface ContentSection {
  id: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  count: number;
}

// --- Mock data ---
const MOCK_SECTIONS: ContentSection[] = [
  { id: 'faq', label: 'FAQs', icon: HelpCircle, count: 24 },
  { id: 'banner', label: 'Banners', icon: Image, count: 12 },
  { id: 'legal', label: 'Legal', icon: Scale, count: 8 },
  { id: 'help', label: 'Help Articles', icon: FileText, count: 31 },
  { id: 'template', label: 'Templates', icon: FileCode, count: 6 },
];

// Translation groups: items with the same translationGroupId are translations of
// each other. Items in a group may have different status/lastUpdated — used to
// surface "out of date" or "missing translation" alerts.
const MOCK_CONTENT: ContentItem[] = [
  // Group 1: How to check data balance — en/si/ta
  { id: '1', title: 'How to check my data balance', type: 'faq', status: 'published', language: 'en', lastUpdated: '2025-08-15', tags: ['data', 'balance', 'faq'], translationGroupId: 'g1' },
  { id: '1s', title: 'මගේ දත්ත ශේෂය පරීක්ෂා කරන්නේ කෙසේද', type: 'faq', status: 'published', language: 'si', lastUpdated: '2025-08-10', tags: ['data', 'balance', 'faq'], translationGroupId: 'g1' },
  { id: '1t', title: 'எனது தரவு இருப்பை எவ்வாறு சோதிப்பது', type: 'faq', status: 'draft', language: 'ta', lastUpdated: '2025-07-20', tags: ['data', 'balance', 'faq'], translationGroupId: 'g1' },
  // Group 2: How to activate a pack — en only
  { id: '2', title: 'How to activate a pack', type: 'faq', status: 'published', language: 'en', lastUpdated: '2025-08-10', tags: ['activation', 'packs'], translationGroupId: 'g2' },
  // Group 3: Summer Sale Banner — en/si (si missing)
  { id: '3', title: 'Summer Sale Banner', type: 'banner', status: 'published', language: 'en', lastUpdated: '2025-08-20', tags: ['promotion', 'summer'], translationGroupId: 'g3', expiresAt: '2025-12-31' },
  { id: '4', title: 'Data Roaming Charges', type: 'help', status: 'published', language: 'en', lastUpdated: '2025-07-22', tags: ['roaming', 'data'], translationGroupId: 'g4' },
  { id: '5', title: 'Privacy Policy', type: 'legal', status: 'published', language: 'en', lastUpdated: '2025-06-01', tags: ['privacy', 'legal'], translationGroupId: 'g5' },
  { id: '5s', title: 'පෞද්ගලිකත්ව ප්‍රතිපත්තිය', type: 'legal', status: 'published', language: 'si', lastUpdated: '2025-06-01', tags: ['privacy', 'legal'], translationGroupId: 'g5' },
  { id: '5t', title: 'தனியுரிமை கொள்கை', type: 'legal', status: 'published', language: 'ta', lastUpdated: '2025-06-01', tags: ['privacy', 'legal'], translationGroupId: 'g5' },
  { id: '6', title: 'Welcome Offer SMS Template', type: 'template', status: 'draft', language: 'en', lastUpdated: '2025-08-25', tags: ['sms', 'welcome'], translationGroupId: 'g6', scheduledAt: '2025-09-10 09:00' },
  { id: '7', title: 'Bill Payment Reminder', type: 'banner', status: 'draft', language: 'si', lastUpdated: '2025-08-18', tags: ['bill', 'reminder'], translationGroupId: 'g7' },
  { id: '8', title: 'What is Fair Usage Policy', type: 'faq', status: 'archived', language: 'en', lastUpdated: '2025-05-10', tags: ['fup', 'policy'], translationGroupId: 'g8' },
  { id: '9', title: 'International Call Rates', type: 'help', status: 'published', language: 'en', lastUpdated: '2025-08-12', tags: ['international', 'calls'], translationGroupId: 'g9' },
  { id: '10', title: 'Terms and Conditions', type: 'legal', status: 'published', language: 'en', lastUpdated: '2025-06-01', tags: ['terms', 'legal'], translationGroupId: 'g10' },
  { id: '11', title: 'Ramadan Promotion Banner', type: 'banner', status: 'archived', language: 'ta', lastUpdated: '2025-03-15', tags: ['promotion', 'ramadan'], translationGroupId: 'g11' },
  { id: '12', title: 'How to contact support', type: 'faq', status: 'published', language: 'en', lastUpdated: '2025-08-01', tags: ['support', 'faq'], translationGroupId: 'g12' },
];

const LANGUAGES = [
  { code: 'en', label: 'English' },
  { code: 'si', label: 'Sinhala' },
  { code: 'ta', label: 'Tamil' },
];

const ITEMS_PER_PAGE = 8;

// --- Component ---
export default function ContentPage() {
  const { activeTenantId } = useActiveTenant();
  const [activeSection, setActiveSection] = useState<string>('faq');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedItems, setSelectedItems] = useState<Set<string>>(new Set());
  const [currentPage, setCurrentPage] = useState(1);
  const [editingItem, setEditingItem] = useState<ContentItem | null>(null);
  const [isNewItem, setIsNewItem] = useState(false);
  const [showPreview, setShowPreview] = useState(false);
  const [saving, setSaving] = useState(false);
  const [translationGroupForView, setTranslationGroupForView] = useState<string | null>(null);

  // Form state for editing
  const [formData, setFormData] = useState({
    title: '',
    content: '',
    language: 'en',
    status: 'draft' as 'draft' | 'published' | 'archived',
    tags: '',
    schedulePublish: false,
    scheduledDate: '',
    expiryEnabled: false,
    expiryDate: '',
    metaTitle: '',
    metaDescription: '',
  });
  const [showSeo, setShowSeo] = useState(false);

  // Filter content by section and search
  const filteredContent = MOCK_CONTENT.filter((item) => {
    const matchesSection = item.type === activeSection;
    const matchesSearch =
      !searchQuery ||
      item.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
      item.tags.some((t) => t.toLowerCase().includes(searchQuery.toLowerCase()));
    return matchesSection && matchesSearch;
  });

  const totalPages = Math.ceil(filteredContent.length / ITEMS_PER_PAGE);
  const paginatedContent = filteredContent.slice(
    (currentPage - 1) * ITEMS_PER_PAGE,
    currentPage * ITEMS_PER_PAGE
  );

  const handleSectionChange = (sectionId: string) => {
    setActiveSection(sectionId);
    setCurrentPage(1);
    setSelectedItems(new Set());
  };

  const toggleSelect = (id: string) => {
    setSelectedItems((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const toggleSelectAll = () => {
    if (selectedItems.size === paginatedContent.length) {
      setSelectedItems(new Set());
    } else {
      setSelectedItems(new Set(paginatedContent.map((c) => c.id)));
    }
  };

  const openNewItem = () => {
    setIsNewItem(true);
    setEditingItem(null);
    setFormData({
      title: '',
      content: '',
      language: 'en',
      status: 'draft',
      tags: '',
      schedulePublish: false,
      scheduledDate: '',
      expiryEnabled: false,
      expiryDate: '',
      metaTitle: '',
      metaDescription: '',
    });
    setShowSeo(false);
  };

  const openEditItem = (item: ContentItem) => {
    setIsNewItem(false);
    setEditingItem(item);
    setFormData({
      title: item.title,
      content: '', // would come from API in real implementation
      language: item.language,
      status: item.status,
      tags: item.tags.join(', '),
      schedulePublish: !!item.scheduledAt,
      scheduledDate: item.scheduledAt || '',
      expiryEnabled: !!item.expiresAt,
      expiryDate: item.expiresAt || '',
      metaTitle: item.metaTitle || '',
      metaDescription: item.metaDescription || '',
    });
    setShowSeo(!!item.metaTitle || !!item.metaDescription);
  };

  const handleSave = async () => {
    setSaving(true);
    // Mock API call - in real implementation call content service
    await new Promise((r) => setTimeout(r, 800));
    setSaving(false);
    toast.success(isNewItem ? 'Content created' : 'Content updated');
    setEditingItem(null);
    setIsNewItem(false);
  };

  const handleBulkAction = async (action: 'publish' | 'archive' | 'delete') => {
    if (selectedItems.size === 0) return;
    const messages = {
      publish: `Published ${selectedItems.size} item(s)`,
      archive: `Archived ${selectedItems.size} item(s)`,
      delete: `Deleted ${selectedItems.size} item(s)`,
    };
    toast.success(messages[action]);
    setSelectedItems(new Set());
  };

  const statusBadgeVariant = (status: string) => {
    if (status === 'published') return 'default';
    if (status === 'draft') return 'secondary';
    return 'outline';
  };

  const statusBadgeClass = (status: string) => {
    if (status === 'published') return 'bg-green-100 text-green-700';
    if (status === 'draft') return 'bg-yellow-100 text-yellow-700';
    return 'bg-gray-100 text-gray-600';
  };

  // ── Translation workflow helpers ────────────────────────────────────
  // Group items by translationGroupId so admins can see at a glance which
  // languages are missing or out of date. Items without a translationGroupId
  // are shown as standalone (no translation expected).
  const translationGroups = (() => {
    const groups = new Map<string, ContentItem[]>();
    MOCK_CONTENT.forEach((item) => {
      if (!item.translationGroupId) return;
      const list = groups.get(item.translationGroupId) ?? [];
      list.push(item);
      groups.set(item.translationGroupId, list);
    });
    return Array.from(groups.entries()).map(([id, items]) => {
      const en = items.find((i) => i.language === 'en');
      const refDate = en ? new Date(en.lastUpdated).getTime() : 0;
      const missing = LANGUAGES.filter((l) => !items.find((i) => i.language === l.code));
      const outdated = items.filter((i) => i.language !== 'en' && new Date(i.lastUpdated).getTime() < refDate);
      return { id, items, referenceItem: en, missing, outdated };
    });
  })();

  const totalMissingTranslations = translationGroups.reduce((sum, g) => sum + g.missing.length, 0);
  const totalOutdatedTranslations = translationGroups.reduce((sum, g) => sum + g.outdated.length, 0);
  const expiringContent = MOCK_CONTENT.filter((i) => i.expiresAt && new Date(i.expiresAt).getTime() < Date.now() + 30 * 24 * 3600 * 1000);
  const scheduledContent = MOCK_CONTENT.filter((i) => i.scheduledAt && new Date(i.scheduledAt).getTime() > Date.now());

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <BookOpen className="w-6 h-6" />
            Content Management
          </h1>
          <p className="text-gray-500 mt-1">
            Manage CMS content for{' '}
            <code className="bg-gray-100 px-1.5 py-0.5 rounded text-sm">
              {activeTenantId || '— no client selected —'}
            </code>
          </p>
        </div>
        <Button onClick={openNewItem}>
          <Plus className="w-4 h-4 mr-2" />
          New Content
        </Button>
      </div>

      {/* ── Translation & Expiry workflow alerts ── */}
      {(totalMissingTranslations > 0 || totalOutdatedTranslations > 0 || expiringContent.length > 0 || scheduledContent.length > 0) && (
        <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
          <Card className="border-amber-300 bg-amber-50">
            <CardContent className="p-3 flex items-center gap-2">
              <Globe className="w-4 h-4 text-amber-700 shrink-0" />
              <div className="text-sm">
                <div className="font-medium text-amber-900">{totalMissingTranslations} missing translations</div>
                <div className="text-xs text-amber-700">across {translationGroups.length} content groups</div>
              </div>
            </CardContent>
          </Card>
          <Card className="border-yellow-300 bg-yellow-50">
            <CardContent className="p-3 flex items-center gap-2">
              <Clock className="w-4 h-4 text-yellow-700 shrink-0" />
              <div className="text-sm">
                <div className="font-medium text-yellow-900">{totalOutdatedTranslations} outdated translations</div>
                <div className="text-xs text-yellow-700">source updated after translation</div>
              </div>
            </CardContent>
          </Card>
          <Card className="border-orange-300 bg-orange-50">
            <CardContent className="p-3 flex items-center gap-2">
              <Clock className="w-4 h-4 text-orange-700 shrink-0" />
              <div className="text-sm">
                <div className="font-medium text-orange-900">{expiringContent.length} expiring soon</div>
                <div className="text-xs text-orange-700">within 30 days</div>
              </div>
            </CardContent>
          </Card>
          <Card className="border-blue-300 bg-blue-50">
            <CardContent className="p-3 flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 text-blue-700 shrink-0" />
              <div className="text-sm">
                <div className="font-medium text-blue-900">{scheduledContent.length} scheduled</div>
                <div className="text-xs text-blue-700">pending future publish</div>
              </div>
            </CardContent>
          </Card>
        </div>
      )}

      <div className="flex gap-6">
        {/* Left sidebar - Content tree */}
        <div className="w-56 shrink-0">
          <Card>
            <CardContent className="p-2">
              <div className="text-xs font-semibold text-gray-500 uppercase px-2 py-1.5">
                Content Sections
              </div>
              {MOCK_SECTIONS.map((section) => {
                const Icon = section.icon;
                return (
                  <button
                    key={section.id}
                    onClick={() => handleSectionChange(section.id)}
                    className={`w-full flex items-center justify-between px-2 py-2 rounded text-sm transition-colors ${
                      activeSection === section.id
                        ? 'bg-purple-50 text-purple-700 font-medium'
                        : 'text-gray-700 hover:bg-gray-50'
                    }`}
                  >
                    <span className="flex items-center gap-2">
                      <Icon className="w-4 h-4" />
                      {section.label}
                    </span>
                    <span className="text-xs bg-gray-100 px-1.5 py-0.5 rounded">
                      {section.count}
                    </span>
                  </button>
                );
              })}
            </CardContent>
          </Card>
        </div>

        {/* Main content area */}
        <div className="flex-1 space-y-4">
          {/* Search bar */}
          <div className="flex items-center gap-3">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
              <Input
                placeholder="Search content by title or tags..."
                value={searchQuery}
                onChange={(e) => {
                  setSearchQuery(e.target.value);
                  setCurrentPage(1);
                }}
                className="pl-9"
              />
            </div>
            {selectedItems.size > 0 && (
              <div className="flex items-center gap-2">
                <span className="text-sm text-gray-500">
                  {selectedItems.size} selected
                </span>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => handleBulkAction('publish')}
                >
                  <CheckCircle2 className="w-3 h-3 mr-1" /> Publish
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => handleBulkAction('archive')}
                >
                  <Archive className="w-3 h-3 mr-1" /> Archive
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  className="text-red-600"
                  onClick={() => handleBulkAction('delete')}
                >
                  <Trash2 className="w-3 h-3 mr-1" /> Delete
                </Button>
              </div>
            )}
          </div>

          {/* Content list */}
          {paginatedContent.length === 0 ? (
            <Card>
              <CardContent className="py-12 text-center text-gray-500">
                No content found in this section.
                <br />
                <Button variant="link" onClick={openNewItem}>
                  Create the first item
                </Button>
              </CardContent>
            </Card>
          ) : (
            <Card>
              <CardContent className="p-0">
                <table className="w-full">
                  <thead>
                    <tr className="border-b bg-gray-50">
                      <th className="text-left px-4 py-3">
                        <input
                          type="checkbox"
                          checked={selectedItems.size === paginatedContent.length && paginatedContent.length > 0}
                          onChange={toggleSelectAll}
                          className="rounded"
                        />
                      </th>
                      <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 uppercase">
                        Title
                      </th>
                      <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 uppercase">
                        Status
                      </th>
                      <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 uppercase">
                        Language
                      </th>
                      <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 uppercase">
                        Last Updated
                      </th>
                      <th className="text-right px-4 py-3 text-xs font-medium text-gray-500 uppercase">
                        Actions
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {paginatedContent.map((item) => (
                      <tr key={item.id} className="border-b hover:bg-gray-50">
                        <td className="px-4 py-3">
                          <input
                            type="checkbox"
                            checked={selectedItems.has(item.id)}
                            onChange={() => toggleSelect(item.id)}
                            className="rounded"
                          />
                        </td>
                        <td className="px-4 py-3">
                          <div className="font-medium text-sm">{item.title}</div>
                          <div className="text-xs text-gray-500">
                            {item.tags.join(', ')}
                          </div>
                        </td>
                        <td className="px-4 py-3">
                          <span
                            className={`text-xs px-2 py-0.5 rounded font-medium ${statusBadgeClass(item.status)}`}
                          >
                            {item.status}
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          <span className="flex items-center gap-1 text-xs text-gray-600">
                            <Globe className="w-3 h-3" />
                            {LANGUAGES.find((l) => l.code === item.language)?.label || item.language}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-xs text-gray-500">
                          <span className="flex items-center gap-1">
                            <Clock className="w-3 h-3" />
                            {item.lastUpdated}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-right">
                          <div className="flex items-center justify-end gap-1">
                            <Button
                              size="sm"
                              variant="ghost"
                              onClick={() => {
                                openEditItem(item);
                                setShowPreview(true);
                              }}
                            >
                              <Eye className="w-3 h-3" />
                            </Button>
                            <Button
                              size="sm"
                              variant="ghost"
                              onClick={() => openEditItem(item)}
                            >
                              <Edit2 className="w-3 h-3" />
                            </Button>
                            {item.translationGroupId && (
                              <Button
                                size="sm"
                                variant="ghost"
                                onClick={() => setTranslationGroupForView(item.translationGroupId!)}
                                title="Translation workflow"
                              >
                                <Globe className="w-3 h-3" />
                              </Button>
                            )}
                            <Button
                              size="sm"
                              variant="ghost"
                              className="text-red-600"
                              onClick={() => {
                                if (confirm('Delete this content?')) {
                                  toast.success('Content deleted');
                                }
                              }}
                            >
                              <Trash2 className="w-3 h-3" />
                            </Button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </CardContent>
            </Card>
          )}

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between">
              <span className="text-sm text-gray-500">
                Showing {(currentPage - 1) * ITEMS_PER_PAGE + 1}–
                {Math.min(currentPage * ITEMS_PER_PAGE, filteredContent.length)} of{' '}
                {filteredContent.length}
              </span>
              <div className="flex gap-1">
                <Button
                  size="sm"
                  variant="outline"
                  disabled={currentPage === 1}
                  onClick={() => setCurrentPage((p) => p - 1)}
                >
                  Previous
                </Button>
                {Array.from({ length: totalPages }, (_, i) => i + 1).map((page) => (
                  <Button
                    key={page}
                    size="sm"
                    variant={currentPage === page ? 'default' : 'outline'}
                    onClick={() => setCurrentPage(page)}
                  >
                    {page}
                  </Button>
                ))}
                <Button
                  size="sm"
                  variant="outline"
                  disabled={currentPage === totalPages}
                  onClick={() => setCurrentPage((p) => p + 1)}
                >
                  Next
                </Button>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Edit / Create Modal */}
      <Dialog open={!!editingItem || isNewItem} onOpenChange={() => { setEditingItem(null); setIsNewItem(false); }}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>
              {isNewItem ? 'Create New Content' : 'Edit Content'}
            </DialogTitle>
          </DialogHeader>

          <div className="space-y-4 max-h-[60vh] overflow-y-auto pr-2">
            {/* Title */}
            <div>
              <Label>Title</Label>
              <Input
                value={formData.title}
                onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                placeholder="Enter content title"
              />
            </div>

            {/* Content editor */}
            <div>
              <Label>Content</Label>
              <Textarea
                value={formData.content}
                onChange={(e) => setFormData({ ...formData, content: e.target.value })}
                placeholder="Enter content (markdown supported)"
                rows={8}
              />
              <p className="text-xs text-gray-500 mt-1">
                Supports Markdown. Toggle preview to see rendered output.
              </p>
            </div>

            {/* Language & Status */}
            <div className="grid grid-cols-2 gap-4">
              <div>
                <Label>Language</Label>
                <select
                  className="w-full border rounded px-3 py-2"
                  value={formData.language}
                  onChange={(e) => setFormData({ ...formData, language: e.target.value })}
                >
                  {LANGUAGES.map((lang) => (
                    <option key={lang.code} value={lang.code}>
                      {lang.label}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <Label>Status</Label>
                <select
                  className="w-full border rounded px-3 py-2"
                  value={formData.status}
                  onChange={(e) => setFormData({ ...formData, status: e.target.value as any })}
                >
                  <option value="draft">Draft</option>
                  <option value="published">Published</option>
                  <option value="archived">Archived</option>
                </select>
              </div>
            </div>

            {/* Schedule publish + expiry */}
            <div className="border rounded p-3 space-y-3">
              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="schedulePublish"
                  checked={formData.schedulePublish}
                  onChange={(e) =>
                    setFormData({ ...formData, schedulePublish: e.target.checked })
                  }
                />
                <Label htmlFor="schedulePublish" className="cursor-pointer">
                  Schedule publish date
                </Label>
              </div>
              {formData.schedulePublish && (
                <Input
                  type="datetime-local"
                  value={formData.scheduledDate}
                  onChange={(e) =>
                    setFormData({ ...formData, scheduledDate: e.target.value })
                  }
                />
              )}

              <div className="flex items-center gap-2 pt-2 border-t">
                <input
                  type="checkbox"
                  id="expiryEnabled"
                  checked={formData.expiryEnabled}
                  onChange={(e) =>
                    setFormData({ ...formData, expiryEnabled: e.target.checked })
                  }
                />
                <Label htmlFor="expiryEnabled" className="cursor-pointer">
                  Auto-unpublish on expiry date
                </Label>
              </div>
              {formData.expiryEnabled && (
                <Input
                  type="datetime-local"
                  value={formData.expiryDate}
                  onChange={(e) =>
                    setFormData({ ...formData, expiryDate: e.target.value })
                  }
                />
              )}
            </div>

            {/* Tags */}
            <div>
              <Label>Tags / Categories</Label>
              <Input
                value={formData.tags}
                onChange={(e) => setFormData({ ...formData, tags: e.target.value })}
                placeholder="Enter tags separated by commas"
              />
              <p className="text-xs text-gray-500 mt-1">
                E.g. data, balance, promotion, welcome
              </p>
            </div>

            {/* SEO collapsible */}
            <div className="border rounded">
              <button
                className="w-full flex items-center justify-between px-3 py-2 text-sm font-medium"
                onClick={() => setShowSeo(!showSeo)}
              >
                <span>SEO Fields</span>
                {showSeo ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
              </button>
              {showSeo && (
                <div className="px-3 pb-3 space-y-3">
                  <div>
                    <Label>Meta Title</Label>
                    <Input
                      value={formData.metaTitle}
                      onChange={(e) =>
                        setFormData({ ...formData, metaTitle: e.target.value })
                      }
                      placeholder="SEO title (max 60 chars)"
                      maxLength={60}
                    />
                    <p className="text-xs text-gray-400 mt-1">
                      {formData.metaTitle.length}/60 characters
                    </p>
                  </div>
                  <div>
                    <Label>Meta Description</Label>
                    <Textarea
                      value={formData.metaDescription}
                      onChange={(e) =>
                        setFormData({ ...formData, metaDescription: e.target.value })
                      }
                      placeholder="SEO description (max 160 chars)"
                      rows={2}
                      maxLength={160}
                    />
                    <p className="text-xs text-gray-400 mt-1">
                      {formData.metaDescription.length}/160 characters
                    </p>
                  </div>
                </div>
              )}
            </div>
          </div>

          <DialogFooter className="gap-2">
            <Button variant="outline" onClick={() => { setEditingItem(null); setIsNewItem(false); }}>
              Cancel
            </Button>
            <Button
              variant="outline"
              onClick={() => setShowPreview(!showPreview)}
            >
              <Eye className="w-4 h-4 mr-2" />
              Preview
            </Button>
            <Button onClick={handleSave} disabled={saving || !formData.title}>
              {saving ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : null}
              {saving ? 'Saving...' : 'Save Content'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Preview Modal */}
      <Dialog open={showPreview && !!editingItem} onOpenChange={() => setShowPreview(false)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Content Preview</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            <h2 className="text-xl font-bold">{editingItem?.title}</h2>
            <div className="flex items-center gap-2 text-xs text-gray-500">
              <Badge variant="outline">{editingItem?.status}</Badge>
              <span>
                {LANGUAGES.find((l) => l.code === editingItem?.language)?.label}
              </span>
            </div>
            <div className="border-t pt-3">
              <p className="text-gray-700 whitespace-pre-wrap">
                {formData.content || 'Content will appear here...'}
              </p>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowPreview(false)}>
              Close
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Translation Workflow dialog */}
      <Dialog open={!!translationGroupForView} onOpenChange={() => setTranslationGroupForView(null)}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Globe className="w-5 h-5" /> Translation Workflow
            </DialogTitle>
          </DialogHeader>
          {(() => {
            const group = translationGroups.find((g) => g.id === translationGroupForView);
            if (!group) return null;
            return (
              <div className="space-y-4 max-h-[60vh] overflow-y-auto pr-2">
                <div className="bg-gray-50 border rounded p-3">
                  <p className="text-sm">
                    <strong>{group.referenceItem?.title ?? 'Content'}</strong> — Group <code>{group.id}</code>
                  </p>
                  <p className="text-xs text-gray-500 mt-1">
                    Manage translations across languages. AI-assisted translation
                    is available (uses the content intelligence service).
                  </p>
                </div>

                {group.missing.length > 0 && (
                  <div className="border border-amber-200 bg-amber-50 rounded p-3">
                    <p className="text-sm font-medium text-amber-900 mb-2">
                      Missing translations ({group.missing.length})
                    </p>
                    <div className="flex flex-wrap gap-2">
                      {group.missing.map((lang) => (
                        <Button
                          key={lang.code}
                          size="sm"
                          variant="outline"
                          className="border-amber-300"
                          onClick={() => {
                            toast.info(`AI translating to ${lang.label}… (mock)`);
                            setTimeout(() => {
                              toast.success(`Translation to ${lang.label} ready for review`);
                            }, 1500);
                          }}
                        >
                          <Globe className="w-3 h-3 mr-1" />
                          AI translate to {lang.label}
                        </Button>
                      ))}
                    </div>
                  </div>
                )}

                {group.outdated.length > 0 && (
                  <div className="border border-yellow-200 bg-yellow-50 rounded p-3">
                    <p className="text-sm font-medium text-yellow-900 mb-2">
                      Outdated translations ({group.outdated.length}) — source was updated after
                      these were last reviewed
                    </p>
                    <div className="flex flex-wrap gap-2">
                      {group.outdated.map((item) => (
                        <Button
                          key={item.id}
                          size="sm"
                          variant="outline"
                          className="border-yellow-300"
                          onClick={() => toast.info(`Re-translating to ${item.language}… (mock)`)}
                        >
                          <RefreshCwIcon /> Re-translate {LANGUAGES.find((l) => l.code === item.language)?.label}
                        </Button>
                      ))}
                    </div>
                  </div>
                )}

                <div>
                  <p className="text-sm font-medium mb-2">Translation status by language</p>
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="text-left text-gray-500 border-b">
                        <th className="py-2 font-normal">Language</th>
                        <th className="py-2 font-normal">Status</th>
                        <th className="py-2 font-normal">Last updated</th>
                        <th className="py-2 font-normal">Translation progress</th>
                        <th className="py-2 font-normal"></th>
                      </tr>
                    </thead>
                    <tbody>
                      {LANGUAGES.map((lang) => {
                        const item = group.items.find((i) => i.language === lang.code);
                        const refDate = group.referenceItem ? new Date(group.referenceItem.lastUpdated).getTime() : 0;
                        const isOutdated = item && lang.code !== 'en' && new Date(item.lastUpdated).getTime() < refDate;
                        return (
                          <tr key={lang.code} className="border-b last:border-0">
                            <td className="py-2">{lang.label}</td>
                            <td className="py-2">
                              {item ? (
                                <Badge className={statusBadgeClass(item.status)}>{item.status}</Badge>
                              ) : (
                                <Badge variant="outline" className="text-amber-700">Missing</Badge>
                              )}
                            </td>
                            <td className="py-2 text-xs text-gray-500 font-mono">{item?.lastUpdated ?? '—'}</td>
                            <td className="py-2 text-xs">
                              {item ? (
                                isOutdated ? <span className="text-yellow-600">⚠ outdated</span> : <span className="text-green-600">✓ current</span>
                              ) : <span className="text-gray-400">—</span>}
                            </td>
                            <td className="py-2 text-right">
                              {item ? (
                                <Button size="sm" variant="ghost" onClick={() => { setEditingItem(item); setTranslationGroupForView(null); }}>
                                  <Edit2 className="w-3 h-3" />
                                </Button>
                              ) : null}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            );
          })()}
          <DialogFooter>
            <Button variant="outline" onClick={() => setTranslationGroupForView(null)}>Close</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function RefreshCwIcon() {
  return <Clock className="w-3 h-3 mr-1" />;
}
