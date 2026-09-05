/**
 * Widget Registration — Registers all widget components with the ComponentRegistry.
 *
 * Called once at app startup (App.tsx bootstrap).
 *
 * Note: Screen components (LoginScreen, HomeScreen, etc.) are not
 * registered here — they are imported directly in the navigation stack.
 */

import { ComponentRegistry } from '../config/ComponentRegistry';
import { BalanceCard } from './BalanceCard';
import { UsageCard } from './UsageCard';
import { BillCard } from './BillCard';
import { PayButton } from './PayButton';
import { BundlesList } from './BundlesList';
import { NotificationsList } from './NotificationsList';
import { BannersCarousel } from './BannersCarousel';
import { QuickActionsGrid } from './QuickActionsGrid';
import { SupportTile } from './SupportTile';
import { InsurancePolicyCard } from './InsurancePolicyCard';
import { InsuranceClaimCard } from './InsuranceClaimCard';
import { InsuranceBeneficiaryCard } from './InsuranceBeneficiaryCard';
import { InsurancePremiumDue } from './InsurancePremiumDue';
import { AISuggestionsCard } from './AISuggestionsCard';

export function registerAllWidgets(registry: ComponentRegistry): void {
  // Telco Selfcare Widgets
  registry.register('BalanceCard', BalanceCard as any, {
    description: 'Displays account balance (prepaid/postpaid)',
    security: 'display',
    analyticsEvents: ['balance_viewed'],
  });
  registry.register('UsageCard', UsageCard as any, {
    description: 'Shows data/voice/SMS usage for billing period',
    security: 'display',
    analyticsEvents: ['usage_viewed'],
  });
  registry.register('BillCard', BillCard as any, {
    description: 'Bill summary with payment status',
    security: 'display',
    analyticsEvents: ['bill_viewed'],
  });
  registry.register('PayButton', PayButton as any, {
    description: 'Payment CTA button',
    security: 'write',
    analyticsEvents: ['payment_initiated'],
  });
  registry.register('BundlesList', BundlesList as any, {
    description: 'Available bundles and top-up packages',
    security: 'display',
    analyticsEvents: ['bundles_viewed', 'bundle_selected'],
  });
  registry.register('NotificationsList', NotificationsList as any, {
    description: 'Recent notifications',
    security: 'read',
    analyticsEvents: ['notifications_viewed', 'notification_opened'],
  });
  registry.register('BannersCarousel', BannersCarousel as any, {
    description: 'Promotional banner carousel',
    security: 'display',
    analyticsEvents: ['banner_viewed', 'banner_tapped'],
  });
  registry.register('QuickActionsGrid', QuickActionsGrid as any, {
    description: 'Grid of quick-access tiles',
    security: 'display',
    analyticsEvents: ['quick_action_tapped'],
  });
  registry.register('SupportTile', SupportTile as any, {
    description: 'Customer support contact options',
    security: 'display',
    analyticsEvents: ['support_viewed'],
  });

  // Insurance Widgets
  registry.register('InsurancePolicyCard', InsurancePolicyCard as any, {
    description: 'Insurance policy summary',
    platforms: ['android', 'ios'],
    security: 'display',
    analyticsEvents: ['policy_viewed'],
  });
  registry.register('InsuranceClaimCard', InsuranceClaimCard as any, {
    description: 'Insurance claim summary',
    platforms: ['android', 'ios'],
    security: 'display',
    analyticsEvents: ['claim_viewed'],
  });
  registry.register('InsuranceBeneficiaryCard', InsuranceBeneficiaryCard as any, {
    description: 'Policy beneficiary card',
    platforms: ['android', 'ios'],
    security: 'read',
    analyticsEvents: ['beneficiary_viewed'],
  });
  registry.register('InsurancePremiumDue', InsurancePremiumDue as any, {
    description: 'Upcoming premium payment reminder',
    platforms: ['android', 'ios'],
    security: 'display',
    analyticsEvents: ['premium_viewed'],
  });
  registry.register('AISuggestionsCard', AISuggestionsCard as any, {
    description: 'AI-powered personalized bundle and plan suggestions',
    security: 'read',
    analyticsEvents: ['ai_suggestions_viewed', 'ai_suggestion_clicked'],
  });
}

// Re-export all widgets for convenience
export {
  BalanceCard,
  UsageCard,
  BillCard,
  PayButton,
  BundlesList,
  NotificationsList,
  BannersCarousel,
  QuickActionsGrid,
  SupportTile,
  InsurancePolicyCard,
  InsuranceClaimCard,
  InsuranceBeneficiaryCard,
  InsurancePremiumDue,
  AISuggestionsCard,
};
