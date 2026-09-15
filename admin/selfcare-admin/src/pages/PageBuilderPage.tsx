/**
 * PageBuilderPage — Drag-and-drop page builder for server-driven UI configuration.
 *
 * Three-pane layout:
 * - Left: Component palette (BalanceCard, UsageSummary, etc.)
 * - Center: Canvas (visual representation of the page)
 * - Right: Property inspector (component props, actions, visibility, analytics)
 *
 * Implements: WF-06 selfcare Studio Page Builder
 */

import React, { useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  DndContext,
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  DragEndEvent,
} from '@dnd-kit/core';
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { z } from 'zod';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Save, Eye, Send, GitMerge, AlertCircle } from 'lucide-react';
import { api } from '@/lib/api';
import { useActiveTenant } from '@/hooks/useActiveTenant';

const sectionSchema = z.object({
  id: z.string().min(1),
  component: z.string().min(1),
  variant: z.string().optional(),
  dataSource: z.string().optional(),
  props: z.record(z.unknown()).default({}),
  visibleWhen: z.object({
    feature: z.string().optional(),
    lob: z.string().optional(),
    segment: z.string().optional(),
  }).optional(),
  actions: z.array(z.object({
    event: z.string(),
    type: z.string(),
    route: z.string().optional(),
  })).default([]),
  states: z.record(z.string()).default({}),
  analytics: z.record(z.string()).default({}),
  order: z.number().default(0),
});

type SectionForm = z.infer<typeof sectionSchema>;

type PaletteEntry = { id: string; label: string; icon: string; category?: string };

/**
 * Normalise a backend catalog item into the palette shape used by the builder.
 * The palette is fully API/DB-driven (v6 rule #1) — labels, icons, categories
 * and availability are authored in the component catalog, never hardcoded here.
 */
function toPalette(item: any): PaletteEntry {
  return {
    id: item.componentId ?? item.id,
    label: item.label ?? item.componentId ?? item.id,
    icon: item.icon ?? '▢',
    category: item.category,
  };
}

