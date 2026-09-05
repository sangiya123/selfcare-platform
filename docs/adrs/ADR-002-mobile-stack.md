# ADR-002: React Native + TypeScript for Mobile

**Status**: ACCEPTED
**Date**: 2026-09-03
**Deciders**: OMOBIO Architecture Council

## Context

The platform must deliver native mobile apps (iOS + Android) for multiple clients
(Dialog, Hutch, Airtel for telco; AIA for insurance). The legacy codebases show
a split: Dialog in React Native, Hutch in React Native, Airtel in Flutter.

We need to choose the mobile framework for the new unified product.

## Decision

**React Native + TypeScript** for the mobile kernel.

- Single TypeScript codebase → iOS + Android
- Server-driven UI: layouts come from the Config Service (ADR-004)
- Native module bridge only when absolutely necessary
- Hermes JS engine for fast startup
- New Architecture (Fabric + TurboModules) enabled by default

## Rationale

### Why React Native
1. **Code reuse** — Dialog and Hutch already use RN. Migrating Airtel from Flutter
   is a one-time investment that pays off in reduced ongoing maintenance.
2. **Talent pool** — JS/TS engineers vastly outnumber Flutter/Dart engineers globally.
3. **Server-driven UI** — RN's declarative model maps naturally to the layout
   document model (sections, widgets, props). Flutter's imperative model would
   require more translation work.
4. **Hot reload** — faster iteration during config-driven UI development.
5. **Mature ecosystem** — large library catalogue (MMKV, Reanimated, MMKV, etc.).

### Why TypeScript
- Type safety across 16+ microservices (config types, API types)
- Better IDE support for component props
- Compile-time validation of server-driven layout documents

### Why not Flutter
- Forces Airtel rewrite AND requires Dialog/Hutch to migrate
- Dart skill set is smaller
- Server-driven UI is harder to express
- The cost of unification outweighs the marginal performance benefit

## Implementation

### Mobile kernel structure
```
mobile/selfcare-app/
├── src/
│   ├── config/         # ConfigSDK, LayoutRenderer, ComponentRegistry, ActionEngine
│   ├── components/     # Widget implementations
│   ├── hooks/          # React hooks (useApi, useTenant, useTheme)
│   ├── styles/         # Design tokens
│   └── screens/        # Top-level screens (Home, Bills, Profile, etc.)
├── ios/                # Standard RN iOS project
├── android/            # Standard RN Android project
└── package.json
```

### Component Registry
Every widget exposed to the server-driven layout must be registered in
`ComponentRegistry` with a stable `componentId`. Adding a new widget:
1. Implement the widget as a React component
2. Register it with `componentRegistry.register('NewWidget', NewWidget, metadata)`
3. The widget is now usable in any layout document

### Theming
- Theme tokens come from the compiled manifest (ThemeDocument)
- `ThemeEngine` resolves semantic tokens → concrete styles
- Industry-specific token mapping (e.g. insurance uses "policy" instead of "connection")

## Consequences

### Positive
- Single codebase across 3 telco clients + insurance
- Faster feature delivery (one app to update)
- Easier to maintain accessibility (single screen reader integration)
- Better analytics (one event format)

### Negative
- Slight performance overhead vs. native (Hermes mitigates this)
- Native modules still required for some platform features (biometrics, etc.)
- App store approval process for both platforms

## Migration path

Airtel Flutter app → React Native: ~6 weeks
- Re-implement screens in RN
- Reuse business logic via shared TS types
- Use Strangler-fig pattern: ship RN screens one at a time
