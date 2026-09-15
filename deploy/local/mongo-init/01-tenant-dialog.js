// ============================================================================
// Selfcare Platform — Complete MongoDB Seed for Dialog (dialog-lk)
// ============================================================================
//
// Runs ONCE on first container start via /docker-entrypoint-initdb.d/
// Idempotent — uses upsert so re-running on a populated DB is safe.
//
// Collections seeded:
//   tenant_configs, theme_documents, navigation_documents, layout_documents,
//   component_catalog, asset_documents, product_mapping_documents,
//   feature_flags, journey_definitions, client_integrations
//
// Rule: NOTHING is hardcoded in services. Everything below is the DB source
// of truth that services read at runtime via API / Mongo queries.
// ============================================================================

print("=== Seeding Selfcare Platform — Dialog (dialog-lk) ===");

const now = new Date();
const ISO = () => now.toISOString();
const TENANT = "dialog-lk";

// ────────────────────────────────────────────────────────────────────────────
// 1. TENANT CONFIG (tenant_configs collection)
//    MongoTenantValidator reads tenantId + status from here.
//    This is THE critical document — without it, all requests get 401.
// ────────────────────────────────────────────────────────────────────────────
db.tenant_configs.updateOne(
  { tenantId: TENANT },
  {
    $set: {
      tenantId: TENANT,
      name: "Dialog Axiata PLC",
      operator: "dialog",
      country: "LK",
      industry: "TELCO",
      supportedLobs: ["MOBILE", "BB", "DTV", "FIBRE"],
      supportedLocales: ["en-LK", "si-LK", "ta-LK"],
      defaultLocale: "en-LK",
      environments: {
        dev:    { apiBaseUrl: "http://localhost:8080", wsBaseUrl: "ws://localhost:8080", active: true },
        stg:    { apiBaseUrl: "https://stg-api.selfcare.selfcare.lk", wsBaseUrl: "wss://stg-api.selfcare.selfcare.lk", active: true },
        prod:   { apiBaseUrl: "https://api.selfcare.selfcare.lk", wsBaseUrl: "wss://api.selfcare.selfcare.lk", active: true }
      },
      enabledFeatures: [
        "ai.assistant.enabled", "ai.summarization.enabled", "ai.sentiment.enabled",
        "ai.recommendations.enabled", "biometric.login.enabled", "dashboard.dark-mode.enabled",
        "recharge.credit-card.enabled", "recharge.bank-transfer.enabled",
        "recharge.ezcash.enabled", "recharge.starpoints.enabled",
        "gamification.badges.enabled", "usage.history.enabled", "billing.ebill.enabled",
        "roaming.packages.enabled", "idd.rates.enabled", "vas.catalog.enabled",
        "loyalty.starpoints.enabled", "share.credit.enabled", "data.loan.enabled",
        "DTV.channels.enabled", "support.chat.enabled", "notifications.push.enabled",
        "notifications.sms.enabled", "profile.edit.enabled", "connections.manage.enabled",
        "payment.savedcards.enabled", "audio.playback.enabled", "qr.scanner.enabled",
        "dashboard.mini-banner.enabled", "dashboard.app-gallery.enabled",
        "dashboard.other-services.enabled", "dashboard.full-width-detail.enabled",
        "dashboard.usage-hyperlink.enabled"
      ],
      packVersion: "1.0.0",
      providerBindings: {
        default: "com.selfcare.dialog.provider",
        auth: "com.selfcare.dialog.provider.DialogAuthProvider",
        balance: "com.selfcare.dialog.provider.DialogBalanceProvider",
        usage: "com.selfcare.dialog.provider.DialogUsageProvider",
        billing: "com.selfcare.dialog.provider.DialogBillingProvider",
        payment: "com.selfcare.dialog.provider.DialogPaymentProvider",
        product: "com.selfcare.dialog.provider.DialogProductCatalogProvider",
        notification: "com.selfcare.dialog.provider.DialogNotificationProvider",
        profile: "com.selfcare.dialog.provider.DialogProfileProvider"
      },
      status: "ACTIVE",
      updatedAt: ISO(),
      updatedBy: "system"
    },
    $setOnInsert: {
      _id: "dialog-lk",
      createdAt: ISO(),
      createdBy: "system"
    }
  },
  { upsert: true }
);
print("  ✓ tenant_configs dialog-lk");

// ────────────────────────────────────────────────────────────────────────────
// 2. THEME DOCUMENTS (theme_documents collection)
//    ThemeDocument — design tokens per tenant/environment
// ────────────────────────────────────────────────────────────────────────────
db.theme_documents.updateOne(
  { tenantId: TENANT, name: "dialog-default", version: "1.0.0" },
  {
    $set: {
      _id: "dialog-theme-1",
      tenantId: TENANT,
      environment: "prod",
      name: "dialog-default",
      version: "1.0.0",
      versionNumber: 1,
      status: "PUBLISHED",
      baseTokens: {
        colors: {
          // Dialog brand palette (mda-mobile config/styles.js)
          primary: "#65246E",
          primary500: "#65246E",
          primary700: "#4A1A52",
          primary900: "#3A1244",
          primary300: "#8C6395",
          accent: "#EB275A",
          accent500: "#EB275A",
          accentGlow: "#FD4D5D",
          background: "#FFFFFF",
          surface: "#FFFFFF",
          surfaceSubtle: "#F5F5F5",
          textPrimary: "#1A1A1A",
          textSecondary: "#727272",
          textOnPrimary: "#FFFFFF",
          onPrimary: "#FFFFFF",
          onSurface: "#1A1A1A",
          onSurfaceVariant: "#727272",
          border: "#E0E0E0",
          error: "#B30000",
          success: "#2E7D32",
          warning: "#F88E13",
          info: "#178DE7"
        },
        typography: {
          fontFamily: "Roboto",
          headingFontFamily: "Roboto",
          h1: "32px", h2: "24px", h3: "20px",
          body: "16px", small: "14px", tiny: "12px",
          weightBold: 700, weightMedium: 500, weightRegular: 400
        },
        spacing: { xs: 4, sm: 8, md: 16, lg: 24, xl: 32, xxl: 48 },
        borderRadius: { sm: 4, md: 8, lg: 12, xl: 16, pill: 999 },
        elevation: {
          none: "0px 0px 0px transparent",
          low: "0px 1px 3px rgba(0,0,0,0.12)",
          medium: "0px 4px 8px rgba(0,0,0,0.16)",
          high: "0px 8px 16px rgba(0,0,0,0.24)"
        },
        iconSizes: { sm: 16, md: 24, lg: 32, xl: 48 },
        breakpoints: { mobile: 0, tablet: 768, desktop: 1024, wide: 1440 },
        buttons: {
          primary: { bg: "#D5003E", text: "#FFFFFF", radius: 8, height: 48, gradient: ["#D5003E", "#FD4D5D"] },
          secondary: { bg: "#EB275A", text: "#FFFFFF", radius: 8, height: 48 },
          outline: { bg: "transparent", text: "#65246E", border: "#65246E", radius: 8, height: 48 },
          text: { bg: "transparent", text: "#65246E", height: 48 }
        },
        cards: {
          default: { bg: "#FFFFFF", border: "#E0E0E0", borderRadius: 12, padding: 16 },
          highlighted: { bg: "#F7F0F8", border: "#8C6395", borderRadius: 12, padding: 16 }
        }
      },
      branding: {
        logo:      { assetId: "dialog-logo",    url: "https://cdn.selfcare.lk/dialog/logo.svg",        alt: "Dialog", width: 200, height: 60 },
        appIcon:   { assetId: "dialog-icon",    url: "https://cdn.selfcare.lk/dialog/icon-512.png",    alt: "Dialog Axiata" },
        favicon:   { assetId: "dialog-favicon", url: "https://cdn.selfcare.lk/dialog/favicon.ico",     alt: "Dialog favicon" },
        splash:    { assetId: "dialog-splash",  url: "https://cdn.selfcare.lk/dialog/splash.png",      alt: "Dialog splash" },
        loginLogo: { assetId: "dialog-logo",    url: "https://cdn.selfcare.lk/dialog/logo.svg",        alt: "Dialog" }
      },
      overrideTokens: {
        // dashboard.dark-mode.enabled is on — provide dark design tokens.
        darkMode: {
          colors: {
            primary: "#B76AC4",
            primary500: "#B76AC4",
            primary700: "#8C4D98",
            primary900: "#5E3168",
            accent: "#EB275A",
            accentGlow: "#FD4D5D",
            background: "#121212",
            surface: "#1E1E1E",
            surfaceSubtle: "#2A2A2A",
            textPrimary: "#F5F5F5",
            textSecondary: "#9E9E9E",
            textOnPrimary: "#FFFFFF",
            onPrimary: "#FFFFFF",
            onSurface: "#F5F5F5",
            onSurfaceVariant: "#9E9E9E",
            border: "#333333",
            error: "#FF5252",
            success: "#69F0AE",
            warning: "#FFAB40",
            info: "#40C4FF"
          },
          buttons: {
            primary: { bg: "#B76AC4", text: "#FFFFFF", radius: 8, height: 48, gradient: ["#B76AC4", "#EB275A"] },
            secondary: { bg: "#EB275A", text: "#FFFFFF", radius: 8, height: 48 },
            outline: { bg: "transparent", text: "#B76AC4", border: "#B76AC4", radius: 8, height: 48 }
          },
          cards: {
            default: { bg: "#1E1E1E", border: "#333333", borderRadius: 12, padding: 16 },
            highlighted: { bg: "#2A1730", border: "#8C6395", borderRadius: 12, padding: 16 }
          }
        }
      },
      resolvedTokens: null,
      publishedBy: "admin@selfcare.lk",
      publishedAt: ISO(),
      createdBy: "system",
      createdAt: ISO(),
      updatedAt: ISO()
    }
  },
  { upsert: true }
);
print("  ✓ theme_documents dialog-default@1.0.0");

