package com.selfcare.journey.service;

import com.selfcare.journey.domain.JourneyDefinition;
import com.selfcare.journey.domain.JourneyInstance;
import com.selfcare.journey.domain.JourneyStep;
import com.selfcare.journey.repository.JourneyDefinitionRepository;
import com.selfcare.journey.repository.JourneyInstanceRepository;
import com.selfcare.platform.common.web.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JourneyExecutionServiceTest {

    @Mock private JourneyDefinitionRepository definitionRepository;
    @Mock private JourneyInstanceRepository instanceRepository;
    @Mock private JourneyEngine journeyEngine;

    private JourneyService service;

    @BeforeEach
    void setUp() {
        service = new JourneyService(definitionRepository, instanceRepository, journeyEngine);
        com.selfcare.platform.common.tenant.TenantContext ctx =
                new com.selfcare.platform.common.tenant.TenantContext();
        ctx.setTenantId("t1");
        ctx.setUserId("user-1");
        com.selfcare.platform.common.tenant.TenantContext.set(ctx);
    }

    @AfterEach
    void tearDown() {
        com.selfcare.platform.common.tenant.TenantContext.clear();
    }

    @Test
    @DisplayName("startJourney creates instance at entry step when published")
    void startJourney_createsInstance() {
        JourneyDefinition definition = JourneyDefinition.builder()
                .id("jd-1")
                .journeyId("onboarding")
                .version(1)
                .status(JourneyDefinition.JourneyStatus.PUBLISHED)
                .entryStepId("welcome")
                .steps(List.of(
                        JourneyStep.builder().stepId("welcome").type(JourneyStep.StepType.FORM).build(),
                        JourneyStep.builder().stepId("otp").type(JourneyStep.StepType.FORM).build()
                ))
                .build();

        when(definitionRepository.findByTenantIdAndJourneyId("t1", "onboarding"))
                .thenReturn(Optional.of(definition));
        when(instanceRepository.findByTenantIdAndUserIdAndJourneyIdAndStatus(
                anyString(), anyString(), anyString(), any()))
                .thenReturn(Optional.empty());
        when(instanceRepository.save(any(JourneyInstance.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        JourneyInstance result = service.startJourney(
                "onboarding", "user-1", "127.0.0.1", "test-agent");

        assertThat(result.getJourneyId()).isEqualTo("onboarding");
        assertThat(result.getCurrentStepId()).isEqualTo("welcome");
        assertThat(result.getStatus()).isEqualTo(JourneyInstance.InstanceStatus.ACTIVE);
        verify(instanceRepository).save(any(JourneyInstance.class));
    }

    @Test
    @DisplayName("startJourney throws NotFoundException for unknown journey")
    void startJourney_notFound() {
        when(definitionRepository.findByTenantIdAndJourneyId("t1", "unknown-journey"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startJourney(
                "unknown-journey", "user-1", "127.0.0.1", "test-agent"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("unknown-journey");
    }

    @Test
    @DisplayName("advanceStep moves to next step and updates instance")
    void advanceStep_toNextStep() {
        JourneyStep step1 = JourneyStep.builder()
                .stepId("step-1").type(JourneyStep.StepType.FORM)
                .nextStepIds(List.of("step-2"))
                .build();
        JourneyStep step2 = JourneyStep.builder()
                .stepId("step-2").type(JourneyStep.StepType.FORM)
                .build();

        JourneyDefinition definition = JourneyDefinition.builder()
                .id("jd-1").journeyId("test").version(1)
                .status(JourneyDefinition.JourneyStatus.PUBLISHED)
                .entryStepId("step-1")
                .steps(List.of(step1, step2))
                .build();

        JourneyInstance instance = JourneyInstance.builder()
                .id("ji-1")
                .journeyId("test")
                .journeyVersion(1)
                .currentStepId("step-1")
                .status(JourneyInstance.InstanceStatus.ACTIVE)
                .tenantId("t1")
                .userId("user-1")
                .state(new HashMap<>())
                .build();

        when(instanceRepository.findById("ji-1")).thenReturn(Optional.of(instance));
        when(definitionRepository.findByTenantIdAndJourneyId("t1", "test"))
                .thenReturn(Optional.of(definition));
        when(journeyEngine.executeStep(any(), any(), any())).thenReturn(Map.of());
        when(journeyEngine.determineNextStep(any(), any(), any())).thenReturn("step-2");
        when(instanceRepository.save(any(JourneyInstance.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        JourneyInstance result = service.advanceStep("ji-1", Map.of("name", "John"));

        assertThat(result.getCurrentStepId()).isEqualTo("step-2");
        assertThat(result.getStatus()).isEqualTo(JourneyInstance.InstanceStatus.ACTIVE);
    }

    @Test
    @DisplayName("advanceStep on last step marks journey as COMPLETED")
    void advanceStep_completesJourney() {
        JourneyStep step1 = JourneyStep.builder()
                .stepId("step-1").type(JourneyStep.StepType.FORM)
                .build();

        JourneyDefinition definition = JourneyDefinition.builder()
                .id("jd-1").journeyId("simple").version(1)
                .status(JourneyDefinition.JourneyStatus.PUBLISHED)
                .entryStepId("step-1")
                .steps(List.of(step1))
                .build();

        JourneyInstance instance = JourneyInstance.builder()
                .id("ji-1")
                .journeyId("simple")
                .journeyVersion(1)
                .currentStepId("step-1")
                .status(JourneyInstance.InstanceStatus.ACTIVE)
                .tenantId("t1")
                .userId("user-1")
                .state(new HashMap<>())
                .build();

        when(instanceRepository.findById("ji-1")).thenReturn(Optional.of(instance));
        when(definitionRepository.findByTenantIdAndJourneyId("t1", "simple"))
                .thenReturn(Optional.of(definition));
        when(journeyEngine.executeStep(any(), any(), any())).thenReturn(Map.of());
        when(journeyEngine.determineNextStep(any(), any(), any())).thenReturn(null);
        when(instanceRepository.save(any(JourneyInstance.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        JourneyInstance result = service.advanceStep("ji-1", Map.of());

        assertThat(result.getStatus()).isEqualTo(JourneyInstance.InstanceStatus.COMPLETED);
        assertThat(result.getCurrentStepId()).isNull();
    }
}