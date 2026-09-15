package com.selfcare.config.seed;

import com.selfcare.config.compiler.ComponentRegistryImpl;
import com.selfcare.config.domain.ComponentCatalogItem;
import com.selfcare.config.service.ComponentCatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Seeds the component_catalog collection with the known-components registry so
 * dev environments ship a complete palette (admin portal + compiled manifest).
 *
 * Runs only on dev/docker. Production catalogs are authored via selfcare Studio.
 * The label/icon/category tables below are seed DATA — they mirror the admin
 * palette and can be overridden per tenant through the admin API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComponentCatalogSeeder {

    private final ComponentCatalogService service;
    private final ComponentRegistryImpl componentRegistry;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private static final String GLOBAL_ENV = "*";

    private static final Map<String, SeedMeta> CATALOG_META = Map.ofEntries(
        Map.entry("BalanceCard", new SeedMeta("Balance Card", "💰", "DISPLAY", List.of("ios", "android"), "UniversalBox",
            Map.of("renderType", "card", "variant", "primary", "elevation", "md", "padding", "lg", "borderRadius", "lg",
                "children", List.of(
                    Map.of("primitive", "UniversalText", "config", Map.of("variant", "label", "value", Map.of("path", "balance.label"))),
                    Map.of("primitive", "UniversalText", "config", Map.of("variant", "value", "value", Map.of("path", "balance.amount"), "format", "currency")),
                    Map.of("primitive", "UniversalText", "config", Map.of("variant", "caption", "value", Map.of("template", "Due {balance.dueDate}"), "color", "secondary"))
                )))),
        Map.entry("UsageCard", new SeedMeta("Usage Card", "📊", "DISPLAY", List.of("ios", "android"), "UniversalBox",
            Map.of("renderType", "card", "elevation", "sm", "padding", "lg", "children", List.of(
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "value", "value", Map.of("path", "usage.data.used"), "format", "bytes")),
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "caption", "value", Map.of("path", "usage.data.total"), "format", "bytes"))
            )))),
        Map.entry("UsageSummary", new SeedMeta("Usage Summary", "📊", "DISPLAY", List.of("ios", "android"), "UniversalBox",
            Map.of("renderType", "card", "variant", "surface", "elevation", "sm", "padding", "lg", "children", List.of(
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "title", "value", Map.of("path", "usage.summary.period"))),
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "value", "value", Map.of("path", "usage.summary.data.used"), "format", "bytes")),
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "body", "value", Map.of("path", "usage.summary.voice.used")))
            )))),
        Map.entry("UsageChart", new SeedMeta("Usage Chart", "📈", "CHART", List.of("ios", "android"), "UniversalChart",
            Map.of("type", "line", "dataSource", "usage.history", "xPath", "periodEnd", "series", List.of(Map.of("key", "data.used", "label", "data"))))),
        Map.entry("BillCard", new SeedMeta("Bill Card", "🧾", "DISPLAY", List.of("ios", "android"), "UniversalBox",
            Map.of("renderType", "card", "elevation", "sm", "padding", "md", "children", List.of(
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "value", "value", Map.of("path", "bill.amount"), "format", "currency")),
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "caption", "value", Map.of("path", "bill.dueDate"), "format", "date")),
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "caption", "value", Map.of("path", "bill.status"), "color", "secondary"))
            )))),
        Map.entry("BundlesList", new SeedMeta("Bundles", "📦", "DISPLAY", List.of("ios", "android"), "UniversalList",
            Map.of("layout", "vertical", "item", Map.of("primitive", "UniversalBox", "config", Map.of("renderType", "card", "padding", "md", "children", List.of(
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "subtitle", "value", Map.of("path", "name"))),
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "caption", "value", Map.of("path", "price"), "format", "currency")),
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "caption", "value", Map.of("path", "validity")))
            ))), "pullToRefresh", true))),
        Map.entry("PackageCard", new SeedMeta("Package Card", "📦", "DISPLAY", List.of("ios", "android"), "UniversalBox",
            Map.of("renderType", "card", "variant", "accent", "elevation", "md", "padding", "lg", "children", List.of(
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "title", "value", Map.of("path", "package.name"))),
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "value", "value", Map.of("path", "package.price"), "format", "currency")),
                Map.of("primitive", "UniversalButton", "config", Map.of("variant", "primary", "label", "Select", "action", Map.of("type", "NAVIGATE", "route", "/packages")))
            )))),
        Map.entry("OfferCarousel", new SeedMeta("Offer Carousel", "🎁", "DISPLAY", List.of("ios", "android"), "UniversalList",
            Map.of("layout", "horizontal", "item", Map.of("primitive", "UniversalBox", "config", Map.of("renderType", "card", "padding", "md", "children", List.of(
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "subtitle", "value", Map.of("path", "title"))),
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "caption", "value", Map.of("path", "description")))
            )))))),
        Map.entry("QuickActionsGrid", new SeedMeta("Quick Actions", "⚡", "NAVIGATION", List.of("ios", "android"), "UniversalGrid",
            Map.of("columns", 4, "gap", "md", "item", Map.of("primitive", "UniversalBox", "config", Map.of("renderType", "card", "alignItems", "center", "padding", "sm", "children", List.of(
                Map.of("primitive", "UniversalImage", "config", Map.of("shape", "icon", "source", Map.of("path", "iconUrl"))),
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "caption", "value", Map.of("path", "label"), "align", "center"))
            )))))),
        Map.entry("NotificationsList", new SeedMeta("Notifications", "🔔", "DISPLAY", List.of("ios", "android"), "UniversalList",
            Map.of("layout", "vertical", "separator", true, "item", Map.of("primitive", "UniversalBox", "config", Map.of("renderType", "surface", "padding", "sm", "children", List.of(
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "body", "value", Map.of("path", "message"))),
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "caption", "value", Map.of("path", "createdAt"), "format", "relative-time", "color", "secondary"))
            )))))),
        Map.entry("NotificationCard", new SeedMeta("Notification", "🔔", "DISPLAY", List.of("ios", "android"), "UniversalBox",
            Map.of("renderType", "card", "padding", "md", "children", List.of(
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "body", "value", Map.of("path", "message"))),
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "caption", "value", Map.of("path", "createdAt"), "format", "relative-time"))
            )))),
        Map.entry("BannersCarousel", new SeedMeta("Banners", "🖼️", "DISPLAY", List.of("ios", "android"), "UniversalList",
            Map.of("layout", "horizontal", "item", Map.of("primitive", "UniversalImage", "config", Map.of("shape", "banner", "source", Map.of("path", "imageUrl"), "height", 180))))),
        Map.entry("Banner", new SeedMeta("Banner", "🖼️", "DISPLAY", List.of("ios", "android"), "UniversalBox",
            Map.of("renderType", "card", "variant", "gradient", "padding", "lg", "children", List.of(
                Map.of("primitive", "UniversalImage", "config", Map.of("shape", "banner", "source", Map.of("path", "imageUrl"), "height", 140)),
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "subtitle", "value", Map.of("path", "title")))
            )))),
        Map.entry("PayButton", new SeedMeta("Pay Button", "💳", "PAYMENT", List.of("ios", "android"), "UniversalButton",
            Map.of("variant", "primary", "size", "lg", "label", "Pay now", "action", Map.of("type", "PAYMENT")))),
        Map.entry("PaymentCard", new SeedMeta("Payment Card", "💳", "PAYMENT", List.of("ios", "android"), "UniversalBox",
            Map.of("renderType", "card", "padding", "md", "children", List.of(
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "body", "value", Map.of("path", "card.label"))),
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "caption", "value", Map.of("path", "card.numberMasked")))
            )))),
        Map.entry("SupportTile", new SeedMeta("Support", "🆘", "NAVIGATION", List.of("ios", "android"), "UniversalBox",
            Map.of("renderType", "card", "padding", "md", "children", List.of(
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "subtitle", "value", Map.of("path", "label"))),
                Map.of("primitive", "UniversalButton", "config", Map.of("variant", "outline", "label", Map.of("path", "actionLabel"), "action", Map.of("type", "NAVIGATE", "route", "/support")))
            )))),
        Map.entry("SupportCard", new SeedMeta("Support Card", "🆘", "NAVIGATION", List.of("ios", "android"), "UniversalBox",
            Map.of("renderType", "card", "padding", "md", "children", List.of(
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "body", "value", Map.of("path", "message")))
            )))),
        Map.entry("ProfileHeader", new SeedMeta("Profile Header", "👤", "DISPLAY", List.of("ios", "android"), "UniversalBox",
            Map.of("renderType", "card", "padding", "lg", "children", List.of(
                Map.of("primitive", "UniversalImage", "config", Map.of("shape", "circle", "source", Map.of("path", "profile.avatarUrl"), "size", 64)),
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "title", "value", Map.of("path", "profile.name"))),
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "caption", "value", Map.of("path", "profile.mobileNo"), "color", "secondary"))
            )))),
        Map.entry("AccountHeader", new SeedMeta("Account Header", "👤", "DISPLAY", List.of("ios", "android"), "UniversalBox",
            Map.of("renderType", "card", "padding", "lg", "children", List.of(
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "title", "value", Map.of("path", "account.name"))),
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "caption", "value", Map.of("path", "account.connectionType"), "color", "secondary"))
            )))),
        Map.entry("ConnectionSwitcher", new SeedMeta("Connection Switcher", "🔄", "INPUT", List.of("ios", "android"), "UniversalList",
            Map.of("layout", "vertical", "item", Map.of("primitive", "UniversalBox", "config", Map.of("renderType", "surface", "padding", "sm", "children", List.of(
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "body", "value", Map.of("path", "label")))
            )))))),
        Map.entry("DataTopupCard", new SeedMeta("Data Top-up", "📶", "INPUT", List.of("ios", "android"), "UniversalBox",
            Map.of("renderType", "card", "padding", "lg", "children", List.of(
                Map.of("primitive", "UniversalInput", "config", Map.of("type", "number", "label", "Recharge amount")),
                Map.of("primitive", "UniversalButton", "config", Map.of("variant", "primary", "label", "Recharge", "action", Map.of("type", "CALL_API", "journeyId", "recharge")))
            )))),
        Map.entry("AIAssistantEntry", new SeedMeta("AI Assistant", "🤖", "NAVIGATION", List.of("ios", "android"), "UniversalBox",
            Map.of("renderType", "card", "variant", "gradient", "padding", "md", "onPress", Map.of("type", "NAVIGATE", "route", "/ai"), "children", List.of(
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "subtitle", "value", "AI Assistant")),
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "caption", "value", Map.of("path", "greeting"), "color", "secondary"))
            )))),
        Map.entry("InsurancePolicyCard", new SeedMeta("Policy Card", "📄", "DISPLAY", List.of("ios", "android"), "UniversalBox",
            Map.of("renderType", "card", "padding", "lg", "children", List.of(
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "title", "value", Map.of("path", "policy.policyNumber"))),
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "body", "value", Map.of("path", "policy.status"))),
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "caption", "value", Map.of("path", "policy.premium"), "format", "currency"))
            )))),
        Map.entry("InsuranceClaimCard", new SeedMeta("Claim Card", "📋", "DISPLAY", List.of("ios", "android"), "UniversalBox",
            Map.of("renderType", "card", "padding", "md", "children", List.of(
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "body", "value", Map.of("path", "claim.claimNumber"))),
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "caption", "value", Map.of("path", "claim.status")))
            )))),
        Map.entry("InsuranceBeneficiaryCard", new SeedMeta("Beneficiary Card", "👥", "DISPLAY", List.of("ios", "android"), "UniversalBox",
            Map.of("renderType", "card", "padding", "md", "children", List.of(
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "body", "value", Map.of("path", "beneficiary.name")))
            )))),
        Map.entry("InsurancePremiumDue", new SeedMeta("Premium Due", "⏰", "DISPLAY", List.of("ios", "android"), "UniversalBox",
            Map.of("renderType", "card", "variant", "accent", "padding", "md", "children", List.of(
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "value", "value", Map.of("path", "premium.amount"), "format", "currency")),
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "caption", "value", Map.of("path", "premium.dueDate"), "format", "date"))
            )))),
        Map.entry("InsuranceClaimList", new SeedMeta("Claims List", "📋", "DISPLAY", List.of("ios", "android"), "UniversalList",
            Map.of("layout", "vertical", "item", Map.of("primitive", "UniversalBox", "config", Map.of("renderType", "card", "padding", "sm", "children", List.of(
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "body", "value", Map.of("path", "claimNumber"))),
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "caption", "value", Map.of("path", "status"), "color", "secondary"))
            )))))),
        Map.entry("InsurancePremiumList", new SeedMeta("Premiums List", "💸", "DISPLAY", List.of("ios", "android"), "UniversalList",
            Map.of("layout", "vertical", "item", Map.of("primitive", "UniversalBox", "config", Map.of("renderType", "card", "padding", "sm", "children", List.of(
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "body", "value", Map.of("path", "amount"), "format", "currency")),
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "caption", "value", Map.of("path", "dueDate"), "format", "date"))
            )))))),
        Map.entry("GenericHeader", new SeedMeta("Header", "🧱", "DISPLAY", List.of("ios", "android"), "UniversalBox",
            Map.of("renderType", "surface", "padding", "md", "children", List.of(
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "title", "value", Map.of("path", "title")))
            )))),
        Map.entry("GenericList", new SeedMeta("List", "📃", "DISPLAY", List.of("ios", "android"), "UniversalList",
            Map.of("layout", "vertical", "item", Map.of("primitive", "UniversalBox", "config", Map.of("renderType", "surface", "padding", "sm", "children", List.of(
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "body", "value", Map.of("path", "label")))
            )))))),
        Map.entry("GenericAction", new SeedMeta("Action", "⏯️", "INPUT", List.of("ios", "android"), "UniversalButton",
            Map.of("variant", "secondary", "label", Map.of("path", "label"), "action", Map.of("type", "NAVIGATE")))),
        Map.entry("GenericForm", new SeedMeta("Form", "📝", "INPUT", List.of("ios", "android"), "UniversalBox",
            Map.of("renderType", "surface", "padding", "lg", "children", List.of(
                Map.of("primitive", "UniversalInput", "config", Map.of("type", "text")),
                Map.of("primitive", "UniversalButton", "config", Map.of("variant", "primary", "label", "Submit", "action", Map.of("type", "CALL_API")))
            )))),
        Map.entry("GenericCard", new SeedMeta("Card", "🔲", "DISPLAY", List.of("ios", "android"), "UniversalBox",
            Map.of("renderType", "card", "padding", "md", "children", List.of(
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "body", "value", Map.of("path", "body")))
            )))),
        Map.entry("GenericBanner", new SeedMeta("Notification Banner", "📣", "FEEDBACK", List.of("ios", "android"), "UniversalBox",
            Map.of("renderType", "card", "variant", "accent", "padding", "md", "children", List.of(
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "body", "value", Map.of("path", "message")))
            )))),
        Map.entry("GenericEmptyState", new SeedMeta("Empty State", "🗂️", "DISPLAY", List.of("ios", "android"), "UniversalBox",
            Map.of("renderType", "surface", "alignItems", "center", "padding", "xl", "children", List.of(
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "title", "value", Map.of("path", "title"), "align", "center")),
                Map.of("primitive", "UniversalText", "config", Map.of("variant", "body", "value", Map.of("path", "message"), "align", "center", "color", "secondary"))
            ))))
    );

    @EventListener(ApplicationReadyEvent.class)
    public void seedCatalog() {
        if (!"dev".equalsIgnoreCase(activeProfile) && !"docker".equalsIgnoreCase(activeProfile)) {
            log.debug("Skipping component catalog seed — profile is {}, not dev/docker", activeProfile);
            return;
        }

        int total = 0;
        for (String componentId : componentRegistry.getAll()) {
            SeedMeta meta = CATALOG_META.get(componentId);
            if (meta == null) {
                log.warn("No seed metadata for component '{}' — skipping.", componentId);
                continue;
            }
            ComponentCatalogItem item = ComponentCatalogItem.builder()
                    .tenantId("dialog-lk")
                    .environment(GLOBAL_ENV)
                    .componentId(componentId)
                    .label(meta.label())
                    .icon(meta.icon())
                    .category(meta.category())
                    .platforms(meta.platforms())
                    .primitive(meta.primitive())
                    .config(meta.config())
                    .security("display".equals(meta.category()) ? "display" : "read")
                    .enabled(true)
                    .status("PUBLISHED")
                    .build();
            service.upsert(item);
            total++;
        }
        log.info("Seeded component catalog: {} components for tenant=dialog-lk (env {})", total, GLOBAL_ENV);
    }

    private record SeedMeta(String label, String icon, String category, List<String> platforms,
                            String primitive, Map<String, Object> config) {
        SeedMeta(String label, String icon, String category, List<String> platforms) {
            this(label, icon, category, platforms, null, null);
        }
    }
}