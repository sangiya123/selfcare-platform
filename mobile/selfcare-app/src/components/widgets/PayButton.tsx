/**
 * PayButton — Initiates a payment transaction.
 *
 * Component ID: PayButton
 * Triggers: payment flow for bill, recharge, or bundle purchase.
 */

import React, { useState } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ActivityIndicator, Modal, TextInput, Alert } from 'react-native';
import type { WidgetProps } from '../ComponentRegistry';
import { tokens } from '../../styles/design-tokens';

interface Config {
  label?: string;
  amount?: number;
  currency?: string;
  paymentType?: 'BILL' | 'RECHARGE' | 'BUNDLE' | 'GENERIC';
  bundleId?: string;
  confirmationRequired?: boolean;
  confirmationMessage?: string;
  providers?: ('CARD' | 'WALLET' | 'BANK_TRANSFER')[];
  successRoute?: string;
  failureRoute?: string;
}

export function PayButton(props: WidgetProps): React.JSX.Element {
  const config = (props.props ?? {}) as Config;
  const [showModal, setShowModal] = useState(false);
  const [selectedProvider, setSelectedProvider] = useState<string | null>(null);
  const [isProcessing, setIsProcessing] = useState(false);

  const amount = config.amount ?? (props.data as { amount: number })?.amount ?? 0;
  const currency = config.currency ?? (props.data as { currency: string })?.currency ?? 'LKR';

  const handlePress = () => {
    if (config.confirmationRequired) {
      Alert.alert('Confirm Payment', config.confirmationMessage ?? `Pay ${currency} ${amount.toFixed(2)}?`, [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Confirm', onPress: () => setShowModal(true) },
      ]);
    } else {
      setShowModal(true);
    }
  };

  const handleProviderSelect = async (provider: string) => {
    setSelectedProvider(provider);
    setIsProcessing(true);

    try {
      await props.onAction?.({
        event: 'initiate_payment',
        type: 'PAYMENT',
        params: {
          paymentType: config.paymentType ?? 'GENERIC',
          bundleId: config.bundleId,
          amount,
          currency,
          provider,
        },
      });
      setShowModal(false);
      if (config.successRoute) {
        props.onAction?.({ event: 'payment_success', type: 'NAVIGATE', route: config.successRoute });
      }
    } catch (err) {
      Alert.alert('Payment Failed', 'Please try again or use a different payment method.');
      if (config.failureRoute) {
        props.onAction?.({ event: 'payment_failed', type: 'NAVIGATE', route: config.failureRoute });
      }
    } finally {
      setIsProcessing(false);
      setSelectedProvider(null);
    }
  };

  const providers = config.providers ?? ['CARD', 'WALLET'];

  return (
    <>
      <TouchableOpacity
        style={[styles.button, props.isLoading && styles.buttonDisabled]}
        activeOpacity={0.8}
        onPress={handlePress}
        disabled={props.isLoading || amount <= 0}
      >
        {props.isLoading
          ? <ActivityIndicator color={tokens.colors.surface} size="small" />
          : <Text style={styles.buttonText}>{config.label ?? `Pay ${currency} ${amount.toFixed(2)}`}</Text>
        }
      </TouchableOpacity>

      <Modal visible={showModal} transparent animationType="slide" onRequestClose={() => setShowModal(false)}>
        <View style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <Text style={styles.modalTitle}>Select Payment Method</Text>
            <Text style={styles.modalAmount}>{currency} {amount.toFixed(2)}</Text>

            {providers.map((p) => (
              <TouchableOpacity
                key={p}
                style={styles.providerOption}
                onPress={() => handleProviderSelect(p)}
                disabled={isProcessing}
              >
                <Text style={styles.providerLabel}>{p.replace('_', ' ')}</Text>
                {selectedProvider === p && isProcessing
                  ? <ActivityIndicator size="small" color={tokens.colors.primary500} />
                  : <Text style={styles.providerArrow}>›</Text>
                }
              </TouchableOpacity>
            ))}

            <TouchableOpacity style={styles.cancelButton} onPress={() => setShowModal(false)}>
              <Text style={styles.cancelText}>Cancel</Text>
            </TouchableOpacity>
          </View>
        </View>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  button: { backgroundColor: tokens.colors.primary500, paddingVertical: tokens.spacing.md, paddingHorizontal: tokens.spacing.xl, borderRadius: tokens.borderRadius.full, alignItems: 'center', justifyContent: 'center', minHeight: 52 },
  buttonDisabled: { opacity: 0.6 },
  buttonText: { color: tokens.colors.surface, fontSize: tokens.fontSize.base, fontWeight: tokens.fontWeight.bold },
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'flex-end' },
  modalContent: { backgroundColor: tokens.colors.surface, borderTopLeftRadius: tokens.borderRadius['2xl'], borderTopRightRadius: tokens.borderRadius['2xl'], padding: tokens.spacing.xl },
  modalTitle: { fontSize: tokens.fontSize.lg, fontWeight: tokens.fontWeight.bold, color: tokens.colors.textPrimary, marginBottom: tokens.spacing.sm },
  modalAmount: { fontSize: tokens.fontSize['2xl'], fontWeight: tokens.fontWeight.bold, color: tokens.colors.textPrimary, marginBottom: tokens.spacing.xl },
  providerOption: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: tokens.spacing.md, borderBottomWidth: 1, borderBottomColor: tokens.colors.border },
  providerLabel: { fontSize: tokens.fontSize.base, color: tokens.colors.textPrimary, fontWeight: tokens.fontWeight.medium },
  providerArrow: { fontSize: 20, color: tokens.colors.textSecondary },
  cancelButton: { marginTop: tokens.spacing.lg, alignItems: 'center', padding: tokens.spacing.md },
  cancelText: { fontSize: tokens.fontSize.base, color: tokens.colors.primary500, fontWeight: tokens.fontWeight.medium },
});
