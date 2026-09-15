/**
 * selfcare kernel (web) — default preview widgets.
 *
 * Minimal DOM implementations of the palette used by the selfcare Studio page
 * builder. These exist so a preview renders the same manifest the mobile app
 * renders; they intentionally stay small (look-and-feel lives in tenant themes).
 */

import React from 'react';
import {
  ComponentRegistry,
  RegisteredComponent,
} from './ComponentRegistry';
import { WebWidgetProps } from './types';

function BalanceCard(props: WebWidgetProps): JSX.Element {
  const data = (props.data as { balance?: string; currency?: string } | undefined) ?? {};
  const label = (props.props?.label as string | undefined) ?? 'Balance';
  return (
    <Card onClick={() => emit(props, '/bills')}>
      <div className="text-sm text-gray-500">{label}</div>
      <div className="text-2xl font-bold text-gray-900">
        {data.currency ?? 'LKR'} {data.balance ?? '0.00'}
      </div>
    </Card>
  );
}

function UsageSummary(props: WebWidgetProps): JSX.Element {
  const data = (props.data as { used?: string; total?: string; unit?: string } | undefined) ?? {};
  const pct = data.total ? Math.min(100, Math.round((Number(data.used ?? 0) / Number(data.total)) * 100)) : 0;
  return (
    <Card>
      <div className="text-sm text-gray-500">Data Usage</div>
      <div className="text-2xl font-bold text-gray-900">
        {data.used ?? '0'} {data.unit ?? 'MB'} / {data.total ?? '0'} {data.unit ?? 'MB'}
      </div>
      <div className="mt-2 h-2 rounded-full bg-gray-200">
        <div className="h-2 rounded-full bg-purple-600" style={{ width: `${pct}%` }} />
      </div>
    </Card>
  );
}

function BillCard(props: WebWidgetProps): JSX.Element {
  const data = (props.data as { latest?: { period?: string; amount?: string } } | undefined) ?? {};
  const bill = data.latest ?? {};
  return (
    <Card onClick={() => emit(props, '/bills')}>
      <div className="text-sm text-gray-500">Latest Bill — {bill.period ?? '-'}</div>
      <div className="text-2xl font-bold text-gray-900">{bill.amount ?? 'LKR 0.00'}</div>
    </Card>
  );
}

function QuickActions(props: WebWidgetProps): JSX.Element {
  const actions = (props.props?.actions as Array<{ id: string; label: string; route?: string }> | undefined) ?? [];
  return (
    <Card>
      <div className="grid grid-cols-4 gap-2">
        {actions.map((action) => (
          <button
            key={action.id}
            type="button"
            onClick={() => action.route && emit(props, action.route)}
            className="flex flex-col items-center gap-1 rounded-md bg-gray-50 px-2 py-3 text-xs text-gray-700 hover:bg-gray-100"
          >
            <span className="text-lg">{action.label.slice(0, 1)}</span>
            <span>{action.label}</span>
          </button>
        ))}
      </div>
    </Card>
  );
}

function Banner(props: WebWidgetProps): JSX.Element {
  const data = (props.data as { title?: string; subtitle?: string; imageUrl?: string } | undefined) ?? {};
  return (
    <Card className="!bg-gradient-to-r !from-purple-700 !to-indigo-700 !text-white">
      <div className="text-lg font-bold">{data.title ?? 'Top-up now'}</div>
      <div className="text-sm opacity-90">{data.subtitle ?? 'Get bonus data on every recharge.'}</div>
    </Card>
  );
}

function AIAssistantEntry(props: WebWidgetProps): JSX.Element {
  return (
    <Card onClick={() => emit(props, '/ai')} className="!border-purple-200 !bg-purple-50">
      <div className="flex items-center gap-3">
        <span className="flex h-9 w-9 items-center justify-center rounded-full bg-purple-600 text-white">AI</span>
        <div>
          <div className="text-sm font-medium text-gray-900">Ask selfcare Assistant</div>
          <div className="text-xs text-gray-500">Get instant help with your account</div>
        </div>
      </div>
    </Card>
  );
}

function Card({
  children,
  onClick,
  className = '',
}: {
  children: React.ReactNode;
  onClick?: () => void;
  className?: string;
}): JSX.Element {
  return (
    <div
      onClick={onClick}
      className={`rounded-lg border border-gray-200 bg-white p-4 shadow-sm ${onClick ? 'cursor-pointer' : ''} ${className}`}
    >
      {children}
    </div>
  );
}

function emit(props: WebWidgetProps, route: string): void {
  props.onAction?.({
    event: 'tap',
    type: 'NAVIGATE',
    route,
  });
}

const DEFAULT_WIDGETS: RegisteredComponent[] = [
  {
    componentId: 'BalanceCard',
    component: BalanceCard,
    security: 'read',
    analyticsEvents: ['balance_view'],
  },
  {
    componentId: 'UsageSummary',
    component: UsageSummary,
    security: 'read',
    analyticsEvents: ['usage_view'],
  },
  {
    componentId: 'UsageChart',
    component: UsageSummary,
    security: 'read',
    analyticsEvents: ['usage_view'],
  },
  {
    componentId: 'BillCard',
    component: BillCard,
    security: 'read',
    analyticsEvents: ['bill_view'],
  },
  {
    componentId: 'QuickActions',
    component: QuickActions,
    security: 'display',
    analyticsEvents: [],
  },
  {
    componentId: 'Banner',
    component: Banner,
    security: 'display',
    analyticsEvents: ['banner_view'],
  },
  {
    componentId: 'AIAssistantEntry',
    component: AIAssistantEntry,
    security: 'read',
    analyticsEvents: ['ai_entry_view'],
  },
];

/** Build a registry prepopulated with the default preview widget set. */
export function createDefaultWidgetRegistry(): ComponentRegistry {
  const registry = new ComponentRegistry();
  DEFAULT_WIDGETS.forEach((entry, index) => {
    registry.register(entry.componentId, entry.component, {
      description: entry.description,
      platforms: entry.platforms,
      minAppVersion: entry.minAppVersion,
      security: entry.security,
      analyticsEvents: entry.analyticsEvents,
    });
    if (index === 0) registry.registerAlias('Balance', 'BalanceCard');
    if (index === 1) registry.registerAlias('Usage', 'UsageSummary');
  });
  return registry;
}

export default createDefaultWidgetRegistry;