export function PageBuilderPage() {
  const { experience = 'home' } = useParams<{ experience: string }>();
  const { activeTenantId } = useActiveTenant();
  const [sections, setSections] = useState<SectionForm[]>([]);
  const [selectedSection, setSelectedSection] = useState<string | null>(null);

  // Component palette — fetched from the admin component catalog (DB-backed).
  const { data: palette = [], isFetching: paletteLoading } = useQuery<PaletteEntry[]>({
    queryKey: ['components', activeTenantId],
    queryFn: () => api.components.list(activeTenantId || '').then((items: any[]) => items.map(toPalette)),
    enabled: !!activeTenantId,
  });

  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  // Load existing layout
  const { data: existing } = useQuery({
    queryKey: ['layout', experience],
    queryFn: () => api.get(`/api/v1/config/experiences/${experience}`).then((r: any) => r.data),
  });

  // Save draft
  const saveDraft = useMutation({
    mutationFn: (data: { sections: SectionForm[] }) =>
      api.put(`/api/v1/config/experiences/${experience}/draft`, data),
  });

  // Submit for approval
  const submitForApproval = useMutation({
    mutationFn: () =>
      api.post(`/api/v1/config/experiences/${experience}/submit`),
  });

  // Publish
  const publish = useMutation({
    mutationFn: () =>
      api.post(`/api/v1/config/experiences/${experience}/publish`),
  });

  // Rollback
  const rollback = useMutation({
    mutationFn: (toVersion: number) =>
      api.post(`/api/v1/config/experiences/${experience}/rollback`, { toVersion }),
  });

  function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event;
    if (over && active.id !== over.id) {
      setSections((items) => {
        const oldIndex = items.findIndex((s) => s.id === active.id);
        const newIndex = items.findIndex((s) => s.id === over.id);
        return arrayMove(items, oldIndex, newIndex);
      });
    }
  }

  function addComponent(componentId: string) {
    const newSection: SectionForm = {
      id: `section_${Date.now()}`,
      component: componentId,
      props: {},
      actions: [],
      states: {},
      analytics: {},
      order: sections.length,
    };
    setSections([...sections, newSection]);
    setSelectedSection(newSection.id);
  }

  function updateSection(id: string, updates: Partial<SectionForm>) {
    setSections(sections.map(s => s.id === id ? { ...s, ...updates } : s));
  }

  function removeSection(id: string) {
    setSections(sections.filter(s => s.id !== id));
    if (selectedSection === id) setSelectedSection(null);
  }

  function handleSave() {
    saveDraft.mutate({ sections });
  }

  function handlePreview() {
    // Open preview in new tab
    window.open(`/preview/${experience}?config=${encodeURIComponent(JSON.stringify({ sections }))}`, '_blank');
  }

  function handlePublish() {
    if (window.confirm('Publish this configuration to production?')) {
      publish.mutate();
    }
  }

  const selected = sections.find(s => s.id === selectedSection);

  return (
    <div className="flex h-screen bg-gray-50">
      {/* LEFT PANE — Component Palette */}
      <aside className="w-64 bg-white border-r overflow-y-auto p-4">
        <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide mb-3">Components</h2>
        <div className="space-y-2">
          {!activeTenantId && (
            <p className="text-xs text-gray-500">Select a client to load its component catalog.</p>
          )}
          {activeTenantId && paletteLoading && palette.length === 0 && (
            <p className="text-xs text-gray-500">Loading palette…</p>
          )}
          {activeTenantId && !paletteLoading && palette.length === 0 && (
            <p className="text-xs text-gray-500 flex items-start gap-1">
              <AlertCircle className="w-3.5 h-3.5 text-amber-500 mt-0.5" />
              No components published for this client yet — approve the catalog in Component Catalog admin.
            </p>
          )}
          {palette.map(c => (
            <button
              key={c.id}
              onClick={() => addComponent(c.id)}
              className="w-full text-left px-3 py-2 bg-white border border-gray-200 rounded-md hover:border-purple-400 hover:shadow-sm transition"
            >
              <span className="text-lg mr-2">{c.icon}</span>
              <span className="text-sm text-gray-800">{c.label}</span>
            </button>
          ))}
        </div>
      </aside>

      {/* CENTER PANE — Canvas */}
      <main className="flex-1 flex flex-col overflow-hidden">
        <header className="bg-white border-b px-6 py-3 flex items-center justify-between">
          <div>
            <h1 className="text-lg font-semibold text-gray-900">Page Builder — {experience}</h1>
            <p className="text-xs text-gray-500 mt-0.5">Configure the {experience} experience</p>
          </div>
          <div className="flex items-center space-x-2">
            <button
              onClick={handleSave}
              disabled={saveDraft.isPending}
              className="inline-flex items-center px-3 py-1.5 bg-white border border-gray-300 text-gray-700 rounded-md text-sm font-medium hover:bg-gray-50 disabled:opacity-50"
            >
              <Save className="w-4 h-4 mr-1.5" />
              Save Draft
            </button>
            <button
              onClick={handlePreview}
              className="inline-flex items-center px-3 py-1.5 bg-white border border-gray-300 text-gray-700 rounded-md text-sm font-medium hover:bg-gray-50"
            >
              <Eye className="w-4 h-4 mr-1.5" />
              Preview
            </button>
            <button
              onClick={() => submitForApproval.mutate()}
              disabled={submitForApproval.isPending}
              className="inline-flex items-center px-3 py-1.5 bg-white border border-gray-300 text-gray-700 rounded-md text-sm font-medium hover:bg-gray-50"
            >
              <Send className="w-4 h-4 mr-1.5" />
              Submit for Approval
            </button>
            <button
              onClick={handlePublish}
              disabled={publish.isPending}
              className="inline-flex items-center px-3 py-1.5 bg-purple-600 text-white rounded-md text-sm font-medium hover:bg-purple-700"
            >
              <GitMerge className="w-4 h-4 mr-1.5" />
              Publish
            </button>
          </div>
        </header>

        <div className="flex-1 overflow-y-auto p-6">
          {sections.length === 0 ? (
            <div className="text-center py-20">
              <p className="text-gray-500">No sections yet. Click a component from the left to add it.</p>
            </div>
          ) : (
            <DndContext
              sensors={sensors}
              collisionDetection={closestCenter}
              onDragEnd={handleDragEnd}
            >
              <SortableContext items={sections.map(s => s.id)} strategy={verticalListSortingStrategy}>
                <div className="max-w-md mx-auto space-y-3">
                  {sections.map((section) => (
                    <SectionPreview
                      key={section.id}
                      section={section}
                      palette={palette}
                      isSelected={selectedSection === section.id}
                      onClick={() => setSelectedSection(section.id)}
                      onRemove={() => removeSection(section.id)}
                    />
                  ))}
                </div>
              </SortableContext>
            </DndContext>
          )}
        </div>
      </main>

      {/* RIGHT PANE — Property Inspector */}
      <aside className="w-80 bg-white border-l overflow-y-auto p-4">
        {selected ? (
          <PropertyInspector
            section={selected}
            onChange={(updates) => updateSection(selected.id, updates)}
          />
        ) : (
          <div className="text-sm text-gray-500 mt-4">
            <p>Select a section to edit its properties.</p>
          </div>
        )}
      </aside>
    </div>
  );
}

