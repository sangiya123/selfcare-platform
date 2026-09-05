# ADR-015: Mobile Security Baseline

## Status
Accepted — 2026-09-04

## Context
The mobile apps (Selfcare App for telco/insurance) handle sensitive
authentication tokens and customer data. The security spec requires:

- Secure token storage (Keychain on iOS, EncryptedSharedPreferences/Keystore on Android)
- Certificate pinning with safe rotation
- Root/jailbreak/Frida detection as risk signals (not sole defense)
- Code signing and store integrity checks
- Encrypted sensitive local cache
- Screenshot/clipboard controls for sensitive screens
- Privacy-safe logging (no PII, no tokens)

## Decision
We adopt a **defense-in-depth** mobile security baseline:

### 1. Token storage
- iOS: Keychain (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`)
- Android: EncryptedSharedPreferences (AES-256-GCM) + Tink
- Refresh token never stored in plaintext

### 2. Certificate pinning
- Pin 2 SPKIs per operator (primary + backup)
- Pin rotation via app updates (not OTA config)
- Fail-closed: if pin validation fails and no backup available, log out and prompt re-auth
- "Pin bypass" toggle in dev builds only (never enabled in release)

### 3. Tamper detection
- Jailbreak (iOS) / Root (Android) — flag as risk, do not block
- Frida / instrumentation framework detection — flag as risk, do not block
- App signature verification (Android `PackageManager.GET_SIGNING_CERTIFICATES`)
- Replay protection: nonce + timestamp on every state-changing call

### 4. Code signing
- iOS: signed with Apple Developer cert in CI
- Android: signed with release key in CI; Play Integrity API check on launch

### 5. Logging
- Structured logger (`@omobio/mobile-logger`)
- Token, password, OTP, MSISDN masked at logger level
- Logs never sent to console in release builds
- Crash logs (Sentry/Crashlytics) include correlation ID but no PII

### 6. Sensitive screen controls
- FLAG_SECURE on Android for screens showing OTP, account balance, payment confirm
- Blur overlay on iOS in `applicationWillResignActive` for sensitive screens
- Disable clipboard copy for OTP screen

### 7. Local cache encryption
- User session cache in encrypted MMKV/Realm
- Encryption key derived from device-bound key
- Wipe local cache on logout

## Consequences

Positive:
- Matches security spec
- Stolen device does not equal stolen account
- Audit trail for every sensitive operation

Negative:
- Increased APK/IPA size (~2-4MB for security libs)
- Slight app start time impact (~50-100ms)
- Pin rotation requires app release

## Tools
- `react-native-keychain` (iOS Keychain bridge)
- `react-native-encrypted-storage` (Android)
- `react-native-mmkv` with encryption
- `react-native-device-info` (jailbreak/root detection)
- `react-native-ssl-pinning` (cert pinning)
- `react-native-app-integrity` (Play Integrity)

## Tests
- Mobile security E2E tests: token storage, jailbreak simulation
- Pin rotation test (mock pin server)
- Replay protection test (nonce reuse should fail)