// ────────────────────────────────────────────────────────────────────────────
// 3. NAVIGATION DOCUMENTS (navigation_documents collection)
//    NavigationDocument — tabs, drawer, routes, deep links
// ────────────────────────────────────────────────────────────────────────────
db.navigation_documents.updateOne(
  { tenantId: TENANT, name: "dialog-main", version: 1 },
  {
    $set: {
      _id: "dialog-nav-1",
      tenantId: TENANT,
      environment: "prod",
      name: "dialog-main",
      version: 1,
      status: "PUBLISHED",
      tabs: [
        { id: "home",    label: "Home",    icon: "home",    route: "/home",    order: 1, badge: null, visibleWhen: null },
        { id: "usage",   label: "Usage",   icon: "chart",   route: "/usage",   order: 2, badge: null, visibleWhen: null },
        { id: "bills",   label: "Bills",   icon: "bill",    route: "/bills",   order: 3, badge: { source: "unreadBills", max: 9 }, visibleWhen: null },
        { id: "shop",    label: "Shop",    icon: "shop",    route: "/shop",    order: 4, badge: null, visibleWhen: null },
        { id: "more",    label: "More",    icon: "menu",    route: "/more",    order: 5, badge: null, visibleWhen: null }
      ],
      drawerItems: [
        { id: "profile",      label: "My Profile",      icon: "user",      route: "/profile",      section: "account", requiresAuth: true, order: 1, enabled: true },
        { id: "connections",  label: "My Connections",  icon: "link",      route: "/connections",  section: "account", requiresAuth: true, order: 2, enabled: true },
        { id: "recharge",     label: "Recharge",        icon: "wallet",    route: "/recharge",     section: "payments", requiresAuth: true, order: 3, enabled: true },
        { id: "payment",      label: "Payment History", icon: "history",   route: "/payment/history", section: "payments", requiresAuth: true, order: 4, enabled: true },
        { id: "roaming",      label: "Roaming",         icon: "globe",     route: "/roaming",      section: "services", requiresAuth: true, order: 5, enabled: true },
        { id: "idd",          label: "IDD",             icon: "phone",     route: "/idd",          section: "services", requiresAuth: true, order: 6, enabled: true },
        { id: "loyalty",      label: "StarPoints",      icon: "star",      route: "/loyalty",      section: "services", requiresAuth: true, order: 7, enabled: true },
        { id: "vas",          label: "VAS Services",    icon: "grid",      route: "/vas",          section: "services", requiresAuth: true, order: 8, enabled: true },
        { id: "share-credit", label: "Share Credit",    icon: "share",     route: "/share-credit", section: "services", requiresAuth: true, order: 9, enabled: true },
        { id: "data-loan",    label: "Data Loan",       icon: "download",  route: "/data-loan",    section: "services", requiresAuth: true, order: 10, enabled: true },
        { id: "dtv",          label: "Dialog TV",       icon: "tv",        route: "/dtv",          section: "services", requiresAuth: true, order: 11, enabled: true },
        { id: "support",      label: "Support",         icon: "help",      route: "/support",      section: "help", requiresAuth: false, order: 12, enabled: true },
        { id: "notifications",label: "Notifications",   icon: "bell",      route: "/notifications",section: "help", requiresAuth: true, order: 13, enabled: true },
        { id: "settings",     label: "Settings",        icon: "settings",  route: "/settings",     section: "help", requiresAuth: true, order: 14, enabled: true },
        { id: "logout",       label: "Sign Out",        icon: "logout",    route: "/logout",       section: "account", requiresAuth: true, order: 15, enabled: true }
      ],
      quickActions: [
        { id: "recharge",   label: "Quick Recharge", icon: "wallet",  action: { type: "START_JOURNEY", journeyId: "recharge-flow", route: null, url: null, params: {}, analyticsEvent: "quick_recharge" }, visibleWhen: null },
        { id: "buy-data",   label: "Buy Data",       icon: "data",    action: { type: "START_JOURNEY", journeyId: "data-pack-flow", route: null, url: null, params: {}, analyticsEvent: "quick_buy_data" }, visibleWhen: null },
        { id: "pay-bill",   label: "Pay Bill",       icon: "bill",    action: { type: "NAVIGATE", route: "/bills/pay", journeyId: null, url: null, params: {}, analyticsEvent: "quick_pay_bill" }, visibleWhen: null },
        { id: "support",    label: "Get Help",       icon: "support", action: { type: "NAVIGATE", route: "/support", journeyId: null, url: null, params: {}, analyticsEvent: "quick_support" }, visibleWhen: null }
      ],
      routes: [
        { id: "home",          path: "/home",          screen: "HomeScreen",          params: [], requiresAuth: false, guards: [] },
        { id: "usage",         path: "/usage",         screen: "UsageScreen",         params: [], requiresAuth: true, guards: ["auth"] },
        { id: "usage-history", path: "/usage/history", screen: "UsageHistoryScreen",  params: [], requiresAuth: true, guards: ["auth"] },
        { id: "bills",         path: "/bills",         screen: "BillsScreen",         params: [], requiresAuth: true, guards: ["auth"] },
        { id: "bills-pay",     path: "/bills/pay",     screen: "PayBillScreen",       params: [{ name: "billId", type: "string", required: false }], requiresAuth: true, guards: ["auth"] },
        { id: "shop",          path: "/shop",          screen: "ShopScreen",          params: [], requiresAuth: false, guards: [] },
        { id: "shop-pack",     path: "/shop/pack/:id", screen: "PackDetailScreen",    params: [{ name: "id", type: "string", required: true }], requiresAuth: false, guards: [] },
        { id: "profile",       path: "/profile",       screen: "ProfileScreen",       params: [], requiresAuth: true, guards: ["auth"] },
        { id: "connections",   path: "/connections",   screen: "ConnectionsScreen",   params: [], requiresAuth: true, guards: ["auth"] },
        { id: "recharge",      path: "/recharge",      screen: "RechargeScreen",      params: [], requiresAuth: true, guards: ["auth"] },
        { id: "payment-history",path: "/payment/history",screen: "PaymentHistoryScreen",params: [], requiresAuth: true, guards: ["auth"] },
        { id: "roaming",       path: "/roaming",       screen: "RoamingScreen",       params: [], requiresAuth: true, guards: ["auth"] },
        { id: "idd",           path: "/idd",           screen: "IddScreen",           params: [], requiresAuth: true, guards: ["auth"] },
        { id: "loyalty",       path: "/loyalty",       screen: "LoyaltyScreen",       params: [], requiresAuth: true, guards: ["auth"] },
        { id: "vas",           path: "/vas",           screen: "VasScreen",           params: [], requiresAuth: true, guards: ["auth"] },
        { id: "share-credit",  path: "/share-credit",  screen: "ShareCreditScreen",   params: [], requiresAuth: true, guards: ["auth"] },
        { id: "data-loan",     path: "/data-loan",     screen: "DataLoanScreen",      params: [], requiresAuth: true, guards: ["auth"] },
        { id: "dtv",           path: "/dtv",           screen: "DtvScreen",           params: [], requiresAuth: true, guards: ["auth"] },
        { id: "support",       path: "/support",       screen: "SupportScreen",       params: [], requiresAuth: false, guards: [] },
        { id: "notifications", path: "/notifications", screen: "NotificationsScreen", params: [], requiresAuth: true, guards: ["auth"] },
        { id: "settings",      path: "/settings",      screen: "SettingsScreen",      params: [], requiresAuth: true, guards: ["auth"] },
        { id: "ai-chat",       path: "/ai-chat",       screen: "AIChatScreen",        params: [], requiresAuth: true, guards: ["auth"] },
        { id: "login",         path: "/login",         screen: "LoginScreen",         params: [], requiresAuth: false, guards: [] },
        { id: "otp",           path: "/otp",           screen: "OtpScreen",           params: [{ name: "phone", type: "string", required: true }], requiresAuth: false, guards: [] },
        { id: "logout",        path: "/logout",        screen: "LoginScreen",         params: [], requiresAuth: false, guards: [] }
      ],
      universalLinks: { enabled: true, associatedDomains: ["selfcare.selfcare.lk"], universalLinks: [] },
      deepLinks: [
        { id: "dl-recharge",  scheme: "mydialog", pathPattern: "/recharge",  route: "/recharge",  paramMap: {}, fallbackUrl: null, enabled: true, description: "Open recharge screen" },
        { id: "dl-pay",       scheme: "mydialog", pathPattern: "/pay",       route: "/bills/pay", paramMap: {}, fallbackUrl: null, enabled: true, description: "Open pay bill screen" },
        { id: "dl-ai-chat",   scheme: "mydialog", pathPattern: "/ai-chat",   route: "/ai-chat",   paramMap: {}, fallbackUrl: null, enabled: true, description: "Open AI assistant" }
      ],
      allowedDomains: ["selfcare.selfcare.lk", "api.selfcare.lk", "cdn.selfcare.lk"],
      metadata: { themeRef: "dialog-default@1.0.0" },
      publishedBy: "admin@selfcare.lk",
      publishedAt: ISO(),
      createdBy: "system",
      createdAt: ISO(),
      updatedAt: ISO()
    }
  },
  { upsert: true }
);
print("  ✓ navigation_documents dialog-main@1");

// ────────────────────────────────────────────────────────────────────────────
// 4. LAYOUT DOCUMENTS (layout_documents collection)
//    LayoutDocument — per-experience, per-environment, per-profile
//    19 experiences × 2 environments = 38 layout documents
// ────────────────────────────────────────────────────────────────────────────
const experiences = ["home", "packages", "bills", "payments", "usage", "profile", "support", "connections", "recharge", "roaming", "idd", "loyalty", "vas", "share-credit", "data-loan", "dtv", "notifications", "settings", "ai"];
const envs = ["dev", "prod"];
const profileKey = "mobile_prepaid_default";

// Service endpoint definitions — authored here (the DB source of truth) and
// compiled + signed into the manifest. Apps resolve SDK endpoint paths from
// `manifest.services` — endpoint changes are DB-level changes, never app
// releases (v6 rule #1). Paths are relative to the API base URL, so both the
// signed manifest and the dev fallbacks in serviceEndpoints.ts agree with the
// canonical gateway routes (docker-compose ports).
const SERVICES = {
  auth: {
    otp: "/api/v1/auth/otp",
    otpVerify: "/api/v1/auth/otp/verify",
    refresh: "/api/v1/auth/refresh",
    signout: "/api/v1/auth/signout"
  },
  customer: {
    consent: "/api/v1/customer/consent",
    dataErasure: "/api/v1/customer/data-erasure"
  },
  billing: {
    bills: "/api/v1/bills",
    bill: "/api/v1/bills/{billId}",
    current: "/api/v1/bills/current"
  },
  usage: {
    current: "/api/v1/usage/current",
    history: "/api/v1/usage/{connectionId}/history",
    creditLimit: "/api/v1/credit-limit/{connectionId}",
    roaming: "/api/v1/usage/{connectionId}/roaming"
  },
  payments: {
    create: "/api/v1/payments",
    history: "/api/v1/payments/history",
    savedCards: "/api/v1/payments/saved-cards"
  },
  catalog: {
    list: "/api/v1/products",
    packages: "/api/v1/products/packages",
    active: "/api/v1/products/active",
    getActive: "/api/v1/offers/active",
    recommendations: "/api/v1/offers/recommendations"
  },
  content: {
    articles: "/api/v1/content/articles",
    banners: "/api/v1/content/banners",
    faqs: "/api/v1/content/faqs"
  },
  notifications: {
    list: "/api/v1/notifications"
  },
  profile: {
    summary: "/api/v1/me",
    connections: "/api/v1/me/connections"
  },
  support: {
    tickets: "/api/v1/support/tickets",
    ticket: "/api/v1/support/tickets/{ticketId}",
    create: "/api/v1/support/tickets",
    status: "/api/v1/support/tickets/{ticketId}/status"
  },
  ai: {
    chat: "/api/v1/ai/chat",
    chatStream: "/api/v1/ai/chat/stream"
  },
  loyalty: {
    points: "/api/v1/loyalty/points",
    history: "/api/v1/loyalty/transactions",
    redeem: "/api/v1/loyalty/redeem"
  },
  recharge: {
    plans: "/api/v1/recharge/plans",
    submit: "/api/v1/recharge"
  },
  shareCredit: {
    submit: "/api/v1/account/share-credit"
  },
  dataLoan: {
    active: "/api/v1/account/data-loan",
    request: "/api/v1/account/data-loan/request"
  }
};

// Data-source catalog — maps dataSource ids used by layout sections to service
// endpoint configs. Compiled + signed into the manifest; the app's
// dataSourceResolver reads these mappings (service namespace on ApiHandle +
// endpoint + params/transform). Adding a data source is a DB-level change.
const DATA_SOURCES = {
  "balance.current":   { service: "bills",   endpoint: "current",          method: "GET", params: { tenantId: "{tenantId}" } },
  "billing.recent":    { service: "bills",   endpoint: "list",             method: "GET", params: { tenantId: "{tenantId}", status: "NEW", limit: 3  }, transform: "extract.data" },
  "billing.current":   { service: "bills",   endpoint: "current",          method: "GET", params: { tenantId: "{tenantId}" } },
  "billing.history":   { service: "bills",   endpoint: "list",             method: "GET", params: { tenantId: "{tenantId}", limit: 12 }, transform: "extract.data" },
  "billing.ebillStatus": { service: "bills", endpoint: "current",          method: "GET", params: { tenantId: "{tenantId}" } },
  "usage.summary":     { service: "usage",   endpoint: "current",          method: "GET", params: { tenantId: "{tenantId}" } },
  "usage.history":     { service: "usage",   endpoint: "history",          method: "GET", params: { tenantId: "{tenantId}", connectionId: "{connectionId}" }, transform: "extract.data" },
  "usage.creditLimit": { service: "usage",   endpoint: "creditLimit",      method: "GET", params: { tenantId: "{tenantId}", connectionId: "{connectionId}" } },
  "product.activePackage": { service: "catalog", endpoint: "getActive",    method: "GET", params: { tenantId: "{tenantId}" } },
  "product.activePackages": { service: "catalog", endpoint: "active",      method: "GET", params: { tenantId: "{tenantId}", connectionId: "{connectionId}" }, transform: "extract.data" },
  "product.quickAddons": { service: "catalog", endpoint: "packages",       method: "GET", params: { tenantId: "{tenantId}", connectionId: "{connectionId}", lob: "DATA" }, transform: "extract.data" },
  "product.recommended":   { service: "catalog", endpoint: "recommendations", method: "GET", params: { tenantId: "{tenantId}" }, transform: "extract.data" },
  "product.dataPacks":     { service: "catalog", endpoint: "list",         method: "GET", params: { tenantId: "{tenantId}", category: "DATA" },  transform: "extract.data" },
  "product.voicePacks":    { service: "catalog", endpoint: "list",         method: "GET", params: { tenantId: "{tenantId}", category: "VOICE" }, transform: "extract.data" },
  "product.comboPacks":    { service: "catalog", endpoint: "list",         method: "GET", params: { tenantId: "{tenantId}", category: "COMBO" }, transform: "extract.data" },
  "content.banners":   { service: "content", endpoint: "banners",          method: "GET", params: { tenantId: "{tenantId}", type: "banner" }, transform: "extract.data" },
  "content.miniBanner": { service: "content", endpoint: "banners",         method: "GET", params: { tenantId: "{tenantId}", position: "MINI" }, transform: "extract.data" },
  "content.appGallery": { service: "content", endpoint: "banners",         method: "GET", params: { tenantId: "{tenantId}", position: "APP_GALLERY" }, transform: "extract.data" },
  "content.otherServices": { service: "content", endpoint: "banners",      method: "GET", params: { tenantId: "{tenantId}", position: "OTHER_SERVICES" }, transform: "extract.data" },
  "content.faqs":      { service: "content", endpoint: "faqs",             method: "GET", params: { tenantId: "{tenantId}" }, transform: "extract.data" },
  "content.settings":  { service: "content", endpoint: "faqs",             method: "GET", params: { tenantId: "{tenantId}" }, transform: "extract.data" },
  "content.videos":    { service: "content", endpoint: "articles",         method: "GET", params: { tenantId: "{tenantId}" }, transform: "extract.data" },
  "notifications.recent": { service: "notifications", endpoint: "list",    method: "GET", params: { tenantId: "{tenantId}", limit: 3 }, transform: "extract.data" },
  "payment.savedCards":  { service: "payments", endpoint: "savedCards",    method: "GET", params: { tenantId: "{tenantId}" }, transform: "extract.data" },
  "payment.history":     { service: "payments", endpoint: "history",       method: "GET", params: { tenantId: "{tenantId}", limit: 20 }, transform: "extract.data" },
  "profile.summary":     { service: "profile",  endpoint: "summary",       method: "GET", params: { tenantId: "{tenantId}" } },
  "connections.list":    { service: "profile",  endpoint: "connections",   method: "GET", params: { tenantId: "{tenantId}" }, transform: "extract.data" },
  "support.tickets":     { service: "support",  endpoint: "tickets",       method: "GET", params: { tenantId: "{tenantId}", connectionId: "{connectionId}" }, transform: "extract.data" },
  "product.roamingPacks":  { service: "catalog", endpoint: "packages",     method: "GET", params: { tenantId: "{tenantId}", category: "ROAMING" }, transform: "extract.data" },
  "product.iddPacks":      { service: "catalog", endpoint: "packages",     method: "GET", params: { tenantId: "{tenantId}", category: "IDD" }, transform: "extract.data" },
  "product.vasAddons":     { service: "catalog", endpoint: "packages",     method: "GET", params: { tenantId: "{tenantId}", lob: "VAS" }, transform: "extract.data" },
  "product.dtvPacks":      { service: "catalog", endpoint: "packages",     method: "GET", params: { tenantId: "{tenantId}", lob: "DTV" }, transform: "extract.data" },
  "product.rechargePlans": { service: "recharge", endpoint: "plans",       method: "GET", params: { tenantId: "{tenantId}" }, transform: "extract.data" },
  "usage.roaming":         { service: "usage",   endpoint: "roaming",      method: "GET", params: { tenantId: "{tenantId}", connectionId: "{connectionId}" } },
  "loyalty.points":        { service: "loyalty", endpoint: "points",       method: "GET", params: { tenantId: "{tenantId}", connectionId: "{connectionId}" } },
  "loyalty.history":       { service: "loyalty", endpoint: "history",      method: "GET", params: { tenantId: "{tenantId}", connectionId: "{connectionId}" }, transform: "extract.data" },
  "loyalty.rewards":       { service: "catalog", endpoint: "packages",     method: "GET", params: { tenantId: "{tenantId}", lob: "REWARDS" }, transform: "extract.data" },
  "data.loan.active":      { service: "dataLoan", endpoint: "active",      method: "GET", params: { tenantId: "{tenantId}", connectionId: "{connectionId}" } },
  "notifications.all":     { service: "notifications", endpoint: "list",   method: "GET", params: { tenantId: "{tenantId}" }, transform: "extract.data" }
};

