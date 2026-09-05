/**
 * BillsScreen — list of bills for the active connection.
 */
import React, { useEffect, useState, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  ActivityIndicator,
  RefreshControl,
} from 'react-native';
import { useApi } from '../hooks/useApi';
import { useTenant } from '../hooks/useTenant';
import { tokens } from '../styles/design-tokens';

interface Bill {
  billId: string;
  billNumber?: string;
  totalAmount: number;
  paidAmount: number;
  outstandingAmount: number;
  currency: string;
  dueDate: string;
  status: 'ISSUED' | 'DUE' | 'PAID' | 'OVERDUE' | 'PARTIALLY_PAID' | 'CANCELLED';
}

const STATUS_COLORS: Record<Bill['status'], { bg: string; fg: string; label: string }> = {
  ISSUED: { bg: '#dbeafe', fg: '#1e40af', label: 'Issued' },
  DUE: { bg: '#fef3c7', fg: '#92400e', label: 'Due' },
  PAID: { bg: '#d1fae5', fg: '#065f46', label: 'Paid' },
  OVERDUE: { bg: '#fee2e2', fg: '#991b1b', label: 'Overdue' },
  PARTIALLY_PAID: { bg: '#fed7aa', fg: '#9a3412', label: 'Partial' },
  CANCELLED: { bg: '#f3f4f6', fg: '#6b7280', label: 'Cancelled' },
};

export function BillsScreen(): React.JSX.Element {
  const api = useApi();
  const { tenantId: activeTenant } = useTenant();
  const [bills, setBills] = useState<Bill[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    try {
      const data = await api.bills.list({ tenantId: activeTenant });
      setBills(data.items ?? []);
    } catch (err) {
      // Surface error in UI; in production also retry with backoff
      setBills([]);
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

  if (bills.length === 0) {
    return (
      <View style={styles.centered}>
        <Text style={styles.emptyTitle}>No bills yet</Text>
        <Text style={styles.emptyBody}>When you have a bill, it will show up here.</Text>
      </View>
    );
  }

  return (
    <FlatList
      data={bills}
      keyExtractor={(b) => b.billId}
      contentContainerStyle={styles.list}
      refreshControl={
        <RefreshControl
          refreshing={refreshing}
          onRefresh={() => {
            setRefreshing(true);
            load();
          }}
        />
      }
      renderItem={({ item }) => <BillRow bill={item} />}
    />
  );
}

function BillRow({ bill }: { bill: Bill }): React.JSX.Element {
  const status = STATUS_COLORS[bill.status] ?? STATUS_COLORS.ISSUED;
  const due = new Date(bill.dueDate);
  const dueIn = Math.round((due.getTime() - Date.now()) / (24 * 60 * 60 * 1000));

  return (
    <TouchableOpacity style={styles.card} activeOpacity={0.7}>
      <View style={styles.cardHeader}>
        <Text style={styles.billNumber}>{bill.billNumber ?? bill.billId.slice(0, 8)}</Text>
        <View style={[styles.badge, { backgroundColor: status.bg }]}>
          <Text style={[styles.badgeText, { color: status.fg }]}>{status.label}</Text>
        </View>
      </View>
      <Text style={styles.amount}>
        {bill.currency} {bill.outstandingAmount.toFixed(2)}
      </Text>
      <Text style={styles.subtext}>
        of {bill.currency} {bill.totalAmount.toFixed(2)} total
      </Text>
      {dueIn > 0 ? (
        <Text style={styles.due}>Due in {dueIn} day{dueIn === 1 ? '' : 's'}</Text>
      ) : dueIn === 0 ? (
        <Text style={styles.due}>Due today</Text>
      ) : (
        <Text style={[styles.due, styles.dueOverdue]}>{Math.abs(dueIn)} days overdue</Text>
      )}
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  centered: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: tokens.colors.surfaceSubtle },
  emptyTitle: { fontSize: 16, fontWeight: '600', color: tokens.colors.textPrimary },
  emptyBody: { fontSize: 13, color: tokens.colors.textSecondary, marginTop: 4 },
  list: { padding: 16 },
  card: {
    backgroundColor: tokens.colors.surface,
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    shadowColor: '#000',
    shadowOpacity: 0.04,
    shadowRadius: 8,
    elevation: 1,
  },
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  billNumber: { fontSize: 14, fontWeight: '600', color: tokens.colors.textPrimary },
  badge: { paddingHorizontal: 8, paddingVertical: 2, borderRadius: 10 },
  badgeText: { fontSize: 11, fontWeight: '600' },
  amount: { fontSize: 22, fontWeight: '700', color: tokens.colors.textPrimary, marginTop: 8 },
  subtext: { fontSize: 12, color: tokens.colors.textSecondary, marginTop: 2 },
  due: { fontSize: 12, color: tokens.colors.textSecondary, marginTop: 8 },
  dueOverdue: { color: tokens.colors.error, fontWeight: '600' },
});
