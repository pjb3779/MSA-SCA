package buaa.msasca.sca.core.port.in;

import java.util.Optional;

import buaa.msasca.sca.core.domain.model.ProjectVersionSourceCache;

public interface GetSourceCacheUseCase {
    Optional<ProjectVersionSourceCache> findValidByProjectVersionId(Long projectVersionId);
}
