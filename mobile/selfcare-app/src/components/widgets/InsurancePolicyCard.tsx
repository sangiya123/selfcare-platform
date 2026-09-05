/**
 * InsurancePolicyCard — Displays a single insurance policy.
 *
 * Component ID: InsurancePolicyCard
 * Industry: INSURANCE (NOT in telco pack)
 */

import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import type { WidgetProps } from '../ComponentRegistry';
import { tokens } from '../../styles/design-tokens';

export interface InsurancePolicy {
  policyId: string;
  policyNumber: string;
  productName: string;
  productType: 'LIFE' | 'HEALTH' | 'MOTOR' | 'HOME' | 'CRITICAL_ILLNESS' | 'TRAVEL';
  policyHolderName: string;
  sumAssured: number;
  premiumAmount: number;
  premiumFrequency: 'MONTHLY' | 'QUARTERLY' | 'ANNUAL';
  currency: string;
  startDate: string;
  endDate: string;
  status: 'ACTIVE' | 'LAPSED' | 'MATURED' | 'PENDING';
  nextDueDate?: string;
}

interface Config {
  showPremium?: boolean;
  showSumAssured?: boolean;
  showPolicyNumber?: boolean;
  showBeneficiaries?: boolean;
  layout?: 'full' | 'compact';
}

const STATUS_COLORS: Record<InsurancePolicy['status'], string> = {
  ACTIVE: tokens.colors.success,
  LAPSED: tokens.colors.error,
  MATURED: tokens.colors.info,
  PENDING: tokens.colors.warning,
};

const TYPE_ICONS: Record<InsurancePolicy['productType'], string> = {
  LIFE: 'L',
  HEALTH: 'H',
  MOTOR: 'M',
  HOME: 'Hm',
  CRITICAL_ILLNESS: 'CI',
  TRAVEL: 'T',
};

export function InsurancePolicyCard(props: WidgetProps): React.JSX.Element {
  const policy = props.data as InsurancePolicy | undefined;
  const config = (props.props ?? {}) as Config;

  if (props.isLoading) return <PolicySkeleton />;
  if (props.error && !policy) {
    return (
      <View style={styles.errorContainer}>
        <Text style={styles.errorText}>{props.error}</Text>
        {props.onRetry && <TouchableOpacity onPress={props.onRetry}><Text style={styles.retryText}>Retry</Text></TouchableOpacity>}
      </View>
    );
  }

  if (!policy) return <View />;

  const statusColor = STATUS_COLORS[policy.status];
  const isCompact = config.layout === 'compact';

  return (
    <TouchableOpacity
      style={[styles.card, isCompact && styles.cardCompact]}
      activeOpacity={0.8}
      onPress={() => props.onAction?.({ event: 'view_policy', type: 'NAVIGATE', route: `/insurance/policies/${policy.policyId}` })}
    >
      <View style={styles.header}>
        <View style={[styles.typeIcon, { backgroundColor: `${statusColor}15` }]}>
          <Text style={[styles.typeIconText, { color: statusColor }]}>{TYPE_ICONS[policy.productType]}</Text>
        </View>
        <View style={styles.headerContent}>
          <Text style={styles.productName} numberOfLines={1}>{policy.productName}</Text>
          {config.showPolicyNumber !== false && (
            <Text style={styles.policyNumber}>#{policy.policyNumber}</Text>
          )}
        </View>
        <View style={[styles.statusBadge, { backgroundColor: `${statusColor}20` }]}>
          <Text style={[styles.statusText, { color: statusColor }]}>{policy.status}</Text>
        </View>
      </View>

      {!isCompact && (
        <View style={styles.detailsRow}>
          {config.showSumAssured !== false && (
            <DetailCell label="Sum Assured" value={`${policy.currency} ${formatNumber(policy.sumAssured)}`} />
          )}
          {config.showPremium !== false && (
            <DetailCell label={`Premium (${policy.premiumFrequency})`} value={`${policy.currency} ${formatNumber(policy.premiumAmount)}`} />
          )}
        </View>
      )}

      {policy.nextDueDate && policy.status === 'ACTIVE' && (
        <Text style={styles.nextDue}>Next premium: {new Date(policy.nextDueDate).toLocaleDateString()}</Text>
      )}
    </TouchableOpacity>
  );
}

function DetailCell({ label, value }: { label: string; value: string }): React.JSX.Element {
  return (
    <View style={styles.detailCell}>
      <Text style={styles.detailLabel}>{label}</Text>
      <Text style={styles.detailValue}>{value}</Text>
    </View>
  );
}

function formatNumber(n: number): string {
  return n.toLocaleString();
}

function PolicySkeleton(): React.JSX.Element {
  return (
    <View style={styles.card}>
      <View style={[styles.skel, { width: '60%', marginBottom: 12 }]} />
      <View style={[styles.skel, { width: '40%' }]} />
    </View>
  );
}

const styles = StyleSheet.create({
  card: { backgroundColor: tokens.colors.surface, borderRadius: tokens.borderRadius.xl, padding: tokens.spacing.lg, ...tokens.elevation.sm },
  cardCompact: { padding: tokens.spacing.md },
  header: { flexDirection: 'row', alignItems: 'center', gap: tokens.spacing.md, marginBottom: tokens.spacing.md },
  typeIcon: { width: 44, height: 44, borderRadius: 22, justifyContent: 'center', alignItems: 'center' },
  typeIconText: { fontSize: tokens.fontSize.sm, fontWeight: tokens.fontWeight.bold },
  headerContent: { flex: 1 },
  productName: { fontSize: tokens.fontSize.base, fontWeight: tokens.fontWeight.semibold, color: tokens.colors.textPrimary },
  policyNumber: { fontSize: tokens.fontSize.xs, color: tokens.colors.textSecondary, marginTop: 2 },
  statusBadge: { paddingHorizontal: 8, paddingVertical: 2, borderRadius: tokens.borderRadius.full },
  statusText: { fontSize: 10, fontWeight: tokens.fontWeight.bold, letterSpacing: 0.5 },
  detailsRow: { flexDirection: 'row', gap: tokens.spacing.lg, paddingTop: tokens.spacing.md, borderTopWidth: 1, borderTopColor: tokens.colors.border },
  detailCell: { flex: 1 },
  detailLabel: { fontSize: tokens.fontSize.xs, color: tokens.colors.textSecondary, marginBottom: 2 },
  detailValue: { fontSize: tokens.fontSize.base, fontWeight: tokens.fontWeight.semibold, color: tokens.colors.textPrimary },
  nextDue: { fontSize: tokens.fontSize.xs, color: tokens.colors.warning, marginTop: tokens.spacing.md, fontWeight: tokens.fontWeight.medium },
  errorContainer: { padding: tokens.spacing.lg, backgroundColor: '#FFF5F5', borderRadius: tokens.borderRadius.lg, alignItems: 'center' },
  errorText: { fontSize: tokens.fontSize.sm, color: tokens.colors.error, marginBottom: tokens.spacing.sm },
  retryText: { fontSize: tokens.fontSize.sm, color: tokens.colors.primary500, fontWeight: tokens.fontWeight.semibold },
  skel: { height: 16, backgroundColor: '#E0E0E0', borderRadius: 4 },
});
