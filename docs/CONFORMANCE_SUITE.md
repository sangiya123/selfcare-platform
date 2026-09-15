# Conformance Suite

The conformance suite is a comprehensive test that validates a deployment
or a new client is correctly integrated with the platform.

## What's tested

| Category | What it checks |
|---|---|
| **Tenant isolation** | Tenant A cannot access Tenant B's data |
| **Auth** | Login, refresh, logout work for each tenant |
| **API contracts** | All endpoints return expected shapes |
| **Dashboard partial response** | Slow widgets don't break the dashboard |
| **Config compiler** | All themes, layouts, journeys compile |
| **Provider adapters** | All providers return correct shapes |
| **Rate limiting** | Limits are enforced |
| **Circuit breaker** | Open when downstream is slow |
| **Audit trail** | All state-changing actions are recorded |
| **Cross-connection ops** | ADR-006 authorization works |
| **Step-up auth** | Sensitive ops require re-auth |
| **Replay detection** | Refresh token reuse is detected |

## Running

### Local
```bash
cd tests/conformance
mvn test
```

### Against a deployed environment
```bash
export SELFCARE_BASE_URL=https://staging.selfcare.io
export SELFCARE_TENANT=dialog-lk
mvn test -Denv=stg
```

### In CI
```yaml
# .github/workflows/conformance.yml
- name: Run conformance suite
  run: |
    mvn -B -f tests/conformance/pom.xml test \
        -Dselfcare.base-url=${{ secrets.STAGING_URL }} \
        -Dselfcare.tenant=${{ secrets.STAGING_TENANT }}
```

## Test cases

### Tenant isolation

```java
@Test
void tenantA_cannotReadTenantB_bills() {
    String tokenA = login("dialog-lk", "user-a");
    String tokenB = login("aia-lk", "user-b");

    // Try to read tenant B's bills using tenant A's token
    assertThrows(ForbiddenException.class, () ->
        apiClient.get("/api/v1/bills", Map.of(
            "X-Tenant-Id", "aia-lk",
            "Authorization", "Bearer " + tokenA
        ))
    );
}
```

### Partial response

```java
@Test
void slowWidget_doesNotBlockOthers() {
    // Mock one widget to take 1s
    mockServer.when(balanceRequest()).respond(after(1000), balanceResponse());

    long start = System.currentTimeMillis();
    DashboardResponse response = apiClient.getDashboard();
    long elapsed = System.currentTimeMillis() - start;

    assertThat(elapsed).isLessThan(700); // overall deadline 500ms + 200ms grace
    assertThat(response.getWidgets().get("balance").getStatus())
        .isIn(TIMEOUT, ERROR);
    assertThat(response.getWidgets().get("banners").getStatus())
        .isEqualTo(SUCCESS);
}
```

### Refresh token rotation

```java
@Test
void refreshToken_reuseIsRejected() {
    String oldRefresh = login(...).refreshToken;
    String newRefresh = apiClient.refresh(oldRefresh).refreshToken;

    // Reuse old token → should fail
    assertThrows(UnauthorizedException.class, () ->
        apiClient.refresh(oldRefresh)
    );

    // Entire session should be revoked
    assertThrows(UnauthorizedException.class, () ->
        apiClient.refresh(newRefresh)
    );
}
```

### Cross-connection ops

```java
@Test
void connectionNotInLinkedList_cannotBeAuthorized() {
    String primaryToken = login("dialog-lk", "primary-user");
    String otherConnectionId = "CONN-9999";  // not in linked list

    assertThrows(ForbiddenException.class, () ->
        apiClient.post("/api/v1/payments/charge", Map.of(
            "fromConnectionId", "CONN-0001",
            "toConnectionId", otherConnectionId,
            "amount", 1000
        ), primaryToken)
    );
}
```

### Step-up auth

```java
@Test
void highValuePayment_requiresStepUp() {
    String token = login(...);
    PaymentRequest req = new PaymentRequest(/* amount > threshold */);

    Response response = apiClient.post("/api/v1/payments/charge", req, token);
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getError().getCode()).isEqualTo("STEP_UP_REQUIRED");

    // Re-auth with OTP
    String stepUpToken = reAuthWithOtp(token);
    Response second = apiClient.post("/api/v1/payments/charge", req, stepUpToken);
    assertThat(second.getStatus()).isEqualTo(200);
}
```

### Config compiler

```java
@Test
void unknownComponent_isRejected() {
    LayoutDocument layout = new LayoutDocument()
        .addSection(new Section().component("UnknownWidget"));

    assertThrows(CompilationException.class, () ->
        configService.publish("dialog-lk", "home", layout)
    );
}
```

## Reports

Results are exported as:
- HTML report (for humans)
- JUnit XML (for CI)
- JSON (for dashboards)

## Adding a new test

1. Add to `tests/conformance/src/test/java/...`
2. Tag with `@Tag("tenant-isolation")` (or other category)
3. Run locally
4. Submit PR

## Mandatory tests for new clients

Before going live, all of these must pass:
- [ ] Tenant isolation
- [ ] Auth flows
- [ ] Provider adapters (all 4-6 adapters)
- [ ] Cross-connection ops (if multi-connection)
- [ ] Step-up auth (if applicable)
- [ ] Config compiler
- [ ] Dashboard partial response
- [ ] Audit trail
