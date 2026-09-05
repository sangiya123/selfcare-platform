# Visual Regression Testing Guide

This directory contains documentation and baseline screenshots for visual regression testing.

## Overview

Visual regression tests ensure UI consistency across:
- Platform versions (iOS/Android)
- Theme/branding changes
- Component library updates
- React Native version upgrades

## Tools

### Recommended: Chromatic (Storybook + Chromatic)

```bash
# Install
npm install @chromatic-com/storybook @storybook/react-native

# Run visual tests (in CI)
npm run chromatic
```

### Alternative: Jest Image Snapshot

```bash
npm install jest-image-snapshot
```

## Baseline Screenshots

The `baseline-screenshots/` directory contains reference screenshots for each screen variant.

### Directory Structure

```
visual-regression/
├── README.md
└── baseline-screenshots/
    ├── README.md
    ├── dialog-lk/           # Tenant-specific
    │   ├── login-otp.png
    │   ├── dashboard-light.png
    │   ├── dashboard-dark.png
    │   └── ...
    ├── aia-lk/              # Insurance tenant
    │   ├── login.png
    │   ├── dashboard.png
    │   └── ...
    └── themes/
        ├── dialog-light.png
        ├── dialog-dark.png
        ├── aia-light.png
        └── ...
```

## Screens to Capture

See `baseline-screenshots/README.md` for the complete list of screens requiring baseline snapshots.

## CI Integration

Add to your CI pipeline:

```yaml
# GitHub Actions example
- name: Visual Regression Tests
  run: npm run test:visual
  env:
    CHROMATIC_PROJECT_TOKEN: ${{ secrets.CHROMATIC_TOKEN }}
```

## Running Locally

```bash
# Build storybook
npm run storybook

# Open Chromatic
npx chromatic --project-token=xxx
```

## Updating Baselines

When intentional UI changes are made:

1. Make the visual change
2. Update baseline screenshots: `npm run update:baselines`
3. Review diffs in Chromatic
4. Approve new baselines

## Accessibility Checks

Visual tests should also verify:
- Color contrast (WCAG AA)
- Touch target sizes (44x44pt minimum)
- Text scaling support
