/**
 * AssetManagerPage — manage uploaded assets (images, fonts, documents, video).
 *
 * Assets are stored in object storage (S3 / GCS / MinIO) with metadata in MongoDB.
 * The backend API /api/v1/admin/assets is served by AdminAssetController.
 *
 * Features:
 *   - Grid / list toggle view
 *   - Drag-and-drop upload area (uploads via API to object storage)
 *   - Filter by asset type (image, font, document, video)
 *   - Search by name
 *   - Asset preview modal for images
 *   - Copy URL button
 *   - Delete with confirmation
 *   - Pagination
 */
import { useState, useCallback } from 'react';
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
import { api } from '@/lib/api';

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
// ─── Helpers ──────────────────────────────────────────────────────────────────

export interface AssetDocumentDto {
  id: string;
  tenantId?: string;
  type: string;
  filename: string;
  mimeType: string;
  sizeBytes: number;
  url: string;
  storageKey?: string;
  width?: number;
  height?: number;
  altText?: string;
  tags?: string[];
  status?: string;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

function toAsset(doc: AssetDocumentDto): Asset {
  const type: AssetType = normAssetType(doc.type);
  return {
    id: doc.id,
    name: doc.filename ?? 'unnamed',
    type,
    mimeType: doc.mimeType ?? '',
    size: doc.sizeBytes ?? 0,
    url: doc.url ?? '#',
    width: doc.width,
    height: doc.height,
    uploadedAt: doc.createdAt ?? '',
    uploadedBy: doc.createdBy ?? '',
    tags: doc.tags ?? [],
  };
}

function normAssetType(type: string): AssetType {
  const t = (type ?? '').toLowerCase();
  if (t === 'image' || t === 'icon' || t === 'logo' || t === 'banner') return 'image';
  if (t === 'font') return 'font';
  if (t === 'document' || t === 'video') return t as AssetType;
  if (t.startsWith('image')) return 'image';
  if (t.startsWith('video')) return 'video';
  if (t.startsWith('font')) return 'font';
  if (t.startsWith('audio')) return 'video';
  return 'document';
}

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
  const queryClient = useQueryClient();
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');
  const [search, setSearch] = useState('');
  const [filterType, setFilterType] = useState<AssetType | ''>('');
  const [page, setPage] = useState(1);
  const [previewAsset, setPreviewAsset] = useState<Asset | null>(null);
  const [deleteAsset, setDeleteAsset] = useState<Asset | null>(null);

  // Load assets from API
  const { data: assets = [], isLoading } = useQuery({
    queryKey: ['assets', activeTenantId, filterType, search, page],
    queryFn: async () => {
      const res = await api.assets.list({
        type: filterType || undefined,
        q: search || undefined,
        page: page - 1,
      });
      const list = (Array.isArray(res) ? res : (res as any)?.data ?? []) as AssetDocumentDto[];
      if (Array.isArray((res as any)?.content)) {
        return ((res as any).content as AssetDocumentDto[]).map(toAsset);
      }
      return list.map(toAsset);
    },
    enabled: !!activeTenantId,
  });

  // Upload handler (real API)
  const uploadMutation = useMutation({
    mutationFn: ({ file, altText, tags }: { file: File; altText?: string; tags?: string }) => {
      const fd = new FormData();
      fd.append('file', file);
      fd.append('tenantId', activeTenantId ?? '');
      if (altText) fd.append('altText', altText);
      if (tags) fd.append('tags', tags);
      return api.assets.upload(fd);
    },
    onSuccess: (_, vars) => {
      queryClient.invalidateQueries({ queryKey: ['assets'] });
      toast.success(`Uploaded: ${vars.file.name}`);
    },
    onError: (err: any) => toast.error(err.message || 'Upload failed'),
  });

  const handleUpload = (files: FileList) => {
    Array.from(files).forEach((file) => {
      const isImage = file.type.startsWith('image/');
      const type = isImage ? 'image' : file.type.includes('font') ? 'font' : file.type.includes('pdf') ? 'document' : 'video';
      uploadMutation.mutate({ file, tags: type });
    });
  };

  // Delete handler (real API)
  const deleteMutation = useMutation({
    mutationFn: (asset: Asset) => api.assets.delete(asset.id),
    onSuccess: (_, asset) => {
      queryClient.invalidateQueries({ queryKey: ['assets'] });
      toast.success(`Deleted: ${asset.name}`);
      setDeleteAsset(null);
    },
    onError: (err: any) => toast.error(err.message || 'Delete failed'),
  });

  const handleDelete = () => {
    if (!deleteAsset) return;
    deleteMutation.mutate(deleteAsset);
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
      {isLoading ? (
        <Card>
          <CardContent className="py-16 text-center text-gray-500">
            Loading assets…
          </CardContent>
        </Card>
      ) : filtered.length === 0 ? (
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
