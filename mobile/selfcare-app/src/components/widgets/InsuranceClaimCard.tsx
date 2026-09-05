/**
 * InsuranceClaimCard — Displays a single insurance claim.
 *
 * Component ID: InsuranceClaimCard
 * Industry: INSURANCE
 */

import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import type { WidgetProps } from '../ComponentRegistry';
import { tokens } from '../../styles/design-tokens';

export interface InsuranceClaim {
  claimId: string;
  claimNumber: string;
  policyId: string;
  policyNumber: string;
  claimType: 'HOSPITALIZATION' | 'DEATH' | 'MATURITY' | 'ACCIDENT' | 'THEFT' | 'DAMAGE' | 'OTHER';
  status: 'SUBMITTED' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED' | 'PAID' | 'CLOSED';
  amountClaimed: number;
  amountApproved?: number;
  currency: string;
  submittedAt: string;
  incidentDate: string;
  description: string;
}

interface Config {
  showAmount?: boolean;
  showStatusTimeline?: boolean;
  layout?: 'full' | 'compact';
}

const STATUS_COLORS: Record<InsuranceClaim['status'], string> = {
  SUBMITTED: tokens.colors.info,
  UNDER_REVIEW: tokens.colors.warning,
  APPROVED: tokens.colors.success,
  REJECTED: tokens.colors.error,
  PAID: tokens.colors.success,
  CLOSED: tokens.colors.textSecondary,
};

const TYPE_LABELS: Record<InsuranceClaim['claimType'], string> = {
  HOSPITALIZATION: 'Hospitalization',
  DEATH: 'Death Benefit',
  MATURITY: 'Maturity',
  ACCIDENT: 'Accident',
  THEFT: 'Theft',
  DAMAGE: 'Damage',
  OTHER: 'Other',
};

export function InsuranceClaimCard(props: WidgetProps): React.JSX.Element {
  const claim = props.data as InsuranceClaim | undefined;
  const config = (props.props ?? {}) as Config;

  if (props.isLoading) return <ClaimSkeleton />;
  if (props.error && !claim) {
    return (
      <View style={styles.errorContainer}>
        <Text style={styles.errorText}>{props.error}</Text>
        {props.onRetry && <TouchableOpacity onPress={props.onRetry}><Text style={styles.retryText}>Retry</Text></TouchableOpacity>}
      </View>
    );
  }

  if (!claim) return <View />;

  const statusColor = STATUS_COLORS[claim.status];
  const isCompact = config.layout === 'compact';

  return (
    <TouchableOpacity
      style={[styles.card, isCompact && styles.cardCompact]}
      activeOpacity={0.8}
      onPress={() => props.onAction?.({ event: 'view_claim', type: 'NAVIGATE', route: `/insurance/claims/${claim.claimId}` })}
    >
      <View style={styles.headerRow}>
        <View style={styles.headerLeft}>
          <Text style={styles.claimType}>{TYPE_LABELS[claim.claimType]}</Text>
          <Text style={styles.claimNumber}>Claim #{claim.claimNumber}</Text>
        </View>
        <View style={[styles.statusBadge, { backgroundColor: `${statusColor}20` }]}>
          <Text style={[styles.statusText, { color: statusColor }]}>{claim.status.replace('_', ' ')}</Text>
        </View>
      </View>

      {!isCompact && (
        <Text style={styles.description} numberOfLines={2}>{claim.description}</Text>
      )}

      {config.showAmount !== false && (
        <View style={styles.amountRow}>
          <View>
            <Text style={styles.amountLabel}>Amount Claimed</Text>
            <Text style={styles.amountValue}>{claim.currency} {claim.amountClaimed.toLocaleString()}</Text>
          </View>
          {claim.amountApproved !== undefined && (
            <View>
              <Text style={styles.amountLabel}>Approved</Text>
              <Text style={[styles.amountValue, { color: tokens.colors.success }]}>
                {claim.currency} {claim.amountApproved.toLocaleString()}
              </Text>
            </View>
          )}
        </View>
      )}

      <View style={styles.footer}>
        <Text style={styles.footerText}>
          Submitted {new Date(claim.submittedAt).toLocaleDateString()}
        </Text>
        <Text style={styles.footerText}>
          Incident {new Date(claim.incidentDate).toLocaleDateString()}
        </Text>
      </View>
    </TouchableOpacity>
  );
}

function ClaimSkeleton(): React.JSX.Element {
  return (
    <View style={styles.card}>
      <View style={[styles.skel, { width: '50%', marginBottom: 8 }]} />
      <View style={[styles.skel, { width: '80%', marginBottom: 12 }]} />
      <View style={[styles.skel, { width: '30%' }]} />
    </View>
  );
}

const styles = StyleSheet.create({
  card: { backgroundColor: tokens.colors.surface, borderRadius: tokens.borderRadius.xl, padding: tokens.spacing.lg, ...tokens.elevation.sm },
  cardCompact: { padding: tokens.spacing.md },
  headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: tokens.spacing.md },
  headerLeft: { flex: 1 },
  claimType: { fontSize: tokens.fontSize.base, fontWeight: tokens.fontWeight.semibold, color: tokens.colors.textPrimary },
  claimNumber: { fontSize: tokens.fontSize.xs, color: tokens.colors.textSecondary, marginTop: 2 },
  statusBadge: { paddingHorizontal: 8, paddingVertical: 2, borderRadius: tokens.borderRadius.full },
  statusText: { fontSize: 10, fontWeight: tokens.fontWeight.bold, letterSpacing: 0.5 },
  description: { fontSize: tokens.fontSize.sm, color: tokens.colors.textSecondary, marginBottom: tokens.spacing.md, lineHeight: 18 },
  amountRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: tokens.spacing.md, paddingTop: tokens.spacing.md, borderTopWidth: 1, borderTopColor: tokens.colors.border },
  amountLabel: { fontSize: tokens.fontSize.xs, color: tokens.colors.textSecondary, marginBottom: 2 },
  amountValue: { fontSize: tokens.fontSize.base, fontWeight: tokens.fontWeight.bold, color: tokens.colors.textPrimary },
  footer: { flexDirection: 'row', justifyContent: 'space-between' },
  footerText: { fontSize: tokens.fontSize.xs, color: tokens.colors.textSecondary },
  errorContainer: { padding: tokens.spacing.lg, backgroundColor: '#FFF5F5', borderRadius: tokens.borderRadius.lg, alignItems: 'center' },
  errorText: { fontSize: tokens.fontSize.sm, color: tokens.colors.error, marginBottom: tokens.spacing.sm },
  retryText: { fontSize: tokens.fontSize.sm, color: tokens.colors.primary500, fontWeight: tokens.fontWeight.semibold },
  skel: { height: 14, backgroundColor: '#E0E0E0', borderRadius: 4 },
});
