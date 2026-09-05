/**
 * AISuggestionsCard — Home-screen widget showing AI-curated suggestions.
 *
 * Renders:
 * - 2-3 AI-recommended bundles or actions
 * - Refresh button to fetch new suggestions
 * - "See all" link to chat
 *
 * Used as a server-driven component in the layout manifest.
 */
import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ActivityIndicator } from 'react-native';
// Text-based icons (replace with react-native-vector-icons for production)
const SparkleIcon = () => <Text style={{ color: theme.primary ?? '#6C2DC7', fontSize: 16 }}>✨</Text>;
const RefreshIcon = () => <Text style={{ color: theme.primary ?? '#6C2DC7', fontSize: 14 }}>↻</Text>;
const ChevronIcon = () => <Text style={{ color: theme.primary ?? '#6C2DC7', fontSize: 14 }}>›</Text>;
import { useRecommendations } from '../../hooks/useRecommendations';
import { useAuth } from '../../hooks/useAuth';
import { useTenant } from '../../hooks/useTenant';

interface Props {
  title?: string;
  onItemPress?: (item: any) => void;
  onSeeAllPress?: () => void;
  theme?: {
    primary?: string;
    textPrimary?: string;
    textSecondary?: string;
    surface?: string;
  };
}

export function AISuggestionsCard({
  title = 'For you',
  onItemPress,
  onSeeAllPress,
  theme = {},
}: Props) {
  const { tenantId } = useTenant();
  const auth = useAuth();
  const connectionId = (auth as any).connectionId ?? null;
  const { bundles, loading, refresh } = useRecommendations(connectionId);
  const [refreshing, setRefreshing] = useState(false);

  const palette = {
    primary: theme.primary ?? '#6C2DC7',
    textPrimary: theme.textPrimary ?? '#212121',
    textSecondary: theme.textSecondary ?? '#757575',
    surface: theme.surface ?? '#F8F5FF',
  };

  const onRefresh = async () => {
    setRefreshing(true);
    await refresh();
    setRefreshing(false);
  };

  return (
    <View style={[styles.container, { backgroundColor: palette.surface }]}>
      <View style={styles.header}>
        <View style={styles.titleRow}>
          <SparkleIcon />
          <Text style={[styles.title, { color: palette.textPrimary }]}>{title}</Text>
        </View>
        <TouchableOpacity onPress={onRefresh} disabled={refreshing || loading} style={styles.refreshButton}>
          {refreshing
            ? <ActivityIndicator size="small" color={palette.primary} />
            : <RefreshIcon />}
        </TouchableOpacity>
      </View>

      {loading && bundles.length === 0 ? (
        <View style={styles.loadingRow}>
          <ActivityIndicator size="small" color={palette.primary} />
          <Text style={[styles.loadingText, { color: palette.textSecondary }]}>
            Personalizing suggestions...
          </Text>
        </View>
      ) : bundles.length === 0 ? (
        <Text style={[styles.emptyText, { color: palette.textSecondary }]}>
          No suggestions available right now.
        </Text>
      ) : (
        <>
          {bundles.slice(0, 3).map((b, i) => (
            <TouchableOpacity
              key={b.productId ?? i}
              style={styles.itemRow}
              onPress={() => onItemPress?.(b)}
            >
              <View style={styles.itemLeft}>
                <Text style={[styles.itemName, { color: palette.textPrimary }]}>
                  {b.name}
                </Text>
                <Text style={[styles.itemReason, { color: palette.textSecondary }]}>
                  {b.reason}
                </Text>
              </View>
              <View style={styles.itemRight}>
                <Text style={[styles.itemPrice, { color: palette.primary }]}>
                  {b.currency} {b.price.toFixed(0)}
                </Text>
                {b.urgency === 'high' && (
                  <View style={styles.urgencyBadge}>
                    <Text style={styles.urgencyText}>URGENT</Text>
                  </View>
                )}
              </View>
            </TouchableOpacity>
          ))}

          {onSeeAllPress && (
            <TouchableOpacity style={styles.seeAllRow} onPress={onSeeAllPress}>
              <Text style={[styles.seeAllText, { color: palette.primary }]}>
                Ask AI for more recommendations
              </Text>
              <ChevronIcon />
            </TouchableOpacity>
          )}
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { borderRadius: 12, padding: 16, marginVertical: 8 },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 },
  titleRow: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  title: { fontSize: 16, fontWeight: '600' },
  refreshButton: { padding: 4 },
  loadingRow: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingVertical: 12 },
  loadingText: { fontSize: 13 },
  emptyText: { fontSize: 13, paddingVertical: 12 },
  itemRow: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingVertical: 10, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: '#E0E0E0',
  },
  itemLeft: { flex: 1, paddingRight: 8 },
  itemName: { fontSize: 14, fontWeight: '500' },
  itemReason: { fontSize: 12, marginTop: 2 },
  itemRight: { alignItems: 'flex-end' },
  itemPrice: { fontSize: 14, fontWeight: '600' },
  urgencyBadge: {
    backgroundColor: '#FF6B00', borderRadius: 4, paddingHorizontal: 6, paddingVertical: 2, marginTop: 4,
  },
  urgencyText: { color: '#FFFFFF', fontSize: 9, fontWeight: '700' },
  seeAllRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 4, paddingTop: 12 },
  seeAllText: { fontSize: 13, fontWeight: '500' },
});
