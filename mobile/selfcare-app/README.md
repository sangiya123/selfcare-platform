# Selfcare App — Mobile Kernel

React Native + TypeScript mobile application kernel.
Renders operator experiences from server-driven configuration.

## Architecture

```
Mobile App Shell
     |
     +-- ConfigSDK       --> fetches compiled manifest from Config Service
     |                      caches locally, checks version/etag
     |
     +-- ComponentRegistry  --> maps componentId -> React Native component
     |
     +-- LayoutRenderer     --> recursively renders sections/widgets from manifest
     |
     +-- ThemeEngine       --> resolves design tokens from tenant config
     |
     +-- NavigationEngine  --> config-driven bottom tabs, routes, deep links
     |
     +-- ActionEngine      --> executes NAVIGATE, START_JOURNEY, CALL_API, etc.
     |
     +-- AnalyticsSDK       --> records impressions, events, errors
     |
     +-- AuthSDK           --> JWT storage in secure storage, token refresh
```

## Key Principles

1. **Config-driven**: No hardcoded screens or journeys. Everything comes from the server manifest.
2. **Offline-first**: Last-known-good manifest cached locally; app works without network.
3. **Graceful degradation**: Each widget owns its loading/error/stale states.
4. **Secure**: JWT stored in EncryptedStorage, not AsyncStorage.
5. **No source forks**: Same binary renders Dialog, Hutch, or Airtel based on config.

## Config SDK

```typescript
import { ConfigSDK } from './config/ConfigSDK';

const configSDK = new ConfigSDK({
  baseUrl: 'https://api.selfcare.example.com',
  tenantId: 'dialog-lk',
});

const manifest = await configSDK.getManifest({
  experience: 'home',
  profileKey: 'mobile_prepaid_youth',
  appVersion: '20.0.0',
});
```

## Component Registry

```typescript
import { ComponentRegistry } from './components/ComponentRegistry';
import { BalanceCard } from './components/widgets/BalanceCard';
import { UsageSummary } from './components/widgets/UsageSummary';
import { BillCard } from './components/widgets/BillCard';

const registry = new ComponentRegistry();
registry.register('BalanceCard', BalanceCard);
registry.register('UsageSummary', UsageSummary);
registry.register('BillCard', BillCard);
```

## Layout Renderer

```typescript
import { LayoutRenderer } from './renderer/LayoutRenderer';

const renderer = new LayoutRenderer(registry, configSDK, themeEngine);
const element = renderer.render(manifest.sections);
```

## State Management

Uses Zustand for lightweight, typed state:

```typescript
import { create } from 'zustand';

interface AppState {
  tenantId: string | null;
  connectionId: string | null;
  manifest: ExperienceManifest | null;
  setConnection: (connectionId: string) => void;
}

export const useAppStore = create<AppState>((set) => ({
  tenantId: null,
  connectionId: null,
  manifest: null,
  setConnection: (connectionId) => set({ connectionId }),
}));
```

## Design System

See `src/styles/design-tokens.ts` for the token system.
Tokens are loaded from the tenant theme config and applied via StyleSheet.

## Building

```bash
npm install

# Android
npm run bundle:android
npm run android

# iOS
npm run bundle:ios
npm run ios
```

## Project Structure

```
selfcare-app/
├── src/
│   ├── config/          # ConfigSDK, theme resolution, manifest caching
│   ├── components/       # Component registry + widget components
│   ├── renderer/         # Layout renderer, section renderer
│   ├── navigation/       # Navigation engine, route config
│   ├── actions/          # Action engine (navigate, call_api, etc.)
│   ├── hooks/            # Custom React hooks
│   ├── services/         # API client, auth service
│   ├── store/           # Zustand state
│   ├── styles/          # Design tokens, global styles
│   ├── utils/           # Helpers, validators
│   └── types/           # TypeScript types
├── android/
├── ios/
└── package.json
```
