/**
 * MongoDB Seed — 001_seed_tenants.js
 *
 * Purpose: Bootstrap the base tenant records in MongoDB.
 * Database: config-tenant-service uses collection "tenants".
 *
 * Run with:
 *   mongosh mongodb://localhost:27017/selfcare_config < seed/001_seed_tenants.js
 *
 * Or via the seed runner in config-tenant-service:
 *   POST /api/v1/internal/seed/run  (internal only, protected by API key)
 */
use('selfcare_config');

// ============================================================
// Helper: upsert a tenant document
// ============================================================
function upsertTenant(tenant) {
  db.tenants.updateOne(
    { _id: tenant._id },
    { $set: tenant },
    { upsert: true }
  );
}

// ============================================================
// Industry Pack definitions
// ============================================================
const TELCO_PACK = {
  industry: 'TELCO',
  name: 'Telco Industry Pack',
  version: '1.0.0',
  adapters: [
    'com.omobio.telco.BalanceProvider',
    'com.omobio.telco.RechargeProvider',
    'com.omobio.telco.ConnectionProvider',
  ],
  terminology: {
    primary: 'operator',
    customer: 'subscriber',
    account: 'connection',
    identifier: 'MSISDN',
    recharge: 'recharge',
    balance: 'balance',
  },
};

const INSURANCE_PACK = {
  industry: 'INSURANCE',
  name: 'Insurance Industry Pack',
  version: '1.0.0',
  adapters: [
    'com.omobio.insurance.PolicyProvider',
    'com.omobio.insurance.ClaimProvider',
    'com.omobio.insurance.PremiumProvider',
  ],
  terminology: {
    primary: 'insurer',
    customer: 'policyholder',
    account: 'policy',
    identifier: 'policy_number',
    recharge: 'premium_payment',
    balance: 'coverage',
  },
};

// ============================================================
// Tenant: Dialog (LK) — Telco Operator
// ============================================================
upsertTenant({
  _id: 'dialog-lk',
  name: 'Dialog Axiata',
  type: 'OPERATOR',
  industryPack: TELCO_PACK,
  status: 'ACTIVE',
  region: 'LK',
  brand: {
    displayName: 'Dialog',
    tagline: 'The Smart Choice',
    logoUrl: '/assets/brands/dialog/logo.svg',
    faviconUrl: '/assets/brands/dialog/favicon.ico',
    colors: {
      primary: '#6C2DC7',
      primaryDark: '#4A1E8A',
      primaryLight: '#A78BFA',
      accent: '#00B900',
      error: '#D32F2F',
      warning: '#FFA000',
      success: '#388E3C',
      info: '#1976D2',
      textPrimary: '#212121',
      textSecondary: '#757575',
      background: '#FFFFFF',
      surface: '#F5F5F5',
      divider: '#E0E0E0',
    },
    fonts: {
      primaryFont: 'Roboto',
      secondaryFont: 'Roboto Slab',
      monoFont: 'Roboto Mono',
    },
  },
  config: {
    currency: 'LKR',
    currencySymbol: 'Rs.',
    decimalPlaces: 2,
    dateFormat: 'DD/MM/YYYY',
    timeZone: 'Asia/Colombo',
    locale: 'si-LK',
  },
  integrations: [
    {
      integrationId: 'dialog-bss-connector',
      type: 'BSS',
      provider: 'DialogBssProvider',
      baseUrl: 'http://bss-dialog.svc:8080/api',
      authType: 'API_KEY',
      apiKeyHeader: 'X-Dialog-API-Key',
      timeout: 5000,
      circuitBreaker: {
        failureRateThreshold: 50,
        waitDurationInOpenState: 30000,
        permittedCallsInHalfOpen: 5,
      },
      enabled: true,
    },
    {
      integrationId: 'dialog-smsc',
      type: 'SMSC',
      provider: 'DialogSmscProvider',
      baseUrl: 'http://smsc-dialog.svc:8080/smpp',
      authType: 'SMPP',
      timeout: 3000,
      enabled: true,
    },
  ],
  features: {
    otpChannels: ['SMS', 'WHATSAPP'],
    paymentMethods: ['WALLET', 'CARD', 'BANK_TRANSFER'],
    stepUpThreshold: 10000.00,
    sessionTimeoutMinutes: 30,
    refreshTokenMaxAgeDays: 210,
    allowCrossConnectionActions: true,
    dashboardWidgets: ['balance', 'usage', 'bills', 'recharge', 'support'],
  },
  createdAt: new Date(),
  updatedAt: new Date(),
  version: 1,
});