function layoutSections(exp) {
  const base = {
    home: [
      // Mirrors the legacy midend DashboardView home layout (homeViewLayout.json):
      // homeConnections -> my-connections, balance/bill -> hero-balance,
      // package-details -> getBasePackageDetail, quick add-ons -> quick-addon-and-reload,
      // credit-limit -> /mydialogcl/credit-limit, banners -> homeOfferBanner,
      // mini-banner -> getMiniBannerConfig, app-gallery -> getOtherAppsInfo,
      // other-services -> getOtherServices, full-width-pack-detail -> getFullWidthDetail,
      // usage-hyperlink -> dash-usage-hyperlink.
      { id: "my-connections", component: "ConnectionSwitcher", variant: "connection-list", dataSource: "connections.list", props: {}, order: 1 },
      { id: "hero-balance", component: "BalanceCard", variant: "hero", dataSource: "balance.current", props: { showOutstanding: true, showDueDate: true }, order: 2 },
      { id: "package-details", component: "PackageDetailCard", variant: "default", dataSource: "product.activePackages", props: { showAutoRenew: true }, order: 3 },
      { id: "usage-summary", component: "UsageSummary", variant: "hero", dataSource: "usage.summary", props: { showData: true, showVoice: true, showSms: true }, order: 4 },
      { id: "quick-addon-and-reload", component: "QuickAddonList", variant: "default", dataSource: "product.quickAddons", props: { showPrice: true, showValidity: true }, order: 5 },
      { id: "credit-limit", component: "CreditLimitCard", variant: "default", dataSource: "usage.creditLimit", props: {}, order: 6 },
      { id: "recent-bills", component: "BillCard", variant: "compact", dataSource: "billing.recent", props: { limit: 3 }, order: 7 },
      { id: "banners", component: "BannersCarousel", variant: "default", dataSource: "content.banners", props: { autoPlay: true, interval: 5000 }, order: 8 },
      { id: "notifications-preview", component: "NotificationsList", variant: "compact", dataSource: "notifications.recent", props: { limit: 3 }, order: 9 },
      // Midend-parity rows 2 (getMiniBannerConfig, getOtherAppsInfo, getOtherServices,
      // getFullWidthDetail, dash-usage-hyperlink) — config-driven home sections.
      { id: "mini-banner", component: "MiniBanner", variant: "default", dataSource: "content.miniBanner", props: { position: "MINI" }, order: 10 },
      { id: "app-gallery", component: "AppGalleryGrid", variant: "default", dataSource: "content.appGallery", props: { columns: 3 }, order: 11 },
      { id: "other-services", component: "OtherServicesGrid", variant: "default", dataSource: "content.otherServices", props: { columns: 2 }, order: 12 },
      { id: "full-width-pack-detail", component: "FullWidthPackageDetail", variant: "full", dataSource: "product.activePackages", props: { fullWidth: true }, order: 13 },
      { id: "usage-hyperlink", component: "UsageHyperlink", variant: "link", dataSource: null, props: { label: "View usage history", route: "/usage/history" }, order: 14 }
    ],
    packages: [
      { id: "active-pack", component: "PackageCard", variant: "highlighted", dataSource: "product.activePackage", props: { showDetails: true }, order: 1 },
      { id: "recommended", component: "BundlesList", variant: "default", dataSource: "product.recommended", props: { showPrice: true, showValidity: true }, order: 2 },
      { id: "data-packs", component: "BundlesList", variant: "category", dataSource: "product.dataPacks", props: { category: "DATA" }, order: 3 },
      { id: "voice-packs", component: "BundlesList", variant: "category", dataSource: "product.voicePacks", props: { category: "VOICE" }, order: 4 },
      { id: "combo-packs", component: "BundlesList", variant: "category", dataSource: "product.comboPacks", props: { category: "COMBO" }, order: 5 }
    ],
    bills: [
      { id: "current-bill", component: "BillCard", variant: "hero", dataSource: "billing.current", props: { showDueDate: true, showPayButton: true }, order: 1 },
      { id: "bill-history", component: "BillCard", variant: "list", dataSource: "billing.history", props: { limit: 12 }, order: 2 },
      { id: "ebill-toggle", component: "GenericToggle", variant: "default", dataSource: "billing.ebillStatus", props: { label: "E-Bill" }, order: 3 }
    ],
    payments: [
      { id: "pay-button", component: "PayButton", variant: "hero", dataSource: null, props: { methods: ["card", "ezcash", "starpoints", "bank"] }, order: 1 },
      { id: "saved-cards", component: "PaymentCard", variant: "list", dataSource: "payment.savedCards", props: { showDefault: true }, order: 2 },
      { id: "payment-history", component: "PaymentCard", variant: "history", dataSource: "payment.history", props: { limit: 20 }, order: 3 }
    ],
    usage: [
      { id: "usage-summary", component: "UsageSummary", variant: "hero", dataSource: "usage.summary", props: { showData: true, showVoice: true, showSms: true }, order: 1 },
      { id: "usage-history", component: "UsageChart", variant: "default", dataSource: "usage.history", props: { limit: 30 }, order: 2 }
    ],
    profile: [
      { id: "profile-header", component: "ProfileHeader", variant: "default", dataSource: "profile.summary", props: {}, order: 1 },
      { id: "connection-switcher", component: "ConnectionSwitcher", variant: "default", dataSource: "connections.list", props: {}, order: 2 },
      { id: "settings-list", component: "GenericList", variant: "settings", dataSource: "content.settings", props: {}, order: 3 }
    ],
    support: [
      { id: "my-requests", component: "ServiceRequestsList", variant: "default", dataSource: "support.tickets", props: {}, order: 1 },
      { id: "support-tile", component: "SupportTile", variant: "default", dataSource: null, props: { channels: ["chat", "call", "email"] }, order: 2 },
      { id: "faq-list", component: "GenericList", variant: "faq", dataSource: "content.faqs", props: { category: "general" }, order: 3 },
      { id: "how-to-videos", component: "GenericList", variant: "videos", dataSource: "content.videos", props: { limit: 5 }, order: 4 }
    ],
    connections: [
      { id: "profile-header", component: "ProfileHeader", variant: "compact", dataSource: "profile.summary", props: {}, order: 1 },
      { id: "my-connections", component: "ConnectionSwitcher", variant: "default", dataSource: "connections.list", props: { showTitle: true }, order: 2 },
      { id: "linked-numbers", component: "GenericList", variant: "connections", dataSource: "connections.list", props: {}, order: 3 },
      { id: "add-connection", component: "GenericBanner", variant: "default", dataSource: null, props: { message: "Add a connection", route: "/support" }, order: 4 }
    ],
    recharge: [
      { id: "recharge-plans", component: "QuickAddonList", variant: "default", dataSource: "product.rechargePlans", props: { showPrice: true, showValidity: true }, order: 1 },
      { id: "pay-method", component: "PayButton", variant: "hero", dataSource: null, props: { methods: ["card", "ezcash", "starpoints", "bank", "qr"] }, order: 2 },
      { id: "saved-cards", component: "PaymentCard", variant: "list", dataSource: "payment.savedCards", props: { showDefault: true }, order: 3 },
      { id: "recharge-history", component: "PaymentCard", variant: "history", dataSource: "payment.history", props: { limit: 10 }, order: 4 }
    ],
    roaming: [
      { id: "roaming-status", component: "GenericCard", variant: "status", dataSource: "usage.roaming", props: {}, order: 1 },
      { id: "roaming-packs", component: "BundlesList", variant: "default", dataSource: "product.roamingPacks", props: { showPrice: true, showValidity: true }, order: 2 },
      { id: "roaming-help", component: "SupportTile", variant: "roaming", dataSource: null, props: { channels: ["call", "chat"] }, order: 3 }
    ],
    idd: [
      { id: "idd-rates", component: "BundlesList", variant: "category", dataSource: "product.iddPacks", props: { category: "IDD" }, order: 1 },
      { id: "idd-help", component: "GenericEmptyState", variant: "default", dataSource: null, props: { title: "Need help?", message: "See IDD destinations and rates" }, order: 2 }
    ],
    loyalty: [
      { id: "starpoints-balance", component: "GenericCard", variant: "points", dataSource: "loyalty.points", props: { showPoints: true }, order: 1 },
      { id: "redeem-options", component: "BundlesList", variant: "default", dataSource: "loyalty.rewards", props: { showPrice: true }, order: 2 },
      { id: "points-history", component: "GenericList", variant: "history", dataSource: "loyalty.history", props: {}, order: 3 }
    ],
    vas: [
      { id: "vas-catalog", component: "BundlesList", variant: "default", dataSource: "product.vasAddons", props: { showPrice: true, showValidity: true }, order: 1 },
      { id: "vas-banner", component: "MiniBanner", variant: "default", dataSource: "content.miniBanner", props: {}, order: 2 }
    ],
    "share-credit": [
      { id: "share-form", component: "GenericForm", variant: "share", dataSource: null, props: { fields: ["mobileNo", "amount"], action: { type: "CALL_API", service: "shareCredit", endpoint: "submit" } }, order: 1 },
      { id: "share-banner", component: "GenericBanner", variant: "default", dataSource: null, props: { message: "Share credit with friends and family" }, order: 2 }
    ],
    "data-loan": [
      { id: "loan-status", component: "GenericCard", variant: "status", dataSource: "data.loan.active", props: {}, order: 1 },
      { id: "loan-request", component: "GenericForm", variant: "loan", dataSource: null, props: { fields: ["amount"], action: { type: "CALL_API", service: "dataLoan", endpoint: "request" } }, order: 2 }
    ],
    dtv: [
      { id: "dtv-packs", component: "BundlesList", variant: "default", dataSource: "product.dtvPacks", props: { showPrice: true }, order: 1 },
      { id: "dtv-featured", component: "FullWidthPackageDetail", variant: "full", dataSource: "product.activePackages", props: {}, order: 2 },
      { id: "dtv-channels", component: "GenericList", variant: "channels", dataSource: "content.videos", props: {}, order: 3 }
    ],
    notifications: [
      { id: "notifications-list", component: "NotificationsList", variant: "default", dataSource: "notifications.all", props: {}, order: 1 },
      { id: "notification-settings", component: "GenericToggle", variant: "channels", dataSource: null, props: { channels: ["push", "sms", "email", "inApp"] }, order: 2 }
    ],
    settings: [
      { id: "profile-header", component: "ProfileHeader", variant: "compact", dataSource: "profile.summary", props: {}, order: 0 },
      { id: "pref-biometric", component: "GenericToggle", variant: "default", dataSource: null, props: { label: "Biometric login", flag: "biometric.login.enabled" }, order: 1 },
      { id: "pref-dark-mode", component: "GenericToggle", variant: "default", dataSource: null, props: { label: "Dark mode", flag: "dashboard.dark-mode.enabled" }, order: 2 },
      { id: "pref-push", component: "GenericToggle", variant: "default", dataSource: null, props: { label: "Push notifications", flag: "notifications.push.enabled" }, order: 3 },
      { id: "pref-sms", component: "GenericToggle", variant: "default", dataSource: null, props: { label: "SMS notifications", flag: "notifications.sms.enabled" }, order: 4 },
      { id: "settings-more", component: "GenericList", variant: "settings", dataSource: "content.settings", props: {}, order: 5 }
    ],
    ai: [
      { id: "ai-assistant-entry", component: "AIAssistantEntry", variant: "hero", dataSource: null, props: { greeting: "How can I help you today?" }, order: 1 },
      { id: "ai-recent-chats", component: "GenericList", variant: "chats", dataSource: "notifications.all", props: {}, order: 2 },
      { id: "ai-disclaimer", component: "GenericBanner", variant: "info", dataSource: null, props: { message: "AI answers are generated and not official advice." }, order: 3 }
    ]
  };
  return base[exp] || [];
}

