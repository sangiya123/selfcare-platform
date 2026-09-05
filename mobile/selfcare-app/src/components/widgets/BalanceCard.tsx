/**
 * BalanceCard — Displays account balance.
 *
 * Component ID: BalanceCard
 * Used for: prepaid balance, postpaid amount due
 *
 * Props (from config):
 *   - showCurrency: boolean
 *   - showExpiry: boolean
 *   - showQuickRecharge: boolean
 *   - layout: 'hero' | 'compact' | 'minimal'
 */

import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import type { WidgetProps } from '../ComponentRegistry';
import { tokens } from '../../styles/design-tokens';

interface BalanceData {
  amount: number;
  currency: string;
  balanceType: 'PREPAID' | 'POSTPAID_DUE';
  expiryDate?: string;
  connectionId?: string;
}

export function BalanceCard(props: WidgetProps): React.JSX.Element {
  const data = props.data as BalanceData | undefined;
  const config = (props.props || {}) as {
    showCurrency?: boolean;
    showExpiry?: boolean;
    showQuickRecharge?: boolean;
    layout?: 'hero' | 'compact' | 'minimal';
  };

  if (props.isLoading) {
    return <BalanceCardSkeleton layout={config.layout} />;
  }

  if (props.error) {
    return (
      <View style={styles.errorContainer}>
        <Text style={styles.errorTitle}>Couldn't load balance</Text>
        {props.retryable && (
          <TouchableOpacity onPress={props.onRetry} style={styles.retryButton}>
            <Text style={styles.retryText}>Try again</Text>
          </TouchableOpacity>
        )}
      </View>
    );
  }

  if (!data) {
    return <View />;
  }

  const layout = config.layout || 'hero';

  if (layout === 'minimal') {
    return (
      <View style={styles.minimalContainer}>
        <Text style={styles.minimalLabel}>
          {data.balanceType === 'PREPAID' ? 'Balance' : 'Due'}
        </Text>
        <Text style={styles.minimalValue}>
          {config.showCurrency !== false ? `${data.currency} ` : ''}
          {data.amount.toFixed(2)}
        </Text>
      </View>
    );
  }

  return (
    <View style={[styles.heroContainer, layout === 'compact' && styles.compact]}>
      <Text style={styles.heroLabel}>
        {data.balanceType === 'PREPAID' ? 'Available Balance' : 'Amount Due'}
      </Text>
      <View style={styles.heroValueRow}>
        {config.showCurrency !== false && (
          <Text style={styles.heroCurrency}>{data.currency}</Text>
        )}
        <Text style={styles.heroValue}>{data.amount.toFixed(2)}</Text>
      </View>

      {config.showExpiry && data.expiryDate && data.balanceType === 'PREPAID' && (
        <Text style={styles.heroExpiry}>
          Valid until {new Date(data.expiryDate).toLocaleDateString()}
        </Text>
      )}

      {config.showQuickRecharge && data.balanceType === 'PREPAID' && (
        <TouchableOpacity
          style={styles.heroRechargeButton}
          onPress={() => props.onAction?.({
            event: 'recharge',
            type: 'NAVIGATE',
            route: '/recharge',
          })}
        >
          <Text style={styles.heroRechargeText}>Recharge</Text>
        </TouchableOpacity>
      )}
    </View>
  );
}

function BalanceCardSkeleton({ layout }: { layout?: string }) {
  return (
    <View style={[styles.heroContainer, layout === 'compact' && styles.compact, styles.skeleton]}>
      <View style={styles.skeletonBlock} />
      <View style={[styles.skeletonBlock, { width: '60%' }]} />
    </View>
  );
}

const styles = StyleSheet.create({
  heroContainer: {
    backgroundColor: tokens.colors.primary700,
    borderRadius: tokens.borderRadius.xl,
    padding: tokens.spacing.xl,
    minHeight: 140,
  },
  compact: {
    minHeight: 100,
    padding: tokens.spacing.lg,
  },
  heroLabel: {
    color: 'rgba(255, 255, 255, 0.8)',
    fontSize: tokens.fontSize.sm,
    fontWeight: tokens.fontWeight.medium,
    marginBottom: tokens.spacing.sm,
  },
  heroValueRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
  },
  heroCurrency: {
    color: 'rgba(255, 255, 255, 0.9)',
    fontSize: tokens.fontSize.lg,
    fontWeight: tokens.fontWeight.medium,
    marginRight: tokens.spacing.xs,
  },
  heroValue: {
    color: tokens.colors.surface,
    fontSize: tokens.fontSize['4xl'],
    fontWeight: tokens.fontWeight.bold,
  },
  heroExpiry: {
    color: 'rgba(255, 255, 255, 0.7)',
    fontSize: tokens.fontSize.xs,
    marginTop: tokens.spacing.sm,
  },
  heroRechargeButton: {
    backgroundColor: tokens.colors.surface,
    paddingVertical: tokens.spacing.sm,
    paddingHorizontal: tokens.spacing.lg,
    borderRadius: tokens.borderRadius.full,
    alignSelf: 'flex-start',
    marginTop: tokens.spacing.lg,
  },
  heroRechargeText: {
    color: tokens.colors.primary700,
    fontSize: tokens.fontSize.sm,
    fontWeight: tokens.fontWeight.semibold,
  },
  minimalContainer: {
    padding: tokens.spacing.md,
  },
  minimalLabel: {
    color: tokens.colors.textSecondary,
    fontSize: tokens.fontSize.xs,
    marginBottom: 2,
  },
  minimalValue: {
    color: tokens.colors.textPrimary,
    fontSize: tokens.fontSize.lg,
    fontWeight: tokens.fontWeight.semibold,
  },
  errorContainer: {
    padding: tokens.spacing.lg,
    backgroundColor: '#FFF5F5',
    borderRadius: tokens.borderRadius.lg,
    alignItems: 'center',
  },
  errorTitle: {
    color: tokens.colors.textPrimary,
    fontSize: tokens.fontSize.sm,
    fontWeight: tokens.fontWeight.semibold,
    marginBottom: tokens.spacing.sm,
  },
  retryButton: {
    paddingVertical: tokens.spacing.xs,
    paddingHorizontal: tokens.spacing.md,
  },
  retryText: {
    color: tokens.colors.primary500,
    fontSize: tokens.fontSize.sm,
    fontWeight: tokens.fontWeight.semibold,
  },
  skeleton: {
    opacity: 0.6,
  },
  skeletonBlock: {
    height: 20,
    backgroundColor: 'rgba(255, 255, 255, 0.3)',
    borderRadius: 4,
    marginBottom: 8,
  },
});