// ============================================================
// Tenant: Hutch (LK) — Telco Operator
// ============================================================
upsertTenant({
  _id: 'hutch-lk',
  name: 'Hutchison Telecommunications',
  type: 'OPERATOR',
  industryPack: TELCO_PACK,
  status: 'ACTIVE',
  region: 'LK',
  brand: {
    displayName: 'Hutch',
    tagline: 'Real Hot Deals',
    logoUrl: '/assets/brands/hutch/logo.svg',
    faviconUrl: '/assets/brands/hutch/favicon.ico',
    colors: {
      primary: '#FF6B00',
      primaryDark: '#CC5500',
      primaryLight: '#FF9E40',
      accent: '#FFD700',
      error: '#D32F2F',
      warning: '#FFA000',
      success: '#388E3C',
      info: '#1976D2',
      textPrimary: '#212121',
      textSecondary: '#757575',
      background: '#FFFFFF',
      surface: '#FFF8F0',
      divider: '#FFE0B2',
    },
    fonts: {
      primaryFont: 'Open Sans',
      secondaryFont: 'Lato',
      monoFont: 'Source Code Pro',
    },
  },
  config: {
    currency: 'LKR',
    currencySymbol: 'Rs.',
    decimalPlaces: 2,
    dateFormat: 'DD/MM/YYYY',
    timeZone: 'Asia/Colombo',
    locale: 'en-LK',
  },
  integrations: [
    {
      integrationId: 'hutch-bss-connector',
      type: 'BSS',
      provider: 'HutchBssProvider',
      baseUrl: 'http://bss-hutch.svc:8080/api',
      authType: 'API_KEY',
      apiKeyHeader: 'X-Hutch-API-Key',
      timeout: 5000,
      circuitBreaker: {
        failureRateThreshold: 50,
        waitDurationInOpenState: 30000,
        permittedCallsInHalfOpen: 5,
      },
      enabled: true,
    },
    {
      integrationId: 'hutch-smsc',
      type: 'SMSC',
      provider: 'HutchSmscProvider',
      baseUrl: 'http://smsc-hutch.svc:8080/smpp',
      authType: 'SMPP',
      timeout: 3000,
      enabled: true,
    },
  ],
  features: {
    otpChannels: ['SMS', 'WHATSAPP'],
    paymentMethods: ['WALLET', 'CARD'],
    stepUpThreshold: 10000.00,
    sessionTimeoutMinutes: 30,
    refreshTokenMaxAgeDays: 210,
    allowCrossConnectionActions: false,
    dashboardWidgets: ['balance', 'usage', 'bills', 'recharge', 'support'],
  },
  createdAt: new Date(),
  updatedAt: new Date(),
  version: 1,
});

// ============================================================
// Tenant: Airtel (LK) — Telco Operator
// ============================================================
upsertTenant({
  _id: 'airtel-lk',
  name: 'Airtel Lanka',
  type: 'OPERATOR',
  industryPack: TELCO_PACK,
  status: 'ACTIVE',
  region: 'LK',
  brand: {
    displayName: 'Airtel',
    tagline: 'Smart BuY',
    logoUrl: '/assets/brands/airtel/logo.svg',
    faviconUrl: '/assets/brands/airtel/favicon.ico',
    colors: {
      primary: '#ED1C24',
      primaryDark: '#B71C1C',
      primaryLight: '#FF8A80',
      accent: '#FFB300',
      error: '#D32F2F',
      warning: '#FFA000',
      success: '#388E3C',
      info: '#1976D2',
      textPrimary: '#212121',
      textSecondary: '#757575',
      background: '#FFFFFF',
      surface: '#FFF5F5',
      divider: '#FFCDD2',
    },
    fonts: {
      primaryFont: 'Nunito Sans',
      secondaryFont: 'Libre Baskerville',
      monoFont: 'Fira Code',
    },
  },
  config: {
    currency: 'LKR',
    currencySymbol: 'Rs.',
    decimalPlaces: 2,
    dateFormat: 'DD/MM/YYYY',
    timeZone: 'Asia/Colombo',
    locale: 'en-LK',
  },
  integrations: [
    {
      integrationId: 'airtel-bss-connector',
      type: 'BSS',
      provider: 'AirtelBssProvider',
      baseUrl: 'http://bss-airtel.svc:8080/api',
      authType: 'API_KEY',
      apiKeyHeader: 'X-Airtel-API-Key',
      timeout: 5000,
      circuitBreaker: {
        failureRateThreshold: 50,
        waitDurationInOpenState: 30000,
        permittedCallsInHalfOpen: 5,
      },
      enabled: true,
    },
    {
      integrationId: 'airtel-smsc',
      type: 'SMSC',
      provider: 'AirtelSmscProvider',
      baseUrl: 'http://smsc-airtel.svc:8080/smpp',
      authType: 'SMPP',
      timeout: 3000,
      enabled: true,
    },
  ],
  features: {
    otpChannels: ['SMS', 'WHATSAPP'],
    paymentMethods: ['WALLET', 'CARD', 'BANK_TRANSFER'],
    stepUpThreshold: 10000.00,
    sessionTimeoutMinutes: 30,
    refreshTokenMaxAgeDays: 210,
    allowCrossConnectionActions: false,
    dashboardWidgets: ['balance', 'usage', 'bills', 'recharge', 'support'],
  },
  createdAt: new Date(),
  updatedAt: new Date(),
  version: 1,
});

