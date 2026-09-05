// MongoDB init script — runs ONCE on first container start.
// Seeds the dialog-lk tenant config so the platform boots fully configured
// for Dialog Axiata PLC (Sri Lanka's largest mobile operator).
//
// This file is loaded by the official mongo image via /docker-entrypoint-initdb.d/
// It is idempotent — uses upsert by tenantId so re-running on a populated DB is safe.

print("=== Seeding dialog-lk tenant config ===");

const tenantId = "dialog-lk";
const now = new Date();
const iso = now.toISOString();

// ─── 1. Tenant metadata ───────────────────────────────────────────────
db.tenants.updateOne(
  { _id: "dialog-lk" },
  {
    $set: {
      _id: "dialog-lk",
      tenantId: "dialog-lk",
      displayName: "Dialog Axiata",
      industry: "TELCO",
      industryPack: "telco",
      tenantType: "OPERATOR",
      country: "LK",
      defaultLocale: "en-LK",
      supportedLocales: ["en-LK", "si-LK", "ta-LK"],
      timezone: "Asia/Colombo",
      currency: "LKR",
      enabled: true,
      createdAt: now,
      updatedAt: now,
      version: 1
    }
  },
  { upsert: true }
);
print("  ✓ tenants dialog-lk");

// ─── 2. Theme / brand ────────────────────────────────────────────────
db.themes.updateOne(
  { _id: "dialog-lk-default" },
  {
    $set: {
      _id: "dialog-lk-default",
      tenantId: "dialog-lk",
      version: "1.0.0",
      isDefault: true,
      tokens: {
        colors: {
          primary: "#E50914",      // Dialog red
          secondary: "#0033A0",    // Dialog blue
          accent: "#FFC72C",       // Highlight yellow
          background: "#FFFFFF",
          surface: "#F5F5F5",
          text: "#1A1A1A",
          error: "#D32F2F",
          success: "#2E7D32"
        },
        typography: {
          fontFamily: "Roboto, system-ui, sans-serif",
          headingScale: { h1: "32px", h2: "24px", h3: "20px", body: "16px", small: "14px" }
        },
        spacing: { xs: "4px", sm: "8px", md: "16px", lg: "24px", xl: "32px" },
        borderRadius: { sm: "4px", md: "8px", lg: "16px", pill: "999px" }
      },
      brand: {
        logo: "https://cdn.dialog.lk/logo.svg",
        favicon: "https://cdn.dialog.lk/favicon.ico",
        appName: "MyDialog",
        tagline: "Powering your digital life"
      },
      createdAt: now,
      updatedAt: now
    }
  },
  { upsert: true }
);
print("  ✓ themes dialog-lk-default");

// ─── 3. Layout (dashboard default) ───────────────────────────────────
db.layouts.updateOne(
  { _id: "dialog-lk-home" },
  {
    $set: {
      _id: "dialog-lk-home",
      tenantId: "dialog-lk",
      version: "1.0.0",
      isDefault: true,
      page: "home",
      sections: [
        {
          id: "hero-balance",
          type: "BALANCE_WIDGET",
          order: 1,
          visible: true,
          config: { showOutstanding: true, showDueDate: true, refreshSeconds: 60 }
        },
        {
          id: "quick-actions",
          type: "QUICK_ACTIONS",
          order: 2,
          visible: true,
          config: {
            actions: [
              { id: "recharge", label: "Recharge", icon: "wallet", action: "START_JOURNEY", journeyId: "recharge-flow" },
              { id: "buy-data", label: "Buy Data", icon: "data", action: "START_JOURNEY", journeyId: "data-flow" },
              { id: "pay-bill", label: "Pay Bill", icon: "bill", action: "NAVIGATE", route: "/billing" },
              { id: "support", label: "Support", icon: "support", action: "START_JOURNEY", journeyId: "support-flow" }
            ]
          }
        },
        {
          id: "usage-overview",
          type: "USAGE_WIDGET",
          order: 3,
          visible: true,
          config: { showData: true, showVoice: true, showSms: true }
        },
        {
          id: "bills",
          type: "BILLS_WIDGET",
          order: 4,
          visible: true,
          config: { limit: 3 }
        },
        {
          id: "ai-assistant",
          type: "AI_CHAT_ENTRYPOINT",
          order: 5,
          visible: true,
          config: { entryId: "ai-chat" }
        }
      ],
      createdAt: now,
      updatedAt: now
    }
  },
  { upsert: true }
);
print("  ✓ layouts dialog-lk-home");