envs.forEach(env => {
  experiences.forEach(exp => {
    const docId = `dialog-${env}-${exp}-1`;
    db.layout_documents.updateOne(
      { tenantId: TENANT, environment: env, experience: exp, profileKey: profileKey },
      {
        $set: {
          tenantId: TENANT,
          environment: env,
          experience: exp,
          profileKey: profileKey,
          schemaVersion: "2.0",
          configVersion: 1,
          compatibility: { platforms: ["android", "ios", "huawei"], minAppVersion: "7.0.0", maxAppVersion: null },
          themeRef: "dialog-default@1.0.0",
          navigationRef: "dialog-main@1",
          services: SERVICES,
          dataSources: DATA_SOURCES,
          sections: layoutSections(exp).map(s => ({
            ...s,
            visibleWhen: null,
            actions: [],
            states: { timeout: "inlineRetry", unavailable: "hide" },
            analytics: { impression: `${exp}_${s.id}_impression` }
          })),
          status: "PUBLISHED",
          publishedBy: "admin@selfcare.lk",
          publishedAt: ISO(),
          updatedAt: ISO()
        },
        $setOnInsert: {
          _id: docId,
          createdBy: "system",
          createdAt: ISO()
        }
      },
      { upsert: true }
    );
  });
});
print("  ✓ layout_documents " + (experiences.length * envs.length) + " layouts (" + experiences.length + " exp × " + envs.length + " env)");

// ────────────────────────────────────────────────────────────────────────────
// 4b. DASHBOARD LAYOUTS (dashboard_layouts collection)
//     Dashboard BFF widget fan-out config — the platform analogue of the legacy
//     midend DashboardView home layout (homeViewLayout.json). Widget ORDER here
//     drives which widgets the dashboard-bff executes and in what sequence;
//     widget ids must exist in the Dashboard WidgetProvider registry. The
//     tenant-level row is the fallback when no profile row matches.
// ────────────────────────────────────────────────────────────────────────────
const DASHBOARD_LAYOUTS = [
  { profileKey: null,           widgetOrder: ["balance", "usage", "package-details", "quick-addon-and-reload", "bill", "quick-actions", "bundles", "banners", "notifications"] },
  { profileKey: "PREPAID",      widgetOrder: ["balance", "usage", "quick-addon-and-reload", "package-details", "bill", "quick-actions", "bundles", "notifications", "banners"] },
  { profileKey: "POSTPAID",     widgetOrder: ["bill", "balance", "usage", "package-details", "quick-addon-and-reload", "quick-actions", "bundles", "notifications", "banners"] },
  { profileKey: "MBB",          widgetOrder: ["balance", "usage", "quick-addon-and-reload", "bill", "package-details", "quick-actions", "notifications", "banners"] },
  { profileKey: "BB",           widgetOrder: ["balance", "usage", "bill", "package-details", "quick-actions", "bundles", "notifications", "banners"] },
  { profileKey: "DTV",          widgetOrder: ["bill", "balance", "package-details", "bundles", "quick-actions", "notifications", "banners"] },
  { profileKey: "FIBRE",        widgetOrder: ["balance", "usage", "bill", "package-details", "quick-actions", "bundles", "notifications", "banners"] },
  { profileKey: "PREPAID_DATA", widgetOrder: ["balance", "usage", "quick-addon-and-reload", "package-details", "quick-actions", "bill", "notifications", "banners"] },
  { profileKey: "POSTPAID_DATA",widgetOrder: ["bill", "balance", "usage", "package-details", "quick-addon-and-reload", "quick-actions", "notifications", "banners"] },
  { profileKey: "MBB_DATA",     widgetOrder: ["balance", "usage", "quick-addon-and-reload", "bill", "quick-actions", "notifications", "banners"] },
  { profileKey: "CORPORATE",    widgetOrder: ["bill", "balance", "usage", "bundles", "quick-actions", "notifications", "banners"] },
  { profileKey: "ROAMING",      widgetOrder: ["balance", "usage", "package-details", "bundles", "quick-actions", "notifications", "banners"] }
];

DASHBOARD_LAYOUTS.forEach((layout, i) => {
  db.dashboard_layouts.updateOne(
    { tenantId: TENANT, profileKey: layout.profileKey, active: true },
    {
      $set: {
        tenantId: TENANT,
        profileKey: layout.profileKey,
        environment: "dev",
        active: true,
        status: "PUBLISHED",
        widgetOrder: layout.widgetOrder,
        updatedAt: ISO()
      },
      $setOnInsert: {
        _id: "dialog-default-" + i,
        createdBy: "system",
        createdAt: ISO()
      }
    },
    { upsert: true }
  );
});
print("  ✓ dashboard_layouts " + DASHBOARD_LAYOUTS.length + " layouts (" + (DASHBOARD_LAYOUTS.length - 1) + " profiles + default fallback)");

