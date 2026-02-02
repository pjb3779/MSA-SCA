package buaa.msasca.sca.core.application.service;

import java.util.Optional;

import buaa.msasca.sca.core.domain.model.ProjectVersionSourceCache;
import buaa.msasca.sca.core.port.in.GetSourceCacheUseCase;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCachePort;

public class GetSourceCacheService implements GetSourceCacheUseCase {
    
    private final ProjectVersionSourceCachePort port;

    public GetSourceCacheService(ProjectVersionSourceCachePort port) {
        this.port = port;
    }

    @Override
    public Optional<ProjectVersionSourceCache> findValidByProjectVersionId(Long projectVersionId) {
        return port.findValidByProjectVersionId(projectVersionId);
    }
}
