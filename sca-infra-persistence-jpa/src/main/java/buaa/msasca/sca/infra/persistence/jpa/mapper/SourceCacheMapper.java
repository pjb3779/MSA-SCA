package buaa.msasca.sca.infra.persistence.jpa.mapper;

import buaa.msasca.sca.core.domain.model.ProjectVersionSourceCache;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectVersionSourceCacheEntity;

public class SourceCacheMapper {
    public ProjectVersionSourceCache toDomain(ProjectVersionSourceCacheEntity e) {
        return new ProjectVersionSourceCache(
            e.getId(),
            e.getProjectVersion().getId(),
            e.getStoragePath(),
            e.isValid(),
            e.getExpiresAt()
        );
    }
}