// ────────────────────────────────────────────────────────────────────────────
// 5. COMPONENT CATALOG (component_catalog collection)
//    45 components — each maps to a Universal primitive + immutable config recipe.
//    The app resolves componentId -> primitive via the compiled manifest; NO
//    feature-specific code ships in the app (ADR-009).
// ────────────────────────────────────────────────────────────────────────────
const components = [
  { _id: "BalanceCard", category: "ACCOUNT", platforms: ["android","ios","huawei","web"], security: "read", minVersion: "1.0.0",
    primitive: "UniversalBox",
    config: { renderType: "card", variant: "primary", elevation: "md", padding: "lg", borderRadius: "lg",
      children: [
        { primitive: "UniversalText", config: { variant: "label", value: { path: "balance.label" } } },
        { primitive: "UniversalText", config: { variant: "value", value: { path: "balance.amount" }, format: "currency" } },
        { primitive: "UniversalText", config: { variant: "caption", value: { template: "Due {balance.dueDate}" }, color: "secondary" } }
      ] } },
  { _id: "UsageCard", category: "ACCOUNT", platforms: ["android","ios","huawei","web"], security: "read", minVersion: "1.0.0",
    primitive: "UniversalBox",
    config: { renderType: "card", elevation: "sm", padding: "lg",
      children: [
        { primitive: "UniversalText", config: { variant: "value", value: { path: "usage.data.used" }, format: "bytes" } },
        { primitive: "UniversalText", config: { variant: "caption", value: { path: "usage.data.total" }, format: "bytes" } }
      ] } },
  { _id: "BillCard", category: "BILLING", platforms: ["android","ios","huawei","web"], security: "read", minVersion: "1.0.0",
    primitive: "UniversalBox",
    config: { renderType: "card", elevation: "sm", padding: "md",
      children: [
        { primitive: "UniversalText", config: { variant: "value", value: { path: "bill.amount" }, format: "currency" } },
        { primitive: "UniversalText", config: { variant: "caption", value: { path: "bill.dueDate" }, format: "date" } },
        { primitive: "UniversalText", config: { variant: "caption", value: { path: "bill.status" }, color: "secondary" } }
      ] } },
  { _id: "BundlesList", category: "CATALOG", platforms: ["android","ios","huawei","web"], security: "display", minVersion: "1.0.0",
    primitive: "UniversalList",
    config: { layout: "vertical", pullToRefresh: true,
      item: { primitive: "UniversalBox", config: { renderType: "card", padding: "md",
        children: [
          { primitive: "UniversalText", config: { variant: "subtitle", value: { path: "name" } } },
          { primitive: "UniversalText", config: { variant: "caption", value: { path: "price" }, format: "currency" } },
          { primitive: "UniversalText", config: { variant: "caption", value: { path: "validity" } } }
        ] } } } },
  { _id: "QuickActionsGrid", category: "NAVIGATION", platforms: ["android","ios","huawei","web"], security: "display", minVersion: "1.0.0",
    primitive: "UniversalGrid",
    config: { columns: 4, gap: "md",
      item: { primitive: "UniversalBox", config: { renderType: "card", alignItems: "center", padding: "sm",
        children: [
          { primitive: "UniversalImage", config: { shape: "icon", source: { path: "iconUrl" } } },
          { primitive: "UniversalText", config: { variant: "caption", value: { path: "label" }, align: "center" } }
        ] } } } },
  { _id: "NotificationsList", category: "ENGAGEMENT", platforms: ["android","ios","huawei","web"], security: "read", minVersion: "1.0.0",
    primitive: "UniversalList",
    config: { layout: "vertical", separator: true,
      item: { primitive: "UniversalBox", config: { renderType: "surface", padding: "sm",
        children: [
          { primitive: "UniversalText", config: { variant: "body", value: { path: "message" } } },
          { primitive: "UniversalText", config: { variant: "caption", value: { path: "createdAt" }, format: "relative-time", color: "secondary" } }
        ] } } } },
  { _id: "BannersCarousel", category: "CONTENT", platforms: ["android","ios","huawei","web"], security: "display", minVersion: "1.0.0",
    primitive: "UniversalList",
    config: { layout: "horizontal",
      item: { primitive: "UniversalImage", config: { shape: "banner", source: { path: "imageUrl" }, height: 180 } } } },
  { _id: "PayButton", category: "PAYMENT", platforms: ["android","ios","huawei","web"], security: "write", minVersion: "1.0.0",
    primitive: "UniversalButton",
    config: { variant: "primary", size: "lg", label: "Pay now", action: { type: "PAYMENT" } } },
  { _id: "SupportTile", category: "SUPPORT", platforms: ["android","ios","huawei","web"], security: "display", minVersion: "1.0.0",
    primitive: "UniversalBox",
    config: { renderType: "card", padding: "md",
      children: [
        { primitive: "UniversalText", config: { variant: "subtitle", value: { path: "label" } } },
        { primitive: "UniversalButton", config: { variant: "outline", label: { path: "actionLabel" }, action: { type: "NAVIGATE", route: "/support" } } }
      ] } },
  { _id: "ProfileHeader", category: "ACCOUNT", platforms: ["android","ios","huawei","web"], security: "read", minVersion: "1.0.0",
    primitive: "UniversalBox",
    config: { renderType: "card", padding: "lg",
      children: [
        { primitive: "UniversalImage", config: { shape: "circle", source: { path: "profile.avatarUrl" }, size: 64 } },
        { primitive: "UniversalText", config: { variant: "title", value: { path: "profile.name" } } },
        { primitive: "UniversalText", config: { variant: "caption", value: { path: "profile.mobileNo" }, color: "secondary" } }
      ] } },
  { _id: "ConnectionSwitcher", category: "ACCOUNT", platforms: ["android","ios","huawei","web"], security: "read", minVersion: "1.0.0",
    primitive: "UniversalList",
    config: { layout: "vertical",
      item: { primitive: "UniversalBox", config: { renderType: "surface", padding: "sm",
        children: [ { primitive: "UniversalText", config: { variant: "body", value: { path: "label" } } } ] } } } },
  { _id: "DataTopupCard", category: "CATALOG", platforms: ["android","ios","huawei","web"], security: "write", minVersion: "1.0.0",
    primitive: "UniversalBox",
    config: { renderType: "card", padding: "lg",
      children: [
        { primitive: "UniversalInput", config: { type: "number", label: "Recharge amount" } },
        { primitive: "UniversalButton", config: { variant: "primary", label: "Recharge", action: { type: "CALL_API", journeyId: "recharge" } } }
      ] } },
  { _id: "UsageSummary", category: "ACCOUNT", platforms: ["android","ios","huawei","web"], security: "read", minVersion: "1.0.0",
    primitive: "UniversalBox",
    config: { renderType: "card", variant: "surface", elevation: "sm", padding: "lg",
      children: [
        { primitive: "UniversalText", config: { variant: "title", value: { path: "usage.summary.period" } } },
        { primitive: "UniversalText", config: { variant: "value", value: { path: "usage.summary.data.used" }, format: "bytes" } },
        { primitive: "UniversalText", config: { variant: "body", value: { path: "usage.summary.voice.used" } } }
      ] } },
  { _id: "UsageChart", category: "ACCOUNT", platforms: ["android","ios","huawei","web"], security: "read", minVersion: "1.0.0",
    primitive: "UniversalChart",
    config: { type: "line", seriesPath: "items", labelPath: "period.label", valuePath: "data.remainingBytes", showLegend: false, showValues: false } },
  { _id: "PaymentCard", category: "PAYMENT", platforms: ["android","ios","huawei","web"], security: "read", minVersion: "1.0.0",
    primitive: "UniversalBox",
    config: { renderType: "card", padding: "md",
      children: [
        { primitive: "UniversalText", config: { variant: "body", value: { path: "card.label" } } },
        { primitive: "UniversalText", config: { variant: "caption", value: { path: "card.numberMasked" } } }
      ] } },
  { _id: "PackageCard", category: "CATALOG", platforms: ["android","ios","huawei","web"], security: "display", minVersion: "1.0.0",
    primitive: "UniversalBox",
    config: { renderType: "card", variant: "accent", elevation: "md", padding: "lg",
      children: [
        { primitive: "UniversalText", config: { variant: "title", value: { path: "package.name" } } },
        { primitive: "UniversalText", config: { variant: "value", value: { path: "package.price" }, format: "currency" } },
        { primitive: "UniversalButton", config: { variant: "primary", label: "Select", action: { type: "NAVIGATE", route: "/packages" } } }
      ] } },
  { _id: "OfferCarousel", category: "CATALOG", platforms: ["android","ios","huawei","web"], security: "display", minVersion: "1.0.0",
    primitive: "UniversalList",
    config: { layout: "horizontal",
      item: { primitive: "UniversalBox", config: { renderType: "card", padding: "md",
        children: [
          { primitive: "UniversalText", config: { variant: "subtitle", value: { path: "title" } } },
          { primitive: "UniversalText", config: { variant: "caption", value: { path: "description" } } }
        ] } } } },
  { _id: "NotificationCard", category: "ENGAGEMENT", platforms: ["android","ios","huawei","web"], security: "read", minVersion: "1.0.0",
    primitive: "UniversalBox",
    config: { renderType: "card", padding: "md",
      children: [
        { primitive: "UniversalText", config: { variant: "body", value: { path: "message" } } },
        { primitive: "UniversalText", config: { variant: "caption", value: { path: "createdAt" }, format: "relative-time" } }
      ] } },
  { _id: "SupportCard", category: "SUPPORT", platforms: ["android","ios","huawei","web"], security: "display", minVersion: "1.0.0",
    primitive: "UniversalBox",
    config: { renderType: "card", padding: "md",
      children: [ { primitive: "UniversalText", config: { variant: "body", value: { path: "message" } } } ] } },
  { _id: "Banner", category: "CONTENT", platforms: ["android","ios","huawei","web"], security: "display", minVersion: "1.0.0",
    primitive: "UniversalBox",
    config: { renderType: "card", variant: "gradient", padding: "lg",
      children: [
        { primitive: "UniversalImage", config: { shape: "banner", source: { path: "imageUrl" }, height: 140 } },
        { primitive: "UniversalText", config: { variant: "subtitle", value: { path: "title" } } }
      ] } },
  { _id: "AIAssistantEntry", category: "AI", platforms: ["android","ios","huawei","web"], security: "read", minVersion: "7.0.0",
    primitive: "UniversalBox",
    config: { renderType: "card", variant: "gradient", padding: "md", onPress: { type: "NAVIGATE", route: "/ai" },
      children: [
        { primitive: "UniversalText", config: { variant: "subtitle", value: "AI Assistant" } },
        { primitive: "UniversalText", config: { variant: "caption", value: { path: "greeting" }, color: "secondary" } }
      ] } },
  { _id: "GenericHeader", category: "LAYOUT", platforms: ["android","ios","huawei","web"], security: "display", minVersion: "1.0.0",
    primitive: "UniversalBox",
    config: { renderType: "surface", padding: "md",
      children: [ { primitive: "UniversalText", config: { variant: "title", value: { path: "title" } } } ] } },
  { _id: "GenericList", category: "LAYOUT", platforms: ["android","ios","huawei","web"], security: "display", minVersion: "1.0.0",
    primitive: "UniversalList",
    config: { layout: "vertical",
      item: { primitive: "UniversalBox", config: { renderType: "surface", padding: "sm",
        children: [ { primitive: "UniversalText", config: { variant: "body", value: { path: "label" } } } ] } } } },
  { _id: "ServiceRequestsList", category: "SUPPORT", platforms: ["android","ios","huawei","web"], security: "read", minVersion: "1.0.0",
    primitive: "UniversalList",
    config: { layout: "vertical", separator: true,
      item: { primitive: "UniversalBox", config: { renderType: "surface", padding: "sm",
        children: [
          { primitive: "UniversalText", config: { variant: "body", value: { path: "subject" } } },
          { primitive: "UniversalText", config: { variant: "caption", value: { path: "status" } } },
          { primitive: "UniversalText", config: { variant: "caption", value: { path: "createdAt" }, format: "relative-time", color: "secondary" } }
        ] } } } },
  { _id: "GenericAction", category: "LAYOUT", platforms: ["android","ios","huawei","web"], security: "write", minVersion: "1.0.0",
    primitive: "UniversalButton",
    config: { variant: "secondary", label: { path: "label" }, action: { type: "NAVIGATE" } } },
  { _id: "GenericForm", category: "LAYOUT", platforms: ["android","ios","huawei","web"], security: "write", minVersion: "1.0.0",
    primitive: "UniversalBox",
    config: { renderType: "surface", padding: "lg",
      children: [
        { primitive: "UniversalInput", config: { type: "text" } },
        { primitive: "UniversalButton", config: { variant: "primary", label: "Submit", action: { type: "CALL_API" } } }
      ] } },
  { _id: "GenericCard", category: "LAYOUT", platforms: ["android","ios","huawei","web"], security: "display", minVersion: "1.0.0",
    primitive: "UniversalBox",
    config: { renderType: "card", padding: "md",
      children: [ { primitive: "UniversalText", config: { variant: "body", value: { path: "body" } } } ] } },
  { _id: "GenericBanner", category: "LAYOUT", platforms: ["android","ios","huawei","web"], security: "display", minVersion: "1.0.0",
    primitive: "UniversalBox",
    config: { renderType: "card", variant: "accent", padding: "md",
      children: [ { primitive: "UniversalText", config: { variant: "body", value: { path: "message" } } } ] } },
  { _id: "GenericEmptyState", category: "LAYOUT", platforms: ["android","ios","huawei","web"], security: "display", minVersion: "1.0.0",
    primitive: "UniversalBox",
    config: { renderType: "surface", alignItems: "center", padding: "xl",
      children: [
        { primitive: "UniversalText", config: { variant: "title", value: { path: "title" }, align: "center" } },
        { primitive: "UniversalText", config: { variant: "body", value: { path: "message" }, align: "center", color: "secondary" } }
      ] } },
  { _id: "GenericToggle", category: "LAYOUT", platforms: ["android","ios","huawei","web"], security: "write", minVersion: "1.0.0",
    primitive: "UniversalInput",
    config: { type: "switch", label: { path: "label" } } },
  { _id: "AccountHeader", category: "ACCOUNT", platforms: ["android","ios","huawei","web"], security: "read", minVersion: "1.0.0",
    primitive: "UniversalBox",
    config: { renderType: "card", padding: "lg",
      children: [
        { primitive: "UniversalText", config: { variant: "title", value: { path: "account.name" } } },
        { primitive: "UniversalText", config: { variant: "caption", value: { path: "account.connectionType" }, color: "secondary" } }
      ] } },
  { _id: "InsurancePolicyCard", category: "INSURANCE", platforms: ["android","ios","huawei","web"], security: "read", minVersion: "1.0.0",
    primitive: "UniversalBox",
    config: { renderType: "card", padding: "lg",
      children: [
        { primitive: "UniversalText", config: { variant: "title", value: { path: "policy.policyNumber" } } },
        { primitive: "UniversalText", config: { variant: "body", value: { path: "policy.status" } } },
        { primitive: "UniversalText", config: { variant: "caption", value: { path: "policy.premium" }, format: "currency" } }
      ] } },
  { _id: "InsuranceClaimCard", category: "INSURANCE", platforms: ["android","ios","huawei","web"], security: "read", minVersion: "1.0.0",
    primitive: "UniversalBox",
    config: { renderType: "card", padding: "md",
      children: [
        { primitive: "UniversalText", config: { variant: "body", value: { path: "claim.claimNumber" } } },
        { primitive: "UniversalText", config: { variant: "caption", value: { path: "claim.status" } } }
      ] } },
  { _id: "InsuranceBeneficiaryCard", category: "INSURANCE", platforms: ["android","ios","huawei","web"], security: "read", minVersion: "1.0.0",
    primitive: "UniversalBox",
    config: { renderType: "card", padding: "md",
      children: [ { primitive: "UniversalText", config: { variant: "body", value: { path: "beneficiary.name" } } } ] } },
  { _id: "InsurancePremiumDue", category: "INSURANCE", platforms: ["android","ios","huawei","web"], security: "read", minVersion: "1.0.0",
    primitive: "UniversalBox",
    config: { renderType: "card", variant: "accent", padding: "md",
      children: [
        { primitive: "UniversalText", config: { variant: "value", value: { path: "premium.amount" }, format: "currency" } },
        { primitive: "UniversalText", config: { variant: "caption", value: { path: "premium.dueDate" }, format: "date" } }
      ] } },
  { _id: "InsuranceClaimList", category: "INSURANCE", platforms: ["android","ios","huawei","web"], security: "read", minVersion: "1.0.0",
    primitive: "UniversalList",
    config: { layout: "vertical",
      item: { primitive: "UniversalBox", config: { renderType: "card", padding: "sm",
        children: [
          { primitive: "UniversalText", config: { variant: "body", value: { path: "claimNumber" } } },
          { primitive: "UniversalText", config: { variant: "caption", value: { path: "status" }, color: "secondary" } }
        ] } } } },
  { _id: "InsurancePremiumList", category: "INSURANCE", platforms: ["android","ios","huawei","web"], security: "read", minVersion: "1.0.0",
    primitive: "UniversalList",
    config: { layout: "vertical",
      item: { primitive: "UniversalBox", config: { renderType: "card", padding: "sm",
        children: [
          { primitive: "UniversalText", config: { variant: "body", value: { path: "amount" }, format: "currency" } },
          { primitive: "UniversalText", config: { variant: "caption", value: { path: "dueDate" }, format: "date" } }
        ] } } } },
  { _id: "PackageDetailCard", category: "CATALOG", platforms: ["android","ios","huawei","web"], security: "read", minVersion: "1.0.0",
    primitive: "UniversalBox",
    config: { renderType: "card", padding: "md",
      children: [
        { primitive: "UniversalText", config: { variant: "title", value: { path: "packageName" } } },
        { primitive: "UniversalText", config: { variant: "caption", value: { path: "activatedAt" }, format: "relative-time", color: "secondary" } },
        { primitive: "UniversalText", config: { variant: "caption", value: { path: "autoRenew" }, color: "secondary" } }
      ] } },
  { _id: "QuickAddonList", category: "CATALOG", platforms: ["android","ios","huawei","web"], security: "display", minVersion: "1.0.0",
    primitive: "UniversalList",
    config: { layout: "horizontal",
      item: { primitive: "UniversalBox", config: { renderType: "card", padding: "sm",
        children: [
          { primitive: "UniversalText", config: { variant: "subtitle", value: { path: "name" } } },
          { primitive: "UniversalText", config: { variant: "caption", value: { path: "price" }, format: "currency" } },
          { primitive: "UniversalText", config: { variant: "caption", value: { path: "validityDays" } } }
        ] } } } },
  { _id: "CreditLimitCard", category: "ACCOUNT", platforms: ["android","ios","huawei","web"], security: "read", minVersion: "1.0.0",
    primitive: "UniversalBox",
    config: { renderType: "card", padding: "md",
      children: [
        { primitive: "UniversalText", config: { variant: "value", value: { path: "creditLimit.amount" }, format: "currency" } },
        { primitive: "UniversalText", config: { variant: "caption", value: { path: "creditLimit.usagePercent" }, color: "secondary" } }
      ] } },
  { _id: "MiniBanner", category: "CONTENT", platforms: ["android","ios","huawei","web"], security: "display", minVersion: "1.0.0",
    primitive: "UniversalBox",
    config: { renderType: "card", variant: "surface", elevation: "sm", padding: "md", flexDirection: "row", alignItems: "center", borderRadius: "lg",
      children: [
        { primitive: "UniversalImage", config: { shape: "thumbnail", source: { path: "imageUrl" }, size: 48 } },
        { primitive: "UniversalText", config: { variant: "body", value: { path: "ctaText" }, color: "primary" } }
      ] } },
  { _id: "AppGalleryGrid", category: "NAVIGATION", platforms: ["android","ios","huawei","web"], security: "display", minVersion: "1.0.0",
    primitive: "UniversalGrid",
    config: { columns: 3, gap: "md",
      item: { primitive: "UniversalBox", config: { renderType: "card", alignItems: "center", padding: "sm",
        children: [
          { primitive: "UniversalImage", config: { shape: "icon", source: { path: "imageUrl" } } },
          { primitive: "UniversalText", config: { variant: "caption", value: { path: "ctaText" }, align: "center" } }
        ] } } } },
  { _id: "OtherServicesGrid", category: "NAVIGATION", platforms: ["android","ios","huawei","web"], security: "display", minVersion: "1.0.0",
    primitive: "UniversalGrid",
    config: { columns: 2, gap: "md",
      item: { primitive: "UniversalBox", config: { renderType: "card", alignItems: "center", padding: "md",
        children: [
          { primitive: "UniversalImage", config: { shape: "icon", source: { path: "imageUrl" } } },
          { primitive: "UniversalText", config: { variant: "body", value: { path: "ctaText" }, align: "center" } }
        ] } } } },
  { _id: "FullWidthPackageDetail", category: "CATALOG", platforms: ["android","ios","huawei","web"], security: "read", minVersion: "1.0.0",
    primitive: "UniversalBox",
    config: { renderType: "card", variant: "gradient", padding: "lg", borderRadius: "lg",
      children: [
        { primitive: "UniversalText", config: { variant: "title", value: { path: "packageName" }, color: "onPrimary" } },
        { primitive: "UniversalText", config: { variant: "value", value: { path: "price" }, format: "currency", color: "onPrimary" } },
        { primitive: "UniversalText", config: { variant: "caption", value: { path: "autoRenew" }, color: "onPrimary" } },
        { primitive: "UniversalButton", config: { variant: "primary", label: "View details", action: { type: "NAVIGATE", route: "/packages" } } }
      ] } },
  { _id: "UsageHyperlink", category: "NAVIGATION", platforms: ["android","ios","huawei","web"], security: "display", minVersion: "1.0.0",
    primitive: "UniversalText",
    config: { variant: "link", value: "View usage history", align: "center", onPress: { type: "NAVIGATE", route: "/usage/history", analyticsEvent: "usage_hyperlink" } } }
];

