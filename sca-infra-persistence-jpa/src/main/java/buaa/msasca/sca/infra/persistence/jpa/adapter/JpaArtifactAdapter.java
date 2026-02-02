package buaa.msasca.sca.infra.persistence.jpa.adapter;

import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

import buaa.msasca.sca.core.domain.enums.ArtifactType;
import buaa.msasca.sca.core.domain.model.AnalysisArtifact;
import buaa.msasca.sca.core.port.out.persistence.AnalysisArtifactPort;
import buaa.msasca.sca.infra.persistence.jpa.entity.artifact.AnalysisArtifactEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.run.ToolRunEntity;
import buaa.msasca.sca.infra.persistence.jpa.mapper.ArtifactMapper;
import buaa.msasca.sca.infra.persistence.jpa.repository.AnalysisArtifactJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ToolRunJpaRepository;

public class JpaArtifactAdapter implements AnalysisArtifactPort {

    private final AnalysisArtifactJpaRepository artifactRepo;
    private final ToolRunJpaRepository toolRunRepo;
    private final ArtifactMapper mapper;

    public JpaArtifactAdapter(AnalysisArtifactJpaRepository artifactRepo, ToolRunJpaRepository toolRunRepo, ArtifactMapper mapper) {
        this.artifactRepo = artifactRepo;
        this.toolRunRepo = toolRunRepo;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public AnalysisArtifact create(Long toolRunId, ArtifactType type, String storagePath, JsonNode metadataJson) {
        ToolRunEntity tr = toolRunRepo.findById(toolRunId)
            .orElseThrow(() -> new IllegalArgumentException("tool_run not found: " + toolRunId));

        AnalysisArtifactEntity e = AnalysisArtifactEntity.create(tr, type, storagePath, metadataJson);
        return mapper.toDomain(artifactRepo.save(e));
    }
}