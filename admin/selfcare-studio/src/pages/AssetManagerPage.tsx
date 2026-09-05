/**
 * AssetManagerPage — manage uploaded assets (images, fonts, documents, video).
 *
 * Assets are stored in object storage (S3 / GCS / MinIO) with metadata in MongoDB.
 * The backend API /api/v1/admin/assets is not yet implemented, so this page
 * uses realistic mock data sourced from picsum.photos for images.
 *
 * Features:
 *   - Grid / list toggle view
 *   - Drag-and-drop upload area (mocks upload to object storage)
 *   - Filter by asset type (image, font, document, video)
 *   - Search by name
 *   - Asset preview modal for images
 *   - Copy URL button
 *   - Delete with confirmation
 *   - Pagination
 */
import { useState, useCallback } from 'react';
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
import { Badge } from '@/components/ui/badge';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import {
  Image as ImageIcon,
  Upload,
  Search,
  Grid,
  List,
  Trash2,
  Copy,
  ExternalLink,
  FileText,
  Type,
  Film,
  Image,
  X,
  ChevronLeft,
  ChevronRight,
  CheckCircle,
} from 'lucide-react';
import { useActiveTenant } from '@/hooks/useActiveTenant';

// ─── Types ────────────────────────────────────────────────────────────────────

export type AssetType = 'image' | 'font' | 'document' | 'video';

export interface Asset {
  id: string;
  name: string;
  type: AssetType;
  /** MIME type, e.g. "image/png" */
  mimeType: string;
  /** Bytes */
  size: number;
  /** Full CDN / object-storage URL */
  url: string;
  /** For images */
  width?: number;
  height?: number;
  /** ISO timestamp */
  uploadedAt: string;
  uploadedBy: string;
  tags?: string[];
}

export interface AssetUploadEvent {
  name: string;
  type: AssetType;
  mimeType: string;
  size: number;
  url: string;
  width?: number;
  height?: number;
}

// ─── Mock data ────────────────────────────────────────────────────────────────

