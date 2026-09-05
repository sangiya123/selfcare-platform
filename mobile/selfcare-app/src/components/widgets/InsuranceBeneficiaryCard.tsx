/**
 * InsuranceBeneficiaryCard — Shows a single beneficiary of an insurance policy.
 *
 * Component ID: InsuranceBeneficiaryCard
 * Industry: INSURANCE
 */

import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import type { WidgetProps } from '../ComponentRegistry';
import { tokens } from '../../styles/design-tokens';

export interface InsuranceBeneficiary {
  beneficiaryId: string;
  name: string;
  relationship: 'SPOUSE' | 'CHILD' | 'PARENT' | 'SIBLING' | 'FRIEND' | 'OTHER';
  dateOfBirth?: string;
  sharePercent: number;
  isPrimary: boolean;
  contactPhone?: string;
  contactEmail?: string;
  isVerified: boolean;
}

interface Config {
  showShare?: boolean;
  showRelationship?: boolean;
  showContact?: boolean;
  showVerifiedBadge?: boolean;
}

const RELATIONSHIP_LABELS: Record<InsuranceBeneficiary['relationship'], string> = {
  SPOUSE: 'Spouse',
  CHILD: 'Child',
  PARENT: 'Parent',
  SIBLING: 'Sibling',
  FRIEND: 'Friend',
  OTHER: 'Other',
};

export function InsuranceBeneficiaryCard(props: WidgetProps): React.JSX.Element {
  const beneficiary = props.data as InsuranceBeneficiary | undefined;
  const config = (props.props ?? {}) as Config;

  if (props.isLoading) return <BeneficiarySkeleton />;
  if (!beneficiary) return <View />;

  return (
    <TouchableOpacity
      style={styles.card}
      activeOpacity={0.8}
      onPress={() => props.onAction?.({ event: 'view_beneficiary', type: 'NAVIGATE', route: `/insurance/beneficiaries/${beneficiary.beneficiaryId}` })}
    >
      <View style={styles.header}>
        <View style={styles.avatar}>
          <Text style={styles.avatarText}>{beneficiary.name.charAt(0).toUpperCase()}</Text>
        </View>
        <View style={styles.headerContent}>
          <View style={styles.nameRow}>
            <Text style={styles.name}>{beneficiary.name}</Text>
            {beneficiary.isPrimary && (
              <View style={styles.primaryBadge}>
                <Text style={styles.primaryBadgeText}>PRIMARY</Text>
              </View>
            )}
          </View>
          {config.showRelationship !== false && (
            <Text style={styles.relationship}>{RELATIONSHIP_LABELS[beneficiary.relationship]}</Text>
          )}
        </View>
        {config.showShare !== false && (
          <View style={styles.shareContainer}>
            <Text style={styles.shareValue}>{beneficiary.sharePercent}%</Text>
            <Text style={styles.shareLabel}>share</Text>
          </View>
        )}
      </View>

      {config.showContact !== false && (beneficiary.contactPhone || beneficiary.contactEmail) && (
        <View style={styles.contactRow}>
          {beneficiary.contactPhone && <Text style={styles.contactText}>{beneficiary.contactPhone}</Text>}
          {beneficiary.contactEmail && <Text style={styles.contactText}>{beneficiary.contactEmail}</Text>}
        </View>
      )}

      {config.showVerifiedBadge !== false && (
        <View style={styles.footer}>
          <View style={styles.verifiedBadge}>
            <Text style={[styles.verifiedText, { color: beneficiary.isVerified ? tokens.colors.success : tokens.colors.warning }]}>
              {beneficiary.isVerified ? '✓ Verified' : '⚠ Verification pending'}
            </Text>
          </View>
        </View>
      )}
    </TouchableOpacity>
  );
}

function BeneficiarySkeleton(): React.JSX.Element {
  return (
    <View style={styles.card}>
      <View style={[styles.skel, { width: '50%' }]} />
    </View>
  );
}

const styles = StyleSheet.create({
  card: { backgroundColor: tokens.colors.surface, borderRadius: tokens.borderRadius.lg, padding: tokens.spacing.md, ...tokens.elevation.sm },
  header: { flexDirection: 'row', alignItems: 'center', gap: tokens.spacing.md },
  avatar: { width: 48, height: 48, borderRadius: 24, backgroundColor: tokens.colors.primary500, justifyContent: 'center', alignItems: 'center' },
  avatarText: { color: tokens.colors.surface, fontSize: tokens.fontSize.lg, fontWeight: tokens.fontWeight.bold },
  headerContent: { flex: 1 },
  nameRow: { flexDirection: 'row', alignItems: 'center', gap: tokens.spacing.xs },
  name: { fontSize: tokens.fontSize.base, fontWeight: tokens.fontWeight.semibold, color: tokens.colors.textPrimary },
  primaryBadge: { backgroundColor: tokens.colors.accent500, paddingHorizontal: 6, paddingVertical: 2, borderRadius: tokens.borderRadius.sm },
  primaryBadgeText: { color: tokens.colors.surface, fontSize: 9, fontWeight: tokens.fontWeight.bold, letterSpacing: 0.5 },
  relationship: { fontSize: tokens.fontSize.xs, color: tokens.colors.textSecondary, marginTop: 2 },
  shareContainer: { alignItems: 'flex-end' },
  shareValue: { fontSize: tokens.fontSize.lg, fontWeight: tokens.fontWeight.bold, color: tokens.colors.primary500 },
  shareLabel: { fontSize: 10, color: tokens.colors.textSecondary },
  contactRow: { marginTop: tokens.spacing.sm, paddingTop: tokens.spacing.sm, borderTopWidth: 1, borderTopColor: tokens.colors.border },
  contactText: { fontSize: tokens.fontSize.xs, color: tokens.colors.textSecondary, marginBottom: 2 },
  footer: { marginTop: tokens.spacing.sm },
  verifiedBadge: {},
  verifiedText: { fontSize: tokens.fontSize.xs, fontWeight: tokens.fontWeight.semibold },
  skel: { height: 18, backgroundColor: '#E0E0E0', borderRadius: 4 },
});
