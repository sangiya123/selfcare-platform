/**
 * QuickActionsGrid — Grid of quick-access tiles.
 *
 * Component ID: QuickActionsGrid
 * Layout: 2-4 column grid
 */

import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import type { WidgetProps } from '../ComponentRegistry';
import { tokens } from '../../styles/design-tokens';

interface QuickAction {
  id: string;
  label: string;
  icon?: string;
  backgroundColor?: string;
  action: { type: string; route?: string; journeyId?: string };
}

interface Config {
  columns?: number;
  showLabels?: boolean;
  tileSize?: 'small' | 'medium' | 'large';
}

export function QuickActionsGrid(props: WidgetProps): React.JSX.Element {
  const config = (props.props ?? {}) as Config;
  const actions = (props.data as QuickAction[] | undefined) ?? [];
  const cols = config.columns ?? 4;
  const tileSize = config.tileSize ?? 'medium';

  if (props.isLoading) return <GridSkeleton columns={cols} rows={2} />;

  if (!actions.length) return <View />;

  return (
    <View style={styles.grid}>
      {actions.map((action) => (
        <TouchableOpacity
          key={action.id}
          style={[styles.tile, tileStyle(tileSize)]}
          onPress={() => props.onAction?.({ event: 'quick_action', ...action.action })}
          activeOpacity={0.7}
        >
          <View style={[styles.iconCircle, { backgroundColor: action.backgroundColor ?? tokens.colors.surfaceSubtle }]}>
            <Text style={styles.iconText}>{action.icon ?? action.label.charAt(0)}</Text>
          </View>
          {config.showLabels !== false && (
            <Text style={styles.label} numberOfLines={2}>{action.label}</Text>
          )}
        </TouchableOpacity>
      ))}
    </View>
  );
}

function tileStyle(size: 'small' | 'medium' | 'large') {
  if (size === 'small') return { padding: tokens.spacing.sm };
  if (size === 'large') return { padding: tokens.spacing.lg };
  return { padding: tokens.spacing.md };
}

function GridSkeleton({ columns, rows }: { columns: number; rows: number }): React.JSX.Element {
  return (
    <View style={styles.grid}>
      {Array.from({ length: columns * rows }).map((_, i) => (
        <View key={i} style={[styles.tile, { opacity: 0.4 }]}>
          <View style={[styles.skelIcon]} />
          <View style={[styles.skelLabel, { width: '60%' }]} />
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  grid: { flexDirection: 'row', flexWrap: 'wrap' },
  tile: { width: '25%', alignItems: 'center' },
  iconCircle: { width: 48, height: 48, borderRadius: 24, justifyContent: 'center', alignItems: 'center', marginBottom: tokens.spacing.xs },
  iconText: { fontSize: tokens.fontSize.lg, fontWeight: tokens.fontWeight.bold, color: tokens.colors.primary500 },
  label: { fontSize: tokens.fontSize.xs, color: tokens.colors.textPrimary, textAlign: 'center' },
  skelIcon: { width: 48, height: 48, borderRadius: 24, backgroundColor: '#E0E0E0', marginBottom: tokens.spacing.xs },
  skelLabel: { height: 12, backgroundColor: '#E0E0E0', borderRadius: 4 },
});