// Catalog recipes must satisfy the compiler's findActive contract:
// tenantId + environment ("*" fallback) + status "PUBLISHED" + a
// componentId that matches the section.component reference. componentId is
// set to the recipe _id so ConfigCompiler/ComponentCatalogSeeder upsert by the
// same (tenantId, environment, componentId) key and never duplicate entries.
components.forEach(c => {
  const { _id, ...recipe } = c;
  db.component_catalog.updateOne(
    { tenantId: TENANT, environment: "*", componentId: _id },
    {
      $set: {
        ...recipe,
        componentId: _id,
        tenantId: TENANT,
        environment: "*",
        status: recipe.status || "PUBLISHED",
        mongoVersion: 0,
        enabled: recipe.enabled != null ? recipe.enabled : true,
        publishedBy: "admin@selfcare.lk",
        createdAt: ISO(),
        updatedAt: ISO()
      }
    },
    { upsert: true }
  );
});
print("  ✓ component_catalog " + components.length + " components");

// ────────────────────────────────────────────────────────────────────────────
// 6. ASSET DOCUMENTS (asset_documents collection)
//    Logos, icons, images — metadata only (actual files in CDN/object storage)
// ────────────────────────────────────────────────────────────────────────────
const assets = [
  { _id: "dialog-logo",       tenantId: TENANT, type: "LOGO",      name: "Dialog Logo",       url: "https://cdn.selfcare.lk/dialog/logo.svg",        mimeType: "image/svg+xml",  width: 200, height: 60 },
  { _id: "dialog-icon",       tenantId: TENANT, type: "ICON",      name: "Dialog App Icon",   url: "https://cdn.selfcare.lk/dialog/icon-512.png",    mimeType: "image/png",       width: 512, height: 512 },
  { _id: "dialog-favicon",    tenantId: TENANT, type: "FAVICON",   name: "Dialog Favicon",    url: "https://cdn.selfcare.lk/dialog/favicon.ico",     mimeType: "image/x-icon",    width: 32, height: 32 },
  { _id: "dialog-splash",     tenantId: TENANT, type: "SPLASH",    name: "Dialog Splash",     url: "https://cdn.selfcare.lk/dialog/splash.png",      mimeType: "image/png",       width: 1080, height: 1920 },
  { _id: "dialog-banner-1",   tenantId: TENANT, type: "BANNER",    name: "Welcome Banner",    url: "https://cdn.selfcare.lk/dialog/banners/welcome.jpg", mimeType: "image/jpeg", width: 1200, height: 400 },
  { _id: "dialog-banner-2",   tenantId: TENANT, type: "BANNER",    name: "Data Offer Banner", url: "https://cdn.selfcare.lk/dialog/banners/data-offer.jpg", mimeType: "image/jpeg", width: 1200, height: 400 },
  { _id: "dialog-recharge-icon", tenantId: TENANT, type: "ICON",   name: "Recharge Icon",     url: "https://cdn.selfcare.lk/dialog/icons/recharge.svg", mimeType: "image/svg+xml", width: 48, height: 48 },
  { _id: "dialog-data-icon",  tenantId: TENANT, type: "ICON",      name: "Data Icon",         url: "https://cdn.selfcare.lk/dialog/icons/data.svg",      mimeType: "image/svg+xml", width: 48, height: 48 },
  { _id: "dialog-bill-icon",  tenantId: TENANT, type: "ICON",      name: "Bill Icon",         url: "https://cdn.selfcare.lk/dialog/icons/bill.svg",      mimeType: "image/svg+xml", width: 48, height: 48 },
  { _id: "dialog-support-icon", tenantId: TENANT, type: "ICON",    name: "Support Icon",      url: "https://cdn.selfcare.lk/dialog/icons/support.svg",   mimeType: "image/svg+xml", width: 48, height: 48 },
  { _id: "dialog-star-icon",  tenantId: TENANT, type: "ICON",      name: "StarPoints Icon",   url: "https://cdn.selfcare.lk/dialog/icons/star.svg",      mimeType: "image/svg+xml", width: 48, height: 48 },
  { _id: "dialog-roaming-icon", tenantId: TENANT, type: "ICON",    name: "Roaming Icon",      url: "https://cdn.selfcare.lk/dialog/icons/roaming.svg",   mimeType: "image/svg+xml", width: 48, height: 48 },
  { _id: "dialog-idd-icon",     tenantId: TENANT, type: "ICON",    name: "IDD Icon",          url: "https://cdn.selfcare.lk/dialog/icons/idd.svg",        mimeType: "image/svg+xml", width: 48, height: 48 },
  { _id: "dialog-vas-icon",     tenantId: TENANT, type: "ICON",    name: "VAS Icon",          url: "https://cdn.selfcare.lk/dialog/icons/vas.svg",        mimeType: "image/svg+xml", width: 48, height: 48 },
  { _id: "dialog-dtv-icon",     tenantId: TENANT, type: "ICON",    name: "Dialog TV Icon",    url: "https://cdn.selfcare.lk/dialog/icons/tv.svg",         mimeType: "image/svg+xml", width: 48, height: 48 },
  { _id: "dialog-share-icon",   tenantId: TENANT, type: "ICON",    name: "Share Credit Icon", url: "https://cdn.selfcare.lk/dialog/icons/share.svg",      mimeType: "image/svg+xml", width: 48, height: 48 },
  { _id: "dialog-data-loan-icon", tenantId: TENANT, type: "ICON",  name: "Data Loan Icon",    url: "https://cdn.selfcare.lk/dialog/icons/data-loan.svg",   mimeType: "image/svg+xml", width: 48, height: 48 },
  { _id: "dialog-settings-icon", tenantId: TENANT, type: "ICON",   name: "Settings Icon",     url: "https://cdn.selfcare.lk/dialog/icons/settings.svg",    mimeType: "image/svg+xml", width: 48, height: 48 },
  { _id: "dialog-connections-icon", tenantId: TENANT, type: "ICON", name: "Connections Icon", url: "https://cdn.selfcare.lk/dialog/icons/connections.svg", mimeType: "image/svg+xml", width: 48, height: 48 },
  { _id: "dialog-notifications-icon", tenantId: TENANT, type: "ICON", name: "Notifications Icon", url: "https://cdn.selfcare.lk/dialog/icons/bell.svg",  mimeType: "image/svg+xml", width: 48, height: 48 },
  { _id: "dialog-ai-icon",      tenantId: TENANT, type: "ICON",    name: "AI Assistant Icon", url: "https://cdn.selfcare.lk/dialog/icons/ai.svg",         mimeType: "image/svg+xml", width: 48, height: 48 },
  { _id: "dialog-avatar-default", tenantId: TENANT, type: "IMAGE", name: "Default Avatar",    url: "https://cdn.selfcare.lk/dialog/avatar-default.png",     mimeType: "image/png",       width: 128, height: 128 },
  { _id: "dialog-loyalty-banner", tenantId: TENANT, type: "BANNER", name: "StarPoints Banner", url: "https://cdn.selfcare.lk/dialog/banners/loyalty.jpg",    mimeType: "image/jpeg", width: 1200, height: 400 }
];

assets.forEach(a => {
  db.asset_documents.updateOne(
    { _id: a._id },
    { $set: { ...a, createdAt: ISO(), updatedAt: ISO() } },
    { upsert: true }
  );
});
print("  ✓ asset_documents " + assets.length + " assets");

// ────────────────────────────────────────────────────────────────────────────
// 7. PRODUCT MAPPING DOCUMENTS (product_mapping_documents collection)
//    Maps operator product codes → canonical platform capabilities
// ────────────────────────────────────────────────────────────────────────────
const productMappings = [
  { _id: "pm-data-1gb",      tenantId: TENANT, operatorCode: "DIALOG_DATA_1GB",      canonicalType: "DATA_PACK",    category: "DATA",   price: 499,   validity: "30D",  data: "1GB",   voice: null, sms: null, status: "ACTIVE" },
  { _id: "pm-data-3gb",      tenantId: TENANT, operatorCode: "DIALOG_DATA_3GB",      canonicalType: "DATA_PACK",    category: "DATA",   price: 1199,  validity: "30D",  data: "3GB",   voice: null, sms: null, status: "ACTIVE" },
  { _id: "pm-data-10gb",     tenantId: TENANT, operatorCode: "DIALOG_DATA_10GB",     canonicalType: "DATA_PACK",    category: "DATA",   price: 2999,  validity: "30D",  data: "10GB",  voice: null, sms: null, status: "ACTIVE" },
  { _id: "pm-voice-100min",  tenantId: TENANT, operatorCode: "DIALOG_VOICE_100MIN",  canonicalType: "VOICE_PACK",   category: "VOICE",  price: 399,   validity: "30D",  data: null,    voice: "100min", sms: null, status: "ACTIVE" },
  { _id: "pm-voice-unlim",   tenantId: TENANT, operatorCode: "DIALOG_VOICE_UNLIM",   canonicalType: "VOICE_PACK",   category: "VOICE",  price: 999,   validity: "30D",  data: null,    voice: "UNLIMITED", sms: null, status: "ACTIVE" },
  { _id: "pm-combo-2gb",     tenantId: TENANT, operatorCode: "DIALOG_COMBO_2GB",     canonicalType: "COMBO_PACK",   category: "COMBO",  price: 799,   validity: "30D",  data: "2GB",   voice: "100min", sms: "50", status: "ACTIVE" },
  { _id: "pm-combo-5gb",     tenantId: TENANT, operatorCode: "DIALOG_COMBO_5GB",     canonicalType: "COMBO_PACK",   category: "COMBO",  price: 1799,  validity: "30D",  data: "5GB",   voice: "300min", sms: "100", status: "ACTIVE" },
  { _id: "pm-sms-100",       tenantId: TENANT, operatorCode: "DIALOG_SMS_100",       canonicalType: "SMS_PACK",     category: "SMS",    price: 99,    validity: "30D",  data: null,    voice: null, sms: "100", status: "ACTIVE" },
  { _id: "pm-roaming-ap",    tenantId: TENANT, operatorCode: "DIALOG_ROAMING_AP",    canonicalType: "ROAMING_PACK", category: "ROAMING", price: 1499, validity: "7D",   data: "1GB",   voice: "60min", sms: null, status: "ACTIVE" },
  { _id: "pm-idd-100min",    tenantId: TENANT, operatorCode: "DIALOG_IDD_100MIN",    canonicalType: "IDD_PACK",     category: "IDD",    price: 599,   validity: "30D",  data: null,    voice: "100min", sms: null, status: "ACTIVE" },
  { _id: "pm-dtv-basic",     tenantId: TENANT, operatorCode: "DIALOG_DTV_BASIC",     canonicalType: "DTV_PACK",     category: "DTV",    price: 499,   validity: "30D",  data: null,    voice: null, sms: null, status: "ACTIVE" },
  { _id: "pm-dtv-premium",   tenantId: TENANT, operatorCode: "DIALOG_DTV_PREMIUM",   canonicalType: "DTV_PACK",     category: "DTV",    price: 999,   validity: "30D",  data: null,    voice: null, sms: null, status: "ACTIVE" },
  { _id: "pm-loyalty-redeem", tenantId: TENANT, operatorCode: "DIALOG_LOYALTY_REDEEM", canonicalType: "LOYALTY_REDEEM", category: "LOYALTY", price: 0, validity: null, data: null, voice: null, sms: null, status: "ACTIVE" },
  { _id: "pm-share-credit",  tenantId: TENANT, operatorCode: "DIALOG_SHARE_CREDIT",  canonicalType: "SHARE_CREDIT", category: "CREDIT", price: 0,    validity: null,  data: null,    voice: null, sms: null, status: "ACTIVE" },
  { _id: "pm-data-loan",     tenantId: TENANT, operatorCode: "DIALOG_DATA_LOAN",     canonicalType: "DATA_LOAN",    category: "LOAN",   price: 0,    validity: "3D",   data: "500MB", voice: null, sms: null, status: "ACTIVE" }
];

