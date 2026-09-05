# Industry Packs

Per-industry provider implementations. The OMOBIO platform is **multi-industry**:
the same kernel serves clients from different verticals. Each vertical has its
own **industry pack** under `industry-packs/`:

- `telco/` — connection-centric clients (Dialog, Hutch, Airtel, ...)
- `insurance/` — policy-centric clients (AIA, future Allianz/Prudential, ...)

Each pack contains:
- A parent Maven module (`telco-industry-pack`, `insurance-industry-pack`)
- One sub-module per client (`dialog/`, `aia/`, ...)

Inside each client module:
- `pom.xml` — Maven build, parent is the industry pack parent
- `src/main/java/.../<Client>Provider.java` — provider beans implementing the
  industry-specific interface (e.g. `BalanceProvider`, `InsuranceProvider`)
- `theme/` — operator/client-specific theme tokens (JSON)

## Structure

```
industry-packs/
├── telco/                           # Telco industry pack (connection-centric)
│   ├── pom.xml
│   ├── dialog/                      # Dialog (LK) — telco client
│   │   ├── pom.xml
│   │   ├── theme/dialog-theme.json
│   │   └── providers/
│   │       ├── pom.xml
│   │       └── src/main/java/com/omobio/dialog/provider/
│   │           ├── DialogAuthProvider.java
│   │           ├── DialogBalanceProvider.java
│   │           ├── DialogProductCatalogProvider.java
│   │           ├── DialogPaymentProvider.java
│   │           ├── DialogProfileProvider.java
│   │           └── DialogNotificationProvider.java
│   ├── hutch/                       # Hutch (LK) — telco client
│   │   └── providers/
│   │       └── HutchAuthProvider.java
│   └── airtel/                      # Airtel (LK) — telco client
│       └── providers/
│           └── AirtelAuthProvider.java
└── insurance/                       # Insurance industry pack (policy-centric)
    ├── pom.xml
    └── aia/                         # AIA — multi-country insurance client
        └── providers/
            ├── pom.xml
            └── src/main/java/com/omobio/aia/provider/
                └── AIAInsuranceProvider.java
```

## Configuration

**All client-specific values come from MongoDB** (`client_integrations` collection),
configured via the Selfcare Studio admin: Integrations page. No env files contain
client URLs or credentials.

The platform supports **multi-country** configuration: the same provider class
(e.g. `AIAInsuranceProvider`) serves all AIA markets. Country-specific differences
(URL, currency, locale) are stored in the integration's `baseUrl`, `metadata`,
and `credentials` map.

## Switching environments

The platform reads `OMOBIO_ENV` (or `SPRING_PROFILES_ACTIVE`). Set it once and the entire stack picks up the right platform config from `.env.<env>`.

```bash
export OMOBIO_ENV=dev
make backend-run
```

Client/country-specific config is in the database — switch env files only for
infrastructure (DB, Redis, Kafka, observability).

## Adding a new client / country

For a brand-new telco client, copy `industry-packs/telco/dialog/` and implement
the telco provider interfaces (auth, balance, payment, etc.).

For a new insurance client, copy `industry-packs/insurance/aia/` and implement
`InsuranceProvider`.

For a new country under an existing client (e.g. AIA Vietnam), no code change is
needed — just append to `TenantSeeder.SEED_TENANTS` and `ClientIntegrationSeeder`.
See [docs/INSURANCE.md](../docs/INSURANCE.md) for the multi-country pattern.

## Removing a client

Set the client's tenant config to `status: SUSPENDED` in MongoDB. Done.
No code change, no rebuild.
