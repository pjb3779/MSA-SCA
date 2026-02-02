package buaa.msasca.sca.infra.persistence.jpa.mapper;

import buaa.msasca.sca.core.domain.model.AnalysisArtifact;
import buaa.msasca.sca.infra.persistence.jpa.entity.artifact.AnalysisArtifactEntity;

public class ArtifactMapper {
    public AnalysisArtifact toDomain(AnalysisArtifactEntity e) {
        return new AnalysisArtifact(
            e.getId(),
            e.getToolRun().getId(),
            e.getArtifactType(),
            e.getStoragePath(),
            e.getMetadataJson(),
            e.getCreatedAt()
        );
    }
}