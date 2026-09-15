package com.selfcare.identity.service;

import com.selfcare.identity.domain.Session;
import com.selfcare.identity.security.JwtIssuer;
import com.selfcare.platform.common.adapter.ApiAdapterRegistry;
import com.selfcare.platform.common.adapter.CidAuthProvider;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.UnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CidAuthServiceTest {

    @Mock private ApiAdapterRegistry<CidAuthProvider> registry;
    @Mock private CidAuthProvider provider;
    @Mock private CustomerSessionService sessionService;
    @Mock private JwtIssuer jwtIssuer;

    private CidAuthService service;

    @BeforeEach
    void setUp() {
        service = new CidAuthService(registry, sessionService, jwtIssuer);

        TenantContext ctx = new TenantContext();
        ctx.setTenantId("dialog-lk");
        ctx.setUserId("u-1");
        TenantContext.set(ctx);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private CidAuthProvider.CidExchangeResult successIdentity() {
        return new CidAuthProvider.CidExchangeResult(
                true, null,
                "94777123456", "cid-uuid-1", "Nimal Perera", true,
                "mife-acc", "mife-ref", "id-token", 3600,
                "PROF-1", "V3", "http://img.dialog.lk/1.png", "WALLET-1",
                List.of(new CidAuthProvider.ConnectionDetail(
                        "94777123456", "My Line", "conn-1", "ctr-1", "MOBILE",
                        "MOBILE_PREPAID", "NIC", "990123456V",
                        true, true, "2026-01-01 10:00:00", "2026-01-01 10:00:00",
                        false, "GSM", false, null, false, false, "ACTIVE")));
    }

    private Session sessionFor(String sessionId) {
        return Session.builder()
                .sessionId(sessionId)
                .tenantId("dialog-lk")
                .userId("CID:cid-uuid-1")
                .tokenFamilyId("fam-1")
                .status("ACTIVE")
                .issuedAt(Instant.now())
                .lastUsedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(2592000))
                .build();
    }

    @Test
    @DisplayName("resolves the tenant provider, creates a CID session and issues tokens")
    void exchangeSuccess() {
        when(registry.getProvider("dialog-lk")).thenReturn(provider);
        when(provider.exchangeCid(eq("dialog-lk"),
                eq(new CidAuthProvider.CidExchangeRequest("auth-code-1", null))))
                .thenReturn(successIdentity());
        Session session = sessionFor("sess-cid-1");
        when(sessionService.createSession(
                eq("CID:cid-uuid-1"), eq("dev-1"), eq("iPhone 15"), eq("10.0.0.1")))
                .thenReturn(session);
        when(jwtIssuer.issueForSession(session))
                .thenReturn(new JwtIssuer.IssuedTokens("AT-1", "RT-1", 86400, 2592000));

        CidAuthService.CidLoginResult result = service.exchange(
                "auth-code-1", null, "dev-1", "iPhone 15", "10.0.0.1");

        assertThat(result.sessionId()).isEqualTo("sess-cid-1");
        assertThat(result.tokens().accessToken()).isEqualTo("AT-1");
        assertThat(result.tokens().refreshToken()).isEqualTo("RT-1");
        assertThat(result.identity().success()).isTrue();
        assertThat(result.identity().cidUuid()).isEqualTo("cid-uuid-1");
        assertThat(result.identity().cxName()).isEqualTo("Nimal Perera");
        assertThat(result.identity().connections()).hasSize(1);
        verify(sessionService).createSession(
                "CID:cid-uuid-1", "dev-1", "iPhone 15", "10.0.0.1");
    }

    @Test
    @DisplayName("propagates the provider redirect URI when given")
    void exchangePassesRedirectUri() {
        when(registry.getProvider("dialog-lk")).thenReturn(provider);
        when(provider.exchangeCid(eq("dialog-lk"),
                eq(new CidAuthProvider.CidExchangeRequest("auth-code-1", "net.omobio.dialogsc:/AuthDataReceiver"))))
                .thenReturn(successIdentity());
        Session session = sessionFor("sess-cid-2");
        when(sessionService.createSession(anyString(), any(), any(), any())).thenReturn(session);
        when(jwtIssuer.issueForSession(session))
                .thenReturn(new JwtIssuer.IssuedTokens("AT-2", "RT-2", 86400, 2592000));

        service.exchange("auth-code-1", "net.omobio.dialogsc:/AuthDataReceiver",
                null, null, "10.0.0.1");

        verify(provider).exchangeCid(eq("dialog-lk"),
                eq(new CidAuthProvider.CidExchangeRequest("auth-code-1", "net.omobio.dialogsc:/AuthDataReceiver")));
    }

    @Test
    @DisplayName("rejects the login when the provider cannot resolve the CID")
    void exchangeProviderFailure() {
        when(registry.getProvider("dialog-lk")).thenReturn(provider);
        when(provider.exchangeCid(eq("dialog-lk"), any()))
                .thenReturn(new CidAuthProvider.CidExchangeResult(
                        false, "Invalid authorization code",
                        null, null, null, false, null, null, null, null,
                        null, null, null, null, List.of()));

        assertThatThrownBy(() -> service.exchange("bad-code", null, null, null, "10.0.0.1"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid authorization code");

        verify(sessionService, never()).createSession(any(), any(), any(), any());
    }
}