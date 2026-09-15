# Selfcare Studio — Admin Portal

React + TypeScript admin/operator portal for configuring and publishing the
selfcare platform. Built with modern tools (React 18, Vite, Tailwind, dnd-kit,
Redux Toolkit, TanStack Query, Zod).

## Modules

1. **Tenant Manager** — Create/manage operators, environments, LOBs
2. **Theme Designer** — Visual theme editor (colors, typography, tokens)
3. **Page Builder** — Drag-and-drop page/experience configuration
4. **Journey Builder** — Multi-step journey composition
5. **Integration Builder** — Provider endpoint/mapping configuration
6. **Feature Manager** — Feature flag definitions, rollouts, kill switches
7. **Navigation Editor** — Bottom tabs, drawers, deep links, routes
8. **Content/Localization** — CMS, translation workflow
9. **Product/Catalog Mapping** — Source product → canonical mapping
10. **Report Builder** — Report definitions, saved filters, scheduling
11. **AI Studio** — Model configuration, RAG, tools, evaluation
12. **User/Role Manager** — RBAC, approvals, audit
13. **Audit Log** — Immutable change history

## Architecture

```
+----------------+     +----------------+     +----------------+
|   Page Builder |     | Journey Builder|     |   AI Studio    |
+--------+-------+     +-------+--------+     +-------+--------+
         |                    |                       |
         v                    v                       v
+--------------------------------------------------------------+
|                  Config Service Client                       |
+--------------------------------------------------------------+
                              |
                              v
+--------------------------------------------------------------+
|   API Gateway -> Config Tenant Service (Mongo)               |
+--------------------------------------------------------------+
```

## Tech Stack

- **Framework**: React 18 + TypeScript 5.6
- **Build**: Vite 5
- **Styling**: Tailwind CSS 3.4
- **State**: Redux Toolkit + Zustand
- **Forms**: React Hook Form + Zod
- **DnD**: dnd-kit
- **API**: Axios + TanStack Query
- **Charts**: Recharts
- **Code Editor**: Monaco
- **Icons**: Lucide React
- **Testing**: Vitest + Testing Library

## Why not MUI v4?

The existing `microservices/admin-portal/` uses old React 16 + MUI v4 patterns.
selfcare Studio uses modern React 18 + Vite + Tailwind for a 10x faster dev
experience and a 50% smaller bundle. Do not import from `microservices/admin-portal/`.

## Quick Start

```bash
npm install
npm run dev
# Open http://localhost:5173
```

## Project Structure

```
selfcare-admin/
├── src/
│   ├── components/      # Reusable UI components
│   ├── pages/          # Route-level pages
│   │   ├── TenantsPage.tsx
│   │   ├── ThemeDesignerPage.tsx
│   │   ├── PageBuilderPage.tsx
│   │   ├── JourneyBuilderPage.tsx
│   │   ├── IntegrationBuilderPage.tsx
│   │   ├── FeatureManagerPage.tsx
│   │   ├── ReportsPage.tsx
│   │   ├── AIStudioPage.tsx
│   │   ├── UsersPage.tsx
│   │   └── AuditPage.tsx
│   ├── builder/        # Page/journey builder logic
│   ├── hooks/          # Custom hooks
│   ├── store/         # Redux store
│   ├── api/           # API clients
│   ├── tokens/        # Design tokens
│   ├── styles/        # Global styles
│   ├── utils/         # Helpers
│   └── config/        # App config
├── public/
├── tests/
├── stories/           # Storybook stories
├── tailwind.config.js
├── vite.config.ts
└── package.json
```
