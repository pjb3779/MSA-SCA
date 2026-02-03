package buaa.msasca.sca.core.port.in;

import java.util.Optional;

import buaa.msasca.sca.core.domain.model.ProjectVersionSourceCache;

public interface GetSourceCacheUseCase {
    //없으면 Optional.empty()를 반환
    Optional<ProjectVersionSourceCache> findValidByProjectVersionId(Long projectVersionId);
}