productMappings.forEach(p => {
  db.product_mapping_documents.updateOne(
    { _id: p._id },
    { $set: { ...p, createdAt: ISO(), updatedAt: ISO() } },
    { upsert: true }
  );
});
print("  ✓ product_mapping_documents " + productMappings.length + " mappings");

// ────────────────────────────────────────────────────────────────────────────
// 8. FEATURE FLAGS (feature_flags collection)
//    FeatureFlag — per-tenant feature toggles with rollout percentage
// ────────────────────────────────────────────────────────────────────────────
db.feature_flags.updateOne(
  { tenantId: TENANT },
  {
    $set: {
      tenantId: TENANT,
      version: "1.0.0",
      flags: {
        "ai.assistant.enabled":        { enabled: true, rollout: 100 },
        "ai.summarization.enabled":    { enabled: true, rollout: 100, minAppVersion: "7.0.0" },
        "ai.sentiment.enabled":        { enabled: true, rollout: 100 },
        "ai.recommendations.enabled":  { enabled: true, rollout: 100 },
        "biometric.login.enabled":     { enabled: true, rollout: 100 },
        "dashboard.dark-mode.enabled": { enabled: true, rollout: 100 },
        "recharge.credit-card.enabled":{ enabled: true, rollout: 100 },
        "recharge.bank-transfer.enabled":{ enabled: true, rollout: 100 },
        "recharge.ezcash.enabled":     { enabled: true, rollout: 100 },
        "recharge.starpoints.enabled": { enabled: true, rollout: 100 },
        "gamification.badges.enabled": { enabled: true, rollout: 50 },
        "usage.history.enabled":       { enabled: true, rollout: 100 },
        "billing.ebill.enabled":       { enabled: true, rollout: 100 },
        "roaming.packages.enabled":    { enabled: true, rollout: 100 },
        "idd.rates.enabled":           { enabled: true, rollout: 100 },
        "vas.catalog.enabled":         { enabled: true, rollout: 100 },
        "loyalty.starpoints.enabled":  { enabled: true, rollout: 100 },
        "share.credit.enabled":        { enabled: true, rollout: 100 },
        "data.loan.enabled":           { enabled: true, rollout: 100 },
        "DTV.channels.enabled":        { enabled: true, rollout: 100 },
        "support.chat.enabled":        { enabled: true, rollout: 100 },
        "notifications.push.enabled":  { enabled: true, rollout: 100 },
        "notifications.sms.enabled":   { enabled: true, rollout: 100 },
        "profile.edit.enabled":        { enabled: true, rollout: 100 },
        "connections.manage.enabled":  { enabled: true, rollout: 100 },
        "payment.savedcards.enabled":  { enabled: true, rollout: 100 },
        "audio.playback.enabled":      { enabled: true, rollout: 100 },
        "qr.scanner.enabled":          { enabled: true, rollout: 100 },
        "dashboard.mini-banner.enabled":      { enabled: true, rollout: 100 },
        "dashboard.app-gallery.enabled":      { enabled: true, rollout: 100 },
        "dashboard.other-services.enabled":   { enabled: true, rollout: 100 },
        "dashboard.full-width-detail.enabled":{ enabled: true, rollout: 100 },
        "dashboard.usage-hyperlink.enabled":  { enabled: true, rollout: 100 }
      },
      updatedAt: ISO()
    },
    $setOnInsert: {
      _id: "dialog-lk-flags",
      createdAt: ISO(),
      createdBy: "system"
    }
  },
  { upsert: true }
);
print("  ✓ feature_flags dialog-lk-flags (33 flags)");

// ────────────────────────────────────────────────────────────────────────────
// 9. JOURNEY DEFINITIONS (journey_definitions collection)
//    JourneyDefinition — reusable, versioned multi-step journeys (journey-service).
//    Schema matches JourneyDefinition + JourneyStep (step.type in FORM / CALL_API /
//    NAVIGATE / BRANCH / WAIT). Quick actions in navigation_documents reference
//    journeyId — these must exist for START_JOURNEY to resolve.
// ────────────────────────────────────────────────────────────────────────────
const JOURNEY_DEFINITIONS = [
  {
    _id: "dialog-lk-journey-recharge",
    journeyId: "recharge-flow",
    name: "Quick Recharge",
    description: "Recharge balance via card, ezCash, StarPoints or bank transfer.",
    version: 1,
    status: "PUBLISHED",
    entryStepId: "step-select-amount",
    tags: ["recharge", "payments"],
    steps: [
      {
        stepId: "step-select-amount",
        type: "FORM",
        title: "Recharge amount",
        description: "Choose an amount and payment method.",
        required: true,
        nextStepIds: ["step-submit-payment"],
        config: {
          fields: [
            { name: "amount", type: "number", label: "Recharge amount (LKR)", required: true },
            { name: "method", type: "select", label: "Payment method", options: ["card", "ezcash", "starpoints", "bank"], required: true }
          ]
        }
      },
      {
        stepId: "step-submit-payment",
        type: "CALL_API",
        title: "Processing payment",
        description: "Submitting your recharge.",
        required: true,
        nextStepIds: ["step-result"],
        timeoutSeconds: 60,
        config: { service: "payments", endpoint: "create", method: "POST", bodyTemplate: { amount: "{{amount}}", channel: "{{method}}", connectionId: "{{connectionId}}" } }
      },
      {
        stepId: "step-result",
        type: "NAVIGATE",
        title: "Done",
        required: true,
        nextStepIds: [],
        config: { route: "/payment/history" }
      }
    ]
  },
  {
    _id: "dialog-lk-journey-data-pack",
    journeyId: "data-pack-flow",
    name: "Buy Data Pack",
    description: "Purchase a data package with a saved card or e-channel.",
    version: 1,
    status: "PUBLISHED",
    entryStepId: "step-select-pack",
    tags: ["catalog", "data", "payments"],
    steps: [
      {
        stepId: "step-select-pack",
        type: "FORM",
        title: "Choose data pack",
        description: "Pick a pack and payment method.",
        required: true,
        nextStepIds: ["step-submit-purchase"],
        config: {
          fields: [
            { name: "packId", type: "select", label: "Data pack", options: ["DIALOG_DATA_1GB", "DIALOG_DATA_3GB", "DIALOG_DATA_10GB"], required: true },
            { name: "method", type: "select", label: "Payment method", options: ["card", "ezcash", "starpoints", "bank"], required: true }
          ]
        }
      },
      {
        stepId: "step-submit-purchase",
        type: "CALL_API",
        title: "Purchasing pack",
        description: "Confirming your order.",
        required: true,
        nextStepIds: ["step-result"],
        timeoutSeconds: 60,
        config: { service: "payments", endpoint: "create", method: "POST", bodyTemplate: { productCode: "{{packId}}", channel: "{{method}}", connectionId: "{{connectionId}}" } }
      },
      {
        stepId: "step-result",
        type: "NAVIGATE",
        title: "Done",
        required: true,
        nextStepIds: [],
        config: { route: "/payment/history" }
      }
    ]
  }
];

const journeyDefs = JOURNEY_DEFINITIONS.map(def => {
  const { _id, ...body } = def;
  return db.journey_definitions.updateOne(
    { tenantId: TENANT, journeyId: def.journeyId },
    {
      $set: {
        ...body,
        tenantId: TENANT,
        steps: def.steps,
        publishedBy: "admin@selfcare.lk",
        createdAt: ISO(),
        updatedAt: ISO(),
        publishedAt: ISO()
      },
      $setOnInsert: { _id, createdBy: "system" }
    },
    { upsert: true }
  );
});
print("  ✓ journey_definitions " + JOURNEY_DEFINITIONS.length + " journeys (recharge-flow, data-pack-flow)");

// ────────────────────────────────────────────────────────────────────────────
// 10. CLIENT INTEGRATIONS (client_integrations collection)
//    ClientIntegrationConfig — operator BSS / MIFE / SMSC / catalog / AI configs.
//    Schema matches platform-common ClientIntegrationConfig document:
//    integrationType, baseUrl (flat), authType, credentials{clientId,clientSecret,apiKey},
//    fieldMapping, advanced, metadata, status. Credential values may be literal or
//    "secret:<k8s-secret>" references that the deployment layer resolves.
// ────────────────────────────────────────────────────────────────────────────
db.client_integrations.updateOne(
  { tenantId: TENANT, integrationType: "DIALOG_BSS" },
  {
    $set: {
      tenantId: TENANT,
      integrationType: "DIALOG_BSS",
      industry: "TELCO",
      baseUrl: "https://bss.dialog.lk/api/v3",
      authType: "OAUTH2_CLIENT_CREDENTIALS",
      credentials: {
        clientId: "dev-client",
        clientSecret: "dev-secret",
        apiKey: "dev-key"
      },
      fieldMapping: {
        balance: "data.balance",
        usage: "data.usage",
        bill: "data.bill",
        connection: "data.connection"
      },
      advanced: {
        timeoutMs: "5000",
        maxRetries: "3",
        backoffMs: "200,1000,5000",
        circuitBreaker: "10:60",
        tokenTtlMinutes: "55"
      },
      metadata: {
        region: "LK",
        capabilities: "getBalance,getUsage,getBill,getConnection,validateMsisdn,activateSim,swapSim,getUsageHistory,getBillHistory,getDataUsage,getVoiceUsage,getRoamingStatus,getIddRates,getLoyaltyPoints,shareCredit,getCreditLimit",
        lob: "GSM",
        "path.balance": "/subscriber/balance/{connection}",
        "path.usage": "/subscriber/usage/{connection}",
        "path.usage.records": "/subscriber/usage/{connection}/records",
        "path.usage.current": "/subscriber/usage/{connection}/current",
        "path.usage.roaming": "/subscriber/usage/{connection}/roaming",
        "path.credit": "/subscriber/credit/{connection}",
        "path.connection.list": "/subscriber/connections/{connection}",
        "path.connection.detail": "/subscriber/connections/{connection}",
        "path.connection.status": "/subscriber/status/{connection}",
        "path.connection.sim": "/subscriber/sim/{connection}",
        "path.billing.bills": "/billing/customer/{customer}/bills",
        "path.billing.bill": "/billing/bills/{bill}",
        "path.billing.outstanding": "/billing/customer/{customer}/outstanding",
        "path.billing.pdf": "/billing/bills/{bill}/pdf",
        "path.billing.lineItems": "/billing/bills/{bill}/line-items",
        "path.billing.paymentPlan": "/billing/bills/{bill}/payment-plan",
        "path.support.list": "/apicall/crm/SblSrInfo/v1.0.0/sendRequest",
        "path.support.create": "/apicall/crm/api/cmu/v1.0.1/cmuenterprise/addComplaint/MyDialog/SCAPP",
        "path.support.cancel": "/apicall/WomWorkOrder/v2.0",
        "support.srType": "Complaint",
        "support.subArea": "MOBILE SERVICE REQUEST",
        "field.currency": "currency",
        "field.mainBalance": "mainBalance",
        "field.balanceType": "balanceType",
        "field.expiryDate": "expiryDate",
        "field.usage.data": "data",
        "field.usage.voice": "voice",
        "field.usage.sms": "sms",
        "field.usage.records": "records",
        "field.creditLimit": "creditLimit",
        "field.usedCredit": "usedCredit",
        "field.availableCredit": "availableCredit",
        "field.connection.list": "connections",
        "field.billing.bills": "bills",
        "field.billing.lineItems": "lineItems"
      },
      status: "ACTIVE",
      health: { status: "UNKNOWN", lastChecked: ISO(), responseTimeMs: null },
      createdAt: ISO(),
      updatedAt: ISO(),
      updatedBy: "system"
    },
    $setOnInsert: { _id: "dialog-lk-bss" }
  },
  { upsert: true }
);

