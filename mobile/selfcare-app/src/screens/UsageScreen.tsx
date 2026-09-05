/**
 * UsageScreen — data/voice/SMS usage for the current billing period.
 */
import React, { useEffect, useState, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ActivityIndicator,
  ScrollView,
  RefreshControl,
} from 'react-native';
import { useApi } from '../hooks/useApi';
import { useTenant } from '../hooks/useTenant';
import { tokens } from '../styles/design-tokens';

interface UsageData {
  data: { used: number; total: number; isUnlimited: boolean };
  voice: { used: number; total: number; isUnlimited: boolean };
  sms: { used: number; total: number; isUnlimited: boolean };
  periodEnd: string;
}

export function UsageScreen(): React.JSX.Element {
  const api = useApi();
  const { tenantId: activeTenant } = useTenant();
  const [usage, setUsage] = useState<UsageData | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    try {
      const data = await api.usage.getCurrent({ tenantId: activeTenant });
      setUsage(data);
    } catch (err) {
      setUsage(null);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [api, activeTenant]);

  useEffect(() => {
    load();
  }, [load]);

  if (loading) {
    return (
      <View style={styles.centered}>
        <ActivityIndicator color={tokens.colors.primary500} />
      </View>
    );
  }

  if (!usage) {
    return (
      <View style={styles.centered}>
        <Text style={styles.emptyTitle}>Usage unavailable</Text>
        <Text style={styles.emptyBody}>Try again in a moment.</Text>
      </View>
    );
  }

  return (
    <ScrollView
      style={styles.root}
      contentContainerStyle={styles.content}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); load(); }} />}
    >
      <Text style={styles.period}>
        Period ends {new Date(usage.periodEnd).toLocaleDateString()}
      </Text>

      <UsageRow
        title="Data"
        usedMb={usage.data.used / 1024 / 1024}
        totalMb={usage.data.total / 1024 / 1024}
        unit="MB"
        isUnlimited={usage.data.isUnlimited}
      />
      <UsageRow
        title="Voice"
        usedMin={usage.voice.used / 60}
        totalMin={usage.voice.total / 60}
        unit="min"
        isUnlimited={usage.voice.isUnlimited}
      />
      <UsageRow
        title="SMS"
        usedCount={usage.sms.used}
        totalCount={usage.sms.total}
        isUnlimited={usage.sms.isUnlimited}
      />
    </ScrollView>
  );
}

interface UsageRowProps {
  title: string;
  isUnlimited: boolean;
  usedMb?: number;
  totalMb?: number;
  usedMin?: number;
  totalMin?: number;
  usedCount?: number;
  totalCount?: number;
  unit?: string;
}

function UsageRow({
  title,
  usedMb,
  totalMb,
  usedMin,
  totalMin,
  usedCount,
  totalCount,
  isUnlimited,
}: UsageRowProps): React.JSX.Element {
  const used = usedMb ?? usedMin ?? usedCount ?? 0;
  const total = totalMb ?? totalMin ?? totalCount ?? 0;
  const pct = isUnlimited || total === 0 ? 0 : Math.min(100, (used / total) * 100);
  const unit = usedMb !== undefined ? 'MB' : usedMin !== undefined ? 'min' : 'SMS';
  const usedStr = unit === 'MB' ? used.toFixed(0) : used.toFixed(0);
  const totalStr = unit === 'MB' ? total.toFixed(0) : total.toFixed(0);

  return (
    <View style={styles.card}>
      <View style={styles.cardHeader}>
        <Text style={styles.title}>{title}</Text>
        <Text style={styles.usedText}>
          {usedStr} / {isUnlimited ? '∞' : totalStr} {unit}
        </Text>
      </View>
      <View style={styles.bar}>
        <View style={[styles.barFill, { width: `${pct}%` }]} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: tokens.colors.surfaceSubtle },
  content: { padding: 16 },
  centered: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  emptyTitle: { fontSize: 16, fontWeight: '600', color: tokens.colors.textPrimary },
  emptyBody: { fontSize: 13, color: tokens.colors.textSecondary, marginTop: 4 },
  period: { fontSize: 12, color: tokens.colors.textSecondary, marginBottom: 12 },
  card: {
    backgroundColor: tokens.colors.surface,
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
  },
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline' },
  title: { fontSize: 16, fontWeight: '600', color: tokens.colors.textPrimary },
  usedText: { fontSize: 13, color: tokens.colors.textSecondary },
  bar: {
    height: 8,
    backgroundColor: tokens.colors.surfaceSubtle,
    borderRadius: 4,
    marginTop: 10,
    overflow: 'hidden',
  },
  barFill: { height: '100%', backgroundColor: tokens.colors.primary500, borderRadius: 4 },
});
