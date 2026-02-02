package buaa.msasca.sca.infra.persistence.jpa.adapter;

import java.time.Instant;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import buaa.msasca.sca.core.domain.model.ProjectVersionSourceCache;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCacheCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCachePort;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectVersionEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectVersionSourceCacheEntity;
import buaa.msasca.sca.infra.persistence.jpa.mapper.SourceCacheMapper;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectVersionJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectVersionSourceCacheJpaRepository;


public class JpaProjectVersionSourceCacheAdapter implements ProjectVersionSourceCachePort, ProjectVersionSourceCacheCommandPort {

    private final ProjectVersionSourceCacheJpaRepository cacheRepo;
    private final ProjectVersionJpaRepository pvRepo;
    private final SourceCacheMapper mapper;

    public JpaProjectVersionSourceCacheAdapter(
        ProjectVersionSourceCacheJpaRepository cacheRepo,
        ProjectVersionJpaRepository pvRepo,
        SourceCacheMapper mapper
    ) {
        this.cacheRepo = cacheRepo;
        this.pvRepo = pvRepo;
        this.mapper = mapper;
    }

    @Override
    public Optional<ProjectVersionSourceCache> findValidByProjectVersionId(Long projectVersionId) {
        return cacheRepo.findFirstByProjectVersion_IdAndValidTrueOrderByUpdatedAtDesc(projectVersionId)
            .map(mapper::toDomain);
    }

    @Override
    @Transactional
    public ProjectVersionSourceCache createNewValid(Long projectVersionId, String storagePath, Instant expiresAt) {
        ProjectVersionEntity pv = pvRepo.findById(projectVersionId)
            .orElseThrow(() -> new IllegalArgumentException("project_version not found: " + projectVersionId));

        // 기존 캐시 invalidate
        for (ProjectVersionSourceCacheEntity e : cacheRepo.findByProjectVersion_IdOrderByUpdatedAtDesc(projectVersionId)) {
        if (e.isValid()) e.invalidate();
        }

        // 새 캐시 생성(기본 valid=true)
        ProjectVersionSourceCacheEntity created = ProjectVersionSourceCacheEntity.create(pv, storagePath, expiresAt);
        ProjectVersionSourceCacheEntity saved = cacheRepo.save(created);

        return mapper.toDomain(saved);
    }
}