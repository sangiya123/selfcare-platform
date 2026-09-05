/**
 * BundlesList — Displays available bundles / top-up packages.
 *
 * Component ID: BundlesList
 * Layout: vertical scrollable list
 */

import React, { useState } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, FlatList } from 'react-native';
import type { WidgetProps } from '../ComponentRegistry';
import { tokens } from '../../styles/design-tokens';
import type { Product } from '../../config/types';

interface Config {
  category?: string;
  showPrice?: boolean;
  showDescription?: boolean;
  maxItems?: number;
  ctaLabel?: string;
}

export function BundlesList(props: WidgetProps): React.JSX.Element {
  const config = (props.props ?? {}) as Config;
  const products = (props.data as Product[] | undefined) ?? [];
  const [expanded, setExpanded] = useState(false);

  const maxItems = config.maxItems ?? 5;
  const displayProducts = expanded ? products : products.slice(0, maxItems);

  if (props.isLoading) return <BundlesSkeleton count={maxItems} />;
  if (props.error && !products.length) {
    return (
      <View style={styles.errorContainer}>
        <Text style={styles.errorText}>{props.error}</Text>
        {props.onRetry && <TouchableOpacity onPress={props.onRetry}><Text style={styles.retryText}>Retry</Text></TouchableOpacity>}
      </View>
    );
  }

  if (!products.length) {
    return (
      <View style={styles.emptyContainer}>
        <Text style={styles.emptyText}>No bundles available</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <FlatList
        data={displayProducts}
        keyExtractor={(item) => item.productId}
        scrollEnabled={false}
        ItemSeparatorComponent={() => <View style={styles.separator} />}
        renderItem={({ item }) => (
          <BundleItem
            product={item}
            showPrice={config.showPrice !== false}
            showDescription={config.showDescription}
            ctaLabel={config.ctaLabel}
            onAction={props.onAction}
          />
        )}
      />
      {products.length > maxItems && (
        <TouchableOpacity style={styles.expandButton} onPress={() => setExpanded((e) => !e)}>
          <Text style={styles.expandText}>{expanded ? 'Show less' : `Show ${products.length - maxItems} more`}</Text>
        </TouchableOpacity>
      )}
    </View>
  );
}

function BundleItem({
  product, showPrice, showDescription, ctaLabel, onAction
}: {
  product: Product;
  showPrice: boolean;
  showDescription?: boolean;
  ctaLabel?: string;
  onAction?: WidgetProps['onAction'];
}): React.JSX.Element {
  const allowanceText = product.allowances.map((a) => formatAllowance(a)).join(' + ');

  return (
    <TouchableOpacity style={styles.item} activeOpacity={0.7}
      onPress={() => onAction?.({
        event: 'select_bundle', type: 'NAVIGATE',
        route: `/bundles/${product.productId}`,
        params: { productId: product.productId }
      })}
    >
      <View style={styles.itemContent}>
        <View style={styles.itemHeader}>
          <Text style={styles.itemName}>{product.name}</Text>
          {showPrice && (
            <Text style={styles.itemPrice}>{product.price.currency} {product.price.amount.toFixed(0)}</Text>
          )}
        </View>
        <Text style={styles.allowanceText}>{allowanceText}</Text>
        {showDescription && <Text style={styles.description}>{product.description}</Text>}
        <View style={styles.footer}>
          <Text style={styles.validity}>Valid {product.validityDays} day{product.validityDays !== 1 ? 's' : ''}</Text>
          <TouchableOpacity
            style={styles.ctaButton}
            onPress={() => onAction?.({
              event: 'purchase_bundle', type: 'PAYMENT',
              params: { productId: product.productId, amount: product.price.amount }
            })}
          >
            <Text style={styles.ctaText}>{ctaLabel ?? 'Buy'}</Text>
          </TouchableOpacity>
        </View>
      </View>
    </TouchableOpacity>
  );
}

function formatAllowance(a: Product['allowances'][0]): string {
  if (a.type === 'DATA') {
    const gb = (a.quantityBytes ?? 0) / (1024 * 1024 * 1024);
    return `${gb >= 1 ? gb.toFixed(0) + ' GB' : ((a.quantityBytes ?? 0) / (1024 * 1024)).toFixed(0) + ' MB'} Data`;
  }
  if (a.type === 'VOICE') {
    const mins = (a.quantitySeconds ?? 0) / 60;
    return `${mins.toFixed(0)} Min Voice`;
  }
  return `${a.quantityCount ?? 0} SMS`;
}

function BundlesSkeleton({ count }: { count: number }): React.JSX.Element {
  return (
    <View style={styles.container}>
      {Array.from({ length: count }).map((_, i) => (
        <View key={i} style={[styles.item, { paddingVertical: tokens.spacing.lg }]}>
          <View style={[styles.skeleton, { width: '50%', marginBottom: 8 }]} />
          <View style={[styles.skeleton, { width: '30%' }]} />
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {},
  item: { backgroundColor: tokens.colors.surface, borderRadius: tokens.borderRadius.lg, padding: tokens.spacing.md },
  itemContent: { gap: 4 },
  itemHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  itemName: { fontSize: tokens.fontSize.base, fontWeight: tokens.fontWeight.semibold, color: tokens.colors.textPrimary },
  itemPrice: { fontSize: tokens.fontSize.base, fontWeight: tokens.fontWeight.bold, color: tokens.colors.primary500 },
  allowanceText: { fontSize: tokens.fontSize.sm, color: tokens.colors.textSecondary },
  description: { fontSize: tokens.fontSize.xs, color: tokens.colors.textSecondary, marginTop: 2 },
  footer: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: tokens.spacing.sm },
  validity: { fontSize: tokens.fontSize.xs, color: tokens.colors.textSecondary },
  ctaButton: { backgroundColor: tokens.colors.primary500, paddingVertical: tokens.spacing.xs, paddingHorizontal: tokens.spacing.md, borderRadius: tokens.borderRadius.full },
  ctaText: { color: tokens.colors.surface, fontSize: tokens.fontSize.sm, fontWeight: tokens.fontWeight.semibold },
  separator: { height: tokens.spacing.sm },
  expandButton: { alignItems: 'center', padding: tokens.spacing.md },
  expandText: { fontSize: tokens.fontSize.sm, color: tokens.colors.primary500, fontWeight: tokens.fontWeight.medium },
  emptyContainer: { padding: tokens.spacing.xl, alignItems: 'center' },
  emptyText: { fontSize: tokens.fontSize.sm, color: tokens.colors.textSecondary },
  errorContainer: { padding: tokens.spacing.lg, backgroundColor: '#FFF5F5', borderRadius: tokens.borderRadius.lg, alignItems: 'center' },
  errorText: { fontSize: tokens.fontSize.sm, color: tokens.colors.error, marginBottom: tokens.spacing.sm },
  retryText: { fontSize: tokens.fontSize.sm, color: tokens.colors.primary500, fontWeight: tokens.fontWeight.semibold },
  skeleton: { height: 16, backgroundColor: '#E0E0E0', borderRadius: 4 },
});
