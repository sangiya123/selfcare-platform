/**
 * UsageCard — Displays usage summary (data, voice, SMS) for the billing period.
 *
 * Component ID: UsageCard
 * Layout variants: 'bar' | 'chip' | 'table'
 */

import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import type { WidgetProps } from '../ComponentRegistry';
import { tokens } from '../../styles/design-tokens';
import type { UsageSummary, DataUsage, VoiceUsage, SmsUsage } from '../../config/types';

interface Config {
  showVoice?: boolean;
  showSms?: boolean;
  showData?: boolean;
  layout?: 'bar' | 'chip' | 'table';
  periodLabel?: string;
}

function UsageBar({ used, total, label, unit, color }: {
  used: number; total: number; label: string; unit: string; color: string;
}) {
  const pct = total > 0 ? Math.min((used / total) * 100, 100) : 0;
  return (
    <View style={styles.barContainer}>
      <View style={styles.barLabelRow}>
        <Text style={styles.barLabel}>{label}</Text>
        <Text style={styles.barValue}>{formatUsage(used, unit)} / {formatUsage(total, unit)}</Text>
      </View>
      <View style={styles.barTrack}>
        <View style={[styles.barFill, { width: `${pct}%`, backgroundColor: color }]} />
      </View>
    </View>
  );
}

function formatBytes(bytes: number): string {
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

function formatSeconds(secs: number): string {
  const h = Math.floor(secs / 3600);
  const m = Math.floor((secs % 3600) / 60);
  if (h > 0) return `${h}h ${m}m`;
  return `${m}m`;
}

function formatUsage(quantity: number, unit: string): string {
  if (unit === 'bytes') return formatBytes(quantity);
  if (unit === 'seconds') return formatSeconds(quantity);
  return `${quantity}`;
}

export function UsageCard(props: WidgetProps): React.JSX.Element {
  const data = props.data as UsageSummary | undefined;
  const config = (props.props ?? {}) as Config;

  if (props.isLoading) return <UsageCardSkeleton />;
  if (props.error && !data) return <UsageError message={props.error} onRetry={props.onRetry} />;

  const { showData = true, showVoice = true, showSms = true, layout = 'bar', periodLabel } = config;
  const period = periodLabel ?? (data ? `${formatPeriod(data.periodStart)} - ${formatPeriod(data.periodEnd)}` : '');

  if (layout === 'chip') {
    return (
      <View style={styles.chipContainer}>
        {showData && data?.data && (
          <Chip icon="data" label="Data" value={formatBytes(data.data.remainingBytes)} color={tokens.colors.primary500} />
        )}
        {showVoice && data?.voice && (
          <Chip icon="voice" label="Voice" value={formatSeconds(data.voice.remainingSeconds)} color={tokens.colors.accent500} />
        )}
        {showSms && data?.sms && (
          <Chip icon="sms" label="SMS" value={String(data.sms.remainingCount)} color={tokens.colors.success} />
        )}
      </View>
    );
  }

  return (
    <View style={styles.card}>
      <Text style={styles.periodLabel}>{period}</Text>
      {showData && data?.data && (
        <UsageBar used={data.data.totalBytes} total={data.data.allowanceBytes}
          label="Data" unit="bytes" color={tokens.colors.primary500} />
      )}
      {showVoice && data?.voice && (
        <UsageBar used={data.voice.totalSeconds} total={data.voice.allowanceSeconds}
          label="Voice" unit="seconds" color={tokens.colors.accent500} />
      )}
      {showSms && data?.sms && (
        <UsageBar used={data.sms.totalCount} total={data.sms.allowanceCount}
          label="SMS" unit="count" color={tokens.colors.success} />
      )}
      <TouchableOpacity
        style={styles.detailsButton}
        onPress={() => props.onAction?.({ event: 'view_usage', type: 'NAVIGATE', route: '/usage' })}
      >
        <Text style={styles.detailsText}>View details</Text>
      </TouchableOpacity>
    </View>
  );
}

function Chip({ icon, label, value, color }: { icon: string; label: string; value: string; color: string }) {
  return (
    <View style={[styles.chip, { borderColor: color }]}>
      <Text style={[styles.chipLabel, { color }]}>{label}</Text>
      <Text style={styles.chipValue}>{value}</Text>
    </View>
  );
}

function formatPeriod(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

function UsageCardSkeleton(): React.JSX.Element {
  return (
    <View style={styles.card}>
      <View style={[styles.skeletonBlock, { width: '50%', marginBottom: 16 }]} />
      {[1, 2, 3].map((i) => (
        <View key={i} style={[styles.skeletonBlock, { marginBottom: 8 }]} />
      ))}
    </View>
  );
}

function UsageError({ message, onRetry }: { message: string; onRetry?: () => void }): React.JSX.Element {
  return (
    <View style={styles.errorContainer}>
      <Text style={styles.errorText}>{message}</Text>
      {onRetry && (
        <TouchableOpacity onPress={onRetry}><Text style={styles.retryText}>Retry</Text></TouchableOpacity>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  card: { backgroundColor: tokens.colors.surface, borderRadius: tokens.borderRadius.xl, padding: tokens.spacing.xl },
  periodLabel: { fontSize: tokens.fontSize.xs, color: tokens.colors.textSecondary, marginBottom: tokens.spacing.md },
  barContainer: { marginBottom: tokens.spacing.md },
  barLabelRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 4 },
  barLabel: { fontSize: tokens.fontSize.sm, fontWeight: tokens.fontWeight.medium, color: tokens.colors.textPrimary },
  barValue: { fontSize: tokens.fontSize.xs, color: tokens.colors.textSecondary },
  barTrack: { height: 6, backgroundColor: tokens.colors.border, borderRadius: 3, overflow: 'hidden' },
  barFill: { height: '100%', borderRadius: 3 },
  detailsButton: { marginTop: tokens.spacing.sm, alignSelf: 'flex-start' },
  detailsText: { fontSize: tokens.fontSize.sm, color: tokens.colors.primary500, fontWeight: tokens.fontWeight.semibold },
  chipContainer: { flexDirection: 'row', gap: tokens.spacing.sm },
  chip: { flex: 1, borderWidth: 1.5, borderRadius: tokens.borderRadius.lg, padding: tokens.spacing.md, alignItems: 'center' },
  chipLabel: { fontSize: tokens.fontSize.xs, fontWeight: tokens.fontWeight.semibold, marginBottom: 2 },
  chipValue: { fontSize: tokens.fontSize.base, fontWeight: tokens.fontWeight.bold, color: tokens.colors.textPrimary },
  errorContainer: { padding: tokens.spacing.lg, backgroundColor: '#FFF5F5', borderRadius: tokens.borderRadius.lg, alignItems: 'center' },
  errorText: { fontSize: tokens.fontSize.sm, color: tokens.colors.error, marginBottom: tokens.spacing.sm },
  retryText: { fontSize: tokens.fontSize.sm, color: tokens.colors.primary500, fontWeight: tokens.fontWeight.semibold },
  skeletonBlock: { height: 20, backgroundColor: '#E0E0E0', borderRadius: 4 },
});