const MOCK_ASSETS: Asset[] = [
  {
    id: 'ast_001',
    name: 'hero-banner-dialog.jpg',
    type: 'image',
    mimeType: 'image/jpeg',
    size: 284_320,
    url: 'https://picsum.photos/id/100/1920/600',
    width: 1920,
    height: 600,
    uploadedAt: '2026-08-15T10:23:00Z',
    uploadedBy: 'samantha.fernando@omobio.io',
    tags: ['banner', 'hero'],
  },
  {
    id: 'ast_002',
    name: 'brand-logo-dialog.svg',
    type: 'image',
    mimeType: 'image/svg+xml',
    size: 12_480,
    url: 'https://picsum.photos/id/101/400/400',
    width: 400,
    height: 400,
    uploadedAt: '2026-08-10T09:00:00Z',
    uploadedBy: 'roshan.de Silva@operator-x.com',
    tags: ['logo', 'brand'],
  },
  {
    id: 'ast_003',
    name: 'customer-onboarding-step1.png',
    type: 'image',
    mimeType: 'image/png',
    size: 512_800,
    url: 'https://picsum.photos/id/102/800/600',
    width: 800,
    height: 600,
    uploadedAt: '2026-08-12T14:30:00Z',
    uploadedBy: 'nimali.perera@operator-x.com',
    tags: ['onboarding', 'ux'],
  },
  {
    id: 'ast_004',
    name: 'Dialog_Style_Guide_v2.pdf',
    type: 'document',
    mimeType: 'application/pdf',
    size: 2_340_000,
    url: '#',
    uploadedAt: '2026-07-20T11:15:00Z',
    uploadedBy: 'samantha.fernando@omobio.io',
    tags: ['design', 'guidelines'],
  },
  {
    id: 'ast_005',
    name: 'NotoSans-Regular.ttf',
    type: 'font',
    mimeType: 'font/ttf',
    size: 98_320,
    url: '#',
    uploadedAt: '2026-06-01T08:00:00Z',
    uploadedBy: 'samantha.fernando@omobio.io',
    tags: ['font', 'noto'],
  },
  {
    id: 'ast_006',
    name: 'app-promo-video.mp4',
    type: 'video',
    mimeType: 'video/mp4',
    size: 18_440_000,
    url: '#',
    uploadedAt: '2026-08-25T16:45:00Z',
    uploadedBy: 'dilini.rathnayake@operator-x.com',
    tags: ['promo', 'video'],
  },
  {
    id: 'ast_007',
    name: 'tab-icon-home.svg',
    type: 'image',
    mimeType: 'image/svg+xml',
    size: 1_240,
    url: 'https://picsum.photos/id/103/128/128',
    width: 128,
    height: 128,
    uploadedAt: '2026-09-01T08:30:00Z',
    uploadedBy: 'nimali.perera@operator-x.com',
    tags: ['icon', 'ui'],
  },
  {
    id: 'ast_008',
    name: 'background-pattern.png',
    type: 'image',
    mimeType: 'image/png',
    size: 44_800,
    url: 'https://picsum.photos/id/104/1200/800',
    width: 1200,
    height: 800,
    uploadedAt: '2026-09-02T12:00:00Z',
    uploadedBy: 'roshan.de Silva@operator-x.com',
    tags: ['background', 'pattern'],
  },
  {
    id: 'ast_009',
    name: 'Privacy_Policy_2026.pdf',
    type: 'document',
    mimeType: 'application/pdf',
    size: 890_000,
    url: '#',
    uploadedAt: '2026-09-01T09:00:00Z',
    uploadedBy: 'samantha.fernando@omobio.io',
    tags: ['legal', 'policy'],
  },
  {
    id: 'ast_010',
    name: 'product-screenshot-ios.png',
    type: 'image',
    mimeType: 'image/png',
    size: 1_024_000,
    url: 'https://picsum.photos/id/105/1242/2688',
    width: 1242,
    height: 2688,
    uploadedAt: '2026-09-03T15:20:00Z',
    uploadedBy: 'dilini.rathnayake@operator-x.com',
    tags: ['screenshot', 'mobile'],
  },
  {
    id: 'ast_011',
    name: 'Roboto-Bold.ttf',
    type: 'font',
    mimeType: 'font/ttf',
    size: 124_800,
    url: '#',
    uploadedAt: '2026-06-01T08:05:00Z',
    uploadedBy: 'samantha.fernando@omobio.io',
    tags: ['font', 'roboto'],
  },
  {
    id: 'ast_012',
    name: 'onboarding-walkthrough.mp4',
    type: 'video',
    mimeType: 'video/mp4',
    size: 32_100_000,
    url: '#',
    uploadedAt: '2026-09-01T10:00:00Z',
    uploadedBy: 'nimali.perera@operator-x.com',
    tags: ['onboarding', 'video'],
  },
];

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function AssetIcon({ type }: { type: AssetType }) {
  const cls = 'w-5 h-5';
  switch (type) {
    case 'image': return <Image className={`${cls} text-blue-500`} />;
    case 'font': return <Type className={`${cls} text-purple-500`} />;
    case 'document': return <FileText className={`${cls} text-orange-500`} />;
    case 'video': return <Film className={`${cls} text-red-500`} />;
  }
}

