/**
 * BillCard — Displays bill summary and payment status.
 *
 * Component ID: BillCard
 * Used for: postpaid bill overview
 */

import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import type { WidgetProps } from '../ComponentRegistry';
import { tokens } from '../../styles/design-tokens';
import type { Bill } from '../../config/types';

interface Config {
  showBillNumber?: boolean;
  showStatusBadge?: boolean;
  showPayButton?: boolean;
  layout?: 'full' | 'compact';
}

const STATUS_COLORS: Record<string, string> = {
  PAID: tokens.colors.success,
  DUE: tokens.colors.warning,
  OVERDUE: tokens.colors.error,
  PENDING: tokens.colors.info,
};

export function BillCard(props: WidgetProps): React.JSX.Element {
  const bill = props.data as Bill | undefined;
  const config = (props.props ?? {}) as Config;

  if (props.isLoading) return <BillCardSkeleton />;
  if (props.error && !bill) {
    return (
      <View style={styles.errorContainer}>
        <Text style={styles.errorText}>{props.error}</Text>
        {props.retryable && <TouchableOpacity onPress={props.onRetry}><Text style={styles.retryText}>Retry</Text></TouchableOpacity>}
      </View>
    );
  }

  if (!bill) return <View />;

  const statusColor = STATUS_COLORS[bill.status] ?? tokens.colors.info;
  const isOverdue = bill.status === 'OVERDUE';
  const daysUntilDue = Math.ceil((new Date(bill.dueDate).getTime() - Date.now()) / (1000 * 60 * 60 * 24));

  return (
    <TouchableOpacity
      style={[styles.card, config.layout === 'compact' && styles.cardCompact]}
      activeOpacity={0.8}
      onPress={() => props.onAction?.({ event: 'view_bill', type: 'NAVIGATE', route: `/bills/${bill.billId}` })}
    >
      <View style={styles.header}>
        <View>
          <Text style={styles.label}>Bill {config.showBillNumber !== false ? bill.billNumber : ''}</Text>
          {config.showStatusBadge && (
            <View style={[styles.badge, { backgroundColor: `${statusColor}20` }]}>
              <Text style={[styles.badgeText, { color: statusColor }]}>{bill.status}</Text>
            </View>
          )}
        </View>
        <Text style={[styles.amount, tokens.colors.textPrimary]}>{bill.currency} {bill.totalAmount.toFixed(2)}</Text>
      </View>

      <View style={styles.divider} />

      <View style={styles.footer}>
        <View>
          <Text style={styles.footerLabel}>
            {isOverdue ? 'Overdue by' : 'Due in'} {Math.abs(daysUntilDue)} day{Math.abs(daysUntilDue) !== 1 ? 's' : ''}
          </Text>
          <Text style={styles.dueDate}>Due: {new Date(bill.dueDate).toLocaleDateString()}</Text>
        </View>
        {config.showPayButton && bill.status !== 'PAID' && (
          <TouchableOpacity
            style={[styles.payButton, isOverdue && styles.payButtonOverdue]}
            onPress={() => props.onAction?.({
              event: 'pay_bill', type: 'PAYMENT',
              params: { billId: bill.billId, amount: bill.outstandingAmount }
            })}
          >
            <Text style={styles.payButtonText}>
              {isOverdue ? 'Pay Now' : 'Pay'}
            </Text>
          </TouchableOpacity>
        )}
      </View>
    </TouchableOpacity>
  );
}

function BillCardSkeleton(): React.JSX.Element {
  return (
    <View style={styles.card}>
      <View style={[styles.skeleton, { width: '40%', marginBottom: 8 }]} />
      <View style={[styles.skeleton, { width: '25%' }]} />
    </View>
  );
}

const styles = StyleSheet.create({
  card: { backgroundColor: tokens.colors.surface, borderRadius: tokens.borderRadius.xl, padding: tokens.spacing.xl, ...tokens.elevation.sm },
  cardCompact: { padding: tokens.spacing.lg },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  label: { fontSize: tokens.fontSize.xs, color: tokens.colors.textSecondary, marginBottom: 4 },
  badge: { paddingHorizontal: 8, paddingVertical: 2, borderRadius: tokens.borderRadius.full, alignSelf: 'flex-start' },
  badgeText: { fontSize: 10, fontWeight: tokens.fontWeight.bold, letterSpacing: 0.5 },
  amount: { fontSize: tokens.fontSize['2xl'], fontWeight: tokens.fontWeight.bold },
  divider: { height: 1, backgroundColor: tokens.colors.border, marginVertical: tokens.spacing.md },
  footer: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  footerLabel: { fontSize: tokens.fontSize.sm, color: tokens.colors.textSecondary },
  dueDate: { fontSize: tokens.fontSize.xs, color: tokens.colors.textSecondary, marginTop: 2 },
  payButton: { backgroundColor: tokens.colors.primary500, paddingVertical: tokens.spacing.sm, paddingHorizontal: tokens.spacing.lg, borderRadius: tokens.borderRadius.full },
  payButtonOverdue: { backgroundColor: tokens.colors.error },
  payButtonText: { color: tokens.colors.surface, fontSize: tokens.fontSize.sm, fontWeight: tokens.fontWeight.semibold },
  errorContainer: { padding: tokens.spacing.lg, backgroundColor: '#FFF5F5', borderRadius: tokens.borderRadius.lg, alignItems: 'center' },
  errorText: { fontSize: tokens.fontSize.sm, color: tokens.colors.error, marginBottom: tokens.spacing.sm },
  retryText: { fontSize: tokens.fontSize.sm, color: tokens.colors.primary500, fontWeight: tokens.fontWeight.semibold },
  skeleton: { height: 20, backgroundColor: '#E0E0E0', borderRadius: 4 },
});
