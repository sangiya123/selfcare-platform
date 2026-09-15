package com.selfcare.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfcare.approval.config.ApprovalServiceConfig;
import com.selfcare.approval.domain.ApprovalAction;
import com.selfcare.approval.domain.ApprovalRequest;
import com.selfcare.approval.domain.ApprovalStatus;
import com.selfcare.approval.repository.ApprovalRequestRepository;
import com.selfcare.approval.service.ApprovalWorkflowService;
import com.selfcare.platform.common.web.BadRequestException;
import com.selfcare.platform.common.web.ConflictException;
import com.selfcare.platform.common.web.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalWorkflowServiceTest {

    @Mock private ApprovalRequestRepository repository;
    @Mock private RedisTemplate<String, Object> redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ApprovalServiceConfig config;
    private ApprovalWorkflowService service;

    @BeforeEach
    void setUp() {
        config = new ApprovalServiceConfig();
        config.setDefaultExpiryHours(72);
        service = new ApprovalWorkflowService(repository, config, objectMapper, redisTemplate);
    }

    // ------------------- isApprovalRequired -------------------

    @Test
    @DisplayName("All 9 high-risk actions require approval")
    void allActionsRequireApproval() {
        for (ApprovalAction action : ApprovalAction.values()) {
            assertThat(service.isApprovalRequired(action.name()))
                    .as("Action %s should require approval", action)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Unknown actions do not require approval")
    void unknownActionsDoNotRequire() {
        assertThat(service.isApprovalRequired("POTATO")).isFalse();
        assertThat(service.isApprovalRequired("")).isFalse();
    }

    // ------------------- submitForApproval -------------------

    @Test
    @DisplayName("Submit creates a PENDING request with expiry")
    void submitCreatesPending() {
        when(repository.save(any(ApprovalRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ApprovalRequest result = service.submitForApproval(
                "tenant-A", "LAYOUT_PUBLISH", "layout", "L1",
                "user-1", "[email protected]",
                Map.of("before", "draft", "after", "v2"),
                "corr-123");

        ArgumentCaptor<ApprovalRequest> captor = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(repository).save(captor.capture());
        ApprovalRequest saved = captor.getValue();

        assertThat(saved.getRequestId()).isNotBlank();
        assertThat(saved.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(saved.getTenantId()).isEqualTo("tenant-A");
        assertThat(saved.getAction()).isEqualTo("LAYOUT_PUBLISH");
        assertThat(saved.getResourceType()).isEqualTo("layout");
        assertThat(saved.getResourceId()).isEqualTo("L1");
        assertThat(saved.getRequesterId()).isEqualTo("user-1");
        assertThat(saved.getRequesterEmail()).isEqualTo("[email protected]");
        assertThat(saved.getChangeSnapshot()).contains("before").contains("after");
        assertThat(saved.getExpiresAt()).isNotNull();
        assertThat(saved.getCorrelationId()).isEqualTo("corr-123");
        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.PENDING);
    }

    @Test
    @DisplayName("Submit rejects unknown action")
    void submitRejectsUnknown() {
        assertThatThrownBy(() -> service.submitForApproval(
                "t", "MYSTERY", "r", "r1", "u", "e", null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("MYSTERY");

        verify(repository, never()).save(any());
    }

    // ------------------- approve -------------------

    @Test
    @DisplayName("Approve transitions PENDING to APPROVED")
    void approveTransitions() {
        ApprovalRequest existing = newPending("req-1", "LAYOUT_PUBLISH", "user-1");
        when(repository.findByRequestId("req-1")).thenReturn(Optional.of(existing));
        when(repository.save(any(ApprovalRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.approve("req-1", "approver-9", "[email protected]", "lgtm", "JIRA-42");

        assertThat(existing.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(existing.getApproverId()).isEqualTo("approver-9");
        assertThat(existing.getApproverEmail()).isEqualTo("[email protected]");
        assertThat(existing.getComments()).isEqualTo("lgtm");
        assertThat(existing.getTicketReference()).isEqualTo("JIRA-42");
        assertThat(existing.getDecidedAt()).isNotNull();
    }

    @Test
    @DisplayName("Approve on non-pending fails with conflict")
    void approveNonPendingFails() {
        ApprovalRequest existing = newPending("req-1", "LAYOUT_PUBLISH", "user-1");
        existing.setStatus(ApprovalStatus.APPROVED);
        when(repository.findByRequestId("req-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.approve(
                "req-1", "approver-9", "[email protected]", "lgtm", null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("Approve on missing request fails with not found")
    void approveMissingFails() {
        when(repository.findByRequestId("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.approve(
                "nope", "approver", "[email protected]", null, null))
                .isInstanceOf(NotFoundException.class);
    }

    // ------------------- reject -------------------

    @Test
    @DisplayName("Reject transitions PENDING to REJECTED")
    void rejectTransitions() {
        ApprovalRequest existing = newPending("req-1", "LAYOUT_PUBLISH", "user-1");
        when(repository.findByRequestId("req-1")).thenReturn(Optional.of(existing));
        when(repository.save(any(ApprovalRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.reject("req-1", "approver-9", "[email protected]", "broken layout");

        assertThat(existing.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(existing.getComments()).isEqualTo("broken layout");
        assertThat(existing.getDecidedAt()).isNotNull();
    }

    // ------------------- cancel -------------------

    @Test
    @DisplayName("Cancel by original requester transitions to CANCELLED")
    void cancelByOwnerTransitions() {
        ApprovalRequest existing = newPending("req-1", "LAYOUT_PUBLISH", "user-1");
        when(repository.findByRequestId("req-1")).thenReturn(Optional.of(existing));
        when(repository.save(any(ApprovalRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.cancel("req-1", "user-1", "duplicate request");

        assertThat(existing.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
        assertThat(existing.getComments()).isEqualTo("duplicate request");
    }

    @Test
    @DisplayName("Cancel by non-requester is rejected")
    void cancelByOtherFails() {
        ApprovalRequest existing = newPending("req-1", "LAYOUT_PUBLISH", "user-1");
        when(repository.findByRequestId("req-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.cancel("req-1", "user-2", "no"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only the original requester");
    }

    // ------------------- scheduled expiry -------------------

    @Test
    @DisplayName("Expire sweeps stale PENDING requests")
    void expireStalePending() {
        ApprovalRequest stale = newPending("req-1", "LAYOUT_PUBLISH", "user-1");
        stale.setExpiresAt(Instant.now().minusSeconds(60));
        when(repository.findStalePendingAllTenants(any(Instant.class)))
                .thenReturn(List.of(stale));
        when(repository.save(any(ApprovalRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.expirePendingRequests();

        assertThat(stale.getStatus()).isEqualTo(ApprovalStatus.EXPIRED);
        assertThat(stale.getDecidedAt()).isNotNull();
    }

    @Test
    @DisplayName("Expire is a no-op when there are no stale requests")
    void expireNoOp() {
        when(repository.findStalePendingAllTenants(any(Instant.class)))
                .thenReturn(List.of());

        service.expirePendingRequests();

        verify(repository, never()).save(any());
    }

    // ------------------- helper -------------------

    private static ApprovalRequest newPending(String requestId, String action, String requester) {
        return ApprovalRequest.builder()
                .requestId(requestId)
                .tenantId("tenant-A")
                .action(action)
                .resourceType("layout")
                .resourceId("L1")
                .requesterId(requester)
                .requesterEmail(requester + "@example.com")
                .status(ApprovalStatus.PENDING)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
