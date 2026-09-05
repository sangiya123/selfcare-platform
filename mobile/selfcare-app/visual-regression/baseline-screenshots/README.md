# Baseline Screenshots

This directory lists all screens that need baseline visual snapshots.

## Screenshot Requirements

- Resolution: 1284x2778 (iPhone 14 Pro Max) or equivalent
- Dark mode and light mode variants
- Safe area insets respected
- Network status: Online (no offline indicators)
- Data: Mock/fixture data for consistency

## Screen List

### Authentication

| Screen | File Pattern | Tenant(s) | Theme(s) |
|--------|-------------|-----------|----------|
| Login - MSISDN Entry | `login-msisdn-{tenant}-{theme}.png` | dialog-lk, aia-lk | light, dark |
| Login - OTP Entry | `login-otp-{tenant}-{theme}.png` | dialog-lk | light, dark |
| Login - Insurance Policy Lookup | `login-policy-{tenant}-{theme}.png` | aia-lk | light, dark |
| Biometric Prompt | `biometric-prompt-{platform}-{theme}.png` | all | light, dark |
| Session Expired | `session-expired-{tenant}-{theme}.png` | dialog-lk | light, dark |

### Dashboard (Telco)

| Screen | File Pattern | Tenant(s) | Theme(s) |
|--------|-------------|-----------|----------|
| Home Dashboard | `dashboard-{tenant}-{theme}.png` | dialog-lk | light, dark |
| Balance Card - PREPAID | `balance-prepaid-{theme}.png` | dialog-lk | light, dark |
| Balance Card - POSTPAID | `balance-postpaid-{theme}.png` | dialog-lk | light, dark |
| Usage Card - Bar Layout | `usage-bar-{theme}.png` | dialog-lk | light, dark |
| Usage Card - Chip Layout | `usage-chip-{theme}.png` | dialog-lk | light, dark |
| Bills Widget | `bills-{tenant}-{theme}.png` | dialog-lk | light, dark |
| Quick Actions | `quick-actions-{theme}.png` | dialog-lk | light, dark |
| Notifications | `notifications-{theme}.png` | dialog-lk | light, dark |
| Banners Carousel | `banners-{tenant}-{theme}.png` | dialog-lk | light, dark |

### Dashboard (Insurance)

| Screen | File Pattern | Tenant(s) | Theme(s) |
|--------|-------------|-----------|----------|
| Home Dashboard | `dashboard-{tenant}-{theme}.png` | aia-lk | light, dark |
| Policy List | `policy-list-{theme}.png` | aia-lk | light, dark |
| Policy Card - LIFE | `policy-life-{theme}.png` | aia-lk | light, dark |
| Policy Card - HEALTH | `policy-health-{theme}.png` | aia-lk | light, dark |
| Policy Card - MOTOR | `policy-motor-{theme}.png` | aia-lk | light, dark |
| Claims Widget | `claims-{theme}.png` | aia-lk | light, dark |
| Premium Due Widget | `premium-due-{theme}.png` | aia-lk | light, dark |
| Beneficiaries Widget | `beneficiaries-{theme}.png` | aia-lk | light, dark |

### Connection Management

| Screen | File Pattern | Tenant(s) | Theme(s) |
|--------|-------------|-----------|----------|
| Connection Switcher Modal | `connection-switcher-{theme}.png` | dialog-lk | light, dark |
| Connection List | `connection-list-{theme}.png` | dialog-lk | light, dark |
| Connection Details | `connection-detail-{theme}.png` | dialog-lk | light, dark |
| Suspended Connection | `connection-suspended-{theme}.png` | dialog-lk | light, dark |
| Link New Connection | `connection-link-{theme}.png` | dialog-lk | light, dark |

### Payments

| Screen | File Pattern | Tenant(s) | Theme(s) |
|--------|-------------|-----------|----------|
| Bill Payment | `payment-bill-{theme}.png` | dialog-lk | light, dark |
| Payment Method Selection | `payment-methods-{theme}.png` | dialog-lk | light, dark |
| Step-Up Auth | `payment-stepup-{theme}.png` | dialog-lk | light, dark |
| Payment Confirmation | `payment-confirm-{theme}.png` | dialog-lk | light, dark |
| Payment Success | `payment-success-{theme}.png` | dialog-lk | light, dark |
| Recharge | `recharge-{theme}.png` | dialog-lk | light, dark |

### Offline Mode

| Screen | File Pattern | Tenant(s) | Theme(s) |
|--------|-------------|-----------|----------|
| Offline Banner | `offline-banner-{theme}.png` | all | light, dark |
| Stale Data Indicator | `stale-indicator-{theme}.png` | all | light, dark |
| Sync Indicator | `sync-indicator-{theme}.png` | all | light, dark |

### Error States

| Screen | File Pattern | Tenant(s) | Theme(s) |
|--------|-------------|-----------|----------|
| Network Error | `error-network-{theme}.png` | all | light, dark |
| Session Expired | `error-session-{theme}.png` | all | light, dark |
| Widget Load Failure | `error-widget-{theme}.png` | all | light, dark |

### Settings & Profile

| Screen | File Pattern | Tenant(s) | Theme(s) |
|--------|-------------|-----------|----------|
| Profile Screen | `profile-{theme}.png` | all | light, dark |
| Settings Screen | `settings-{theme}.png` | all | light, dark |
| Language Selection | `settings-language-{theme}.png` | all | light, dark |
| Theme Selection | `settings-theme-{theme}.png` | all | light, dark |
| Sign Out Confirm | `signout-confirm-{theme}.png` | all | light, dark |

## Naming Convention

```
{screen}-{variant?}-{tenant?}-{theme}.png
```

Examples:
- `dashboard-dialog-lk-light.png`
- `balance-prepaid-dark.png`
- `policy-life-aia-lk-light.png`

## Capturing Baselines

### iOS (Simulator)

```bash
# Open simulator, navigate to screen
xcrun simctl io booted screenshot baseline-screenshots/dialog-lk/dashboard-light.png
```

### Android (Emulator)

```bash
# Navigate to screen
adb shell screencap /sdcard/screen.png
adb pull /sdcard/screen.png baseline-screenshots/dialog-lk/dashboard-light.png
```

### Automated (Detox + Storybook)

```bash
npm run capture:baselines
```
