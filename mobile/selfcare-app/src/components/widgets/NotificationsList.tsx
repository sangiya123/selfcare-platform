/**
 * NotificationsList — Shows the user's recent notifications.
 *
 * Component ID: NotificationsList
 */

import React, { useState } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, FlatList } from 'react-native';
import type { WidgetProps } from '../ComponentRegistry';
import { tokens } from '../../styles/design-tokens';

interface Notification {
  id: string;
  title: string;
  body: string;
  type: 'INFO' | 'PROMO' | 'ALERT' | 'TRANSACTIONAL';
  isRead: boolean;
  createdAt: string;
  imageUrl?: string;
  ctaText?: string;
  ctaAction?: { type: string; route?: string };
}

interface Config {
  maxItems?: number;
  showUnreadOnly?: boolean;
  showTypeIcon?: boolean;
  groupByType?: boolean;
}

const TYPE_COLORS: Record<Notification['type'], string> = {
  INFO: tokens.colors.info,
  PROMO: tokens.colors.accent500,
  ALERT: tokens.colors.error,
  TRANSACTIONAL: tokens.colors.success,
};

export function NotificationsList(props: WidgetProps): React.JSX.Element {
  const config = (props.props ?? {}) as Config;
  const raw = (props.data as Notification[] | undefined) ?? [];
  const items = config.showUnreadOnly ? raw.filter((n) => !n.isRead) : raw;
  const [expanded, setExpanded] = useState(false);

  const maxItems = config.maxItems ?? 5;
  const display = expanded ? items : items.slice(0, maxItems);

  if (props.isLoading) return <NotifSkeleton count={3} />;

  if (!items.length) {
    return (
      <View style={styles.empty}>
        <Text style={styles.emptyTitle}>All caught up</Text>
        <Text style={styles.emptyText}>No notifications yet</Text>
      </View>
    );
  }

  return (
    <View>
      <FlatList
        data={display}
        keyExtractor={(n) => n.id}
        scrollEnabled={false}
        renderItem={({ item }) => (
          <NotifItem
            item={item}
            showTypeIcon={config.showTypeIcon}
            onAction={props.onAction}
          />
        )}
        ItemSeparatorComponent={() => <View style={{ height: tokens.spacing.sm }} />}
      />
      {items.length > maxItems && (
        <TouchableOpacity onPress={() => setExpanded((e) => !e)} style={styles.expand}>
          <Text style={styles.expandText}>{expanded ? 'Show less' : `Show ${items.length - maxItems} more`}</Text>
        </TouchableOpacity>
      )}
    </View>
  );
}

function NotifItem({ item, showTypeIcon, onAction }: { item: Notification; showTypeIcon?: boolean; onAction?: WidgetProps['onAction'] }): React.JSX.Element {
  const accent = TYPE_COLORS[item.type];
  const timeAgo = formatTimeAgo(item.createdAt);

  return (
    <TouchableOpacity
      style={[styles.item, !item.isRead && styles.unread]}
      activeOpacity={0.7}
      onPress={() => onAction?.({ event: 'open_notification', type: 'NAVIGATE', route: `/notifications/${item.id}` })}
    >
      {showTypeIcon !== false && <View style={[styles.iconDot, { backgroundColor: accent }]} />}
      <View style={styles.itemContent}>
        <View style={styles.itemHeader}>
          <Text style={styles.title} numberOfLines={1}>{item.title}</Text>
          <Text style={styles.time}>{timeAgo}</Text>
        </View>
        <Text style={styles.body} numberOfLines={2}>{item.body}</Text>
        {item.ctaText && (
          <TouchableOpacity
            onPress={() => item.ctaAction && onAction?.(item.ctaAction as any)}
            style={styles.ctaButton}
          >
            <Text style={styles.ctaText}>{item.ctaText}</Text>
          </TouchableOpacity>
        )}
      </View>
      {!item.isRead && <View style={styles.unreadDot} />}
    </TouchableOpacity>
  );
}

function formatTimeAgo(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime();
  const minutes = Math.floor(diffMs / 60000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}d ago`;
  return new Date(iso).toLocaleDateString();
}

function NotifSkeleton({ count }: { count: number }): React.JSX.Element {
  return (
    <View>
      {Array.from({ length: count }).map((_, i) => (
        <View key={i} style={styles.item}>
          <View style={[styles.skel, { width: '40%', marginBottom: 8 }]} />
          <View style={[styles.skel, { width: '80%' }]} />
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  item: { flexDirection: 'row', backgroundColor: tokens.colors.surface, borderRadius: tokens.borderRadius.lg, padding: tokens.spacing.md, gap: tokens.spacing.md, ...tokens.elevation.sm },
  unread: { backgroundColor: '#F0F4FF' },
  iconDot: { width: 4, height: '100%', minHeight: 40, borderRadius: 2 },
  itemContent: { flex: 1 },
  itemHeader: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 2 },
  title: { flex: 1, fontSize: tokens.fontSize.sm, fontWeight: tokens.fontWeight.semibold, color: tokens.colors.textPrimary },
  time: { fontSize: tokens.fontSize.xs, color: tokens.colors.textSecondary, marginLeft: tokens.spacing.sm },
  body: { fontSize: tokens.fontSize.sm, color: tokens.colors.textSecondary, lineHeight: 18 },
  ctaButton: { alignSelf: 'flex-start', marginTop: tokens.spacing.sm, paddingVertical: 2 },
  ctaText: { fontSize: tokens.fontSize.sm, color: tokens.colors.primary500, fontWeight: tokens.fontWeight.semibold },
  unreadDot: { width: 8, height: 8, borderRadius: 4, backgroundColor: tokens.colors.primary500, alignSelf: 'center' },
  empty: { alignItems: 'center', padding: tokens.spacing['2xl'] },
  emptyTitle: { fontSize: tokens.fontSize.base, fontWeight: tokens.fontWeight.semibold, color: tokens.colors.textPrimary, marginBottom: 4 },
  emptyText: { fontSize: tokens.fontSize.sm, color: tokens.colors.textSecondary },
  expand: { alignItems: 'center', padding: tokens.spacing.md },
  expandText: { fontSize: tokens.fontSize.sm, color: tokens.colors.primary500, fontWeight: tokens.fontWeight.medium },
  skel: { height: 14, backgroundColor: '#E0E0E0', borderRadius: 4 },
});