function AssetTypeBadge({ type }: { type: AssetType }) {
  const map: Record<AssetType, { label: string; cls: string }> = {
    image: { label: 'Image', cls: 'bg-blue-100 text-blue-700' },
    font: { label: 'Font', cls: 'bg-purple-100 text-purple-700' },
    document: { label: 'Document', cls: 'bg-orange-100 text-orange-700' },
    video: { label: 'Video', cls: 'bg-red-100 text-red-700' },
  };
  const { label, cls } = map[type];
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${cls}`}>
      {label}
    </span>
  );
}

// ─── Upload zone ──────────────────────────────────────────────────────────────

interface UploadZoneProps {
  onUpload: (files: FileList) => void;
}

function UploadZone({ onUpload }: UploadZoneProps) {
  const [dragging, setDragging] = useState(false);

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      setDragging(false);
      if (e.dataTransfer.files.length) onUpload(e.dataTransfer.files);
    },
    [onUpload]
  );

  return (
    <div
      className={`border-2 border-dashed rounded-xl p-8 text-center transition-colors cursor-pointer ${
        dragging ? 'border-purple-500 bg-purple-50' : 'border-gray-300 hover:border-purple-400'
      }`}
      onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
      onDragLeave={() => setDragging(false)}
      onDrop={handleDrop}
      onClick={() => document.getElementById('file-input')?.click()}
    >
      <Upload className="w-8 h-8 mx-auto mb-2 text-gray-400" />
      <p className="font-medium text-gray-700">Drag &amp; drop files here, or click to browse</p>
      <p className="text-xs text-gray-400 mt-1">
        PNG, JPG, SVG, PDF, TTF, OTF, MP4 — max 50 MB per file
      </p>
      <input
        id="file-input"
        type="file"
        multiple
        className="hidden"
        onChange={(e) => e.target.files && onUpload(e.target.files)}
      />
    </div>
  );
}

// ─── Preview modal ────────────────────────────────────────────────────────────

interface PreviewModalProps {
  asset: Asset;
  onClose: () => void;
}

function PreviewModal({ asset, onClose }: PreviewModalProps) {
  const copyUrl = () => {
    navigator.clipboard.writeText(asset.url);
    toast.success('URL copied to clipboard');
  };

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <AssetIcon type={asset.type} />
            {asset.name}
          </DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          {asset.type === 'image' && asset.url !== '#' ? (
            <div className="rounded-lg overflow-hidden border bg-gray-50 flex items-center justify-center p-2">
              <img
                src={asset.url}
                alt={asset.name}
                className="max-h-[400px] object-contain rounded"
              />
            </div>
          ) : asset.type === 'video' ? (
            <div className="rounded-lg overflow-hidden border bg-gray-900 flex items-center justify-center h-48">
              <Film className="w-12 h-12 text-gray-500" />
              <span className="ml-3 text-gray-400">Video preview not available in browser</span>
            </div>
          ) : (
            <div className="rounded-lg border bg-gray-50 flex items-center justify-center h-32">
              <AssetIcon type={asset.type} />
              <span className="ml-3 text-gray-500">Preview not available</span>
            </div>
          )}
          <div className="grid grid-cols-2 gap-2 text-sm">
            <div>
              <span className="font-medium text-gray-600">Size:</span> {formatBytes(asset.size)}
            </div>
            {asset.width && asset.height && (
              <div>
                <span className="font-medium text-gray-600">Dimensions:</span> {asset.width} x {asset.height}px
              </div>
            )}
            <div>
              <span className="font-medium text-gray-600">Type:</span> {asset.mimeType}
            </div>
            <div>
              <span className="font-medium text-gray-600">Uploaded:</span>{' '}
              {new Date(asset.uploadedAt).toLocaleDateString()}
            </div>
            <div className="col-span-2">
              <span className="font-medium text-gray-600">URL:</span>{' '}
              <code className="text-xs bg-gray-100 px-1 py-0.5 rounded ml-1 break-all">
                {asset.url}
              </code>
            </div>
            {asset.tags && asset.tags.length > 0 && (
              <div className="col-span-2">
                <span className="font-medium text-gray-600">Tags:</span>{' '}
                {asset.tags.map((t) => (
                  <Badge key={t} variant="secondary" className="ml-1">
                    {t}
                  </Badge>
                ))}
              </div>
            )}
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={copyUrl}>
            <Copy className="w-4 h-4 mr-2" /> Copy URL
          </Button>
          {asset.url !== '#' && (
            <Button variant="outline" asChild>
              <a href={asset.url} target="_blank" rel="noopener noreferrer">
                <ExternalLink className="w-4 h-4 mr-2" /> Open
              </a>
            </Button>
          )}
          <Button variant="destructive" onClick={onClose}>
            Close
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// ─── Delete confirm modal ──────────────────────────────────────────────────────

interface DeleteModalProps {
  asset: Asset;
  onClose: () => void;
  onConfirm: () => void;
}

function DeleteModal({ asset, onClose, onConfirm }: DeleteModalProps) {
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>Delete Asset</DialogTitle>
        </DialogHeader>
        <p className="text-sm text-gray-600">
          Permanently delete <strong>{asset.name}</strong>? This cannot be undone.
        </p>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button variant="destructive" onClick={onConfirm}>
            <Trash2 className="w-4 h-4 mr-2" /> Delete
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// ─── Pagination ──────────────────────────────────────────────────────────────

const PAGE_SIZE = 8;

function usePagination<T>(items: T[], page: number) {
  const totalPages = Math.ceil(items.length / PAGE_SIZE);
  const pageItems = items.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
  return { pageItems, totalPages };
}

// ─── Main page ────────────────────────────────────────────────────────────────

export default function AssetManagerPage() {
  const { activeTenantId } = useActiveTenant();
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');
  const [search, setSearch] = useState('');
  const [filterType, setFilterType] = useState<AssetType | ''>('');
  const [page, setPage] = useState(1);
  const [assets, setAssets] = useState<Asset[]>(MOCK_ASSETS);
  const [previewAsset, setPreviewAsset] = useState<Asset | null>(null);
  const [deleteAsset, setDeleteAsset] = useState<Asset | null>(null);

  // Upload handler (mock)
  const handleUpload = (files: FileList) => {
    Array.from(files).forEach((file) => {
      const isImage = file.type.startsWith('image/');
      const newAsset: Asset = {
        id: `ast_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
        name: file.name,
        type: isImage ? 'image' : file.type.includes('font') ? 'font' : file.type.includes('pdf') ? 'document' : 'video',
        mimeType: file.type,
        size: file.size,
        url: isImage ? `https://picsum.photos/seed/${Date.now()}/800/600` : '#',
        width: isImage ? 800 : undefined,
        height: isImage ? 600 : undefined,
        uploadedAt: new Date().toISOString(),
        uploadedBy: 'current.admin@omobio.io',
      };
      setAssets((prev) => [newAsset, ...prev]);
      toast.success(`Uploaded: ${file.name}`);
    });
  };

  // Filtered list
  const filtered = assets.filter((a) => {
    const matchSearch = !search || a.name.toLowerCase().includes(search.toLowerCase());
    const matchType = !filterType || a.type === filterType;
    return matchSearch && matchType;
  });

  // Reset page when filters change
  const handleSearchChange = (v: string) => { setSearch(v); setPage(1); };
  const handleTypeChange = (v: AssetType | '') => { setFilterType(v); setPage(1); };

  const { pageItems, totalPages } = usePagination(filtered, page);

  const handleDelete = () => {
    if (!deleteAsset) return;
    setAssets((prev) => prev.filter((a) => a.id !== deleteAsset.id));
    toast.success(`Deleted: ${deleteAsset.name}`);
    setDeleteAsset(null);
  };

  const copyUrl = (asset: Asset) => {
    navigator.clipboard.writeText(asset.url);
    toast.success('URL copied');
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <ImageIcon className="w-6 h-6" />
            Asset Manager
          </h1>
          <p className="text-gray-500 mt-1">
            Upload and manage images, fonts, documents, and videos for{' '}
            <code className="bg-gray-100 px-1.5 py-0.5 rounded text-sm">
              {activeTenantId || '—'}
            </code>
          </p>
        </div>
        <div className="flex items-center gap-1 border rounded overflow-hidden">
          <button
            className={`p-2 ${viewMode === 'grid' ? 'bg-gray-100 text-purple-600' : 'text-gray-500 hover:bg-gray-50'}`}
            onClick={() => setViewMode('grid')}
            title="Grid view"
          >
            <Grid className="w-4 h-4" />
          </button>
          <button
            className={`p-2 ${viewMode === 'list' ? 'bg-gray-100 text-purple-600' : 'text-gray-500 hover:bg-gray-50'}`}
            onClick={() => setViewMode('list')}
            title="List view"
          >
            <List className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Upload zone */}
      <UploadZone onUpload={handleUpload} />

      {/* Filters */}
      <Card>
        <CardContent className="pt-4">
          <div className="flex flex-wrap gap-3">
            <div className="flex-1 min-w-[200px]">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                <Input
                  placeholder="Search assets…"
                  value={search}
                  onChange={(e) => handleSearchChange(e.target.value)}
                  className="pl-9"
                />
              </div>
            </div>
            <select
              className="border rounded px-3 py-2 text-sm"
              value={filterType}
              onChange={(e) => handleTypeChange(e.target.value as AssetType | '')}
            >
              <option value="">All Types</option>
              <option value="image">Image</option>
              <option value="document">Document</option>
              <option value="font">Font</option>
              <option value="video">Video</option>
            </select>
          </div>
        </CardContent>
      </Card>

      {/* Assets */}
      {filtered.length === 0 ? (
        <Card>
          <CardContent className="py-16 text-center text-gray-500">
            No assets match the current filters.
          </CardContent>
        </Card>
      ) : viewMode === 'grid' ? (
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
          {pageItems.map((asset) => (
            <Card
              key={asset.id}
              className="overflow-hidden hover:shadow-md transition-shadow cursor-pointer group"
              onClick={() => setPreviewAsset(asset)}
            >
              <div className="aspect-square bg-gray-100 flex items-center justify-center relative overflow-hidden">
                {asset.type === 'image' && asset.url !== '#' ? (
                  <img
                    src={asset.url}
                    alt={asset.name}
                    className="w-full h-full object-cover group-hover:scale-105 transition-transform"
                    loading="lazy"
                  />
                ) : (
                  <AssetIcon type={asset.type} />
                )}
                <div className="absolute inset-0 bg-black/0 group-hover:bg-black/40 transition-colors flex items-center justify-center gap-2 opacity-0 group-hover:opacity-100">
                  <button
                    className="p-1.5 bg-white rounded-full text-gray-700 hover:bg-gray-100"
                    onClick={(e) => { e.stopPropagation(); copyUrl(asset); }}
                    title="Copy URL"
                  >
                    <Copy className="w-4 h-4" />
                  </button>
                  <button
                    className="p-1.5 bg-white rounded-full text-red-600 hover:bg-red-50"
                    onClick={(e) => { e.stopPropagation(); setDeleteAsset(asset); }}
                    title="Delete"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
              <CardContent className="p-3">
                <p className="text-sm font-medium truncate" title={asset.name}>{asset.name}</p>
                <div className="flex items-center justify-between mt-1">
                  <AssetTypeBadge type={asset.type} />
                  <span className="text-xs text-gray-400">{formatBytes(asset.size)}</span>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      ) : (
        <Card>
          <CardContent className="p-0">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b bg-gray-50 text-left">
                    <th className="px-4 py-3 font-medium text-gray-600 w-10"></th>
                    <th className="px-4 py-3 font-medium text-gray-600">Name</th>
                    <th className="px-4 py-3 font-medium text-gray-600">Type</th>
                    <th className="px-4 py-3 font-medium text-gray-600">Size</th>
                    {viewMode === 'list' && <th className="px-4 py-3 font-medium text-gray-600">Dimensions</th>}
                    <th className="px-4 py-3 font-medium text-gray-600">Uploaded</th>
                    <th className="px-4 py-3 font-medium text-gray-600">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {pageItems.map((asset) => (
                    <tr
                      key={asset.id}
                      className="hover:bg-gray-50 transition-colors cursor-pointer"
                      onClick={() => setPreviewAsset(asset)}
                    >
                      <td className="px-4 py-3">
                        {asset.type === 'image' && asset.url !== '#' ? (
                          <img
                            src={asset.url}
                            alt={asset.name}
                            className="w-10 h-10 rounded object-cover"
                          />
                        ) : (
                          <div className="w-10 h-10 rounded bg-gray-100 flex items-center justify-center">
                            <AssetIcon type={asset.type} />
                          </div>
                        )}
                      </td>
                      <td className="px-4 py-3 font-medium">{asset.name}</td>
                      <td className="px-4 py-3"><AssetTypeBadge type={asset.type} /></td>
                      <td className="px-4 py-3 text-gray-500">{formatBytes(asset.size)}</td>
                      {viewMode === 'list' && (
                        <td className="px-4 py-3 text-gray-500">
                          {asset.width && asset.height ? `${asset.width}x${asset.height}` : '—'}
                        </td>
                      )}
                      <td className="px-4 py-3 text-gray-500">
                        {new Date(asset.uploadedAt).toLocaleDateString()}
                      </td>
                      <td className="px-4 py-3" onClick={(e) => e.stopPropagation()}>
                        <div className="flex items-center gap-1">
                          <Button size="sm" variant="ghost" onClick={() => copyUrl(asset)} title="Copy URL">
                            <Copy className="w-3 h-3" />
                          </Button>
                          {asset.url !== '#' && (
                            <Button size="sm" variant="ghost" asChild title="Open">
                              <a href={asset.url} target="_blank" rel="noopener noreferrer">
                                <ExternalLink className="w-3 h-3" />
                              </a>
                            </Button>
                          )}
                          <Button size="sm" variant="ghost" className="text-red-600" onClick={() => setDeleteAsset(asset)}>
                            <Trash2 className="w-3 h-3" />
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-gray-500">
            Showing {(page - 1) * PAGE_SIZE + 1}–{Math.min(page * PAGE_SIZE, filtered.length)} of {filtered.length} assets
          </p>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((p) => Math.max(1, p - 1))}
              disabled={page === 1}
            >
              <ChevronLeft className="w-4 h-4" />
            </Button>
            <span className="text-sm">Page {page} of {totalPages}</span>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
              disabled={page === totalPages}
            >
              <ChevronRight className="w-4 h-4" />
            </Button>
          </div>
        </div>
      )}

      {/* Modals */}
      {previewAsset && (
        <PreviewModal asset={previewAsset} onClose={() => setPreviewAsset(null)} />
      )}
      {deleteAsset && (
        <DeleteModal
          asset={deleteAsset}
          onClose={() => setDeleteAsset(null)}
          onConfirm={handleDelete}
        />
      )}
    </div>
  );
}
