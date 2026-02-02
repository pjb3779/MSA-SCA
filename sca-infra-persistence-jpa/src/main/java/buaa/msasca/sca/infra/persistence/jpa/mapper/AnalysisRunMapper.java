package buaa.msasca.sca.infra.persistence.jpa.mapper;

import buaa.msasca.sca.core.domain.model.AnalysisRun;
import buaa.msasca.sca.infra.persistence.jpa.entity.run.AnalysisRunEntity;

public class AnalysisRunMapper {
    public AnalysisRun toDomain(AnalysisRunEntity e) {
        return new AnalysisRun(
            e.getId(),
            e.getProjectVersion().getId(),
            e.getConfigJson(),
            e.getStatus(),
            e.getStartedAt(),
            e.getFinishedAt(),
            e.getTriggeredBy(),
            e.getCreatedAt(),
            e.getUpdatedAt()
        );
    }
}