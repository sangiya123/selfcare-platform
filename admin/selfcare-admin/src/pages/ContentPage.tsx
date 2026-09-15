/**
 * ContentPage — CMS for FAQ articles, banners, legal text, help articles, and templates.
 *
 * Content is stored in MongoDB and served by the content-service.
 * All content is tenant-scoped and supports multi-language.
 * The Config Service compiles this into the runtime manifest (ADR-004).
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
import { api } from '@/lib/api';

// --- Types matching backend DTOs ---
interface Article {
  id: string;
  tenantId: string;
  slug: string;
  category: 'NEWS' | 'HOW_TO' | 'PROMOTION' | 'ANNOUNCEMENT';
  defaultLocale: string;
  translations: Record<string, { title: string; summary: string; body: string; metaDescription?: string; featuredImageUrl?: string }>;
  tags: string[];
  featuredImageUrl?: string;
  author?: string;
  status: 'DRAFT' | 'REVIEW' | 'PUBLISHED' | 'ARCHIVED';
  visibility: 'ALL' | 'POSTPAID_ONLY' | 'PREPAID_ONLY' | 'SEGMENT_X';
  version: number;
  publishAt?: string;
  unpublishAt?: string;
  createdAt: string;
  updatedAt: string;
  publishedAt?: string;
}

interface FAQ {
  id: string;
  tenantId: string;
  category: string;
  translations: Record<string, { question: string; answer: string }>;
  tags: string[];
  displayOrder: number;
  status: 'ACTIVE' | 'ARCHIVED';
  createdAt: string;
  updatedAt: string;
}

interface Banner {
  id: string;
  tenantId: string;
  position: 'HERO_TOP' | 'CAROUSEL' | 'SIDEBAR' | 'FOOTER';
  translations: Record<string, { title: string; subtitle: string; description: string }>;
  imageUrl: string;
  mobileImageUrl?: string;
  targetUrl?: string;
  targetType: 'DEEP_LINK' | 'EXTERNAL_URL' | 'INTERNAL_PAGE';
  ctaText?: string;
  displayOrder: number;
  activeFrom?: string;
  activeTo?: string;
  status: 'ACTIVE' | 'INACTIVE';
  createdAt: string;
  updatedAt: string;
}

type ContentType = 'article' | 'faq' | 'banner';
type ContentItem = Article | FAQ | Banner;

function isArticle(item: ContentItem): item is Article {
  return 'category' in item && 'slug' in item;
}
function isFAQ(item: ContentItem): item is FAQ {
  return 'question' in item;
}
function isBanner(item: ContentItem): item is Banner {
  return 'position' in item;
}

function getTranslation(item: ContentItem, lang: string) {
  return item.translations?.[lang] || item.translations?.en || {};
}

interface ContentSection {
  id: ContentType;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  count: number;
}

const CONTENT_SECTIONS: ContentSection[] = [
  { id: 'article', label: 'Articles', icon: FileText, count: 0 },
  { id: 'faq', label: 'FAQs', icon: HelpCircle, count: 0 },
  { id: 'banner', label: 'Banners', icon: Image, count: 0 },
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
  const queryClient = useQueryClient();
  const [activeSection, setActiveSection] = useState<ContentType>('article');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedItems, setSelectedItems] = useState<Set<string>>(new Set());
  const [currentPage, setCurrentPage] = useState(1);
  const [editingItem, setEditingItem] = useState<ContentItem | null>(null);
  const [isNewItem, setIsNewItem] = useState(false);
  const [showPreview, setShowPreview] = useState(false);
  const [translationGroupForView, setTranslationGroupForView] = useState<string | null>(null);

  // Form state for editing
  const [formData, setFormData] = useState({
    id: '',
    type: 'article' as ContentType,
    title: '',
    content: '',
    category: 'HOW_TO',
    language: 'en',
    status: 'DRAFT' as Article['status'] | FAQ['status'] | Banner['status'],
    tags: '',
    schedulePublish: false,
    scheduledDate: '',
    expiryEnabled: false,
    expiryDate: '',
    metaTitle: '',
    metaDescription: '',
    // Article fields
    slug: '',
    defaultLocale: 'en',
    summary: '',
    featuredImageUrl: '',
    author: '',
    visibility: 'ALL' as Article['visibility'],
    version: 1,
    publishAt: '',
    unpublishAt: '',
    // FAQ fields
    displayOrder: 0,
    question: '',
    answer: '',
    // Banner fields
    position: 'HERO_TOP' as Banner['position'],
    imageUrl: '',
    mobileImageUrl: '',
    targetUrl: '',
    targetType: 'INTERNAL_PAGE' as Banner['targetType'],
    ctaText: '',
    activeFrom: '',
    activeTo: '',
  });
  const [showSeo, setShowSeo] = useState(false);

  // Fetch content from API
  const { data: articles = [], isLoading: loadingArticles } = useQuery({
    queryKey: ['content', 'articles', activeTenantId],
    queryFn: () => api.content.list({ type: 'article', locale: '', status: '' }),
    enabled: !!activeTenantId,
  });

  const { data: faqs = [], isLoading: loadingFaqs } = useQuery({
    queryKey: ['content', 'faqs', activeTenantId],
    queryFn: () => api.content.list({ type: 'faq', locale: '', status: '' }),
    enabled: !!activeTenantId,
  });

  const { data: banners = [], isLoading: loadingBanners } = useQuery({
    queryKey: ['content', 'banners', activeTenantId],
    queryFn: () => api.content.list({ type: 'banner', locale: '', status: '' }),
    enabled: !!activeTenantId,
  });

  const isLoading = loadingArticles || loadingFaqs || loadingBanners;

  // Update section counts
  const sectionsWithCounts = CONTENT_SECTIONS.map(s => ({
    ...s,
    count: s.id === 'article' ? articles.length : s.id === 'faq' ? faqs.length : banners.length,
  }));

  // Get current section data
  const getSectionData = () => {
    switch (activeSection) {
      case 'article': return articles;
      case 'faq': return faqs;
      case 'banner': return banners;
    }
  };

  const sectionData = getSectionData();

  // Filter content by search
  const filteredContent = sectionData.filter((item) => {
    const title = getItemTitle(item);
    const matchesSearch =
      !searchQuery ||
      title.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (item.tags?.some((t: string) => t.toLowerCase().includes(searchQuery.toLowerCase())) ?? false);
    return matchesSearch;
  });

  const totalPages = Math.ceil(filteredContent.length / ITEMS_PER_PAGE);
  const paginatedContent = filteredContent.slice(
    (currentPage - 1) * ITEMS_PER_PAGE,
    currentPage * ITEMS_PER_PAGE
  );

  // Mutations
  const createMutation = useMutation({
    mutationFn: (data: any) => api.content.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['content', activeSection, activeTenantId] });
      toast.success('Content created');
      setEditingItem(null);
      setIsNewItem(false);
    },
    onError: (err: any) => toast.error(err.message || 'Failed to create content'),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: any }) => api.content.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['content', activeSection, activeTenantId] });
      toast.success('Content updated');
      setEditingItem(null);
      setIsNewItem(false);
    },
    onError: (err: any) => toast.error(err.message || 'Failed to update content'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.content.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['content', activeSection, activeTenantId] });
      toast.success('Content deleted');
      setSelectedItems(new Set());
    },
    onError: (err: any) => toast.error(err.message || 'Failed to delete content'),
  });

  const saving = createMutation.isPending || updateMutation.isPending;

  const handleSectionChange = (sectionId: ContentType) => {
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
      setSelectedItems(new Set(paginatedContent.map((c: any) => c.id)));
    }
  };

  const openNewItem = () => {
    setIsNewItem(true);
    setEditingItem(null);
    setFormData({
      id: '',
      type: activeSection,
      title: '',
      content: '',
      category: 'HOW_TO',
      language: 'en',
      status: 'DRAFT',
      tags: '',
      schedulePublish: false,
      scheduledDate: '',
      expiryEnabled: false,
      expiryDate: '',
      metaTitle: '',
      metaDescription: '',
      slug: '',
      defaultLocale: 'en',
      summary: '',
      featuredImageUrl: '',
      author: '',
      visibility: 'ALL',
      version: 1,
      publishAt: '',
      unpublishAt: '',
      displayOrder: 0,
      question: '',
      answer: '',
      position: 'HERO_TOP',
      imageUrl: '',
      mobileImageUrl: '',
      targetUrl: '',
      targetType: 'INTERNAL_PAGE',
      ctaText: '',
      activeFrom: '',
      activeTo: '',
    });
    setShowSeo(false);
  };

  const openEditItem = (item: ContentItem) => {
    setIsNewItem(false);
    setEditingItem(item);
    const type = isArticle(item) ? 'article' : isFAQ(item) ? 'faq' : 'banner';
    const lang = isArticle(item) ? item.defaultLocale : 'en';
    const trans = getTranslation(item, lang);
    const articleTrans = isArticle(item) ? trans as { title: string; summary: string; body: string; metaDescription?: string; featuredImageUrl?: string } : null;
    const faqTrans = isFAQ(item) ? trans as { question: string; answer: string } : null;
    const bannerTrans = isBanner(item) ? trans as { title: string; subtitle: string; description: string } : null;
    setFormData({
      id: item.id,
      type,
      title: (articleTrans?.title || bannerTrans?.title || faqTrans?.question || (isArticle(item) ? item.slug : '')) || '',
      content: (articleTrans?.body || faqTrans?.answer || bannerTrans?.description || ''),
      category: isArticle(item) ? item.category : isFAQ(item) ? item.category : 'HOW_TO',
      language: lang,
      status: item.status,
      tags: isArticle(item) || isFAQ(item) ? (item.tags?.join(', ') || '') : '',
      schedulePublish: isArticle(item) && !!item.publishAt,
      scheduledDate: isArticle(item) ? (item.publishAt || '').slice(0, 16) : '',
      expiryEnabled: (isArticle(item) && !!item.unpublishAt) || (isBanner(item) && !!item.activeTo),
      expiryDate: (isArticle(item) ? item.unpublishAt : isBanner(item) ? item.activeTo : '')?.slice(0, 16) || '',
      metaTitle: '',
      metaDescription: articleTrans?.metaDescription || '',
      slug: isArticle(item) ? item.slug : '',
      defaultLocale: isArticle(item) ? item.defaultLocale : 'en',
      summary: articleTrans?.summary || '',
      featuredImageUrl: isArticle(item) ? (item.featuredImageUrl || '') : '',
      author: isArticle(item) ? (item.author || '') : '',
      visibility: isArticle(item) ? item.visibility : 'ALL',
      version: isArticle(item) ? item.version : 1,
      publishAt: isArticle(item) ? (item.publishAt || '') : '',
      unpublishAt: isArticle(item) ? (item.unpublishAt || '') : '',
      displayOrder: isFAQ(item) ? item.displayOrder : isBanner(item) ? item.displayOrder : 0,
      question: faqTrans?.question || '',
      answer: faqTrans?.answer || '',
      position: isBanner(item) ? item.position : 'HERO_TOP',
      imageUrl: isBanner(item) ? item.imageUrl : '',
      mobileImageUrl: isBanner(item) ? (item.mobileImageUrl || '') : '',
      targetUrl: isBanner(item) ? (item.targetUrl || '') : '',
      targetType: isBanner(item) ? item.targetType : 'INTERNAL_PAGE',
      ctaText: isBanner(item) ? (item.ctaText || '') : '',
      activeFrom: isBanner(item) ? (item.activeFrom || '') : '',
      activeTo: isBanner(item) ? (item.activeTo || '') : '',
    });
    setShowSeo(!!articleTrans?.metaDescription);
  };

  const buildPayload = () => {
    const base = {
      tenantId: activeTenantId,
      tags: formData.tags.split(',').map(t => t.trim()).filter(Boolean),
      status: formData.status,
    };

    if (formData.type === 'article') {
      return {
        ...base,
        type: 'article',
        slug: formData.slug || formData.title.toLowerCase().replace(/\s+/g, '-'),
        category: formData.category,
        defaultLocale: formData.defaultLocale,
        translations: {
          [formData.language]: {
            title: formData.title,
            summary: formData.summary,
            body: formData.content,
            metaDescription: formData.metaDescription,
            featuredImageUrl: formData.featuredImageUrl,
          },
        },
        featuredImageUrl: formData.featuredImageUrl,
        author: formData.author,
        visibility: formData.visibility,
        version: formData.version,
        publishAt: formData.schedulePublish && formData.scheduledDate ? new Date(formData.scheduledDate).toISOString() : undefined,
        unpublishAt: formData.expiryEnabled && formData.expiryDate ? new Date(formData.expiryDate).toISOString() : undefined,
      };
    } else if (formData.type === 'faq') {
      return {
        ...base,
        type: 'faq',
        category: formData.category,
        translations: {
          [formData.language]: {
            question: formData.question,
            answer: formData.content,
          },
        },
        displayOrder: formData.displayOrder,
      };
    } else {
      return {
        ...base,
        type: 'banner',
        position: formData.position,
        translations: {
          [formData.language]: {
            title: formData.title,
            subtitle: formData.metaTitle,
            description: formData.content,
          },
        },
        imageUrl: formData.imageUrl,
        mobileImageUrl: formData.mobileImageUrl,
        targetUrl: formData.targetUrl,
        targetType: formData.targetType,
        ctaText: formData.ctaText,
        displayOrder: formData.displayOrder,
        activeFrom: formData.activeFrom ? new Date(formData.activeFrom).toISOString() : undefined,
        activeTo: formData.activeTo ? new Date(formData.activeTo).toISOString() : undefined,
      };
    }
  };

  const handleSave = async () => {
    if (!formData.title) return;
    const payload = buildPayload();
    if (isNewItem) {
      createMutation.mutate(payload);
    } else if (editingItem) {
      updateMutation.mutate({ id: editingItem.id, data: payload });
    }
  };

  const handleBulkAction = async (action: 'publish' | 'archive' | 'delete') => {
    if (selectedItems.size === 0) return;
    for (const id of selectedItems) {
      const item = sectionData.find((i: any) => i.id === id);
      if (!item) continue;
      if (action === 'delete') {
        deleteMutation.mutate(id);
      } else {
        const newStatus = action === 'publish' ? 'PUBLISHED' : 'ARCHIVED';
        updateMutation.mutate({ id, data: { ...item, status: newStatus } });
      }
    }
  };

  const getItemTitle = (item: ContentItem): string => {
    if (isArticle(item)) return item.slug;
    const trans = getTranslation(item, 'en');
    return (trans as any).title || (trans as any).question || 'Untitled';
  };

  const getItemStatus = (item: ContentItem): string => item.status;
  const getItemLanguage = (item: ContentItem): string => isArticle(item) ? item.defaultLocale : 'en';
  const getItemLastUpdated = (item: ContentItem): string => item.updatedAt || item.createdAt || '';

  const statusBadgeClass = (status: string) => {
    if (status === 'PUBLISHED' || status === 'ACTIVE') return 'bg-green-100 text-green-700';
    if (status === 'DRAFT' || status === 'INACTIVE') return 'bg-yellow-100 text-yellow-700';
    return 'bg-gray-100 text-gray-600';
  };

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

      <div className="flex gap-6">
        {/* Left sidebar - Content tree */}
        <div className="w-56 shrink-0">
          <Card>
            <CardContent className="p-2">
              <div className="text-xs font-semibold text-gray-500 uppercase px-2 py-1.5">
                Content Sections
              </div>
              {sectionsWithCounts.map((section) => {
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
                          <div className="font-medium text-sm">{getItemTitle(item)}</div>
                          <div className="text-xs text-gray-500">
                            {item.tags?.join(', ') || ''}
                          </div>
                        </td>
                        <td className="px-4 py-3">
                          <span
                            className={`text-xs px-2 py-0.5 rounded font-medium ${statusBadgeClass(getItemStatus(item))}`}
                          >
                            {getItemStatus(item)}
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          <span className="flex items-center gap-1 text-xs text-gray-600">
                            <Globe className="w-3 h-3" />
                            {LANGUAGES.find((l) => l.code === getItemLanguage(item))?.label || getItemLanguage(item)}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-xs text-gray-500">
                          <span className="flex items-center gap-1">
                            <Clock className="w-3 h-3" />
                            {getItemLastUpdated(item)}
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
                            <Button
                              size="sm"
                              variant="ghost"
                              className="text-red-600"
                              onClick={() => {
                                if (confirm('Delete this content?')) {
                                  deleteMutation.mutate(item.id);
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
                  {formData.type === 'article' ? (
                    <>
                      <option value="DRAFT">Draft</option>
                      <option value="REVIEW">Review</option>
                      <option value="PUBLISHED">Published</option>
                      <option value="ARCHIVED">Archived</option>
                    </>
                  ) : (
                    <>
                      <option value="ACTIVE">Active</option>
                      <option value="INACTIVE">Inactive</option>
                      <option value="ARCHIVED">Archived</option>
                    </>
                  )}
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
            <h2 className="text-xl font-bold">{editingItem ? getItemTitle(editingItem) : ''}</h2>
            <div className="flex items-center gap-2 text-xs text-gray-500">
              <Badge variant="outline">{editingItem?.status}</Badge>
              <span>
                {LANGUAGES.find((l) => l.code === (editingItem ? getItemLanguage(editingItem) : 'en'))?.label}
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

    </div>
  );
}

function RefreshCwIcon() {
  return <Clock className="w-3 h-3 mr-1" />;
}
