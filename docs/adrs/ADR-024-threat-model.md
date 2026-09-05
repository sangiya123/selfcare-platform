# ADR-024: Threat Model Summary (STRIDE)

## Status
Accepted — 2026-09-04

## Context
The platform processes sensitive customer data (PII, payment, location,
behavioural). The security spec requires a documented threat model that
covers all 19+ services and the data flow between them.

We adopt **STRIDE** as the classification framework because:
- Microsoft-standard, well-known by SRE and security teams
- Maps cleanly to our NFRs and test cases
- Extensible to AI-specific threats (prompt injection, model theft)

## Decision
We maintain a **STRIDE-classified threat register** for every service
in `docs/security/threat-model/`. For each trust boundary, we identify
threats and link to mitigations and tests.

### 1. STRIDE categories
- **S**poofing — impersonation of users, services, or systems
- **T**ampering — unauthorized modification of data or code
- **R**epudiation — denial of an action without audit trail
- **I**nformation disclosure — leakage of PII / secrets
- **D**enial of service — exhausting resources
- **E**levation of privilege — gaining unauthorized access

### 2. Per-service threat model
Each service doc (`threat-model/{service}.md`) includes:
- Trust boundaries (ingress/egress)
- Data classification of inputs/outputs
- STRIDE table: threat → mitigation → test
- Linked ADR(s) and NFR(s)
- Known accepted risks (with sign-off)

### 3. Cross-service threats
- **Tenant escape** — accessing data across tenants
  - Mitigation: TenantContext in every call, row-level filter
  - Test: `TenantIsolationTest` (conformance suite)
- **Provider compromise** — malicious response from upstream
  - Mitigation: schema validation, response size cap, response timeout
  - Test: `MaliciousProviderTest`
- **Token theft** — refresh token interception
  - Mitigation: short access TTL, refresh rotation, IP/UA binding
  - Test: `RefreshTokenRotationTest`
- **AI prompt injection** — user input manipulating LLM
  - Mitigation: prompt-injection detector, system-prompt lockdown, RAG scope
  - Test: `PromptInjectionTest`
- **Insider threat** — admin exfiltrating customer data
  - Mitigation: just-in-time access, audit, data export limits
  - Test: `AdminExportTest`
- **DDoS** — flooding any public endpoint
  - Mitigation: rate limit per IP/tenant, WAF, gateway-level filtering
  - Test: `RateLimitTest`, `DDoSSimulationTest`
- **Supply chain** — compromised dependency
  - Mitigation: SCA (Snyk/Trivy), signed images, SBOM, pin versions
  - Test: `DependencyVulnerabilityTest` (CI)

### 4. AI-specific threats
- **Prompt injection** (user content altering model behaviour)
- **Model theft** (extracting model weights or fine-tunes)
- **Training data leakage** (PII in completions)
- **Jailbreak** (bypassing policy filters)
- **Cost amplification** (forcing expensive inference)
- Mitigations in AI Gateway (ADR-008)

### 5. Acceptance criteria
- New service: threat model doc created before code merge
- Material change: threat model updated in same PR
- Quarterly review: security team re-validates

## Consequences

Positive:
- Documented security posture
- Traceable from threat → mitigation → test
- Compliance audit evidence
- New engineers onboard faster

Negative:
- Maintenance overhead
- Threat models can go stale
- Cross-team coordination required

## Compliance
- NFR-SEC series
- OWASP API Top 10
- OWASP LLM Top 10
- ISO 27001 Annex A
