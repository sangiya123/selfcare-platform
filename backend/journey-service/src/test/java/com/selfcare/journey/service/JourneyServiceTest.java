package com.selfcare.journey.service;

import com.selfcare.journey.domain.JourneyDefinition;
import com.selfcare.journey.domain.JourneyInstance;
import com.selfcare.journey.domain.JourneyStep;
import com.selfcare.journey.repository.JourneyDefinitionRepository;
import com.selfcare.journey.repository.JourneyInstanceRepository;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.BadRequestException;
import com.selfcare.platform.common.web.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JourneyService.
 *
 * Verifies:
 * - Journey definition CRUD (list, get, create, publish, archive)
 * - Journey instance lifecycle (start, advance, abandon)
 * - Error cases: not found, invalid status transitions
 */
@ExtendWith(MockitoExtension.class)
class JourneyServiceTest {

    @Mock private JourneyDefinitionRepository definitionRepository;
    @Mock private JourneyInstanceRepository instanceRepository;
    @Mock private JourneyEngine journeyEngine;

    private JourneyService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new JourneyService(definitionRepository, instanceRepository, journeyEngine);
        setField(service, "instanceTtlDays", 30);

        TenantContext ctx = new TenantContext();
        ctx.setTenantId("dialog-lk");
        ctx.setUserId("user-1");
        TenantContext.set(ctx);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    // ======================================================================
    // listJourneys
    // ======================================================================

    @Test
    @DisplayName("listJourneys returns all definitions for tenant")
    void listJourneys_returnsAll() {
        JourneyDefinition j1 = JourneyDefinition.builder().journeyId("onboarding").name("Onboarding").build();
        JourneyDefinition j2 = JourneyDefinition.builder().journeyId("upgrades").name("Upgrades").build();
        when(definitionRepository.findByTenantId("dialog-lk")).thenReturn(List.of(j1, j2));

        List<JourneyDefinition> result = service.listJourneys();

        assertThat(result).hasSize(2);
    }

    // ======================================================================
    // getJourney
    // ======================================================================

    @Test
    @DisplayName("getJourney returns definition when found")
    void getJourney_found() {
        JourneyDefinition def = JourneyDefinition.builder()
                .journeyId("onboarding").tenantId("dialog-lk").build();
        when(definitionRepository.findByTenantIdAndJourneyId("dialog-lk", "onboarding"))
                .thenReturn(Optional.of(def));

        JourneyDefinition result = service.getJourney("onboarding");

        assertThat(result.getJourneyId()).isEqualTo("onboarding");
    }

