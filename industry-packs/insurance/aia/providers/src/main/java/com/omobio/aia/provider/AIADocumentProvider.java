package com.omobio.aia.provider;

import com.omobio.platform.common.adapter.ApiAdapter;
import com.omobio.platform.common.adapter.DocumentProvider;
import com.omobio.platform.common.adapter.RegisterAdapter;
import com.omobio.platform.common.tenant.TenantConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AIA Document Provider — manages claim documents, KYC documents, and other
 * supporting files for AIA insurance customers.
 *
 * <p>Implements the canonical {@link DocumentProvider} contract from platform-common.
 * Supports document upload (multipart), list, download (signed URL), and delete
 * across all six AIA market tenants (aia-lk, aia-sg, aia-th, aia-my, aia-hk, aia-in).</p>
 *
 * <p>Per-tenant config (base URL, clientId, clientSecret, apiKey) is loaded
 * at runtime from {@link TenantConfigurationService}. Configure via
 * Selfcare Studio admin: Integrations &gt; AIA Insurance.</p>
 *
 * <p>Note: AIA's primary use case for documents is claim evidence upload.
 * The {@link AIAInsuranceProvider#uploadClaimDocument} method delegates to
 * this provider for the upload step; this provider exposes the broader
 * document-management capabilities (list, download, delete).</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "aia-lk", providerInterface = DocumentProvider.class)
@RequiredArgsConstructor
public class AIADocumentProvider implements ApiAdapter, DocumentProvider {

    private static final String INTEGRATION_TYPE = "AIA_INSURANCE";
    private static final String ADAPTER_ID = "aia-lk";

    private final TenantConfigurationService tenantConfig;
    private final AIAHttpClient httpClient;

    @Value("${omobio.aia.mock-mode:true}")
    private boolean mockMode;

    @Value("${omobio.tenant.default-id:aia-lk}")
    private String defaultTenantId;

    // Per-tenant config cache: tenantId -> AIAInsuranceProvider.Config
    private final Map<String, com.omobio.aia.provider.AIAInsuranceProvider.Config> configCache = new ConcurrentHashMap<>();
    // Per-tenant mock state for document storage
    private final Map<String, Map<String, DocumentRecord>> mockDocuments = new ConcurrentHashMap<>();

    @Override
    public String getAdapterId() {
        return defaultTenantId;
    }

    // ================================================================
    // Configuration
    // ================================================================

    private com.omobio.aia.provider.AIAInsuranceProvider.Config loadConfig(String tenantId) {
        return configCache.computeIfAbsent(tenantId, this::resolveConfig);
    }

