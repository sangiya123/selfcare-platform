package com.omobio.journey.service;

import com.omobio.journey.domain.JourneyDefinition;
import com.omobio.journey.domain.JourneyInstance;
import com.omobio.journey.domain.StepInstance;
import com.omobio.journey.repository.JourneyDefinitionRepository;
import com.omobio.journey.repository.JourneyInstanceRepository;
import com.omobio.platform.common.web.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
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

    private JourneyExecutionService service;

    @BeforeEach
    void setUp() {
        service = new JourneyExecutionService(definitionRepository, instanceRepository);
    }

    @Test
    @DisplayName("startJourney creates instance and returns first step")
    void startJourney_createsInstance() {
        JourneyDefinition definition = JourneyDefinition.builder()
                .id("jd-1")
                .journeyId("onboarding")
                .version(1)
                .steps(List.of(
                        JourneyDefinition.Step.builder()
                                .stepId("welcome")
                                .type("SCREEN")
                                .componentId("WelcomeScreen")
                                .build(),
                        JourneyDefinition.Step.builder()
                                .stepId("otp")
                                .type("SCREEN")
                                .componentId("OtpScreen")
                                .build()
                ))
                .build();

        when(definitionRepository.findByJourneyIdAndVersion("onboarding", 1))
                .thenReturn(Optional.of(definition));
        when(instanceRepository.save(any(JourneyInstance.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        JourneyInstance result = service.startJourney("onboarding", 1, "tenant-1", "conn-1",
                Map.of());

        assertThat(result.getJourneyId()).isEqualTo("onboarding");
        assertThat(result.getCurrentStepId()).isEqualTo("welcome");
        assertThat(result.getStatus()).isEqualTo("IN_PROGRESS");
        verify(instanceRepository).save(any(JourneyInstance.class));
    }

    @Test
    @DisplayName("startJourney throws NotFoundException for unknown journey")
    void startJourney_notFound() {
        when(definitionRepository.findByJourneyIdAndVersion("unknown-journey", 1))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startJourney("unknown-journey", 1,
                "tenant-1", "conn-1", Map.of()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Journey not found");
    }

    @Test
    @DisplayName("advanceStep moves to next step and updates instance")
    void advanceStep_toNextStep() {
        JourneyDefinition.Step step1 = JourneyDefinition.Step.builder()
                .stepId("step-1").type("SCREEN").componentId("Screen1").build();
        JourneyDefinition.Step step2 = JourneyDefinition.Step.builder()
                .stepId("step-2").type("SCREEN").componentId("Screen2").build();

        JourneyDefinition definition = JourneyDefinition.builder()
                .id("jd-1").journeyId("test").version(1)
                .steps(List.of(step1, step2))
                .build();

        JourneyInstance instance = JourneyInstance.builder()
                .id("ji-1")
                .journeyId("test")
                .version(1)
                .currentStepId("step-1")
                .status("IN_PROGRESS")
                .tenantId("tenant-1")
                .build();

        when(definitionRepository.findByJourneyIdAndVersion("test", 1))
                .thenReturn(Optional.of(definition));
        when(instanceRepository.findById("ji-1")).thenReturn(Optional.of(instance));
        when(instanceRepository.save(any(JourneyInstance.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        JourneyInstance result = service.advanceStep("ji-1", "step-1", Map.of());

        assertThat(result.getCurrentStepId()).isEqualTo("step-2");
    }

    @Test
    @DisplayName("advanceStep on last step marks journey as COMPLETED")
    void advanceStep_completesJourney() {
        JourneyDefinition.Step step1 = JourneyDefinition.Step.builder()
                .stepId("step-1").type("SCREEN").componentId("Screen1").build();

        JourneyDefinition definition = JourneyDefinition.builder()
                .id("jd-1").journeyId("simple").version(1)
                .steps(List.of(step1))
                .build();

        JourneyInstance instance = JourneyInstance.builder()
                .id("ji-1")
                .journeyId("simple")
                .version(1)
                .currentStepId("step-1")
                .status("IN_PROGRESS")
                .tenantId("tenant-1")
                .build();

        when(definitionRepository.findByJourneyIdAndVersion("simple", 1))
                .thenReturn(Optional.of(definition));
        when(instanceRepository.findById("ji-1")).thenReturn(Optional.of(instance));
        when(instanceRepository.save(any(JourneyInstance.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        JourneyInstance result = service.advanceStep("ji-1", "step-1", Map.of());

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
    }
}
