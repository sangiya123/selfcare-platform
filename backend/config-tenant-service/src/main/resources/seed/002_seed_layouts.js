/**
 * MongoDB Seed — 002_seed_layouts.js
 *
 * Purpose: Bootstrap default layouts and themes for each tenant.
 * Database: config-tenant-service uses collections "layouts" and "themes".
 *
 * Run after 001_seed_tenants.js:
 *   mongosh mongodb://localhost:27017/selfcare_config < seed/002_seed_layouts.js
 */
use('selfcare_config');

// ============================================================
// Helper: upsert a layout document
// ============================================================
function upsertLayout(layout) {
  db.layouts.updateOne(
    { _id: layout._id, tenantId: layout.tenantId },
    { $set: layout },
    { upsert: true }
  );
}

function upsertTheme(theme) {
  db.themes.updateOne(
    { _id: theme._id, tenantId: theme.tenantId },
    { $set: theme },
    { upsert: true }
  );
}

// ============================================================
// Layouts: Dialog (LK) — Telco
// ============================================================
upsertLayout({
  _id: 'dialog-lk-home',
  tenantId: 'dialog-lk',
  experience: 'MOBILE',
  screen: 'HOME',
  version: 1,
  status: 'PUBLISHED',
  layout: {
    type: 'LAYOUT',
    version: '1.0',
    sections: [
      {
        id: 'hero-section',
        type: 'SECTION',
        style: { padding: '16px', backgroundColor: '#6C2DC7' },
        widgets: [
          {
            id: 'balance-hero',
            type: 'WIDGET',
            componentId: 'BalanceCard',
            props: {
              title: 'Your Balance',
              showRechargeButton: true,
              variant: 'hero',
            },
          },
        ],
      },
      {
        id: 'usage-section',
        type: 'SECTION',
        style: { padding: '16px', marginTop: '12px' },
        widgets: [
          {
            id: 'data-usage',
            type: 'WIDGET',
            componentId: 'UsageProgress',
            props: {
              metric: 'data',
              unit: 'GB',
              showWarningThreshold: 20,
            },
          },
          {
            id: 'voice-usage',
            type: 'WIDGET',
            componentId: 'UsageProgress',
            props: {
              metric: 'voice',
              unit: 'min',
              showWarningThreshold: 20,
            },
          },
          {
            id: 'sms-usage',
            type: 'WIDGET',
            componentId: 'UsageProgress',
            props: {
              metric: 'sms',
              unit: 'SMS',
              showWarningThreshold: 20,
            },
          },
        ],
      },
      {
        id: 'bills-section',
        type: 'SECTION',
        style: { padding: '16px', marginTop: '12px' },
        widgets: [
          {
            id: 'bills-widget',
            type: 'WIDGET',
            componentId: 'BillList',
            props: {
              title: 'Recent Bills',
              maxItems: 3,
              showPayButton: true,
            },
            actions: [
              {
                type: 'NAVIGATE',
                payload: { route: 'Bills', params: {} },
              },
            ],
          },
        ],
      },
      {
        id: 'quick-actions-section',
        type: 'SECTION',
        style: { padding: '16px', marginTop: '12px' },
        widgets: [
          {
            id: 'quick-actions',
            type: 'WIDGET',
            componentId: 'QuickActions',
            props: {
              actions: [
                { label: 'Recharge', icon: 'recharge', route: 'Recharge' },
                { label: 'Data Packs', icon: 'data', route: 'DataPacks' },
                { label: 'Pay Bill', icon: 'pay', route: 'Bills' },
                { label: 'Support', icon: 'support', route: 'Support' },
              ],
            },
          },
        ],
      },
    ],
  },
  publishedAt: new Date(),
  createdAt: new Date(),
  updatedAt: new Date(),
});

upsertLayout({
  _id: 'dialog-lk-bills',
  tenantId: 'dialog-lk',
  experience: 'MOBILE',
  screen: 'BILLS',
  version: 1,
  status: 'PUBLISHED',
  layout: {
    type: 'LAYOUT',
    version: '1.0',
    sections: [
      {
        id: 'bills-header',
        type: 'SECTION',
        style: { padding: '16px' },
        widgets: [
          {
            id: 'bill-summary',
            type: 'WIDGET',
            componentId: 'BillSummary',
            props: { showTotal: true },
          },
        ],
      },
      {
        id: 'bills-list-section',
        type: 'SECTION',
        style: { padding: '0 16px' },
        widgets: [
          {
            id: 'bill-list',
            type: 'WIDGET',
            componentId: 'BillList',
            props: {
              maxItems: 50,
              showPayButton: true,
              showStatusBadge: true,
            },
          },
        ],
      },
    ],
  },
  publishedAt: new Date(),
  createdAt: new Date(),
  updatedAt: new Date(),
});

// ============================================================
// Layouts: AIA — Insurance
// ============================================================
upsertLayout({
  _id: 'aia-multi-home',
  tenantId: 'aia-multi',
  experience: 'MOBILE',
  screen: 'HOME',
  version: 1,
  status: 'PUBLISHED',
  layout: {
    type: 'LAYOUT',
    version: '1.0',
    sections: [
      {
        id: 'policies-hero-section',
        type: 'SECTION',
        style: { padding: '16px', backgroundColor: '#0078A8' },
        widgets: [
          {
            id: 'policies-overview',
            type: 'WIDGET',
            componentId: 'PolicyOverview',
            props: {
              title: 'Your Policies',
              variant: 'hero',
              showClaimButton: true,
            },
          },
        ],
      },
      {
        id: 'claims-section',
        type: 'SECTION',
        style: { padding: '16px', marginTop: '12px' },
        widgets: [
          {
            id: 'active-claims',
            type: 'WIDGET',
            componentId: 'ClaimList',
            props: {
              title: 'Active Claims',
              maxItems: 3,
              statusFilter: ['SUBMITTED', 'UNDER_REVIEW', 'APPROVED'],
            },
            actions: [
              {
                type: 'NAVIGATE',
                payload: { route: 'Claims', params: {} },
              },
            ],
          },
        ],
      },
      {
        id: 'premium-section',
        type: 'SECTION',
        style: { padding: '16px', marginTop: '12px' },
        widgets: [
          {
            id: 'upcoming-premiums',
            type: 'WIDGET',
            componentId: 'PremiumSchedule',
            props: {
              title: 'Upcoming Premiums',
              maxItems: 3,
              showPayButton: true,
            },
            actions: [
              {
                type: 'NAVIGATE',
                payload: { route: 'Premiums', params: {} },
              },
            ],
          },
        ],
      },
      {
        id: 'quick-actions-section',
        type: 'SECTION',
        style: { padding: '16px', marginTop: '12px' },
        widgets: [
          {
            id: 'insurance-quick-actions',
            type: 'WIDGET',
            componentId: 'QuickActions',
            props: {
              actions: [
                { label: 'My Policies', icon: 'policy', route: 'Policies' },
                { label: 'Submit Claim', icon: 'claim', route: 'NewClaim' },
                { label: 'Pay Premium', icon: 'pay', route: 'Premiums' },
                { label: 'Support', icon: 'support', route: 'Support' },
              ],
            },
          },
        ],
      },
    ],
  },
  publishedAt: new Date(),
  createdAt: new Date(),
  updatedAt: new Date(),
});

// ============================================================
// Seed log
// ============================================================
print('✅ Seeded layouts: dialog-lk-home, dialog-lk-bills, aia-multi-home');
print(`📊 Total layouts: ${db.layouts.countDocuments()}`);
print(`📊 Total themes: ${db.themes.countDocuments()}`);