db.client_integrations.updateOne(
  { tenantId: TENANT, integrationType: "DIALOG_MIFE" },
  {
    $set: {
      tenantId: TENANT,
      integrationType: "DIALOG_MIFE",
      industry: "TELCO",
      baseUrl: "https://auth.dialog.lk",
      authType: "OAUTH2_CLIENT_CREDENTIALS",
      credentials: {
        clientId: "dev-client",
        clientSecret: "dev-secret"
      },
      fieldMapping: {
        tokenUrl: "https://auth.dialog.lk/oauth/token",
        payment: "https://pay.dialog.lk/api/v2",
        recharge: "https://recharge.dialog.lk/api/v1"
      },
      advanced: {
        timeoutMs: "10000",
        maxRetries: "2",
        backoffMs: "1000,5000",
        circuitBreaker: "5:120",
        tokenTtlMinutes: "55"
      },
      metadata: {
        region: "LK",
        capabilities: "submitPayment,recharge,getAccessToken,cidExchange",
        "path.cid.token": "/apicall/dio-token",
        "path.cid.profile": "/apicall/crm/system/dds/selfcare-profile/v1.0.0/profiles/get-by-cid",
        "path.cid.wallet": "/apicall/crm/system/dds/get-wallet-by-cid/v1.0.0/wallets/get-by-cid-id",
        "cid.redirectUri": "net.omobio.dialogsc:/AuthDataReceiver"
      },
      status: "ACTIVE",
      health: { status: "UNKNOWN", lastChecked: ISO(), responseTimeMs: null },
      createdAt: ISO(),
      updatedAt: ISO(),
      updatedBy: "system"
    },
    $setOnInsert: { _id: "dialog-lk-mife" }
  },
  { upsert: true }
);

db.client_integrations.updateOne(
  { tenantId: TENANT, integrationType: "DIALOG_SMSC" },
  {
    $set: {
      tenantId: TENANT,
      integrationType: "DIALOG_SMSC",
      industry: "TELCO",
      baseUrl: "https://smsc.dialog.lk/api/v1",
      authType: "API_KEY",
      credentials: {
        apiKey: "dev-key",
        senderId: "DialogSC"
      },
      fieldMapping: {
        sms: "data.sms",
        push: "data.push",
        email: "data.email",
        inApp: "data.inApp"
      },
      advanced: {
        timeoutMs: "3000",
        maxRetries: "3",
        backoffMs: "100,500,2000",
        circuitBreaker: "20:30"
      },
      metadata: {
        region: "LK",
        capabilities: "sendSms,sendPush,sendEmail,sendInApp"
      },
      status: "ACTIVE",
      health: { status: "UNKNOWN", lastChecked: ISO(), responseTimeMs: null },
      createdAt: ISO(),
      updatedAt: ISO(),
      updatedBy: "system"
    },
    $setOnInsert: { _id: "dialog-lk-smsc" }
  },
  { upsert: true }
);

db.client_integrations.updateOne(
  { tenantId: TENANT, integrationType: "DIALOG_CATALOG" },
  {
    $set: {
      tenantId: TENANT,
      integrationType: "DIALOG_CATALOG",
      industry: "TELCO",
      baseUrl: "https://catalog.dialog.lk/api/v2",
      authType: "API_KEY",
      credentials: {
        apiKey: "dev-key"
      },
      fieldMapping: {
        product: "data.product",
        price: "data.price",
        promotion: "data.promotion"
      },
      advanced: {
        timeoutMs: "5000",
        maxRetries: "2",
        backoffMs: "500,2000",
        circuitBreaker: "10:60"
      },
      metadata: {
        region: "LK",
        capabilities: "getProducts,getPrices,getPromotions",
        "path.catalog.product": "/products/{id}",
        "path.catalog.data-packages": "/products/{category}",
        "path.catalog.voice-packages": "/products/{category}",
        "path.catalog.combo-packages": "/products/{category}",
        "path.catalog.addons": "/products/{category}",
        "path.catalog.roaming-packages": "/products/{category}",
        "path.catalog.dtv-packages": "/products/{category}",
        "field.catalog.items": "items",
        "field.currency": "currency"
      },
      status: "ACTIVE",
      health: { status: "UNKNOWN", lastChecked: ISO(), responseTimeMs: null },
      createdAt: ISO(),
      updatedAt: ISO(),
      updatedBy: "system"
    },
    $setOnInsert: { _id: "dialog-lk-catalog" }
  },
  { upsert: true }
);

db.client_integrations.updateOne(
  { tenantId: TENANT, integrationType: "DIALOG_PAYMENT_GATEWAY" },
  {
    $set: {
      tenantId: TENANT,
      integrationType: "DIALOG_PAYMENT_GATEWAY",
      industry: "TELCO",
      baseUrl: "https://pay.dialog.lk/api/v2",
      authType: "OAUTH2_CLIENT_CREDENTIALS",
      credentials: {
        clientId: "dev-client",
        clientSecret: "dev-secret"
      },
      fieldMapping: {
        tokenUrl: "https://auth.dialog.lk/oauth/token",
        payment: "https://pay.dialog.lk/api/v2/payments",
        txnStatus: "https://pay.dialog.lk/api/v2/payments/{txnId}",
        refund: "https://pay.dialog.lk/api/v2/payments/{txnId}/refund"
      },
      advanced: {
        timeoutMs: "10000",
        maxRetries: "3",
        backoffMs: "1000,5000,10000",
        circuitBreaker: "10:60",
        tokenTtlMinutes: "55",
        idempotencyKeyHeader: "Idempotency-Key"
      },
      metadata: {
        region: "LK",
        capabilities: "submitPayment,checkStatus,refund,reconcile",
        channels: "visa,mastercard,amex,ezcash,starpoints,bank-transfer,qr"
      },
      status: "ACTIVE",
      health: { status: "UNKNOWN", lastChecked: ISO(), responseTimeMs: null },
      createdAt: ISO(),
      updatedAt: ISO(),
      updatedBy: "system"
    },
    $setOnInsert: { _id: "dialog-lk-payment-gateway" }
  },
  { upsert: true }
);

db.client_integrations.updateOne(
  { tenantId: TENANT, integrationType: "DIALOG_CMS" },
  {
    $set: {
      tenantId: TENANT,
      integrationType: "DIALOG_CMS",
      industry: "TELCO",
      baseUrl: "https://cms.dialog.lk/api/v1",
      authType: "API_KEY",
      credentials: {
        apiKey: "dev-key"
      },
      fieldMapping: {
        article: "data.article",
        banner: "data.banner",
        faq: "data.faq"
      },
      advanced: {
        timeoutMs: "5000",
        maxRetries: "2",
        backoffMs: "500,2000",
        circuitBreaker: "10:60"
      },
      metadata: {
        region: "LK",
        capabilities: "getArticles,getBanners,getFaqs,getVideos,getAppGallery,getMiniBanner,getOtherServices"
      },
      status: "ACTIVE",
      health: { status: "UNKNOWN", lastChecked: ISO(), responseTimeMs: null },
      createdAt: ISO(),
      updatedAt: ISO(),
      updatedBy: "system"
    },
    $setOnInsert: { _id: "dialog-lk-cms" }
  },
  { upsert: true }
);

db.client_integrations.updateOne(
  { tenantId: TENANT, integrationType: "ANTHROPIC" },
  {
    $set: {
      tenantId: TENANT,
      integrationType: "ANTHROPIC",
      industry: "TELCO",
      baseUrl: "https://api.anthropic.com/v1",
      authType: "API_KEY",
      credentials: {
        apiKey: "env:ANTHROPIC_API_KEY",
        defaultModel: "claude-sonnet-4-5"
      },
      fieldMapping: {
        messages: "messages",
        chat: "chatCompletion",
        embedding: "embedding",
        sentiment: "sentimentAnalysis"
      },
      advanced: {
        timeoutSeconds: "60",
        maxTokens: "4096",
        defaultTemperature: "0.7",
        maxRetries: "2",
        backoffMs: "1000,5000",
        circuitBreaker: "5:300"
      },
      metadata: {
        industry: "TELCO",
        capabilities: "chatCompletion,embedding,sentimentAnalysis,contentModeration,recommendation"
      },
      status: "ACTIVE",
      health: { status: "UNKNOWN", lastChecked: ISO(), responseTimeMs: null },
      createdAt: ISO(),
      updatedAt: ISO(),
      updatedBy: "system"
    },
    $setOnInsert: { _id: "dialog-lk-ai" }
  },
  { upsert: true }
);

db.client_integrations.updateOne(
  { tenantId: TENANT, integrationType: "OPENAI" },
  {
    $set: {
      tenantId: TENANT,
      integrationType: "OPENAI",
      industry: "TELCO",
      baseUrl: "https://api.openai.com/v1",
      authType: "API_KEY",
      credentials: {
        apiKey: "env:OPENAI_API_KEY",
        defaultModel: "gpt-4o-mini"
      },
      fieldMapping: {
        chat: "chatCompletion",
        embedding: "embedding",
        moderation: "contentModeration"
      },
      advanced: {
        timeoutSeconds: "60",
        maxTokens: "4096",
        defaultTemperature: "0.7",
        maxRetries: "2",
        backoffMs: "1000,5000",
        circuitBreaker: "5:300"
      },
      metadata: {
        industry: "TELCO",
        capabilities: "chatCompletion,embedding,sentimentAnalysis,contentModeration,recommendation"
      },
      status: "ACTIVE",
      health: { status: "UNKNOWN", lastChecked: ISO(), responseTimeMs: null },
      createdAt: ISO(),
      updatedAt: ISO(),
      updatedBy: "system"
    },
    $setOnInsert: { _id: "dialog-lk-ai-openai" }
  },
  { upsert: true }
);

db.client_integrations.updateOne(
  { tenantId: TENANT, integrationType: "GOOGLE_AI" },
  {
    $set: {
      tenantId: TENANT,
      integrationType: "GOOGLE_AI",
      industry: "TELCO",
      baseUrl: "https://generativelanguage.googleapis.com/v1beta",
      authType: "API_KEY",
      credentials: {
        apiKey: "env:GOOGLE_AI_API_KEY",
        defaultModel: "gemini-1.5-flash"
      },
      fieldMapping: {
        chat: "chatCompletion",
        sentiment: "sentimentAnalysis"
      },
      advanced: {
        timeoutSeconds: "60",
        maxTokens: "4096",
        defaultTemperature: "0.7",
        maxRetries: "2",
        backoffMs: "1000,5000",
        circuitBreaker: "5:300"
      },
      metadata: {
        industry: "TELCO",
        capabilities: "chatCompletion,sentimentAnalysis,recommendation"
      },
      status: "ACTIVE",
      health: { status: "UNKNOWN", lastChecked: ISO(), responseTimeMs: null },
      createdAt: ISO(),
      updatedAt: ISO(),
      updatedBy: "system"
    },
    $setOnInsert: { _id: "dialog-lk-ai-google" }
  },
  { upsert: true }
);

db.client_integrations.updateOne(
  { tenantId: TENANT, integrationType: "AI_PROVIDER" },
  {
    $set: {
      tenantId: TENANT,
      integrationType: "AI_PROVIDER",
      industry: "TELCO",
      baseUrl: null,
      authType: "NONE",
      credentials: {},
      advanced: {},
      metadata: {
        provider: "openai",
        capabilities: "provider-routing"
      },
      status: "ACTIVE",
      health: { status: "UNKNOWN", lastChecked: ISO(), responseTimeMs: null },
      createdAt: ISO(),
      updatedAt: ISO(),
      updatedBy: "system"
    },
    $setOnInsert: { _id: "dialog-lk-ai-provider" }
  },
  { upsert: true }
);
print("  ✓ client_integrations 10 integrations (DIALOG_BSS, DIALOG_MIFE, DIALOG_SMSC, DIALOG_CATALOG, DIALOG_PAYMENT_GATEWAY, DIALOG_CMS, ANTHROPIC, OPENAI, GOOGLE_AI, AI_PROVIDER)");

// ────────────────────────────────────────────────────────────────────────────
// 11. INDEXES (idempotent)
// ────────────────────────────────────────────────────────────────────────────
db.tenant_configs.createIndex({ tenantId: 1 }, { unique: true });
db.tenant_configs.createIndex({ status: 1 });
db.theme_documents.createIndex({ tenantId: 1, name: 1, version: 1 }, { unique: true });
db.theme_documents.createIndex({ status: 1 });
db.navigation_documents.createIndex({ tenantId: 1, name: 1, version: 1, status: 1 }, { unique: true });
db.layout_documents.createIndex({ tenantId: 1, environment: 1, experience: 1, profileKey: 1 }, { unique: true });
db.layout_documents.createIndex({ status: 1 });
db.component_catalog.createIndex({ category: 1 });
db.asset_documents.createIndex({ tenantId: 1, type: 1 });
db.product_mapping_documents.createIndex({ tenantId: 1, operatorCode: 1 }, { unique: true });
db.product_mapping_documents.createIndex({ tenantId: 1, canonicalType: 1 });
db.feature_flags.createIndex({ tenantId: 1 }, { unique: true });
db.journey_definitions.createIndex({ tenantId: 1, journeyId: 1 }, { unique: true });
db.journey_definitions.createIndex({ status: 1 });
db.client_integrations.createIndex({ tenantId: 1, integrationType: 1 }, { unique: true });
print("  ✓ indexes created");

print("=== Seed complete for dialog-lk ===");
print("  Collections: tenant_configs, theme_documents, navigation_documents,");
print("  layout_documents, component_catalog, asset_documents,");
print("  product_mapping_documents, feature_flags, journey_definitions,");
print("  client_integrations");