// ============================================================
// Tenant: AIA (Multi-country) — Insurance Insurer
// ============================================================
upsertTenant({
  _id: 'aia-multi',
  name: 'AIA Group',
  type: 'INSURER',
  industryPack: INSURANCE_PACK,
  status: 'ACTIVE',
  region: 'MULTI',
  brand: {
    displayName: 'AIA',
    tagline: 'Healthier, Longer, Better Lives',
    logoUrl: '/assets/brands/aia/logo.svg',
    faviconUrl: '/assets/brands/aia/favicon.ico',
    colors: {
      primary: '#0078A8',
      primaryDark: '#004F75',
      primaryLight: '#80D0E8',
      accent: '#00A651',
      error: '#D32F2F',
      warning: '#FFA000',
      success: '#388E3C',
      info: '#1976D2',
      textPrimary: '#212121',
      textSecondary: '#757575',
      background: '#FFFFFF',
      surface: '#F0F8FF',
      divider: '#B3D9ED',
    },
    fonts: {
      primaryFont: 'Source Sans Pro',
      secondaryFont: 'Merriweather',
      monoFont: 'Roboto Mono',
    },
  },
  config: {
    currency: 'USD',
    currencySymbol: '$',
    decimalPlaces: 2,
    dateFormat: 'MM/DD/YYYY',
    timeZone: 'Asia/Hong_Kong',
    locale: 'en-HK',
  },
  integrations: [
    {
      integrationId: 'aia-insurance-api',
      type: 'INSURANCE_PROVIDER',
      provider: 'AiaInsuranceProvider',
      baseUrl: 'http://aia-api.svc:8080/api/v2',
      authType: 'OAUTH2_CLIENT_CREDENTIALS',
      oauth2: {
        tokenUrl: 'http://aia-api.svc:8080/oauth/token',
        clientId: '${AIA_API_CLIENT_ID}',
        clientSecret: '${AIA_API_CLIENT_SECRET}',
        scopes: ['insurance:read', 'insurance:write', 'claims:submit'],
      },
      timeout: 10000,
      circuitBreaker: {
        failureRateThreshold: 30,
        waitDurationInOpenState: 60000,
        permittedCallsInHalfOpen: 3,
      },
      enabled: true,
    },
  ],
  features: {
    otpChannels: ['SMS', 'EMAIL'],
    paymentMethods: ['CARD', 'BANK_TRANSFER', 'DIRECT_DEBIT'],
    stepUpThreshold: 500.00,
    sessionTimeoutMinutes: 60,
    refreshTokenMaxAgeDays: 30,
    allowCrossConnectionActions: false,
    dashboardWidgets: ['policies', 'claims', 'premiums', 'beneficiaries', 'support'],
    insurance: {
      countries: ['LK', 'BD', 'NP', 'PK', 'SG', 'MY', 'TH', 'VN', 'ID', 'PH'],
      claimTypes: ['HEALTH', 'LIFE', 'ACCIDENT', 'TRAVEL'],
      supportedBenefits: ['HOSPITALIZATION', 'OUTPATIENT', 'DENTAL', 'MATERNITY'],
    },
  },
  createdAt: new Date(),
  updatedAt: new Date(),
  version: 1,
});

// ============================================================
// Seed log
// ============================================================
print('✅ Seeded 4 tenants: dialog-lk, hutch-lk, airtel-lk, aia-multi');
print(`📊 Total tenants: ${db.tenants.countDocuments()}`);
