/**
 * ProductMappingPage — maps operator product sources to canonical platform products.
 *
 * Telco operators (Dialog, Hutch, Airtel) expose their own product catalogs via
 * proprietary BSS APIs. This page allows admins to create a mapping layer between
 * those source products and the platform's canonical product model.
 *
 * Mappings are stored in MongoDB via the product-service and used at runtime
 * to transform operator-specific data into platform-native data (ADR-004).
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
  Package,
  Search,
  Plus,
  ArrowRight,
  Loader2,
  AlertTriangle,
  CheckCircle2,
  RefreshCw,
  Download,
  Upload,
  Smartphone,
  Wifi,
  Phone,
  Globe,
  CreditCard,
  Zap,
  Eye,
  X,
  Edit2,
  Trash2,
  ChevronRight,
} from 'lucide-react';
import { useActiveTenant } from '@/hooks/useActiveTenant';

// --- Types ---
interface SourceProduct {
  id: string;
  name: string;
  operatorCode: string;
  type: string;
  price: number;
  currency: string;
  isMapped: boolean;
  mappedTo?: string;
}

interface CanonicalCategory {
  id: string;
  name: string;
  icon: React.ComponentType<{ className?: string }>;
  subcategories: string[];
}

interface ProductMapping {
  id: string;
  sourceProductId: string;
  sourceProductName: string;
  canonicalCategoryId: string;
  canonicalCategoryName: string;
  subcategory: string;
  displayNameOverride?: string;
  priority: number;
  eligibilityRules?: string;
  badges?: string[];
  activationProvider?: string;
  priceMapping?: string;
}

interface MobilePreview {
  name: string;
  category: string;
  subcategory: string;
  displayName?: string;
  badges: string[];
  price: string;
}

// --- Mock Data ---
const MOCK_SOURCE_PRODUCTS: SourceProduct[] = [
  // Unmapped products
  { id: 'DIA-DS-001', name: 'Daily Surfer 10MB', operatorCode: 'DIALOG', type: 'data', price: 15, currency: 'LKR', isMapped: false },
  { id: 'DIA-DS-002', name: 'Daily Surfer 25MB', operatorCode: 'DIALOG', type: 'data', price: 30, currency: 'LKR', isMapped: false },
  { id: 'DIA-DS-003', name: 'Daily Surfer 50MB', operatorCode: 'DIALOG', type: 'data', price: 50, currency: 'LKR', isMapped: false },
  { id: 'DIA-WD-001', name: 'Weekly Data 100MB', operatorCode: 'DIALOG', type: 'data', price: 150, currency: 'LKR', isMapped: false },
  { id: 'DIA-WD-002', name: 'Weekly Data 250MB', operatorCode: 'DIALOG', type: 'data', price: 300, currency: 'LKR', isMapped: false },
  { id: 'DIA-MD-001', name: 'Monthly Data 500MB', operatorCode: 'DIALOG', type: 'data', price: 500, currency: 'LKR', isMapped: false },
  { id: 'DIA-MD-002', name: 'Monthly Data 1GB', operatorCode: 'DIALOG', type: 'data', price: 850, currency: 'LKR', isMapped: false },
  { id: 'DIA-VP-001', name: 'Voice Pack 100 mins', operatorCode: 'DIALOG', type: 'voice', price: 200, currency: 'LKR', isMapped: false },
  { id: 'DIA-VP-002', name: 'Voice Pack 300 mins', operatorCode: 'DIALOG', type: 'voice', price: 450, currency: 'LKR', isMapped: false },
  { id: 'DIA-VP-003', name: 'Voice Pack Unlimited', operatorCode: 'DIALOG', type: 'voice', price: 1200, currency: 'LKR', isMapped: false },
  { id: 'DIA-SMS-001', name: 'SMS Bundle 100', operatorCode: 'DIALOG', type: 'sms', price: 50, currency: 'LKR', isMapped: false },
  // Mapped products
  { id: 'DIA-DS-004', name: 'Daily Surfer 100MB', operatorCode: 'DIALOG', type: 'data', price: 75, currency: 'LKR', isMapped: true, mappedTo: 'DYNAMIC_DATA' },
  { id: 'DIA-WD-003', name: 'Weekly Data 500MB', operatorCode: 'DIALOG', type: 'data', price: 500, currency: 'LKR', isMapped: true, mappedTo: 'DYNAMIC_DATA' },
  { id: 'DIA-MD-003', name: 'Monthly Data 2GB', operatorCode: 'DIALOG', type: 'data', price: 1500, currency: 'LKR', isMapped: true, mappedTo: 'DYNAMIC_DATA' },
  { id: 'DIA-MD-004', name: 'Monthly Data 5GB', operatorCode: 'DIALOG', type: 'data', price: 2500, currency: 'LKR', isMapped: true, mappedTo: 'DYNAMIC_DATA' },
  { id: 'DIA-INTL-001', name: 'International Call India', operatorCode: 'DIALOG', type: 'roaming', price: 15, currency: 'LKR', isMapped: true, mappedTo: 'INTL_CALLS' },
  { id: 'HUT-DS-001', name: 'Hutch Daily 20MB', operatorCode: 'HUTCH', type: 'data', price: 18, currency: 'LKR', isMapped: false },
  { id: 'HUT-WD-001', name: 'Hutch Weekly 150MB', operatorCode: 'HUTCH', type: 'data', price: 175, currency: 'LKR', isMapped: false },
  { id: 'HUT-MD-001', name: 'Hutch Monthly 750MB', operatorCode: 'HUTCH', type: 'data', price: 650, currency: 'LKR', isMapped: false },
  { id: 'HUT-VP-001', name: 'Hutch Voice 200 mins', operatorCode: 'HUTCH', type: 'voice', price: 350, currency: 'LKR', isMapped: false },
  { id: 'AIR-DS-001', name: 'Airtel Daily 15MB', operatorCode: 'AIRTEL', type: 'data', price: 20, currency: 'LKR', isMapped: false },
  { id: 'AIR-WD-001', name: 'Airtel Weekly 200MB', operatorCode: 'AIRTEL', type: 'data', price: 220, currency: 'LKR', isMapped: false },
  { id: 'AIR-MD-001', name: 'Airtel Monthly 1.5GB', operatorCode: 'AIRTEL', type: 'data', price: 1100, currency: 'LKR', isMapped: false },
];

const MOCK_CANONICAL_CATEGORIES: CanonicalCategory[] = [
  { id: 'DYNAMIC_DATA', name: 'Dynamic Data Packs', icon: Wifi, subcategories: ['Daily', 'Weekly', 'Monthly', 'Annual', 'Night Owl', 'Social Pack'] },
  { id: 'VOICE_PACKS', name: 'Voice Packs', icon: Phone, subcategories: ['On-Net', 'Off-Net', 'International', 'Unlimited', 'Night Call'] },
  { id: 'SMS_BUNDLES', name: 'SMS & Messaging', icon: Smartphone, subcategories: ['Local SMS', 'International SMS', 'MMS', 'WhatsApp Bundle'] },
  { id: 'ROAMING', name: 'Roaming & International', icon: Globe, subcategories: ['IDD Rates', 'Roaming Packs', 'Tourist Packs', 'Business Roaming'] },
  { id: 'BUNDLE_COMBOS', name: 'Bundle Combos', icon: Package, subcategories: ['Voice + Data', 'Triple Play', 'Family Plans', 'Student Plans'] },
  { id: 'VAS', name: 'Value Added Services', icon: Zap, subcategories: ['Music', 'Video', 'Gaming', 'News', 'Religious'] },
  { id: 'DEVICE_PLANS', name: 'Device & Hardware', icon: Smartphone, subcategories: ['Phone Installments', 'Device Insurance', 'Accessories'] },
  { id: 'BILL_PAY', name: 'Bill Payment & Topup', icon: CreditCard, subcategories: ['Prepaid Topup', 'Postpaid Bill', 'Auto Pay'] },
];

const MOCK_MAPPINGS: ProductMapping[] = [
  { id: 'map-001', sourceProductId: 'DIA-DS-004', sourceProductName: 'Daily Surfer 100MB', canonicalCategoryId: 'DYNAMIC_DATA', canonicalCategoryName: 'Dynamic Data Packs', subcategory: 'Daily', displayNameOverride: 'Daily Surfer 100MB', priority: 1, badges: ['Popular'], activationProvider: 'DIALOG_ACTIVATION', priceMapping: 'match_operator_price' },
  { id: 'map-002', sourceProductId: 'DIA-WD-003', sourceProductName: 'Weekly Data 500MB', canonicalCategoryId: 'DYNAMIC_DATA', canonicalCategoryName: 'Dynamic Data Packs', subcategory: 'Weekly', priority: 1, activationProvider: 'DIALOG_ACTIVATION' },
  { id: 'map-003', sourceProductId: 'DIA-MD-003', sourceProductName: 'Monthly Data 2GB', canonicalCategoryId: 'DYNAMIC_DATA', canonicalCategoryName: 'Dynamic Data Packs', subcategory: 'Monthly', priority: 1, badges: ['Best Value'], activationProvider: 'DIALOG_ACTIVATION', eligibilityRules: 'postpaid_only' },
  { id: 'map-004', sourceProductId: 'DIA-MD-004', sourceProductName: 'Monthly Data 5GB', canonicalCategoryId: 'DYNAMIC_DATA', canonicalCategoryName: 'Dynamic Data Packs', subcategory: 'Monthly', priority: 2, badges: ['Premium'], activationProvider: 'DIALOG_ACTIVATION' },
  { id: 'map-005', sourceProductId: 'DIA-INTL-001', sourceProductName: 'International Call India', canonicalCategoryId: 'ROAMING', canonicalCategoryName: 'Roaming & International', subcategory: 'International', priority: 1, activationProvider: 'DIALOG_ACTIVATION' },
];

const MOCK_MOBILE_PREVIEW: MobilePreview = {
  name: 'Daily Surfer 100MB',
  category: 'Dynamic Data Packs',
  subcategory: 'Daily',
  badges: ['Popular'],
  price: 'LKR 75',
};

// --- Component ---
export default function ProductMappingPage() {
  const { activeTenantId } = useActiveTenant();
  const [searchQuery, setSearchQuery] = useState('');
  const [filterOperator, setFilterOperator] = useState('ALL');
  const [filterMapped, setFilterMapped] = useState<'all' | 'mapped' | 'unmapped'>('all');
  const [selectedProducts, setSelectedProducts] = useState<Set<string>>(new Set());
  const [editingMapping, setEditingMapping] = useState<ProductMapping | null>(null);
  const [showMappingDialog, setShowMappingDialog] = useState(false);
  const [selectedSourceProduct, setSelectedSourceProduct] = useState<SourceProduct | null>(null);
  const [saving, setSaving] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [showPreview, setShowPreview] = useState(false);

  // Mapping form state
  const [mappingForm, setMappingForm] = useState({
    canonicalCategoryId: '',
    canonicalCategoryName: '',
    subcategory: '',
    displayNameOverride: '',
    priority: 1,
    eligibilityRules: '',
    badges: '',
    activationProvider: '',
    priceMapping: 'match_operator_price',
  });

  const filteredProducts = MOCK_SOURCE_PRODUCTS.filter((product) => {
    const matchesSearch =
      !searchQuery ||
      product.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      product.id.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesOperator = filterOperator === 'ALL' || product.operatorCode === filterOperator;
    const matchesMapped =
      filterMapped === 'all' ||
      (filterMapped === 'mapped' && product.isMapped) ||
      (filterMapped === 'unmapped' && !product.isMapped);
    return matchesSearch && matchesOperator && matchesMapped;
  });

  const unmappedCount = MOCK_SOURCE_PRODUCTS.filter((p) => !p.isMapped).length;
  const mappedCount = MOCK_SOURCE_PRODUCTS.filter((p) => p.isMapped).length;

  const openCreateMapping = (product: SourceProduct) => {
    setSelectedSourceProduct(product);
    setEditingMapping(null);
    setMappingForm({
      canonicalCategoryId: '',
      canonicalCategoryName: '',
      subcategory: '',
      displayNameOverride: product.name,
      priority: 1,
      eligibilityRules: '',
      badges: '',
      activationProvider: 'DIALOG_ACTIVATION',
      priceMapping: 'match_operator_price',
    });
    setShowMappingDialog(true);
  };

  const openEditMapping = (mapping: ProductMapping) => {
    setSelectedSourceProduct(null);
    setEditingMapping(mapping);
    setMappingForm({
      canonicalCategoryId: mapping.canonicalCategoryId,
      canonicalCategoryName: mapping.canonicalCategoryName,
      subcategory: mapping.subcategory,
      displayNameOverride: mapping.displayNameOverride || '',
      priority: mapping.priority,
      eligibilityRules: mapping.eligibilityRules || '',
      badges: mapping.badges?.join(', ') || '',
      activationProvider: mapping.activationProvider || '',
      priceMapping: mapping.priceMapping || 'match_operator_price',
    });
    setShowMappingDialog(true);
  };

  const handleSaveMapping = async () => {
    setSaving(true);
    await new Promise((r) => setTimeout(r, 800));
    setSaving(false);
    toast.success(editingMapping ? 'Mapping updated' : 'Mapping created');
    setShowMappingDialog(false);
    setSelectedSourceProduct(null);
    setEditingMapping(null);
  };

  const handleBulkMap = (categoryId: string, categoryName: string) => {
    if (selectedProducts.size === 0) return;
    toast.success(`Mapped ${selectedProducts.size} product(s) to ${categoryName}`);
    setSelectedProducts(new Set());
  };

  const handleSync = async () => {
    setSyncing(true);
    await new Promise((r) => setTimeout(r, 1500));
    setSyncing(false);
    toast.success('Synced 3 new products from Dialog BSS API');
  };

  const handleExport = () => {
    const data = JSON.stringify(MOCK_MAPPINGS, null, 2);
    const blob = new Blob([data], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `product-mappings-${activeTenantId || 'export'}.json`;
    a.click();
    toast.success('Mappings exported');
  };

  const handleImport = () => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.json';
    input.onchange = (e) => {
      const file = (e.target as HTMLInputElement).files?.[0];
      if (file) {
        toast.success(`Imported mappings from ${file.name}`);
      }
    };
    input.click();
  };

  const toggleSelect = (id: string) => {
    setSelectedProducts((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const selectedCategory = MOCK_CANONICAL_CATEGORIES.find((c) => c.id === mappingForm.canonicalCategoryId);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <Package className="w-6 h-6" />
            Product Mapping
          </h1>
          <p className="text-gray-500 mt-1">
            Map operator products to canonical catalog for{' '}
            <code className="bg-gray-100 px-1.5 py-0.5 rounded text-sm">
              {activeTenantId || '— no client selected —'}
            </code>
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={handleImport}>
            <Upload className="w-4 h-4 mr-2" />
            Import
          </Button>
          <Button variant="outline" onClick={handleExport}>
            <Download className="w-4 h-4 mr-2" />
            Export
          </Button>
          <Button variant="outline" onClick={handleSync} disabled={syncing}>
            {syncing ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <RefreshCw className="w-4 h-4 mr-2" />}
            {syncing ? 'Syncing...' : 'Sync from Operator'}
          </Button>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-3 gap-4">
        <Card>
          <CardContent className="pt-4">
            <div className="text-sm text-gray-500">Total Products</div>
            <div className="text-2xl font-bold">{MOCK_SOURCE_PRODUCTS.length}</div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4">
            <div className="text-sm text-gray-500">Mapped</div>
            <div className="text-2xl font-bold text-green-600">{mappedCount}</div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4">
            <div className="text-sm text-gray-500">Unmapped</div>
            <div className="text-2xl font-bold text-yellow-600">{unmappedCount}</div>
          </CardContent>
        </Card>
      </div>

      <div className="flex gap-6">
        {/* Left panel - Source products */}
        <div className="flex-1 space-y-4">
          {/* Filters */}
          <div className="flex items-center gap-3">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
              <Input
                placeholder="Search by product name or ID..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="pl-9"
              />
            </div>
            <select
              className="border rounded px-3 py-2 text-sm"
              value={filterOperator}
              onChange={(e) => setFilterOperator(e.target.value)}
            >
              <option value="ALL">All Operators</option>
              <option value="DIALOG">Dialog</option>
              <option value="HUTCH">Hutch</option>
              <option value="AIRTEL">Airtel</option>
            </select>
            <select
              className="border rounded px-3 py-2 text-sm"
              value={filterMapped}
              onChange={(e) => setFilterMapped(e.target.value as any)}
            >
              <option value="all">All</option>
              <option value="mapped">Mapped</option>
              <option value="unmapped">Unmapped</option>
            </select>
            {selectedProducts.size > 0 && (
              <div className="flex items-center gap-2">
                <span className="text-sm text-gray-500">{selectedProducts.size} selected</span>
                <Button size="sm" variant="outline" onClick={() => setSelectedProducts(new Set())}>
                  Clear
                </Button>
              </div>
            )}
          </div>

          {/* Bulk mapping actions */}
          {selectedProducts.size > 0 && (
            <div className="flex items-center gap-2 p-3 bg-purple-50 rounded-lg">
              <span className="text-sm font-medium">Bulk map to:</span>
              {MOCK_CANONICAL_CATEGORIES.slice(0, 3).map((cat) => (
                <Button
                  key={cat.id}
                  size="sm"
                  variant="secondary"
                  onClick={() => handleBulkMap(cat.id, cat.name)}
                >
                  {cat.name}
                </Button>
              ))}
            </div>
          )}

          {/* Source products list */}
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-base">Source Products</CardTitle>
              <CardDescription>
                Products from operator BSS APIs — click to map
              </CardDescription>
            </CardHeader>
            <CardContent className="p-0">
              <div className="max-h-[500px] overflow-y-auto">
                {filteredProducts.map((product) => (
                  <div
                    key={product.id}
                    className={`flex items-center gap-3 px-4 py-3 border-b hover:bg-gray-50 cursor-pointer ${
                      !product.isMapped ? 'bg-yellow-50' : ''
                    }`}
                    onClick={() =>
                      product.isMapped
                        ? openEditMapping(MOCK_MAPPINGS.find((m) => m.sourceProductId === product.id)!)
                        : openCreateMapping(product)
                    }
                  >
                    <input
                      type="checkbox"
                      checked={selectedProducts.has(product.id)}
                      onChange={(e) => {
                        e.stopPropagation();
                        toggleSelect(product.id);
                      }}
                      className="rounded"
                    />
                    <div className="flex-1">
                      <div className="flex items-center gap-2">
                        <span className="font-medium text-sm">{product.name}</span>
                        {!product.isMapped && (
                          <AlertTriangle className="w-3 h-3 text-yellow-500" />
                        )}
                      </div>
                      <div className="text-xs text-gray-500">
                        {product.id} · {product.operatorCode} · {product.type}
                      </div>
                    </div>
                    <div className="text-sm font-medium">
                      {product.currency} {product.price.toLocaleString()}
                    </div>
                    <div>
                      {product.isMapped ? (
                        <Badge variant="default" className="bg-green-100 text-green-700">
                          Mapped
                        </Badge>
                      ) : (
                        <Badge variant="secondary" className="bg-yellow-100 text-yellow-700">
                          Unmapped
                        </Badge>
                      )}
                    </div>
                    <ChevronRight className="w-4 h-4 text-gray-400" />
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Right panel - Canonical categories */}
        <div className="w-80 shrink-0 space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="font-semibold text-sm text-gray-500 uppercase">Canonical Categories</h3>
            <Button size="sm" variant="ghost">
              <Plus className="w-3 h-3 mr-1" /> Add
            </Button>
          </div>
          {MOCK_CANONICAL_CATEGORIES.map((category) => {
            const Icon = category.icon;
            const categoryMappings = MOCK_MAPPINGS.filter((m) => m.canonicalCategoryId === category.id);
            return (
              <Card key={category.id} className="hover:shadow-md transition-shadow cursor-pointer">
                <CardHeader className="py-3 px-4">
                  <div className="flex items-center gap-2">
                    <div className="w-8 h-8 rounded bg-purple-100 flex items-center justify-center">
                      <Icon className="w-4 h-4 text-purple-600" />
                    </div>
                    <div className="flex-1">
                      <CardTitle className="text-sm">{category.name}</CardTitle>
                      <CardDescription className="text-xs">
                        {categoryMappings.length} products mapped
                      </CardDescription>
                    </div>
                  </div>
                </CardHeader>
                <CardContent className="px-4 pb-3 pt-0">
                  <div className="flex flex-wrap gap-1">
                    {category.subcategories.slice(0, 3).map((sub) => (
                      <span key={sub} className="text-xs bg-gray-100 px-2 py-0.5 rounded">
                        {sub}
                      </span>
                    ))}
                    {category.subcategories.length > 3 && (
                      <span className="text-xs text-gray-500">
                        +{category.subcategories.length - 3} more
                      </span>
                    )}
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>
      </div>

      {/* Mapping Dialog */}
      <Dialog open={showMappingDialog} onOpenChange={setShowMappingDialog}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>
              {editingMapping ? 'Edit Mapping' : 'Create Product Mapping'}
            </DialogTitle>
            {selectedSourceProduct && (
              <p className="text-sm text-gray-500">
                Mapping: <strong>{selectedSourceProduct.name}</strong> ({selectedSourceProduct.id})
              </p>
            )}
          </DialogHeader>

          <div className="space-y-4 max-h-[60vh] overflow-y-auto pr-2">
            {/* Canonical category */}
            <div>
              <Label>Canonical Category</Label>
              <select
                className="w-full border rounded px-3 py-2"
                value={mappingForm.canonicalCategoryId}
                onChange={(e) => {
                  const cat = MOCK_CANONICAL_CATEGORIES.find((c) => c.id === e.target.value);
                  setMappingForm({
                    ...mappingForm,
                    canonicalCategoryId: e.target.value,
                    canonicalCategoryName: cat?.name || '',
                    subcategory: '',
                  });
                }}
              >
                <option value="">Select category...</option>
                {MOCK_CANONICAL_CATEGORIES.map((cat) => (
                  <option key={cat.id} value={cat.id}>{cat.name}</option>
                ))}
              </select>
            </div>

            {/* Subcategory */}
            {selectedCategory && (
              <div>
                <Label>Subcategory</Label>
                <select
                  className="w-full border rounded px-3 py-2"
                  value={mappingForm.subcategory}
                  onChange={(e) => setMappingForm({ ...mappingForm, subcategory: e.target.value })}
                >
                  <option value="">Select subcategory...</option>
                  {selectedCategory.subcategories.map((sub) => (
                    <option key={sub} value={sub}>{sub}</option>
                  ))}
                </select>
              </div>
            )}

            {/* Display name override */}
            <div>
              <Label>Display Name Override</Label>
              <Input
                value={mappingForm.displayNameOverride}
                onChange={(e) => setMappingForm({ ...mappingForm, displayNameOverride: e.target.value })}
                placeholder="Leave blank to use operator product name"
              />
            </div>

            {/* Priority */}
            <div>
              <Label>Priority / Ranking</Label>
              <Input
                type="number"
                min={1}
                max={10}
                value={mappingForm.priority}
                onChange={(e) => setMappingForm({ ...mappingForm, priority: parseInt(e.target.value) || 1 })}
              />
              <p className="text-xs text-gray-400 mt-1">1 = highest priority in listings</p>
            </div>

            {/* Eligibility rules */}
            <div>
              <Label>Eligibility Rules</Label>
              <Textarea
                value={mappingForm.eligibilityRules}
                onChange={(e) => setMappingForm({ ...mappingForm, eligibilityRules: e.target.value })}
                placeholder="e.g. postpaid_only, tenure > 30, segment = VIP"
                rows={2}
              />
            </div>

            {/* Badges */}
            <div>
              <Label>Badges / Icons</Label>
              <Input
                value={mappingForm.badges}
                onChange={(e) => setMappingForm({ ...mappingForm, badges: e.target.value })}
                placeholder="e.g. Popular, Best Value, New"
              />
              <p className="text-xs text-gray-400 mt-1">Comma-separated</p>
            </div>

            {/* Activation provider */}
            <div>
              <Label>Activation Provider</Label>
              <select
                className="w-full border rounded px-3 py-2"
                value={mappingForm.activationProvider}
                onChange={(e) => setMappingForm({ ...mappingForm, activationProvider: e.target.value })}
              >
                <option value="">Select provider...</option>
                <option value="DIALOG_ACTIVATION">Dialog Activation Service</option>
                <option value="HUTCH_ACTIVATION">Hutch Activation Service</option>
                <option value="AIRTEL_ACTIVATION">Airtel Activation Service</option>
                <option value="GENERIC">Generic (Cross-operator)</option>
              </select>
            </div>

            {/* Price mapping */}
            <div>
              <Label>Price Mapping</Label>
              <select
                className="w-full border rounded px-3 py-2"
                value={mappingForm.priceMapping}
                onChange={(e) => setMappingForm({ ...mappingForm, priceMapping: e.target.value })}
              >
                <option value="match_operator_price">Match operator price exactly</option>
                <option value="add_markup">Add markup percentage</option>
                <option value="fixed_conversion">Fixed currency conversion</option>
                <option value="custom">Custom mapping</option>
              </select>
            </div>

            {/* Preview button */}
            <div className="pt-2 border-t">
              <Button variant="outline" size="sm" onClick={() => setShowPreview(true)}>
                <Eye className="w-4 h-4 mr-2" />
                Preview in Mobile App
              </Button>
            </div>
          </div>

          <DialogFooter className="gap-2">
            <Button variant="outline" onClick={() => setShowMappingDialog(false)}>
              Cancel
            </Button>
            {editingMapping && (
              <Button
                variant="outline"
                className="text-red-600"
                onClick={() => {
                  if (confirm('Delete this mapping?')) {
                    toast.success('Mapping deleted');
                    setShowMappingDialog(false);
                  }
                }}
              >
                <Trash2 className="w-4 h-4 mr-2" />
                Delete
              </Button>
            )}
            <Button onClick={handleSaveMapping} disabled={saving || !mappingForm.canonicalCategoryId}>
              {saving ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : null}
              {saving ? 'Saving...' : editingMapping ? 'Update Mapping' : 'Create Mapping'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Mobile Preview Dialog */}
      <Dialog open={showPreview} onOpenChange={setShowPreview}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Mobile App Preview</DialogTitle>
          </DialogHeader>
          <div className="bg-gray-100 rounded-lg p-4">
            {/* Mock phone frame */}
            <div className="bg-white rounded-xl shadow-lg mx-auto max-w-xs overflow-hidden">
              <div className="bg-purple-600 text-white px-4 py-3">
                <div className="text-sm font-medium">Available Packs</div>
              </div>
              <div className="p-3 space-y-2">
                <div className="border rounded-lg p-3">
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-2">
                      <Wifi className="w-4 h-4 text-purple-600" />
                      <span className="font-medium text-sm">
                        {mappingForm.displayNameOverride || selectedSourceProduct?.name || MOCK_MOBILE_PREVIEW.name}
                      </span>
                    </div>
                    {mappingForm.badges && mappingForm.badges.split(',')[0] && (
                      <Badge variant="default" className="text-xs bg-purple-100 text-purple-700">
                        {mappingForm.badges.split(',')[0].trim()}
                      </Badge>
                    )}
                  </div>
                  <div className="text-xs text-gray-500 mb-2">
                    {selectedCategory?.name || MOCK_MOBILE_PREVIEW.category} ·{' '}
                    {mappingForm.subcategory || MOCK_MOBILE_PREVIEW.subcategory}
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="font-bold text-purple-600">
                      {selectedSourceProduct?.currency} {selectedSourceProduct?.price || 75}
                    </span>
                    <button className="bg-purple-600 text-white text-xs px-3 py-1 rounded">
                      Activate
                    </button>
                  </div>
                </div>
              </div>
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