    private com.omobio.aia.provider.AIAInsuranceProvider.Config resolveConfig(String tenantId) {
        return tenantConfig.getIntegration(tenantId, INTEGRATION_TYPE)
                .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()))
                .map(c -> new com.omobio.aia.provider.AIAInsuranceProvider.Config(
                        tenantId,
                        c.getBaseUrl(),
                        c.getCredential("clientId"),
                        c.getCredential("clientSecret"),
                        c.getCredential("apiKey"),
                        c.getMetadata()))
                .orElseGet(() -> com.omobio.aia.provider.AIAInsuranceProvider.Config.empty(tenantId));
    }

    // ================================================================
    // DocumentProvider implementation
    // ================================================================

    @Override
    public UploadResult uploadDocument(String tenantId, String customerId, String fileName,
                                       byte[] content, String mimeType, DocumentMetadata metadata) {
        if (mockMode) {
            return uploadDocumentMock(tenantId, customerId, fileName, content, mimeType, metadata);
        }

        com.omobio.aia.provider.AIAInsuranceProvider.Config cfg = loadConfig(tenantId);
        if (!cfg.isValid()) {
            log.warn("AIA Insurance not configured for tenant={}", tenantId);
            return new UploadResult(false, null, fileName, mimeType, (long) (content == null ? 0 : content.length),
                    null, "No AIA Insurance integration configured");
        }

        try {
            String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());

            // Multipart upload via raw HTTP
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.parseMediaType(mimeType));
            headers.set("X-File-Name", Base64.getEncoder().encodeToString(
                    fileName.getBytes(StandardCharsets.UTF_8)));
            if (metadata != null) {
                if (metadata.category() != null) headers.set("X-Doc-Category", metadata.category());
                if (metadata.relatedEntityId() != null) headers.set("X-Entity-Id", metadata.relatedEntityId());
                if (metadata.relatedEntityType() != null) headers.set("X-Entity-Type", metadata.relatedEntityType());
                if (metadata.description() != null) headers.set("X-Description", metadata.description());
            }

            String url = cfg.baseUrl() + "/api/v1/documents/upload";
            RestTemplate rt = new RestTemplate();
            HttpEntity<byte[]> req = new HttpEntity<>(content != null ? content : new byte[0], headers);
            ResponseEntity<Map> resp = rt.exchange(url, org.springframework.http.HttpMethod.POST, req, Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null
                    && resp.getBody().get("data") instanceof Map<?, ?> d) {
                return new UploadResult(
                        true,
                        (String) d.get("documentId"),
                        fileName,
                        mimeType,
                        (long) (content == null ? 0 : content.length),
                        Instant.now(),
                        null
                );
            }
            return new UploadResult(false, null, fileName, mimeType,
                    (long) (content == null ? 0 : content.length), null, "Unexpected response");
        } catch (Exception e) {
            log.error("AIADocumentProvider.uploadDocument failed for tenant={} customer={} file={}: {}",
                    tenantId, customerId, fileName, e.getMessage());
            return new UploadResult(false, null, fileName, mimeType,
                    (long) (content == null ? 0 : content.length), null, e.getMessage());
        }
    }

    @Override
    public List<DocumentSummary> listDocuments(String tenantId, String customerId, String category) {
        if (mockMode) {
            return listDocumentsMock(tenantId, customerId, category);
        }

        com.omobio.aia.provider.AIAInsuranceProvider.Config cfg = loadConfig(tenantId);
        if (!cfg.isValid()) {
            log.warn("AIA Insurance not configured for tenant={}", tenantId);
            return List.of();
        }

        var queryParams = new HashMap<String, Object>();
        queryParams.put("customerId", customerId);
        if (category != null) queryParams.put("category", category);

        try {
            String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
            List<Map<String, Object>> data = httpClient.getList(tenantId, cfg.baseUrl(),
                    "/api/v1/documents", queryParams, token);
            return data.stream().map(m -> new DocumentSummary(
                    (String) m.get("documentId"),
                    customerId,
                    (String) m.get("fileName"),
                    (String) m.get("mimeType"),
                    m.get("sizeBytes") != null ? ((Number) m.get("sizeBytes")).longValue() : null,
                    (String) m.get("category"),
                    (String) m.get("relatedEntityId"),
                    (String) m.get("relatedEntityType"),
                    parseInstant(m.get("uploadedAt")),
                    parseInstant(m.get("expiresAt"))
            )).toList();
        } catch (Exception e) {
            log.error("AIADocumentProvider.listDocuments failed for tenant={} customer={}: {}",
                    tenantId, customerId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public String getDownloadUrl(String tenantId, String documentId) {
        if (mockMode) {
            return "https://mock-aia.local/documents/" + documentId + "?signature=mock";
        }

        com.omobio.aia.provider.AIAInsuranceProvider.Config cfg = loadConfig(tenantId);
        if (!cfg.isValid()) {
            log.warn("AIA Insurance not configured for tenant={}", tenantId);
            return null;
        }

        try {
            String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(),
                    "/api/v1/documents/" + documentId + "/download-url", null, token);
            if (data == null) return null;
            Object inner = data.get("data");
            if (inner instanceof Map<?, ?> d) {
                return (String) ((Map<String, Object>) d).get("url");
            }
            return (String) data.get("url");
        } catch (Exception e) {
            log.error("AIADocumentProvider.getDownloadUrl failed for tenant={} documentId={}: {}",
                    tenantId, documentId, e.getMessage());
            return null;
        }
    }

    @Override
    public boolean deleteDocument(String tenantId, String documentId) {
        if (mockMode) {
            Map<String, DocumentRecord> store = mockDocuments.get(tenantId);
            if (store == null) return false;
            return store.remove(documentId) != null;
        }

        com.omobio.aia.provider.AIAInsuranceProvider.Config cfg = loadConfig(tenantId);
        if (!cfg.isValid()) {
            log.warn("AIA Insurance not configured for tenant={}", tenantId);
            return false;
        }

        try {
            String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
            httpClient.delete(tenantId, cfg.baseUrl(),
                    "/api/v1/documents/" + documentId, token);
            return true;
        } catch (Exception e) {
            log.error("AIADocumentProvider.deleteDocument failed for tenant={} documentId={}: {}",
                    tenantId, documentId, e.getMessage());
            return false;
        }
    }

    // ================================================================
    // Mock mode helpers
    // ================================================================

    private UploadResult uploadDocumentMock(String tenantId, String customerId, String fileName,
                                            byte[] content, String mimeType, DocumentMetadata metadata) {
        Map<String, DocumentRecord> store = mockDocuments.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
        String docId = "AIA-DOC-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        String category = metadata != null ? metadata.category() : "OTHER";
        String relatedId = metadata != null ? metadata.relatedEntityId() : null;
        String relatedType = metadata != null ? metadata.relatedEntityType() : null;
        store.put(docId, new DocumentRecord(docId, customerId, fileName, mimeType,
                (long) (content == null ? 0 : content.length), category, relatedId, relatedType,
                Instant.now(), null));
        return new UploadResult(true, docId, fileName, mimeType,
                (long) (content == null ? 0 : content.length), Instant.now(), null);
    }

    private List<DocumentSummary> listDocumentsMock(String tenantId, String customerId, String category) {
        Map<String, DocumentRecord> store = mockDocuments.getOrDefault(tenantId, Map.of());
        return store.values().stream()
                .filter(r -> r.customerId().equals(customerId))
                .filter(r -> category == null || category.equals(r.category()))
                .<DocumentSummary>map(r -> new DocumentSummary(
                        r.documentId(), r.customerId(), r.fileName(), r.mimeType(),
                        r.sizeBytes(), r.category(), r.relatedEntityId(), r.relatedEntityType(),
                        r.uploadedAt(), r.expiresAt()))
                .toList();
    }

    // ================================================================
    // Helpers
    // ================================================================

    private Instant parseInstant(Object v) {
        if (v == null) return null;
        if (v instanceof Instant i) return i;
        if (v instanceof Number n) return Instant.ofEpochSecond(n.longValue());
        if (v instanceof String s) {
            try { return Instant.parse(s); } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Internal mock record.
     */
    private record DocumentRecord(
            String documentId,
            String customerId,
            String fileName,
            String mimeType,
            Long sizeBytes,
            String category,
            String relatedEntityId,
            String relatedEntityType,
            Instant uploadedAt,
            Instant expiresAt
    ) {}
}
