package com.omobio.ai.repository;

import com.omobio.ai.domain.AiPromptVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiPromptVersionRepository extends JpaRepository<AiPromptVersion, String> {

    Optional<AiPromptVersion> findFirstByTemplateIdAndIsActiveTrueOrderByVersionDesc(String templateId);

    List<AiPromptVersion> findByTemplateIdOrderByVersionDesc(String templateId);

    Optional<AiPromptVersion> findByTemplateIdAndVersion(String templateId, Integer version);
}
