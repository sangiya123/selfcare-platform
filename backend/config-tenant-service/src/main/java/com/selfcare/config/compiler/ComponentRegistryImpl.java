package com.selfcare.config.compiler;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Registry of known component IDs that the layout renderer / config compiler
 * recognizes. New widgets must be registered here before they can be used
 * in published layouts.
 *
 * Multi-industry aware: components may be tagged with industry applicability.
 */
@Component
public class ComponentRegistryImpl implements ConfigCompiler.ComponentRegistry {

    private static final Set<String> KNOWN_COMPONENTS = Set.of(
        // Telco / general
        "BalanceCard", "UsageCard", "BillCard", "BundlesList",
        "QuickActionsGrid", "NotificationsList", "BannersCarousel",
        "PayButton", "SupportTile", "ProfileHeader",
        "ConnectionSwitcher", "DataTopupCard",
        // Insurance
        "InsurancePolicyCard", "InsuranceClaimCard",
        "InsuranceBeneficiaryCard", "InsurancePremiumDue",
        "InsuranceClaimList", "InsurancePremiumList",
        // Generic
        "GenericHeader", "GenericList", "GenericAction", "GenericForm",
        "GenericCard", "GenericBanner", "GenericEmptyState",
        // Preview/palette parity with selfcare Studio widgets + legacy layouts
        "AccountHeader", "UsageSummary", "UsageChart", "PaymentCard",
        "PackageCard", "OfferCarousel", "NotificationCard",
        "SupportCard", "Banner", "AIAssistantEntry"
    );

    @Override
    public boolean hasComponent(String componentId) {
        return componentId != null && KNOWN_COMPONENTS.contains(componentId);
    }

    public Set<String> getAll() {
        return KNOWN_COMPONENTS;
    }
}