export default PageBuilderPage;

function SectionPreview({
  section,
  palette,
  isSelected,
  onClick,
  onRemove,
}: {
  section: SectionForm;
  palette: PaletteEntry[];
  isSelected: boolean;
  onClick: () => void;
  onRemove: () => void;
}) {
  const paletteEntry = palette.find(p => p.id === section.component);
  return (
    <div
      onClick={onClick}
      className={`p-4 bg-white border-2 rounded-lg cursor-pointer transition ${
        isSelected ? 'border-purple-500 shadow-md' : 'border-gray-200 hover:border-gray-300'
      }`}
    >
      <div className="flex items-center justify-between">
        <div className="flex items-center">
          <span className="text-2xl mr-3">{paletteEntry?.icon || '▢'}</span>
          <div>
            <p className="text-sm font-medium text-gray-900">{paletteEntry?.label || section.component}</p>
            <p className="text-xs text-gray-500">id: {section.id}</p>
          </div>
        </div>
        <button
          onClick={(e) => { e.stopPropagation(); onRemove(); }}
          className="text-gray-400 hover:text-red-600 text-sm"
        >
          Remove
        </button>
      </div>
    </div>
  );
}

function PropertyInspector({
  section,
  onChange,
}: {
  section: SectionForm;
  onChange: (updates: Partial<SectionForm>) => void;
}) {
  return (
    <div className="space-y-4">
      <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide">Properties</h2>

      <div>
        <label className="block text-xs font-medium text-gray-700 mb-1">Section ID</label>
        <input
          type="text"
          value={section.id}
          onChange={(e) => onChange({ id: e.target.value })}
          className="w-full px-3 py-1.5 text-sm border border-gray-300 rounded-md"
        />
      </div>

      <div>
        <label className="block text-xs font-medium text-gray-700 mb-1">Component</label>
        <input
          type="text"
          value={section.component}
          onChange={(e) => onChange({ component: e.target.value })}
          className="w-full px-3 py-1.5 text-sm border border-gray-300 rounded-md bg-gray-50"
        />
      </div>

      <div>
        <label className="block text-xs font-medium text-gray-700 mb-1">Variant</label>
        <input
          type="text"
          value={section.variant || ''}
          onChange={(e) => onChange({ variant: e.target.value })}
          placeholder="e.g., hero, compact, expanded"
          className="w-full px-3 py-1.5 text-sm border border-gray-300 rounded-md"
        />
      </div>

      <div>
        <label className="block text-xs font-medium text-gray-700 mb-1">Data Source</label>
        <input
          type="text"
          value={section.dataSource || ''}
          onChange={(e) => onChange({ dataSource: e.target.value })}
          placeholder="e.g., balance.summary"
          className="w-full px-3 py-1.5 text-sm border border-gray-300 rounded-md"
        />
      </div>

      <div>
        <label className="block text-xs font-medium text-gray-700 mb-1">Props (JSON)</label>
        <textarea
          value={JSON.stringify(section.props, null, 2)}
          onChange={(e) => {
            try {
              onChange({ props: JSON.parse(e.target.value) });
            } catch {}
          }}
          rows={4}
          className="w-full px-3 py-1.5 text-xs font-mono border border-gray-300 rounded-md"
        />
      </div>

      <div>
        <label className="block text-xs font-medium text-gray-700 mb-1">Visibility</label>
        <input
          type="text"
          value={section.visibleWhen?.feature || ''}
          onChange={(e) => onChange({ visibleWhen: { ...section.visibleWhen, feature: e.target.value } })}
          placeholder="Feature flag name"
          className="w-full px-3 py-1.5 text-sm border border-gray-300 rounded-md"
        />
      </div>

      <div>
        <label className="block text-xs font-medium text-gray-700 mb-1">Analytics Event</label>
        <input
          type="text"
          value={section.analytics?.impression || ''}
          onChange={(e) => onChange({ analytics: { ...section.analytics, impression: e.target.value } })}
          placeholder="home_balance_impression"
          className="w-full px-3 py-1.5 text-sm border border-gray-300 rounded-md"
        />
      </div>
    </div>
  );
}
