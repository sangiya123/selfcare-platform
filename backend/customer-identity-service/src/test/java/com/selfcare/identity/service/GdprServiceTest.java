package com.selfcare.identity.service;

import com.selfcare.identity.domain.ConsentRecord;
import com.selfcare.identity.domain.DataErasureRequest;
import com.selfcare.identity.repository.ConsentRecordRepository;
import com.selfcare.identity.repository.DataErasureRequestRepository;
import com.selfcare.platform.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GdprServiceTest {

    @Mock private ConsentRecordRepository consentRepository;
    @Mock private DataErasureRequestRepository erasureRepository;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    private GdprService service;

    @BeforeEach
    void setUp() {
        service = new GdprService(consentRepository, erasureRepository, kafkaTemplate);
        ReflectionTestUtils.setField(service, "erasureTopic", "identity.user.erasure");

        TenantContext ctx = new TenantContext();
        ctx.setTenantId("dialog-lk");
        ctx.setUserId("user-1");
        TenantContext.set(ctx);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ======================================================================
    // recordConsent
    // ======================================================================

    @Test
    @DisplayName("recordConsent creates a new record when none exists")
    void recordConsent_newRecord() {
        when(consentRepository.findActive("dialog-lk", "user-1", "MARKETING_EMAIL"))
                .thenReturn(Optional.empty());
        when(consentRepository.save(any(ConsentRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ConsentRecord result = service.recordConsent(
                "user-1", "MARKETING_EMAIL", true, "1.0.0", "signup-screen",
                "203.0.113.50", "Mozilla/5.0");

        assertThat(result.getPurpose()).isEqualTo("MARKETING_EMAIL");
        assertThat(result.isGranted()).isTrue();
        assertThat(result.getVersion()).isEqualTo("1.0.0");
        assertThat(result.getSource()).isEqualTo("signup-screen");
        assertThat(result.getCapturedAt()).isNotNull();
        assertThat(result.getSupersededAt()).isNull();
    }

    @Test
    @DisplayName("recordConsent supersedes existing record on version change")
    void recordConsent_supersedesOldRecord() {
        ConsentRecord existing = ConsentRecord.builder()
                .id(UUID.randomUUID())
                .tenantId("dialog-lk")
                .userId("user-1")
                .purpose("MARKETING_EMAIL")
                .version("1.0.0")
                .granted(true)
                .capturedAt(Instant.parse("2025-01-01T00:00:00Z"))
                .build();
        when(consentRepository.findActive("dialog-lk", "user-1", "MARKETING_EMAIL"))
                .thenReturn(Optional.of(existing));
        when(consentRepository.save(any(ConsentRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ConsentRecord result = service.recordConsent(
                "user-1", "MARKETING_EMAIL", true, "2.0.0", "renewal-prompt",
                "203.0.113.50", "Mozilla/5.0");

        assertThat(existing.getSupersededAt()).isNotNull();
        assertThat(result.getVersion()).isEqualTo("2.0.0");
    }

    @Test
    @DisplayName("recordConsent is idempotent for same version + decision")
    void recordConsent_idempotent() {
        ConsentRecord existing = ConsentRecord.builder()
                .id(UUID.randomUUID())
                .tenantId("dialog-lk")
                .userId("user-1")
                .purpose("TERMS_OF_SERVICE")
                .version("1.0.0")
                .granted(true)
                .capturedAt(Instant.now())
                .build();
        when(consentRepository.findActive("dialog-lk", "user-1", "TERMS_OF_SERVICE"))
                .thenReturn(Optional.of(existing));

        ConsentRecord result = service.recordConsent(
                "user-1", "TERMS_OF_SERVICE", true, "1.0.0", "signup",
                "203.0.113.50", "Mozilla/5.0");

        // No new record saved
        verify(consentRepository, never()).save(any());
        assertThat(result).isSameAs(existing);
    }

    // ======================================================================
    // hasConsent
    // ======================================================================

    @Test
    @DisplayName("hasConsent returns true when active granted consent exists")
    void hasConsent_granted() {
        ConsentRecord record = ConsentRecord.builder()
                .purpose("ANALYTICS")
                .granted(true)
                .build();
        when(consentRepository.findActive("dialog-lk", "user-1", "ANALYTICS"))
                .thenReturn(Optional.of(record));

        assertThat(service.hasConsent("user-1", "ANALYTICS")).isTrue();
    }

    @Test
    @DisplayName("hasConsent returns false when consent is denied")
    void hasConsent_denied() {
        ConsentRecord record = ConsentRecord.builder()
                .purpose("ANALYTICS")
                .granted(false)
                .build();
        when(consentRepository.findActive("dialog-lk", "user-1", "ANALYTICS"))
                .thenReturn(Optional.of(record));

        assertThat(service.hasConsent("user-1", "ANALYTICS")).isFalse();
    }

    @Test
    @DisplayName("hasConsent returns false when no record exists")
    void hasConsent_noRecord() {
        when(consentRepository.findActive("dialog-lk", "user-1", "UNKNOWN"))
                .thenReturn(Optional.empty());

        assertThat(service.hasConsent("user-1", "UNKNOWN")).isFalse();
    }

    // ======================================================================
    // requestErasure
    // ======================================================================

    @Test
    @DisplayName("requestErasure creates a PENDING request")
    void requestErasure_pending() {
        when(erasureRepository.findActiveForUser(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(erasureRepository.save(any(DataErasureRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DataErasureRequest req = service.requestErasure(
                "user-1", "GDPR Article 17", "user-1", "203.0.113.50", "Mozilla/5.0");

        assertThat(req.getStatus()).isEqualTo("PENDING");
        assertThat(req.getUserIdHash()).hasSize(64); // SHA-256 hex
        assertThat(req.getRequestedAt()).isNotNull();
        assertThat(req.getReason()).isEqualTo("GDPR Article 17");
    }

    @Test
    @DisplayName("requestErasure is idempotent — returns existing request")
    void requestErasure_idempotent() {
        DataErasureRequest existing = DataErasureRequest.builder()
                .id(UUID.randomUUID())
                .status("PENDING")
                .build();
        when(erasureRepository.findActiveForUser(anyString(), anyString()))
                .thenReturn(Optional.of(existing));

        DataErasureRequest req = service.requestErasure(
                "user-1", null, "user-1", null, null);

        assertThat(req).isSameAs(existing);
        verify(erasureRepository, never()).save(any());
    }

    @Test
    @DisplayName("requestErasure rejects empty userId")
    void requestErasure_blankUserId() {
        assertThatThrownBy(() -> service.requestErasure(
                "  ", null, "user-1", null, null))
                .isInstanceOf(com.selfcare.platform.common.web.BadRequestException.class);
    }

    // ======================================================================
    // markErasure lifecycle
    // ======================================================================

    @Test
    @DisplayName("markErasureInProgress sets status and startedAt")
    void markErasureInProgress() {
        UUID id = UUID.randomUUID();
        DataErasureRequest req = DataErasureRequest.builder()
                .id(id).status("PENDING").build();
        when(erasureRepository.findById(id)).thenReturn(Optional.of(req));
        when(erasureRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markErasureInProgress(id, "account-service,notification-service");

        assertThat(req.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(req.getStartedAt()).isNotNull();
        assertThat(req.getNotifiedServices()).contains("account-service");
    }

    @Test
    @DisplayName("markErasureCompleted sets status and completedAt")
    void markErasureCompleted() {
        UUID id = UUID.randomUUID();
        DataErasureRequest req = DataErasureRequest.builder()
                .id(id).status("IN_PROGRESS").build();
        when(erasureRepository.findById(id)).thenReturn(Optional.of(req));
        when(erasureRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markErasureCompleted(id);

        assertThat(req.getStatus()).isEqualTo("COMPLETED");
        assertThat(req.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("markErasureFailed sets status and failureReason")
    void markErasureFailed() {
        UUID id = UUID.randomUUID();
        DataErasureRequest req = DataErasureRequest.builder()
                .id(id).status("IN_PROGRESS").build();
        when(erasureRepository.findById(id)).thenReturn(Optional.of(req));
        when(erasureRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markErasureFailed(id, "downstream timeout");

        assertThat(req.getStatus()).isEqualTo("FAILED");
        assertThat(req.getFailureReason()).isEqualTo("downstream timeout");
    }

    // ======================================================================
    // emitErasureEvent
    // ======================================================================

    @Test
    @DisplayName("emitErasureEvent publishes to Kafka")
    void emitErasureEvent() {
        DataErasureRequest req = DataErasureRequest.builder()
                .id(UUID.randomUUID())
                .tenantId("dialog-lk")
                .userIdHash("abc123")
                .status("PENDING")
                .requestedAt(Instant.now())
                .build();

        service.emitErasureEvent(req, "user-1");

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), valueCaptor.capture());
        assertThat(topicCaptor.getValue()).isEqualTo("identity.user.erasure");
        assertThat(keyCaptor.getValue()).isEqualTo(req.getId().toString());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> payload = (java.util.Map<String, Object>) valueCaptor.getValue();
        assertThat(payload.get("eventType")).isEqualTo("USER_DATA_ERASURE");
        assertThat(payload.get("tenantId")).isEqualTo("dialog-lk");
        assertThat(payload.get("userId")).isEqualTo("user-1");
    }
}
