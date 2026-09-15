package com.selfcare.dialog.provider;

import com.selfcare.platform.common.adapter.CidAuthProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.MultiValueMap;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DialogCidProviderTest {

    @Mock
    private DialogHttpClient httpClient;

    private DialogCidProvider provider;

    private static final String TENANT = "dialog-lk";
    private static final String BASE = "https://mife.dialog.lk";
    private static final String CLIENT_ID = "cid-client";
    private static final String CLIENT_SECRET = "cid-secret";
    private static final String API_KEY = "dev-key";
    private static final String TOKEN_PATH = "/apicall/dio-token";
    private static final String PROFILE_PATH =
            "/apicall/crm/system/dds/selfcare-profile/v1.0.0/profiles/get-by-cid";
    private static final String WALLET_PATH =
            "/apicall/crm/system/dds/get-wallet-by-cid/v1.0.0/wallets/get-by-cid-id";

    @BeforeEach
    void setUp() {
        provider = new DialogCidProvider(httpClient);
    }

    private DialogHttpClient.DialogConfig validConfig() {
        return new DialogHttpClient.DialogConfig(
                TENANT, BASE, CLIENT_ID, CLIENT_SECRET, API_KEY, null, Map.of(), Map.of());
    }

    private String idToken(String payloadJson) {
        String header = base64("{\"alg\":\"HS512\",\"typ\":\"JWT\"}");
        String payload = base64(payloadJson);
        return header + "." + payload + ".signature";
    }

    private String base64(String json) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String standardIdToken() {
        return idToken("{\"username\":\"94777123456\",\"sub\":\"cid-uuid-1234\","
                + "\"first_name\":\"Nimal\",\"last_name\":\"Perera\","
                + "\"amr\":[\"pwd\",\"hdr\"]}");
    }

    @Test
    @DisplayName("getAdapterId returns dialog-lk")
    void adapterId() {
        assertEquals("dialog-lk", provider.getAdapterId());
    }

    @Test
    @DisplayName("returns failure when Dialog MIFE is not configured")
    void noConfigReturnsFailure() {
        when(httpClient.resolveConfig(eq(TENANT), eq("DIALOG_MIFE")))
                .thenReturn(DialogHttpClient.DialogConfig.empty(TENANT));

        CidAuthProvider.CidExchangeResult result = provider.exchangeCid(TENANT,
                new CidAuthProvider.CidExchangeRequest("auth-code-1", null));

        assertNotNull(result);
        assertFalse(result.success());
        assertNotNull(result.failureReason());
    }

    @Test
    @DisplayName("exchanges the authorization code, decodes id_token and enriches with profile + wallet")
    void exchangeCidSuccess() {
        when(httpClient.resolveConfig(eq(TENANT), eq("DIALOG_MIFE"))).thenReturn(validConfig());
        when(httpClient.getAccessToken(eq(TENANT), eq(BASE), eq(CLIENT_ID), eq(CLIENT_SECRET)))
                .thenReturn("app-token");
        when(httpClient.postForm(eq(TENANT), eq(BASE), eq(TOKEN_PATH), any(), isNull()))
                .thenReturn(Map.of(
                        "access_token", "acc-1",
                        "refresh_token", "ref-1",
                        "id_token", standardIdToken(),
                        "token_type", "Bearer",
                        "expires_in", 3600));
        when(httpClient.get(eq(TENANT), eq(BASE), eq(PROFILE_PATH), anyMap(), eq("app-token"), eq(API_KEY)))
                .thenReturn(Map.of(
                        "id", "PROF-1",
                        "version", "V3",
                        "image_url", "http://img.dialog.lk/1.png",
                        "connection_list", List.of(Map.of(
                                "number", "94777123456",
                                "name", "My Line",
                                "connection_id", "conn-1",
                                "lob", "MOBILE",
                                "connection_type", "MOBILE_PREPAID",
                                "is_primary", true))));
        when(httpClient.get(eq(TENANT), eq(BASE), eq(WALLET_PATH), anyMap(), eq("app-token"), eq(API_KEY)))
                .thenReturn(Map.of("wallet_id", "WALLET-9"));

        CidAuthProvider.CidExchangeResult result = provider.exchangeCid(TENANT,
                new CidAuthProvider.CidExchangeRequest("auth-code-1", null));

        assertTrue(result.success());
        assertEquals("94777123456", result.username());
        assertEquals("cid-uuid-1234", result.cidUuid());
        assertEquals("Nimal Perera", result.cxName());
        assertTrue(result.headerEnriched());
        assertEquals("acc-1", result.mifeAccessToken());
        assertEquals("ref-1", result.mifeRefreshToken());
        assertEquals(3600, result.mifeExpiresIn());
        assertEquals("PROF-1", result.profileId());
        assertEquals("V3", result.profileVersion());
        assertEquals("http://img.dialog.lk/1.png", result.profileImageUrl());
        assertEquals("WALLET-9", result.walletId());
        assertEquals(1, result.connections().size());
        assertEquals("conn-1", result.connections().get(0).connectionId());
        assertTrue(result.connections().get(0).isPrimary());
    }

    @Test
    @DisplayName("posts client_id, client_secret and configured redirect URI in the dio-token form")
    void dioTokenFormContainsCredentials() {
        DialogHttpClient.DialogConfig cfg = new DialogHttpClient.DialogConfig(
                TENANT, BASE, CLIENT_ID, CLIENT_SECRET, API_KEY, null,
                Map.of("cid.redirectUri", "net.omobio.dialogsc:/AuthDataReceiver"), Map.of());
        when(httpClient.resolveConfig(eq(TENANT), eq("DIALOG_MIFE"))).thenReturn(cfg);
        when(httpClient.postForm(eq(TENANT), eq(BASE), eq(TOKEN_PATH), any(), isNull()))
                .thenReturn(Map.of("id_token", idToken("{\"username\":\"94777123456\",\"sub\":\"cid-uuid-9\"}"),
                        "access_token", "acc-1"));
        when(httpClient.getAccessToken(eq(TENANT), eq(BASE), eq(CLIENT_ID), eq(CLIENT_SECRET)))
                .thenReturn("app-token");
        when(httpClient.get(eq(TENANT), eq(BASE), eq(PROFILE_PATH), anyMap(), eq("app-token"), eq(API_KEY)))
                .thenReturn(Map.of("id", "PROF-1"));

        provider.exchangeCid(TENANT, new CidAuthProvider.CidExchangeRequest("auth-code-1", null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<MultiValueMap<String, String>> captor = ArgumentCaptor.forClass(MultiValueMap.class);
        verify(httpClient).postForm(eq(TENANT), eq(BASE), eq(TOKEN_PATH), captor.capture(), isNull());
        MultiValueMap<String, String> form = captor.getValue();
        assertEquals("authorization_code", form.getFirst("grant_type"));
        assertEquals("auth-code-1", form.getFirst("code"));
        assertEquals("net.omobio.dialogsc:/AuthDataReceiver", form.getFirst("redirect_uri"));
        assertEquals(CLIENT_ID, form.getFirst("client_id"));
        assertEquals(CLIENT_SECRET, form.getFirst("client_secret"));
    }

    @Test
    @DisplayName("uses the profile wallet_id when present and skips the wallet API")
    void usesProfileWalletId() {
        when(httpClient.resolveConfig(eq(TENANT), eq("DIALOG_MIFE"))).thenReturn(validConfig());
        when(httpClient.getAccessToken(eq(TENANT), eq(BASE), eq(CLIENT_ID), eq(CLIENT_SECRET)))
                .thenReturn("app-token");
        when(httpClient.postForm(eq(TENANT), eq(BASE), eq(TOKEN_PATH), any(), isNull()))
                .thenReturn(Map.of("id_token", idToken("{\"username\":\"94777123456\",\"sub\":\"cid-uuid-1\"}"),
                        "access_token", "acc-1"));
        when(httpClient.get(eq(TENANT), eq(BASE), eq(PROFILE_PATH), anyMap(), eq("app-token"), eq(API_KEY)))
                .thenReturn(Map.of("id", "PROF-2", "wallet_id", "WALLET-PROFILE"));

        CidAuthProvider.CidExchangeResult result = provider.exchangeCid(TENANT,
                new CidAuthProvider.CidExchangeRequest("auth-code-1", null));

        assertTrue(result.success());
        assertEquals("WALLET-PROFILE", result.walletId());
        verify(httpClient, never()).get(eq(TENANT), eq(BASE), eq(WALLET_PATH), anyMap(),
                eq("app-token"), eq(API_KEY));
    }

    @Test
    @DisplayName("falls back to empty wallet when the wallet API fails")
    void walletFailureIsBestEffort() {
        when(httpClient.resolveConfig(eq(TENANT), eq("DIALOG_MIFE"))).thenReturn(validConfig());
        when(httpClient.getAccessToken(eq(TENANT), eq(BASE), eq(CLIENT_ID), eq(CLIENT_SECRET)))
                .thenReturn("app-token");
        when(httpClient.postForm(eq(TENANT), eq(BASE), eq(TOKEN_PATH), any(), isNull()))
                .thenReturn(Map.of("id_token", standardIdToken(), "access_token", "acc-1"));
        when(httpClient.get(eq(TENANT), eq(BASE), eq(PROFILE_PATH), anyMap(), eq("app-token"), eq(API_KEY)))
                .thenReturn(Map.of("id", "PROF-1"));
        when(httpClient.get(eq(TENANT), eq(BASE), eq(WALLET_PATH), anyMap(), eq("app-token"), eq(API_KEY)))
                .thenThrow(new DialogApiException(null, DialogApiException.DIALOG_GEN_503, "upstream down"));

        CidAuthProvider.CidExchangeResult result = provider.exchangeCid(TENANT,
                new CidAuthProvider.CidExchangeRequest("auth-code-1", null));

        assertTrue(result.success());
        assertEquals("", result.walletId());
    }

    @Test
    @DisplayName("returns failure when the token response lacks an id_token")
    void missingIdTokenReturnsFailure() {
        when(httpClient.resolveConfig(eq(TENANT), eq("DIALOG_MIFE"))).thenReturn(validConfig());
        when(httpClient.postForm(eq(TENANT), eq(BASE), eq(TOKEN_PATH), any(), isNull()))
                .thenReturn(Map.of("access_token", "acc-1"));

        CidAuthProvider.CidExchangeResult result = provider.exchangeCid(TENANT,
                new CidAuthProvider.CidExchangeRequest("auth-code-1", null));

        assertNotNull(result);
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("id_token"));
    }

    @Test
    @DisplayName("returns failure on a malformed id_token")
    void malformedIdTokenReturnsFailure() {
        when(httpClient.resolveConfig(eq(TENANT), eq("DIALOG_MIFE"))).thenReturn(validConfig());
        when(httpClient.postForm(eq(TENANT), eq(BASE), eq(TOKEN_PATH), any(), isNull()))
                .thenReturn(Map.of("id_token", "not-a-token", "access_token", "acc-1"));

        CidAuthProvider.CidExchangeResult result = provider.exchangeCid(TENANT,
                new CidAuthProvider.CidExchangeRequest("auth-code-1", null));

        assertNotNull(result);
        assertFalse(result.success());
    }
}