package buaa.msasca.sca.infra.persistence.jpa.mapper;

import buaa.msasca.sca.core.domain.model.ToolRun;
import buaa.msasca.sca.infra.persistence.jpa.entity.run.ToolRunEntity;

public class ToolRunMapper {
    public ToolRun toDomain(ToolRunEntity e) {
        return new ToolRun(
            e.getId(),
            e.getAnalysisRun().getId(),
            e.getToolType(),
            e.getToolVersion(),
            e.getConfigJson(),
            e.getStatus(),
            e.getStartedAt(),
            e.getFinishedAt(),
            e.getErrorMessage(),
            e.getCreatedAt(),
            e.getUpdatedAt()
        );
    }
}