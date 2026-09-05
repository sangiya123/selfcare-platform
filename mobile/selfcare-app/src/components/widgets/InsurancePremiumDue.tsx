/**
 * InsurancePremiumDue — Shows upcoming or overdue premium payment.
 *
 * Component ID: InsurancePremiumDue
 * Industry: INSURANCE
 */

import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import type { WidgetProps } from '../ComponentRegistry';
import { tokens } from '../../styles/design-tokens';

export interface InsurancePremiumDue {
  premiumId: string;
  policyId: string;
  policyNumber: string;
  amount: number;
  currency: string;
  dueDate: string;
  isOverdue: boolean;
  daysUntilDue: number;
  paymentMethods: ('CARD' | 'BANK_TRANSFER' | 'WALLET')[];
}

interface Config {
  showPayButton?: boolean;
  showPolicyNumber?: boolean;
  warningThresholdDays?: number;
}

export function InsurancePremiumDue(props: WidgetProps): React.JSX.Element {
  const premium = props.data as InsurancePremiumDue | undefined;
  const config = (props.props ?? {}) as Config;

  if (props.isLoading) return <PremiumSkeleton />;
  if (props.error && !premium) {
    return (
      <View style={styles.errorContainer}>
        <Text style={styles.errorText}>{props.error}</Text>
        {props.onRetry && <TouchableOpacity onPress={props.onRetry}><Text style={styles.retryText}>Retry</Text></TouchableOpacity>}
      </View>
    );
  }

  if (!premium) return <View />;

  const warningThreshold = config.warningThresholdDays ?? 7;
  const isUrgent = premium.daysUntilDue <= warningThreshold;

  const bgColor = premium.isOverdue
    ? '#FEF2F2'
    : isUrgent
      ? '#FFFBEB'
      : '#F0FDF4';

  const borderColor = premium.isOverdue
    ? tokens.colors.error
    : isUrgent
      ? tokens.colors.warning
      : tokens.colors.success;

  return (
    <View style={[styles.card, { backgroundColor: bgColor, borderColor, borderWidth: 1 }]}>
      <View style={styles.headerRow}>
        <View style={styles.headerLeft}>
          <Text style={styles.label}>
            {premium.isOverdue ? 'Payment Overdue' : isUrgent ? 'Payment Due Soon' : 'Premium Payment'}
          </Text>
          {config.showPolicyNumber !== false && (
            <Text style={styles.policyNumber}>Policy #{premium.policyNumber}</Text>
          )}
        </View>
        <View style={styles.amountContainer}>
          <Text style={styles.amount}>{premium.currency} {premium.amount.toLocaleString()}</Text>
        </View>
      </View>

      <View style={styles.dueRow}>
        <Text style={styles.dueLabel}>
          {premium.isOverdue
            ? `${Math.abs(premium.daysUntilDue)} day${Math.abs(premium.daysUntilDue) !== 1 ? 's' : ''} overdue`
            : premium.daysUntilDue === 0
              ? 'Due today'
              : `Due in ${premium.daysUntilDue} day${premium.daysUntilDue !== 1 ? 's' : ''}`
          }
        </Text>
        <Text style={styles.dueDate}>{new Date(premium.dueDate).toLocaleDateString()}</Text>
      </View>

      {config.showPayButton !== false && (
        <TouchableOpacity
          style={[styles.payButton, { backgroundColor: borderColor }]}
          onPress={() => props.onAction?.({
            event: 'pay_premium', type: 'PAYMENT',
            params: {
              premiumId: premium.premiumId,
              amount: premium.amount,
              policyId: premium.policyId,
            }
          })}
        >
          <Text style={styles.payButtonText}>Pay Now</Text>
        </TouchableOpacity>
      )}
    </View>
  );
}

function PremiumSkeleton(): React.JSX.Element {
  return (
    <View style={styles.card}>
      <View style={[styles.skel, { width: '50%', marginBottom: 8 }]} />
      <View style={[styles.skel, { width: '30%' }]} />
    </View>
  );
}

const styles = StyleSheet.create({
  card: { borderRadius: tokens.borderRadius.xl, padding: tokens.spacing.lg, ...tokens.elevation.sm },
  headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: tokens.spacing.sm },
  headerLeft: { flex: 1 },
  label: { fontSize: tokens.fontSize.sm, fontWeight: tokens.fontWeight.semibold, color: tokens.colors.textPrimary },
  policyNumber: { fontSize: tokens.fontSize.xs, color: tokens.colors.textSecondary, marginTop: 2 },
  amountContainer: {},
  amount: { fontSize: tokens.fontSize['2xl'], fontWeight: tokens.fontWeight.bold, color: tokens.colors.textPrimary },
  dueRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: tokens.spacing.md },
  dueLabel: { fontSize: tokens.fontSize.sm, color: tokens.colors.textSecondary, fontWeight: tokens.fontWeight.medium },
  dueDate: { fontSize: tokens.fontSize.xs, color: tokens.colors.textSecondary },
  payButton: { paddingVertical: tokens.spacing.md, borderRadius: tokens.borderRadius.full, alignItems: 'center' },
  payButtonText: { color: tokens.colors.surface, fontSize: tokens.fontSize.base, fontWeight: tokens.fontWeight.semibold },
  errorContainer: { padding: tokens.spacing.lg, backgroundColor: '#FFF5F5', borderRadius: tokens.borderRadius.lg, alignItems: 'center' },
  errorText: { fontSize: tokens.fontSize.sm, color: tokens.colors.error, marginBottom: tokens.spacing.sm },
  retryText: { fontSize: tokens.fontSize.sm, color: tokens.colors.primary500, fontWeight: tokens.fontWeight.semibold },
  skel: { height: 18, backgroundColor: '#E0E0E0', borderRadius: 4 },
});
