# Mobile SDK — Integration Guide

The mobile app (React Native) ships with a built-in SDK that handles
authentication, config fetching, theme resolution, and component rendering.

## Quick start

```tsx
import { selfcareApp } from '@selfcare/selfcare-sdk';

export default function App() {
  return (
    <selfcareApp
      apiBaseUrl="https://api.selfcare.io"
      tenantId="dialog-lk"          // or null for multi-tenant
      experience="home"
      onError={(err) => console.error(err)}
    />
  );
}
```

That's it — the SDK handles:
- Auth lifecycle (OTP send/verify, token refresh, secure storage)
- Config manifest fetching with ETag caching
- Theme resolution from manifest
- Server-driven layout rendering
- Action dispatch (NAVIGATE, CALL_API, etc.)

## Components

### ConfigSDK

```ts
import { ConfigSDK } from '@selfcare/selfcare-sdk';

const config = new ConfigSDK({
  apiBaseUrl: 'https://api.selfcare.io',
  tenantId: 'dialog-lk',
  cache: 'mmkv'        // or 'encryptedStorage' for sensitive
});

await config.initialize();
const manifest = await config.getManifest('home', 'mobile_prepaid');
const theme = await config.getTheme();
```

### AuthSDK

```ts
import { AuthSDK } from '@selfcare/selfcare-sdk';

const auth = new AuthSDK({ apiBaseUrl: 'https://api.selfcare.io', tenantId: 'dialog-lk' });

// Send OTP
await auth.sendOtp({ identifier: '+94771234567', channel: 'SMS' });

// Verify (returns tokens + user info)
const result = await auth.verifyOtp({ identifier: '+94771234567', code: '123456' });

// Listen for auth events
auth.on('token-refreshed', (tokens) => {
  console.log('tokens refreshed', tokens);
});

// Sign out
await auth.signOut();
```

### ComponentRegistry

Register a custom component to make it usable in server-driven layouts:

```ts
import { ComponentRegistry } from '@selfcare/selfcare-sdk';
import { MyCustomWidget } from './widgets/MyCustomWidget';

ComponentRegistry.register('MyCustomWidget', MyCustomWidget, {
  description: 'A custom widget for displaying analytics',
  category: 'analytics',
  // JSON Schema for props validation
  propsSchema: {
    type: 'object',
    properties: {
      metric: { type: 'string' },
      showTrend: { type: 'boolean', default: true }
    },
    required: ['metric']
  }
});
```

After registration, the widget is usable in any layout document (subject to
approval workflow per ADR-009).

### LayoutRenderer

```tsx
import { LayoutRenderer } from '@selfcare/selfcare-sdk';

<LayoutRenderer
  manifest={manifest}
  components={ComponentRegistry.all()}
  onAction={(action) => {
    // action.type is one of the registered action types
    switch (action.type) {
      case 'NAVIGATE':
        navigation.navigate(action.payload.route);
        break;
      case 'CALL_API':
        apiClient.call(action.payload.endpoint, action.payload.body);
        break;
      case 'OPEN_WEB':
        Linking.openURL(action.payload.url);
        break;
      // ...
    }
  }}
/>
```

### ThemeEngine

```tsx
import { ThemeEngine } from '@selfcare/selfcare-sdk';

const theme = ThemeEngine.fromManifest(manifest);

// Returns React Native theme object
<NavigationContainer theme={theme.reactNavigation}>
  <Stack.Navigator>...</Stack.Navigator>
</NavigationContainer>
```

## Security

### Token storage
- **Access token**: In-memory only
- **Refresh token**: Encrypted storage (iOS Keychain / Android Keystore)
- Never log tokens
- Tokens cleared on signOut or app uninstall

### Tenant isolation
- SDK enforces `X-Tenant-Id` header on every request
- Tokens are tenant-scoped
- Cross-tenant requests rejected by gateway

### Config integrity
- Manifest cached with ETag
- Apps refuse to render if `schemaVersion` is unsupported
- Required components must be registered (else render falls back to "missing component" placeholder)

## Customization

### Override a component
```ts
// Replace a platform component with a custom implementation
ComponentRegistry.override('BalanceCard', CustomBalanceCard);
```

The override is local to your app — it doesn't affect the platform.

### Add a custom action handler
```ts
ComponentRegistry.onAction('OPEN_WEB', (action, ctx) => {
  // Open in WebView with SSO
  ctx.openWebView(action.payload.url, { sso: true });
});
```

## Debugging

### Enable verbose logging
```ts
import { setLogLevel } from '@selfcare/selfcare-sdk';
setLogLevel('debug');
```

### Inspect current config
```ts
const current = await config.getCurrentManifest();
console.log('Config version:', current.configVersion);
console.log('Sections:', current.sections.length);
```

### Force refresh
```ts
await config.refreshManifest('home');
```

## Build and deploy

### iOS
```bash
cd ios
pod install
xcodebuild -workspace selfcareSelfcare.xcworkspace -scheme selfcareSelfcare -configuration Release
```

### Android
```bash
cd android
./gradlew assembleRelease
```

### Bundle JS (offline)
```bash
npx react-native bundle --platform android --dev false \
    --entry-file index.js \
    --bundle-output android/app/src/main/assets/index.android.bundle
```