    @Test
    @DisplayName("getJourney throws NotFoundException when missing")
    void getJourney_notFound() {
        when(definitionRepository.findByTenantIdAndJourneyId("dialog-lk", "unknown"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJourney("unknown"))
                .isInstanceOf(NotFoundException.class);
    }

    // ======================================================================
    // createJourney
    // ======================================================================

    @Test
    @DisplayName("createJourney saves definition with DRAFT status")
    void createJourney_savesDraft() {
        JourneyDefinition input = JourneyDefinition.builder()
                .journeyId("new-flow")
                .name("New Flow")
                .build();
        when(definitionRepository.existsByTenantIdAndJourneyId("dialog-lk", "new-flow"))
                .thenReturn(false);
        when(definitionRepository.save(any(JourneyDefinition.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        JourneyDefinition result = service.createJourney(input);

        assertThat(result.getStatus()).isEqualTo(JourneyDefinition.JourneyStatus.DRAFT);
        assertThat(result.getTenantId()).isEqualTo("dialog-lk");
    }

    @Test
    @DisplayName("createJourney throws BadRequestException for duplicate journeyId")
    void createJourney_duplicate() {
        JourneyDefinition input = JourneyDefinition.builder()
                .journeyId("existing").build();
        when(definitionRepository.existsByTenantIdAndJourneyId("dialog-lk", "existing"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createJourney(input))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("existing");
    }

    // ======================================================================
    // publishJourney
    // ======================================================================

    @Test
    @DisplayName("publishJourney transitions DRAFT to PUBLISHED")
    void publishJourney_draftToPublished() {
        JourneyDefinition def = JourneyDefinition.builder()
                .journeyId("onboarding")
                .tenantId("dialog-lk")
                .status(JourneyDefinition.JourneyStatus.DRAFT)
                .version(1)
                .entryStepId("step-1")
                .steps(List.of(JourneyStep.builder()
                        .stepId("step-1").type(JourneyStep.StepType.FORM).build()))
                .build();
        when(definitionRepository.findByTenantIdAndJourneyId("dialog-lk", "onboarding"))
                .thenReturn(Optional.of(def));
        when(definitionRepository.save(any(JourneyDefinition.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        JourneyDefinition result = service.publishJourney("onboarding");

        assertThat(result.getStatus()).isEqualTo(JourneyDefinition.JourneyStatus.PUBLISHED);
        assertThat(result.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("publishJourney throws BadRequestException for non-DRAFT journey")
    void publishJourney_archivedFails() {
        JourneyDefinition def = JourneyDefinition.builder()
                .journeyId("old")
                .tenantId("dialog-lk")
                .status(JourneyDefinition.JourneyStatus.ARCHIVED)
                .build();
        when(definitionRepository.findByTenantIdAndJourneyId("dialog-lk", "old"))
                .thenReturn(Optional.of(def));

        assertThatThrownBy(() -> service.publishJourney("old"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("DRAFT");
    }

    // ======================================================================
    // archiveJourney
    // ======================================================================

    @Test
    @DisplayName("archiveJourney transitions PUBLISHED to ARCHIVED")
    void archiveJourney_publishedToArchived() {
        JourneyDefinition def = JourneyDefinition.builder()
                .journeyId("onboarding")
                .tenantId("dialog-lk")
                .status(JourneyDefinition.JourneyStatus.PUBLISHED)
                .build();
        when(definitionRepository.findByTenantIdAndJourneyId("dialog-lk", "onboarding"))
                .thenReturn(Optional.of(def));
        when(definitionRepository.save(any(JourneyDefinition.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        JourneyDefinition result = service.archiveJourney("onboarding");

        assertThat(result.getStatus()).isEqualTo(JourneyDefinition.JourneyStatus.ARCHIVED);
    }

    // ======================================================================
    // startJourney (instance lifecycle)
    // ======================================================================

    @Test
    @DisplayName("startJourney creates instance for published journey")
    void startJourney_createsInstance() {
        JourneyDefinition def = JourneyDefinition.builder()
                .journeyId("onboarding")
                .tenantId("dialog-lk")
                .status(JourneyDefinition.JourneyStatus.PUBLISHED)
                .entryStepId("step-1")
                .steps(List.of(JourneyStep.builder()
                        .stepId("step-1").type(JourneyStep.StepType.FORM).build()))
                .build();
        when(definitionRepository.findByTenantIdAndJourneyId("dialog-lk", "onboarding"))
                .thenReturn(Optional.of(def));
        when(instanceRepository.findByTenantIdAndUserIdAndJourneyIdAndStatus(
                eq("dialog-lk"), eq("user-1"), eq("onboarding"), any()))
                .thenReturn(Optional.empty());
        when(instanceRepository.save(any(JourneyInstance.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        JourneyInstance result = service.startJourney(
                "onboarding", "user-1", "127.0.0.1", "test-agent");

        assertThat(result).isNotNull();
        assertThat(result.getCurrentStepId()).isEqualTo("step-1");
        assertThat(result.getStatus()).isEqualTo(JourneyInstance.InstanceStatus.ACTIVE);
    }

    @Test
    @DisplayName("startJourney throws BadRequestException when journey is not published")
    void startJourney_draftFails() {
        JourneyDefinition def = JourneyDefinition.builder()
                .journeyId("draft-flow")
                .tenantId("dialog-lk")
                .status(JourneyDefinition.JourneyStatus.DRAFT)
                .build();
        when(definitionRepository.findByTenantIdAndJourneyId("dialog-lk", "draft-flow"))
                .thenReturn(Optional.of(def));

        assertThatThrownBy(() -> service.startJourney(
                "draft-flow", "user-1", "127.0.0.1", "test-agent"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not available");
    }

    @Test
    @DisplayName("startJourney resumes existing ACTIVE instance instead of creating a new one")
    void startJourney_resumesActive() {
        JourneyDefinition def = JourneyDefinition.builder()
                .journeyId("onboarding")
                .tenantId("dialog-lk")
                .status(JourneyDefinition.JourneyStatus.PUBLISHED)
                .entryStepId("step-1")
                .steps(List.of(JourneyStep.builder()
                        .stepId("step-1").type(JourneyStep.StepType.FORM).build()))
                .build();
        JourneyInstance existing = JourneyInstance.builder()
                .id("ji-1")
                .tenantId("dialog-lk")
                .userId("user-1")
                .journeyId("onboarding")
                .status(JourneyInstance.InstanceStatus.ACTIVE)
                .state(new HashMap<>())
                .build();
        when(definitionRepository.findByTenantIdAndJourneyId("dialog-lk", "onboarding"))
                .thenReturn(Optional.of(def));
        when(instanceRepository.findByTenantIdAndUserIdAndJourneyIdAndStatus(
                eq("dialog-lk"), eq("user-1"), eq("onboarding"), any()))
                .thenReturn(Optional.of(existing));

        JourneyInstance result = service.startJourney(
                "onboarding", "user-1", "127.0.0.1", "test-agent");

        assertThat(result).isSameAs(existing);
        verify(instanceRepository, never()).save(any(JourneyInstance.class));
    }

    // ======================================================================
    // advanceStep
    // ======================================================================

    @Test
    @DisplayName("advanceStep moves instance to next step")
    void advanceStep_nextStep() {
        JourneyStep step1 = JourneyStep.builder()
                .stepId("step-1").type(JourneyStep.StepType.FORM)
                .nextStepIds(List.of("step-2"))
                .build();
        JourneyStep step2 = JourneyStep.builder()
                .stepId("step-2").type(JourneyStep.StepType.FORM)
                .build();
        JourneyDefinition def = JourneyDefinition.builder()
                .journeyId("onboarding")
                .tenantId("dialog-lk")
                .status(JourneyDefinition.JourneyStatus.PUBLISHED)
                .entryStepId("step-1")
                .steps(List.of(step1, step2))
                .build();
        JourneyInstance instance = JourneyInstance.builder()
                .id("ji-1")
                .tenantId("dialog-lk")
                .userId("user-1")
                .journeyId("onboarding")
                .currentStepId("step-1")
                .status(JourneyInstance.InstanceStatus.ACTIVE)
                .state(new HashMap<>())
                .build();
        when(instanceRepository.findById("ji-1")).thenReturn(Optional.of(instance));
        when(definitionRepository.findByTenantIdAndJourneyId("dialog-lk", "onboarding"))
                .thenReturn(Optional.of(def));
        when(journeyEngine.executeStep(any(), any(), any())).thenReturn(Map.of());
        when(journeyEngine.determineNextStep(any(), any(), any())).thenReturn("step-2");
        when(instanceRepository.save(any(JourneyInstance.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        JourneyInstance result = service.advanceStep("ji-1", Map.of("name", "John"));

        assertThat(result).isNotNull();
        assertThat(result.getCurrentStepId()).isEqualTo("step-2");
        assertThat(result.getStatus()).isEqualTo(JourneyInstance.InstanceStatus.ACTIVE);
    }

    @Test
    @DisplayName("advanceStep throws NotFoundException for unknown instance")
    void advanceStep_notFound() {
        when(instanceRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.advanceStep("unknown", Map.of()))
                .isInstanceOf(NotFoundException.class);
    }

    // ======================================================================
    // abandonJourney
    // ======================================================================

    @Test
    @DisplayName("abandonJourney transitions instance to ABANDONED")
    void abandonJourney_setsAbandoned() {
        JourneyInstance instance = JourneyInstance.builder()
                .id("inst-1")
                .tenantId("dialog-lk")
                .userId("user-1")
                .journeyId("onboarding")
                .status(JourneyInstance.InstanceStatus.ACTIVE)
                .state(new HashMap<>())
                .build();
        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(instance));

        service.abandonJourney("inst-1", "user-cancelled");

        assertThat(instance.getStatus()).isEqualTo(JourneyInstance.InstanceStatus.ABANDONED);
        assertThat(instance.getFailureReason()).isEqualTo("user-cancelled");
        verify(instanceRepository).save(instance);
    }
}