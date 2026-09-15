package com.selfcare.config.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfcare.config.compiler.ConfigCompiler;
import com.selfcare.config.compiler.ConfigSchemaValidator;
import com.selfcare.config.compiler.ManifestSigner;
import com.selfcare.config.compiler.ManifestVerifier;
import com.selfcare.config.domain.LayoutDocument;
import com.selfcare.config.repository.LayoutDocumentRepository;
import com.selfcare.config.repository.ThemeDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LayoutServiceTest {

    private LayoutDocumentRepository layoutRepository;
    private ConfigCompiler configCompiler;
    private LayoutService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        layoutRepository = mock(LayoutDocumentRepository.class);
        ThemeDocumentRepository themeRepository = mock(ThemeDocumentRepository.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ConfigSchemaValidator validator = new ConfigSchemaValidator(mapper);
        ConfigCompiler.ThemeRepository themes = ref -> Optional.of(new java.util.HashMap<>());
        ConfigCompiler.NavigationRepository navs = ref -> Optional.of(new java.util.ArrayList<>());
        ConfigCompiler.ComponentCatalogRepository catalog = (tenantId, env) -> List.of();
        configCompiler = new ConfigCompiler(mapper, id -> true, themes, navs, catalog, validator,
                new ManifestSigner("unit-test-key"));
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        service = new LayoutService(layoutRepository, themeRepository, configCompiler, redis,
                mock(ApprovalClient.class));
    }

    private LayoutDocument layout(String profileKey) {
        return LayoutDocument.builder()
                .tenantId("dialog-lk").environment("prod").experience("home")
                .profileKey(profileKey).schemaVersion("2.0").configVersion(1)
                .themeRef("dialog-default@1.0.0").navigationRef("dialog-main@1")
                .status("PUBLISHED").build();
    }

    @Test
    void exactProfileKeyWins() {
        LayoutDocument doc = layout("mobile_prepaid_default");
        when(layoutRepository.findByTenantIdAndEnvironmentAndExperienceAndProfileKeyAndStatus(
                "dialog-lk", "prod", "home", "mobile_prepaid_default", "PUBLISHED"))
                .thenReturn(Optional.of(doc));

        ConfigCompiler.CompiledManifest manifest =
                service.getPublishedManifest("dialog-lk", "prod", "home", "mobile_prepaid_default");

        assertEquals("home", manifest.getExperience());
    }

    @Test
    void fallsBackToDefaultProfileKeyWhenRequestedProfileMissing() {
        LayoutDocument doc = layout("mobile_prepaid_default");
        when(layoutRepository.findByTenantIdAndEnvironmentAndExperienceAndProfileKeyAndStatus(
                eq("dialog-lk"), eq("prod"), eq("home"), eq("default"), eq("PUBLISHED")))
                .thenReturn(Optional.of(doc));

        ConfigCompiler.CompiledManifest manifest =
                service.getPublishedManifest("dialog-lk", "prod", "home", "unseeded-profile");

        assertEquals("mobile_prepaid_default", manifest.getProfileKey());
        verify(layoutRepository, never())
                .findByTenantIdAndEnvironmentAndExperienceAndStatusOrderByConfigVersionDesc(any(), any(), any(), any());
    }

    @Test
    void fallsBackToFirstPublishedForExperienceWhenNoProfileMatches() {
        LayoutDocument seeded = layout("mobile_prepaid_default");
        when(layoutRepository.findByTenantIdAndEnvironmentAndExperienceAndProfileKeyAndStatus(
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(layoutRepository.findByTenantIdAndEnvironmentAndExperienceAndStatusOrderByConfigVersionDesc(
                "dialog-lk", "prod", "home", "PUBLISHED"))
                .thenReturn(List.of(seeded));

        ConfigCompiler.CompiledManifest manifest =
                service.getPublishedManifest("dialog-lk", "prod", "home", "default");

        assertEquals("mobile_prepaid_default", manifest.getProfileKey());
    }

    @Test
    void throwsWhenNoPublishedLayoutExists() {
        when(layoutRepository.findByTenantIdAndEnvironmentAndExperienceAndProfileKeyAndStatus(
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(layoutRepository.findByTenantIdAndEnvironmentAndExperienceAndStatusOrderByConfigVersionDesc(
                any(), any(), any(), any())).thenReturn(List.of());

        assertThrows(com.selfcare.platform.common.web.NotFoundException.class,
                () -> service.getPublishedManifest("dialog-lk", "prod", "home", "default"));
    }
}