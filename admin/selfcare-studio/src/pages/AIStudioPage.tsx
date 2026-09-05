/**
 * AI Studio — Admin page for configuring the AI assistant.
 *
 * Tabs:
 *   1. Overview      — current AI config summary + usage metrics
 *   2. Model Config — LLM provider, model selection, API keys
 *   3. Tools        — tool permissions (AUTO / APPROVAL / DENIED) per tool
 *   4. Prompts      — system prompt editor with variable preview
 *   5. Knowledge Base — RAG knowledge chunks, similarity search
 *   6. Safety        — moderation settings
 *
 * Data flow:
 *   GET/PUT /api/v1/admin/ai/config  → AI config
 *   GET/PUT /api/v1/admin/ai/tools   → tool permissions
 *   GET/PUT /api/v1/admin/ai/prompts → prompt templates
 *   GET/POST /api/v1/admin/ai/knowledge → RAG chunks
 *   GET /api/v1/admin/ai/usage  → usage metrics
 */
import { useState, useEffect, useCallback } from 'react';
import {
  Tabs, TabsContent, TabsList, TabsTrigger,
} from '@/components/ui/tabs';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Badge } from '@/components/ui/badge';
import { toast } from 'sonner';
import {
  Bot, Zap, Shield, BookOpen, Brain, AlertTriangle,
  CheckCircle, XCircle, Clock, DollarSign,
  Plus, Search, Trash2, RefreshCw, Save, Play,
  ChevronRight,
} from 'lucide-react';
import { api } from '@/lib/api';
import { useActiveTenant } from '@/hooks/useActiveTenant';

// ─────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────

interface AiConfig {
  provider: string;
  model?: string;
  moderationEnabled: boolean;
  ragEnabled: boolean;
  toolsEnabled: boolean;
  usage: Record<string, any>;
}

interface ToolDef {
  name: string;
  description: string;
  permission: 'AUTO' | 'APPROVAL' | 'DENIED' | 'NONE';
  inputSchema?: Record<string, any>;
}

interface PromptTemplate {
  id: string;
  content: string;
  isCustomOverride: boolean;
}

interface KnowledgeChunk {
  chunkId: string;
  text: string;
  similarity?: number;
  metadata?: Record<string, string>;
}

interface UsageMetrics {
  tenantId: string;
  inputTokensToday: number;
  outputTokensToday: number;
  requestCountToday: number;
  estimatedCostUsd: number;
}

// ─────────────────────────────────────────────────────────
// Tool permission badge
// ─────────────────────────────────────────────────────────

function PermBadge({ perm }: { perm: ToolDef['permission'] }) {
  const cfg = {
    AUTO: { color: 'bg-green-100 text-green-800', icon: Zap },
    APPROVAL: { color: 'bg-yellow-100 text-yellow-800', icon: Clock },
    DENIED: { color: 'bg-red-100 text-red-800', icon: XCircle },
    NONE: { color: 'bg-gray-100 text-gray-500', icon: XCircle },
  }[perm];
  const Icon = cfg.icon;
  return (
    <Badge className={`${cfg.color} gap-1`}>
      <Icon size={12} /> {perm}
    </Badge>
  );
}

// ─────────────────────────────────────────────────────────
// Overview Tab
// ─────────────────────────────────────────────────────────

