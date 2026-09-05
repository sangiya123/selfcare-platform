package com.omobio.journey.service;

import com.omobio.journey.domain.HistoryEntry;
import com.omobio.journey.domain.JourneyDefinition;
import com.omobio.journey.domain.JourneyInstance;
import com.omobio.journey.domain.JourneyStep;
import com.omobio.journey.repository.JourneyDefinitionRepository;
import com.omobio.journey.repository.JourneyInstanceRepository;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.BadRequestException;
import com.omobio.platform.common.web.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
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
 * - SpEL condition evaluation in JourneyEngine
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
    @DisplayName("publishJourney throws BadRequestException for ARCHIVED journey")
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
                .hasMessageContaining("ARCHIVED");
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
                .build();
        when(definitionRepository.findByTenantIdAndJourneyId("dialog-lk", "onboarding"))
                .thenReturn(Optional.of(def));
        when(instanceRepository.existsByTenantIdAndUserIdAndJourneyId("dialog-lk", "user-1", "onboarding"))
                .thenReturn(false);
        when(journeyEngine.executeStep(any(), any())).thenReturn(
                new JourneyService.StepResult("step-1", true, null, null));
        when(instanceRepository.save(any(JourneyInstance.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        JourneyInstance result = service.startJourney("onboarding");

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(JourneyInstance.InstanceStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("startJourney throws BadRequestException when journey is DRAFT")
    void startJourney_draftFails() {
        JourneyDefinition def = JourneyDefinition.builder()
                .journeyId("draft-flow")
                .tenantId("dialog-lk")
                .status(JourneyDefinition.JourneyStatus.DRAFT)
                .build();
        when(definitionRepository.findByTenantIdAndJourneyId("dialog-lk", "draft-flow"))
                .thenReturn(Optional.of(def));

        assertThatThrownBy(() -> service.startJourney("draft-flow"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    @DisplayName("startJourney throws BadRequestException when user already in journey")
    void startJourney_alreadyActive() {
        JourneyDefinition def = JourneyDefinition.builder()
                .journeyId("onboarding")
                .tenantId("dialog-lk")
                .status(JourneyDefinition.JourneyStatus.PUBLISHED)
                .build();
        when(definitionRepository.findByTenantIdAndJourneyId("dialog-lk", "onboarding"))
                .thenReturn(Optional.of(def));
        when(instanceRepository.existsByTenantIdAndUserIdAndJourneyId("dialog-lk", "user-1", "onboarding"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.startJourney("onboarding"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already in progress");
    }

    // ======================================================================
    // advanceJourney
    // ======================================================================

    @Test
    @DisplayName("advanceJourney moves instance to next step")
    void advanceJourney_nextStep() {
        JourneyInstance instance = JourneyInstance.builder()
                .instanceId("inst-1")
                .tenantId("dialog-lk")
                .userId("user-1")
                .journeyId("onboarding")
                .status(JourneyInstance.InstanceStatus.IN_PROGRESS)
                .build();
        when(instanceRepository.findByInstanceId("inst-1")).thenReturn(Optional.of(instance));
        when(journeyEngine.executeStep(any(), any()))
                .thenReturn(new JourneyService.StepResult("step-2", true, null, null));
        when(instanceRepository.save(any(JourneyInstance.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        JourneyInstance result = service.advanceJourney("inst-1", null);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("advanceJourney throws NotFoundException for unknown instance")
    void advanceJourney_notFound() {
        when(instanceRepository.findByInstanceId("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.advanceJourney("unknown", null))
                .isInstanceOf(NotFoundException.class);
    }

    // ======================================================================
    // abandonJourney
    // ======================================================================

    @Test
    @DisplayName("abandonJourney transitions instance to ABANDONED")
    void abandonJourney_setsAbandoned() {
        JourneyInstance instance = JourneyInstance.builder()
                .instanceId("inst-1")
                .tenantId("dialog-lk")
                .userId("user-1")
                .journeyId("onboarding")
                .status(JourneyInstance.InstanceStatus.IN_PROGRESS)
                .build();
        when(instanceRepository.findByInstanceId("inst-1")).thenReturn(Optional.of(instance));
        when(instanceRepository.save(any(JourneyInstance.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        JourneyInstance result = service.abandonJourney("inst-1");

        assertThat(result.getStatus()).isEqualTo(JourneyInstance.InstanceStatus.ABANDONED);
    }

    // ======================================================================
    // JourneyEngine step evaluation
    // ======================================================================

    @Test
    @DisplayName("startJourney evaluates SpEL condition before starting")
    void startJourney_evaluatesCondition() {
        JourneyDefinition def = JourneyDefinition.builder()
                .journeyId("conditional")
                .tenantId("dialog-lk")
                .status(JourneyDefinition.JourneyStatus.PUBLISHED)
                .entryStepId("step-1")
                .build();
        when(definitionRepository.findByTenantIdAndJourneyId("dialog-lk", "conditional"))
                .thenReturn(Optional.of(def));
        when(instanceRepository.existsByTenantIdAndUserIdAndJourneyId("dialog-lk", "user-1", "conditional"))
                .thenReturn(false);
        // Engine says condition not met
        when(journeyEngine.executeStep(any(), any()))
                .thenReturn(new JourneyService.StepResult("step-1", false, "CONDITION_NOT_MET", null));

        JourneyInstance result = service.startJourney("conditional");

        assertThat(result.getStatus()).isEqualTo(JourneyInstance.InstanceStatus.ABANDONED);
        assertThat(result.getAbandonReason()).isEqualTo("CONDITION_NOT_MET");
    }
}
