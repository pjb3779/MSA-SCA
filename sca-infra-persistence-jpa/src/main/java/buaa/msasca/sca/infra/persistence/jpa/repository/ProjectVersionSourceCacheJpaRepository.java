package buaa.msasca.sca.infra.persistence.jpa.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectVersionSourceCacheEntity;

public interface ProjectVersionSourceCacheJpaRepository extends JpaRepository<ProjectVersionSourceCacheEntity, Long>{
    Optional<ProjectVersionSourceCacheEntity> findFirstByProjectVersion_IdAndValidTrueOrderByUpdatedAtDesc(Long projectVersionId);

    List<ProjectVersionSourceCacheEntity> findByProjectVersion_IdOrderByUpdatedAtDesc(Long projectVersionId);
}