function OverviewTab({ config, usage }: { config: AiConfig; usage: UsageMetrics | null }) {
  return (
    <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-sm font-medium">Provider</CardTitle>
          <Bot size={16} className="text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-bold capitalize">{config.provider ?? 'anthropic'}</div>
          <p className="text-xs text-muted-foreground">Active LLM provider</p>
        </CardContent>
      </Card>
      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-sm font-medium">Requests Today</CardTitle>
          <Zap size={16} className="text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-bold">{usage?.requestCountToday ?? 0}</div>
          <p className="text-xs text-muted-foreground">AI chat requests</p>
        </CardContent>
      </Card>
      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-sm font-medium">Tokens Today</CardTitle>
          <Brain size={16} className="text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-bold">
            {((usage?.inputTokensToday ?? 0) + (usage?.outputTokensToday ?? 0)).toLocaleString()}
          </div>
          <p className="text-xs text-muted-foreground">
            {((usage?.inputTokensToday ?? 0) / 1000).toFixed(1)}k input /{' '}
            {((usage?.outputTokensToday ?? 0) / 1000).toFixed(1)}k output
          </p>
        </CardContent>
      </Card>
      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-sm font-medium">Est. Cost</CardTitle>
          <DollarSign size={16} className="text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-bold">
            ${usage?.estimatedCostUsd?.toFixed(4) ?? '0.00'}
          </div>
          <p className="text-xs text-muted-foreground">USD today</p>
        </CardContent>
      </Card>

      {/* Feature flags */}
      <Card className="md:col-span-2">
        <CardHeader><CardTitle>Active Features</CardTitle></CardHeader>
        <CardContent className="space-y-2">
          <div className="flex items-center gap-2">
            {config.ragEnabled
              ? <CheckCircle size={16} className="text-green-600" />
              : <XCircle size={16} className="text-red-500" />}
            <span>RAG Knowledge Base</span>
          </div>
          <div className="flex items-center gap-2">
            {config.toolsEnabled
              ? <CheckCircle size={16} className="text-green-600" />
              : <XCircle size={16} className="text-red-500" />}
            <span>Tool Calling</span>
          </div>
          <div className="flex items-center gap-2">
            {config.moderationEnabled
              ? <CheckCircle size={16} className="text-green-600" />
              : <XCircle size={16} className="text-red-500" />}
            <span>Content Moderation</span>
          </div>
        </CardContent>
      </Card>

      {/* Quick links */}
      <Card className="md:col-span-2">
        <CardHeader><CardTitle>Quick Actions</CardTitle></CardHeader>
        <CardContent className="space-y-2">
          <Button variant="outline" size="sm" className="w-full justify-between" asChild>
            <a href="#tools">Configure Tools <ChevronRight size={14} /></a>
          </Button>
          <Button variant="outline" size="sm" className="w-full justify-between" asChild>
            <a href="#prompts">Edit Prompts <ChevronRight size={14} /></a>
          </Button>
          <Button variant="outline" size="sm" className="w-full justify-between" asChild>
            <a href="#knowledge">Manage Knowledge <ChevronRight size={14} /></a>
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}

// ─────────────────────────────────────────────────────────
// Model Config Tab
// ─────────────────────────────────────────────────────────

const PROVIDERS = ['anthropic', 'openai', 'google-ai'];
const MODELS: Record<string, string[]> = {
  anthropic: ['claude-sonnet-4-5', 'claude-opus-4-5', 'claude-haiku-4-7'],
  openai: ['gpt-4o-mini', 'gpt-4o', 'gpt-4-turbo'],
  'google-ai': ['gemini-1.5-flash', 'gemini-1.5-pro', 'gemini-1.0-pro'],
};

function ModelConfigTab({
  config, onSave,
}: {
  config: AiConfig;
  onSave: (cfg: Partial<AiConfig>) => void;
}) {
  const [provider, setProvider] = useState(config.provider ?? 'anthropic');
  const [model, setModel] = useState(MODELS['anthropic'][0]);
  const [apiKey, setApiKey] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => { setProvider(config.provider ?? 'anthropic'); }, [config]);

  const onSaveModel = async () => {
    setSaving(true);
    try {
      await api.put('/api/v1/admin/ai/config', { ...config, provider, model });
      onSave({ ...config, provider, model });
      toast.success('Model configuration saved');
    } catch (e: any) {
      toast.error(e.message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>Model Configuration</CardTitle>
        <CardDescription>Choose the LLM provider and model for this tenant</CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        <div className="space-y-2">
          <Label>Provider</Label>
          <div className="flex gap-2">
            {PROVIDERS.map(p => (
              <Button
                key={p}
                variant={provider === p ? 'default' : 'outline'}
                onClick={() => { setProvider(p); setModel(MODELS[p][0]); }}
                size="sm"
              >
                {p}
              </Button>
            ))}
          </div>
        </div>

        <div className="space-y-2">
          <Label>Model</Label>
          <select
            className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
            value={model}
            onChange={e => setModel(e.target.value)}
          >
            {(MODELS[provider] ?? []).map(m => (
              <option key={m} value={m}>{m}</option>
            ))}
          </select>
        </div>

        <div className="space-y-2">
          <Label>API Key</Label>
          <Input
            type="password"
            placeholder="sk-... (stored securely, not displayed)"
            value={apiKey}
            onChange={e => setApiKey(e.target.value)}
          />
          <p className="text-xs text-muted-foreground">
            API keys are stored in the secret manager, not in this form.
          </p>
        </div>

        <Button onClick={onSaveModel} disabled={saving}>
          <Save size={14} className="mr-2" />
          {saving ? 'Saving...' : 'Save Configuration'}
        </Button>
      </CardContent>
    </Card>
  );
}

// ─────────────────────────────────────────────────────────
// Tools Tab
// ─────────────────────────────────────────────────────────

function ToolsTab({ tools, onUpdate }: {
  tools: ToolDef[];
  onUpdate: (name: string, perm: ToolDef['permission']) => void;
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Tool Permissions</CardTitle>
        <CardDescription>
          AUTO = executes without asking. APPROVAL = queues for review. DENIED = blocked.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <div className="space-y-3">
          {tools.map(tool => (
            <div key={tool.name} className="flex items-center justify-between rounded-lg border p-4">
              <div className="flex-1">
                <div className="font-medium">{tool.name}</div>
                <div className="text-sm text-muted-foreground">{tool.description}</div>
              </div>
              <div className="flex items-center gap-2">
                <PermBadge perm={tool.permission} />
                <div className="flex gap-1">
                  {(['AUTO', 'APPROVAL', 'DENIED'] as const).map(perm => (
                    <Button
                      key={perm}
                      size="sm"
                      variant={tool.permission === perm ? 'default' : 'outline'}
                      onClick={() => onUpdate(tool.name, perm)}
                    >
                      {perm}
                    </Button>
                  ))}
                </div>
              </div>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}

// ─────────────────────────────────────────────────────────
// Prompts Tab
// ─────────────────────────────────────────────────────────

const PROMPT_IDS = ['default', 'telco', 'insurance', 'billing', 'support', 'sales'];

function PromptsTab({ tenantId }: { tenantId: string }) {
  const [activeId, setActiveId] = useState('telco');
  const [content, setContent] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    api.get(`/api/v1/admin/ai/prompts/${activeId}`).then((r: any) => {
      setContent(r.data.content ?? '');
    }).catch(() => setContent(''));
  }, [activeId, tenantId]);

  const onSave = async () => {
    setSaving(true);
    try {
      await api.put(`/api/v1/admin/ai/prompts/${activeId}`, { content });
      toast.success(`Prompt "${activeId}" saved`);
    } catch (e: any) {
      toast.error(e.message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>Prompt Templates</CardTitle>
        <CardDescription>Customize system prompts per industry and use case. Use {'{{variable}}'} for substitution.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex flex-wrap gap-2">
          {PROMPT_IDS.map(id => (
            <Button
              key={id}
              size="sm"
              variant={activeId === id ? 'default' : 'outline'}
              onClick={() => setActiveId(id)}
            >
              {id}
            </Button>
          ))}
        </div>
        <Textarea
          className="font-mono text-sm min-h-[400px]"
          value={content}
          onChange={e => setContent(e.target.value)}
          placeholder="System prompt will appear here..."
        />
        <div className="flex items-center justify-between">
          <p className="text-xs text-muted-foreground">
            Variables: {'{{tenantName}}'}, {'{{userName}}'}, {'{{connectionId}}'}
          </p>
          <Button onClick={onSave} disabled={saving}>
            <Save size={14} className="mr-2" />
            {saving ? 'Saving...' : 'Save Prompt'}
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

// ─────────────────────────────────────────────────────────
// Knowledge Base Tab
// ─────────────────────────────────────────────────────────

function KnowledgeBaseTab({ tenantId }: { tenantId: string }) {
  const [chunks, setChunks] = useState<KnowledgeChunk[]>([]);
  const [search, setSearch] = useState('');
  const [searchResults, setSearchResults] = useState<KnowledgeChunk[]>([]);
  const [newText, setNewText] = useState('');
  const [newSource, setNewSource] = useState('');
  const [indexing, setIndexing] = useState(false);

  const onSearch = async () => {
    if (!search.trim()) return;
    try {
      const results = await api.get<{ data: KnowledgeChunk[] }>('/api/v1/admin/ai/knowledge', { params: { query: search } });
      setSearchResults(results.data ?? []);
    } catch (e: any) {
      toast.error(e.message);
    }
  };

  const onIndex = async () => {
    if (!newText.trim()) return;
    setIndexing(true);
    try {
      await api.post('/api/v1/admin/ai/knowledge', {
        text: newText,
        source: newSource || 'manual',
        tags: {},
      });
      toast.success('Chunk indexed successfully');
      setNewText('');
      setNewSource('');
    } catch (e: any) {
      toast.error(e.message);
    } finally {
      setIndexing(false);
    }
  };

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>RAG Knowledge Base</CardTitle>
          <CardDescription>
            Add knowledge chunks that the AI can use to ground responses in accurate information.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex gap-2">
            <Input
              placeholder="Search knowledge base..."
              value={search}
              onChange={e => setSearch(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && onSearch()}
            />
            <Button onClick={onSearch}><Search size={14} /></Button>
          </div>

          {searchResults.length > 0 && (
            <div className="space-y-2">
              <p className="text-sm font-medium">Search Results</p>
              {searchResults.map((chunk, i) => (
                <div key={chunk.chunkId ?? i} className="rounded-lg border p-3">
                  <div className="text-sm">{chunk.text}</div>
                  {chunk.similarity !== undefined && (
                    <div className="mt-1 text-xs text-muted-foreground">
                      Similarity: {(chunk.similarity * 100).toFixed(1)}%
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}

          <hr />

          <div className="space-y-2">
            <Label>Add New Knowledge Chunk</Label>
            <Textarea
              placeholder="Enter factual information the AI should know..."
              value={newText}
              onChange={e => setNewText(e.target.value)}
              className="min-h-[100px]"
            />
            <div className="flex gap-2">
              <Input
                placeholder="Source (e.g. support-faq, billing-policy)"
                value={newSource}
                onChange={e => setNewSource(e.target.value)}
              />
              <Button onClick={onIndex} disabled={indexing || !newText.trim()}>
                <Plus size={14} className="mr-1" />
                {indexing ? 'Indexing...' : 'Add Chunk'}
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

// ─────────────────────────────────────────────────────────
// Safety Tab
// ─────────────────────────────────────────────────────────

function SafetyTab({ config, onSave }: {
  config: AiConfig;
  onSave: (cfg: Partial<AiConfig>) => void;
}) {
  const [moderation, setModeration] = useState(config.moderationEnabled ?? true);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Safety & Moderation</CardTitle>
        <CardDescription>Configure content filtering and safety guardrails</CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <div className="font-medium">Content Moderation</div>
            <div className="text-sm text-muted-foreground">
              Use OpenAI Moderation API to filter harmful content
            </div>
          </div>
          <Button
            variant={moderation ? 'default' : 'outline'}
            onClick={async () => {
              setModeration(!moderation);
              await api.put('/api/v1/admin/ai/config', { ...config, moderationEnabled: !moderation });
              onSave({ ...config, moderationEnabled: !moderation });
            }}
          >
            {moderation ? 'Enabled' : 'Disabled'}
          </Button>
        </div>

        <div className="flex items-center gap-2 rounded-lg border border-yellow-200 bg-yellow-50 p-4">
          <AlertTriangle className="text-yellow-600" size={20} />
          <div>
            <div className="font-medium text-yellow-800">Moderation Categories</div>
            <div className="text-sm text-yellow-700">
              hate, harassment, violence, sexual, self-harm, and copyright are monitored.
              Contact OMOBIO support to configure additional categories.
            </div>
          </div>
        </div>

        <div className="flex items-center gap-2 rounded-lg border p-4">
          <Shield className="text-blue-500" size={20} />
          <div>
            <div className="font-medium">Prompt Injection Guardrails</div>
            <div className="text-sm text-muted-foreground">
              Automatic detection of jailbreak and instruction injection patterns is always active.
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

// ─────────────────────────────────────────────────────────
// Main AI Studio Page
// ─────────────────────────────────────────────────────────

export default function AIStudioPage() {
  const { activeTenantId } = useActiveTenant();

  const [config, setConfig] = useState<AiConfig>({
    provider: 'anthropic',
    moderationEnabled: true,
    ragEnabled: true,
    toolsEnabled: true,
    usage: {},
  });
  const [tools, setTools] = useState<ToolDef[]>([]);
  const [usage, setUsage] = useState<UsageMetrics | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [cfg, toolList, usageData] = await Promise.all([
        api.get<AiConfig>('/api/v1/admin/ai/config'),
        api.get<ToolDef[]>('/api/v1/admin/ai/tools'),
        api.get<UsageMetrics>('/api/v1/admin/ai/usage'),
      ]);
      setConfig(cfg ?? config);
      setTools(toolList ?? []);
      setUsage(usageData ?? null);
    } catch (e: any) {
      toast.error('Failed to load AI config: ' + e.message);
    } finally {
      setLoading(false);
    }
  }, [activeTenantId]);

  useEffect(() => { load(); }, [load]);

  const onUpdateTool = async (name: string, perm: ToolDef['permission']) => {
    try {
      await api.put(`/api/v1/admin/ai/tools/${name}`, { permission: perm });
      setTools(tools.map(t => t.name === name ? { ...t, permission: perm } : t));
      toast.success(`Tool "${name}" set to ${perm}`);
    } catch (e: any) {
      toast.error(e.message);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center p-12">
        <RefreshCw className="animate-spin text-muted-foreground" size={32} />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Page header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">AI Studio</h1>
          <p className="text-muted-foreground">
            Configure the AI assistant for <strong>{activeTenantId}</strong>
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={load}>
          <RefreshCw size={14} className="mr-2" />
          Refresh
        </Button>
      </div>

      <Tabs defaultValue="overview" className="space-y-4">
        <TabsList>
          <TabsTrigger value="overview"><Brain size={14} className="mr-1" />Overview</TabsTrigger>
          <TabsTrigger value="model"><Zap size={14} className="mr-1" />Model</TabsTrigger>
          <TabsTrigger value="tools"><BookOpen size={14} className="mr-1" />Tools</TabsTrigger>
          <TabsTrigger value="prompts"><Bot size={14} className="mr-1" />Prompts</TabsTrigger>
          <TabsTrigger value="knowledge"><BookOpen size={14} className="mr-1" />Knowledge</TabsTrigger>
          <TabsTrigger value="safety"><Shield size={14} className="mr-1" />Safety</TabsTrigger>
        </TabsList>

        <TabsContent value="overview">
          <OverviewTab config={config} usage={usage} />
        </TabsContent>

        <TabsContent value="model">
          <ModelConfigTab config={config} onSave={(cfg) => setConfig((prev) => ({ ...prev, ...cfg }))} />
        </TabsContent>

        <TabsContent value="tools" id="tools">
          <ToolsTab tools={tools} onUpdate={onUpdateTool} />
        </TabsContent>

        <TabsContent value="prompts" id="prompts">
          <PromptsTab tenantId={activeTenantId} />
        </TabsContent>

        <TabsContent value="knowledge" id="knowledge">
          <KnowledgeBaseTab tenantId={activeTenantId} />
        </TabsContent>

        <TabsContent value="safety">
          <SafetyTab config={config} onSave={(cfg) => setConfig((prev) => ({ ...prev, ...cfg }))} />
        </TabsContent>
      </Tabs>
    </div>
  );
}