// ─── 4. Feature flags ────────────────────────────────────────────────
db.feature_flags.updateOne(
  { _id: "dialog-lk-flags" },
  {
    $set: {
      _id: "dialog-lk-flags",
      tenantId: "dialog-lk",
      version: "1.0.0",
      flags: {
        "ai.assistant.enabled": { enabled: true, rollout: 100 },
        "biometric.login.enabled": { enabled: true, rollout: 100 },
        "dashboard.dark-mode.enabled": { enabled: true, rollout: 100 },
        "recharge.credit-card.enabled": { enabled: true, rollout: 100 },
        "recharge.bank-transfer.enabled": { enabled: true, rollout: 100 },
        "gamification.badges.enabled": { enabled: true, rollout: 50 },
        "ai.summarization.enabled": { enabled: true, rollout: 100, minAppVersion: "1.4.0" }
      },
      updatedAt: now
    }
  },
  { upsert: true }
);
print("  ✓ feature_flags dialog-lk-flags");

// ─── 5. Integrations (operator BSS / SMSC / etc.) ────────────────────
db.integrations.updateOne(
  { _id: "dialog-lk-bss" },
  {
    $set: {
      _id: "dialog-lk-bss",
      tenantId: "dialog-lk",
      type: "operator-bss",
      industry: "TELCO",
      status: "ACTIVE",
      endpoint: {
        baseUrl: "https://bss.dialog.lk/api/v3",
        authMethod: "OAUTH2_CLIENT_CREDENTIALS",
        tokenUrl: "https://auth.dialog.lk/oauth2/token",
        clientIdRef: "secret:omobio-dialog-lk-bss-client-id",
        clientSecretRef: "secret:omobio-dialog-lk-bss-client-secret"
      },
      capabilities: [
        "getBalance", "getUsage", "getBill", "submitPayment", "recharge",
        "getConnection", "validateMsisdn", "activateSim", "swapSim"
      ],
      timeoutMs: 5000,
      retryPolicy: { maxRetries: 3, backoffMs: [200, 1000, 5000] },
      circuitBreaker: { failureThreshold: 10, openDurationSeconds: 60 },
      createdAt: now,
      updatedAt: now
    }
  },
  { upsert: true }
);
print("  ✓ integrations dialog-lk-bss");

// ─── 6. Navigation ──────────────────────────────────────────────────
db.navigations.updateOne(
  { _id: "dialog-lk-main" },
  {
    $set: {
      _id: "dialog-lk-main",
      tenantId: "dialog-lk",
      version: "1.0.0",
      tabs: [
        { id: "home", label: "Home", icon: "home", route: "/home" },
        { id: "billing", label: "Bills", icon: "bill", route: "/billing" },
        { id: "usage", label: "Usage", icon: "chart", route: "/usage" },
        { id: "shop", label: "Shop", icon: "shop", route: "/shop" },
        { id: "more", label: "More", icon: "menu", route: "/more" }
      ],
      updatedAt: now
    }
  },
  { upsert: true }
);
print("  ✓ navigations dialog-lk-main");

// ─── 7. Indexes (idempotent) ─────────────────────────────────────────
db.tenants.createIndex({ tenantId: 1 }, { unique: true });
db.themes.createIndex({ tenantId: 1, isDefault: 1 });
db.layouts.createIndex({ tenantId: 1, page: 1 });
db.feature_flags.createIndex({ tenantId: 1 });
db.integrations.createIndex({ tenantId: 1, type: 1 });
db.navigations.createIndex({ tenantId: 1 });
print("  ✓ indexes created");

print("=== Seeding complete for dialog-lk ===");
