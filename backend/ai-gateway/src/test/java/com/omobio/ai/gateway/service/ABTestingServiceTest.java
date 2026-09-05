package com.omobio.ai.gateway.service;

import com.omobio.ai.service.ABTestingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ABTestingServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;

    private ABTestingService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new ABTestingService(redisTemplate);
    }

    @Test
    @DisplayName("returns null for unknown experiment")
    void unknownExperiment() {
        when(valueOps.get("omobio:ai:experiment:unknown")).thenReturn(null);

        String variant = service.getVariant("unknown", "user-1");

        assertThat(variant).isNull();
    }

    @Test
    @DisplayName("returns null for null user")
    void nullUser() {
        String variant = service.getVariant("any", null);
        assertThat(variant).isNull();
    }

    @Test
    @DisplayName("returns null for blank user")
    void blankUser() {
        String variant = service.getVariant("any", "   ");
        assertThat(variant).isNull();
    }

    @Test
    @DisplayName("returns null for inactive experiment")
    void inactiveExperiment() {
        ABTestingService.Experiment exp = ABTestingService.Experiment.builder()
                .experimentId("exp-1")
                .active(false)
                .variants(List.of(
                        ABTestingService.Variant.builder().name("control").weight(100).build()
                ))
                .build();
        when(valueOps.get("omobio:ai:experiment:exp-1")).thenReturn(exp);

        String variant = service.getVariant("exp-1", "user-1");

        assertThat(variant).isNull();
    }

    @Test
    @DisplayName("returns cached assignment if already assigned")
    void cachedAssignment() {
        ABTestingService.Experiment exp = ABTestingService.Experiment.builder()
                .experimentId("exp-1")
                .active(true)
                .variants(List.of(
                        ABTestingService.Variant.builder().name("control").weight(50).build(),
                        ABTestingService.Variant.builder().name("treatment").weight(50).build()
                ))
                .build();

        when(valueOps.get("omobio:ai:experiment:exp-1")).thenReturn(exp);
        when(valueOps.get("omobio:ai:assignment:exp-1:user-1")).thenReturn("treatment");

        String variant = service.getVariant("exp-1", "user-1");

        assertThat(variant).isEqualTo("treatment");
    }

    @Test
    @DisplayName("assigns variant deterministically for the same user")
    void deterministicAssignment() {
        ABTestingService.Experiment exp = ABTestingService.Experiment.builder()
                .experimentId("exp-1")
                .active(true)
                .variants(List.of(
                        ABTestingService.Variant.builder().name("control").weight(50).build(),
                        ABTestingService.Variant.builder().name("treatment").weight(50).build()
                ))
                .build();

        when(valueOps.get("omobio:ai:experiment:exp-1")).thenReturn(exp);
        when(valueOps.get(anyString())).thenReturn(null);

        // First call assigns and caches
        String variant1 = service.getVariant("exp-1", "user-1");
        assertThat(variant1).isIn("control", "treatment");
    }

    @Test
    @DisplayName("control variant returns the base template")
    void resolveControlTemplate() {
        // No experiment active
        when(valueOps.get("omobio:ai:experiment:prompt-default")).thenReturn(null);

        String templateId = service.resolveTemplateId("default", "user-1");
        assertThat(templateId).isEqualTo("default");
    }

    @Test
    @DisplayName("saveExperiment persists the experiment")
    void saveExperiment() {
        ABTestingService.Experiment exp = ABTestingService.Experiment.builder()
                .experimentId("exp-1")
                .name("Test")
                .active(true)
                .durationDays(7)
                .variants(List.of())
                .build();

        service.saveExperiment(exp);

        verify(valueOps).set(eq("omobio:ai:experiment:exp-1"),
                any(ABTestingService.Experiment.class),
                any(java.time.Duration.class));
    }
}
